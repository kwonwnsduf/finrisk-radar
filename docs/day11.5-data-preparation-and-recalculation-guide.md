# Day11.5 — 데이터 수집과 리스크 계산 연결 구현

## 이 문서에서 설명하는 것

Day11.5는 새로운 위험 분석 기능을 하나 더 만든 작업이 아니다. 기존에 따로 움직이던 다음 세 흐름을 하나의 작업으로 연결한 변경이다.

```text
① DART 재무 데이터 수집
② Risk Score 계산
③ Day11 문서 후보 승인 후 재계산
```

이 문서는 완성된 구조를 나열하기보다 다음 질문에 답하는 방식으로 설명한다.

1. 기존 코드에서는 어디가 끊겨 있었는가?
2. 어떤 코드를 어떻게 바꿨는가?
3. 추가된 코드가 중간 연결을 어떻게 구현하는가?
4. Calculate와 Day11 후보 승인은 실제로 어떻게 흘러가는가?
5. 채권 발행사와 REIT는 어디서 다르게 처리되는가?

빠르게 읽으려면 다음 순서가 좋다.

```text
1장 변경 전·후
→ 3장 계산 요청 분기
→ 5장 Job ID 연결
→ 6장 수집 완료 후 계산 재개
→ 13장 전체 흐름
→ 15장 실제 예시
```

---

## 1. 가장 먼저 보는 변경 전·후

### 변경 전

Calculate 버튼은 데이터가 있는지 확인하지 않고 바로 Risk 계산 메시지를 보냈다.

```text
Calculate 클릭
→ Risk Job 생성(REQUESTED)
→ risk-score-requested
→ Risk 계산 실행
→ financial_metrics/reit_metrics가 없으면 실패
```

재무 데이터 수집도 별도 흐름으로는 존재했다.

```text
재무 수집 요청
→ DART 호출
→ financial_metrics 저장
→ financial-data-fetched
→ 종료
```

두 흐름이 각각 존재했지만, 다음 연결이 없었다.

```text
Risk 계산에 데이터가 없음
→ 재무 수집 실행
→ 저장 완료
→ 원래 Risk 계산 재개
```

Day11 후보 승인도 `RiskCalculationRequestService`를 호출했지만, 기초 데이터가 없으면 같은 지점에서 실패했다.

### 변경 후

```text
Calculate 또는 후보 승인
→ 계산 데이터 존재 여부 확인

데이터가 있으면
  → Risk Job(REQUESTED)
  → 바로 계산

데이터가 없으면
  → Risk Job(COLLECTING)
  → DART 재무 수집
  → 원본 저장
  → financial_metrics 저장
  → REIT이면 reit_metrics 준비
  → 원래 Risk Job을 REQUESTED로 변경
  → 계산 재개
  → RiskScore 저장
```

핵심은 새로운 `RiskDataPreparationService`와 `RiskDataPreparationConsumer`가 수집과 계산 사이를 연결한다는 점이다.

---

## 2. 기존 코드가 끊겼던 정확한 위치

변경 전 `RiskCalculationRequestService`는 자산을 확인한 뒤 무조건 계산 Job을 만들고 `risk-score-requested`를 발행했다.

### 이전 코드

```java
String version =
    asset.getAssetType() == AssetType.REIT
        ? REIT_VERSION
        : CORPORATE_VERSION;

job = jobs.create(userId, assetId, version);

publisher.publishRequested(
    new RiskScoreRequestedEvent(
        job.getJobId(), assetId, userId, Instant.now()));
```

이 코드는 다음 질문을 하지 않았다.

```text
이 자산에 계산 가능한 financial_metrics가 있는가?
REIT라면 reit_metrics가 있는가?
최근 데이터인가?
```

결국 `RiskEvaluationContextFactory`에서 데이터가 없다는 예외가 발생했다.

```java
if (asset.getAssetType() == AssetType.BOND_ISSUER && fs.isEmpty())
  throw new BusinessException(ErrorCode.RISK_FINANCIAL_DATA_NOT_FOUND);

if (asset.getAssetType() == AssetType.REIT && rms.isEmpty())
  throw new BusinessException(ErrorCode.RISK_REIT_METRIC_NOT_FOUND);
```

즉, 문제는 계산 공식이 아니라 **계산 직전에 데이터를 준비하는 단계가 없었다는 것**이다.

---

## 3. 변경 1 — 계산 전에 데이터 준비 여부 확인

