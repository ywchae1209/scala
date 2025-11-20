# [ 요건 ]
두 개의 DB_A과 DB_B 있는 TableA과 TableB의 Row들을 비교하는 기능

* 참고1: 비유

> 두 시점의 거주자 조사결과 목록비교를 하는 것처럼 생각해볼 수 있다.
>
> 두개의 거주자 목록이 있는데, 목록은 집주소 순서로 정렬되어 있다.  
> ( 두 목록의 집주소 정렬방식은 동일해야 한다. <-- 목록은 앞에서 뒤로 한번만 읽는다. )  
> ( 같은 집주소에 여러 집이 있으면 안된다. )
>
> 이때,
> 정렬키 == 집주소, 컬럼비교 == 거주자로 생각하면 쉬을 듯.  
> 집주소 같고, 거주자도 같으면 변동없음.   
> 집주소 같은데, 거주자 다르면 변동됨.  
> 집주소 다르면, 다른 집 (신축한 집이거나, 철거한 집)

* 참고2: 정렬방식이 중요한 이유와 (프로그램)이 정렬방식을 알아야 하는 이유.

> 집주소1 = `1, 3, 5, 7`
> 집주소2 = `2, 6, 4, 7` 인 경우에
> 첫번째 비교(`1` vs `2`) 이후,  
> 무엇을 반환(`1`을 반환해야 함)하고,   
> 무엇을 남겨서(`2`를 남겨서 `3`과 비교해야함) 진행할 지 결정해야 한다.  
> 즉, 작은 쪽을 반환하고 큰쪽을 남겨서 다음 비교를 해야 하므로  
> 대소비교(정렬방식)을 알아야 하는 것이다.

* 참고3: 개별 컬럼들의 정렬 순서가 필요한 이유

> 집주소가 시, 동, 번지로 구성되어 있다고 가정하면  
> `시`를 먼저 비교하고, 같을 경우에  
> `동`을 비교하며, 마찬가지로 같을 경우에  
> `번지`를 비교한다.  
> 참고2와 같은 논리로,  
> `시`간의 우선순위, `동`간의 우선순위, `번지`간의 우선순위를 알아야 한다.


# [ 전제와 제약 ]
## 1.	비교대상
```
SQL을 통해 얻어진 ResultSetA와 ResultSetB를 비교한다..
```

- DBMS의 정렬기능으로 정렬된 ResultSet을 비교함.
  ( 비교대상 건수가 매우 클 수 있으므로, Memory적재불가하기 때문)
- 비교성능의 상당부분은 **DBMS의 정렬성능과 데이터의 전송속도**에 영향 받음

## 2.	비교조건
### 1)	Order by 절의 조건(이하 SortKey ; 정렬조건)
```
ResultSetA과 ResultSetB는 **같은 SortKey 조건** 으로 정렬되어야 한다.
```
- 정렬된 두 집합비교이므로, 같은 정렬 조건이어야 함. ( 각기 다른 정렬조건이면 비교 불가)
- (이해가 안된다면, 도입의 설명을 다시 읽어보자.)

```
ResultSetA의 SortKey들은 **ResultSetA내에서 unique**해야 한다.
```

- 즉, SortKey는 특정 Row를 유일하게 식별할 수 있는 정보여야 함.
- ResultSetA와 ResultSetB의 각 대응하는 행들을 1:1 비교하므로 인한 제약

### 2)	Equals 조건  (이하 컬럼비교)
```
Order by 절에서 사용된 컬럼들 외에 RowA와 RowB에서 같기를 기대하는 컬럼쌍을 말한다.
```


## 3.	비교결과의 분류

|           | same SortKey | different SortKey   |
|-----------|--------------|---------------------|
| same cols | same         | onlyInA or onlyInB  |
| diff cols | changed      | onlyInA or onlyInB  |


설명:
정렬키가 같고, 컬럼비교가 다른 경우는 RowA가 RowB로 변경되었음을 의미.

# 입출력
## [ 입력 ]
아래의 입력은 예시이며, 바뀔 수 있음. ( 이름이 마음에 안든다던가... )

* 형식에 대한 고민.
> * json은 여러줄에 걸친 문자열입력이 제한적이어서, sql문을 보기좋게 작성하기 어렵다.
> * yaml은 한줄에 여러 필드를 나열할 수 없어서, 세로로 불필요하게 길어진다.
> * hocon은 간결하고 두가지 모두 가능한 포맷이어서, hocon을 쓰기로 한다.

```hocon
    TableA {
      driver = "oracle.jdbc.OracleDriver"
      jdbcUrl = "jdbc:oracle:thin:@//172.16.0.51:1521/orclpdb"
      username = "cdctest"
      password = "cdctest"
      pool = { maxPoolSize: 2, minIdle: 2, setIdleTimeoutMs: 60000, connectionTimeoutMs: 30000 }
      sql = """
        SELECT c1, c2, c3, c4, c5
        FROM tableA
        ORDER BY c1 DESC NULLS FIRST,
                 c2 ASC NULLS FIRST,
                 c3 DESC
      """
    }
 
    TableB {
      driver = "oracle.jdbc.OracleDriver"
      jdbcUrl = "jdbc:oracle:thin:@//172.16.0.52:1521/orclpdb"
      username = "cdctest"
      password = "cdctest"
      pool = { maxPoolSize: 2, minIdle: 2, setIdleTimeoutMs: 60000, connectionTimeoutMs: 30000 }
      sql = """
        SELECT d1, d2, d3, d4, d5
        FROM tableB
        ORDER BY d1 DESC NULLS FIRST,
                 d2 ASC NULLS FIRST,
                 d3 DESC
      """
   }
   ### 비교방법 설정 ( how to compare )
   compare {
      sortKey = [ { colA: 1, colB: 1, ascending: false, nullAsSmallest: true },
                  { colA: 2, colB: 2, ascending: false, nullAsSmallest: false, tolerance: { milli: 5 } },
                  { colA: 3, colB: 3, ascending: false, nullAsSmallest: false, tolerance: { delta: 0.001 } } ]
 
      compCols = [ { colA: 4, colB: 4 },
                   { colA: 5, colB: 5, tolerance: { delta: 0.01 } } ]
   }
    
   ### 처리방법 설정 ( hot to applyTo target DB) 
   # used format
    #{"type":"Change","uuid":"fbfd2808-91bc-4c53-8070-0f4a7f326e93","rowA":{"keys":[{"kind":"Decimal","idx":1,"val":"2"}],"cols":[{"kind":"Decimal","idx":2,"val":"5120"},{"kind":"LongString","idx":3}]},"rowB":{"keys":[{"kind":"Decimal","idx":1,"val":"2"}],"cols":[{"kind":"Decimal","idx":2,"val":"5120"},{"kind":"LongString","idx":3}]}}

  Change = {                            # 처리할 대상 json ( "type" = "Change" 인 json과 부속 "Ext")
    use.db = "mock"                     # use.db = "tableA" | "tableB" | "mock" 
    table = "TB_CLOB_DIFF"
    action = "update"                   # action = "insert" | "update" | "delete"
    batch = 10
    cols = {                            # "insert" 또는 "update"시의 column
        S_KEY1 = "rowA.keys.1"          
        C_SIZE = "rowA.cols.2"
        C_CLOB = "rowA.cols.3"
    }
    where = {                           # "delete" 또는 "update" 시의 조건 컬럼
        S_KEY1 = "rowB.keys.1"
    }
}

    

```
### 1.	비교대상 TableA와 TableB를 생성하는 방법에 대한 설정

