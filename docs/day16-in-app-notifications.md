# Day 16 인앱 알림 시스템

## 1. 구현 목표

Day 16에서는 백테스트, AI 리포트, 위험 계산, 결제, FSD에서 발생한 중요한 결과를
사용자 또는 관리자에게 보여주는 영속적인 인앱 알림 시스템을 구현했다.

알림은 원본 도메인의 상태가 아니라 원본 결과에서 만들어지는 **파생 데이터**다.

```text
백테스트 COMPLETED
→ 백테스트 완료 알림 생성

알림 저장 실패
→ 백테스트 COMPLETED 상태는 그대로 유지
```

따라서 알림 생성·조회·읽음 처리 실패가 백테스트, 리포트, 위험 계산, 결제 등의 원본
상태를 되돌리지 않는다.

Day 16에서 구현한 주요 기능은 다음과 같다.

- 사용자별 알림 목록과 unread count
- 알림 한 건 읽음 및 전체 읽음
- 백테스트 완료·실패 알림
- AI 리포트 완료·실패 알림
- 위험 계산 요청자에게만 전달되는 HIGH/CRITICAL 알림
- 결제 완료·취소·실패 알림
- 결제 복구 필요 관리자 알림
- FSD 검토 필요 관리자 알림
- Kafka 재전달에 대한 DB 멱등성
- Header Bell, unread badge, 최근 5개 알림
- 전체 알림 목록 화면

## 2. 전체 구조

백테스트, 리포트, 위험, FSD 결과는 다음 흐름을 사용한다.

```text
원본 도메인 Service
→ 원본 상태 DB 저장
→ ApplicationEventPublisher로 Spring 내부 이벤트 발행
→ DB commit 성공
→ @TransactionalEventListener(AFTER_COMMIT)
→ 도메인 EventPublisher
→ Kafka 결과 topic
→ Notification Kafka Consumer
→ NotificationService
→ notifications 테이블
```

결제는 기존 Outbox 구조를 유지한다.

```text
PaymentPersistenceService
→ 결제 상태와 outbox_events를 같은 트랜잭션으로 저장
→ OutboxPublisher
→ finrisk.payment.events.v1
→ PaymentNotificationConsumer
→ NotificationService
→ notifications 테이블
```

최종 알림 대상은 다음과 같다.

| 원본 결과 | 대상 | 알림 |
| --- | --- | --- |
| 백테스트 완료·최종 실패 | 백테스트 요청 사용자 | 결과 알림 |
| AI 리포트 완료·최종 실패 | 리포트 요청 사용자 | 결과 알림 |
| HIGH/CRITICAL이 존재하는 위험 계산 | 위험 계산 요청 사용자 | 계산당 한 건의 위험 알림 |
| 결제 완료·복구 완료·취소·최종 실패 | 결제 주문 사용자 | 결제 결과 알림 |
| 결제 복구 필요 | 모든 `ROLE_ADMIN` | 운영 확인 알림 |
| 신규 FSD `REVIEW` 또는 `BLOCK` | 모든 `ROLE_ADMIN` | FSD 검토 알림 |

## 3. Kafka와 `ApplicationEventPublisher`

### 3.1 차이

| 구분 | `ApplicationEventPublisher` | Kafka |
| --- | --- | --- |
| 범위 | 같은 Spring 애플리케이션 내부 | Kafka broker를 통한 메시지 전달 |
| 전달 기준 | Java 이벤트 타입 | Kafka topic |
| 저장 | 메모리 이벤트, 별도 보관 없음 | broker에 일정 기간 보관 |
| Consumer group | 없음 | 있음 |
| 직렬화 | 일반적으로 필요 없음 | JSON 등의 직렬화 필요 |
| 현재 용도 | DB commit 이후 후속 처리 연결 | 알림 Consumer에 결과 전달 |

