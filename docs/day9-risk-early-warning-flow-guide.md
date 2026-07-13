# Day 9 기업·채권 부도 위험 조기경보 시스템 상세 가이드

## 1. 문서 목적

이 문서는 Day 9에 구현된 기업·채권 부도 위험 조기경보 기능의 실제 코드 흐름을 설명한다.

설명 범위는 다음과 같다.

- 사용자가 계산을 요청한 순간부터 결과를 조회할 때까지의 전체 흐름
- Kafka 비동기 처리와 Job 상태 전이
- 재무·부채·시장·신용사건·계열관계 데이터가 계산 Context로 조립되는 방식
- Rule 실행, 점수 합산, 위험등급, 실제 부도상태, 데이터 품질, Confidence 산정 방식
- `RiskScore`, `RiskSignal`, `CreditEvent`, `AssetRelationship` 등 핵심 엔티티의 의미
- Day 5~8 구조와 비교했을 때 달라진 DB·Kafka·트랜잭션 설계
- API, 프론트 polling, Top Risk Factors 생성 방식
- 현재 구현된 범위와 아직 구현되지 않은 한계

이 기능은 공식 신용등급이나 실제 부도확률을 산출하는 모델이 아니다. 내부에 보유한 데이터로 위험 징후를 설명 가능한 규칙으로 탐지하는 조기경보 시스템이다.

---

## 2. 전체 구조 한눈에 보기

```text
사용자
  |
  | POST /api/risks/assets/{assetId}/calculations
  v
RiskController
  |
  v
RiskCalculationRequestService
  |-- 사용자 존재 확인
  |-- Asset 존재 확인
  |-- BOND_ISSUER 확인
  |-- 활성 Job 중복 확인
  |-- RiskCalculationJob(REQUESTED) 저장
  `-- risk-score-requested 발행
             |
             v
        Kafka Broker
             |
             v
RiskRequestedConsumer
  |
  v
RiskCalculationExecutionService
  |-- REQUESTED -> RUNNING 조건부 전이
  |-- RiskEvaluationContextFactory
  |-- RiskScoringEngine
  |-- RiskResultPersistenceService
  `-- 전체 실행시간 로그
             |
             v
     하나의 DB Transaction
  |-- RiskScore 저장
  |-- RiskSignal 저장
  |-- Job COMPLETED
  `-- 내부 완료 Notification 발행
             |
             | COMMIT
             v
RiskAfterCommitEventListener
  |-- risk-score-calculated 발행
  `-- HIGH/CRITICAL Signal은 risk-signal-detected 발행

프론트엔드
  |-- Job API를 2초 간격으로 polling
  |-- COMPLETED이면 RiskScore 상세 조회
  `-- 점수, 등급, 상태, Confidence, Top Factors 표시
```

핵심 원칙은 API 요청 스레드에서 계산하지 않는 것이다. 요청 스레드는 Job을 만들고 Kafka 요청 메시지를 발행한 뒤 `202 Accepted`를 반환한다.

---

## 3. 계산 요청 흐름

### 3.1 API 진입점

파일: `risk/api/RiskController.java`

```http
POST /api/risks/assets/{assetId}/calculations
Authorization: Bearer <access-token>
```

Controller는 JWT에서 `CustomUserPrincipal`을 받아 사용자 ID를 계산 요청 서비스에 전달한다.

응답은 계산 결과가 아니라 Job 정보다.

```json
{
  "success": true,
  "data": {
    "jobId": "uuid",
    "assetId": 10,
    "status": "REQUESTED",
    "ruleVersion": "corporate-risk-v1",
    "dataAsOfDate": "2026-07-12",
    "riskScoreId": null
  }
}
```

HTTP status는 `202 Accepted`다.

### 3.2 요청 검증과 Job 생성

파일: `risk/service/RiskCalculationRequestService.java`

처리 순서:

1. `UserRepository.existsById()`로 사용자 존재 확인
2. `AssetRepository.findById()`로 Asset 조회
3. `AssetType.BOND_ISSUER`인지 확인
4. `RiskCalculationJobService.create()` 호출
5. `corporate-risk-v1`을 Job의 `ruleVersion`으로 저장
6. `RiskScoreRequestedEvent` 발행

지원하지 않는 Asset이면 `RISK_ASSET_NOT_SUPPORTED`가 발생한다.

Kafka 발행이 최종 실패하면 이미 생성한 Job을 다음 상태로 바꾼다.

```text
REQUESTED -> FAILED
failureCode = RISK_010
```

그 후 API는 `RISK_KAFKA_PUBLISH_FAILED`를 반환한다.

### 3.3 동일 Asset 동시 계산 방지

애플리케이션에서는 `RiskCalculationJobService.create()`가 다음 상태를 조회한다.

```text
REQUESTED
RUNNING
```

그러나 조회 후 저장 사이에는 race condition이 있을 수 있다. 최종 방어는 PostgreSQL partial unique index가 담당한다.

```sql
CREATE UNIQUE INDEX uq_risk_active_job_asset
ON risk_calculation_jobs(asset_id)
WHERE status IN ('REQUESTED', 'RUNNING');
```

따라서 같은 Asset에는 활성 Job이 하나만 존재할 수 있고, `COMPLETED`와 `FAILED` 이력은 여러 건 보존된다.

DB unique 위반은 `RISK_CALCULATION_ALREADY_RUNNING`으로 변환된다.

---

## 4. Kafka 요청과 Consumer 흐름

### 4.1 Topic 구성

파일: `risk/kafka/RiskTopics.java`, `RiskKafkaConfiguration.java`

| Topic | 용도 |
|---|---|
| `risk-score-requested` | 위험 계산 요청 |
| `risk-score-calculated` | 계산과 DB 저장 완료 |
| `risk-score-failed` | 계산 최종 실패 |
| `risk-signal-detected` | HIGH 또는 CRITICAL Signal 알림 |

Day 9에는 DLT와 Outbox가 없다.

### 4.2 Requested Event

파일: `risk/event/RiskScoreRequestedEvent.java`

```json
{
  "jobId": "uuid",
  "assetId": 10,
  "userId": 1,
  "requestedAt": "2026-07-12T10:00:00Z"
}
```

Kafka key는 `assetId`다. 같은 Asset 메시지가 동일 partition에서 순서를 유지하도록 하기 위한 선택이다.

### 4.3 Consumer

파일: `risk/kafka/RiskRequestedConsumer.java`

Consumer group:

```text
finrisk-risk-calculator
```

Consumer 자체에는 계산 로직이 없다. 메시지를 받으면 `RiskCalculationExecutionService.execute(jobId)`만 호출한다.

Risk Consumer는 별도 container factory인 `riskKafkaListenerContainerFactory`를 사용한다. 따라서 Day 9 retry 정책이 기존 Market, Backtest, Financial Consumer에 전역으로 영향을 주지 않는다.

### 4.4 Docker 프로필의 trusted package

Kafka JSON 역직렬화를 위해 기본 설정과 Docker 설정 모두 다음 패키지를 신뢰 목록에 포함한다.

```text
com.finrisk.radar.risk.event
```

Docker profile은 `application-docker.yaml`에서 Kafka 설정을 다시 선언하므로 이 파일에도 Risk event package가 반드시 있어야 한다.

---

## 5. Job 실행과 중복 메시지 방지

### 5.1 실행 서비스

파일: `risk/service/RiskCalculationExecutionService.java`

실행 서비스는 다음 네 컴포넌트를 조합한다.

| 의존 컴포넌트 | 책임 |
|---|---|
| `RiskCalculationJobService` | Job 조회와 상태 전이 |
| `RiskEvaluationContextFactory` | 계산 입력 데이터 조회·정규화 |
| `RiskScoringEngine` | Rule 실행과 최종 판단 |
| `RiskResultPersistenceService` | 결과의 원자적 저장 |

### 5.2 조건부 RUNNING 전이

Repository는 다음 native update를 실행한다.

```sql
UPDATE risk_calculation_jobs
SET status = 'RUNNING',
    started_at = :started,
    updated_at = :started
