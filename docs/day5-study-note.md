# FinRisk Radar Day 5 학습 노트: 외부 가격 데이터 파이프라인

> 대상 독자: Spring Boot, Kafka, S3, React Query를 처음 연결해 보는 개발자  
> 기준 코드: 현재 저장소의 Day 5 구현  
> 핵심 문장: **사용자의 수집 요청을 Kafka 작업으로 바꾸고, Yahoo/CSV 원본은 S3에, 검증된 OHLCV는 PostgreSQL에 저장한 뒤 프론트 차트에서 조회한다.**

---

## 0. 먼저 보는 전체 지도

```text
[사용자 / 자산 상세 화면]
        |
        | POST /api/collector/market-prices/fetch
        v
[MarketPriceCollectorController]
        |
        v
[CollectionRequestService]
  1) 사용자/자산/기간/ticker 검증
  2) CollectionLog = REQUESTED 저장
  3) market-data-fetch-requested 발행
        |
        v
[Kafka]
        |
        v
[MarketDataFetchConsumer]
        |
        v
[MarketPriceCollectionService]
  CollectionLog = RUNNING
        |
        +--> YahooFinanceMarketDataClient
        |       실패/빈 데이터
        |             |
        |             v
        |    CsvMarketDataClient
        |
        v
  RawMarketData 생성
        |
        v
  S3RawMarketDataStorage.store()  ──> S3 raw JSON
        |
        v
  MarketDataNormalizer.normalize()
        |
        v
  MarketPriceWriter.upsert()      ──> PostgreSQL market_prices
        |
        v
  CollectionLog = COMPLETED 또는 FAILED
        |
        +--> market-data-fetched
        └--> collection-failed

[프론트엔드]
  GET /api/market-prices/{assetId}
        |
        v
  React Query + Recharts LineChart
```

### 흐름을 한 문장씩 기억하기

1. HTTP 요청은 실제 수집을 직접 하지 않고 작업 등록만 한다.
2. 작업 상태는 `CollectionLog`에 남긴다.
3. Kafka Consumer가 외부 데이터 수집을 실행한다.
4. Yahoo가 실패하면 CSV가 대신한다.
5. 정제 전에 원본을 S3에 먼저 보존한다.
6. 검증된 가격은 PostgreSQL에 upsert한다.
7. 프론트는 job을 polling하고 완료되면 가격을 다시 조회한다.

---

## 1. Day 5의 목적과 이후 기능과의 연결

### 1.1 왜 외부 가격 데이터 파이프라인이 필요한가

금융 기능은 가격 데이터가 있어야 계산을 시작할 수 있다. 예를 들어 수익률, 변동성, 최대 낙폭, 벤치마크 비교는 모두 날짜별 가격 시계열이 필요하다. 외부 API를 화면에서 즉석 호출하면 다음 문제가 생긴다.

- 외부 API가 느리면 사용자 요청도 같이 느려진다.
- 외부 API 장애가 곧 서비스 장애가 된다.
- 같은 데이터를 여러 기능이 반복해서 받아야 한다.
- 어떤 원본을 언제 받았는지 추적하기 어렵다.
- 외부 값이 바뀌었을 때 과거 계산을 재현하기 어렵다.

Day 5는 이 문제를 해결하기 위해 **수집, 원본 보존, 정제, 저장, 상태 추적, 조회**를 하나의 파이프라인으로 만든다.

### 1.2 이후 Day와의 관계

| 후속 기능 | Day 5 데이터가 쓰이는 방식 |
|---|---|
| Day 6 백테스트 | 날짜별 종가로 매수·매도 시뮬레이션, 수익률, 누적 자산가치, MDD를 계산한다. |
| Day 9 위험 탐지 | 급락률, 변동성 확대, 거래량 급증, 추세 붕괴 같은 시장 위험 신호를 만든다. |
| Day 13 AI 리포트 | 가격 추세와 위험 지표를 RAG/Agent 문맥에 넣어 “왜 위험한가”를 설명하는 보고서를 만든다. |

Day 5는 단순한 차트 기능이 아니라 이후 분석 기능들이 공유하는 **시장 데이터 기반 계층**이다.

---

## 2. 전체 동작 흐름

### 2.1 요청 단계

1. 사용자가 자산 상세 화면에서 “가격 데이터 수집”을 누른다.
2. 프론트의 `fetchMarketPrices()`가 `POST /api/collector/market-prices/fetch`를 호출한다.
3. `MarketPriceCollectorController.fetch()`가 JWT 사용자와 요청 DTO를 받는다.
4. `CollectionRequestService.request()`가 다음을 검증한다.
   - `startDate <= endDate`
   - 로그인 사용자가 실제로 존재하는지
   - `assetId`에 해당하는 자산이 존재하는지
   - 자산의 시장과 요청 ticker가 일치하는지
   - 자산 유형이 `STOCK` 또는 `REIT`인지
5. `CollectionLogService.createRequested()`가 UUID job을 `REQUESTED` 상태로 저장한다.
6. `MarketDataEventPublisher.publishRequestedAndAwait()`가 Kafka broker 응답을 최대 5초 기다린다.
7. 발행에 성공하면 HTTP 202와 `jobId`를 반환한다. 발행에 실패하면 CollectionLog를 `FAILED`로 변경하고 503 계열 비즈니스 오류를 반환한다.

### 2.2 비동기 수집 단계

1. `MarketDataFetchConsumer.consume()`가 `market-data-fetch-requested`를 소비한다.
2. `MarketPriceCollectionService.collect(jobId)`가 CollectionLog를 `RUNNING`으로 바꾼다.
3. `FallbackMarketDataClient.fetch()`가 먼저 Yahoo Client를 실행한다.
4. Yahoo 호출이 실패하거나 유효 일봉이 없으면 CSV Client를 실행한다.
5. 선택된 공급자의 결과를 `RawMarketData(source, payload)`로 만든다.
6. `RawMarketDataStorage.store()`가 raw payload를 S3에 저장한다.
7. S3 경로를 CollectionLog의 `rawS3Path`에 기록한다.
8. `MarketDataNormalizer.normalize()`가 raw payload를 `PriceBar` 목록으로 정제한다.
9. `MarketPriceWriter.upsert()`가 PostgreSQL에 batch upsert한다.
10. 성공하면 CollectionLog를 `COMPLETED`로 변경하고 `market-data-fetched`를 발행한다.
11. 예외가 발생하면 CollectionLog를 `FAILED`로 변경하고 `collection-failed`를 발행한다.