현재 FinRisk Radar 백엔드는 하나의 Spring Boot 애플리케이션이다. 따라서 Kafka Producer와
Notification Consumer도 현재는 같은 애플리케이션에 존재한다. Kafka가 기술적으로 반드시
필요한 것은 아니지만, 기존 프로젝트의 Kafka 처리 방식과 일관성을 유지하고 Kafka 발행
이후의 Consumer 재시도, 향후 서비스 분리 가능성을 확보하기 위해 결과 이벤트에도 Kafka를
사용했다.

### 3.2 `ApplicationEventPublisher`를 사용한 이유

원본 DB 트랜잭션 안에서 Kafka를 바로 호출하면 다음 불일치가 생길 수 있다.

```text
Kafka 완료 이벤트 발행 성공
→ 이후 DB commit 실패
→ Kafka에는 완료 이벤트가 있지만 DB에는 완료 상태가 없음
```

이를 피하기 위해 원본 Service는 Spring 내부 이벤트만 발행한다.

```java
events.publishEvent(new ReportFinishedNotification(...));
```

이 이벤트는 다음 Listener가 받는다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void finished(ReportFinishedNotification event) {
    ...
}
```

`AFTER_COMMIT`이므로 실제 순서는 다음과 같다.

```text
원본 상태 변경
→ Spring 내부 이벤트 등록
→ DB commit 성공
→ Listener 실행
→ Kafka 발행
```

DB transaction이 rollback되면 `AFTER_COMMIT` Listener는 실행되지 않는다.

### 3.3 현재 방식의 한계

다음 반대 상황은 여전히 가능하다.

```text
DB commit 성공
→ 프로세스 종료 또는 Kafka 발행 실패
→ 결과 이벤트 유실
→ 알림 미생성
```

Day 16에서는 백테스트, 리포트, 위험, FSD에 Outbox를 추가하지 않았다. 이 영역에서는
발행 실패 로그와 metric을 남기고 원본 상태는 유지한다. 이 유실 구간까지 제거하려면 향후
공용 Outbox를 도입해야 한다.

결제 도메인은 이미 Outbox가 있으므로 기존 재시도 구조를 그대로 사용한다.

## 4. DB와 알림 데이터 모델

Flyway `V19__create_notifications.sql`에서 `notifications` 테이블을 추가했다.

```text
notifications
├─ id
├─ user_id
├─ type
├─ title
├─ message
├─ reference_type
├─ reference_id
├─ target_url
├─ event_id
├─ is_read
├─ created_at
└─ read_at
```

### 4.1 필드 의미

| 필드 | 의미 |
| --- | --- |
| `user_id` | 알림을 받는 사용자 |
| `type` | 알림 종류와 UI 분류 기준 |
| `title` | 사용자에게 보여주는 짧은 제목 |
| `message` | 사용자에게 보여주는 본문 |
| `reference_type` | 원본 도메인 종류 |
| `reference_id` | 원본 객체 식별자 |
| `target_url` | 클릭 시 이동할 내부 경로 |
| `event_id` | 동일 결과 이벤트의 중복 저장 방지 키 |
| `is_read` | 읽음 여부 |
| `created_at` | 생성 시간 |
| `read_at` | 최초 읽은 시간 |

`reference_id`와 `event_id`는 원본 ID가 Long 또는 UUID인 경우를 모두 처리하기 위해 문자열로
저장한다.

```text
event_id     VARCHAR(100)
reference_id VARCHAR(100)
```

### 4.2 `reference_id`와 `event_id` 차이

```text
reference_id
= 어떤 원본 객체를 가리키는가

