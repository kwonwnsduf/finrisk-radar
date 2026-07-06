# Day 5 시장 가격 데이터 파이프라인

## 개요

Day 5는 주식과 리츠의 일별 OHLCV를 수동 요청으로 수집한다. 채권, 재무제표, 공시 데이터는 가격 데이터와 성격과 갱신 주기가 다르므로 Day 8 DART 연동에서 처리한다.

Yahoo Finance는 한국과 미국 종목을 한 형식으로 조회할 수 있고 API key가 필요하지 않아 초기 가격 공급자로 선택했다. 공식 SLA가 없는 외부 서비스이므로 호출 실패나 데이터 부재 시 저장소의 CSV를 사용한다. CSV fallback은 개발·테스트 재현성과 외부 장애 시 최소 가용성을 제공한다.

## 아키텍처

```text
사용자
→ POST /api/collector/market-prices/fetch
→ Kafka market-data-fetch-requested
→ Collector Consumer
→ Yahoo Finance
→ 실패 시 CSV fallback
→ S3 raw data 저장
→ PostgreSQL market_prices upsert
→ Kafka market-data-fetched 또는 collection-failed
→ GET /api/market-prices/{assetId}
→ Frontend Recharts 가격 차트
```

Kafka는 HTTP 요청과 외부 수집 작업을 분리한다. POST는 UUID jobId와 REQUESTED 상태를 즉시 반환하고 Consumer가 실제 작업을 수행한다. 요청 이벤트는 at-least-once로 처리할 수 있으므로 `(asset_id, date, source)` upsert와 CollectionLog 상태 전이로 중복을 방어한다.

원본은 정제 전에 S3에 저장한다. 이후 정제나 DB 저장이 실패해도 원본으로 문제를 분석하고 재처리할 수 있다. PostgreSQL에는 조회와 차트에 필요한 검증된 OHLCV만 저장해 애플리케이션의 읽기 모델을 단순하게 유지한다.

처리 순서는 다음과 같다.

```text
Yahoo/CSV raw 수집 → S3 저장 → 정제/검증 → PostgreSQL upsert
→ CollectionLog COMPLETED/FAILED → Kafka 결과 이벤트
```

S3 key는 `market-prices/{ticker}/{startYear}/{startDate}_{endDate}.json`이다. 예시는 `market-prices/005930.KS/2024/2024-01-01_2024-12-31.json`이다. JSON envelope에는 assetId, ticker, source, 요청 기간, 수집 시각과 provider 원본이 포함된다.

## 데이터와 API

`MarketPriceSource`는 `YAHOO`, `CSV`, `MANUAL`이다. 같은 날짜에 여러 source가 있으면 조회 우선순위는 `MANUAL > YAHOO > CSV`다. MANUAL은 향후 관리자 보정과 수동 CSV 업로드를 위한 확장점이며 Day 5에서는 생성하지 않는다.

CollectionLog 상태는 `REQUESTED → RUNNING → COMPLETED | FAILED`다. jobId는 Java 17에서 추가 라이브러리 없이 생성 가능한 UUIDv4를 사용한다. 시간순 UUID가 필요해지면 UUIDv7으로 교체할 수 있다.

공개 API는 다음 세 개뿐이다.

```http
GET /api/market-prices/{assetId}?startDate=2024-01-01&endDate=2024-12-31
POST /api/collector/market-prices/fetch
GET /api/collector/market-prices/jobs/{jobId}
```

가격 조회는 공개다. 수집 요청과 job 조회는 Bearer token이 필요하고 job은 요청자 또는 관리자만 조회할 수 있다.

```json
{
  "assetId": 1,
  "ticker": "005930.KS",
  "startDate": "2024-01-01",
  "endDate": "2024-12-31"
}
```

KOSPI는 `.KS`, KOSDAQ은 `.KQ`를 붙이고 미국 시장은 Asset ticker를 그대로 사용한다. 서버가 assetId와 요청 ticker의 일치를 다시 검증한다.

## 설정과 실행

`.env.local`에 다음 값을 설정한다. 자격증명은 Git에 커밋하지 않는다.

```dotenv
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY=...
AWS_SECRET_KEY=...
AWS_S3_BUCKET=...
```

```powershell
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml up -d --build
```

Swagger UI는 `http://localhost:8080/swagger-ui.html`, 프론트 자산 상세는 `http://localhost:3000/assets/{assetId}`에서 확인한다.

## 테스트

```powershell
cd backend
.\gradlew.bat test

cd ..\frontend
corepack pnpm@10.30.3 typecheck
corepack pnpm@10.30.3 test
corepack pnpm@10.30.3 lint
corepack pnpm@10.30.3 build
```

수동 통합 테스트에서는 다음을 확인한다.

1. POST가 HTTP 202와 UUID jobId를 반환한다.
2. job 상태가 REQUESTED, RUNNING, COMPLETED로 전이한다.
3. S3에 ticker/year key와 raw envelope가 생성된다.
4. 가격 API가 날짜 오름차순으로 중복 없이 반환한다.
5. 같은 기간 재요청 시 행 수가 늘지 않고 값과 updated_at이 갱신된다.
6. Yahoo 실패 시 CSV source로 완료되며 양쪽 모두 실패하면 FAILED가 된다.

## 트러블슈팅

- `AWS S3 configuration is incomplete`: 네 환경변수와 Compose 전달 여부를 확인한다. 애플리케이션은 기동되지만 해당 수집 job은 FAILED가 된다.
- job이 REQUESTED에 머묾: Kafka broker 상태, `market-data-fetch-requested` topic, Consumer group을 확인한다.
- `TICKER_MISMATCH`: Asset의 market과 ticker를 확인한다. 예를 들어 KOSPI `005930`의 요청 ticker는 `005930.KS`다.
- CSV fallback 실패: `backend/src/main/resources/market-data-fallback/{ticker}.csv`의 파일명, 헤더와 요청 기간을 확인한다.
- S3에는 원본이 있지만 DB에 없음: CollectionLog message를 확인하고 동일 요청을 재발행한다. S3 key와 DB upsert는 재처리에 안전하다.

## Day 5 이후 확장

Day 5에는 Scheduler나 CronJob을 구현하지 않는다. 운영화 단계에서 매일 장 마감 이후 Spring `@Scheduled` 또는 Kubernetes CronJob이 대상 자산별 `market-data-fetch-requested` 이벤트를 발행하도록 확장한다. 수동 요청과 자동 요청이 같은 Consumer와 멱등 저장 경로를 재사용하게 한다.