```hocon
    TableA {
      driver = "oracle.jdbc.OracleDriver"
      jdbcUrl = "jdbc:oracle:thin:@//172.16.0.51:1521/orclpdb"
      username = "cdctest"
      password = "cdctest"
      pool = { maxPoolSize: 2, minIdle: 2, setIdleTimeoutMs: 60000, connectionTimeoutMs: 30000 }
      sql = """
        SELECT c1, c2, c3, c4, c5
        FROM tableA
        ORDER BY c1 DESC NULLS FIRST,
                 c2 ASC NULLS FIRST,
                 c3 DESC
      """
    }
 
    TableB {
      driver = "oracle.jdbc.OracleDriver"
      jdbcUrl = "jdbc:oracle:thin:@//172.16.0.52:1521/orclpdb"
      username = "cdctest"
      password = "cdctest"
      pool = { maxPoolSize: 2, minIdle: 2, setIdleTimeoutMs: 60000, connectionTimeoutMs: 30000 }
      sql = """
        SELECT d1, d2, d3, d4, d5
        FROM tableB
        ORDER BY d1 DESC NULLS FIRST,
                 d2 ASC NULLS FIRST,
                 d3 DESC
      """
   }
```

----

하나씩 살펴보자.
```hocon
driver = "oracle.jdbc.OracleDriver"
```
* DBMS 종류에 따라 사용할 JDBC Driver이름.개발자에게 문의하자.

```hocon
jdbcUrl = "jdbc:oracle:thin:@//172.16.0.51:1521/orclpdb"
username = "cdctest"
password = "cdctest"
```
* DBMS 접속정보와 사용할 계정정보이다.

```hocon
 pool = { maxPoolSize: 2, minIdle: 2, setIdleTimeoutMs: 60000, connectionTimeoutMs: 30000 }
```
* DBMS connection pool 설정이다. ( 생략해도 된다.)

```
sql = """
          SELECT d1, d2, d3, d4, d5
              FROM tableB
              ORDER BY d1 DESC NULLS FIRST,
                       d2 ASC NULLS LAST,
                       d3 DESC
        """
```
* 여러줄에 걸쳐 작성된 Table 추출을 위한 sql문이다.
* order by 절의 컬럼들은 ASC/DESC로 오름차순/내림차순을 **반드시** 명시하자.
* nullable커럼의 경우에는 NULL을 First에 둘지, Last에 둘지 **반드시** 명시하자. ( nullable이 아니면 생략해도 됨)
* 추출할 row의 범위를 조정하고 싶다면 `where절`을 추가하자.

#### 주의할 점
* TableA와 TableB의 sql문에서 `select` 컬럼의 갯수, `order by`컬럼의 갯수, 순서와 컬럼정렬(asc/desc, null last/first)는 일치해야 한다.

### 2. 비교조건

```hocon
    compare {
      sortKey = [ { colA: 1, colB: 1, ascending: false, nullAsSmallest: true },
                  { colA: 2, colB: 2, ascending: false, nullAsSmallest: false, tolerance: { milli: 5 } },
                  { colA: 3, colB: 3, ascending: false, nullAsSmallest: false, tolerance: { delta: 0.001 } } ]
 
      compCols = [ { colA: 4, colB: 4 },
                   { colA: 5, colB: 5, tolerance: { delta: 0.01 } } ]
    }
```
비교조건을 하나씩 살펴보자.

#### 1. SortKey 컬럼 조건쌍
```hocon
      sortKey = [ { colA: 1, colB: 1, ascending: false, nullAsSmallest: true },
                  { colA: 2, colB: 2, ascending: false, nullAsSmallest: false, tolerance: { milli: 5 } },
                  { colA: 3, colB: 3, ascending: false, nullAsSmallest: false, tolerance: { delta: 0.001 } } ]
```
* colA와 colB는 TableA와 TableB의 몇번째 컬럼(1부터 시작)끼리 비교하는 지를 정하는 것이다.
* 정렬순서를 알아야 하므로, ascending인지 여부(false면 descending), null값이 있으면 가장 작은 값으로 취급할지 여부를 정한다.
* tolerance(허용오차)는 두값이 허용오차범위내에서 다를 경우, 같다고 판단하는 값이다.

> millis : 몇 밀리의 오차를 허용할 지   
> delta  :  얼마나 오차를 허용할지 ( 실수를 쓰면 된다. )

#### 2.	컬럼비교 조건쌍

```hocon
      compCols = [ { colA: 4, colB: 4 },
                   { colA: 5, colB: 5, tolerance: { delta: 0.01 } } ]
    }
```
* colA와 colB는 TableA와 TableB의 몇번째 컬럼(1부터 시작)끼리 비교하는 지를 정하는 것이다.
* 같은 지 여부만 알면 되므로, 정렬순서판단을 위한 설정을 필요없다.
* 허용오차는 위와 마찬가지로, 두값이 허용오차범위내에서 다를 경우, 같다고 판단하는 값이다.

