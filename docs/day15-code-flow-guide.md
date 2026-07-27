# Day 15 관리자 운영 콘솔 코드 흐름 가이드

이 문서는 FinRisk Radar Day 15 관리자 운영 콘솔을 처음 공부하는 사람이 실제 코드의 실행 순서를 따라갈 수 있도록 작성한 가이드다.

Day 15 관리자는 투자 분석가가 아니라 SaaS 서비스 운영자다. 따라서 전체 자산, 모든 위험 신호, 정상 완료된 작업을 열람하는 백오피스가 아니라 다음 항목에 집중한다.

- 사용자와 구독 현황
- 결제 성공·실패·취소·복구 필요 상태
- FSD 이상 결제 검토
- 문서 기반 신용사건 후보 검토
- 실패하거나 실제 복구 기준을 초과한 비동기 작업

새 운영 로그 테이블은 만들지 않는다. 기존 엔티티를 조회하고, 관리자 전용 DTO로 안전하게 조립한다.

---

## 0. 가장 먼저 보는 전체 구조

### 0.1 Backend 공통 흐름

```text
관리자 HTTP 요청
  ↓
SecurityConfig
  └─ /api/admin/** 요청에 ROLE_ADMIN 요구
  ↓
Admin Controller
  └─ 요청 파라미터와 인증 Principal 처리
  ↓
Admin Query Service 또는 Review Service
  ├─ 조회: @Transactional(readOnly = true)
  └─ 변경: @Transactional + pessimistic lock
  ↓
Repository
  ├─ count/sum 집계
  ├─ Specification 동적 필터
  ├─ Pageable 목록 조회
  └─ 페이지 ID 기반 bulk 조회
  ↓
관리자 DTO
  └─ 민감정보를 제외한 응답
  ↓
ApiResponse
```

### 0.2 Frontend 공통 흐름

```text
/admin route
  ↓
AdminShell
  ├─ AdminGuard
  ├─ AdminSidebar
  └─ AdminMobileNav
  ↓
페이지별 Client Component
  ↓
React Query
  ↓
src/lib/api/admin.ts 또는 도메인 API 모듈
  ↓
/api/admin/**
```

### 0.3 기능별 전체 흐름

```text
대시보드
  └─ 각 Repository의 COUNT/SUM 결과를 하나의 응답으로 조립

사용자·구독
  └─ User 또는 Subscription 페이지 조회
      + 관련 Subscription/User/PaymentOrder bulk 조회

결제
  └─ PaymentOrder 페이지 조회
      + User/PaymentAttempt/FsdEvent bulk 조회

FSD
  ├─ 목록·상세 조회
  └─ 관리자 검토 시 FsdEvent 행 잠금 후 상태 변경

신용사건 후보
  ├─ Candidate + Asset + Match + Document 조회
  └─ 승인 시 CreditEvent 생성 후 위험 재계산 요청

시스템 문제
  └─ FAILED 작업
      + 기존 기준상 stale인 AI 리포트만 조회
```

---

## 1. 추천 코드 학습 순서

다음 순서로 보면 복잡도가 자연스럽게 올라간다.

1. `SecurityConfig`
2. `AdminPage`
3. `AdminDashboardResponse`
4. `AdminDashboardController`
5. `AdminDashboardQueryService`
6. `AdminUserSubscriptionModels`
7. `AdminUserSubscriptionController`
8. `AdminUserSubscriptionQueryService`
9. `AdminPaymentModels`
10. `AdminPaymentController`
11. `AdminPaymentQueryService`
12. `AdminOperationalIssueModels`
13. `AdminOperationalIssueController`
14. `AdminOperationalIssueQueryService`
15. `AdminFsdController`
16. `AdminFsdService`
17. `FsdEvent`, `FsdRepository`
18. `CreditEventCandidateAdminController`
19. `CreditEventCandidateQueryService`
20. `CreditEventReviewService`
21. `DocumentRiskRecalculationCoordinator`
22. Frontend `admin.ts`
23. `AdminShell`과 각 관리자 화면

조회 코드는 다음 패턴을 중심으로 읽는다.

```text
Controller
  → Specification
  → Page<Entity>
  → 현재 페이지의 연관 ID 수집
  → bulk 조회
  → Map으로 인덱싱
  → DTO 조립
  → AdminPage
```

검토 코드는 다음 패턴을 중심으로 읽는다.

```text
Controller
  → 인증 Principal
  → @Transactional
  → findByIdForUpdate()
  → 상태 재검증
  → 엔티티 상태 변경
  → commit
  → 후속 이벤트
```

---

## 2. 실제 상태와 모델

Day 15는 다음과 같이 이미 존재하는 상태만 사용한다.

```text
Role
├─ ROLE_USER
└─ ROLE_ADMIN

PlanType
├─ FREE
├─ PREMIUM
└─ ADMIN

SubscriptionStatus
├─ ACTIVE
├─ CANCELED
└─ EXPIRED

PaymentOrderStatus
├─ READY
├─ CONFIRMING
├─ PAID
├─ CANCELING
├─ CANCELED
├─ FAILED
└─ RECOVERY_REQUIRED

FsdStatus
├─ OPEN
├─ REVIEWING
├─ RESOLVED
└─ FALSE_POSITIVE

CreditEventCandidateStatus
├─ PENDING_REVIEW
├─ APPROVED
└─ REJECTED

BacktestStatus / ReportStatus / CollectionStatus / DocumentCollectionStatus
├─ REQUESTED
├─ RUNNING
├─ COMPLETED
└─ FAILED
```