### 추가된 코드

`RiskCalculationRequestService`에 `RiskDataPreparationService`를 주입했다.

```java
private final RiskDataPreparationService preparation;
```

계산 요청 시 다음 판단을 추가했다.

```java
boolean collecting =
    !preparation.hasRequiredData(asset, LocalDate.now());
```

그 결과에 따라 Risk Job의 최초 상태와 다음 동작을 나눈다.

```java
job = jobs.create(userId, assetId, version, collecting);

if (collecting) {
  preparation.requestCollection(job, asset);
} else {
  publisher.publishRequested(
      new RiskScoreRequestedEvent(
          job.getJobId(), assetId, userId, Instant.now()));
}
```

### 코드의 의미

```text
collecting = false
→ 이미 계산할 데이터가 있음
→ Risk Job = REQUESTED
→ risk-score-requested 바로 발행

collecting = true
→ 계산할 데이터가 없음
→ Risk Job = COLLECTING
→ 재무 수집부터 요청
```

### 새로 추가된 Risk 상태

이 구분을 표현하기 위해 `RiskCalculationStatus`에 `COLLECTING`을 추가했다.

```java
public enum RiskCalculationStatus {
  COLLECTING,
  REQUESTED,
  RUNNING,
  COMPLETED,
  FAILED
}
```

각 상태의 의미는 다음과 같다.

| 상태 | 실제 의미 |
|---|---|
| `COLLECTING` | 계산 전 필요한 DART 데이터를 수집·저장하는 중 |
| `REQUESTED` | 데이터 준비 완료, 계산 Consumer 대기 중 |
| `RUNNING` | Rule Engine이 실행 중 |
| `COMPLETED` | RiskScore와 RiskSignal 저장 완료 |
| `FAILED` | 수집, 준비, 메시지 발행 또는 계산 실패 |

변경 전에는 `REQUESTED`가 “수집 중”과 “계산 대기”를 구분할 수 없었다. Day11.5에서는 이 둘을 분리했다.

---

## 4. 변경 2 — 어떤 데이터를 준비됐다고 판단하는가

새 클래스:

```text
backend/src/main/java/com/finrisk/radar/risk/service/
RiskDataPreparationService.java
```

### 채권 발행사

`assets.asset_type = BOND_ISSUER`이면 `financial_metrics`의 가장 최근 데이터를 확인한다.

```java
return financials
    .findByAssetIdOrderByYearDescQuarterDesc(asset.getId())
    .stream()
    .findFirst()
    .map(metric -> !periodEndDate(metric).isBefore(oldestAccepted))
    .orElse(false);
```

### REIT

`assets.asset_type = REIT`이면 `reit_metrics`를 확인한다.

```java
return reitMetrics
    .findFirstByAssetIdAndPeriodLessThanEqualOrderByPeriodDesc(
        asset.getId(), asOf)
    .filter(metric -> !metric.getPeriod().isBefore(oldestAccepted))
    .isPresent();
```

### 왜 최신 분기 하나만 강제하지 않는가

DART 공시는 기준일 당일에 항상 최신 분기 자료가 존재하지 않는다. 그래서 다음 순서로 판단한다.

1. 오늘 기준으로 공시가 끝났어야 하는 최신 분기를 계산한다.
2. 해당 분기 종료일을 구한다.
3. 그 시점보다 15개월 이상 오래되지 않은 데이터면 계산 가능한 최근 데이터로 인정한다.

이 처리가 없으면 공시 제출 전 기간마다 Calculate가 반복해서 실패할 수 있다.

---

## 5. 변경 3 — Risk Job과 재무 수집 Job 연결

데이터가 없다는 것을 알아도 “재무 수집이 끝난 뒤 어떤 Risk Job을 다시 시작할지” 알아야 한다.

이를 위해 `riskCalculationJobId`를 재무 수집 전체 흐름에 전달하도록 변경했다.

### 변경 전 이벤트

```java
public record FinancialDataFetchRequestedEvent(
    UUID jobId,
    Long assetId,
    String stockCode,
    Integer year,
    Integer quarter,
    Instant requestedAt) {}
```

여기서 `jobId`는 Financial Collection Job ID다. Risk Job ID는 없었다.

### 변경 후 이벤트

```java
public record FinancialDataFetchRequestedEvent(
    UUID jobId,
    Long assetId,
    String stockCode,
    Integer year,
    Integer quarter,
    UUID riskCalculationJobId,
    Instant requestedAt) {}
```