### 참고
1. 실제 비교를 진행하기 전에 DB접속가능한 지 여부, 읽어온 query결과(ResultSet)에 비교조건이 맞는지 등을 점검한다.
2. sql문을 해석하는 기능을 구현해서, 비교조건을 단순하게 정하면 좋을 텐데... 표준SQL만 반드시 사용한다고 볼 수 없어서 구현에 제한이 있다. 불편하면 의견 주기 바란다.



## [ compare 결과 출력 ]

아래와 같이 분류된 결과를 포함한 data를 print-out

```scala
case class OnlyInA[T](row: T) extends Result[T]
case class OnlyInB[T](row: T) extends Result[T]
case class Change[T](rowA: T, rowB: T) extends Result[T]
case class Same[T](rowA: T, rowB: T) extends Result[T]
```

* Json, Bson 등으로 출력하도록 할 예정
* 파일로 기록하는 기능은 shell의 redirect를 사용하면 될테니.. 굳이 넣을 필요 없을 듯.


### 주의: SQL문으로 치환할 수 없는 데이터 처리 (LOB 등의 경우)
```
LOB컬럼의 insert는 SQL문에 직접 쓸 수 없고, 
1. program에서 처리하거나
2. 비표준 SQL(오라클은 PL/SQL)로 처리(파일로 써놓고 해당컬럼으로 읽어들이도록 하는 식)
해야 한다.
```
* 파일 size문제로 binary형식의 보관을 해야 함 ( Bson같은 포맷으로 )
* 이 경우, 별도 viewer 내지 editor필요할지 따져봐야 함. ( Bson은 표준적 포맷이라 있을 법도 하고.)

### 처리결과의 format ( serialzie & deserialze )

1. 업계의 표준 형식을 사용하기로 한다. ( json )
2. 최대한 간결한 구조로 ( 읽기 쉽도록)
3. 알아야하는 구조의 갯수는 최소화 (2개 :: 판정결과와 Ext)
3. Large 데이터(Lob)의 분할 (Ext)

[ 유형 1]
* 비교결과 정보 ("OnlyInA" | "OnlyInB" | "Same" | "Change")

``` hocon
{
  "type": "Change",                      # "OnlyInA" | "OnlyInB" | "Same" | "Change" 
  "uuid": "086b90e0-bce0-43e4-9d2e-877eb55be45d",
  "rowA": {                              # tableA의 row
    "keys": [                            # 정렬비교에 사용한 컬럼
      { "kind": "Decimal", "idx": 1, "val": "1" }
    ],
    "cols": [                            # 값비교에 사용된 컬럼
      { "kind": "Bytes",     "idx": 2, "val": "AQIDBAUGBwgJCgsMDQ4PEA==" }, # binary데이타는 base64로 인코딩
      { "kind": "LongBytes", "idx": 3 },  # LongByte, LongString의 Ext json에 값이 담긴다.
      { "kind": "LongBytes", "idx": 4 }
    ]
  },
  "rowB": {                              # tableB의 row
    "keys": [
      { "kind": "Decimal", "idx": 1, "val": "1" }
    ],
    "cols": [
      { "kind": "Bytes",     "idx": 2, "val": "AQIDBAUGBwgJCgsMDQ4PEA==" },
      { "kind": "LongBytes", "idx": 3 },
      { "kind": "LongBytes", "idx": 4 }
    ]
  }
}
```

[ 유형 2]
* `유형 1`에 Large Column이 포함된 경우에 함께 저장되는 정보

```hocon
{
  "type": "Ext",                         # Ext 유형
  "isBin": true,                         # raw에 담긴 데이터가 binary인지 여부
  
  # locator 정보 :: Ext의 값(raw)이 어떤 판정(OnlyInA, ..)의 어떤 col에 해당하는 지
  "uuid": "086b90e0-bce0-43e4-9d2e-877eb55be45d", 
  "in": "rowA",
  "col": 3,
  
  # partition 정보 : 몇개의 Ext로 분할되었는지(all), 그중 몇번째인지 (num -- 0부터시작하는 순번)
  "all": 1,
  "num": 0,
  "raw": "TE9ORyBSQVcgU0FNUExFIERBVEE="
}

```

### 비교결과 출력의 사용

* 판단결과를 표준출력으로 print한다.
* 출력결과는 파일로 기록하는 것은 pipe (`|`)등을 이용하면 된다. ( 아래 명령 sample 참고)

> 파일쓰기 기능을 굳이 개발할 경우, 여러가지 문제발생 소지만 존재하므로  
> Unix의 철학에 따른 처리방식으로 이와 같이 처리하기로 함.

```bash
# out_file로 기록
some_command > out_file 

# 100M씩 분할하여 기록
some_command | split -b 100M - output_part_

# 100000라인씩 분할하여 기록
some_command | split -l 100000 - output_part_

# 화면 출력하면서 100M씩 분할하여 기록
some_command | tee >(split -b 100M - output_part_)

# 압축하여 100M씩 기록
some_command | gzip | split -b 100M - output.gz.part_

# 분할한 파일을 합칠때
cat output_part_* > full_output.txt

# 분할압축된 파일을 풀어서 합칠때
cat output.gz.part_* | gunzip > full_output.txt
```
* shell 사용법은 따로 공부해 두자.


## [ 후처리 : ApplyTo]

### 1.	후처리 작업 지시

```hocon
  ### 처리방법 설정 ( hot to applyTo target DB)
  #{"type":"Change","uuid":"fbfd2808-91bc-4c53-8070-0f4a7f326e93","rowA":{"keys":[{"kind":"Decimal","idx":1,"val":"2"}],"cols":[{"kind":"Decimal","idx":2,"val":"5120"},{"kind":"LongString","idx":3}]},"rowB":{"keys":[{"kind":"Decimal","idx":1,"val":"2"}],"cols":[{"kind":"Decimal","idx":2,"val":"5120"},{"kind":"LongString","idx":3}]}}

  Change = {                            # 처리할 대상 json ( "type" = "Change" 인 json과 부속 "Ext")
    use.db = "mock"                     # use.db = "tableA" | "tableB" | "mock"
    
    # 
    table = "TB_CLOB_DIFF"
    action = "update"                   # action = "insert" | "update" | "delete"
    batch = 10                          # batch단위 (생략하면 10. Large컬럼이 있으면 작은 값으로. 아니면 512, 1024 정도)
    
    cols = {                            # "insert" 또는 "update"시의 column
      S_KEY1 = "rowA.keys.1"          
      C_SIZE = "rowA.cols.2"
      C_CLOB = "rowA.cols.3"
    }
    
    where = {                           # "delete" 또는 "update" 시의 조건 컬럼
      S_KEY1 = "rowB.keys.1"
    }
  }
```