`needsReview`, `autoRenew`, 사용자 정지 상태, 임의의 stale 상태는 추가하지 않았다.

---

## 3. 관리자 권한 흐름

모든 관리자 API는 다음 경로 아래에 있다.

```text
/api/admin/**
```

`SecurityConfig`는 이 경로를 `ROLE_ADMIN`으로 제한한다.

```text
토큰 없음
→ 401 Unauthorized

ROLE_USER
→ 403 Forbidden

ROLE_ADMIN
→ Controller 실행
```

검토 기능에서 reviewer ID는 요청 본문으로 받지 않는다.

```java
@AuthenticationPrincipal CustomUserPrincipal principal
```

```java
principal.userId()
```

를 사용한다. 따라서 클라이언트가 다른 사용자의 ID를 reviewer로 위조할 수 없다.

---

## 4. `AdminPage`: 관리자 목록의 공통 페이지 응답

```java
public record AdminPage<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
```

각 필드의 의미는 다음과 같다.

| 필드 | 의미 |
|---|---|
| `items` | 현재 페이지의 실제 DTO 목록 |
| `page` | 현재 페이지 번호. 0부터 시작 |
| `size` | 페이지당 최대 행 수 |
| `totalElements` | 필터에 맞는 전체 행 수 |
| `totalPages` | 전체 페이지 수 |

`AdminPage.from()`은 Spring Data의 `Page<S>`가 가진 페이지 메타데이터를 유지하면서 엔티티 목록을 관리자 DTO 목록으로 교체한다.

```java
public static <S, T> AdminPage<T> from(
    Page<S> page,
    List<T> items
) {
  return new AdminPage<>(
      items,
      page.getNumber(),
      page.getSize(),
      page.getTotalElements(),
      page.getTotalPages()
  );
}
```

예:

```text
Page<User>
  + List<AdminUserItem>
        ↓
AdminPage<AdminUserItem>
```

모든 관리자 목록은 최대 `size=100`으로 제한한다.

---

## 5. 관리자 대시보드

### 5.1 관련 클래스

```text
AdminDashboardController
AdminDashboardQueryService
AdminDashboardResponse
```

### 5.2 API 흐름

```text
GET /api/admin/dashboard
  ↓
AdminDashboardController.get()
  ↓
AdminDashboardQueryService.get()
  ↓
여러 Repository의 count/sum query
  ↓
AdminDashboardResponse
```

### 5.3 `AdminDashboardResponse`

대시보드 응답은 다음 네 묶음으로 구성된다.

```text
Users
Payments
Jobs
Reviews
```

#### Users

```text
전체 사용자
FREE 사용자
PREMIUM 사용자
현재 활성 구독
최근 24시간 가입자
최근 7일 가입자
최근 7일 생성된 Subscription 레코드
```

활성 구독의 정확한 조건:

```text
status = ACTIVE
AND currentPeriodEnd > now
```

#### Payments

```text
최근 24시간 승인 건수·금액
최근 7일 승인 건수·금액
최근 24시간 실패 Attempt
최근 7일 실패 Attempt
최근 7일 완료 취소 건수·금액
RECOVERY_REQUIRED 주문
OPEN + REVIEWING FSD
```

승인 금액은 `PaymentTransaction.approvedAt`, `totalAmount`를 기준으로 집계한다. 금액은 통화별로 분리하고 환산하지 않는다.

#### Jobs

```text
현재 REQUESTED/RUNNING 백테스트
최근 24시간 FAILED 백테스트
현재 REQUESTED/RUNNING AI 리포트
최근 24시간 FAILED AI 리포트
stale AI 리포트
최근 24시간 FAILED 시세 수집
최근 24시간 FAILED 문서 수집
```

AI 리포트 stale 기준은 `ReportRecoveryPolicy`를 사용한다.

```text
REQUESTED 1분 초과
RUNNING 5분 초과
```

#### Reviews

```text
OPEN FSD
REVIEWING FSD
PENDING_REVIEW 후보
PENDING_REVIEW 후보가 있는 distinct 자산
최근 24시간 생성된 PENDING_REVIEW 후보
```

### 5.4 왜 `findAll()`로 세지 않는가

잘못된 방식:

```java
users.findAll().stream()
    .filter(...)
    .count();
```

현재 방식:

```java
users.count();
users.countByPlan(PlanType.FREE);
users.countByCreatedAtAfter(day);
```

DB가 `COUNT(*)` 또는 `SUM(...)` 결과만 반환하므로 전체 엔티티를 JVM 메모리에 올리지 않는다.

---

## 6. 사용자·구독 조회

### 6.1 클래스 의미

```text
AdminUserSubscriptionModels
├─ AdminUserItem
└─ AdminSubscriptionItem

AdminUserSubscriptionController
└─ HTTP 요청과 필터 파라미터

AdminUserSubscriptionQueryService
└─ 조회, bulk 로딩, DTO 조립
```

### 6.2 `AdminUserItem`

사용자 한 명을 기준으로 만든 행이다.

```text
User
├─ userId
├─ email
├─ name
├─ role
├─ plan
└─ joinedAt

현재 Subscription 요약
├─ activeSubscription
└─ subscriptionEndAt

최신 PaymentOrder 요약
├─ latestPaymentOrderId
├─ latestPaymentStatus
└─ latestPaymentAt
```

현재 `latestPaymentAt`에는 승인 시각이 아니라 최신 `PaymentOrder.createdAt`, 즉 최신 결제 주문 생성 시각이 들어간다.

### 6.3 `AdminSubscriptionItem`

구독 레코드 하나를 기준으로 만든 행이다.

