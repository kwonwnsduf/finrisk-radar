# FinRisk Radar Day 13 코드 흐름 가이드

## 1. 이 문서의 목적

Day 13은 크게 두 기능을 추가한다.

1. 기존 데이터를 근거로 AI Report를 비동기로 생성한다.
2. 자연어를 기존 백테스트 요청 형식으로 변환한 뒤 기존 백테스트 실행 흐름에 연결한다.

이 문서는 화면이나 API 명세보다 백엔드 내부 구현을 먼저 설명한다. 코드를 읽을 때는 다음 질문에 답할 수 있도록 구성했다.

- HTTP 요청이 어느 Controller로 들어오는가?
- 어떤 Service가 검증하고 사용량을 처리하는가?
- 언제 DB에 Job 또는 Report가 생성되는가?
- Kafka에는 언제 무엇을 발행하는가?
- 실제 LLM 호출과 데이터 조회는 어디에서 하는가?
- 실패했을 때 사용량을 언제 복구하고 언제 유지하는가?
- 응답의 `ACCEPTED`는 정확히 무엇이 성공했다는 뜻인가?

프론트엔드는 문서 마지막에서 API 호출, polling, 화면 연결만 간단히 설명한다.

---

## 2. Day 13 전체 구조

### 2.1 AI Report

```text
사용자
  -> ReportController
  -> ReportRequestService
  -> UsageLimitService.reserve()
  -> ReportPersistenceService.create()
  -> ai_reports INSERT 및 COMMIT
  -> ReportEventPublisher
  -> Kafka: report-generation-requested
  -> ReportGenerationConsumer
  -> ReportGenerationService
  -> 유형별 Workflow
  -> Tool로 내부 데이터 조회
  -> OpenAiLlmClient
  -> ReportEvidenceValidator
  -> ReportPersistenceService.complete()
  -> ai_reports COMPLETED
```

### 2.2 자연어 백테스트

```text
사용자 자연어
  -> BacktestController
  -> NaturalLanguageBacktestService
  -> OpenAiLlmClient로 제한된 JSON 파싱
  -> 자산 식별 및 요청 검증
  -> UsageLimitService.reserve(BACKTEST)
  -> BacktestRequestService
  -> BacktestJob 생성
  -> 자연어 파싱 메타데이터 저장
  -> 기존 backtest-requested Kafka 발행
  -> ACCEPTED 응답
  -> 기존 Backtest Consumer가 실제 계산
```

중요한 차이는 AI Report는 LLM으로 분석 결과를 만들고, 자연어 백테스트는 LLM을 백테스트 요청 파서로만 사용한다는 점이다.

---

## 3. 패키지별 역할

### 3.1 `com.finrisk.radar.report`

Report 도메인의 중심 패키지다.

| 클래스 | 역할 |
|---|---|
| `AiReport` | Report의 상태와 결과를 보관하는 JPA Entity |
| `AiReportRepository` | Report 저장, 조회, 중복 검사, 잠금 조회 |
| `ReportType` | 위험 분석, 백테스트 해석, 관심목록 요약 구분 |
| `ReportStatus` | `REQUESTED`, `RUNNING`, `COMPLETED`, `FAILED` |
| `ReportStep` | 사용자에게 보여줄 서버 진행 단계 |

### 3.2 `report.api`

HTTP 요청과 응답 형식을 담당한다.

```text
ReportController
  -> ReportRequestService
  -> ReportQueryService
```

### 3.3 `report.service`

요청 접수, 상태 저장, 실제 생성 Workflow, 조회, 장애 복구를 담당한다.

| 클래스 | 역할 |
|---|---|
| `ReportRequestService` | Report 요청 접수, 사용량 예약, DB 생성, Kafka 발행 |
| `ReportPersistenceService` | 트랜잭션 안에서 Report 생성과 상태 변경 |
| `ReportGenerationService` | Report 유형에 맞는 Workflow 선택 |
| `RiskAnalysisWorkflow` | 위험 Report 생성 |
| `BacktestAnalysisWorkflow` | 완료된 백테스트 해석 Report 생성 |
| `WatchlistSummaryWorkflow` | 관심목록 요약 Report 생성 |
| `ReportEvidenceValidator` | LLM이 존재하지 않는 근거 ID를 만들지 않았는지 검사 |
| `ReportQueryService` | 사용자 소유권을 확인하며 목록과 상세 조회 |
| `ReportDispatchRecoveryScheduler` | 오래 멈춘 `REQUESTED`, `RUNNING` Report 복구 |

### 3.4 `report.tool`

Workflow가 기존 도메인 데이터를 정해진 양만 가져오도록 감싼 조회 도구다. 자유형 LLM Tool Calling이 아니라 서버가 정한 순서대로 직접 호출한다.

### 3.5 `report.llm`

OpenAI Responses API 호출, Structured Outputs, Prompt와 JSON Schema를 담당한다.

### 3.6 `report.event`, `report.kafka`

Report 생성 요청 이벤트와 Kafka 발행·소비·재시도 설정을 담당한다.

---

## 4. DB와 `AiReport` Entity

## 4.1 V16 Migration

`V16__create_ai_reports_and_extend_backtests.sql`은 `ai_reports`를 만들고 `backtest_jobs`에 자연어 파싱 정보를 추가한다.