### DB connection 설정
1. `use.db`를 설정할 경우, `tableA` 또는 `tableB`의 접속정보를 이용하거나, `mock` (DBMS관련 호출을 화면에 출력하는 모드)
2. 만약, 다른 DB를 사용하고 싶다면 use.db 대신 아래의 정보를 쓰면 된다.
3. 1과 2의 설정이 모두 주어지면, 2의 설정을 사용.
```hocon
  Change = {                            # 처리할 대상 json ( "type" = "Change" 인 json과 부속 "Ext")
    # 이렇게 직접 지정할 수도 있다.
    driver = "oracle.jdbc.OracleDriver"
    jdbcUrl = "jdbc:oracle:thin:@//172.16.0.51:1521/orclpdb"
    username = "cdctest"
    password = "cdctest"
    
    table = "TB_CLOB_DIFF"
    action = "update"                   # action = "insert" | "update" | "delete"
    batch = 10                          # insert할 batch단위 ( Large 가 포함된 경우, 작은 값으로. 아니면 512, 1024 정도)
    
    cols = {                            # "insert" 또는 "update"시의 column
      S_KEY1 = "rowA.keys.1"          
      C_SIZE = "rowA.cols.2"
      C_CLOB = "rowA.cols.3"
    }
    
    where = {                           # "delete" 또는 "update" 시의 조건 컬럼
      S_KEY1 = "rowB.keys.1"
    }
  }
```

### use.db = "mock"

* 아래와 같은 정보를 볼 수 있다.
* DB에 어떤 요청을 하는지 일일이 추적할 수 있도록 하기 위한 기능 (for developer & tester)

> Tip.   
> applyTo에서 문제가 발생하면, use.db 값을 바꿔가면서 어떤 API 호출이 있는지 1:1 비교를 해보자.

```
Mark:	[Run] ApplyTo ====
Mark:	UPDATE TB_CLOB_DIFF SET C_CLOB = ?, S_KEY1 = ?, C_SIZE = ? WHERE S_KEY1 = ?	:::	C_CLOB(1) <--- rowA.cols.3 (), S_KEY1(2) <--- rowA.keys.1 (), C_SIZE(3) <--- rowA.cols.2 (), S_KEY1(4) <--- rowB.keys.1 ()
Mark:	[Run] use sample data.
Mark:	{"type":"Change","uuid":"086b90e0-bce0-43e4-9d2e-877eb55be45d","rowA":{"keys":[{"kind":"Decimal","idx":1,"val":"1"}],"cols":[{"kind":"Bytes","idx":2,"val":"AQIDBAUGBwgJCgsMDQ4PEA=="},{"kind":"LongBytes","idx":3},{"kind":"LongBytes","idx":4}]},"rowB":{"keys":[{"kind":"Decimal","idx":1,"val":"1"}],"cols":[{"kind":"Bytes","idx":2,"val":"AQIDBAUGBwgJCgsMDQ4PEA=="},{"kind":"LongBytes","idx":3},{"kind":"LongBytes","idx":4}]}}
{"type":"Ext","isBin":true,"uuid":"086b90e0-bce0-43e4-9d2e-877eb55be45d","in":"rowA","col":3,"all":1,"num":0,"raw":"TE9ORyBSQVcgU0FNUExFIERBVEE="}
{"type":"Ext","isBin":true,"uuid":"086b90e0-bce0-43e4-9d2e-877eb55be45d","in":"rowA","col":4,"all":1,"num":0,"raw":"QkxPQiBTQU1QTEUgREFUQQ=="}
{"type":"Ext","isBin":true,"uuid":"086b90e0-bce0-43e4-9d2e-877eb55be45d","in":"rowB","col":3,"all":1,"num":0,"raw":"TE9ORyBSQVcgU0FNUExFIERBVEE="}
{"type":"Ext","isBin":true,"uuid":"086b90e0-bce0-43e4-9d2e-877eb55be45d","in":"rowB","col":4,"all":1,"num":0,"raw":"QkxPQiBTQU1QTEUgREFUQQ=="}

		[Mock.Conn] setAutoCommit(false)
		[Mock.Conn] prepareStatement(sql=UPDATE TB_CLOB_DIFF SET C_CLOB = ?, S_KEY1 = ?, C_SIZE = ? WHERE S_KEY1 = ?)
Mark:	[DBHandler: Changed] UPDATE TB_CLOB_DIFF SET C_CLOB = ?, S_KEY1 = ?, C_SIZE = ? WHERE S_KEY1 = ?
	Memo:	[DBHandler: Changed] start new record 086b90e0-bce0-43e4-9d2e-877eb55be45d
Note:	[Director]	086b90e0-bce0-43e4-9d2e-877eb55be45d
	Memo:	[Director] initialize tasks with field-types
Note:	[setField]	086b90e0-bce0-43e4-9d2e-877eb55be45d		S_KEY1(2) <--- rowA.keys.1 (normal)	{"kind":"Decimal","idx":1,"val":"1"}
Note:	[setField]	086b90e0-bce0-43e4-9d2e-877eb55be45d		C_SIZE(3) <--- rowA.cols.2 (normal:binary)	{"kind":"Bytes","idx":2,"val":"AQIDBAUGBwgJCgsMDQ4PEA=="}
Note:	[setField]	086b90e0-bce0-43e4-9d2e-877eb55be45d		S_KEY1(4) <--- rowB.keys.1 (normal)	{"kind":"Decimal","idx":1,"val":"1"}
	Memo:	[DBHandler: Changed] start new Exts 086b90e0-bce0-43e4-9d2e-877eb55be45d
	Memo:	[Route] Change
		[Mock.Stmt] setString(2, 1)
		[Mock.Stmt] setBytes(3, length=16)
		[Mock.Stmt] setString(4, 1)
	Memo:	[handleExt] Add.. uuid: 086b90e0-bce0-43e4-9d2e-877eb55be45d ( rowA.cols.3 ) : 0/1 C_CLOB(1) <--- rowA.cols.3 (large:binary) 
Note:	[setExt]	086b90e0-bce0-43e4-9d2e-877eb55be45d.rowA.3	#1	C_CLOB(1) <--- rowA.cols.3 (large:binary)
Mark:	[DBHandler: Changed] Adding to batch.(LOBs completed) 086b90e0-bce0-43e4-9d2e-877eb55be45d
	Memo:	[DBHandler: Changed] Batch added : 086b90e0-bce0-43e4-9d2e-877eb55be45d
	Memo:	[DBHandler: Changed] Batch count : 1
	Memo:	[handleExt] Skip. uuid: 086b90e0-bce0-43e4-9d2e-877eb55be45d ( rowA.cols.4 ) : 0/1
	Memo:	[handleExt] Skip. uuid: 086b90e0-bce0-43e4-9d2e-877eb55be45d ( rowB.cols.3 ) : 0/1
	Memo:	[handleExt] Skip. uuid: 086b90e0-bce0-43e4-9d2e-877eb55be45d ( rowB.cols.4 ) : 0/1
Note:	[TimeLog] [DBHandler: Changed] flush elapsed: 0.05 ms
Note:	[DBHandler: Changed] flushed count : 0
Note:	[TimeLog] [DBHandler: Changed] commit elapsed: 0.03 ms
	Memo:	[Route] closeAll
		[Mock.Stmt] setBinaryStream(1, 086b90e0-bce0-43e4-9d2e-877eb55be45d.rowA.3	#1	C_CLOB(1) <--- rowA.cols.3 (large:binary))
		[Mock.Stmt] addBatch()
		[Mock.Stmt] clearParameters()
		[Mock.Stmt] executeBatch()
		[Mock.Conn] commit()
		[Mock.Stmt] close()
		[Mock.Conn] close()
```