```text
Subscription
├─ subscriptionId
├─ plan
├─ status
├─ currentPeriodStart
└─ currentPeriodEnd

User 요약
├─ userId
├─ email
└─ name

활성화 주문
└─ publicOrderId
```

`AdminUserItem`의 최신 주문과 `AdminSubscriptionItem`의 활성화 주문은 의미가 다르다.

```text
latestPaymentOrderId
→ 사용자의 가장 최근 주문

publicOrderId
→ 해당 구독 projection을 활성화한 주문
```

### 6.4 사용자 목록 흐름

```text
GET /api/admin/users
  ↓
AdminUserSubscriptionController.users()
  ↓
AdminUserSubscriptionQueryService.users()
  ↓
Specification<User>
  ├─ email/name 검색
  ├─ plan
  ├─ role
  ├─ 가입일 범위
  └─ 활성 구독 존재 여부
  ↓
Page<User>
  ↓
현재 페이지 userId 수집
  ├─ Subscription bulk 조회
  └─ 최신 PaymentOrder bulk 조회
  ↓
AdminUserItem
  ↓
AdminPage<AdminUserItem>
```

활성 구독 필터는 상관 subquery를 사용한다.

```text
Subscription.userId = User.id
AND Subscription.status = ACTIVE
AND currentPeriodEnd > now
```

```text
activeSubscription=true
→ EXISTS(subquery)

activeSubscription=false
→ NOT EXISTS(subquery)
```

### 6.5 사용자 목록 bulk 조회

페이지 조회가 끝나면 현재 페이지에 있는 사용자 ID만 모은다.

```java
Set<Long> userIds =
    result.stream()
        .map(User::getId)
        .collect(Collectors.toSet());
```

그 ID를 이용해 관련 데이터를 한 번에 조회한다.

```java
subscriptions.findByUserIdIn(userIds);
orders.findLatestByUserIds(userIds);
```

결과는 Map으로 바꾼다.

```text
userId → Subscription
userId → 최신 PaymentOrder
```

이후 각 User를 DTO로 바꿀 때 DB를 다시 호출하지 않고 Map에서 찾는다.

### 6.6 구독 목록 흐름

```text
GET /api/admin/subscriptions
  ↓
AdminUserSubscriptionController.subscriptions()
  ↓
AdminUserSubscriptionQueryService.subscriptions()
  ↓
Specification<Subscription>
  ├─ plan
  ├─ status
  ├─ userId
  └─ 7일 이내 만료 예정
  ↓
Page<Subscription>
  ↓
User bulk 조회
  ↓
activatedByPaymentOrderId 기반 PaymentOrder bulk 조회
  ↓
AdminSubscriptionItem
```

`expiring=true`의 조건:

```text
status = ACTIVE
AND currentPeriodEnd > now
AND currentPeriodEnd <= now + 7일
```

---

## 7. 결제 운영 조회

### 7.1 클래스 의미

```text
AdminPaymentModels
├─ AdminPaymentItem
├─ AdminPaymentAttempt
├─ AdminPaymentCancellation
└─ AdminPaymentDetail

AdminPaymentController
└─ 목록과 상세 HTTP API

AdminPaymentQueryService
└─ 결제 검색과 안전한 상세 조립
```

### 7.2 결제 엔티티 관계

```text
PaymentOrder
├─ 주문과 현재 상태
├─ PaymentAttempt 여러 건
├─ PaymentTransaction 최대 한 건
├─ PaymentCancellation 최대 한 건
└─ FsdEvent 여러 건
```

각 엔티티의 의미:

| 엔티티 | 의미 |
|---|---|
| `PaymentOrder` | 서비스가 만든 결제 주문과 현재 상태 |
| `PaymentAttempt` | 주문 생성·승인·취소·복구 시도 |
| `PaymentTransaction` | 실제 승인 완료된 거래 |
| `PaymentCancellation` | 취소 요청과 처리 결과 |
| `FsdEvent` | 결제 이상 징후 탐지 결과 |

### 7.3 결제 목록 흐름

```text
GET /api/admin/payments
  ↓
AdminPaymentController.list()
  ↓
AdminPaymentQueryService.list()
  ↓
Specification<PaymentOrder>
  ├─ public orderId
  ├─ userId
  ├─ email subquery
  ├─ PaymentOrderStatus
  ├─ 생성일 범위
  ├─ FsdStatus subquery
  └─ recoveryRequired
  ↓
Page<PaymentOrder>
  ↓
페이지 orderId/userId 수집
  ├─ User bulk 조회
  ├─ PaymentAttempt bulk 조회
  └─ FsdEvent bulk 조회
  ↓
최신 실패 Attempt와 최신 FSD 선택
  ↓
AdminPaymentItem
```

`recoveryRequired=true`는 별도 가상 상태가 아니다.

```java
status = PaymentOrderStatus.RECOVERY_REQUIRED
```

조건으로 변환한다.

### 7.4 최신 실패 Attempt

Attempt를 `createdAt DESC`로 조회한 뒤 주문별로 첫 번째 `FAILED`를 선택한다.

```text
15:00 RECOVERY / SUCCEEDED
14:55 CONFIRM / FAILED  ← 최신 실패
14:50 CONFIRM / FAILED
```

목록에는 최신 실패의 `errorCode`만 표시하고, 전체 시도 이력은 상세에서 제공한다.

### 7.5 결제 상세 흐름

```text
GET /api/admin/payments/{publicOrderId}
  ↓
PaymentOrder 조회
  ↓
해당 주문의 PaymentAttempt 전체 조회
  ↓
PaymentCancellation 조회
  ↓
attemptType=RECOVERY인 최신 Attempt 선택
  ↓
AdminPaymentDetail
```