핵심 FK는 다음과 같다.

```sql
requested_by_user_id BIGINT NOT NULL REFERENCES app_users(id),
asset_id BIGINT REFERENCES assets(id),
backtest_job_id UUID REFERENCES backtest_jobs(job_id)
```

DB FK의 역할은 참조하는 ID가 실제로 존재하는지 보장하는 것이다. Entity에서는 연관 객체를 자동 로딩하지 않도록 ID만 매핑한다.

```java
@Column(name = "requested_by_user_id")
private Long userId;

@Column(name = "asset_id")
private Long assetId;

@Column(name = "backtest_job_id")
private UUID backtestJobId;
```

이 방식의 의미는 다음과 같다.

```text
DB FK
  -> 존재하지 않는 userId, assetId, backtestJobId 저장 차단

Entity의 ID 참조
  -> User, Asset, BacktestJob 자동 로딩 방지
  -> 필요한 Workflow가 Repository 또는 Tool로 명시적으로 조회
```

`ck_ai_reports_target`은 Report 유형별 대상 조합을 DB에서도 검사한다.

```text
RISK_ANALYSIS
  asset_id 필요
  backtest_job_id 없어야 함

BACKTEST_ANALYSIS
  asset_id 없어야 함
  backtest_job_id 필요

WATCHLIST_SUMMARY
  asset_id와 backtest_job_id 모두 없어야 함
```

## 4.2 `AiReport.requested()`

새 Report Entity를 만드는 정적 팩토리 메서드다.

```java
AiReport value = new AiReport();
value.id = UUID.randomUUID();
value.userId = userId;
value.reportType = type;
value.status = ReportStatus.REQUESTED;
value.requestedAt = LocalDateTime.now();
return value;
```

`new AiReport()`는 빈 Java 객체를 만들고, 이어지는 코드가 필요한 필드를 채운다. 아직 DB 저장은 아니다. `reports.saveAndFlush(report)`가 호출될 때 INSERT 된다.

초기 진행 단계는 유형에 따라 다르다.

```java
value.currentStep =
    type == ReportType.RISK_ANALYSIS
        ? ReportStep.ASSET_RESOLUTION
        : ReportStep.RISK_DATA;
```

## 4.3 상태 변경 메서드

### `startOrResume()`

```java
public boolean startOrResume() {
  if (status == COMPLETED || status == FAILED) return false;

  if (status == REQUESTED) {
    status = RUNNING;
    startedAt = LocalDateTime.now();
  }

  attemptCount++;
  return true;
}
```

- 처음 Kafka Consumer가 실행하면 `REQUESTED -> RUNNING`
- 재시도이면 `RUNNING`을 유지하고 `attemptCount` 증가
- 이미 끝난 Report이면 `false`를 반환해 중복 Consumer를 무시

### `advance()`

Report가 실행 중일 때 현재 진행 단계를 바꾼다.

```java
report.advance(ReportStep.AI_ANALYSIS);
```

### `complete()`

제목, 요약, 구조화 JSON, 모델과 토큰 사용량을 넣고 `COMPLETED`로 바꾼다.

### `fail()`

실패 코드, 사용자에게 저장할 안전한 메시지, 재시도 가능 여부와 시간을 기록하고 `FAILED`로 바꾼다.

### `markUsageCompensated()`

```java
public boolean markUsageCompensated() {
  if (usageCompensatedAt != null) return false;
  usageCompensatedAt = LocalDateTime.now();
  return true;
}
```

이 메서드는 Redis 사용량을 직접 복구하지 않는다. 사용량 복구 표시를 DB에 한 번만 기록한다.

```text
첫 호출
  usage_compensated_at = 현재 시간
  true 반환
  -> 호출자가 Redis 사용량 복구

두 번째 호출
  이미 시간이 있음
  false 반환
  -> 중복 복구하지 않음
```

---

## 5. AI Report 요청 접수 흐름

## 5.1 `ReportController`

세 생성 API가 모두 `ReportRequestService.request()`로 모인다.

```text
POST /api/reports/risk
POST /api/reports/backtest
POST /api/reports/watchlist-summary
```

예를 들어 위험 Report는 다음 값을 전달한다.

```java
requests.request(
    principal.userId(),
    request.assetId(),
    null,
    ReportType.RISK_ANALYSIS,
    request.question(),
    idempotencyKey);
```

새 Report를 생성하면 HTTP 202, 기존 요청을 재사용하면 HTTP 200을 반환한다.

```java
return ResponseEntity
    .status(creation.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED)
    .body(...);
```

여기서 `ACCEPTED`는 Report 생성이 끝났다는 뜻이 아니다.

```text
HTTP 202 ACCEPTED
  -> Report DB 생성과 Kafka 발행까지 성공
  -> 실제 LLM Report 생성은 비동기로 진행
```

## 5.2 `ReportRequestService.request()`

동기 요청 접수의 중심 메서드다.

### 1단계: LLM 설정 확인

```java
if (!llm.configured()) {
  throw new BusinessException(ErrorCode.REPORT_LLM_NOT_CONFIGURED);
}
```

API key와 모델이 없으면 사용량을 예약하거나 DB를 만들기 전에 종료한다.

### 2단계: 위험 분석 자산 식별