성공·실패 이벤트에도 같은 필드를 추가했다.

```java
public record FinancialDataFetchedEvent(
    UUID jobId,                 // 재무 수집 Job
    Long assetId,
    // ...수집 결과...
    UUID riskCalculationJobId,  // 다시 시작할 Risk Job
    Instant completedAt) {}
```

### DB에도 연결 ID 저장

```sql
ALTER TABLE financial_collection_logs
  ADD COLUMN risk_calculation_job_id UUID
  REFERENCES risk_calculation_jobs(job_id);
```

### 두 UUID를 구분해야 한다

```text
Financial Job ID
→ DART 수집 작업 자체의 상태를 추적

Risk Job ID
→ 수집이 끝난 뒤 재개할 계산 작업을 추적
```

예시:

```text
Risk Job       = R-100 (COLLECTING)
Financial Job  = F-200 (RUNNING)

F-200이 완료 이벤트를 발행할 때
riskCalculationJobId=R-100을 같이 전달

RiskDataPreparationConsumer가 R-100을 찾아 계산 재개
```

일반 재무 수집 API에서 요청한 작업은 `riskCalculationJobId=null`이다. 따라서 재무 데이터만 저장하고 Risk 계산은 자동 실행하지 않는다.

---

## 6. 변경 4 — 수집 완료 이벤트로 계산 재개

새 클래스:

```text
backend/src/main/java/com/finrisk/radar/risk/kafka/
RiskDataPreparationConsumer.java
```

이 클래스가 Day11.5의 핵심 연결부다.

### 구독하는 토픽

```java
@KafkaListener(
    topics = FinancialDataTopics.FETCHED,
    groupId = "finrisk-risk-data-preparation")
```

`FinancialDataTopics.FETCHED`의 실제 값은 다음과 같다.

```java
public static final String FETCHED = "financial-data-fetched";
```

### `financial-data-fetched`는 누가 발행하는가

```text
FinancialDataFetchConsumer
→ FinancialDataCollectionService.collect()
→ DART 원본과 financial_metrics 저장 완료
→ FinancialDataEventPublisher.publishFetched()
→ financial-data-fetched
```

즉, `fetched`는 파일이나 메서드 이름이 아니라 **“재무 데이터 저장 완료” Kafka 토픽 이름**이다.

### Consumer 내부 실행 순서

#### 1. Risk 계산과 연결된 수집인지 확인

```java
if (event.riskCalculationJobId() == null) return;
```

`null`이면 독립적인 재무 수집이므로 계산을 시작하지 않는다.

#### 2. 원래 Risk Job 조회

```java
RiskCalculationJob job =
    jobs.get(event.riskCalculationJobId());
```

#### 3. 중복 Kafka 이벤트 방어

```java
if (terminal(job)) return;
```

이미 `COMPLETED` 또는 `FAILED`면 같은 이벤트가 다시 와도 처리하지 않는다.

#### 4. REIT 데이터 준비

```java
preparation.prepareCollectedData(event, asset);
```

채권 발행사는 `financial_metrics` 저장이 끝났으므로 추가 변환이 없다. REIT는 계산기가 요구하는 `reit_metrics` 형식으로 최소 proxy를 만든다.

#### 5. 상태 전이

```java
if (!jobs.markRequested(job.getJobId())) return;
```

실제 DB update는 다음 의미다.

```sql
UPDATE risk_calculation_jobs
SET status = 'REQUESTED'
WHERE job_id = :id
  AND status = 'COLLECTING';
```

동일 이벤트가 두 번 도착해도 첫 번째 처리만 `COLLECTING → REQUESTED`에 성공한다.

#### 6. 실제 계산 메시지 발행

```java
publisher.publishRequested(
    new RiskScoreRequestedEvent(
        job.getJobId(),
        job.getAssetId(),
        job.getUserId(),
        Instant.now()));
```

토픽이 다음처럼 연결된다.

```text
financial-data-fetched
→ RiskDataPreparationConsumer
→ risk-score-requested
→ RiskRequestedConsumer
→ RiskCalculationExecutionService
```

---

## 7. 변경 5 — DART 조회 결과를 정확하게 저장

연결만 해도 실제 DART에 요청 분기 자료가 없으면 실패한다. 그래서 수집 로직도 함께 변경했다.

### 변경 전

