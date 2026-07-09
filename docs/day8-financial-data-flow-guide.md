# Day 8 Financial Data Flow Guide

이 문서는 Day 8 구현이 어떤 흐름으로 작동하는지, 각 클래스가 어떤 책임을 갖는지 설명한다. Day 8의 목적은 위험 점수를 계산하는 것이 아니라 Day 9 위험탐지에 필요한 재무제표 지표와 부채 만기 데이터를 저장할 기반을 만드는 것이다.

## 1. 전체 목적

Day 8은 크게 두 종류의 데이터를 다룬다.

- DART 재무제표 데이터: 매출, 영업이익, 순이익, 부채총계, 자본총계, 현금, 영업현금흐름, 이자비용 등을 수집하고 `financial_metrics`에 저장한다.
- 부채 만기 데이터: 채권, CP, ABSTB, 대출, 단기차입 만기 정보를 `debt_maturities`에 저장한다.

정확한 리츠 특화 이벤트, 차환 실패, 캐시트랩, 기한이익상실, 회생절차, 공시/뉴스 키워드 파싱은 Day 11 범위로 미뤘다. Day 8은 데이터 수집, 저장, 조회, 비동기 처리 기반까지만 담당한다.

## 2. 큰 흐름

```text
사용자
  |
  | POST /api/financial-metrics/fetch
  v
FinancialMetricController
  |
  v
FinancialDataRequestService
  |
  | 1. 사용자 존재 확인
  | 2. assetId 또는 stockCode로 기존 Asset 조회
  | 3. FinancialCollectionLog REQUESTED 생성
  | 4. Kafka financial-data-fetch-requested 발행
  v
Kafka
  |
  v
FinancialDataFetchConsumer
  |
  v
FinancialDataCollectionService
  |
  | 1. 로그 RUNNING 전환
  | 2. stockCode -> corpCode 조회
  | 3. DART CFS 재무제표 호출
  | 4. CFS 결과가 비면 OFS fallback
  | 5. 원본 DART 응답 S3 저장
  | 6. DART 응답 정규화
  | 7. FinancialMetric upsert
  | 8. 로그 COMPLETED 또는 FAILED 처리
  v
financial-data-fetched 또는 financial-data-fetch-failed
```

부채 만기 샘플 CSV import는 별도 흐름이다.

```text
사용자
  |
  | POST /api/financial-metrics/debt-maturities/import-sample
  v
FinancialMetricController
  |
  v
DebtMaturityService
  |
  | 1. classpath sample CSV 읽기
  | 2. 기존 S3 bucket에 sample CSV 저장
  | 3. asset_ticker로 기존 Asset 조회
  | 4. 중복이 없으면 DebtMaturity 저장
  v
debt_maturities
```

샘플 CSV는 실제 투자 데이터가 아니며 위험탐지 로직 테스트용 데이터다.

## 3. API

### POST `/api/financial-metrics/fetch`

DART 재무제표 수집을 요청한다. 실제 수집을 HTTP 요청 안에서 끝내지 않고 Kafka 작업으로 넘긴다.

요청 예시:

```json
{
  "assetId": 1,
  "year": 2024,
  "quarter": 4
}
```

또는:

```json
{
  "stockCode": "005930",
  "year": 2024,
  "quarter": 4
}
```