event_id
= 어떤 결과 이벤트로 만들어진 알림인가
```

예를 들어 백테스트 작업 81의 완료 알림은 다음과 같다.

```text
reference_id = 81
event_id     = backtest:completed:81
```

같은 작업이 실패 결과라면 이벤트 ID가 달라진다.

```text
event_id = backtest:failed:81
```

### 4.3 멱등성

DB에는 다음 unique 제약이 있다.

```sql
UNIQUE (event_id, user_id)
```

같은 이벤트가 Kafka에서 재전달돼도 같은 사용자에게 알림이 중복 저장되지 않는다.

저장은 PostgreSQL의 다음 문장을 사용한다.

```sql
INSERT INTO notifications (...)
VALUES (...)
ON CONFLICT (event_id, user_id)
DO NOTHING;
```

`exists()`로 먼저 조회하지 않고 한 번의 INSERT 문으로 처리하므로 동시 요청에도 안전하다.

```text
신규 저장 → 영향받은 행 1개 → inserted = 1
중복 저장 → 영향받은 행 0개 → inserted = 0
```

## 5. 공통 Notification 패키지

### 5.1 `Notification`

알림 JPA 엔티티다. DB 컬럼을 표현하고 알림 한 건의 읽음 상태 전이를 담당한다.

```java
public void markRead(LocalDateTime now) {
    if (read) return;
    read = true;
    readAt = now.truncatedTo(ChronoUnit.MICROS);
}
```

이미 읽은 알림은 아무것도 변경하지 않으므로 최초 `readAt`을 보존한다. PostgreSQL의 timestamp
정밀도와 첫 응답 값을 일치시키기 위해 마이크로초 단위로 절삭한다.

### 5.2 `NotificationType`

알림의 사건 종류를 나타낸다.

```text
BACKTEST_COMPLETED
BACKTEST_FAILED
REPORT_COMPLETED
REPORT_FAILED
HIGH_RISK_SIGNAL_DETECTED
PAYMENT_COMPLETED
PAYMENT_CANCELED
PAYMENT_FAILED
FSD_REVIEW_REQUIRED
PAYMENT_RECOVERY_REQUIRED
```

프론트의 필터, 아이콘, 표시 방식에 사용할 수 있는 시스템 분류 값이다.

### 5.3 `NotificationReferenceType`

알림이 어떤 원본 도메인을 가리키는지 나타낸다.

```text
BACKTEST
AI_REPORT
ASSET
PAYMENT_ORDER
FSD_EVENT
```

### 5.4 `NotificationRepository`

알림 DB 접근을 담당한다.

주요 메서드는 다음과 같다.

| 메서드 | 역할 |
| --- | --- |
| `findByIdAndUserId` | 사용자 소유 알림 한 건 조회 |
| `countByUserIdAndReadFalse` | unread count |
| `insertIgnoringDuplicate` | 중복을 무시하는 알림 INSERT |
| `markAllRead` | 현재 사용자의 unread 알림 일괄 UPDATE |

전체 읽음 처리는 한 번의 bulk update로 실행한다.

```java
update Notification n
set n.read = true, n.readAt = :readAt
where n.userId = :userId
  and n.read = false
```

이미 읽은 알림은 조건에서 제외되므로 기존 `readAt`이 유지된다.

### 5.5 `NotificationService`

알림 생성과 읽음 변경을 담당한다.

#### 사용자 한 명에게 생성

```java
boolean create(Long userId, Command command)
```

처리 순서:

```text
Command 검증
→ INSERT ... ON CONFLICT DO NOTHING
→ 신규면 notification.created 증가
→ 중복이면 notification.duplicate 증가
```

`Command`는 알림을 만들 때 필요한 값을 묶은 객체다.

```text
eventId
type
title
message
referenceType
referenceId
targetUrl
```

`targetUrl`은 `/`로 시작하는 내부 경로만 허용하고 `//`로 시작하는 외부 우회 경로는 거부한다.

#### 모든 관리자에게 생성

```java
int createForAdmins(Command command)
```

`UserRepository.findIdsByRole(ROLE_ADMIN)`으로 관리자 ID를 조회하고 각 관리자에게 같은
이벤트의 알림을 저장한다. unique 기준에 `user_id`가 포함되므로 동일 이벤트도 관리자별로
한 건씩 저장된다.

#### 개별 읽음

```java
Item markRead(Long userId, Long notificationId)
```

`notificationId + userId`로 조회하므로 다른 사용자의 알림을 읽을 수 없다. 다른 사용자
알림이거나 존재하지 않는 경우 모두 `NOTIFICATION_NOT_FOUND`로 처리해 존재 여부를 노출하지
않는다.