WHERE job_id = :id
  AND status = 'REQUESTED';
```

영향받은 행이 1개일 때만 최초 Consumer가 선점한 것이다.

이미 `COMPLETED` 또는 `FAILED`인 Job 메시지가 다시 전달되면 계산하지 않는다. 이를 통해 동일 Kafka 메시지 재소비 시 결과가 중복 생성되는 것을 막는다.

Retry 중에는 Job이 이미 `RUNNING`이므로 동일 실행을 다시 시도할 수 있다. 반면 terminal 상태에서는 즉시 반환한다.

### 5.3 전체 실행시간 로그

RUNNING 선점 후 `System.nanoTime()`으로 계산시간을 측정한다.

완료 로그 예시:

```text
event=risk_calculation_job_completed
jobId=...
assetId=10
elapsedMs=144
executedRuleCount=14
calculatedRuleCount=10
notAvailableRuleCount=4
insufficientDataRuleCount=0
failedRuleCount=0
ruleVersion=corporate-risk-v1
totalScore=95
riskGrade=CRITICAL
dataQuality=COMPLETE
confidence=HIGH
```

이 메트릭은 DB에 저장하지 않는다.

---

## 6. 계산 입력 Context 생성

### 6.1 RiskEvaluationContext

파일: `risk/engine/RiskEvaluationContext.java`

Context는 Rule이 DB Repository를 직접 호출하지 않도록 계산에 필요한 데이터를 한 번에 담는다.

```text
Asset
계산 기준일
재무지표 이력
최신 부채 만기 snapshot
이전 부채 만기 snapshot
시장가격 이력
대상 회사의 CreditEvent
관계 상대 회사의 CreditEvent
유효한 AssetRelationship
```

Rule은 Context를 읽기만 한다. Rule 간 결과를 서로 전달하지 않는다.

### 6.2 재무 데이터

`FinancialMetricRepository.findByAssetIdOrderByYearDescQuarterDesc()`로 최신순 이력을 조회한다.

재무 데이터가 한 건도 없으면 계산 자체를 실패시킨다.

```text
RISK_FINANCIAL_DATA_NOT_FOUND
```

이는 재무 데이터가 없는 회사를 0점의 안전한 회사로 오인하지 않기 위한 정책이다.

### 6.3 부채 만기 snapshot

`DebtMaturity.snapshotDate`를 기준으로 계산 기준일 이전 snapshot을 최신순 정렬한다.

- 첫 번째 snapshot: 현재 만기구조 계산
- 두 번째 snapshot: 만기 집중 증가 추세 비교
- snapshot metadata가 전혀 없는 기존 데이터: 조회된 전체 데이터를 현재 데이터로 사용

### 6.4 시장가격 proxy

`BOND_ISSUER` 자체에는 주가 ticker가 없을 수 있으므로 `Asset.marketPriceAssetId`로 시장가격을 가진 다른 Asset을 연결한다.

연결이 없으면 가격을 임의 생성하지 않고 Market Rule은 `NOT_AVAILABLE`을 반환한다.

연결이 있으면 계산 기준일 이전 가격 중 최대 최근 141개 행을 사용한다.

### 6.5 계열관계와 상대방 사건

`AssetRelationship`은 다음 기간 조건을 만족할 때만 Context에 포함된다.

```text
effectiveFrom <= dataAsOfDate
effectiveTo가 없거나 effectiveTo >= dataAsOfDate
```

유효한 관계의 `toAssetId`를 따라가 상대 Asset의 CreditEvent를 조회한다. 그룹 전염 Rule은 관계만 있다고 점수를 주지 않고 상대 회사의 실제 사건이 함께 있을 때만 점수를 준다.

---

## 7. Rule 구조와 실행 순서

### 7.1 공통 인터페이스

파일: `risk/engine/RiskRule.java`

```java
public interface RiskRule {
    int priority();
    RiskRuleType supports();
    boolean required();
    RiskRuleResult evaluate(RiskEvaluationContext context);
}
```

각 속성의 의미:

| 속성 | 의미 |
|---|---|
| `priority` | 실행 순서. 낮은 값부터 실행 |
| `supports` | Rule의 유일한 식별자 |
| `required` | Confidence 계산에 포함되는 필수 Rule인지 여부 |
| `evaluate` | Context를 입력받아 Rule 결과 반환 |

### 7.2 Registry

파일: `risk/engine/RiskRuleRegistry.java`

Spring이 모든 `RiskRule` Bean을 `List<RiskRule>`로 주입한다. Registry는 다음을 수행한다.

1. priority 오름차순 정렬
2. 동일 priority 중복 검사
3. 동일 RuleType 중복 검사
4. immutable list로 보관

신규 Rule은 Bean만 추가하면 되며 Registry 코드를 수정할 필요가 없다.

### 7.3 현재 priority

| Priority | Rule | 필수 여부 | 최대점수 |
|---:|---|---|---:|
| 100 | DebtRatio | 필수 | 5 |
| 200 | InterestCoverage | 필수 | 7 |
| 300 | OperatingCashFlow | 필수 | 7 |
| 400 | NetLoss | 필수 | 6 |
| 1000 | MaturityConcentration | 필수 | 10 |
| 1100 | ShortTermDebtDependency | 필수 | 8 |
| 1200 | CashMaturityCoverage | 필수 | 9 |
| 1300 | LiquidityCoverage | 필수 | 4 |
| 1400 | MaturityTrend | 선택 | 4 |
| 2000 | PriceDrop | 선택 | 4 |
| 2100 | VolatilitySpike | 선택 | 3 |
| 2200 | VolumeAnomaly | 선택 | 3 |
| 3000 | CreditEvent | 필수 | 25 |
| 4000 | GroupContagion | 선택 | 5 |

priority 사이에 간격을 둬 중간 Rule을 추가할 수 있다.

### 7.4 Rule 결과

파일: `risk/engine/RiskRuleResult.java`

```text
ruleType
category
status
score
maxScore
severity
signalType
message
evidence
sourceId
```

상태:

| 상태 | 의미 |
|---|---|
| `CALCULATED` | 데이터가 있어 계산함 |
| `NOT_AVAILABLE` | 필요한 데이터가 없음 |
| `INSUFFICIENT_DATA` | 카테고리 일부만 계산 가능 |

데이터가 없을 때 0점 안전으로 바꾸지 않는 것이 핵심이다.

### 7.5 Rule 실행시간

각 Rule은 실행 전후 시간을 측정한다.

- 일반 Rule: DEBUG
- 100ms 이상: INFO (`risk_rule_slow`)
- Rule 예외: ERROR (`risk_rule_failed`)

원시 재무정보와 사건 description 전체는 로그에 남기지 않는다.

---

## 8. 현재 Rule별 계산 방식

Rule Bean은 현재 `risk/rule/StandardRiskRuleConfiguration.java`에 모여 있다.

### 8.1 Financial 최대 25점

#### DebtRatioRiskRule

```text
totalDebt / totalEquity * 100
```

자기자본이 0 이하이면 자본잠식으로 최고 위험을 부여한다.

#### InterestCoverageRiskRule

```text
operatingIncome / interestExpense
```

영업손실이면 배율 계산보다 `OPERATING_LOSS`를 우선한다. 이자비용 0은 위험 0점으로 계산한다.

#### OperatingCashFlowRiskRule

최근 최대 4개 재무기간의 음수 OCF 개수와 연속 횟수를 평가한다.

#### NetLossRiskRule

quarter 4 데이터를 연간 데이터로 보고 최대 3년 연속 순손실을 계산한다. 최신 자기자본이 0 이하이면 자본잠식을 우선한다.

### 8.2 Liquidity/Maturity 최대 35점

#### MaturityConcentrationRiskRule

계산 기준일 이후 부채 중 180일 이내 만기금액 비율을 계산한다.

#### ShortTermDebtDependencyRiskRule

다음 유형은 항상 단기성으로 본다.

```text
CP
ABSTB
SHORT_TERM_BORROWING
CURRENT_PORTION_LONG_TERM_DEBT
```

그 외 유형은 `isShortTerm` 또는 365일 이내 만기인지 확인한다.

#### CashMaturityCoverageRiskRule

```text
cash / 180일 이내 만기부채
```

#### LiquidityCoverageRiskRule

```text
(cash + 양수 operatingCashFlow) / 365일 이내 만기부채
```

음수 OCF는 분자에 더하지 않는다.

#### MaturityTrendRiskRule

최신 snapshot과 이전 snapshot의 180일 이내 만기금액 증가율을 비교한다. 이전 snapshot이 없으면 선택 Rule `NOT_AVAILABLE`이다.

### 8.3 Market 최대 10점

#### PriceDropRiskRule

최소 21개 가격이 있으면 최근 20거래일 수익률을 계산한다.

#### VolumeAnomalyRiskRule

최소 80개 가격이 있을 때 최근 20일 평균 거래량과 그 이전 구간 평균을 비교한다.

#### VolatilitySpikeRiskRule

현재 코드는 실제 변동성 계산이 아직 없고 다음 결과를 반환한다.

```text
NOT_AVAILABLE
"Volatility comparison requires a complete 140-day sample"
```

즉, Rule 자리와 최대점수는 준비됐지만 변동성 산식은 후속 구현 대상이다.

### 8.4 CreditEvent 최대 25점

등록된 사건 중 `eventScore()`가 가장 높은 사건 하나를 대표 사건으로 선택한다.

현재 구현은 `incidentKey`별 그룹 합산이나 독립 사건 25% 가산을 수행하지 않는다. 카테고리 최고점 25점과 대표 사건 선택까지만 구현돼 있다.

### 8.5 GroupContagion 최대 5점

다음 조합에서만 점수가 발생한다.

| 관계 | 상대방 사건 | 점수 |
|---|---|---:|
| `CROSS_DEFAULT` | EOD 이상 사건 | 5 |
| `CREDIT_SUPPORT` | 회생신청 | 4 |
| `PAYMENT_GUARANTEE` | 지급불이행 | 3 |

관계만 있고 상대 사건이 없으면 0점이다. 관계 데이터 자체가 없으면 `NOT_AVAILABLE`이다.

---

## 9. Scoring Engine

파일: `risk/engine/RiskScoringEngine.java`

### 9.1 실행 단계

1. Registry의 정렬된 Rule을 순서대로 실행
2. 결과와 실행 통계를 수집
3. 카테고리별 점수 합산
4. 카테고리 최대점수 적용
5. 카테고리 계산 상태 결정
6. 필수 Rule 성공률 계산
7. DataQuality와 Confidence 계산
8. CreditEvent로 DefaultStatus 결정
9. 실제 사건 override 적용
10. RiskGrade 결정
11. `RiskCalculationOutcome` 반환

### 9.2 카테고리 상한

```text
FINANCIAL           25
LIQUIDITY_MATURITY  35
MARKET              10
CREDIT_EVENT        25
GROUP_CONTAGION      5
```

계산할 수 없는 카테고리를 100점 기준으로 다시 환산하지 않는다.

### 9.3 RiskGrade

| 점수 | 등급 |
|---:|---|
| 0~19 | LOW |
| 20~39 | CAUTION |
| 40~59 | MEDIUM |
| 60~79 | HIGH |
| 80~100 | CRITICAL |

### 9.4 DefaultStatus

DefaultStatus는 점수가 아니라 실제 CreditEvent로 결정한다.

심각도 순서:

```text
BANKRUPTCY
REHABILITATION
DEFAULT
EVENT_OF_DEFAULT
PAYMENT_DELAY
NONE
```

따라서 점수가 높다고 자동으로 `DEFAULT`가 되지 않는다.

### 9.5 실제 사건 override

| DefaultStatus | 최종점수 최소값 |
|---|---:|
| DEFAULT | 90 |
| REHABILITATION | 95 |
| BANKRUPTCY | 100 |

현재 override는 최종점수에는 반영되지만 `CRITICAL_EVENT_OVERRIDE`라는 별도 RiskSignal을 생성하지는 않는다. 이는 계획 대비 남은 보완점이다.

### 9.6 DataQuality

최신 재무기간을 분기 종료일로 근사해 계산 기준일보다 180일 이상 오래됐으면 `STALE`이다.

그 외에는:

- 필수 Rule 성공률 100%: `COMPLETE`
- 필수 Rule 일부 미계산: `PARTIAL`

`INSUFFICIENT` enum은 존재하지만 현재 `RiskScoringEngine.quality()`에서 직접 반환하지 않는다.

### 9.7 Confidence

Confidence는 위험도가 아니라 결과 신뢰도다.

```text
STALE 또는 필수 Rule 성공률 < 100% -> LOW
COMPLETE + 필수 Rule 성공률 100% -> HIGH
그 외 -> MEDIUM
```

Market, MaturityTrend, GroupContagion 같은 선택 Rule 부재만으로 HIGH를 낮추지 않는다.

---

## 10. 결과 저장 트랜잭션

파일: `risk/service/RiskResultPersistenceService.java`

`persist()` 전체가 하나의 `@Transactional` 범위다.

```text
BEGIN
  RiskScore INSERT
  RiskSignal INSERT N건
  RiskCalculationJob COMPLETED
  Spring 내부 Notification 발행