```java
String corpCode =
    corpCodes.findCorpCodeByStockCode(log.getStockCode());

RawDartFinancialStatement raw =
    client.fetchFinancialStatement(
        corpCode, log.getYear(), log.getQuarter());

metrics.upsert(
    asset, log.getYear(), log.getQuarter(), values);
```

문제점:

- 비상장 회사는 ticker로 corpCode를 찾을 수 없다.
- 요청 분기 자료가 없으면 바로 실패한다.
- fallback으로 이전 분기를 찾더라도 요청 분기로 저장할 위험이 있다.

### 변경 후

```java
String corpCode = corpCodes.findCorpCode(asset);

RawDartFinancialStatement raw =
    client.fetchLatestFinancialStatement(
        corpCode,
        log.getYear(),
        log.getQuarter(),
        7);

metrics.upsertCollected(
    asset,
    raw.year(),
    raw.quarter(),
    values);
```

달라진 점:

1. Asset에 직접 지정한 DART corpCode를 우선 사용한다.
2. 요청 분기부터 과거 7개 분기까지, 최대 8개 분기를 탐색한다.
3. 요청한 연·분기가 아니라 DART에서 실제로 찾은 연·분기를 저장한다.
4. `periodEndDate`, `reportCode`, `fetchedAt`도 함께 저장한다.

예시:

```text
요청              2026 Q1
DART 실제 반환     2025 Q4

변경 전 위험       2026 Q1 데이터로 잘못 저장하거나 실패
변경 후            2025 Q4 / 2025-12-31 / fallbackUsed=true
```

---

## 8. 변경 6 — JTBC DART 기업코드 처리

JTBC Asset의 ticker는 `JTBC`지만, OpenDART의 상장 종목코드는 아니다. 기존 ticker 검색으로는 corpCode를 찾을 수 없었다.

### DB 변경

```sql
ALTER TABLE assets
  ADD COLUMN dart_corp_code VARCHAR(20);

UPDATE assets
SET dart_corp_code = '00922702'
WHERE ticker = 'JTBC'
  AND market = 'PRIVATE';
```

### 새 조회 방식

```java
public String findCorpCode(Asset asset) {
  if (asset.getDartCorpCode() != null
      && !asset.getDartCorpCode().isBlank()) {
    return asset.getDartCorpCode();
  }
  return findCorpCodeByStockCode(asset.getTicker());
}
```

```text
JTBC
→ assets.dart_corp_code = 00922702 사용

상장 REIT
→ 직접 corpCode가 없으면 ticker로 검색
```

Day11 문서 수집 코드도 같은 방식으로 수정했다.

```diff
- corpCodes.findCorpCodeByStockCode(asset.getTicker())
+ corpCodes.findCorpCode(asset)
```

따라서 JTBC는 재무 수집뿐 아니라 OpenDART 문서 수집도 같은 공식 기업코드를 사용한다.

---

## 9. 변경 7 — REIT 계산용 중간 데이터 생성

채권 발행사와 REIT는 `assets.asset_type`으로 구분한다.

```text
BOND_ISSUER → financial_metrics 필요
REIT        → reit_metrics 필요
```

DART 일반 재무제표를 저장하는 곳은 공통 `financial_metrics`다. 하지만 REIT Rule Engine은 `reit_metrics`를 읽는다. 따라서 REIT에는 한 번 더 중간 변환이 필요하다.

```java
if (asset.getAssetType() != AssetType.REIT) return;
```

REIT일 때 생성하는 최소 proxy:

```text
bookAssetValue    = totalDebt + totalEquity
ltv               = totalDebt / bookAssetValue × 100
totalBorrowings   = totalDebt
interestExpense   = financial interestExpense
rentalIncome      = financial revenue
availableLiquidity = cash
sourceType        = OPEN_DART_FINANCIAL_PROXY
```

예시 코드:

```java
BigDecimal bookAssetValue =
    add(financial.getTotalDebt(), financial.getTotalEquity());

BigDecimal ltv =
    ratio(financial.getTotalDebt(), bookAssetValue);

reitMetrics.save(
    ReitMetric.create(
        asset.getId(),
        period,
        ltv,
        bookAssetValue,
        // ...
        "OPEN_DART_FINANCIAL_PROXY",
        "DART:" + event.corpCode()
            + ":" + event.year()
            + ":" + event.quarter(),
        financial.getFetchedAt()));
```