### 2.3 조회와 화면 단계

1. `AssetPriceChart`가 `getMarketPrices()`로 가격 API를 호출한다.
2. `MarketPriceService.getPrices()`가 기간 조건에 맞는 데이터를 조회한다.
3. 같은 날짜에 여러 source가 있으면 `MANUAL > YAHOO > CSV` 순으로 한 건만 선택한다.
4. 프론트는 응답의 `close`를 `{ label: date, value: close }`로 변환한다.
5. `CommonChart` 내부의 Recharts `LineChart`가 종가 추이를 표시한다.

---

## 3. API 설명

모든 API 응답은 프로젝트 공통 `ApiResponse<T>` 형태다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request succeeded.",
  "data": {}
}
```

### 3.1 가격 조회

```http
GET /api/market-prices/{assetId}?startDate=2024-01-01&endDate=2024-12-31
```

| 항목 | 설명 |
|---|---|
| Controller | `MarketPriceController.getPrices()` |
| 권한 | 공개. JWT 없이 호출 가능 |
| 날짜 | 양쪽 끝 날짜를 포함한다. 한쪽만 전달하거나 둘 다 생략할 수도 있다. |
| 정렬 | 날짜 오름차순 |
| 중복 날짜 | source 우선순위로 한 건만 반환 |

응답 예시:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request succeeded.",
  "data": [
    {
      "assetId": 1,
      "date": "2024-01-02",
      "open": 78200.000000,
      "high": 79800.000000,
      "low": 78200.000000,
      "close": 79600.000000,
      "volume": 17142847,
      "source": "YAHOO"
    }
  ]
}
```

주의할 점:

- 존재하지 않는 자산이면 `ASSET_NOT_FOUND`다.
- `startDate > endDate`면 `INVALID_DATE_RANGE`다.
- DB에는 CSV와 Yahoo가 함께 있어도 API에는 우선 source 한 건만 보인다.

### 3.2 가격 수집 요청

```http
POST /api/collector/market-prices/fetch
Authorization: Bearer {ACCESS_TOKEN}
Content-Type: application/json
```

요청:

```json
{
  "assetId": 1,
  "ticker": "005930.KS",
  "startDate": "2024-01-01",
  "endDate": "2024-12-31"
}
```

응답은 실제 수집 완료가 아니라 **작업 접수 완료**를 뜻한다.

```http
HTTP/1.1 202 Accepted
```

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request succeeded.",
  "data": {
    "jobId": "c684ec9e-12db-4ec5-891f-d5532ea9a2b1",
    "status": "REQUESTED"
  }
}
```

| 항목 | 설명 |
|---|---|
| Controller | `MarketPriceCollectorController.fetch()` |
| Service | `CollectionRequestService.request()` |
| 권한 | 로그인 사용자 필요 |
| 반환 상태 | HTTP 202 |
| jobId | Java 17 기본 `UUID.randomUUID()`를 이용한 UUIDv4 |

### 3.3 수집 작업 상태 조회

```http
GET /api/collector/market-prices/jobs/{jobId}
Authorization: Bearer {ACCESS_TOKEN}
```

응답 예시:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request succeeded.",
  "data": {
    "jobId": "c684ec9e-12db-4ec5-891f-d5532ea9a2b1",
    "assetId": 1,
    "ticker": "005930.KS",
    "source": "YAHOO",
    "status": "COMPLETED",
    "message": "3 market price records stored.",
    "rawS3Path": "s3://{bucket}/market-prices/005930.KS/2024/2024-01-01_2024-01-04.json",
    "startedAt": "2026-07-05T22:10:00",
    "completedAt": "2026-07-05T22:10:02"
  }
}
```

| 항목 | 설명 |
|---|---|
| Controller | `MarketPriceCollectorController.job()` |
| Service | `CollectionLogService.getForUser()` |
| 권한 | 로그인 필요. 요청자 본인 또는 `ROLE_ADMIN`만 조회 가능 |
| 상태 | `REQUESTED`, `RUNNING`, `COMPLETED`, `FAILED` |

`REQUESTED` 단계에서는 실제 공급자가 아직 정해지지 않았으므로 `source`와 `rawS3Path`가 `null`일 수 있다.

---

## 4. DB 구조

스키마는 Flyway migration으로 관리한다.

- `V5__create_market_prices.sql`
- `V6__create_collection_logs.sql`

### 4.1 market_prices

| 컬럼 | 타입 | 의미 |
|---|---|---|
| id | BIGSERIAL | 내부 PK |
| asset_id | BIGINT | `assets.id` FK |
| date | DATE | 거래일 |
| open/high/low/close | NUMERIC(20,6) | 일별 OHLC |
| volume | BIGINT | 거래량 |
| source | VARCHAR(20) | `YAHOO`, `CSV`, `MANUAL` |
| created_at/updated_at | TIMESTAMP | 생성·갱신 시각 |

가격에 `double` 대신 `BigDecimal`/`NUMERIC`을 쓰는 이유는 부동소수점 오차를 피하기 위해서다.

### 4.2 collection_logs

| 컬럼 | 의미 |
|---|---|
| job_id | 작업을 식별하는 UUID PK |
| asset_id | 수집 대상 자산 |
| requested_by_user_id | 작업 요청 사용자 |
| ticker | 실제 외부 조회 ticker |
| start_date/end_date | 요청 기간 |
| source | 최종 선택된 공급자. 초기에는 null 가능 |
| status | REQUESTED/RUNNING/COMPLETED/FAILED |
| message | 현재 상태 또는 안전한 실패 설명 |
| raw_s3_path | 원본 객체 경로 |
| started_at/completed_at | 실행·종료 시각 |
| created_at/updated_at | 로그 생성·갱신 시각 |

### 4.3 왜 `(asset_id, date, source)`가 unique인가

같은 자산, 같은 날짜, 같은 공급자의 가격이 여러 행으로 쌓이면 재수집할 때마다 중복이 증가한다. 따라서 이 세 값을 자연키처럼 사용해 중복을 막는다.

source를 unique key에 포함하는 이유는 CSV와 Yahoo 데이터를 동시에 보존하기 위해서다. 실제 검증에서도 같은 기간에 다음처럼 공존했다.

```text
CSV:   3 rows
YAHOO: 3 rows
```

### 4.4 왜 upsert인가

`MarketPriceWriter.UPSERT_SQL`은 다음 정책을 사용한다.