COMMIT
```

중간에 예외가 발생하면 Score, Signal, COMPLETED 상태가 모두 rollback된다.

### 10.1 RiskScore 저장

다음 정보를 한 계산 snapshot으로 보존한다.

- 총점, RiskGrade, DefaultStatus
- 카테고리별 nullable 점수
- 카테고리별 계산 상태 JSONB
- DataQuality와 Confidence
- 필수 Rule 성공률
- 누락 카테고리 JSONB
- 사용된 데이터 개수
- 기준일과 Rule Version
- 계산시각

`job_id`는 unique이므로 하나의 Job은 하나의 RiskScore만 가진다.

### 10.2 RiskSignal 저장

Rule 결과 중 다음 조건만 Signal로 저장한다.

```text
status == CALCULATED
score > 0
```

즉 0점 정상 결과와 `NOT_AVAILABLE` 결과는 Signal 행으로 저장하지 않는다. 카테고리 상태와 누락 목록은 RiskScore에 남는다.

evidence는 JSONB로 저장한다.

deduplication key:

```text
riskScoreId:ruleType:sourceId-or-policy
```

### 10.3 Rule Version

`ruleVersion`은 RiskScore에만 저장한다.

```text
RiskSignal -> riskScoreId -> RiskScore.ruleVersion
```

Signal마다 같은 version을 중복 저장하지 않아 불일치 가능성을 막는다.

---

## 11. AFTER_COMMIT 이벤트

파일: `risk/service/RiskAfterCommitEventListener.java`

저장 Transaction 안에서는 외부 Kafka 발행을 직접 하지 않는다. 대신 내부 `RiskCalculationCompletedNotification`을 발행한다.

Listener는 다음 시점에 실행된다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

COMMIT 성공 후:

1. `risk-score-calculated` 발행
2. HIGH 또는 CRITICAL RiskSignal마다 `risk-signal-detected` 발행

Kafka 결과 이벤트 발행이 실패해도 이미 commit된 RiskScore는 유지된다. Day 9의 정본은 Kafka가 아니라 DB와 polling API다.

Outbox를 사용하지 않기 때문에 commit 직후 프로세스가 종료되면 결과 이벤트가 유실될 가능성은 남아 있다. 알림·이메일·AI Report가 이벤트 전달에 강하게 의존할 때 Outbox를 도입해야 한다.

---

## 12. 실패와 Retry

파일: `risk/kafka/RiskKafkaErrorConfiguration.java`

### 12.1 Retry 정책

```text
최초 실행
1초 후 Retry 1
2초 후 Retry 2
최종 recovery
```

`ExponentialBackOffWithMaxRetries(2)`를 사용한다.

즉시 실패 처리할 예외:

```text
BusinessException
IllegalArgumentException
ArithmeticException
IllegalStateException
```

일시적인 DB·Kafka·네트워크 인프라 예외는 retry 대상이 될 수 있다.

### 12.2 최종 recovery

최종 실패 시:

1. cause chain에서 `BusinessException` 탐색
2. 구체적인 ErrorCode와 안전한 메시지 추출
3. Job을 별도 Transaction에서 `FAILED` 처리
4. `risk-score-failed` 발행
5. topic, partition, offset, code를 ERROR 로그에 기록
6. 해당 record를 recovery 처리해 다음 메시지 진행

DLT는 사용하지 않는다. 요청 payload는 jobId 중심이며 DB Job에서 복원할 수 있기 때문이다.

---

## 13. 엔티티 의미

### 13.1 RiskCalculationJob

한 번의 비동기 계산 작업을 나타낸다.

| 필드 | 의미 |
|---|---|
| `jobId` | Kafka와 API가 공유하는 UUID |
| `userId` | 요청 사용자 |
| `assetId` | 대상 BOND_ISSUER |
| `status` | REQUESTED/RUNNING/COMPLETED/FAILED |
| 상태별 시각 | 요청·시작·완료·실패 시간 |
| `failureCode/message` | 외부에 노출 가능한 안전한 실패 정보 |
| `ruleVersion` | 계산 정책 버전 |
| `dataAsOfDate` | 계산 기준일 |

### 13.2 RiskScore

한 계산의 최종 snapshot이다. 이력 보존을 위해 이전 Score를 삭제하지 않는다.

`RiskGrade`, `DefaultStatus`, `RiskConfidence`는 서로 다른 의미다.

| 값 | 의미 |
|---|---|
| RiskGrade | 점수 구간의 위험 수준 |
| DefaultStatus | 실제 등록 사건 기반 법적·채무 상태 |
| Confidence | 계산 결과의 데이터 신뢰도 |

### 13.3 RiskSignal

점수가 왜 발생했는지 설명하는 근거 행이다. Rule 종류, Signal 종류, severity, 점수, 메시지, JSON evidence, source ID를 가진다.

### 13.4 CreditEvent

뉴스·신용평가사 자동수집 대신 관리자 API, Seed, 테스트로 입력하는 실제 사건이다.

`externalEventKey`로 동일 사건의 중복 입력을 막는다. `incidentKey`는 동일 원인 사건을 그룹화하기 위해 준비돼 있지만 현재 점수 엔진은 incident grouping을 사용하지 않는다.

### 13.5 AssetRelationship

두 Asset 사이의 계열·보증·신용지원·Cross-default 관계를 표현한다.

`fromAssetId`가 위험을 계산할 대상이고 `toAssetId`가 위험 전염의 상대방이다.

---

## 14. Day 8 데이터 모델 변경

V10은 Day 9 테이블만 추가하지 않고 Day 8 데이터에도 계산 의미를 보강했다.

### 14.1 FinancialMetric 추가 컬럼

```text
period_end_date
report_code
flow_basis
fetched_at
```

`flow_basis` enum:

```text
CUMULATIVE
DISCRETE
UNKNOWN
```

현재 컬럼과 enum은 추가됐지만 DART 수집 서비스가 이 metadata를 실제로 설정하는 로직은 아직 연결되지 않았다. 따라서 기존·신규 DART 행은 기본 `UNKNOWN`일 수 있다.

### 14.2 DebtMaturity 추가 컬럼

```text
snapshot_date
external_debt_key
```

기존 행은 migration에서 `created_at::date`를 snapshot date로 채운다.

### 14.3 DebtType 확장

기존:

```text
BOND
CP
ABSTB
LOAN
SHORT_TERM_BORROWING
```

추가:

```text
PRIVATE_BOND
CURRENT_PORTION_LONG_TERM_DEBT
LEASE_LIABILITY
```

중복 의미인 `CORPORATE_BOND`, `COMMERCIAL_PAPER`, `BANK_LOAN`은 추가하지 않았다.

### 14.4 Asset 시장 proxy

```text
assets.market_price_asset_id
```

자기 자신을 proxy로 지정하지 못하도록 CHECK가 있다. 현재 필드와 조회 로직은 구현됐지만 proxy를 설정하는 관리자 API는 없다.

---

## 15. DB 테이블과 관계

```text
app_users
   |
   v