### 이 proxy가 해결하는 것

```text
기존: financial_metrics 저장 성공
      → reit_metrics 없음
      → REIT 계산 실패

변경: financial_metrics 저장 성공
      → 최소 reit_metrics 생성
      → 계산 실행 가능
```

### 이 proxy가 해결하지 않는 것

일반 재무제표에서 신뢰성 있게 얻을 수 없는 다음 값은 임의로 채우지 않는다.

- 감정평가액
- 공실률
- 배당성향
- 리파이낸싱 금리
- FX 헤지 정산액
- Cash Trap 기준
- Default LTV 기준
- 해외 현금 의존도

따라서 REIT 결과는 `PARTIAL`일 수 있다. 프론트는 이 경우 0점이 안전을 의미하지 않는다고 표시한다.

---

## 10. Day11 승인과 Day11.5 연결

Day11 후보 승인 흐름의 시작은 그대로다.

```text
CreditEventReviewService.approve()
→ CreditEvent 저장
→ Candidate 상태 APPROVED
→ CandidateApprovedNotification 발행
```

승인 트랜잭션이 commit된 다음 실행된다.

```java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT)
public void approved(CandidateApprovedNotification event) {
  request(
      event.candidateId(),
      event.assetId(),
      event.reviewerUserId());
}
```

이후 `DocumentRiskRecalculationCoordinator`가 공통 진입점인 `RiskCalculationRequestService.request()`를 호출한다.

```text
후보 승인
→ CreditEvent가 먼저 DB에 확정
→ RiskCalculationRequestService
→ 데이터 있으면 즉시 계산
→ 데이터 없으면 COLLECTING부터 시작
```

CreditEvent가 먼저 저장되므로 새 계산의 `RiskEvaluationContextFactory`가 승인된 사건을 읽는다. 그 결과 `creditEventScore`, 관련 `RiskSignal`, 최종 점수가 바뀔 수 있다.

---

## 11. 변경 8 — 승인 재계산 상태가 DB에 남도록 수정

기존 Coordinator는 다음 상태까지만 기록했다.

```text
REQUESTED 또는 DEFERRED
```

그리고 `request()`가 같은 클래스 내부 호출이어서 트랜잭션 프록시가 적용되지 않는 경우 상태 변경이 유실될 여지도 있었다.

Day11.5에서는 상태 변경 뒤 명시적으로 저장한다.

```java
c.recalculationRequested(job.getJobId());
candidates.save(c);
```

실제 Risk Job 결과도 후보에 다시 반영한다.

```java
if (job.getStatus() == RiskCalculationStatus.COMPLETED) {
  candidate.recalculationCompleted();
} else if (job.getStatus() == RiskCalculationStatus.FAILED) {
  candidate.recalculationFailed(job.getFailureMessage());
}
```

추가된 추적 필드:

```text
recalculation_attempt_count
recalculation_last_attempted_at
recalculation_last_error
```

자동 재시도:

```text
DEFERRED
→ 60초마다 다시 요청
→ 최대 5회
→ 계속 실패하면 FAILED
```

수동 재시도 API:

```http
POST /api/admin/credit-event-candidates/{id}/recalculate
```

---

## 12. 변경 9 — 프론트에서 중간 상태와 결과 표시

### Calculate 화면

기존 활성 상태:

```ts
const active =
  job?.status === "REQUESTED" ||
  job?.status === "RUNNING";
```

변경 후:

```ts
const active =
  job?.status === "COLLECTING" ||
  job?.status === "REQUESTED" ||
  job?.status === "RUNNING";
```

상태별 화면 의미:

```text
COLLECTING → DART 데이터를 수집하고 저장하는 중
REQUESTED  → 데이터 준비 완료, 계산 대기 중
RUNNING    → 저장된 데이터로 계산 중
```

현재 실행한 Calculate Job은 2초마다 조회한다.

### 승인 후 관리자 화면

기존 관리자 화면은 승인 API 호출 후 후보 목록만 다시 읽었다. 새 Risk Job이 생성됐는지, 끝났는지, 실패했는지 보여주지 않았다.

변경 후에는 다음을 추적한다.

```text
후보 승인 응답
→ Candidate 조회
→ recalculationJobId 확인
→ Risk Job 2초 폴링
→ COMPLETED면 “새 점수 보기” 표시
→ FAILED면 오류와 “다시 계산” 표시
```