# [ 상세 ]

## 1. 공통 : 타입 문제
```
문자열 정렬컬럼(“123”,… )과 정수(123,…)컬럼은 각각 정렬 순서가 다름
```

### 타입변환과 정렬은 SQL문에서 해야 한다.

```sql
SELECT 
  CAST(emp_id AS INTEGER) AS emp_id_num, -- CAST를 이용해 타입변환
  name
FROM employees
ORDER BY emp_id_num;                    -- 타입변환한 값으로 정렬
```
* SQL에서의 타입변환은 `CAST`/`CONVERT`를 사용한다.
* DBMS별로 타입변환 방법은 다를 수 있으니 미리 조사해 두도록 하자.
* 누군가 써 놓은 글. [[https://gent.tistory.com/621]]

### (정렬조건과 컬럼비교에서) 각 대응컬럼들은 같은 타입이어야 한다.

* SQL1과 SQL2의 비교컬럼쌍은 같은 타입이어야 한다. (당연한 얘기)
* SortKey1과 SortKey2는 같은 컬럼순서여야 한다. ( c1-c2.. == c1'-c2'..)

* 설명) 약간의 허용 : 아래의 경우, 정렬순서 문제는 없으므로
> SQL1과 SQL2의 대응컬럼타임이   
> 정수형(Short) vs 정수형(Long)이면 타입변환하지 않아도 된다.  
> 마찬가지로,   
> 실수형 vs 실수형일 경우 타입변환하지 않아도 된다.

* 프로그램 내부적으로 숫자형 변환을 하여 비교하기 때문임.
* 실수형과 정수형간의 비교는 프로그램에서 허용하지 않기로 한다.
* 실수형과 정수형간의 비교가 필요하면, sql문에서 형변환하도록 하자.


> 타입에 대한 상식
> 1. 모든 타입들이 equal 비교가 가능한 것은 아니다.
> 2. 모든 타입들이 대소비교(정렬) 가능한 것은 아니다. (json을 생각해 보라)

## 2. 정렬 조건

```
프로그램은 정렬조건에 따른 정렬 순서를 알아야 한다.
```

* SortKeyA와 SortKeyB가 다를 경우, 작은 쪽의 SortKey를 이동하면서 일치하는 상대방 SortKey를 찾아야 하므로.
* 이를 위해, 오름차순/내림차수, Null값의 정렬순서 등 정렬순서 파악을 위한 설정이 필요했다.
* (이해가 안된다면, 도입부의 설명을 다시 읽어보자. )

## 비교

### 허용오차
```
  대응하는 컬럼의 타입이 실수형 또는 시각(Time)형인 경우, 허용오차를 지정할 수 있다.
```
* 같은 실수(real number)라도 DBMS에 따라 다른 값을 저장하는 경우(DB 내의 저장방식에 따라 다름) 있으며,
* 시각도 nano-second, milli-second 단위저장 등 저장방식에 따라 다른 값을 저장하는 경우가 있다.


* 아래와 같은 단위로 허용오차를 정할 수 있다. (SortKey쌍, 컬럼비교 쌍 모두)
```scala
milli: Int    // 밀리단위로 허용오차 표기
delta: Double // 실수로 허용오차 표기
```

# 성능/Memory 최적화 및 제한

## 제한
#### 동시성 처리의 제한
``` 
ResultSet은 Cursor타입의 자료형으로,  
1. 특정 시점에 access가능한 row는 현재 cursor(지시자)가 가리키는 1개로 한정되며,
2. 지시자 자체를 저장할 방법을 제공하지 않으므로,
row의 지시자들을 모으고, 구획회해서 처리하는** 동시처리는 불가능**하다. (구조적 제한)
```
* 일정 묶음단위로로 쪼개서 비교하는 동시적 처리는 불가능하다는 뜻
* 동시처리가 필요하다면, `where절`로 Table의 범위를 나누어서 동시에 실행하자.
*
> 잡담)   
> Cursor타입인 ResultSet에 병렬처리를 하려면,  
> Row를 읽어서 memory에 적재해 둔 상태로 묶음/분할 처리를 해야 하는데,  
> LOB컬럼타입의 경우, 매우 큰 용량이 필요하므로   
> 여러 Row를 메모리적재가 곤란하다는 제한이 있다.  
> DBMS vendor의 고육지책으로 나온 방식(ResultSet)으로 이해는 되지만  
> 그만큼 한계도 뚜렷한 것 같다.

## 최적화

#### 1.	Long String, Long Binary (LOB 등)
* 동시에 Memory 적재하는 것은 최소화 ( 동시에 최대 4개 이하)
* Large Memory할당 최소화 ( 1개의 1GB 대신, 1024개의 1MB로)