```sql
ON CONFLICT (asset_id, date, source) DO UPDATE SET
  open = EXCLUDED.open,
  high = EXCLUDED.high,
  low = EXCLUDED.low,
  close = EXCLUDED.close,
  volume = EXCLUDED.volume,
  updated_at = EXCLUDED.updated_at
```

재수집을 단순 skip하면 공급자가 과거 값을 정정해도 반영할 수 없다. upsert는 행 개수는 유지하면서 최신 공급자 값으로 갱신한다. `JdbcTemplate.batchUpdate()`를 사용해 여러 일자를 한 번에 처리한다.

### 4.5 source 우선순위

`MarketPriceSource`의 실제 우선순위는 다음과 같다.

| Source | priority | 의미 |
|---|---:|---|
| MANUAL | 3 | 관리자가 검증 후 넣는 보정값. 아직 Day 5 입력 기능은 없음 |
| YAHOO | 2 | 기본 외부 시장 데이터 |
| CSV | 1 | Yahoo 장애 시 fallback 데이터 |

`MarketPriceService.getPrices()`는 날짜별로 데이터를 합치면서 더 높은 `priority()`를 가진 행을 선택한다. 따라서 CSV로 먼저 저장한 뒤 Yahoo 수집에 성공하면 DB에는 둘 다 남고 화면에는 Yahoo가 보인다.

---

## 5. Kafka

### 5.1 Topic 역할

| Topic | 발행 시점 | Producer | Consumer |
|---|---|---|---|
| `market-data-fetch-requested` | CollectionLog를 REQUESTED로 만든 뒤 | `CollectionRequestService` → `MarketDataEventPublisher` | `MarketDataFetchConsumer` |
| `market-data-fetched` | S3 저장, 정제, DB upsert, COMPLETED 처리 후 | `MarketDataFetchConsumer` | 현재 저장소에는 없음. 이후 분석/알림 기능이 구독 가능 |
| `collection-failed` | Consumer 처리 중 최종 예외 발생 후 | `MarketDataFetchConsumer` | 현재 저장소에는 없음. 이후 운영 알림/재처리 기능이 구독 가능 |

### 5.2 이벤트 payload

requested:

```json
{
  "jobId": "UUID",
  "assetId": 1,
  "ticker": "005930.KS",
  "startDate": "2024-01-01",
  "endDate": "2024-12-31",
  "requestedAt": "2026-07-05T13:00:00Z"
}
```

fetched:

```json
{
  "jobId": "UUID",
  "assetId": 1,
  "ticker": "005930.KS",
  "source": "YAHOO",
  "recordCount": 245,
  "rawS3Path": "s3://{bucket}/...json",
  "completedAt": "2026-07-05T13:00:02Z"
}
```

failed:

```json
{
  "jobId": "UUID",
  "assetId": 1,
  "ticker": "005930.KS",
  "errorCode": "MARKET_DATA_COLLECTION_FAILED",
  "message": "안전하게 정제된 실패 메시지",
  "failedAt": "2026-07-05T13:00:02Z"
}
```

### 5.3 Kafka를 쓰는 이유

초보자 관점에서는 Kafka를 “오래 걸리는 일을 HTTP 요청에서 떼어내는 작업 우체통”으로 이해하면 쉽다.

- 사용자는 Yahoo와 S3가 끝날 때까지 HTTP 연결을 유지하지 않는다.
- Consumer를 늘려 수집량을 확장할 수 있다.
- 이후 위험 탐지, 알림, AI 보고서가 fetched 이벤트를 독립적으로 구독할 수 있다.
- API 서버와 수집 처리기의 책임이 분리된다.

### 5.4 멱등성과 현재 구현의 경계

- Kafka는 같은 메시지가 다시 전달될 가능성을 고려해야 한다.
- `CollectionLog.start()`는 상태가 REQUESTED일 때만 RUNNING으로 바뀐다. 이미 실행된 job이면 `false`를 반환한다.
- 가격 저장은 unique constraint와 upsert로 중복을 막는다.
- requested 발행은 최대 5초 broker ack를 기다린다.
- 현재 DB 저장과 Kafka 발행을 하나의 원자적 transaction으로 묶는 outbox 패턴은 적용하지 않았다.
- 현재 Consumer는 예외를 잡아 failed 이벤트를 발행하므로 Kafka listener 수준의 DLT/retry는 없다. Yahoo Client 내부에 HTTP 재시도는 존재한다.

면접에서는 “Day 5 수준에서는 상태 로그와 멱등 upsert로 방어했고, 운영 단계에서는 transactional outbox와 DLT를 도입할 수 있다”고 설명하면 정확하다.

---

## 6. Yahoo Finance Client

### 6.1 호출 방식

`YahooFinanceMarketDataClient.request()`가 다음 Chart endpoint를 호출한다.

```text
GET {baseUrl}/v8/finance/chart/{ticker}
  ?period1={start epoch seconds}
  &period2={endDate + 1 day epoch seconds}
  &interval=1d
  &events=history
```

종료일을 포함하기 위해 `period2`에는 `endDate.plusDays(1)`을 사용한다. 최종 Normalizer가 다시 요청 기간으로 필터링하므로 경계 밖 데이터가 DB에 들어가지 않는다.

연결 timeout은 5초, 읽기 timeout은 10초다.

### 6.2 ticker 변환

`MarketTickerResolver.resolve()`의 규칙:

| Asset market | 저장된 ticker | Yahoo ticker |
|---|---|---|
| KOSPI | 005930 | 005930.KS |
| KOSDAQ | 035720 | 035720.KQ |
| NASDAQ/NYSE 등 | AAPL | AAPL |

이미 `.`이 들어 있는 ticker는 그대로 대문자로 사용한다. `validate()`는 요청 ticker와 계산된 ticker가 다르면 `TICKER_MISMATCH`를 발생시킨다. 프론트에서도 같은 변환을 하지만 보안과 데이터 정합성을 위해 서버가 반드시 다시 검증한다.

### 6.3 User-Agent가 필요한 이유

Yahoo Chart endpoint는 API key가 없지만 자동화 요청 필터가 있다. 실제 문제 재현 결과는 다음과 같았다.

| 요청 | 결과 |
|---|---|
| 기본 curl/Java 계열 User-Agent | HTTP 429 |
| `User-Agent: Java/17...` + JSON Accept | HTTP 429 |
| 브라우저형 `User-Agent: Mozilla/5.0 ...` + JSON Accept | HTTP 200, 정상 OHLCV JSON |