상세 응답에서 제외하는 민감정보:

```text
paymentKey
customerKey
requestFingerprint
request/response payload
provider raw response
IP
User-Agent
idempotencyKey
카드 원문
```

### 7.6 결제 reconciliation

```text
POST /api/admin/payments/{orderId}/reconcile
```

기존 `PaymentService.reconcile()`을 사용한다.

```text
AdminPaymentQueryService
→ 결제 문제를 조회

PaymentService.reconcile()
→ 실제 PG 상태와 내부 상태를 대조
```

조회 Service가 결제 상태를 직접 변경하지 않는다.

---

## 8. bulk 조회를 읽는 방법

Day 15 조회 코드가 어려워 보이는 가장 큰 이유는 N+1을 피하기 위해 Stream, Map, 그룹화를 한 번에 사용하기 때문이다.

### 8.1 bulk 조회의 목적

좋지 않은 방식:

```text
FSD 20개 조회
각 FSD마다 PaymentOrder 조회 20번
각 FSD마다 User 조회 20번
각 FSD마다 Attempt 조회 20번
```

bulk 방식:

```text
FSD 페이지 조회
필요한 orderId를 모아 PaymentOrder 일괄 조회
필요한 userId를 모아 User 일괄 조회
필요한 orderId로 Attempt 일괄 조회
Map에서 각 행의 연관 데이터 찾기
```

### 8.2 ID를 모아 일괄 조회하는 코드

```java
List<Long> orderIds =
    values.stream()
        .map(FsdEvent::getPaymentOrderId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
```

단계:

```text
FsdEvent 목록
  ↓ paymentOrderId만 추출
[10, 20, 10, null]
  ↓ null 제거
[10, 20, 10]
  ↓ 중복 제거
[10, 20]
```

일괄 조회:

```java
orders.findByIdIn(orderIds);
```

SQL 개념:

```sql
SELECT *
FROM payment_orders
WHERE id IN (10, 20);
```

### 8.3 `Function.identity()`와 `v -> v`

```java
.collect(
    Collectors.toMap(
        PaymentOrder::getId,
        Function.identity()
    )
);
```

또는:

```java
.collect(
    Collectors.toMap(
        PaymentOrder::getId,
        v -> v
    )
);
```

둘은 같은 의미다.

```text
PaymentOrder.id → PaymentOrder 객체 자신
```

결과:

```text
10 → PaymentOrder #10
20 → PaymentOrder #20
```

### 8.4 `keySet()`

Map:

```text
10 → PaymentOrder #10
20 → PaymentOrder #20
```

```java
orderById.keySet()
```

결과:

```text
[10, 20]
```

Map의 value가 아니라 key인 주문 ID만 가져온다.

### 8.5 `findByPaymentOrderIdInOrderByCreatedAtDesc`

이름을 나누면:

```text
find
By PaymentOrderId In
OrderBy CreatedAt Desc
```

의미:

```text
전달한 paymentOrderId 목록에 속하는 Attempt를
createdAt 최신순으로 조회
```

SQL 개념:

```sql
SELECT *
FROM payment_attempts
WHERE payment_order_id IN (...)
ORDER BY created_at DESC;
```

### 8.6 `computeIfAbsent()`

```java
attemptsByOrder
    .computeIfAbsent(
        attempt.getPaymentOrderId(),
        ignored -> new ArrayList<>()
    )
    .add(adminAttempt);
```

의미:

```text
해당 주문 ID에 List가 있으면 기존 List 반환
없으면 새 ArrayList를 Map에 저장하고 반환
반환된 List에 adminAttempt 추가
```

일반 코드로 풀면:

```java
Long orderId = attempt.getPaymentOrderId();
List<AdminPaymentAttempt> orderAttempts =
    attemptsByOrder.get(orderId);

if (orderAttempts == null) {
  orderAttempts = new ArrayList<>();
  attemptsByOrder.put(orderId, orderAttempts);
}

orderAttempts.add(adminAttempt);
```

`ignored`는 Java 키워드가 아니다. 람다로 전달받은 key를 사용하지 않는다는 의미의 변수 이름이다.

### 8.7 bulk 코드의 공통 형태

```text
1. 페이지 엔티티에서 연관 ID 추출
2. null 제거
3. 필요하면 중복 제거
4. Repository로 일괄 조회
5. ID → Entity Map 생성
6. 필요하면 ID → List<DTO>로 그룹화
7. 원래 페이지 엔티티를 DTO로 변환
```

---

## 9. FSD 이상 결제 검토

### 9.1 클래스 의미

```text
AdminFsdController
├─ FSD 목록
├─ FSD 상세
├─ 상태·메모 변경
└─ 기존 결제 reconcile API

AdminFsdService
├─ Specification 필터
├─ User/PaymentOrder/Attempt bulk 조립
└─ 상태 변경 트랜잭션

FsdEvent
└─ 실제 상태 전이 규칙

FsdRepository
└─ 조회, 집계, findByIdForUpdate()
```

### 9.2 FSD 목록 흐름

```text
GET /api/admin/fsd-events
  ↓
AdminFsdController.events()
  ↓
AdminFsdService.list()
  ↓
Specification<FsdEvent>
  ├─ status
  ├─ severity
  ├─ decision
  ├─ ruleCode
  ├─ detectedAt 범위
  └─ 사용자 ID/주문 검색
  ↓
Page<FsdEvent>
  ↓
PaymentOrder와 User bulk 조회
  ↓
FsdEventResponse
```

목록에서는 Attempt를 포함하지 않는다.