응답은 HTTP 202이며, 수집 완료가 아니라 작업 접수 완료를 의미한다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request succeeded.",
  "data": {
    "jobId": "uuid",
    "status": "REQUESTED"
  }
}
```

주의사항:

- `assetId`와 `stockCode` 둘 중 하나는 필요하다.
- Asset은 자동 생성하지 않는다.
- stockCode에 해당하는 Asset이 없으면 `ASSET_NOT_FOUND`로 실패한다.
- DART API key는 `DART_API_KEY` 환경변수로만 읽는다.

### GET `/api/financial-metrics/{assetId}`

특정 Asset의 재무지표 목록을 조회한다. 정렬은 최신 연도/분기 우선이다.

### GET `/api/financial-metrics/{assetId}/debt-maturities`

특정 Asset의 부채 만기 목록을 조회한다. 정렬은 만기일 오름차순이다.

### POST `/api/financial-metrics/debt-maturities/import-sample`

개발/테스트용 샘플 부채 만기 CSV를 import한다. 같은 `(asset_id, maturity_date, amount, debt_type)` 조합은 중복 저장하지 않는다.

## 4. DB 구조

### `financial_metrics`

DART 재무제표 응답에서 정규화한 핵심 재무지표를 저장한다.

주요 컬럼:

- `asset_id`: 기존 `assets.id` FK
- `year`, `quarter`: 사업연도와 분기
- `revenue`: 매출
- `operating_income`: 영업이익
- `net_income`: 당기순이익
- `total_debt`: 부채총계
- `total_equity`: 자본총계
- `cash`: 현금및현금성자산
- `operating_cash_flow`: 영업활동현금흐름
- `interest_expense`: 이자비용
- `debt_ratio`: 부채비율
- `interest_coverage_ratio`: 이자보상배율

`(asset_id, year, quarter)` unique constraint가 있어서 같은 분기 데이터는 upsert된다.

### `debt_maturities`

채권/부채 만기 정보를 저장한다.

주요 컬럼:

- `asset_id`
- `maturity_date`
- `amount`
- `debt_type`
- `interest_rate`
- `currency`
- `is_short_term`

지원하는 `DebtType`:

- `BOND`
- `CP`
- `ABSTB`
- `LOAN`
- `SHORT_TERM_BORROWING`

### `dart_corp_codes`

DART 고유번호 API에서 받은 corp code를 캐시한다.

주요 컬럼:

- `corp_code`
- `corp_name`
- `stock_code`
- `modify_date`

Day 8은 corp code를 하드코딩하지 않는다. stock code로 corp code를 찾기 위해 이 캐시를 사용한다.

### `financial_collection_logs`

Day 8 전용 수집 로그다. 기존 Day 5 `collection_logs`는 시장가격 수집 의미가 강해서 억지로 재사용하지 않았다.

주요 컬럼:

- `job_id`
- `asset_id`
- `requested_by_user_id`
- `stock_code`
- `corp_code`
- `year`
- `quarter`
- `statement_division`: 최종 사용한 재무제표 구분, `CFS` 또는 `OFS`
- `fallback_used`: CFS가 비어서 OFS로 fallback했는지 여부
- `status`: `REQUESTED`, `RUNNING`, `COMPLETED`, `FAILED`
- `raw_s3_path`
- `message`

## 5. DART 연동 흐름

### 5.1 API key 설정

설정 위치:

```yaml
app:
  dart:
    base-url: ${DART_BASE_URL:https://opendart.fss.or.kr}
    api-key: ${DART_API_KEY:}
```

Docker compose backend 환경변수에도 `DART_API_KEY`가 전달된다. 키 값은 코드에 하드코딩하지 않는다.

### 5.2 corp code 조회

`DartCorpCodeService.findCorpCodeByStockCode()`가 담당한다.

흐름:

```text
stockCode 입력
  |
  v
dart_corp_codes 캐시 조회
  |
  | 있으면 corpCode 반환
  |
  | 없으면
  v
DartClient.downloadCorpCodeXml()
  |
  v
S3 dart-corp-codes/corp_codes.xml 저장
  |
  v
DartCorpCodeParser.parse()
  |
  v
dart_corp_codes upsert
  |
  v
stockCode로 다시 조회 후 corpCode 반환
```

삼성전자 `005930 -> 00126380`은 테스트 검증용 참고값으로만 사용한다.

### 5.3 재무제표 조회

`DartClient.fetchFinancialStatement()`가 담당한다.

분기별 DART report code:

- 1분기: `11013`
- 2분기: `11012`
- 3분기: `11014`
- 4분기: `11011`

조회 순서:

```text
CFS 호출
  |
  | list가 있으면 CFS 사용
  |
  | list가 비어 있으면
  v
OFS 호출
  |
  | list가 있으면 OFS 사용, fallbackUsed=true
  |
  | 그래도 비어 있으면 실패
```

`statementDivision`과 `fallbackUsed`는 `financial_collection_logs`와 S3 raw envelope에 남는다.

## 6. S3 저장 구조

새 버킷은 만들지 않고 기존 `app.market-data.s3` 설정을 재사용한다. prefix만 분리한다.

저장 prefix:

- DART 재무제표 원본: `financial-statements/{assetId}/{year}/{quarter}/raw.json`
- corp code XML: `dart-corp-codes/corp_codes.xml`
- 샘플 부채 만기 CSV: `debt-maturity/sample/debt_maturity_sample.csv`

재무제표 raw JSON envelope에는 다음 metadata가 들어간다.

- `assetId`
- `stockCode`
- `corpCode`
- `year`
- `quarter`
- `statementDivision`
- `fallbackUsed`
- `fetchedAt`
- `payload`

## 7. 정규화와 계산

`DartFinancialStatementNormalizer`가 DART 응답의 `list` 배열에서 계정명을 찾아 `FinancialMetricValues`로 바꾼다.

찾는 주요 계정명:

- 매출: `매출액`, `영업수익`, `수익(매출액)`
- 영업이익: `영업이익`, `영업손실`
- 순이익: `당기순이익`, `당기순손익`, `분기순이익`, `반기순이익`
- 부채총계: `부채총계`
- 자본총계: `자본총계`
- 현금: `현금및현금성자산`, `현금 및 현금성자산`
- 영업현금흐름: `영업활동현금흐름`, `영업활동으로 인한 현금흐름`
- 이자비용: `이자비용`, `금융원가`, `금융비용`

금액 파싱:

- 쉼표는 제거한다.
- `(50)` 같은 괄호 표기는 `-50`으로 처리한다.
- 값이 없으면 null로 둔다.

계산:

```text
debt_ratio = total_debt / total_equity * 100
interest_coverage_ratio = operating_income / interest_expense
```

분모가 없거나 0이면 비율은 null이다. Day 8은 이 값을 저장만 하고 위험 등급은 계산하지 않는다.

## 8. Kafka 흐름

### Topic

- `financial-data-fetch-requested`
- `financial-data-fetched`
- `financial-data-fetch-failed`

### Producer

`FinancialDataEventPublisher`가 담당한다.

- requested 이벤트는 broker ack를 최대 5초 기다린다.
- fetched/failed 이벤트는 비동기로 발행한다.

### Consumer

`FinancialDataFetchConsumer`가 `financial-data-fetch-requested`를 구독한다.

consumer group:

```text
finrisk-financial-data-collector
```

성공 시 `FinancialDataFetchedEvent`를 발행하고, 실패 시 `FinancialDataFetchFailedEvent`를 발행한다.

## 9. 클래스별 의미

### API/DTO

| 클래스 | 의미 |
| --- | --- |
| `FinancialMetricController` | Day 8 REST API 진입점 |
| `FinancialMetricFetchRequest` | fetch 요청 DTO. `assetId` 또는 `stockCode`, `year`, `quarter`를 받는다. |
| `FinancialMetricFetchResponse` | fetch 요청 접수 응답. `jobId`, `status`를 반환한다. |
| `FinancialMetricResponse` | 저장된 재무지표 조회 응답 |
| `DebtMaturityResponse` | 부채 만기 조회 응답 |
| `DebtMaturityImportResponse` | 샘플 CSV import 결과 응답 |

### 요청/수집 서비스

| 클래스 | 의미 |
| --- | --- |
| `FinancialDataRequestService` | HTTP 요청을 검증하고 수집 job을 만든 뒤 Kafka requested 이벤트를 발행한다. |
| `FinancialDataCollectionService` | 실제 수집 orchestration. corp code 조회, DART 호출, S3 저장, 정규화, DB 저장, 로그 상태 전환을 담당한다. |
| `FinancialCollectionResult` | 수집 성공 결과를 Consumer에 전달하는 내부 record |

### 수집 로그

| 클래스 | 의미 |
| --- | --- |
| `FinancialCollectionLog` | 금융 데이터 수집 job의 상태 머신 Entity |
| `FinancialCollectionLogService` | 로그 생성, RUNNING/COMPLETED/FAILED 전환 담당 |
| `FinancialCollectionLogRepository` | `financial_collection_logs` JPA repository |
| `FinancialCollectionStatus` | `REQUESTED`, `RUNNING`, `COMPLETED`, `FAILED` |

### DART client/corp code

| 클래스 | 의미 |
| --- | --- |
| `DartProperties` | `app.dart` 설정 바인딩 |
| `DartClient` | DART corp code XML과 재무제표 API 호출 담당 |
| `DartClientException` | DART 호출/파싱 계층 예외 |
| `DartCorpCode` | corp code 캐시 Entity |
| `DartCorpCodeRepository` | corp code 캐시 조회 |
| `DartCorpCodeService` | stockCode -> corpCode 해석. 캐시 미스 시 DART XML 다운로드/파싱/upsert |
| `DartCorpCodeParser` | DART XML을 `DartCorpCodeEntry` 목록으로 파싱 |
| `DartCorpCodeEntry` | XML row 하나를 담는 record |
| `DartReportCode` | quarter -> DART report code 매핑 |
| `DartStatementDivision` | `CFS`, `OFS` 구분 |
| `RawDartFinancialStatement` | DART raw payload와 metadata를 담는 record |

### 재무지표

| 클래스 | 의미 |
| --- | --- |
| `FinancialMetric` | `financial_metrics` Entity |
| `FinancialMetricRepository` | 재무지표 저장/조회 repository |
| `FinancialMetricService` | 재무지표 upsert와 조회 담당 |
| `FinancialMetricValues` | 정규화된 재무지표 값 묶음 |
| `DartFinancialStatementNormalizer` | DART raw JSON을 `FinancialMetricValues`로 변환 |

### 부채 만기

| 클래스 | 의미 |
| --- | --- |
| `DebtMaturity` | `debt_maturities` Entity |
| `DebtMaturityRepository` | 부채 만기 저장/조회 repository |
| `DebtMaturityService` | 샘플 CSV import와 조회 담당 |
| `DebtType` | `BOND`, `CP`, `ABSTB`, `LOAN`, `SHORT_TERM_BORROWING` |

### S3 저장

| 클래스 | 의미 |
| --- | --- |
| `FinancialRawStorage` | 금융 원본 저장소 interface |
| `S3FinancialRawStorage` | 기존 S3 bucket에 DART raw, corp code XML, sample CSV 저장 |
| `UnavailableFinancialRawStorage` | S3 설정이 없을 때 명시적으로 실패하는 구현 |
| `FinancialStorageConfiguration` | S3 설정 여부에 따라 저장소 bean 선택 |

### Kafka

| 클래스 | 의미 |
| --- | --- |
| `FinancialDataTopics` | Day 8 topic 상수 |
| `FinancialDataKafkaConfiguration` | Day 8 topic bean 생성 |
| `FinancialDataEventPublisher` | Kafka 이벤트 발행 |
| `FinancialDataEventPublishException` | requested 이벤트 발행 실패 예외 |
| `FinancialDataFetchConsumer` | requested 이벤트를 받아 실제 수집 수행 |
| `FinancialDataFetchRequestedEvent` | 수집 요청 이벤트 |
| `FinancialDataFetchedEvent` | 수집 성공 이벤트 |
| `FinancialDataFetchFailedEvent` | 수집 실패 이벤트 |

## 10. 실패 처리

주요 실패 상황:

- DART API key 없음: `DART_API_KEY_MISSING`
- stockCode에 맞는 corp code 없음: `FINANCIAL_CORP_CODE_NOT_FOUND`
- Asset 없음: `ASSET_NOT_FOUND`
- Kafka requested 발행 실패: `FINANCIAL_COLLECTION_REQUEST_FAILED`
- DART API 응답 없음/비정상: 수집 job FAILED
- S3 저장 불가: 수집 job FAILED

Consumer 내부 실패는 `FinancialCollectionLog`에 FAILED로 남고, `financial-data-fetch-failed` 이벤트가 발행된다.

## 11. 테스트와 검증 포인트

자동 테스트:

- `DartCorpCodeParserTest`: XML 파싱과 삼성전자 corp code 검증
- `DartReportCodeTest`: quarter -> report code 매핑 검증
- `DartFinancialStatementNormalizerTest`: 금액 파싱, 부채비율, 이자보상배율 검증
- `FinancialDataFetchConsumerTest`: 수집 성공 시 fetched 이벤트 발행 검증

Docker 검증 포인트:

- backend/frontend build 성공
- backend health `UP`
- frontend `/login` HTTP 200
- Flyway V9 적용
- Day 8 DB table 생성
- Day 8 Kafka topic 생성
- OpenAPI에 Day 8 endpoint 노출

## 12. Day 9와의 연결

Day 9 위험탐지는 Day 8 데이터를 읽어서 판단한다.

채권/JTBC형 위험 판단에 필요한 Day 8 데이터:

- 부채비율: `financial_metrics.debt_ratio`
- 이자보상배율: `financial_metrics.interest_coverage_ratio`
- 영업현금흐름: `financial_metrics.operating_cash_flow`
- 6개월 내 만기부채: `debt_maturities.maturity_date`, `amount`
- 현금: `financial_metrics.cash`
- CP/단기차입 비중: `debt_maturities.debt_type`, `is_short_term`

리츠/JR글로벌리츠형 위험 판단에 필요한 Day 8 데이터:

- 만기 집중도: `debt_maturities`
- 차환 부담 테스트용 금리: `debt_maturities.interest_rate`
- 현금 대비 만기부채: `financial_metrics.cash`와 `debt_maturities.amount`

LTV, 배당성향, 환헤지 정산금, 캐시트랩, 유상증자 실패, 기한이익상실 같은 이벤트성/리츠 특화 항목은 Day 11 공시/뉴스 파싱에서 보강한다.

## 13. 실행 예시

Docker:

```powershell
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml up -d --build
```

Backend test:

```powershell
cd backend
.\gradlew.bat test
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest -UseBasicParsing http://localhost:3000/login
```

Kafka topic check:

```powershell
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list
```

DB table check:

```powershell
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml exec -T postgres psql -U finrisk -d finrisk -c "select table_name from information_schema.tables where table_schema='public' and table_name like '%financial%' order by table_name;"
```

## 14. 중요한 설계 선택

기존 Day 5 `CollectionLog`를 억지로 재사용하지 않았다. 이유는 기존 로그가 `ticker`, `start_date`, `end_date`, `MarketPriceSource`처럼 시장가격 수집 의미에 강하게 묶여 있기 때문이다.

대신 Day 8은 `FinancialCollectionLog`를 분리했다. 이 선택으로 기존 Day 1~Day 7 기능과 API shape를 깨지 않고, Day 8에 필요한 `year`, `quarter`, `corp_code`, `statement_division`, `fallback_used` 같은 metadata를 자연스럽게 저장할 수 있다.

또한 DART 원본은 DB에 그대로 넣지 않고 S3에 저장하고, PostgreSQL에는 정규화된 핵심 지표만 저장한다. 이 구조는 Day 5 시장가격 수집의 원본 보존 방식과 같은 철학이다.