#### 전체 읽음

```java
ReadAllResult markAllRead(Long userId)
```

현재 사용자의 unread 알림만 변경하고 실제 변경된 개수를 `updatedCount`로 반환한다.

### 5.6 `NotificationQueryService`

조회 전용 서비스다.

```java
PageResponse list(
    Long userId,
    Boolean read,
    NotificationType type,
    int page,
    int size
)
```

JPA `Specification`으로 조건을 동적으로 조립한다.

```text
userId 조건 → 항상 적용
read 조건   → 요청값이 있을 때만 적용
type 조건   → 요청값이 있을 때만 적용
```

예를 들어 다음 요청은:

```http
GET /api/notifications?read=false&type=REPORT_FAILED
```

개념적으로 다음 조건이 된다.

```sql
WHERE user_id = :currentUserId
  AND is_read = false
  AND type = 'REPORT_FAILED'
ORDER BY created_at DESC, id DESC
```

페이지는 0 이상, size는 1 이상 50 이하로 보정한다. `createdAt DESC, id DESC`를 사용해 생성
시간이 같아도 순서를 안정적으로 유지한다.

```java
UnreadCount unreadCount(Long userId)
```

는 현재 사용자의 `read = false` 행 개수를 반환한다.

### 5.7 `NotificationModels`

API 응답 DTO를 한 클래스에 모았다.

| 모델 | 의미 |
| --- | --- |
| `Item` | 알림 한 건 |
| `PageResponse` | 알림 목록과 페이지 정보 |
| `UnreadCount` | unread 개수 |
| `ReadAllResult` | 전체 읽음으로 변경한 행 개수 |

`Item.from(Notification)`과 `PageResponse.from(Page<Notification>)`이 엔티티를 API 응답으로
변환한다. 내부 중복 방지용 `eventId`와 사용자 소유 정보 `userId`는 응답에서 제외한다.

### 5.8 `NotificationController`

로그인 사용자에게 네 API를 제공한다.

```http
GET   /api/notifications
GET   /api/notifications/unread-count
PATCH /api/notifications/{notificationId}/read
PATCH /api/notifications/read-all
```

`CustomUserPrincipal.userId()`를 Service에 넘겨 모든 조회와 변경을 사용자 단위로 제한한다.

## 6. Kafka Consumer 구조

하나의 거대한 Consumer 대신 원본 영역별로 분리했다.

```text
JobNotificationConsumer
├─ BacktestCompletedEvent
├─ BacktestFailedEvent
├─ ReportCompletedEvent
└─ ReportFailedEvent

PaymentNotificationConsumer
├─ PaymentDomainEvent
└─ FsdReviewRequiredEvent

RiskNotificationConsumer
└─ RiskScoreCalculatedEvent
```

모든 Notification Consumer는 다음 group ID를 사용한다.

```text
finrisk-notification-v1
```

기존 작업 Consumer와 다른 group이므로 기존 Consumer와 메시지를 나눠 갖지 않는다.

Consumer 처리 중 DB 오류가 발생하면 예외를 Kafka에 다시 전달해 재시도를 허용한다. 재시도가
끝까지 실패하면 `notification.consumer.failure` metric을 증가시킨다. 재전달로 동일 알림이
다시 저장될 때는 DB conflict-ignore가 0을 반환하므로 예외 없이 정상 소비를 마친다.

## 7. 백테스트 알림 흐름

### 7.1 변경된 클래스

| 클래스 | 변경 내용 |
| --- | --- |
| `BacktestJobService` | 완료·실패 저장 후 Spring 내부 이벤트 발행, 최종 상태 중복 방어 |
| `BacktestFinishedNotification` | commit 전 내부 전달용 이벤트 |
| `BacktestAfterCommitEventListener` | commit 후 완료·실패 Kafka 이벤트로 변환 |
| `BacktestCompletedEvent` | 완료 Kafka payload |
| `BacktestFailedEvent` | 실패 Kafka payload |
| `BacktestTopics` | 완료·실패 topic 이름 추가 |
| `BacktestKafkaConfiguration` | 완료·실패 topic 생성 Bean 추가 |
| `BacktestEventPublisher` | 완료·실패 Kafka 발행과 실패 metric 추가 |

