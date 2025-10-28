

# [ 요건 ]
두 개의 DB_A과 DB_B 있는 TableA과 TableB의 Row들을 비교하는 기능


# [ 전제와 제약 ]
## 1.	비교대상
```
SQL을 통해 얻어진 ResultSetA와 ResultSetB를 비교한다..
```

- DBMS의 정렬기능으로 정렬된 ResultSet을 비교함.
     ( 비교대상 건수가 매우 클 수 있으므로, Memory적재불가하기 때문)
- 비교성능의 상당부분은 DBMS의 정렬성능과 데이터의 전송속도에 영향 받음

## 2.	비교조건
### 1)	Order by 절의 조건(이하 SortKey ; 정렬조건)
```
ResultSetA과 ResultSetB는 **같은 SortKey 조건**으로 정렬되어야 한다.
```
- 정렬된 두 집합비교이므로, 같은 정렬 조건이어야 함. ( 각기 다른 정렬조건이면 비교 불가)

```
ResultSetA의 SortKey들은 **ResultSetA내에서 unique**해야 한다.
```

- 즉, SortKey는 특정 Row를 유일하게 식별할 수 있는 정보여야 함.
- ResultSetA와 ResultSetB의 각 대응하는 행들을 1:1 비교하므로 인한 제약

### 2)	Equals 조건  (이하 컬럼비교)
```
Order by 절에서 사용된 컬럼들 외에 RowA와 RowB에서 같기를 기대하는 컬럼쌍을 말한다.
```

* 참고
> 정렬키 == 집주소, 컬럼비교 == 거주자로 비유해서 생각하면 쉬을 듯.  
> 집주소 같고, 거주자도 같으면 변동없음.   
> 집주소 같은데, 거주자 다르면 변동됨.  
> 집주소 다르면, 다른 집  
>
> 목록1과 목록2는  일정한 규칙의 집주소순서로 정렬되어 있음.


## 3.	비교결과의 분류

|           | same SortKey       | different SortKey  |
|-----------|--------------------|--------------------|
| same cols | same               | update             |
| diff cols | onlyInA or onlyInB | onlyInA or onlyInB |


설명:
정렬키가 같고, 컬럼비교가 다른 경우는 RowA가 RowB로 변경되었음을 의미.

# 입출력
## [ 입력 ]

### 1.	DB접속 정보 : DB1용, DB2용 2개

두 DB에 접속할 수 있는 정보
```scala
      /*
      driverClassName = "oracle.jdbc.OracleDriver",
      jdbcUrl         = "jdbc:oracle:thin:@//172.16.0.51:1521/orclpdb",
      username        = "cdctest",
      password        = "cdctest"
      */
      case class DatabaseConfig(driverClassName: String,
      jdbcUrl: String,
      username: String,
      password: String)

```

### 2.	SQL문 2개 : ResultSetA용, ResultSetB 용
각 DB에서 비교대상 ResultSet을 질의할 수 있는 질의문 2개

```sql
SELECT department_id, name, salary4
FROM employees
ORDER BY department_id ASC, salary DESC;
```
* where절 등 SQL은 사용자 임의 추가가능.
* order by절의 컬럼들도 select절에 나열해야 함.

### 3.	SortKey 컬럼 조건쌍
```json
[
  { "ColA_Index": 1,"Col_Index": 1, "ascending" : true, "nullAsSmallest": true, "ToleranceMillis":  25},
  { "ColA_Index": 2,"Col_Index": 2, "ascending" : false, "nullAsSmallest": false }
  
]
```
* 위는 SortKey에 사용된 컬럼쌍 2개를 지정한 것.
* 컬럼쌍의 순서는 **order by절에서 사용한 순서**에 따라야 함.

### 4.	컬럼비교 조건쌍

```json
[
  { "ColA_Index": 3,"Col_Index": 3},
  { "ColA_Index": 4,"Col_Index": 4}
]
```
* 위는 컬럼비교를 진행할 컬럼쌍 2개를 지정한 것.

