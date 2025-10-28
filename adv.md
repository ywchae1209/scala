

# [ 요건 ]
두 개의 DB_A과 DB_B 있는 TableA과 TableB의 Row들을 비교하는 기능

* 참고: 비유
> 두 시점의 거주자 조사결과 목록비교를 하는 것처럼 생각해볼 수 있다.
>
> 두개의 거주자 목록이 있는데, 목록은 집주소 순서로 정렬되어 있다.
> ( 두 목록의 집주소 정렬방식은 동일)
>
> 이때,
> 정렬키 == 집주소, 컬럼비교 == 거주자로 비유해서 생각하면 쉬을 듯.  
> 집주소 같고, 거주자도 같으면 변동없음.   
> 집주소 같은데, 거주자 다르면 변동됨.  
> 집주소 다르면, 다른 집


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


## 3.	비교결과의 분류

|           | same SortKey       | different SortKey  |
|-----------|--------------------|--------------------|
| same cols | same               | update             |
| diff cols | onlyInA or onlyInB | onlyInA or onlyInB |


설명:
정렬키가 같고, 컬럼비교가 다른 경우는 RowA가 RowB로 변경되었음을 의미.

# 입출력
## [ 입력 ]
아래의 입력형식은 예시이며, 바뀔 수 있음.

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
* 후처리:: 추가 필요 ( todo )

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


* 성능 최적화를 위한 Tip (todo)
```
몇자 써볼 예정
```


#### 지원컬럼 타입

<< 아래 >>
* `Types.xxx`는 java.sql.Types에 정의된 컬럼타입이고,
* `BytesType`은 프로그램내에서 정의한 타입 wrapper이다.
* 타입 wrapper를 사용한 이유는 java.sql.Types에 대응되는 DBMS 타입이 고정적이지 않아서 임.

```scala
// 지원하는 java.sql.Types
      Types.BIT             -> (BytesType,   getAsBytes),
      Types.BOOLEAN         -> (BooleanType, getAsBoolean),

      // check Integer Type
      Types.TINYINT         -> (IntType, getAsInt),
      Types.SMALLINT        -> (IntType, getAsInt),
      Types.INTEGER         -> (IntType, getAsInt),
      Types.BIGINT          -> (LongType, getAsLong), // BigInt --> Long

      // check Real Type
      Types.FLOAT           -> (DoubleType, getAsDouble),
      Types.REAL            -> (DoubleType, getAsDouble),
      Types.DOUBLE          -> (DoubleType, getAsDouble),

      Types.DECIMAL         -> (DecimalType, getAsDecimal),

      // check String Type
      Types.CHAR            -> (StringType, getAsString),
      Types.VARCHAR         -> (StringType, getAsString),
      Types.NCHAR           -> (StringType, getAsString),
      Types.NVARCHAR        -> (StringType, getAsString),

      // check Long String Type
      Types.LONGVARCHAR     -> (LongStringType, getAsLongString),
      Types.LONGNVARCHAR    -> (LongStringType, getAsLongString),

      Types.CLOB            -> (LongStringType, getLobAsLongString),
      Types.NCLOB           -> (LongStringType, getLobAsLongString),

      // check Binary Type
      Types.BINARY          -> (BytesType, getAsBytes),
      Types.VARBINARY       -> (BytesType, getAsBytes),

      // check Long Binary Type
      Types.LONGVARBINARY   -> (LongBytesType, getAsLongBytes),
      Types.BLOB            -> (LongBytesType, getLobAsLongBytes),

      // check Time type
      Types.DATE            -> (DateType, getAsDate),
      Types.TIME            -> (TimeType, getAsTime),
      Types.TIMESTAMP       -> (TimestampType, getAsTimestamp),

      Types.TIME_WITH_TIMEZONE        -> (OffTimeType, getAsOffsetTime),
      Types.TIMESTAMP_WITH_TIMEZONE   -> (OffTimestampType, getAsOffsetDateTime),
```

* Type Note
  타입만으로 처리방식을 확정할 수 없는 사례
```scala
   // Types.NUMERIC인 경우, 정수인지, 실수인지 타입만으로 알수 없으므로
   // scale과 precision을 보고 처리할 타입을 정함.
    private def numericColType(cs: ColShape): (ColValType, Getter) = {

      require(cs.typeCode == Types.NUMERIC, s"not Numeric type : ${cs}")

      val ret = if (cs.scale != 0)  { DecimalType -> getAsDecimal }
      else {
        if (cs.precision == 0)      { DecimalType -> getAsDecimal }
        else if (cs.precision > 18) { BigIntType -> getAsBigInt }
        else if (cs.precision <= 9) { IntType -> getAsInt }
        else                        { LongType -> getAsLong }
      }
      ret
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
case object LongStringType extends ColValType   // todo
case object LongBytesType extends ColValType    // todo

```

#### oracle 컬럼타입과 지원 여부
##### Oracle SQL 타입과 java.sql.Types 매핑표 (표준 + 확장 포함)
- **추가필요한 타입은 요청바람**