현재 `configuredBuilder()`는 브라우저형 User-Agent와 `Accept: application/json`을 기본 헤더로 설정한다. 이 문제는 ticker나 날짜가 틀린 것이 아니라 Yahoo의 bot/rate-limit 정책에 걸린 것이었다.

### 6.4 retry/backoff

`fetch()`는 최대 3회 시도한다.

- HTTP 429 또는 5xx: 재시도
- 네트워크 `RestClientException`: 재시도
- 첫 재시도 전 250ms, 다음 재시도 전 500ms 대기
- 그 외 4xx: 즉시 실패
- 3회 모두 실패하면 `MarketDataClientException`

backoff는 외부 서비스가 잠깐 과부하일 때 즉시 연속 호출하여 상황을 악화시키지 않기 위한 장치다.

### 6.5 데이터 유효성 확인과 정제

`hasDailyData()`는 다음을 확인한다.

- `chart.error`가 null인지
- `result`와 `timestamp`가 존재하는지
- quote 배열에 숫자인 close가 하나 이상 있는지

이 검사를 통과한 raw JSON은 먼저 S3에 저장된다. 이후 `MarketDataNormalizer.normalizeYahoo()`가 다음을 수행한다.

- `exchangeTimezoneName`으로 timestamp를 거래소 현지 날짜로 변환
- timestamp와 open/high/low/close/volume 배열을 같은 index로 결합
- null 값 제외
- 요청 기간 밖 날짜 제외
- 음수 가격·거래량 제외
- `high >= max(open, close)`, `low <= min(open, close)`, `high >= low` 검증

---

## 7. CSV fallback

### 7.1 fallback이란

주 공급자가 실패했을 때 보조 수단으로 서비스를 계속 제공하는 전략이다. 여기서는 Yahoo가 primary, CSV가 fallback이다.

`FallbackMarketDataClient.fetch()`의 핵심은 다음과 같다.

```text
Yahoo fetch 성공 → YAHOO raw 반환
Yahoo MarketDataClientException → 경고 로그 → CSV fetch
```

### 7.2 파일 위치와 형식

```text
backend/src/main/resources/market-data-fallback/{ticker}.csv
```

현재 예제:

- `005930.KS.csv`
- `035420.KS.csv`
- `AAPL.csv`

형식:

```csv
date,open,high,low,close,volume
2024-01-02,78200,79800,78200,79600,17142847
```

`CsvMarketDataClient`는 classpath resource를 문자열 raw payload로 읽는다. ticker에는 `[A-Z0-9._-]+`만 허용하여 경로 조작을 막는다.

`MarketDataNormalizer.normalizeCsv()`는 다음을 확인한다.

- 헤더가 정확히 `date,open,high,low,close,volume`인지
- 각 행이 6개 컬럼인지
- 날짜·숫자를 파싱할 수 있는지
- 요청 기간 안인지
- OHLC 관계와 거래량이 유효한지

### 7.3 왜 테스트와 안정성에 도움이 되는가

- 인터넷 없이 동일 입력으로 정제 로직을 반복 테스트할 수 있다.
- Yahoo 장애 중에도 최소 샘플 가격을 제공할 수 있다.
- 장애 대응 경로가 실제로 동작하는지 검증할 수 있다.
- 외부 응답 형식 변화와 내부 정제 문제를 분리할 수 있다.

주의: CSV는 모든 ticker와 기간을 무한히 커버하는 데이터 공급자가 아니다. 해당 ticker 파일이나 요청 기간의 행이 없으면 job은 FAILED가 된다.

---

## 8. S3 raw 저장

### 8.1 무엇을 저장하는가

S3에는 정제 전 공급자 payload를 JSON envelope로 저장한다.

- Yahoo source: Yahoo JSON을 `payload` JSON 객체로 보존
- CSV source: CSV 원문 문자열을 `payload`에 보존

### 8.2 왜 DB와 S3를 나누는가

| 저장소 | 저장 내용 | 목적 |
|---|---|---|
| S3 | 정제 전 raw payload + 메타데이터 | 감사, 장애 분석, 재처리, 원본 보존 |
| PostgreSQL | 검증된 OHLCV 행 | 빠른 조회, 차트, 백테스트, 위험 계산 |

DB에 원본 JSON까지 넣으면 조회 모델이 복잡해지고 저장량이 커진다. 반대로 S3 raw만 있으면 날짜 조건 조회와 분석 계산이 불편하다. 두 저장소는 중복이 아니라 서로 다른 책임을 가진다.

### 8.3 저장 순서

```text
raw 수집
→ S3 저장
→ CollectionLog.rawS3Path 기록
→ 정제
→ DB upsert
→ CollectionLog COMPLETED
```

정제나 DB 저장이 실패해도 원본이 남기 때문에 문제를 재현할 수 있다. 단, S3 성공 후 DB 실패 시 raw 객체가 남는 것은 의도된 결과이며 CollectionLog가 FAILED 상태와 경로를 기록한다.

### 8.4 S3 key

```text
market-prices/{ticker}/{startYear}/{startDate}_{endDate}.json
```

예시:

```text
market-prices/005930.KS/2024/2024-01-01_2024-01-04.json
```

동일 ticker·기간을 재수집하면 같은 key를 덮어쓴다. 장기 감사 이력이 필요하면 bucket versioning 또는 key에 jobId를 포함하는 확장을 고려한다.

### 8.5 JSON envelope

```json
{
  "assetId": 1,
  "ticker": "005930.KS",
  "source": "YAHOO",
  "startDate": "2024-01-01",
  "endDate": "2024-01-04",
  "fetchedAt": "2026-07-05T13:10:00Z",
  "payload": {
    "chart": {
      "result": [],
      "error": null
    }
  }
}
```

### 8.6 설정 연결

```text
.env.local.example의 이름
        ↓ Docker Compose environment 전달
AWS_REGION / AWS_ACCESS_KEY / AWS_SECRET_KEY / AWS_S3_BUCKET
        ↓ application.yaml placeholder
app.market-data.s3.*
        ↓ @ConfigurationProperties
S3Properties
        ↓
S3StorageConfiguration
        ↓
S3RawMarketDataStorage
```

`S3Properties.configured()`가 false면 `UnavailableRawMarketDataStorage`를 Bean으로 사용한다. 이 설계는 S3 설정이 없다고 애플리케이션 전체가 기동 실패하지 않게 한다. 대신 수집 시 `RawStorageException`이 발생하고 job이 FAILED가 된다.