```java
responses(result.getContent(), false);
```

### 9.3 FSD 상세 흐름

```text
GET /api/admin/fsd-events/{id}
  ↓
FsdEvent 조회
  ↓
PaymentOrder/User 조회
  ↓
PaymentAttempt 일괄 조회
  ↓
상세 FsdEventResponse
```

상세에서는 Attempt를 포함한다.

```java
responses(List.of(event), true);
```

`includeAttempts`는 목록과 상세에서 조회 무게를 조절하는 boolean 스위치다.

### 9.4 `responses()`의 실제 역할

```text
List<FsdEvent>
  + PaymentOrder
  + User
  + 필요하면 PaymentAttempt
        ↓
List<FsdEventResponse>
```

이름을 더 풀어 쓰면 다음과 같은 의미다.

```text
buildFsdResponses
enrichFsdEvents
toFsdEventResponses
```

### 9.5 FSD 검토 흐름

```text
PATCH /api/admin/fsd-events/{id}
  ↓
Controller가 principal.userId() 전달
  ↓
@Transactional
  ↓
findByIdForUpdate(id)
  ↓
FsdEvent.review(next, note, adminId)
  ↓
JPA dirty checking
  ↓
COMMIT
  ↓
잠금 해제
```

허용 전이:

```text
OPEN → REVIEWING
OPEN → RESOLVED
OPEN → FALSE_POSITIVE
REVIEWING → RESOLVED
REVIEWING → FALSE_POSITIVE
```

같은 상태 재요청, terminal 상태 변경, `REVIEWING → OPEN`은 `409 Conflict`다.

### 9.6 pessimistic lock 시점

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select event from FsdEvent event where event.id = :id")
Optional<FsdEvent> findByIdForUpdate(Long id);
```

DB 개념:

```sql
SELECT *
FROM fsd_events
WHERE id = :id
FOR UPDATE;
```

잠금 생명주기:

```text
트랜잭션 시작
  ↓
findByIdForUpdate()에서 행 잠금 획득
  ↓
상태 변경과 응답 조립 중 잠금 유지
  ↓
성공: COMMIT하며 잠금 해제
실패: ROLLBACK하며 잠금 해제
```

두 관리자가 같은 FSD를 동시에 처리하면 두 번째 요청은 첫 번째 트랜잭션 종료까지 대기한다. 이후 최신 상태를 다시 읽기 때문에 중복 검토를 막을 수 있다.

---

## 10. 문서 기반 신용사건 후보 검토

### 10.1 데이터 관계

```text
Document
  ↓ 문장 분석
DocumentRiskMatch
  ↓ 같은 사건으로 묶음
CreditEventCandidate
  ↓ 관리자 승인
CreditEvent
  ↓
RiskCalculationJob
```

`CreditEventCandidate`는 아직 확정된 신용사건이 아니다.

```text
PENDING_REVIEW
├─ 관리자 승인 → APPROVED + CreditEvent 생성
└─ 관리자 거절 → REJECTED
```

### 10.2 클래스 의미

```text
CreditEventCandidateAdminController
├─ 목록·상세 API
├─ 승인·거절 API
└─ 기존 재계산 API

CreditEventCandidateQueryService
├─ 목록 bulk 조회
└─ 상세 evidence/nearby 조립

CreditEventReviewService
├─ 후보 행 잠금
├─ 승인·거절 상태 검증
├─ CreditEvent 생성
└─ 승인 Notification 발행

DocumentRiskRecalculationCoordinator
├─ 승인 후 위험 재계산 요청
├─ DEFERRED 자동 재시도
└─ RiskCalculationJob 결과 동기화
```

### 10.3 후보 목록 흐름

```text
GET /api/admin/credit-event-candidates
  ↓
CreditEventCandidateAdminController.list()
  ↓
CreditEventCandidateQueryService.list()
  ↓
Specification<CreditEventCandidate>
  ├─ status
  ├─ severity
  ├─ assetId
  ├─ eventType
  └─ createdAt 범위
  ↓
Page<CreditEventCandidate>
  ↓
Asset bulk 조회
  ↓
representativeMatchId 기반 Match bulk 조회
  ↓
Match의 documentId 기반 Document bulk 조회
  ↓
CandidateSummaryResponse
```

기본 상태는 실제 enum인 `PENDING_REVIEW`다.

### 10.4 `representativeMatchId`

한 후보에 여러 `DocumentRiskMatch`가 연결될 수 있다.

```text
CreditEventCandidate
├─ Match A confidence 0.91
├─ Match B confidence 0.84
└─ Match C confidence 0.72
```

`representativeMatchId`는 목록에서 대표 문서 정보를 보여주기 위해 선택된 Match ID다.

```text
candidate.representativeMatchId
  ↓
DocumentRiskMatch
  ↓
Document title/source
```

Day 15에서 새 필드를 만든 것이 아니라 기존 필드의 getter를 추가하고 조회에 재사용했다.

### 10.5 후보 상세 흐름

```text
GET /api/admin/credit-event-candidates/{candidateId}
  ↓
Candidate 조회
  ↓
Asset 조회
  ↓
candidateId로 모든 DocumentRiskMatch 조회
  ↓
Match들의 Document bulk 조회
  ↓
matchResponses 생성
  ↓
같은 자산·사건 유형·±7일 후보 조회
  ↓
nearby 생성
  ↓
CandidateDetailResponse
```

#### `matchResponses`

현재 후보가 왜 생성됐는지 보여주는 실제 문서 근거다.

```text
Document 정보
├─ title
├─ sourceType
├─ sourceName
└─ sourceUrl