같은 관리자 화면에서 문서 수집도 직접 실행할 수 있다.

```text
자산 선택
→ 최근 1일 NAVER_NEWS + OPEN_DART 수집 요청
→ 각 Document Collection Job 상태 2초 폴링
```

### 자산 상세 화면

후보 승인에 의한 계산은 다른 화면에서 시작되므로 자산 상세 화면이 이를 알 수 없었다. 다음 자동 갱신을 추가했다.

```text
15초마다 최신 RiskScore 조회
브라우저 focus 시 즉시 최신 RiskScore 조회
```

---

## 13. 전체 구현 흐름

### 13.1 Calculate 버튼

```text
RiskOverview.calculate()
→ POST /api/risks/assets/{assetId}/calculations
→ RiskController.calculate()
→ RiskCalculationRequestService.request()
→ RiskDataPreparationService.hasRequiredData()

데이터 있음
→ Risk Job(REQUESTED)
→ risk-score-requested

데이터 없음
→ Risk Job(COLLECTING)
→ RiskDataPreparationService.requestCollection()
→ FinancialDataRequestService.requestForRisk()
→ Financial Collection Job 생성
→ financial-data-fetch-requested
→ FinancialDataFetchConsumer
→ FinancialDataCollectionService.collect()
→ DART 조회
→ 원본 저장
→ financial_metrics 저장
→ financial-data-fetched
→ RiskDataPreparationConsumer.collected()
→ REIT이면 reit_metrics proxy 저장
→ Risk Job COLLECTING → REQUESTED
→ risk-score-requested

공통 계산
→ RiskRequestedConsumer
→ RiskCalculationExecutionService.execute()
→ RiskEvaluationContextFactory.create()
→ RiskScoringEngine.calculate()
→ RiskResultPersistenceService.persist()
→ RiskSignal + RiskScore 저장
→ Risk Job COMPLETED
```

### 13.2 Day11 후보 승인

```text
관리자 승인
→ CreditEventReviewService.approve()
→ CreditEvent 저장
→ CandidateApprovedNotification
→ DocumentRiskRecalculationCoordinator
→ RiskCalculationRequestService.request()
→ 이후 Calculate와 동일한 흐름
```

Calculate와 승인은 별도 계산기를 사용하는 것이 아니다. 둘 다 `RiskCalculationRequestService`부터 같은 수집·계산 흐름을 공유한다.

---

## 14. 실패 흐름

### DART 수집 실패

```text
FinancialDataCollectionService 예외
→ Financial Collection Job FAILED
→ financial-data-fetch-failed
→ RiskDataPreparationConsumer.collectionFailed()
→ 연결된 Risk Job FAILED
→ RISK_DATA_COLLECTION_FAILED
```

### REIT 중간 데이터 준비 실패

```text
prepareCollectedData() 실패
→ Risk Job FAILED
→ RISK_DATA_PREPARATION_FAILED
```

### 계산 메시지 발행 실패

```text
risk-score-requested 발행 실패
→ Risk Job FAILED
→ RISK_KAFKA_PUBLISH_FAILED
```

### 문서 승인 재계산 요청 실패

```text
Candidate DEFERRED
→ 자동 재시도
→ 최대 횟수 초과 시 FAILED
→ 관리자 수동 재시도 가능
```

---

## 15. 실제 동작 예시

### JTBC

```text
Asset
  id=10
  ticker=JTBC
  assetType=BOND_ISSUER
  dartCorpCode=00922702

Calculate
→ 최근 financial_metrics 없음
→ Risk Job COLLECTING
→ corpCode 00922702로 DART 조회
→ 실제 재무 데이터 financial_metrics 저장
→ financial-data-fetched
→ Risk Job REQUESTED
→ corporate-risk-v1 계산
→ RiskScore 저장
```

### 제이알글로벌리츠

```text
Asset
  id=8
  ticker=348950
  assetType=REIT

Calculate
→ 최근 reit_metrics 없음
→ Risk Job COLLECTING
→ DART 최신 가용 분기 탐색
→ 2026 Q1이 없으면 2025 Q4 사용 가능
→ financial_metrics 저장
→ financial-data-fetched
→ OPEN_DART_FINANCIAL_PROXY reit_metrics 생성
→ Risk Job REQUESTED
→ reit-risk-v1 계산
→ PARTIAL 가능성을 포함한 RiskScore 저장
```

### Day11 후보 승인