---

## 9. 주요 설정 파일

### 9.1 backend/build.gradle

Day 5 핵심 의존성:

| 의존성 | 용도 |
|---|---|
| `spring-boot-starter-data-jpa` | MarketPrice/CollectionLog JPA 조회·상태 저장 |
| `spring-boot-starter-web` | REST API와 Yahoo RestClient |
| `spring-kafka` | Kafka producer, consumer, topic 생성 |
| AWS SDK v2 BOM + `software.amazon.awssdk:s3` | S3 raw 객체 저장 |
| Flyway + PostgreSQL | V5/V6 migration과 가격 DB |
| `spring-boot-starter-validation` | fetch request 필드 검증 |
| `spring-kafka-test`, `spring-boot-starter-test` | 테스트 지원 |

### 9.2 application.yaml

- Kafka key: `StringSerializer`
- Kafka value: `JsonSerializer`
- Consumer: `JsonDeserializer`
- trusted package: `com.finrisk.radar.collector.event`
- Yahoo base URL: `${YAHOO_FINANCE_BASE_URL:https://query1.finance.yahoo.com}`
- S3 속성: 네 AWS 환경변수를 `app.market-data.s3`에 연결

Yahoo base URL을 설정으로 분리했기 때문에 테스트 서버나 다른 공급 endpoint로 교체하기 쉽다.

### 9.3 application-docker.yaml

- PostgreSQL: `SPRING_DATASOURCE_URL`, user/password
- Redis host/password
- Kafka: `KAFKA_BOOTSTRAP_SERVERS`
- JPA: `ddl-auto: none`이므로 스키마는 Flyway가 책임진다.
- `app.seed-assets.enabled: true`로 Docker에서 예제 자산을 준비한다.

### 9.4 infra/docker/docker-compose.local.yml

- PostgreSQL, Redis, ZooKeeper, Kafka, backend, frontend, Prometheus, Grafana를 한 네트워크에 구성한다.
- backend는 PostgreSQL, Redis, Kafka가 healthy일 때 시작한다.
- Kafka 내부 주소는 `kafka:9092`, 호스트 주소는 `localhost:29092`다.
- AWS 네 환경변수를 backend 컨테이너로 전달한다.
- backend healthcheck는 `/actuator/health`다.

### 9.5 .env.local.example

실제 값이 아닌 **필요한 변수 이름과 예시 형식**만 제공한다.

```dotenv
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY=replace-with-aws-access-key
AWS_SECRET_KEY=replace-with-aws-secret-key
AWS_S3_BUCKET=replace-with-s3-bucket
```

실제 `.env.local`은 Git에 커밋하거나 문서에 복사하면 안 된다.

### 9.6 frontend/package.json

| 의존성 | Day 5 역할 |
|---|---|
| `@tanstack/react-query` | 가격 조회 캐시, 수집 mutation, job polling |
| `axios` | backend API 요청 |
| `recharts` | 종가 LineChart |
| `zustand` | 로그인 상태 확인 후 수집 버튼/로그인 안내 분기 |
| `vitest`, Testing Library | 가격 차트 상태와 수집 요청 테스트 |

---

## 10. 주요 코드 파일별 설명

### 10.1 marketprice 패키지

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `MarketPrice.java` | `market_prices` JPA 엔티티 | 조회 결과 매핑 시 사용 | 실제 쓰기는 `MarketPriceWriter`의 native upsert가 담당한다. |
| `MarketPriceSource.java` | YAHOO/CSV/MANUAL 및 우선순위 | `priority()` | enum 선언 순서가 아니라 숫자 priority로 비교한다. |
| `MarketPriceRepository.java` | asset/date 조건별 조회 | `findAllByAsset_Id...` | source 우선 선택은 Repository가 아니라 Service에서 한다. |
| `MarketPriceService.java` | 날짜 검증, 자산 확인, source 병합 | `getPrices()` | 같은 날짜의 여러 source를 `LinkedHashMap.merge()`로 한 건 선택한다. |
| `MarketPriceResponse.java` | API 출력 DTO | `from()` | Entity를 그대로 외부에 노출하지 않는다. |
| `MarketPriceController.java` | 공개 가격 GET API | `getPrices()` | ISO 날짜 query parameter를 받는다. |
| `MarketPriceWriter.java` | PostgreSQL batch upsert | `upsert()` | `(asset,date,source)` 충돌 시 값과 updated_at을 갱신한다. |

### 10.2 collector/api

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `MarketPriceCollectorController.java` | fetch와 job API | `fetch()`, `job()` | `@AuthenticationPrincipal`의 userId/role을 Service에 전달한다. |
| `MarketPriceFetchRequest.java` | fetch 입력 DTO | record validation | assetId 양수, ticker 필수/100자 이하, 날짜 필수다. 날짜 순서는 Service가 검증한다. |
| `MarketPriceFetchResponse.java` | 202 응답 DTO | jobId/status | 반환 status는 접수 시점의 REQUESTED다. |

### 10.3 collector/service

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `CollectionRequestService.java` | 동기 요청 검증·job 생성·requested 발행 | `request()` | DB와 Kafka는 하나의 transaction이 아니다. 발행 실패 시 log를 FAILED로 보정한다. |
| `MarketPriceCollectionService.java` | 전체 수집 orchestration | `collect()` | 반드시 S3 저장 후 정제/DB 저장 순서다. |
| `MarketTickerResolver.java` | 시장별 Yahoo ticker 계산·검증 | `resolve()`, `validate()` | STOCK/REIT만 허용한다. |
| `MarketDataNormalizer.java` | Yahoo/CSV raw를 PriceBar로 변환 | `normalize()`, `normalizeYahoo()`, `normalizeCsv()` | null, 기간 밖, 잘못된 OHLC/volume을 제거한다. |
| `PriceBar.java` | 정제된 내부 일봉 값 객체 | record | DB 엔티티와 외부 raw DTO 사이의 경계다. |
| `CollectionResult.java` | 성공 결과를 Consumer로 전달 | record | fetched event 생성에 필요한 값만 담는다. |