```
ResultSet이 Cursor이라는 특성을 가져서 생긴 제한으로, 
두번 읽거나 또는 메모리에 적재하거나 하는 방식 중 하나를 선택해야 함.
이 중에서 메모리 적재를 선택하고, 소요량을 최소로 하는 방식을 선택한 것이다.
``` 

```
메모리에 문제가 보이면, 실행시 JVM메모리 옵션을 조정해보도록 하자. (각자 찾아보자)
```

#### 2.	메모리
* 필요한 만큼만 지연로딩(통신부담경감) 및 지연할당, zero-메모리 복제(메모리사용량 최소화)

#### 3.	비교연산 최적화
* 아래와 같은 이유로 LOB등에 대한 Hash비교는 하지 않는 대신, 조기판정(early-decision)을 통한 비교 연산 최소화
```
하나의 LOB를 다른 LOB와 비교하는 경우는 LOB당 최대 1회이므로, 
Hash를 계산하기 위해 LOB전체 순회를 하는 것은 불필요함.
또한, DBMS가 Hash를 계산하도록 하는 기능이 있더라도 
결국 부담이 DBMS에 추가되어 전체 비교성능을 떨어뜨릴 것. 
(미리 계산된 것이면, SortKey/컬럼비교에 사용하면 되나, 없으면 실시간 계산을 해야 하니)
```

#### 4.설정을 통한 성능 최적화
* 지연적재, 최소계산 방식을 적용하였으므로, SQL문과 설정에 따라 성능의 차이가 날 수 있다.


* 성능 최적화를 위한 Tip
1. 성능결정 요소를 잘 이해해라.
    * DBMS의 처리성능, Network band, 대상 table의 key, Disk I/O를 쓸것인지 여부 등
2. SQL 작성을 잘 해라.
    * key를 적절히 사용하도록 하는 지 확인해라.
    * key가 없다면, where / order by절의 컬럼 순서도 살펴봐라.
3. 중간 Data에 불필요한 정보는 빼도록 해라.
    * 중간 Data의 생성과 해석비용은 생각보다 크다.
    * 불필요한 중간 Data는 버려라. 예를 들면, `같다고 확인된 record`는 보통 필요없을 테니, filter로 제거하면 된다.

#### 지원컬럼 타입

<< 아래 >>
* `Types.xxx`는 java.sql.Types에 정의된 컬럼타입이고,
* `BytesType`은 프로그램내에서 정의한 타입 wrapper이다.
* 타입 wrapper를 사용한 이유는 java.sql.Types에 대응되는 DBMS 타입이 고정적이지 않아서 임.

* Oralce은 기본 타입중 일부를 자신만의 고유한 TypeCode로 사용하는 짓을 한다. (바보짓)

[ 일반 컬럼타입들 ]
```scala
val typeMap: Map[Int, (ColValType, Getter)] = Map(

Types.BIT             -> (BytesType,   getAsBytes),               // -7 oracle.jdbc.OracleTypes.BIT
Types.BOOLEAN         -> (BooleanType, getAsBoolean),             // 16 oracle.jdbc.OracleTypes.BOOLEAN

// check Integer Type
Types.TINYINT         -> (IntType, getAsInt),                     // -6 oracle.jdbc.OracleTypes.TINYINT

Types.SMALLINT        -> (IntType, getAsInt),                     // 5 oracle.jdbc.OracleTypes.SMALLINT
Types.INTEGER         -> (IntType, getAsInt),                     // 4 oracle.jdbc.OracleTypes.INTEGER
Types.BIGINT          -> (LongType, getAsLong),                   // -5  oracle.jdbc.OracleTypes.BIGINT

// check Real Type
Types.FLOAT           -> (DoubleType, getAsDouble),               // 6 oracle.jdbc.OracleTypes.FLOAT
Types.REAL            -> (DoubleType, getAsDouble),               // 7 oracle.jdbc.OracleTypes.REAL
Types.DOUBLE          -> (DoubleType, getAsDouble),               // 8 oracle.jdbc.OracleTypes.DOUBLE
Types.DECIMAL         -> (DecimalType, getAsDecimal),             // 3 oracle.jdbc.OracleTypes.DECIMAL

// check String Type
Types.CHAR            -> (StringType, getAsString),               // 1 oracle.jdbc.OracleTypes.CHAR
Types.VARCHAR         -> (StringType, getAsString),               // 12 oracle.jdbc.OracleTypes.VARCHAR
Types.NCHAR           -> (StringType, getAsString),               // -15 oracle.jdbc.OracleTypes.NCHAR
Types.NVARCHAR        -> (StringType, getAsString),               // -9 oracle.jdbc.OracleTypes.NVARCHAR

// check Long String Type
Types.LONGVARCHAR     -> (LongStringType, getAsLongString),      // -1 oracle.jdbc.OracleTypes.LONGVARCHAR
Types.LONGNVARCHAR    -> (LongStringType, getAsLongString),      // -16 oracle.jdbc.OracleTypes.LONGNVARCHAR

Types.CLOB            -> (LongStringType, getLobAsLongString),   // 2005 oracle.jdbc.OracleTypes.CLOB
Types.NCLOB           -> (LongStringType, getLobAsLongString),   // 2011 oracle.jdbc.OracleTypes.NCLOB

// check Binary Type
Types.BINARY          -> (BytesType, getAsBytes),                // -2 oracle.jdbc.OracleTypes.BINARY
Types.VARBINARY       -> (BytesType, getAsBytes),                // -3 oracle.jdbc.OracleTypes.VARBINARY

// check Long Binary Type
Types.LONGVARBINARY   -> (LongBytesType, getAsLongBytes),        // -4 oracle.jdbc.OracleTypes.LONGVARBINARY

Types.BLOB            -> (LongBytesType, getLobAsLongBytes),     // 2004 oracle.jdbc.OracleTypes.BLOB

// check Time type
Types.DATE            -> (DateType, getAsDate),                   // 91 oracle.jdbc.OracleTypes.DATE
Types.TIME            -> (TimeType, getAsTime),                   // 92 oracle.jdbc.OracleTypes.TIME
Types.TIMESTAMP       -> (TimestampType, getAsTimestamp),         // 93 oracle.jdbc.OracleTypes.TIMESTAMP

Types.TIME_WITH_TIMEZONE        -> (OffTimeType, getAsOffsetTime),              // 2013
Types.TIMESTAMP_WITH_TIMEZONE   -> (OffTimestampType, getAsOffsetDateTime),     // 2014

// idiot oracle :: special mapping code
oracle.jdbc.OracleTypes.TIMESTAMPTZ -> (OffTimestampType, getAsOffsetDateTime),     // -101 :: Types.TIME_WITH_TIMEZONE(2013)
oracle.jdbc.OracleTypes.TIMESTAMPLTZ -> (OffTimestampType, getAsOffsetDateTime),    // -102 :: no matching type
oracle.jdbc.OracleTypes.BINARY_FLOAT -> (DoubleType, getAsDouble),                  // 100  :: no matching type
oracle.jdbc.OracleTypes.BINARY_DOUBLE -> (DoubleType, getAsDouble),                 // 101  :: no matching type

// todo :: support later
//   SQLXML------- // Types.SQLXML // 2009 oracle.jdbc.OracleTypes.SQLXML
//   JSON  ------- //              // 2016 oracle.jdbc.OracleTypes.JSON :: no matching type
//   GEO   ------- //              // not found in oracle.jdbc library. need other oracle library.

)
```