| OK  | Oracle SQL 타입                       | 대응되는 `java.sql.Types` | 비고 |
|-----|-------------------------------------|----------------------------|------|
|     | **문자형**                             |||
| O   | CHAR, CHARACTER                     | `Types.CHAR` | 고정길이 문자열 |
| O   | VARCHAR2, VARCHAR                   | `Types.VARCHAR` | 가변길이 문자열 |
| O   | LONG                                | `Types.LONGVARCHAR` | 매우 긴 문자열 |
| O   | NCHAR                               | `Types.NCHAR` | 유니코드 고정 문자열 |
| O   | NVARCHAR2                           | `Types.NVARCHAR` | 유니코드 가변 문자열 |
| O   | CLOB                                | `Types.CLOB` | 문자 대용량 객체 |
| O   | NCLOB                               | `Types.NCLOB` | 유니코드 문자 대용량 객체 |
|     | **숫자형**                             |||
| O   | NUMBER, DECIMAL, NUMERIC            | `Types.NUMERIC`, `Types.DECIMAL`, 또는 `Types.INTEGER`, `Types.DOUBLE`, `Types.BIGINT` 등 | 정밀도/스케일에 따라 매핑 |
| O   | FLOAT                               | `Types.FLOAT` | 부동소수점 |
| O   | DOUBLE PRECISION                    | `Types.DOUBLE` | 배정밀도 부동소수점 |
| O   | REAL                                | `Types.REAL` | 단정밀도 부동소수점 |
| O   | BINARY_FLOAT                        | `Types.FLOAT` | 32비트 IEEE 부동소수점 (Oracle 확장) |
| O   | BINARY_DOUBLE                       | `Types.DOUBLE` | 64비트 IEEE 부동소수점 (Oracle 확장) |
|     | **날짜/시간형**                          |||
| O   | DATE                                | `Types.DATE` 또는 `Types.TIMESTAMP` | Oracle DATE는 시간 포함 |
| O   | TIMESTAMP                           | `Types.TIMESTAMP` | 초 단위 이하 정밀도 포함 |
| O   | TIMESTAMP WITH TIME ZONE            | `Types.TIMESTAMP_WITH_TIMEZONE` | 타임존 포함 |
| O   | TIMESTAMP WITH LOCAL TIME ZONE      | `Types.TIMESTAMP_WITH_LOCAL_TIMEZONE` | 세션 타임존 기준 |
|     | INTERVAL YEAR TO MONTH              | `Types.OTHER` | Oracle 고유 타입 |
|     | INTERVAL DAY TO SECOND              | `Types.OTHER` | Oracle 고유 타입 |
|     | **이진형**                             |||
| O   | RAW                                 | `Types.BINARY` 또는 `Types.VARBINARY` | 이진 데이터 |
| O   | LONG RAW                            | `Types.LONGVARBINARY` | 긴 이진 데이터 |
| O   | BLOB                                | `Types.BLOB` | 이진 대용량 객체 |
|     | BFILE                               | `Types.OTHER` 또는 `OracleTypes.BFILE` | 외부 파일 참조 (읽기 전용) |
|     | **객체 및 컬렉션형**                       |||
|     | OBJECT, STRUCT (사용자 정의 타입)          | `Types.STRUCT` | 사용자 정의 객체 |
|     | VARRAY, NESTED TABLE                | `Types.ARRAY` | 컬렉션 타입 |
|     | REF                                 | `Types.REF` | 객체 참조 타입 |
|     | REF CURSOR                          | `Types.REF_CURSOR` | 결과셋을 반환하는 커서 (JDBC 4.1+) |
|     | ANYTYPE / ANYDATA / ANYDATASET      | `Types.OTHER` | Oracle 전용 동적 타입 |
|     | XMLTYPE                             | `Types.SQLXML` (JDBC 4.0+) 또는 `Types.CLOB` | XML 데이터 (DOM 혹은 문자열 형태) |
|     | JSON                                | `Types.SQLJSON` (JDBC 4.3+) 또는 `Types.CLOB` / `Types.VARCHAR` | Oracle 21c 이상에서 공식 지원 |
|     | UROWID / ROWID                      | `Types.OTHER` 또는 `OracleTypes.ROWID` | 고유 로우 식별자 |
|     | **공간(Spatial) / 위치정보형**             |||
|     | SDO_GEOMETRY                        | `Types.STRUCT` | Oracle Spatial 타입 (좌표, 지오메트리 구조체) |
|     | SDO_TOPO_GEOMETRY                   | `Types.STRUCT` | 위상지오메트리 |
|     | SDO_GEORASTER                       | `Types.STRUCT` | 영상 데이터 |
|     | SDO_POINT_TYPE                      | `Types.STRUCT` | 좌표값 구조체 |
|     | **기타 특수형**                          |||
|     | XMLType                             | `Types.SQLXML` 또는 `Types.CLOB` | XML 문서 저장용 |
|     | JSON                                | `Types.SQLJSON` 또는 `Types.CLOB` / `Types.VARCHAR` | JSON 문서 저장용 |
|     | URITYPE, HTTPURITYPE                | `Types.VARCHAR` | URI 문자열 |
|     | OPAQUE (예: SYS.ANYDATA, ORDAudio 등) | `Types.OTHER` | Oracle 확장 바이너리 타입 |
|     | MLSLABEL                            | `Types.VARCHAR` | Oracle Label Security용 |
|     | RAW MLSLABEL                        | `Types.BINARY` | 보안 라벨 RAW 버전 |