** 컬럼쌍의 순서는 임의로 정해도 됨.

## [ 출력 ]

아래와 같이 분류된 결과를 포함한 data를 print-out

```scala
case class OnlyInA[T](row: T) extends Result[T]
case class OnlyInB[T](row: T) extends Result[T]
case class Update[T](rowA: T, rowB: T) extends Result[T]
case class Same[T](rowA: T, rowB: T) extends Result[T]
```

* Json, Bson 등으로 출력하도록 할 예정
* 파일로 기록하는 기능은 shell의 redirect를 사용하면 될테니.. 굳이 넣을 필요 없을 듯.

## [ 후처리 ]
* 후처리:: 추가 필요 ( Todo )

### 주의: SQL문으로 치환할 수 없는 데이터 처리
```
LOB컬럼의 insert는 SQL문에 직접 쓸 수 없고, 
1. program에서 처리하거나
2. PL/SQL로 처리(파일로 써놓고 해당컬럼으로 읽어들이도록 하는 식)
해야 함
```
* 파일 여러개로 처리할 경우, 관리문제 예상되므로, Data파일내에 두고 program으로 처리하는 방식으로 구현할 예정
* 파일 size문제로 binary형식의 보관을 해야 함 ( Bson같은 포맷으로 )
* 이 경우, 별도 viewer 내지 editor필요할지 따져봐야 함. ( Bson은 표준적 포맷이라 있을 법도 하고.)

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

### (정렬조건과 컬럼비교에서) 각 대응컬럼들은 같은 타입이어야 한다.

* SQL1과 SQL2의 비교컬럼쌍은 같은 타입이어야 한다. (당연한 얘기)
* SortKey1과 SortKey2는 같은 컬럼순서여야 한다. ( c1-c2.. == c1'-c2'..)


* 설명) 약간의 허용 : 아래의 경우, 정렬순서 문제는 없으므로
``` 
SQL1과 SQL2의 대응컬럼타임이 
정수형(Short) vs 정수형(Long)이면 타입변환하지 않아도 된다.

마찬가지로, 
실수형 vs 실수형일 경우 타입변환하지 않아도 된다.
```

## 2. 정렬 조건

```
프로그램은 정렬조건에 따른 정렬 순서를 알아야 한다.
```

* SortKeyA와 SortKeyB가 다를 경우, 작은 쪽의 SortKey를 이동하면서 일치하는 상대방 SortKey를 찾아야 하므로. 
* 이를 위해, 오름차순/내림차수, Null값의 정렬순서 등 정렬순서 파악을 위한 설정이 필요했다.

## 비교

### 허용오차
```
  대응하는 컬럼의 타입이 실수형 또는 시각(Time)형인 경우, 허용오차를 지정할 수 있다.
```
* 같은 실수(real number)라도 DBMS에 따라 다른 값을 저장하는 경우(DB 내의 저장방식에 따라 다름) 있으며, 
* 시각도 nano-second, milli-second 단위저장 등 저장방식에 따라 다른 값을 저장하는 경우가 있다.


* 아래와 같은 단위로 허용오차를 정할 수 있다. (SortKey쌍, 컬럼비교 쌍 모두)
```scala
case class DeltaMillis(milli: Int) // 밀리단위로 허용오차 표기
case class DeltaDouble(delta: Double) // 실수로 허용오차 표기
```

# 성능/Memory 최적화 및 제한

## 제한
#### 동시성 처리의 제한
``` 
ResultSet은 Cursor타입의 자료형으로, 현재 access가능한 row는 1개로 한정됨.
따라서, 구획에 따른 동시처리는 불가능.
```
(ResultSetA와 ResultSetB의 비교를) 일정 묶음단위로로 쪼개서 비교하는 동시적 처리는 불가능하다는 뜻

동시처리가 필요하다면, `where절`로 Table의 범위를 나누어서 실행하자.

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
```
몇자 써볼 예정
```