위험 Report의 `assetId`가 없으면 질문에서 자산을 찾는다.

```java
if (type == ReportType.RISK_ANALYSIS && assetId == null) {
  assetId = assets.resolve(null, question).getId();
}
```

0개면 `ASSET_NOT_FOUND`, 여러 개면 `REPORT_ASSET_AMBIGUOUS`가 발생한다.

### 3단계: 사용량 종류 선택

```java
UsageType usageType =
    type == ReportType.RISK_ANALYSIS
        ? UsageType.RISK_REPORT
        : UsageType.AI_AGENT;
```

```text
RISK_ANALYSIS       -> RISK_REPORT
BACKTEST_ANALYSIS   -> AI_AGENT
WATCHLIST_SUMMARY   -> AI_AGENT
```

### 4단계: Redis 사용량 예약

```java
UsageReservation reservation = usage.reserve(userId, usageType);
```

현재 구현에서 `reserve()`는 Redis 카운터를 즉시 1 증가시킨다. 이후 성공하면 별도의 `confirm()` 없이 그대로 사용량으로 남기고, 실패하거나 중복 요청이면 `release()`로 감소시킨다.

### 5단계: Report DB 생성

```java
creation = persistence.create(
    userId,
    assetId,
    backtestId,
    type,
    question,
    fingerprint(...),
    idempotencyKey,
    usageType,
    reservation);
```

`ReportPersistenceService.create()`는 별도 Spring Bean의 `@Transactional` 메서드다. 메서드가 정상 반환할 때 트랜잭션이 커밋된다.

### 6단계: 중복 요청이면 새 예약 복구

```java
if (creation.reused()) {
  usage.release(reservation);
  return creation;
}
```

기존 Report를 반환했으므로 이번 호출에서 새로 증가시킨 Redis 카운터는 되돌린다.

### 7단계: DB 커밋 후 Kafka 발행

```java
publisher.requested(
    new ReportGenerationRequestedEvent(
        report.getId(),
        userId,
        type,
        report.getPromptVersion(),
        Instant.now()));
```

`ReportPersistenceService.create()`가 끝난 뒤 호출되므로 Kafka 발행은 Report DB 커밋 이후다.

### 8단계: Kafka 발행 실패 보상

```java
catch (ReportEventPublishException exception) {
  persistence
      .failAndMarkCompensation(...)
      .ifPresent(usage::releaseKey);

  throw new BusinessException(ErrorCode.REPORT_EVENT_PUBLISH_FAILED);
}
```

처리 결과:

```text
ai_reports.status = FAILED
usage_compensated_at 기록
Redis 사용량 1 감소
HTTP 오류 반환
```

`usage_compensated_at`이 이미 있으면 reservation key를 반환하지 않아 같은 사용량을 두 번 복구하지 않는다.

---

## 6. 중복 요청 제어

## 6.1 `idempotency_key`

클라이언트가 같은 요청을 다시 전송해도 같은 Report를 돌려주기 위한 명시적 키다.

```sql
CREATE UNIQUE INDEX uq_ai_reports_user_idempotency
ON ai_reports(requested_by_user_id, idempotency_key)
WHERE idempotency_key IS NOT NULL;
```

동일 사용자와 동일 key 조합은 하나만 존재할 수 있다.

## 6.2 `request_fingerprint`

서버가 요청 내용을 SHA-256으로 만든 값이다.

```text
reportType
+ assetId
+ backtestJobId
+ 정규화한 question
-> SHA-256
```

클라이언트가 새로운 `Idempotency-Key`를 보내거나 key를 생략해도 최근 5분 안에 내용이 같은 요청이면 기존 Report를 재사용한다.

## 6.3 사용자 단위 직렬화와 동시 실행 제한

```java
users.findByIdForUpdate(userId)
```

사용자 row를 잠가 같은 사용자의 동시 요청이 중복 검사 구간을 동시에 통과하지 못하게 한다.

```java
count(REQUESTED, RUNNING) >= 1
```

이면 `REPORT_CONCURRENCY_LIMIT`를 발생시킨다.

---

## 7. Report Kafka 흐름

## 7.1 이벤트

`ReportGenerationRequestedEvent`는 다음 값만 전달한다.

```java
UUID reportId;
Long userId;
ReportType reportType;
String promptVersion;
Instant requestedAt;
```

실제 질문이나 문서 본문은 Kafka 메시지에 넣지 않는다. Consumer는 `reportId`로 DB를 다시 조회한다.

## 7.2 Publisher

`ReportEventPublisher.requested()`는 다음 Topic으로 발행한다.

```text
report-generation-requested
```

```java
kafka
    .send(topic, event.reportId().toString(), event)
    .get(5, TimeUnit.SECONDS);
```

`get()`을 호출하므로 Kafka 전송을 요청만 하고 즉시 반환하는 것이 아니라 최대 5초 동안 전송 결과를 기다린다.

## 7.3 Consumer

```java
@KafkaListener(
    topics = ReportTopics.GENERATION_REQUESTED,
    groupId = "finrisk-report-worker")
public void consume(ReportGenerationRequestedEvent event) {
  reports.generate(event.reportId());
}
```

Consumer는 로직을 직접 구현하지 않고 `ReportGenerationService.generate()`에 위임한다.

---