DocumentRiskMatch 정보
├─ sentenceText
├─ matchedText
├─ assertionType
├─ confidence
├─ extractedAmount
├─ extractedCurrency
└─ evidence
```

#### `nearby`

다음 조건의 다른 후보다.

```text
같은 assetId
AND 같은 eventType
AND eventDate가 현재 후보 기준 ±7일
AND 현재 candidateId 자신은 제외
```

`nearby`는 중복 확정 결과가 아니다. 관리자가 같은 사건일 가능성을 비교할 참고 목록이다.

### 10.6 후보 승인 흐름

```text
POST /api/admin/credit-event-candidates/{id}/approve
  ↓
Controller가 principal.userId() 전달
  ↓
CreditEventReviewService.approve()
  ↓
findByIdForUpdate(id)
  ↓
PENDING_REVIEW인지 재검증
  ↓
DocumentRiskMatch 조회
  ↓
RiskAdminService.createEvent()
  ↓
실제 CreditEvent 생성
  ↓
Candidate를 APPROVED로 변경
  ↓
CandidateApprovedNotification 발행
  ↓
트랜잭션 COMMIT
  ↓
DocumentRiskRecalculationCoordinator
```

이미 `APPROVED` 또는 `REJECTED`면 `409 Conflict`다.

### 10.7 후보 거절 흐름

```text
POST /api/admin/credit-event-candidates/{id}/reject
  ↓
후보 행 잠금
  ↓
PENDING_REVIEW 검증
  ↓
REJECTED
  ↓
reviewer/note/reviewedAt 기록
```

거절 시에는 `CreditEvent`를 만들지 않고 위험 재계산도 요청하지 않는다.

---

## 11. `DocumentRiskRecalculationCoordinator`

이 Coordinator는 위험 점수를 직접 계산하지 않는다.

```text
승인된 후보
  ↓
RiskCalculationRequestService에 재계산 Job 요청
  ↓
RiskCalculationJob 상태 추적
```

### 11.1 AFTER_COMMIT 이벤트

```java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT
)
public void approved(CandidateApprovedNotification event)
```

후보 승인 트랜잭션이 성공적으로 commit된 후에만 실행된다.

```text
승인 성공 + COMMIT
→ 위험 재계산 요청

승인 실패 + ROLLBACK
→ Listener 실행 안 함
```

승인되지 않은 사건으로 위험 계산을 시작하는 것을 막는다.

### 11.2 재계산 상태

```text
NOT_REQUESTED
REQUESTED
DEFERRED
COMPLETED
FAILED
```

흐름:

```text
NOT_REQUESTED
  ↓ 요청 성공
REQUESTED
  ├─ Risk Job 완료 → COMPLETED
  ├─ Risk Job 실패 → FAILED
  └─ 조회·요청 일시 실패 → DEFERRED
                              ↓ 재시도
                           REQUESTED
```

### 11.3 `reconcileRequested()`

결제 reconciliation과 관계없다.

```text
Candidate.recalculationStatus
             ↕
실제 RiskCalculationJob.status
```

를 동기화한다.

```text
Job ID 없음
→ DEFERRED 또는 최대 횟수 후 FAILED

Risk Job COMPLETED
→ Candidate recalculationStatus COMPLETED

Risk Job FAILED
→ Candidate recalculationStatus FAILED

Risk Job REQUESTED/RUNNING
→ 아직 진행 중이므로 변경하지 않음
```

Scheduler가 기본 60초마다 `DEFERRED` 후보를 재시도하고 `REQUESTED` 후보의 Job 결과를 확인한다.

현재 Spring Application Event 기반이므로 commit 직후 프로세스가 비정상 종료되는 작은 구간에서는 `APPROVED + NOT_REQUESTED`로 남을 가능성이 있다. 현재 자동 재시도는 `DEFERRED`, 결과 확인은 `REQUESTED`를 대상으로 한다.

---

## 12. 시스템 문제 조회

### 12.1 클래스 의미

```text
AdminOperationalIssueModels
├─ CollectionIssueKind
└─ AdminOperationalIssue

AdminOperationalIssueController
└─ 작업 유형별 API

AdminOperationalIssueQueryService
├─ 문제 작업 조회
├─ 공통 Raw 변환
├─ User/Asset bulk 조회
└─ 공통 운영 문제 DTO 조립
```

### 12.2 포함 기준

```text
백테스트
└─ FAILED만

AI 리포트
├─ FAILED
├─ REQUESTED 1분 초과
└─ RUNNING 5분 초과

시세 수집
└─ FAILED만

문서 수집
└─ FAILED만
```

백테스트와 수집에는 기존 stale 기준이 없으므로 임의로 만들지 않았다.

### 12.3 API

```text
GET /api/admin/operational-issues/backtests
GET /api/admin/operational-issues/reports
GET /api/admin/operational-issues/collections?kind=MARKET_DATA
GET /api/admin/operational-issues/collections?kind=DOCUMENT
```

### 12.4 `Raw`

각 작업 엔티티는 필드 이름과 구조가 다르다.

```text
BacktestJob
AiReport
CollectionLog
DocumentCollectionJob
```

이를 먼저 내부 중간 구조인 `Raw`로 통일한다.

```text
issueType
jobId
userId
assetId
status
requestedAt
startedAt
completedAt
updatedAt
failureCode
failureMessage
```

`Raw`는 외부 API 응답이 아니다. User와 Asset을 붙이기 전의 내부 중간 데이터다.

### 12.5 `enrich()`

```text
List<Raw>
  ↓ userId/assetId 추출