### 10.4 collector/client

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `MarketDataClient.java` | 공급자 교체 가능한 인터페이스 | `fetch()` | orchestration은 구체 Yahoo 구현을 직접 몰라도 된다. |
| `YahooFinanceMarketDataClient.java` | Yahoo Chart API 호출 | `fetch()`, `request()`, `backoff()`, `hasDailyData()` | User-Agent가 없으면 실제 환경에서 HTTP 429가 발생했다. |
| `CsvMarketDataClient.java` | classpath CSV 원본 읽기 | `fetch()` | 파일명 안전성 검사와 파일 존재 확인을 한다. |
| `FallbackMarketDataClient.java` | Yahoo primary + CSV fallback | `fetch()` | `@Primary`이므로 `MarketDataClient` 주입 시 이 구현이 선택된다. |
| `RawMarketData.java` | source와 raw 문자열 | record | 정제 전 S3 저장을 가능하게 한다. |
| `MarketDataClientException.java` | 공급자/CSV 오류 표준화 | 생성자 | fallback이 잡는 예외 타입이다. |

### 10.5 collector/storage

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `RawMarketDataStorage.java` | raw 저장소 인터페이스 | `store()` | 이후 MinIO/GCS 등으로 교체할 수 있다. |
| `S3RawMarketDataStorage.java` | key/envelope 생성 후 putObject | `store()` | 동일 기간 key는 덮어쓴다. ticker 안전성도 검사한다. |
| `S3Properties.java` | `app.market-data.s3` 바인딩 | `configured()` | 실제 값은 환경변수에서 온다. |
| `S3StorageConfiguration.java` | 저장소 Bean 선택 | `rawMarketDataStorage()` | 설정 완전 여부에 따라 S3 또는 Unavailable 구현을 선택한다. |
| `UnavailableRawMarketDataStorage.java` | 미설정 시 명시적 실패 | `store()` | 앱 기동은 허용하지만 수집은 실패시킨다. |
| `RawStorageException.java` | S3 계층 오류 | 생성자 | 외부 상세 예외를 사용자에게 그대로 노출하지 않는다. |

### 10.6 collector/kafka와 event

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `MarketDataTopics.java` | topic 이름 상수 | 3개 상수 | producer/consumer 문자열 불일치를 막는다. |
| `MarketDataKafkaConfiguration.java` | 세 topic Bean 생성 | `TopicBuilder` | 로컬 단일 broker 기준 partition 1, replica 1이다. |
| `MarketDataEventPublisher.java` | Kafka 발행 | `publishRequestedAndAwait()`, `publishFetched()`, `publishFailed()` | requested만 broker 결과를 동기 대기한다. |
| `MarketDataFetchConsumer.java` | requested 소비·수집 실행·결과 발행 | `consume()` | 성공 result가 null이면 중복/이미 처리된 job이므로 결과 발행을 생략한다. |
| `EventPublishException.java` | 요청 이벤트 발행 실패 | 생성자 | Request Service가 잡아 CollectionLog를 FAILED로 만든다. |
| `MarketDataFetchRequestedEvent.java` | 수집 명령 메시지 | record | 기간과 jobId 포함 |
| `MarketDataFetchedEvent.java` | 수집 성공 메시지 | record | source, 건수, S3 경로 포함 |
| `CollectionFailedEvent.java` | 수집 실패 메시지 | record | 안전한 오류 코드/메시지 포함 |

### 10.7 collector/log

| 파일 | 하는 일 | 중요 메서드/시점 | 주의점 |
|---|---|---|---|
| `CollectionLog.java` | 상태 머신을 가진 JPA 엔티티 | `requested()`, `start()`, `rawStored()`, `complete()`, `fail()` | COMPLETED/FAILED는 다시 fail로 덮지 않는다. |
| `CollectionStatus.java` | 네 상태 정의 | enum | 정상 경로는 REQUESTED→RUNNING→COMPLETED다. |
| `CollectionLogRepository.java` | UUID PK 조회/저장 | JpaRepository | job 상태 영속화 |
| `CollectionLogService.java` | transaction 경계와 권한 검사 | `createRequested()`, `markRunning()`, `markRawStored()`, `markCompleted()`, `markFailed()`, `getForUser()` | `markFailed()`는 `REQUIRES_NEW`라 기존 작업 transaction 실패와 분리해 실패 로그를 남긴다. |
| `CollectionJobResponse.java` | job API DTO | `from()` | 요청 사용자 ID는 외부 응답에 노출하지 않는다. |

### 10.8 프론트엔드

| 파일 | 하는 일 | 중요 함수/시점 | 주의점 |
|---|---|---|---|
| `src/lib/api/market-prices.ts` | API 타입과 Axios 함수 | `getMarketPrices()`, `fetchMarketPrices()`, `getCollectionJob()` | backend의 enum/status와 TypeScript union을 맞춰야 한다. |
| `src/components/assets/asset-price-chart.tsx` | 기간 필터, 차트, 수집 버튼, polling | `rangeFor()`, `marketTicker()`, `AssetPriceChart()` | ALL 조회와 ALL 수집의 기간 의미가 다르다. |
| `src/components/assets/asset-detail.tsx` | 자산 조회 후 가격 차트 연결 | `<AssetPriceChart asset={asset} />` | Asset을 먼저 읽은 뒤 차트에 넘긴다. |
| `src/components/common/common-chart.tsx` | Recharts 공통 LineChart | `CommonChart` | Day 5는 close만 value로 전달한다. |

---

## 11. 프론트엔드 동작

### 11.1 가격 조회

`AssetDetail`이 자산을 조회한 뒤 `AssetPriceChart`에 전달한다. `pricesQuery`의 key는 다음과 같다.

```ts
["market-prices", asset.id, range.startDate, range.endDate]
```

기간이 바뀌면 query key가 바뀌므로 React Query가 해당 기간 데이터를 새로 조회한다.

### 11.2 기간 필터

| 버튼 | 조회 범위 |
|---|---|
| 1M | 오늘부터 1개월 전 |
| 6M | 오늘부터 6개월 전 |
| 1Y | 오늘부터 1년 전 |
| ALL | GET에는 날짜 query를 전달하지 않아 DB 전체 조회 |

ALL 상태에서 새로 수집할 때는 무한 기간을 요청할 수 없으므로 최근 5년을 요청한다. 즉, **ALL 조회는 저장된 전체**, **ALL 수집은 최근 5년**이다.

### 11.3 수집 버튼

- 가격 데이터가 없고 로그인 상태면 “가격 데이터 수집” 버튼을 표시한다.
- 비로그인 상태면 로그인 링크를 표시한다.
- BOND_ISSUER는 Day 5 대상이 아니므로 차트 컴포넌트가 `null`을 반환한다.
- mutation 성공 시 반환받은 `jobId`를 state에 저장한다.