## 8. 비동기 Report 생성 흐름

## 8.1 `ReportGenerationService.generate()`

```java
if (!persistence.begin(id)) return;
```

Report를 잠금 조회한 뒤 `RUNNING`으로 바꾼다. 이미 `COMPLETED` 또는 `FAILED`이면 중복 메시지이므로 종료한다.

다음으로 유형에 맞는 Workflow를 선택한다.

```java
GeneratedReport generated =
    switch (report.getReportType()) {
      case RISK_ANALYSIS -> risk.generate(report);
      case BACKTEST_ANALYSIS -> backtest.generate(report);
      case WATCHLIST_SUMMARY -> watchlist.generate(report);
    };
```

Workflow가 성공하면:

```java
persistence.complete(id, generated);
```

이 호출이 구조화 결과, 요약, 모델, 토큰을 저장하고 `COMPLETED`로 변경한다.

## 8.2 LLM 오류 분류

`LlmClientException`은 `retryable` 값을 가진다.

```text
retryable
  HTTP 429
  HTTP 5xx
  network/timeout

non-retryable
  인증 오류
  잘못된 모델 또는 요청
  refusal
  incomplete
  JSON 오류
  Structured Output 누락
```

재시도 가능한 오류는 `ReportGenerationException(retryable=true)`로 변환해 Kafka Error Handler가 다시 처리하게 한다.

업무 데이터 부족, 잘못된 evidence, JSON 검증 실패 같은 `BusinessException`은 `NonRetryableReportException`으로 변환한다.

---

## 9. Kafka 재시도와 최종 실패

`ReportKafkaErrorConfiguration`은 재시도 횟수와 간격을 설정한다.

```text
첫 실행 실패
-> 1초 후 재시도
-> 다시 실패
-> 2초 후 재시도
-> 최종 실패 처리
```

`ExponentialBackOffWithMaxRetries(2)`이므로 최초 실행 이후 최대 두 번 다시 시도한다.

`NonRetryableReportException`은 재시도하지 않는다.

최종 실패 시:

```java
persistence.fail(
    reportId,
    failureCode,
    failureMessage,
    retryable);
```

Kafka가 이미 접수한 뒤 발생한 비동기 데이터 또는 LLM 실패에는 사용량을 복구하지 않는다.

```text
DB 생성 후 최초 Kafka 발행 실패
  -> 사용량 복구

Kafka 접수 후 Consumer/LLM 실패
  -> 사용량 유지
```

---

## 10. 유형별 Workflow

## 10.1 `RiskAnalysisWorkflow`

실행 순서:

```text
AssetSearchTool
-> RiskDataTool
-> FinancialRiskTool
-> DocumentSearchTool
-> context JSON 생성
-> LlmClient.generate()
-> ReportEvidenceValidator
-> GeneratedReport 반환
```

핵심 코드는 다음과 같다.

```java
Asset asset = assets.resolve(report.getAssetId(), report.getQuestion());
var risk = risks.load(asset.getId());
var financial = financials.load(asset);
var docs = documents.search(asset.getId(), report.getQuestion());
```

LLM에는 서버가 조회한 검증된 context만 전달한다.

```java
llm.generate(
    new LlmRequest(
        prompts.developer(ReportType.RISK_ANALYSIS),
        "Question: " + question + "\nVerified context:\n" + context,
        prompts.schemaName(ReportType.RISK_ANALYSIS),
        prompts.schema(ReportType.RISK_ANALYSIS)));
```

LLM 응답은 바로 저장하지 않고 evidence를 검증한다.

## 10.2 `BacktestAnalysisWorkflow`

`BacktestResultTool`을 통해 사용자 소유권과 `COMPLETED` 상태를 확인한다.

LLM context 제한:

```text
성과 지표
월별 수익 최대 24개
대표 거래 최대 20개
```

LLM은 이미 계산된 백테스트를 해석할 뿐 새로운 백테스트 계산을 하지 않는다.

## 10.3 `WatchlistSummaryWorkflow`

```text
사용자 관심목록 최대 20개
-> 자산별 최신 위험 데이터
-> 관련 RAG 문서
-> 전체 문서 최대 8개
-> LLM 요약
-> evidence 검증
```

한 자산의 위험 데이터가 없으면 전체 요청을 바로 실패시키지 않고 해당 자산을 `riskDataAvailable=false`로 context에 기록한다.

---

## 11. Tool 클래스별 의미

## 11.1 `AssetSearchTool`

- `assetId`가 있으면 ID로 조회
- 없으면 질문에 포함된 자산명 또는 ticker로 조회
- 0개면 자산 없음
- 여러 개면 모호성 오류

## 11.2 `RiskDataTool`

최신 위험 점수와 위험 신호 최대 20개를 가져온다.

## 11.3 `FinancialRiskTool`

- 재무 지표 최대 8개
- 현재부터 24개월 안의 부채 만기 최대 20개
- REIT이면 최신 및 이전 REIT 지표

## 11.4 `DocumentSearchTool`

RAG에서 먼저 최대 20개 후보를 받고 다음 제한을 적용한다.

```text
similarity >= 0.65
문서 하나당 최대 2개 Chunk
전체 최대 8개 Chunk
```

## 11.5 `BacktestResultTool`