User와 Asset bulk 조회
  ↓
userId → User Map
assetId → Asset Map
  ↓
AdminOperationalIssue
```

최종 DTO에는 다음 정보가 추가된다.

```text
email
assetName
ticker
ageSeconds
```

### 12.6 `basis`와 `ageSeconds`

```java
LocalDateTime basis =
    completedAt != null
        ? completedAt
        : startedAt != null
            ? startedAt
            : requestedAt;
```

`basis`는 경과 시간 계산 기준이다. DB 필드가 아니라 지역변수다.

```text
FAILED 작업
→ completedAt 기준
→ 실패 후 경과 시간

stale RUNNING
→ startedAt 기준
→ 실행된 후 끝나지 않은 시간

stale REQUESTED
→ requestedAt 기준
→ 요청 후 시작되지 않은 시간
```

```java
ageSeconds =
    Math.max(
        0,
        Duration.between(basis, now).getSeconds()
    );
```

`completedAt`이 우선인 이유는 정상 완료 작업이 이 화면에 없고, FAILED 작업에서는 “실패가 발생한 후 얼마나 지났는가”가 운영상 유용하기 때문이다.

---

## 13. AI 리포트 stale와 복구 기준

### 13.1 `ReportRecoveryPolicy`

```java
REQUESTED_STALE_AFTER = Duration.ofMinutes(1);
RUNNING_STALE_AFTER = Duration.ofMinutes(5);
```

다음 세 곳이 같은 기준을 사용한다.

```text
ReportDispatchRecoveryScheduler
AdminDashboardQueryService
AdminOperationalIssueQueryService
```

따라서 운영 화면에 stale로 표시되는 기준과 실제 Scheduler가 복구 대상으로 선택하는 기준이 다르지 않다.

### 13.2 `ReportDispatchRecoveryScheduler`

기본 60초마다 실행된다.

```text
REQUESTED 1분 초과
→ 요청 이벤트 재발행
→ 재발행 실패 시 실패·사용량 보상

RUNNING 5분 초과
→ 요청 이벤트 재발행
→ 재발행 실패 시 FAILED 처리
```

Day 15에서 복구 동작은 바꾸지 않았다. 기존 하드코딩된 1분·5분을 공통 정책으로 분리했다.

---

## 14. Frontend 구조

### 14.1 최종 라우트

```text
/admin
/admin/users
/admin/payments
/admin/fsd
/admin/credit-event-candidates
/admin/operational-issues
```

### 14.2 공통 레이아웃

```text
admin/layout.tsx
  ↓
AdminShell
  ├─ AdminGuard
  ├─ AdminSidebar
  ├─ AdminMobileNav
  └─ page children
```

`AdminGuard`는 인증 초기화, 익명 사용자, 일반 사용자, 관리자를 구분한다.

```text
initializing
→ 권한 확인 중

anonymous
→ /login redirect

ROLE_USER
→ 접근 권한 없음

ROLE_ADMIN
→ 관리자 화면 렌더링
```

### 14.3 공통 컴포넌트

```text
AdminTable
→ 표와 좁은 화면 overflow

AdminPagination
→ 이전·다음 페이지

AdminStatusBadge
→ 상태별 배지

AdminQueryState
→ loading/error/retry
```

모든 화면에 범용 Filter나 Dialog를 강제로 적용하지 않는다. 실제 구조가 같은 UI만 공통화한다.

### 14.4 API 모듈

`src/lib/api/admin.ts`는 다음 역할을 한다.

```text
Backend 응답 TypeScript 타입
React Query key
필터에서 빈 값 제거
관리자 API 호출
```

`clean()`:

```java
search=""
plan=undefined
page=0
```

에서 빈 필터를 제거하고 실제 의미가 있는 파라미터만 보낸다.

```text
page=0은 유지
activeSubscription=false도 유지
빈 문자열과 null/undefined만 제거
```

### 14.5 mutation 후 cache invalidate

FSD 검토 성공:

```text
FSD 목록·상세 cache invalidate
관리자 dashboard invalidate
```

Candidate 승인·거절 성공:

```text
Candidate 목록·상세 cache invalidate
관리자 dashboard invalidate
```

결제 reconciliation 성공:

```text
Payment 목록·상세 cache invalidate
관리자 dashboard invalidate
```

검토 대기 숫자와 실제 목록이 서로 어긋나지 않도록 다시 조회한다.

---

## 15. 최종 Backend API

```text
GET   /api/admin/dashboard

GET   /api/admin/users
GET   /api/admin/subscriptions

GET   /api/admin/payments
GET   /api/admin/payments/{orderId}
POST  /api/admin/payments/{orderId}/reconcile

GET   /api/admin/fsd-events
GET   /api/admin/fsd-events/{id}
PATCH /api/admin/fsd-events/{id}

GET   /api/admin/credit-event-candidates
GET   /api/admin/credit-event-candidates/{id}
POST  /api/admin/credit-event-candidates/{id}/approve
POST  /api/admin/credit-event-candidates/{id}/reject