### 7.2 코드 흐름

`BacktestJobService`가 결과 저장과 `job.complete()`를 수행한 뒤 내부 이벤트를 발행한다.

```text
BacktestFinishedNotification
├─ jobId
├─ requestedByUserId
├─ assetId
├─ status
└─ occurredAt
```

실패 처리에서는 이미 `COMPLETED` 또는 `FAILED`이면 다시 처리하지 않는다.

```text
RUNNING → FAILED → 이벤트 발행
FAILED  → markFailed 재호출 → 무시
```

`BacktestAfterCommitEventListener`는 commit 이후 상태에 따라 분기한다.

```text
COMPLETED → BacktestCompletedEvent → backtest-completed
FAILED    → BacktestFailedEvent    → backtest-failed
```

`JobNotificationConsumer`는 요청 사용자에게 다음 알림을 만든다.

```text
완료 eventId = backtest:completed:{jobId}
실패 eventId = backtest:failed:{jobId}
targetUrl    = /backtests?jobId={jobId}
```

별도 `/backtests/{jobId}` 상세 화면은 만들지 않고 기존 Workbench를 재사용한다.

## 8. AI 리포트 알림 흐름

### 8.1 변경된 클래스

| 클래스 | 변경 내용 |
| --- | --- |
| `ReportPersistenceService` | 최종 상태 저장 후 내부 이벤트 발행, 중복 최종 처리 방어 |
| `ReportFinishedNotification` | commit 전 내부 전달용 이벤트 |
| `ReportAfterCommitEventListener` | commit 후 완료·실패 Kafka 이벤트로 변환 |
| `ReportCompletedEvent` | 완료 Kafka payload |
| `ReportFailedEvent` | 실패 Kafka payload |
| `ReportTopics` | `report-completed`, `report-failed` 추가 |
| `ReportKafkaConfiguration` | 완료·실패 topic 생성 |
| `ReportEventPublisher` | 완료·실패 Kafka 발행과 실패 metric |

### 8.2 완료·실패 흐름

`ReportPersistenceService.complete()`는 리포트를 완료 처리한 뒤 다음 내부 이벤트를 발행한다.

```text
ReportFinishedNotification
├─ reportId
├─ userId
├─ reportType
├─ status
└─ occurredAt
```

`fail()`은 이미 `COMPLETED` 또는 `FAILED`인 리포트를 다시 실패 처리하지 않는다.

`ReportAfterCommitEventListener`는 다음과 같이 Kafka 이벤트를 선택한다.

```text
COMPLETED → ReportCompletedEvent → report-completed
FAILED    → ReportFailedEvent    → report-failed
```

`JobNotificationConsumer`는 리포트 소유 사용자에게 알림을 만든다.

```text
완료 eventId = report:completed:{reportId}
실패 eventId = report:failed:{reportId}
targetUrl    = /reports/{reportId}
```

### 8.3 실패 복구와 보상 처리

리포트 요청 시 사용자 사용량을 Redis에 먼저 예약한다.

```text
사용량 예약
→ 리포트 DB 생성
→ report-generation-requested Kafka 발행
```

생성 요청 발행이 실패하거나 복구 발행도 최종 실패하면 리포트를 `FAILED`로 만들고 예약한
사용량을 반환한다.

```text
실패 복구
= 전송되지 못하거나 멈춘 리포트 요청을 Scheduler가 다시 발행

보상 처리
= 실제 생성이 시작되지 못한 리포트가 차감한 사용량을 반환
```

`usageCompensatedAt`으로 사용량 반환을 한 번만 수행하고, 최종 상태 검사로 실패 metric과 실패
결과 이벤트도 한 번만 생성한다.

## 9. 위험 알림 흐름

### 9.1 가장 중요한 정책