- 사용자 역할 조회
- 본인 Job인지 확인
- Job이 `COMPLETED`인지 확인
- 결과가 존재하는지 확인

## 11.6 `WatchlistTool`

인증 사용자의 관심목록을 최대 20개 반환한다.

---

## 12. Evidence 검증

`ReportEvidenceValidator`는 LLM이 출력한 `documentId`, `chunkIndex`, `riskSignalId`가 실제 context에 들어 있던 값인지 확인한다.

```text
LLM evidence의 documentId + chunkIndex
  -> RAG 검색 결과에 실제 존재해야 함

LLM evidence의 riskSignalId
  -> 서버가 전달한 위험 신호 목록에 실제 존재해야 함
```

존재하지 않는 ID이면 `REPORT_EVIDENCE_INVALID`로 실패한다.

문서가 유효하면 서버가 원본 검색 결과에서 다음 값을 다시 덮어쓴다.

```text
sourceName
sourceUrl
publishedAt
similarity
```

따라서 LLM이 URL이나 유사도를 임의로 만들어도 그대로 저장하지 않는다.

---

## 13. LLM 구현

## 13.1 설정

`LlmProperties`는 `app.llm.openai` 설정을 읽는다.

```yaml
app:
  llm:
    openai:
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_LLM_MODEL:}
```

애플리케이션 코드는 모델명을 직접 사용하지 않고 `OPENAI_LLM_MODEL` 값을 읽는다.

## 13.2 `LlmClient`

Workflow가 OpenAI 구현에 직접 결합되지 않도록 만든 인터페이스다.

```java
public interface LlmClient {
  LlmResponse generate(LlmRequest request);
  boolean configured();
  String modelName();
}
```

## 13.3 `OpenAiLlmClient`

호출 API:

```text
POST {OPENAI_BASE_URL}/v1/responses
```

Structured Outputs 핵심 요청:

```java
format.put("type", "json_schema");
format.put("name", request.schemaName());
format.put("strict", true);
format.put("schema", request.schema());
```

모델에는 developer prompt와 user prompt를 분리해 전달한다.

```java
"input", List.of(
    Map.of("role", "developer", "content", developerPrompt),
    Map.of("role", "user", "content", userPrompt))
```

응답에서:

- `status=incomplete` 검사
- `refusal` 검사
- `output_text` 존재 여부 검사
- 출력 문자열이 JSON인지 검사
- input/output token 추출

을 수행한다.

## 13.4 `ReportPromptFactory`

Prompt 버전과 Report 유형별 JSON Schema를 제공한다.

```text
risk-analysis-v1
backtest-analysis-v1
watchlist-summary-v1
backtest-request-parser-v1
```

Prompt 버전을 DB와 이벤트에 기록하므로 나중에 어떤 규칙으로 생성한 Report인지 추적할 수 있다.

---

## 14. Report 저장 Service

`ReportPersistenceService`가 Report 상태 변경을 한곳에 모은 이유는 각 변경을 짧고 명확한 트랜잭션으로 실행하기 위해서다.

| 메서드 | 의미 |
|---|---|
| `create()` | 중복 검사 후 `REQUESTED` Report 저장 |
| `begin()` | 잠금 후 `RUNNING` 전환 및 attempt 증가 |
| `advance()` | 진행 단계 변경 |
| `addUsage()` | 실패 응답에서도 발생한 토큰 누적 |
| `complete()` | 결과 저장 및 `COMPLETED` 전환 |
| `fail()` | 최종 실패 기록 |
| `failAndMarkCompensation()` | 실패 기록과 사용량 보상 표시를 한 트랜잭션에서 처리 |
| `get()` | 내부 생성용 Report 조회 |

`findByIdForUpdate()`는 비관적 쓰기 잠금을 사용한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select r from AiReport r where r.id = :id")
Optional<AiReport> findByIdForUpdate(UUID id);
```

같은 Report를 Consumer, 재시도, 복구 스케줄러가 동시에 바꾸는 것을 막는다.

---

## 15. Report 조회 흐름

## 15.1 상세 조회

```text
GET /api/reports/{id}
-> ReportController
-> ReportQueryService.get()
-> ReportRepository.findById()
-> 소유권 검사
-> ReportResponse.from()
```

관리자가 아니고 Report 소유자가 아니면 `REPORT_FORBIDDEN`이다.

## 15.2 목록 조회

```text
GET /api/reports
  ?reportType=
  &status=
  &page=0
  &size=20
```

페이지는 최소 0, size는 1~50으로 보정한다. type과 status 유무에 따라 Repository 메서드를 선택한다.

---

## 16. Report 장애 복구

`ReportDispatchRecoveryScheduler`는 기본 60초마다 실행된다.

### 오래된 `REQUESTED`

```text
requestedAt이 1분보다 오래됨
-> 최초 Kafka 발행이 유실됐다고 판단
-> 이벤트 재발행
```

재발행도 실패하면:

```text
Report FAILED
사용량 보상 표시
Redis 사용량 복구
```

### 오래된 `RUNNING`

```text
startedAt이 5분보다 오래됨
-> Worker가 중간에 멈췄다고 판단
-> 이벤트 재발행
```

이 Report는 Kafka가 이미 한 번 접수한 작업이므로 재발행 실패 시에도 사용량은 복구하지 않는다.

---

## 17. 자연어 백테스트

## 17.1 일반 백테스트와의 관계

```text
일반 백테스트
BacktestController
  -> BacktestRequestService
  -> BacktestJob 생성
  -> Kafka 발행