* Type Note
  타입만으로 처리방식을 확정할 수 없는 사례
```scala
    private def numericColType(cs: ColShape): (ColValType, Getter) = {

      require(cs.typeCode == Types.NUMERIC, s"not Numeric type : ${cs}")

      // Types.NUMERIC인 경우, 정수인지, 실수인지 타입만으로 알수 없으므로
      // scale과 precision을 보고 처리할 타입을 정함.
      val ret = if (cs.scale != 0)  { DecimalType -> getAsDecimal }
      else {
        if (cs.precision == 0)      { DecimalType -> getAsDecimal }
        else if (cs.precision > 18) { BigIntType -> getAsBigInt }
        else if (cs.precision <= 9) { IntType -> getAsInt }
        else                        { LongType -> getAsLong }
        ret
      }
    }

```

```scala
// type wrapper
case object BooleanType extends ColValType
case object IntType extends ColValType
case object LongType extends ColValType
case object BigIntType extends ColValType
case object DoubleType extends ColValType
case object DecimalType extends ColValType
case object DateType extends ColValType
case object TimeType extends ColValType
case object TimestampType extends ColValType
case object OffTimeType extends ColValType
case object OffTimestampType extends ColValType
case object StringType extends ColValType
case object BytesType extends ColValType
case object LongStringType extends ColValType   
case object LongBytesType extends ColValType    

```

#### oracle 컬럼타입과 지원 여부
##### Oracle SQL 타입과 java.sql.Types 매핑표 (표준 + 확장 포함)
- **추가필요한 타입은 요청바람**
- `?` 표시한 타입들은 아마도(?) 지원필요할 것 같은 비표준 컬럼타입들.
- Oracle SQL 타입에 `**` 표시한 부분은 jdbc표준과 다른 타입코드 사용함.

| OK  | Oracle SQL 타입                    | 대응되는 `java.sql.Types`                                                                  | 비고 |
|-----|----------------------------------|----------------------------------------------------------------------------------------|------|
|     | **문자형**                          |                                                                                        ||
| O   | CHAR, CHARACTER                  | `Types.CHAR`                                                                           | 고정길이 문자열 |
| O   | VARCHAR2, VARCHAR                | `Types.VARCHAR`                                                                        | 가변길이 문자열 |
| O   | LONG                             | `Types.LONGVARCHAR`                                                                    | 매우 긴 문자열 |
| O   | NCHAR                            | `Types.NCHAR`                                                                          | 유니코드 고정 문자열 |
| O   | NVARCHAR2                        | `Types.NVARCHAR`                                                                       | 유니코드 가변 문자열 |
| O   | CLOB                             | `Types.CLOB`                                                                           | 문자 대용량 객체 |
| O   | NCLOB                            | `Types.NCLOB`                                                                          | 유니코드 문자 대용량 객체 |
|     | **숫자형**                          |                                                                                        ||
| O   | NUMBER, DECIMAL, NUMERIC         | `Types.NUMERIC`, `Types.DECIMAL`, 또는 `Types.INTEGER`, `Types.DOUBLE`, `Types.BIGINT` 등 | 정밀도/스케일에 따라 매핑 |
| O   | FLOAT                            | `Types.FLOAT`                                                                          | 부동소수점 |
| O   | DOUBLE PRECISION                 | `Types.DOUBLE`                                                                         | 배정밀도 부동소수점 |
| O   | REAL                             | `Types.REAL`                                                                           | 단정밀도 부동소수점 |
| O   | BINARY_FLOAT`**`                  | `Types.FLOAT`                                                                          | 32비트 IEEE 부동소수점 (Oracle 확장) |
| O   | BINARY_DOUBLE`**`                  | `Types.DOUBLE`                                                                         | 64비트 IEEE 부동소수점 (Oracle 확장) |
|     | **날짜/시간형**                       |                                                                                        ||
| O   | DATE                             | `Types.DATE` 또는 `Types.TIMESTAMP`                                                      | Oracle DATE는 시간 포함 |
| O   | TIMESTAMP                        | `Types.TIMESTAMP`                                                                      | 초 단위 이하 정밀도 포함 |
| O   | TIMESTAMP WITH TIME ZONE`**`       | `Types.TIMESTAMP_WITH_TIMEZONE`.                                                       | 타임존 포함 |
| O   | TIMESTAMP WITH LOCAL TIME ZONE`**` | `Types.TIMESTAMP_WITH_LOCAL_TIMEZONE`                                                  | 세션 타임존 기준 |
| ?   | INTERVAL YEAR TO MONTH           | `Types.OTHER`                                                                          | Oracle 고유 타입 |
| ?   | INTERVAL DAY TO SECOND           | `Types.OTHER`                                                                          | Oracle 고유 타입 |
|     | **이진형**                          |                                                                                        ||
| O   | RAW                              | `Types.BINARY` 또는 `Types.VARBINARY`                                                    | 이진 데이터 |
| O   | LONG RAW                         | `Types.LONGVARBINARY`                                                                  | 긴 이진 데이터 |
| O   | BLOB                             | `Types.BLOB`                                                                           | 이진 대용량 객체 |
| ?   | BFILE                            | `Types.OTHER` 또는 `OracleTypes.BFILE`                                                   | 외부 파일 참조 (읽기 전용) |
|     | **객체 및 컬렉션형**                    |                                                                                        ||
|     | OBJECT, STRUCT (사용자 정의 타입)       | `Types.STRUCT`                                                                         | 사용자 정의 객체 |
|     | VARRAY, NESTED TABLE             | `Types.ARRAY`                                                                          | 컬렉션 타입 |
|     | REF                              | `Types.REF`                                                                            | 객체 참조 타입 |
|     | REF CURSOR                       | `Types.REF_CURSOR`                                                                     | 결과셋을 반환하는 커서 (JDBC 4.1+) |
|     | ANYTYPE / ANYDATA / ANYDATASET   | `Types.OTHER`                                                                          | Oracle 전용 동적 타입 |
| ?   | XMLTYPE                          | `Types.SQLXML` (JDBC 4.0+) 또는 `Types.CLOB`                                             | XML 데이터 (DOM 혹은 문자열 형태) |
| ?   | JSON                             | `Types.SQLJSON` (JDBC 4.3+) 또는 `Types.CLOB` / `Types.VARCHAR`                          | Oracle 21c 이상에서 공식 지원 |
|     | UROWID / ROWID                   | `Types.OTHER` 또는 `OracleTypes.ROWID`                                                   | 고유 로우 식별자 |
|     | **공간(Spatial) / 위치정보형**          |                                                                                        ||
| ?   | SDO_GEOMETRY                     | `Types.STRUCT`                                                                         | Oracle Spatial 타입 (좌표, 지오메트리 구조체) |
| ?   | SDO_TOPO_GEOMETRY                | `Types.STRUCT`                                                                         | 위상지오메트리 |
| ?   | SDO_GEORASTER                    | `Types.STRUCT`                                                                         | 영상 데이터 |
| ?   | SDO_POINT_TYPE                   | `Types.STRUCT`                                                                         | 좌표값 구조체 |
|     | **기타 특수형**                       |                                                                                        ||
|     | URITYPE, HTTPURITYPE             | `Types.VARCHAR`                                                                        | URI 문자열 |
|     | OPAQUE (예: SYS.ANYDATA, ORDAudio 등) | `Types.OTHER`                                                                          | Oracle 확장 바이너리 타입 |
|     | MLSLABEL                         | `Types.VARCHAR`                                                                        | Oracle Label Security용 |
|     | RAW MLSLABEL                     | `Types.BINARY`                                                                         | 보안 라벨 RAW 버전 |