위험 계산은 사용자가 직접 요청한다. 따라서 Watchlist에 같은 자산을 등록한 모든 사용자에게
알림을 보내지 않는다.

```text
사용자 위험 계산 요청
→ 해당 사용자의 RiskCalculationJob
→ 계산 완료
→ 현재 계산에 HIGH/CRITICAL 존재
→ 요청 사용자에게 알림 한 건
```

`WatchlistRepository`와 사용자 fan-out은 사용하지 않는다.

### 9.2 변경된 클래스

| 클래스 | 변경 내용 |
| --- | --- |
| `RiskResultPersistenceService` | 내부 이벤트에 `job.userId`와 이번 계산의 Signal 목록 전달 |
| `RiskCalculationCompletedNotification` | `userId` 필드 추가 |
| `RiskAfterCommitEventListener` | 현재 Signal에서 최고 severity와 고위험 개수 계산 |
| `RiskScoreCalculatedEvent` | `userId`, `highestSeverity`, `highRiskSignalCount` 추가 |
| `RiskEventPublisher` | Kafka 즉시·비동기 실패 로그와 metric 추가 |
| `RiskNotificationConsumer` | payload 요약만으로 알림 조건 판단 |

### 9.3 현재 계산 Signal만 사용

`RiskResultPersistenceService`는 이번 계산에서 실제 저장한 `List<RiskSignal>`을 내부 이벤트에
담는다.

`RiskAfterCommitEventListener`는 이 목록에서 다음 값을 계산한다.

```text
highestSeverity
= 현재 계산 Signal 중 가장 높은 severity

highRiskSignalCount
= 현재 계산 Signal 중 HIGH 또는 CRITICAL 개수
```

Signal이 없으면 `highestSeverity = INFO`다.

`RiskScoreCalculatedEvent` payload:

```text
jobId
userId
assetId
riskScoreId
totalScore
riskGrade
defaultStatus
highestSeverity
highRiskSignalCount
calculatedAt
```

`RiskNotificationConsumer`는 Signal 테이블을 다시 조회하지 않는다. 다음 두 조건을 모두
만족할 때만 알림을 생성한다.

```text
highRiskSignalCount > 0
AND
highestSeverity가 HIGH 또는 CRITICAL
```

```text
eventId  = risk:calculated:{jobId}
대상     = event.userId
targetUrl = /assets/{assetId}
```

LOW/MEDIUM만 존재하거나 Signal이 없으면 정상 소비 후 아무 알림도 만들지 않는다.

`RiskAfterCommitEventListener`의 기존 `publishSignal()`은 HIGH/CRITICAL 개별 Signal 이벤트를
발행하는 용도다. Day 16 사용자 알림은 개별 Signal마다 여러 건을 만들지 않고
`publishCalculated()`의 계산 요약 이벤트 한 건을 사용한다.

## 10. 결제 알림과 Outbox

### 10.1 공통 이벤트 Envelope

결제 topic에는 여러 종류의 이벤트가 함께 존재한다.

```text
topic = finrisk.payment.events.v1
value = PaymentDomainEvent
```

`PaymentDomainEvent` 구조:

```text
eventId
eventType
occurredAt
payload
eventVersion
```

`PaymentNotificationConsumer`는 특정 완료 DTO가 아닌 이 공통 타입을 받고 `eventType`으로
분기한다.

```text
PaymentCompletedEvent         → PAYMENT_COMPLETED
PaymentRecoveryCompletedEvent → PAYMENT_COMPLETED
PaymentCanceledEvent          → PAYMENT_CANCELED
PaymentFailedEvent            → PAYMENT_FAILED
PaymentRecoveryRequiredEvent  → 관리자 PAYMENT_RECOVERY_REQUIRED
SubscriptionActivatedEvent    → 정상 소비 후 무시
```

알림과 관련 없는 이벤트를 정상적으로 무시하므로 같은 topic에 새로운 이벤트가 추가돼도 특정
DTO 역직렬화 오류를 만들지 않는다.