자연어 백테스트
BacktestController
  -> NaturalLanguageBacktestService
  -> LLM으로 BacktestCreateRequest 생성
  -> BacktestRequestService
  -> 같은 BacktestJob 생성
  -> 같은 Kafka 발행
```

자연어 전용 계산 엔진이나 자연어 전용 Job이 따로 있는 것이 아니다. 기존 `BacktestJob`에 자연어 파싱 메타데이터만 추가한다.

## 17.2 Controller

```text
POST /api/backtests/natural-language
```

응답의 `outcome`에 따라 HTTP 상태를 정한다.

```java
"ACCEPTED".equals(response.outcome())
    ? HttpStatus.ACCEPTED
    : HttpStatus.OK
```

```text
NEEDS_CLARIFICATION -> HTTP 200
ACCEPTED            -> HTTP 202
```

## 17.3 `NaturalLanguageBacktestService.request()`

### 1단계: LLM 파싱

자연어를 strict JSON Schema에 맞는 `Draft`로 변환한다.

```java
record Draft(
    String assetQuery,
    StrategyType strategyType,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal initialCash,
    List<StrategyCondition> buyConditions,
    List<StrategyCondition> sellConditions,
    List<String> missingFields) {}
```

지원하지 않는 전략 enum은 Schema 또는 Jackson 변환 과정에서 차단된다.

### 2단계: 자산 식별

- 요청에 `assetId`가 있으면 해당 자산 조회
- 없으면 LLM이 추출한 `assetQuery`로 검색
- 정확히 한 개면 선택
- 여러 개면 후보 최대 10개와 함께 clarification
- 없으면 `asset`을 누락 필드로 추가

### 3단계: clarification

필수 필드가 없으면 Job이나 사용량을 만들기 전에 반환한다.

```java
return NaturalLanguageBacktestResponse.clarification(
    null,
    missingFields,
    candidates);
```

결과:

```text
HTTP 200
outcome = NEEDS_CLARIFICATION
BACKTEST 사용량 변화 없음
AI_AGENT 사용량 변화 없음
BacktestJob 생성 없음
```

### 4단계: 공통 요청 생성과 검증

```java
BacktestCreateRequest request =
    new BacktestCreateRequest(
        asset.getId(),
        draft.strategyType(),
        draft.startDate(),
        draft.endDate(),
        draft.initialCash(),
        draft.buyConditions(),
        draft.sellConditions());

validator.validate(request);
```

`BacktestRequestValidator`는 다음을 검사한다.

- 날짜 역전 금지
- 미래 종료일 금지
- 최대 10년
- 초기자금 0 초과, 상한 이하
- CUSTOM 매수·매도 조건 각각 1~5개
- period 2~500
- RSI 0~100
- 매수 위치에 매도 조건 또는 그 반대 사용 금지
- 조건 유형별 필수 `period`, `value` 검사

### 5단계: BACKTEST 사용량만 예약

```java
UsageReservation reservation =
    usage.reserve(userId, UsageType.BACKTEST);
```

LLM을 사용했지만 `AI_AGENT`는 예약하지 않는다.

### 6단계: 공통 `BacktestRequestService` 호출

```java
BacktestCreateResponse response =
    requests.request(userId, request, afterJobCreated);
```

`BacktestRequestService`는 다음을 수행한다.

```text
사용자 존재 확인
자산 존재 확인
공통 Validator 재검증
BacktestJob 생성
자연어 전용 후처리 실행
backtest-requested Kafka 발행
BacktestCreateResponse 반환
```

## 17.4 `afterJobCreated.accept(job)`의 정확한 의미

호출하는 쪽은 세 번째 인자로 람다를 전달한다.

```java
requests.request(
    userId,
    request,
    job -> {
      created.set(job);
      jobs.attachNaturalLanguageMetadata(...);
    });
```

받는 쪽의 세 번째 매개변수는 다음과 같다.

```java
Consumer<BacktestJob> afterJobCreated
```

Java는 인자를 순서대로 매개변수에 전달하므로 다음처럼 연결된다.

```text
세 번째 인자 job -> { ... }
  -> afterJobCreated 변수
```

`BacktestRequestService`가 Job을 만든 뒤:

```java
BacktestJob job = jobs.createRequested(...);
afterJobCreated.accept(job);
```

을 실행한다. `accept(job)`은 성공을 의미하지 않는다. Java `Consumer`에 들어 있는 함수를 `job` 값으로 실행한다는 뜻이다.

따라서 실제로 실행되는 코드는:

```java
created.set(job);
jobs.attachNaturalLanguageMetadata(...);
```

이다.

이 콜백을 사용한 이유는 공통 `BacktestRequestService`의 Job 생성과 Kafka 발행 사이에 자연어 전용 메타데이터 저장을 넣기 위해서다.

```text
Job 생성
-> 자연어 메타데이터 저장
-> Kafka 발행
```

현재 용도가 하나뿐이므로 향후에는 `requestNaturalLanguage()`처럼 명시적인 메서드로 분리하면 더 이해하기 쉬울 수 있다.

## 17.5 `AtomicReference<BacktestJob> created`

```java
AtomicReference<BacktestJob> created = new AtomicReference<>();
```

처음에는 `null`인 Job 보관 상자다.

```java
created.set(job);
```

은 Job이 실제로 생성됐다는 사실을 기록한다.

실패 시:

```java
if (created.get() == null) {
  usage.release(reservation);
}
```

정책:

```text
Job 생성 전 실패
  -> created == null
  -> BACKTEST 사용량 복구