risk_calculation_jobs ---- assets
   |
   | 1:1 (job_id unique)
   v
risk_scores
   |
   | 1:N
   v
risk_signals

assets 1:N credit_events
assets N:N assets (asset_relationships로 표현)
```

핵심 제약:

| 제약 | 목적 |
|---|---|
| active Job partial unique | 동일 Asset 동시 계산 방지 |
| `risk_scores.job_id` unique | Job당 결과 하나 |
| `risk_signals.deduplication_key` unique | 동일 Rule 근거 중복 방지 |
| `credit_events.external_event_key` unique | 동일 사건 중복 방지 |
| relationship distinct CHECK | 자기 자신과 관계 방지 |
| relationship date CHECK | 잘못된 유효기간 방지 |

evidence JSONB에는 Day 9에서 GIN index를 만들지 않았다. 현재 조회는 score ID와 asset ID 중심이기 때문이다.

---

## 16. 기존 Day 5~8과 달라진 흐름

### 16.1 Day 5 Market Collection

```text
요청
-> CollectionLog
-> market-data-fetch-requested
-> 수집 Consumer
-> MarketPrice 저장
```

주목적은 외부 데이터를 수집하고 정규화하는 것이다.

### 16.2 Day 6~7 Backtest

```text
요청
-> BacktestJob
-> backtest-requested
-> 전략 실행
-> BacktestResult 저장
```

Day 9가 가장 많이 재사용한 비동기 Job 구조다. 다만 Day 9는 다음을 강화했다.

- Asset별 활성 Job partial unique index
- native conditional update로 RUNNING 선점
- 전용 Kafka listener factory와 retry 분류
- Score와 여러 Signal의 원자적 저장
- AFTER_COMMIT 결과 이벤트
- 데이터 품질과 Confidence

### 16.3 Day 8 Financial Collection

```text
요청
-> FinancialCollectionLog
-> financial-data-fetch-requested
-> DART 조회
-> 원본 S3 저장
-> FinancialMetric upsert
```

Day 8은 데이터를 만드는 pipeline이고 Day 9는 이 데이터를 읽어 판단하는 pipeline이다.

### 16.4 Kafka 관점의 차이

기존 Day 5~8은 수집 또는 단일 결과 생성이 중심이었다. Day 9는 하나의 입력 메시지에서 다음 여러 출력이 발생한다.

```text
risk-score-calculated 1건
risk-signal-detected 0~N건
```

또한 실패 메시지는 Job FAILED 저장 후 별도 `risk-score-failed`로 발행한다.

---

## 17. 조회 API와 Top Risk Factors

### 17.1 사용자 API

| Method | Path | 기능 |
|---|---|---|
| POST | `/api/risks/assets/{assetId}/calculations` | 계산 요청 |
| GET | `/api/risks/jobs/{jobId}` | Job polling |
| GET | `/api/risks/assets/{assetId}/latest` | 최신 결과 |
| GET | `/api/risks/assets/{assetId}/history` | 결과 이력 |
| GET | `/api/risks/{riskScoreId}` | 상세 결과 |
| GET | `/api/risks/{riskScoreId}/signals` | Signal 목록 |

Job 조회는 요청자 본인 또는 ADMIN만 가능하다.

### 17.2 관리자 API

| Method | Path | 기능 |
|---|---|---|
| POST | `/api/admin/risks/assets/{assetId}/credit-events` | CreditEvent 입력 |
| GET | `/api/admin/risks/assets/{assetId}/credit-events` | CreditEvent 조회 |
| POST | `/api/admin/risks/asset-relationships` | 관계 입력 |

`SecurityConfig`에서 `/api/admin/risks/**`는 `ROLE_ADMIN`으로 제한한다.

### 17.3 Top Risk Factors

파일: `risk/service/RiskQueryService.java`

Top Factors는 DB에 별도 저장하지 않는다. RiskSignal을 조회할 때 동적으로 만든다.

정렬 기준:

1. severity 내림차순
2. score 내림차순
3. signalType 이름순
4. 동일 ruleType은 첫 번째만 사용
5. 최대 5개

이 방식은 RiskSignal을 근거의 정본으로 유지하고 UI 전용 파생 데이터를 중복 저장하지 않는 장점이 있다.

---

## 18. 프론트엔드 흐름

파일:

- `frontend/src/lib/api/risks.ts`
- `frontend/src/components/risks/risk-overview.tsx`
- `frontend/src/components/assets/asset-detail.tsx`

`AssetDetail`은 `assetType === BOND_ISSUER`일 때만 `RiskOverview`를 렌더링한다.

처리 순서:

1. Asset 상세 진입 시 최신 RiskScore 조회
2. 결과가 없으면 빈 상태 표시
3. Calculate 버튼으로 Job 요청
4. REQUESTED/RUNNING 동안 2초마다 Job 조회
5. COMPLETED이면 riskScoreId로 상세 결과 조회
6. FAILED이면 failureMessage 표시
7. 컴포넌트 unmount 또는 terminal 상태에서 interval 정리

화면 표시 항목:

- 총점
- RiskGrade와 DefaultStatus
- Confidence와 필수 Rule 성공률
- 카테고리별 점수 또는 Not available
- Top Risk Factors
- DataQuality, 기준일, Rule Version

현재 프론트는 Signal 전체 상세, evidence 구조화 표시, 만기 차트, 점수 이력 차트, CreditEvent Timeline까지는 구현하지 않았다. 백엔드 API와 데이터 구조는 일부 준비돼 있으나 화면은 요약 중심이다.

---

## 19. 테스트와 실제 검증 결과

자동 검증:

- backend 전체 Gradle test 통과
- frontend typecheck 통과
- frontend Vitest 통과
- frontend ESLint 통과
- frontend production build 통과

Docker E2E에서 확인한 성공 사례:

```text
Job             COMPLETED
Total Score     95
Risk Grade      CRITICAL
Default Status  REHABILITATION
Data Quality    COMPLETE
Confidence      HIGH
Required Rules  100%
Top Factors     5
Signals         10
```

실패 사례:

```text
Job             FAILED
Failure Code    RISK_006
Message         Financial data required for risk calculation was not found.
```

실제 Kafka에서 calculated, signal-detected, failed event 발행도 확인했다.

---

## 20. 현재 구현의 중요한 한계

코드를 읽거나 다음 작업을 계획할 때 다음을 구분해야 한다.

1. `VolatilitySpikeRiskRule`은 자리만 있고 실제 변동성 계산이 없다.
2. `CreditEventRiskRule`은 최고 사건 하나만 반영하며 incident grouping 가산 정책은 없다.
3. override 적용 사실을 별도 `CRITICAL_EVENT_OVERRIDE` Signal로 저장하지 않는다.
4. FinancialMetric의 `periodEndDate`, `reportCode`, `flowBasis`, `fetchedAt`을 DART 수집 시 채우는 연결 로직이 없다.
5. 누적 DART 현금흐름을 당기값으로 변환하는 로직이 없다.
6. `marketPriceAssetId`를 설정하는 API가 없다.
7. Confidence reason과 optional availability를 별도 DTO 필드로 제공하지 않는다.
8. 프론트는 현재 결과 요약 중심이며 Signal 전체·만기·Timeline·이력 시각화는 미구현이다.
9. Outbox가 없어 DB commit 후 Kafka 결과 이벤트가 유실될 수 있는 짧은 구간이 있다.
10. DLT와 자동 재처리는 없다. 최종 실패는 DB Job과 failed event, 로그로 운영한다.

---

## 21. 코드 읽기 권장 순서

처음 Day 9 코드를 읽는다면 다음 순서가 가장 이해하기 쉽다.

1. `RiskController`
2. `RiskCalculationRequestService`
3. `RiskCalculationJob`과 Repository
4. `RiskRequestedConsumer`
5. `RiskCalculationExecutionService`
6. `RiskEvaluationContextFactory`
7. `RiskRule`, `RiskRuleResult`, `RiskRuleRegistry`
8. `StandardRiskRuleConfiguration`
9. `RiskScoringEngine`
10. `RiskResultPersistenceService`
11. `RiskAfterCommitEventListener`
12. `RiskQueryService`
13. `RiskScore`, `RiskSignal`, `CreditEvent`, `AssetRelationship`
14. V10 migration
15. frontend `risks.ts`와 `risk-overview.tsx`

---

## 22. 장애 확인 순서

### Job이 REQUESTED에 머무는 경우

1. `risk-score-requested` topic 존재 여부
2. `finrisk-risk-calculator` Consumer group 실행 여부
3. `application-docker.yaml` trusted packages
4. Kafka 역직렬화 로그

### Job이 RUNNING에 머무는 경우

1. Consumer 예외 로그
2. DB connection과 transaction 상태
3. retry 진행 여부
4. `risk_calculation_jobs.failure_*` 갱신 여부

### COMPLETED인데 화면에 결과가 없는 경우

1. Job 응답의 `riskScoreId`
2. `risk_scores.job_id` 행 존재 여부
3. `/api/risks/{riskScoreId}` 응답
4. 프론트 polling interval 종료 조건

### 점수가 낮은데 안전한 회사가 아닌 경우

1. `dataQuality`
2. `confidence`
3. `requiredRuleSuccessRate`
4. `categoryStatuses`
5. `missingCategories`

점수만 단독으로 해석하면 안 된다.

---

## 23. 위기 감지와 RiskSignal 생성 기준

위기 감지와 Signal 저장은 같은 단계가 아니다.

```text
RiskRule이 위험 조건 평가
→ RiskRuleResult 생성
→ ScoringEngine이 점수·상태 합산
→ PersistenceService가 저장 대상 Signal 선별
→ RiskSignal DB 저장
→ COMMIT 후 중요 Signal만 Kafka 발행
```

### 23.1 Rule이 위기를 감지하는 방식

각 `RiskRule`은 `RiskEvaluationContext`의 데이터를 읽고 다음 결과를 반환한다.

```text
status
score
maxScore
severity
signalType
message
evidence
sourceId
```

예를 들어 부채비율 Rule은 다음 순서로 동작한다.

```text
FinancialMetric에서 totalDebt와 totalEquity 조회
→ totalDebt / totalEquity * 100 계산
→ 임계구간에 따라 0~5점 결정
→ HIGH_DEBT_RATIO 또는 CAPITAL_IMPAIRMENT 지정
→ 계산값과 임계값을 evidence에 기록
```

위험 조건을 만족하지 않아도 데이터가 충분하면 `CALCULATED, 0점` 결과를 반환한다. 데이터가 없으면 안전한 0점으로 바꾸지 않고 `NOT_AVAILABLE`을 반환한다.

```text
데이터 있음 + 위험 없음
→ CALCULATED, 0점

데이터 있음 + 위험 있음
→ CALCULATED, 1점 이상

필요 데이터 없음
→ NOT_AVAILABLE
```

### 23.2 현재 감지하는 주요 위기

| 영역 | 감지 내용 | 대표 Signal |
|---|---|---|
| 재무 | 높은 부채비율, 자본잠식 | `HIGH_DEBT_RATIO`, `CAPITAL_IMPAIRMENT` |
| 재무 | 낮은 이자보상배율, 영업손실 | `INTEREST_COVERAGE_LOW`, `OPERATING_LOSS` |
| 재무 | 음수 영업현금흐름 지속 | `NEGATIVE_OPERATING_CASH_FLOW`, `PERSISTENT_CASH_FLOW_DEFICIT` |
| 재무 | 연속 순손실 | `NET_LOSS`, `PERSISTENT_NET_LOSS` |
| 유동성 | 6개월 내 만기 집중 | `MATURITY_WALL` |
| 유동성 | CP·ABSTB·단기차입 의존 | `SHORT_TERM_DEBT_DEPENDENCY` |
| 유동성 | 현금 대비 만기부채 부족 | `LOW_LIQUIDITY_COVERAGE` |
| 유동성 | 이전 snapshot 대비 만기 증가 | `MATURITY_CONCENTRATION_INCREASE` |
| 시장 | 최근 20거래일 가격 급락 | `PRICE_CRASH` |
| 시장 | 거래량 급증 | `ABNORMAL_VOLUME` |
| 신용사건 | 차환실패, 지급지연, 부도, 회생, 파산 | 해당 `CreditEventType` 이름 |
| 그룹 | 보증·지원·Cross-default를 통한 위험 전이 | `GROUP_CONTAGION_RISK` |

세부 산식과 구간은 이 문서의 “8. 현재 Rule별 계산 방식”을 참고한다.

### 23.3 DB RiskSignal 생성 조건

실제 저장 기준은 `RiskResultPersistenceService.persist()`에 있다.

```java
for (RiskRuleResult result : outcome.results()) {
    if (result.status() != CategoryCalculationStatus.CALCULATED
        || result.score() <= 0) {
        continue;
    }

    // RiskSignal 생성
}
```

따라서 다음 두 조건을 모두 만족해야 RiskSignal 행이 생성된다.

```text
status == CALCULATED
score > 0
```

| Rule 결과 | RiskSignal DB 저장 |
|---|---|
| CALCULATED, 5점 | 저장 |
| CALCULATED, 1점 | 저장 |
| CALCULATED, 0점 | 저장하지 않음 |
| NOT_AVAILABLE | 저장하지 않음 |
| INSUFFICIENT_DATA | 저장하지 않음 |

0점과 데이터 부족 결과를 버린다는 의미는 아니다. 이 정보는 RiskScore의 다음 필드에 반영된다.

```text
categoryStatuses
missingCategories
dataQuality
confidence
requiredRuleSuccessRate
```

### 23.4 Severity 결정 기준

`RiskRuleResult.calculated()`는 해당 Rule의 최대점수 대비 획득점수 비율로 severity를 정한다.

```text
위험 비율 = score / maxScore
```

| 조건 | Severity |
|---:|---|
| score <= 0 | INFO |
| 0% 초과, 30% 미만 | LOW |
| 30% 이상 | MEDIUM |
| 60% 이상 | HIGH |
| 80% 이상 | CRITICAL |

예시:

```text
DebtRatio 5/5
→ 100%
→ CRITICAL

MaturityConcentration 6/10
→ 60%
→ HIGH

PriceDrop 1/4
→ 25%
→ LOW
```

Severity는 회사 전체 RiskGrade가 아니다. 개별 Rule이 탐지한 Signal의 심각도다.

### 23.5 RiskSignal에 저장하는 근거

저장 필드:

```text
riskScoreId
assetId
category
ruleType
signalType
severity
score
message
evidence JSONB
sourceType
sourceId
detectedAt
deduplicationKey
```

예시:

```json
{
  "category": "FINANCIAL",
  "ruleType": "DEBT_RATIO",
  "signalType": "HIGH_DEBT_RATIO",
  "severity": "CRITICAL",
  "score": 5,
  "message": "Debt ratio evaluated",
  "evidence": {
    "currentValue": 600,
    "threshold": "100/200/300/500"
  }
}
```

`sourceId`가 있으면 근거가 된 FinancialMetric, MarketPrice 등의 ID를 연결한다. deduplication key는 다음 구조다.

```text
riskScoreId:ruleType:sourceId-or-policy
```

### 23.6 Kafka Signal 발행 기준

DB에 저장된 모든 Signal을 Kafka로 발행하지 않는다.

`RiskAfterCommitEventListener`는 Transaction COMMIT 후 다음 severity만 `risk-signal-detected`로 발행한다.

```text
HIGH
CRITICAL
```

| Severity | DB 저장 | Kafka 발행 |
|---|---|---|
| LOW | 저장 | 발행하지 않음 |
| MEDIUM | 저장 | 발행하지 않음 |
| HIGH | 저장 | 발행 |
| CRITICAL | 저장 | 발행 |

전체 흐름:

```text
CALCULATED + score > 0
→ RiskSignal DB 저장
→ Score·Signal·Job COMMIT
→ HIGH/CRITICAL만 risk-signal-detected 발행
```

이렇게 하면 낮은 수준의 모든 징후를 Kafka 알림으로 확산하지 않으면서 DB에서는 전체 양수 Signal 이력을 조회할 수 있다.

### 23.7 현재 Signal 생성의 한계

1. `CALCULATED, 0점` 정보성 결과는 Signal 행으로 저장하지 않는다.
2. `NOT_AVAILABLE` 원인은 개별 Signal이 아니라 RiskScore의 누락 정보로만 확인한다.
3. 실제 사건 override로 총점이 상승해도 `CRITICAL_EVENT_OVERRIDE` Signal을 별도로 생성하지 않는다.
4. CreditEvent Rule은 현재 대표 사건 하나의 Signal만 저장한다.
5. 동일 incident 안의 여러 사건을 각각 Signal로 분해하지 않는다.

---

## 24. 패키지별 클래스 인벤토리

### 24.1 Domain과 Enum

| 클래스 | 역할 |
|---|---|
| `RiskCalculationJob` | 비동기 계산 작업과 상태·실패 이력 |
| `RiskCalculationStatus` | REQUESTED/RUNNING/COMPLETED/FAILED |
| `RiskScore` | 한 계산의 최종 점수 snapshot |
| `RiskGrade` | 점수 기반 LOW~CRITICAL 등급 |
| `DefaultStatus` | 실제 사건 기반 채무·법적 상태 |
| `RiskDataQuality` | COMPLETE/PARTIAL/INSUFFICIENT/STALE |
| `RiskConfidence` | 결과 신뢰도 HIGH/MEDIUM/LOW |
| `RiskCategory` | FINANCIAL, LIQUIDITY, MARKET, EVENT, GROUP |
| `CategoryCalculationStatus` | 카테고리 계산 가능 여부 |
| `RiskSignal` | 점수의 설명 가능한 근거 |
| `RiskSeverity` | Signal 심각도 |
| `CreditEvent` | 직접 입력된 실제 신용사건 |
| `CreditEventType` | 차환실패, 지급불이행, 회생 등 사건 유형 |
| `AssetRelationship` | Asset 간 보증·지원·Cross-default 관계 |
| `AssetRelationshipType` | 관계 유형 enum |
| `RiskScenarioSeedData` | property로 활성화하는 synthetic JTBC 시나리오 |

### 24.2 Repository

| 클래스 | 주요 조회·변경 |
|---|---|
| `RiskCalculationJobRepository` | 활성 Job 존재 확인, 조건부 RUNNING native update |
| `RiskScoreRepository` | job 결과, 최신 결과, asset 이력 조회 |
| `RiskSignalRepository` | score별 Signal 점수순 조회 |
| `CreditEventRepository` | external key 중복 확인, 기준일 이전 사건 조회 |
| `AssetRelationshipRepository` | fromAsset 기준 관계 조회 |

### 24.3 Engine

| 클래스 | 역할 |
|---|---|
| `RiskEvaluationContext` | 모든 Rule이 공유하는 immutable 입력 |
| `RiskEvaluationContextFactory` | Repository 데이터를 Context로 조립 |
| `RiskRule` | Rule 공통 계약 |
| `RiskRuleType` | Rule의 유일 식별자 |
| `RiskRuleResult` | Rule별 상태·점수·evidence 결과 |
| `RiskRuleRegistry` | Bean 수집, priority 정렬, 중복 검증 |
| `RiskScoringEngine` | Rule 실행과 최종 정책 적용 |
| `RiskCalculationOutcome` | persistence로 전달하는 전체 계산 결과 |
| `RiskJobExecutionSummary` | 메모리 안에서 Rule 실행 건수 집계 |

### 24.4 Rule

| 클래스 | 역할 |
|---|---|
| `StandardRiskRuleConfiguration` | 현재 14개 RiskRule Bean과 공통 계산 helper 제공 |

현재 Rule은 별도 public class 14개가 아니라 Configuration의 `@Bean`으로 등록된다. Bean별 priority와 `RiskRuleType`이 있으므로 Registry에서는 각각 독립 Rule처럼 실행된다.

### 24.5 Service

| 클래스 | 역할 |
|---|---|
| `RiskCalculationRequestService` | 요청 검증, Job 생성, requested event 발행 |
| `RiskCalculationJobService` | Job 생성·조회·RUNNING·FAILED 전이 |
| `RiskCalculationExecutionService` | Context→Engine→Persistence 실행 orchestration |
| `RiskResultPersistenceService` | Score·Signal·COMPLETED 원자적 저장 |
| `RiskCalculationCompletedNotification` | Transaction 내부 완료 notification |
| `RiskAfterCommitEventListener` | commit 후 Kafka 결과·Signal 이벤트 발행 |
| `RiskQueryService` | 권한 확인, 최신·이력·상세·Signal 조회, Top Factors 생성 |
| `RiskAdminService` | CreditEvent·AssetRelationship 검증과 저장 |

### 24.6 Kafka와 Event

| 클래스 | 역할 |
|---|---|
| `RiskTopics` | 네 개 topic 이름 상수 |
| `RiskKafkaConfiguration` | Kafka `NewTopic` Bean 생성 |
| `RiskKafkaErrorConfiguration` | Risk 전용 listener factory, retry와 recovery |
| `RiskRequestedConsumer` | requested event 소비 |
| `RiskEventPublisher` | requested 동기 확인 발행, 결과 비동기 발행 |
| `RiskEventPublishException` | 요청 event 발행 실패 wrapper |
| `RiskScoreRequestedEvent` | 계산 요청 payload |
| `RiskScoreCalculatedEvent` | 완료 결과 payload |
| `RiskScoreFailedEvent` | 최종 실패 payload |
| `RiskSignalDetectedEvent` | 중요 Signal payload |

### 24.7 API와 DTO

| 클래스 | 역할 |
|---|---|
| `RiskController` | 사용자 계산·Job·결과·Signal API |
| `RiskAdminController` | 관리자 사건·관계 API |
| `RiskJobResponse` | polling용 Job 응답 |
| `RiskScoreResponse` | 상세 점수·품질·Confidence·Top Factors 응답 |
| `RiskSignalResponse` | Signal 상세 응답 |
| `TopRiskFactorResponse` | 사용자에게 보여줄 핵심 위험 요인 |
| `CreditEventCreateRequest` | 관리자 사건 입력 검증 DTO |
| `AssetRelationshipCreateRequest` | 관리자 관계 입력 검증 DTO |
| `RiskResponses` | 현재 동작 없는 package helper placeholder |

`RiskResponses`는 현재 기능을 제공하지 않으므로 삭제해도 동작에 영향이 없다.

### 24.8 Day 8 보완 클래스

| 클래스 | Day 9를 위해 달라진 점 |
|---|---|
| `Asset` | `marketPriceAssetId` 추가 |
| `FinancialMetric` | 기준일·보고서·flow basis·수집시각 추가 |
| `FinancialFlowBasis` | 누적/당기/미확인 구분 |
| `DebtMaturity` | snapshot date와 external debt key 추가 |
| `DebtType` | 사모채·유동성장기부채·리스부채 추가 |
| 관련 Repository | 기간·snapshot 기반 조회 기반 보완 |