### 10.2 `PaymentPersistenceService` 변경

`markRecoveryRequired()`에서 주문 상태만 변경하던 기존 동작에
`PaymentRecoveryRequiredEvent` Outbox 저장을 추가했다.

```text
payment_orders.status = RECOVERY_REQUIRED
outbox_events.event_type = PaymentRecoveryRequiredEvent
outbox_events.status = PENDING
```

두 변경은 같은 DB 트랜잭션으로 commit된다.

### 10.3 `RECOVERY_REQUIRED`와 이벤트의 차이

```text
RECOVERY_REQUIRED
= 결제사 결과를 확정하지 못해 다시 확인해야 하는 주문 상태

PaymentRecoveryRequiredEvent
= 해당 상태가 발생했다는 사실을 관리자에게 알리는 이벤트
```

이벤트가 실제 복구를 수행하는 것은 아니다. 실제 복구는 `PaymentRecoveryScheduler`가 오래된
`CONFIRMING`, `CANCELING`, `RECOVERY_REQUIRED` 주문을 조회하고 결제사 상태를 다시 확인한다.

```text
결제사에서 결제 성공 확인
→ PAID
→ PaymentRecoveryCompletedEvent
→ 사용자 결제 완료 알림

결제사에서 전체 취소 확인
→ CANCELED
→ PaymentCanceledEvent
→ 사용자 취소 알림

아직 확인 실패
→ RECOVERY_REQUIRED 유지
→ 다음 Scheduler 실행에서 재시도
```

### 10.4 Outbox 발행 과정

`OutboxPublisher`는 `status = PENDING`인 이벤트만 조회한다.

```text
PENDING   = 아직 Kafka로 보내야 함
PUBLISHED = 발행 성공
FAILED    = 최대 재시도 후 최종 실패
```

발행에 성공하면 `PUBLISHED`, 재시도 가능한 실패면 다시 `PENDING`, 최대 횟수를 넘으면
`FAILED`가 된다. `PUBLISHED`까지 다시 조회하면 같은 이벤트를 계속 발행하게 되므로
`PENDING`만 조회한다.

## 11. FSD 관리자 알림

### 11.1 `FsdEngine` 변경

FSD 판정 규칙과 점수 계산은 변경하지 않았다. 신규 `REVIEW` 또는 `BLOCK` 행을 실제 저장한
뒤 Spring 내부 이벤트를 발행하는 코드가 추가됐다.

```text
ALLOW
→ FSD 행 저장 안 함
→ 이벤트 없음

기존 중복 REVIEW/BLOCK
→ 저장 건너뜀
→ 이벤트 없음

신규 REVIEW/BLOCK
→ FsdEvent saveAndFlush
→ FsdDetectedNotification 내부 이벤트
```

`saveAndFlush()` 결과를 받는 이유는 DB가 생성한 `fsdEventId`를 Kafka 이벤트에 사용하기
위해서다.

### 11.2 commit 이후 처리

```text
FsdDetectedNotification
→ FsdAfterCommitEventListener
→ FsdReviewRequiredEvent
→ fsd-review-required topic
→ PaymentNotificationConsumer
→ 모든 관리자에게 FSD_REVIEW_REQUIRED 알림
```

target URL은 실제 관리자 화면인 `/admin/fsd`다. 알림 메시지와 API에는 IP, user agent,
evidence, 결제 원본 payload 같은 민감정보를 포함하지 않는다.

## 12. Kafka 발행 실패와 Metric

백테스트, 리포트, 위험, FSD EventPublisher는 두 종류의 실패를 모두 처리한다.

```text
1. KafkaTemplate.send() 호출 자체의 즉시 예외
2. send() 이후 비동기 callback에서 확인된 발행 실패
```

실패 시:

```text
ERROR 로그 기록
notification.event.publish.failure 증가
source와 topic tag 기록
원본 상태는 유지
```

예:

```text
notification.event.publish.failure
source=report
topic=report-completed
```

알림 공통 metric은 다음과 같다.