Job 생성 후 실패
  -> created != null
  -> BACKTEST 사용량 유지
```

이 코드에서 `AtomicReference`는 비동기 실행을 위한 것이 아니라 람다 내부에서 바깥쪽 상태를 변경할 수 있는 가변 상자로 사용된다.

## 17.6 자연어 메타데이터

V16에서 `backtest_jobs`에 추가된 값이다.

```text
natural_language_question
parser_model
parser_prompt_version
parser_input_token_count
parser_output_token_count
```

실제 백테스트 전략과 계산 결과가 아니라 “어떤 자연어를 어떤 모델과 Prompt 버전으로 파싱했는지”를 추적하기 위한 감사 정보다.

## 17.7 `NaturalLanguageBacktestResponse.accepted()`

```java
return NaturalLanguageBacktestResponse.accepted(response, request);
```

`accepted()`는 프로젝트에서 만든 정적 팩토리 메서드다.

```java
return new NaturalLanguageBacktestResponse(
    "ACCEPTED",
    response.jobId(),
    response.status(),
    request,
    List.of(),
    List.of());
```

이 줄에 도달했다는 뜻:

```text
파싱 성공
검증 성공
BACKTEST 사용량 예약 성공
BacktestJob 생성 성공
자연어 메타데이터 저장 성공
Kafka 발행 성공
```

하지만 실제 백테스트 계산 완료를 뜻하지는 않는다.

```text
ACCEPTED
  -> 계산 요청 접수 완료

COMPLETED
  -> Kafka Worker의 실제 계산 완료
```

---

## 18. `BacktestRequestService`와 실제 계산의 관계

`BacktestRequestService`는 백테스트를 직접 계산하지 않는다.

```text
검증
-> Job 생성
-> Kafka 발행
-> Job ID 응답
```

실제 계산은 기존 백테스트 Kafka Consumer와 Engine이 수행한다.

```text
REQUESTED
-> RUNNING
-> COMPLETED

또는