```text
문서에서 REFINANCING_FAILURE 후보 생성
→ 관리자 승인
→ CreditEvent 생성
→ 재계산 자동 요청
→ 필요한 데이터가 없으면 먼저 수집
→ 새 계산 Context에 승인 CreditEvent 포함
→ Credit Event Rule 점수 재평가
→ 새 RiskScore 저장
```

---

## 16. 변경된 파일을 목적별로 보기

### 계산 진입과 데이터 판단

| 파일 | 변경 의미 |
|---|---|
| `RiskCalculationRequestService` | 데이터 유무에 따라 수집 또는 계산으로 분기 |
| `RiskDataPreparationService` | 데이터 최신성 확인, 수집 요청, REIT proxy 생성 |
| `RiskCalculationStatus` | `COLLECTING` 추가 |
| `RiskCalculationJob` | collecting Job 생성 지원 |
| `RiskCalculationJobService` | `COLLECTING → REQUESTED` 상태 전이 |

### 재무 수집 연결

| 파일 | 변경 의미 |
|---|---|
| `FinancialDataRequestService` | Risk Job ID가 있는 수집 요청 지원 |
| `FinancialCollectionLog` | Risk Job ID 저장 |
| `FinancialDataFetchRequestedEvent` | `riskCalculationJobId` 전달 |
| `FinancialDataFetchedEvent` | 성공 후 같은 ID 반환 |
| `FinancialDataFetchFailedEvent` | 실패 후 같은 ID 반환 |
| `FinancialDataFetchConsumer` | 성공·실패 이벤트에 Risk Job ID 유지 |
| `RiskDataPreparationConsumer` | 수집 결과를 받아 원래 계산 재개 |

### DART 정확성

| 파일 | 변경 의미 |
|---|---|
| `Asset` | 직접 DART corpCode 저장 |
| `DartCorpCodeService` | 직접 corpCode 우선, 없으면 ticker 검색 |
| `DartClient` | 과거 분기 fallback 조회 |
| `FinancialDataCollectionService` | 실제 반환 분기 저장 |
| `FinancialMetric` | 기간 종료일, 보고서 코드, 수집 시각 저장 |
| `DocumentCollectionExecutionService` | JTBC 문서 수집도 직접 corpCode 사용 |

### Day11 승인 추적

| 파일 | 변경 의미 |
|---|---|
| `CreditEventCandidate` | 시도 횟수, 오류, 완료·실패 상태 관리 |
| `DocumentRiskRecalculationCoordinator` | 저장 보장, 결과 동기화, 자동·수동 재시도 |
| `CreditEventCandidateAdminController` | 수동 재계산 API |

### 프론트엔드

| 파일 | 변경 의미 |
|---|---|
| `risk-overview.tsx` | `COLLECTING` 표시 및 최신 점수 자동 갱신 |
| `reit-risk-overview.tsx` | 동일 처리와 PARTIAL 경고 |
| `credit-event-candidate-list.tsx` | 문서 수집, 승인 재계산 추적, 결과 링크 |
| `documents.ts` | 수집 Job·후보·재시도 API 추가 |

### DB Migration

| Migration | 변경 의미 |
|---|---|
| `V13__connect_risk_data_collection.sql` | corpCode, Risk/Financial Job 연결, `COLLECTING` |
| `V14__track_document_recalculation_retries.sql` | 승인 재계산 시도·시간·오류 추적 |

---

## 17. 핵심 정리

Day11.5에서 구현한 중간 과정은 다음 한 줄이다.

```text
Risk Job ID를 재무 수집 이벤트에 함께 전달하고,
financial-data-fetched를 받은 Consumer가 그 Risk Job을 다시 계산 단계로 넘긴다.
```

코드 기준 핵심 연결은 다음 네 곳이다.

```text
RiskCalculationRequestService
  데이터 없음 판단 및 COLLECTING 생성

FinancialDataRequestService
  riskCalculationJobId를 재무 수집에 전달

FinancialDataFetchConsumer
  저장 완료 이벤트에 riskCalculationJobId를 유지

RiskDataPreparationConsumer
  COLLECTING → REQUESTED 후 risk-score-requested 발행
```

따라서 지금은 다음이 하나의 연속된 작업이다.

```text
Calculate 또는 후보 승인
→ 실제 데이터 확인
→ 부족하면 실제 수집
→ DB 저장
→ 계산 재개
→ 새 점수 저장 및 화면 반영
```