| Metric | 의미 |
| --- | --- |
| `notification.created` | 신규 알림 저장 |
| `notification.duplicate` | 동일 `(eventId, userId)` 중복 무시 |
| `notification.consumer.failure` | Kafka Consumer 처리가 재시도 후에도 실패 |
| `notification.event.publish.failure` | 원본 결과 Kafka 발행 실패 |

## 13. API 응답과 보안

알림 목록 Item은 다음 값을 반환한다.

```text
id
type
title
message
referenceType
referenceId
targetUrl
isRead
createdAt
readAt
```

모든 API는 인증이 필요하다. 목록과 unread count는 로그인 사용자 ID를 항상 조건에 포함한다.
개별 읽음도 `notificationId + principal.userId`로 조회한다.

알림에는 다음 정보를 넣지 않는다.

- 결제 key와 카드 정보
- gateway 원본 응답
- 비밀키
- request fingerprint
- IP와 user agent
- FSD evidence와 임계값 상세

## 14. 프론트엔드 구현 요약

프론트는 React Query를 단일 서버 상태 저장소로 사용한다.

### API

`src/lib/api/notifications.ts`에서 목록, unread count, 개별 읽음, 전체 읽음 API를 제공한다.

### Header Bell

`NotificationBell`은 다음 기능을 제공한다.

- unread count가 0보다 클 때 badge 표시
- 30초마다 unread count polling
- 창 focus와 네트워크 reconnect 시 갱신
- Bell을 열면 최근 5개 조회
- 개별 읽음과 모두 읽음
- 전체 알림 페이지 이동

알림 클릭 시 읽음 요청을 시작하고 `targetUrl`로 이동한다. 읽음 API 실패가 화면 이동을 막지는
않는다.

### 전체 알림 화면

`/notifications`에서 다음 기능을 제공한다.

- 읽음 여부 필터
- 알림 타입 필터
- 번호 기반 pagination
- loading, error, empty 상태

### 백테스트 화면 재사용

백테스트 알림은 다음 URL을 사용한다.

```text
/backtests?jobId={jobId}
```

`BacktestWorkbench`가 query parameter를 읽고 기존 상태·결과·차트·테이블 UI로 해당 작업을
불러온다. 별도 상세 route는 만들지 않았다.

## 15. 검증

구현 후 다음을 검증했다.

- 백엔드 전체 테스트
- Notification Service와 Kafka Consumer 테스트
- Flyway V19 Testcontainers 마이그레이션
- 프론트 typecheck, test, lint, production build
- Docker Compose 전체 스택 기동
- PostgreSQL V19 적용과 실제 제약 확인
- 신규 Kafka topic과 `finrisk-notification-v1` group 확인
- 실제 `payment outbox → Kafka → Notification Consumer → 인증 API` E2E
- Kafka 동일 이벤트 재전달 시 중복 알림 방지
- 개별 읽음 재호출 시 최초 `readAt` 보존
- 전체 읽음과 무관한 결제 이벤트 무시
- 테스트 데이터 정리와 컨테이너 ERROR 로그 확인

## 16. 제외 범위와 향후 개선

Day 16에서는 다음을 구현하지 않았다.

- 이메일, SMS, 모바일 push
- WebSocket, SSE
- 알림 수신 설정
- 마케팅 알림
- 알림 삭제와 보관 기간 Scheduler
- 위험 자동 계산 Scheduler
- Watchlist 전체 사용자 위험 알림
- `/backtests/{jobId}` 신규 상세 화면
- 백테스트·리포트·위험·FSD 공용 Outbox

가장 중요한 향후 개선은 공용 Outbox다. 현재 단일 애플리케이션 규모에서 복잡도를 낮추려면
Kafka 결과 topic을 제거하고 `AFTER_COMMIT` Listener에서 알림을 직접 저장하는 방법도 있다.
반대로 Kafka 기반 확장성과 확실한 전달을 유지하려면 원본 상태와 Outbox 이벤트를 같은
트랜잭션으로 저장하는 방식이 적합하다.