* 참고 : 타입의 중요성과 타입지원의 의미

> URI는 uri-encoding으로 같은 값이 다른 문자열로 저장될 수 있다.
>
> URI타입인지 확인하는 기능은 (아직) 구현되어 있지 않으므로,  
> 두 컬럼이 URITYPE 타입인 경우, Types.VARCHAR로 인식하여 문자열 비교를 하게 된다.
> ( 희박하겠으나, 있을 수 있는 일)
>
> 마찬가지로, URI타입컬럼(현 버전은 Types.VARCHAR로 인식)과 문자열 컬럼을 비교도  
> 문자열 비교방식으로 비교한다. (즉, 틀릴 수 있는 비교를 하게 된다)

#### FAQ ?
1. **DB의 encoding ?**
  > 영향받지 않음. (jdbc/JVM의 encoding 방식에 따름 : UTF-16)   
  > endian ? 당연히 영향 받지 않음 (질문자체가 non-sense)
   
2. select `*` from table 같은 **동적 조건을 제한하는 이유**
  > 동적으로 결정되는 정보는 `그 정보가 항상 일관되게 같다`라는 가정이 충족되어야 한다. (멱등성이라고 한다.)   
  > 이런 류의 편의 기능은 UI 수준에서 제공되어야 하는 기능이고, core기능에서는 허용하면 안되는 기능이다.(매우 상식적인 사항)  

3. **비교결과의 Json 출력 참고**
  > **NDJson** (New-line Delimited Json)임. ( Json은 문자열 필드에 줄바꿈을 직접 삽입하지 못하기 때문에 가능한 방식)  
  > 따라서 모든 New-line의 **시작점**은 유형을 나타내는 같은 문자열로 시작된다고 봐도 된다.   
  > 즉, grep으로 filtering하시면 된다는 것.(우연히 같은 내용이 중간에 등장할까 걱정할 필요없이..)

# 프로그램 사용방법
* 옵션 등은 달라질 수 있음

## << 사용방법 >>
```
java -jar TableDiff_0.4.jar [실행옵션]

  -c, --conf <file>    config file path. format: HOCON (required)
  -o, --out <format> one of [json | bin] (required) <<< (compare) 비교결과를 출력하라는 것.
  -i  --in <format>   one of [json | bin]           <<  (applyTo) format으로 지정한 유형의 입력을 처리하라는 것
  -f  --from <file>    apply data from file-path(when omitted, from stdin)
  -h, --help             show this help message
```
### * 옵션 주의사항
```
   1. --in 과 --out 중 하나, --conf는 반드시 주어야 함.
   2. --in 과 --out 는 동시에 쓸 수 없음. (한가지 용도로 동작)
```

### 1) 비교할 때
```
    java -jar TableDiff_0.4.jar -c configFile -o json > 저장할_파일
```
* 비교 결과를 stdout으로 출력

####  * 이스터 에그 (?)
```
    java -jar TableDiff_0.4.jar -c _sample -o json 
```
* 프로그램내에 포함된 샘플 설정파일로 비교 진행 ( _sample은 매직워드 )

### 2) 적용할 때
```
    java -jar TableDiff_0.4.jar -c configFile -i json -f dataFile
```

* dataFile은 앞 1) 에서 저장한 파일
* -f 옵션을 안주면, stdin으로 입력받는 것으로 작업


#### * 설정에서 
```
      use.db = "tableA" --> tableA에서 사용한 DB접속정보 사용
      use.db = "tableB" --> tableA에서 사용한 DB접속정보 사용
      use.db = "mock"  --> DB접속없이 시뮬레이션 한 결과 출력 (mock-up DB)
```

#### * 이스터 에그 (?)
```
    java -jar TableDiff_0.4.jar -c _sample -o json -f _sample
```
* 프로그램내에 포함된 샘플 Data파일과 mockup-DB를 이용하여 후처리 진행 표시





     :: 프로그램내에 포함된 샘플 Data파일과 mockup-DB를 이용하여 applyTo 진행
     ( _sample은 매직워드 )