REQUESTED/RUNNING
-> FAILED
```

Kafka 발행 실패 시 `BacktestJobService.markFailed()`는 `REQUIRES_NEW` 트랜잭션으로 Job을 `FAILED` 처리한다.

자연어 백테스트는 Job이 이미 생성된 뒤 Kafka 발행에 실패해도 BACKTEST 사용량을 유지한다.

---

## 19. 사용량 정책 요약

### AI Report

| 상황 | 사용량 |
|---|---|
| LLM 미설정, 자산 식별 실패 | 예약 전 실패, 변화 없음 |
| 중복 Report 재사용 | 새 예약 즉시 release |
| Report DB 생성 실패 | release |
| DB 커밋 후 최초 Kafka 발행 실패 | Report FAILED + 명시적 release |
| Kafka 접수 후 LLM/데이터 실패 | 유지 |
| stale REQUESTED 최종 발행 실패 | release |
| stale RUNNING 복구 발행 실패 | 유지 |

### 자연어 백테스트

| 상황 | BACKTEST | AI_AGENT |
|---|---:|---:|
| clarification | 변화 없음 | 변화 없음 |
| 파싱 실패 | 변화 없음 | 변화 없음 |
| 검증 실패 | 변화 없음 | 변화 없음 |
| Job 생성 전 실패 | 예약 후 release | 변화 없음 |
| Job 생성 성공 | 1 증가 | 변화 없음 |
| Job 생성 후 Kafka 실패 | 유지 | 변화 없음 |

---

## 20. 주요 오류 코드

| 코드 | 의미 |
|---|---|
| `REPORT_001` | Report 없음 |
| `REPORT_002` | 다른 사용자의 Report 접근 |
| `REPORT_003` | 질문에서 찾은 자산이 여러 개 |
| `REPORT_004` | 분석에 필요한 데이터 부족 |
| `REPORT_005` | 사용자에게 이미 실행 중인 Report 존재 |
| `REPORT_006` | OpenAI key 또는 LLM 모델 미설정 |
| `REPORT_007` | LLM 구조화 출력이 올바르지 않음 |
| `REPORT_008` | LLM이 허용되지 않은 evidence ID 사용 |
| `REPORT_009` | Report Kafka 발행 실패 |
| `BACKTEST_006` | 자연어 파싱 실패 |
| `BACKTEST_007` | 백테스트 조건 검증 실패 |

---

## 21. 테스트에서 확인하는 정책

### `AiReportTest`

- 상태 전이
- 완료 이후 중복 실행 차단
- 사용량 보상 표시가 한 번만 성공하는지 확인

### `ReportRequestServiceTest`

- DB 커밋 후 Kafka 발행 실패 시 Report FAILED
- 사용량 명시적 보상
- 중복 Report 재사용 시 추가 예약 복구

### `OpenAiLlmClientTest`

- API key와 모델 모두 필요한지 확인
- `/v1/responses`와 Structured Outputs 요청 확인
- JSON 결과와 token usage 파싱
- 429는 retryable, 인증 오류는 non-retryable

### `ReportEvidenceValidatorTest`

- LLM이 context에 없던 문서 ID를 만들면 거부

### `NaturalLanguageBacktestServiceTest`

- clarification에서 사용량 0
- Job 생성 성공 시 BACKTEST만 1회
- AI_AGENT 사용량이 변하지 않는지 확인
- 자연어 메타데이터 저장 확인

---

## 22. 현재 구현을 읽을 때 주의할 점

### 22.1 진행 단계

Enum에는 다음 단계가 모두 존재한다.

```text
ASSET_RESOLUTION
RISK_DATA
DOCUMENT_SEARCH
AI_ANALYSIS
REPORT_SAVE
COMPLETED
```

하지만 현재 `ReportGenerationService`는 시작 후 바로 `AI_ANALYSIS`로 변경한다.

```java
persistence.advance(id, ReportStep.AI_ANALYSIS);
```

Workflow 내부에서 위험 데이터 조회와 문서 검색은 실제로 수행되지만 `RISK_DATA`, `DOCUMENT_SEARCH`를 각각 DB에 기록하지는 않는다. UI 단계 표시를 정확히 순차 갱신하려면 Workflow 단계별 `advance()` 연결이 추가로 필요하다.

### 22.2 Report 사용량의 reserve

현재 `UsageLimitService.reserve()`가 Redis 카운터를 즉시 증가시킨다. 성공 시 별도 확정 메서드를 호출하지 않고 release하지 않는 것으로 사용량을 확정한다.

### 22.3 자연어 백테스트 콜백

`Consumer<BacktestJob>` 콜백은 동작하지만 처음 읽는 사람이 흐름을 이해하기 어렵다. 기능적으로는 “Job 생성 직후 자연어 메타데이터 저장” 한 가지 용도다.

---

## 23. 백엔드 코드 추천 학습 순서

다음 순서로 읽으면 요청부터 결과까지 한 방향으로 따라가기 쉽다.

1. `ReportType`, `ReportStatus`, `ReportStep`
2. `AiReport`
3. `AiReportRepository`
4. `ReportPersistenceService`
5. `ReportRequestService`
6. `ReportEventPublisher`
7. `ReportGenerationConsumer`
8. `ReportGenerationService`
9. `RiskAnalysisWorkflow`
10. `BacktestAnalysisWorkflow`
11. `WatchlistSummaryWorkflow`
12. `report.tool` 클래스
13. `LlmClient`, `OpenAiLlmClient`
14. `ReportPromptFactory`
15. `ReportEvidenceValidator`
16. `ReportQueryService`
17. `ReportDispatchRecoveryScheduler`
18. `NaturalLanguageBacktestService`
19. `BacktestRequestValidator`
20. `BacktestRequestService`
21. `BacktestJobService`, `BacktestJob`

---

## 24. 프론트엔드 구현 요약

프론트엔드는 백엔드의 상태를 직접 계산하지 않고 API 응답을 표시한다.

## 24.1 API Client

### `frontend/src/lib/api/reports.ts`

- 위험 Report 생성
- 백테스트 해석 Report 생성
- 관심목록 요약 생성
- Report 목록과 상세 조회
- 생성 요청마다 `Idempotency-Key` 생성

### `frontend/src/lib/api/backtests.ts`

- 자연어 백테스트 요청
- 완료 백테스트 목록 조회
- 생성된 Job 상태 조회

## 24.2 `/ai`

`ReportAgentWorkbench`에서 네 모드를 선택한다.

```text
위험 분석
백테스트 해석
자연어 백테스트
관심목록 요약
```

Report 생성 성공이면 `/reports/{reportId}`로 이동한다.

자연어 백테스트가 `NEEDS_CLARIFICATION`이면 누락 필드를 보여주고, `ACCEPTED`이면 Job ID를 저장해 상태를 polling한다.

## 24.3 `/reports`

`ReportList`가 유형과 상태 filter, pagination을 제공한다.

## 24.4 `/reports/[reportId]`

`ReportDetail`은 상태가 `REQUESTED` 또는 `RUNNING`인 동안 2초마다 다시 조회한다.

```typescript
return status === "REQUESTED" || status === "RUNNING"
  ? 2000
  : false;
```

`COMPLETED`, `FAILED`, 화면 이탈 시 polling이 중지된다.

구조화 결과는 React 값으로 렌더링하며 raw HTML을 사용하지 않는다. `sourceUrl`이 실제 문자열로 있을 때만 원문 링크를 만든다.

## 24.5 프론트 전체 흐름

```text
/ai에서 요청
-> API Client
-> 백엔드 HTTP 202
-> /reports/{id} 이동
-> 2초 polling
-> REQUESTED/RUNNING 진행 표시
-> COMPLETED 구조화 결과 표시
   또는 FAILED 오류 표시
```

자연어 백테스트는:

```text
/ai에서 자연어 입력
-> NEEDS_CLARIFICATION이면 누락 필드 표시
-> ACCEPTED이면 Backtest Job polling
-> COMPLETED이면 수익률과 MDD 표시
```