GET   /api/admin/operational-issues/backtests
GET   /api/admin/operational-issues/reports
GET   /api/admin/operational-issues/collections
```

기존 호환 API:

```text
POST /api/admin/credit-event-candidates/{id}/recalculate
```

Day 15 UI에서는 수동 재처리를 제공하지 않는다.

---

## 16. DB와 인덱스

Day 15는 새로운 업무 테이블이나 상태 컬럼을 만들지 않는다.

`V18__add_admin_operations_indexes.sql`은 실제 관리자 조회 조건과 정렬을 위한 인덱스만 추가한다.

```text
payment_orders(status, created_at DESC)
payment_transactions(approved_at DESC)
payment_attempts(result, completed_at DESC)
payment_cancellations(status, completed_at DESC)
subscriptions(status, current_period_end)
credit_event_candidates(status, created_at DESC)
backtest_jobs(status, completed_at DESC)
collection_logs(status, completed_at DESC)
document_collection_jobs(status, completed_at DESC)
```

기존 인덱스는 재사용한다.

```text
AiReport status/requestedAt
FSD status/severity/decision/detectedAt
후보 status/confidence/eventDate
결제 recovery status/updatedAt
```

---

## 17. 민감정보 경계

관리자라도 다음 정보를 응답 DTO에 넣지 않는다.

```text
비밀번호
providerId
JWT
Refresh Token
paymentKey
customerKey
requestFingerprint
request/response payload
providerResponse
rawResponse
IP
User-Agent
카드 원문
```

엔티티에 getter가 존재하더라도 관리자 DTO가 사용하지 않으면 JSON 응답에 포함되지 않는다. 엔티티를 Controller에서 직접 반환하지 않는 이유다.

---

## 18. 구현 변경의 핵심

### 새로 만든 것이 아닌 것

```text
PENDING_REVIEW 상태
후보 승인·거절 API
후보 승인 시 CreditEvent 생성
CandidateApprovedNotification
DocumentRiskRecalculationCoordinator
FSD 상태
결제 reconcile
AI 리포트 1분/5분 recovery 기준
```

이들은 기존 구조를 재사용했다.

### Day 15에서 보강한 것

```text
관리자 대시보드 count/sum 집계
사용자·구독 조회
결제 운영 조회와 안전한 상세
시스템 문제 조회
목록 Pageable 및 최대 size=100
페이지 단위 bulk 조회
FSD 중복 검토 방지 lock
Candidate 중복 승인·거절 방지 lock
상태 오류의 404/409 변환
후보 상세 evidence와 nearby
관리자 Frontend 전체 라우트
React Query cache invalidate
운영 조회용 인덱스
```

---

## 19. 디버깅할 때 따라가는 순서

### 대시보드 숫자가 이상할 때

```text
AdminDashboardController
→ AdminDashboardQueryService
→ 해당 Repository count/sum 메서드
→ 기준 시각 day/week/now
→ 실제 enum과 날짜 필드
```

### 사용자 목록이 이상할 때

```text
AdminUserSubscriptionController 파라미터
→ User Specification
→ 활성 구독 subquery
→ Page<User>
→ subscriptionByUser
→ latestOrderByUser
→ AdminUserItem
```

### 결제 목록이 이상할 때

```text
AdminPaymentController 파라미터
→ PaymentOrder Specification
→ email/FSD subquery
→ Page<PaymentOrder>
→ latestFailure
→ eventByOrder
→ AdminPaymentItem
```

### FSD 검토가 충돌할 때

```text
AdminFsdController.review()
→ principal.userId()
→ AdminFsdService.review()
→ FsdRepository.findByIdForUpdate()
→ FsdEvent.review()
→ ErrorCode.FSD_INVALID_STATUS_TRANSITION
```

### 후보 승인 후 위험 계산이 안 될 때

```text
CreditEventCandidateAdminController.approve()
→ CreditEventReviewService.approve()
→ Candidate 상태와 approvedCreditEventId
→ CandidateApprovedNotification
→ DocumentRiskRecalculationCoordinator.approved()
→ recalculationStatus
→ recalculationJobId
→ RiskCalculationJob.status
→ reconcileRequested()
```

### 시스템 문제에 작업이 안 보일 때

```text
실제 status
→ 실제 시간 필드
→ 도메인별 포함 기준
→ ReportRecoveryPolicy
→ Specification
→ Raw
→ enrich()
→ AdminOperationalIssue
```

---

## 20. 테스트와 완료 기준

Backend:

```text
관리자 API 401/403/200
FSD 상태 전이
FSD 중복 검토 방지
Candidate 승인·거절 중복 방지
CandidateApprovedNotification
위험 재계산 연결
FAILED/stale 문제 조회
정상 작업 제외
민감정보 미노출
페이지 최대 크기
bulk 조회 구조
```

Frontend:

```text
관리자 권한 Guard
API 파라미터 정리
loading/error/empty/retry
페이지네이션
좁은 화면 overflow
검토 mutation 후 cache invalidate
typecheck
test
lint
production build
```

Docker:

```text
Flyway V18
Backend/Frontend health
익명 401
일반 사용자 403
관리자 200
대시보드·사용자·결제·시스템 문제 응답
```

---

## 21. 마지막 요약

Day 15의 핵심은 다음 세 문장으로 정리할 수 있다.

1. 대시보드는 전체 엔티티를 가져오지 않고 DB의 `COUNT`, `SUM`으로 운영 현황을 계산한다.
2. 목록은 현재 페이지의 연관 ID를 모아 bulk 조회하고, Map으로 인덱싱해 N+1을 방지한다.
3. FSD와 신용사건 후보 검토는 인증된 관리자 ID, 트랜잭션, pessimistic lock, 상태 재검증으로 중복 처리를 막는다.

전체 코드를 한 줄로 연결하면 다음과 같다.

```text
ROLE_ADMIN 요청
  ↓
Controller
  ↓
조회라면 Specification/Page/count/sum
변경이라면 Transaction/Lock/상태 검증
  ↓
페이지 연관 데이터 bulk 조회
  ↓
민감정보 없는 관리자 DTO
  ↓
React Query 화면
  ↓
mutation 후 관련 cache invalidate
```