### 11.4 job polling

```ts
refetchInterval: (query) =>
  terminal(query.state.data?.status) ? false : 2_000
```

2초마다 job API를 조회하고 `COMPLETED` 또는 `FAILED`가 되면 멈춘다. COMPLETED가 되면 다음 query를 invalidate한다.

```ts
queryClient.invalidateQueries({ queryKey: ["market-prices", asset.id] })
```

이후 최신 DB 가격으로 차트가 다시 렌더링된다.

---

## 12. 실행과 테스트

### 12.1 자동 테스트

백엔드:

```powershell
cd backend
.\gradlew.bat test
```

현재 구현 검증에서는 전체 112개 테스트가 통과했다. Day 5 핵심 테스트:

- `YahooFinanceMarketDataClientTest`: User-Agent/Accept와 429 재시도
- `MarketDataNormalizerTest`: CSV 기간 필터와 잘못된 헤더
- `MarketTickerResolverTest`: `.KS`, `.KQ`, 미국 ticker와 채권 거절
- `CollectionLogTest`: 상태 전이와 중복 start 방지
- `MarketPriceServiceTest`: MANUAL > YAHOO > CSV 우선순위

프론트:

```powershell
cd frontend
corepack pnpm@10.30.3 typecheck
corepack pnpm@10.30.3 test
corepack pnpm@10.30.3 lint
corepack pnpm@10.30.3 build
```

`asset-price-chart.test.tsx`는 빈 데이터에서 로그인/수집 UI와 KOSPI ticker 변환 요청을 검증한다.

### 12.2 Docker Compose 실행

```powershell
docker compose --env-file .env.local `
  -f infra/docker/docker-compose.local.yml up -d --build

docker compose --env-file .env.local `
  -f infra/docker/docker-compose.local.yml ps
```

확인 대상:

- postgres, redis, zookeeper, kafka가 healthy
- backend, frontend가 healthy
- backend `/actuator/health`가 `UP`

### 12.3 Kafka topic 확인

```powershell
docker compose --env-file .env.local `
  -f infra/docker/docker-compose.local.yml `
  exec -T kafka kafka-topics `
  --bootstrap-server localhost:9092 --list
```

메시지 확인:

```powershell
docker compose --env-file .env.local `
  -f infra/docker/docker-compose.local.yml `
  exec -T kafka kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic market-data-fetched `
  --from-beginning --timeout-ms 5000
```

### 12.4 DB 확인

컨테이너의 실제 user/database 환경값을 사용하거나 `.env.local.example`의 기본 개발 구성을 기준으로 접속한다.

```sql
SELECT asset_id, date, close, volume, source
FROM market_prices
ORDER BY asset_id, date, source;

SELECT job_id, ticker, source, status, raw_s3_path, message
FROM collection_logs
ORDER BY created_at DESC;

SELECT source, count(*)
FROM market_prices
WHERE asset_id = 1
GROUP BY source;
```

같은 기간을 두 번 수집한 뒤 같은 source의 row count가 증가하지 않으면 upsert 멱등성이 확인된다.

### 12.5 S3 확인

AWS CLI가 해당 계정으로 인증된 상태에서:

```powershell
aws s3api head-object `
  --bucket $env:AWS_S3_BUCKET `
  --key "market-prices/005930.KS/2024/2024-01-01_2024-01-04.json"
```

검증 포인트:

- Content-Type이 `application/json`
- envelope의 `assetId`, `ticker`, `source`, 기간 존재
- YAHOO이면 `payload.chart` 존재
- 민감한 AWS 값 자체는 출력하지 않는다.

### 12.6 curl/Postman 예시

로그인 후 받은 access token을 로컬 변수에만 보관한다.

```bash
ACCESS_TOKEN="<access-token>"
```

수집 요청:

```bash
curl -X POST http://localhost:8080/api/collector/market-prices/fetch \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "assetId": 1,
    "ticker": "005930.KS",
    "startDate": "2024-01-01",
    "endDate": "2024-01-04"
  }'
```

job 조회:

```bash
curl http://localhost:8080/api/collector/market-prices/jobs/<jobId> \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

가격 조회:

```bash
curl "http://localhost:8080/api/market-prices/1?startDate=2024-01-01&endDate=2024-01-04"
```

Postman에서는 로그인 응답의 access token을 환경변수에 저장하고 Authorization 탭에서 Bearer Token으로 참조하면 된다.

---

## 13. 실제 발생한 문제와 해결

### 13.1 처음에 왜 2024년 CSV 데이터만 보였나

예제 CSV에는 2024년 초의 소수 행만 들어 있었다. Yahoo 요청이 매번 실패하자 `FallbackMarketDataClient`가 정상적으로 CSV로 전환했고, 해당 기간의 CSV 행이 DB에 저장됐다. 따라서 화면과 API에는 CSV가 가진 2024년 샘플만 나타났다.

이것은 DB 조회 오류가 아니라 fallback이 의도대로 작동한 결과였다.

### 13.2 HTTP 429 원인

backend 컨테이너에서 동일 Yahoo URL을 직접 호출해 비교했다.

```text
Java 계열 User-Agent → HTTP 429
브라우저형 User-Agent → HTTP 200 + 정상 JSON
```

Yahoo가 자동화 요청을 제한하면서 Java 기본 요청을 rate-limit/bot 요청으로 판단한 것이 원인이었다.

### 13.3 User-Agent가 왜 해결책인가

`YahooFinanceMarketDataClient.configuredBuilder()`에 다음 개념의 헤더를 추가했다.

```http
User-Agent: Mozilla/5.0 ... FinRiskRadar/1.0
Accept: application/json
```

그 결과 실제 Docker E2E에서:

```text
JOB_STATUS=COMPLETED
JOB_SOURCE=YAHOO
PRICE_API_SOURCES=YAHOO
FALLBACK_LOG_PRESENT=False
```

S3 envelope도 `source=YAHOO`, `payload.chart` 존재를 확인했다.

### 13.4 CSV fallback이 정상 동작했다는 의미

Yahoo 문제가 해결되기 전에도 전체 job은 CSV로 COMPLETED됐다. 이는 다음 구성 요소가 실제로 동작했다는 증거다.

- Yahoo 예외가 표준 `MarketDataClientException`으로 변환됨
- Fallback Client가 CSV로 전환함
- CSV raw가 S3에 저장됨
- CSV가 정제되어 DB에 upsert됨
- job과 fetched 이벤트가 정상 완료됨

장애가 있었지만 서비스가 완전히 멈추지 않았다는 점이 fallback의 가치다.

---

## 14. 면접·포트폴리오 설명

### 14.1 1분 설명

> “Day 5에서는 주식과 리츠의 일별 OHLCV 수집 파이프라인을 구현했습니다. 사용자가 수집 API를 호출하면 CollectionLog를 생성하고 Kafka requested 이벤트를 발행해 HTTP 요청과 실제 외부 수집을 분리했습니다. Consumer는 Yahoo Finance를 우선 호출하고 실패하면 classpath CSV로 fallback합니다. 정제 전 원본은 재처리와 감사 목적으로 S3에 먼저 저장하고, 검증된 가격은 `(assetId, date, source)` 기준 PostgreSQL upsert로 저장했습니다. 프론트에서는 React Query로 job 상태를 polling하고 완료되면 가격을 다시 조회해 Recharts로 보여줍니다. 실제 개발 중 Yahoo가 Java User-Agent를 HTTP 429로 차단하는 문제를 재현해 브라우저형 User-Agent와 retry/backoff를 추가했고, Yahoo와 CSV 양쪽 경로를 Docker E2E로 검증했습니다.”

### 14.2 기술적으로 깊게 설명

> “수집 명령과 결과 이벤트를 분리한 event-driven 구조입니다. 요청 단계에서는 사용자·자산·ticker·기간을 검증하고 UUID CollectionLog를 REQUESTED로 저장한 뒤 Kafka broker ack를 확인하고 202를 반환합니다. Consumer는 조건부 상태 전이로 중복 job 실행을 막고, 공급자 추상화인 MarketDataClient를 통해 Yahoo primary/CSV fallback을 선택합니다. raw payload를 먼저 S3 JSON envelope로 보존한 뒤 거래소 timezone, null OHLC, 기간 경계와 가격 불변식을 검증해 PriceBar로 정규화합니다. PostgreSQL은 native batch upsert로 멱등성을 확보하고, 조회 시 MANUAL, YAHOO, CSV 우선순위를 적용합니다. 현재는 DB-Kafka dual write에 outbox가 없고 결과 topic consumer도 없다는 경계가 있으며, 운영화 단계에서는 outbox, DLT, bucket versioning, 자동 스케줄 수집으로 확장할 수 있습니다.”

### 14.3 “왜 Kafka를 썼나요?”

> “Yahoo와 S3 호출은 지연과 실패 가능성이 있어 HTTP 요청 안에서 처리하면 응답 시간이 길고 장애가 전파됩니다. Kafka로 작업을 분리해 API는 jobId를 빠르게 반환하고 Consumer가 비동기로 실행하게 했습니다. 또한 향후 위험 탐지, 알림, AI 리포트가 fetched 이벤트를 독립적으로 구독할 수 있어 기능 간 결합도가 낮아집니다.”

### 14.4 “왜 S3와 DB를 둘 다 쓰나요?”

> “두 저장소의 역할이 다릅니다. S3에는 정제 전 원본을 보존해 감사, 장애 분석, 재처리를 가능하게 하고, PostgreSQL에는 검증된 OHLCV만 저장해 기간 조회와 분석 계산을 빠르게 합니다. 원본 보존과 읽기 성능을 동시에 만족시키기 위한 분리입니다.”

### 14.5 “왜 Yahoo Finance를 썼나요?”

> “초기 MVP에서 API key 없이 한국과 미국 주식의 일별 OHLCV를 같은 형태로 조회할 수 있어 선택했습니다. 다만 공식 SLA가 없는 비공식 endpoint 성격을 인지하고 MarketDataClient 인터페이스로 공급자 교체가 가능하게 했으며, CSV fallback과 raw 보존으로 장애 위험을 줄였습니다.”

### 14.6 “CSV fallback은 왜 넣었나요?”

> “외부 API 장애가 전체 수집 실패로 바로 이어지는 것을 줄이고, 인터넷과 무관하게 정제·저장 경로를 재현 가능하게 테스트하기 위해 넣었습니다. 실제 Yahoo 429 장애 때 CSV 경로가 S3 저장부터 DB upsert, fetched 이벤트까지 정상 완료되어 설계 효과를 확인했습니다.”

### 14.7 꼬리 질문에 대비할 현재 한계

| 질문 | 정직한 답변과 확장 방향 |
|---|---|
| Kafka와 DB가 동시에 성공한다는 보장이 있나요? | 현재는 없다. 요청 발행 실패 시 log를 FAILED로 보정한다. 운영 단계에서는 transactional outbox를 고려한다. |
| Consumer 실패 재시도는요? | Yahoo HTTP는 3회 backoff 재시도한다. Kafka DLT/retry 정책은 아직 없으며 다음 운영 단계 과제다. |
| S3 원본 이력은 모두 남나요? | 동일 ticker·기간 key는 덮어쓴다. bucket versioning 또는 jobId key 확장이 필요하다. |
| Yahoo 약관/안정성 문제는요? | 공식 SLA가 없으므로 대체 공급자 구현과 rate-limit 정책이 필요하다. 인터페이스 분리는 이를 위한 준비다. |
| 수동 보정은 구현됐나요? | `MANUAL` source와 조회 우선순위만 준비했고 업로드 API는 Day 5 범위 밖이다. |

---

## 15. 복습 체크리스트

- [ ] 왜 POST가 200이 아니라 202인지 설명할 수 있다.
- [ ] CollectionLog의 네 상태 전이를 말할 수 있다.
- [ ] Kafka 세 topic의 producer/consumer를 구분할 수 있다.
- [ ] Yahoo 실패가 CSV로 전환되는 클래스 흐름을 말할 수 있다.
- [ ] 왜 raw S3 저장이 정제보다 먼저인지 설명할 수 있다.
- [ ] `(asset_id, date, source)` unique와 upsert의 관계를 설명할 수 있다.
- [ ] MANUAL > YAHOO > CSV 우선순위를 설명할 수 있다.
- [ ] User-Agent 없이 Yahoo가 429를 반환한 원인을 설명할 수 있다.
- [ ] 프론트의 job polling과 query invalidation을 설명할 수 있다.
- [ ] 현재 구현의 한계(outbox, DLT, S3 versioning)를 숨기지 않고 말할 수 있다.

이 체크리스트를 막힘없이 설명할 수 있으면 Day 5의 핵심 설계를 이해한 것이다.
