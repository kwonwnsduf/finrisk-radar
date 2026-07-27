# Day 14 결제·구독·FSD 코드 흐름 가이드

이 문서는 Day 14 기능을 **백엔드 코드가 실제로 실행되는 순서**로 설명한다.
단순히 컨트롤러부터 읽는 방식이 아니라, 아래 순서로 이해할 수 있도록 구성했다.

1. 사용자가 결제 버튼을 누른 순간부터 어떤 코드가 실행되는가
2. 주문 생성·Toss 카드 인증·백엔드 최종 승인이 어떻게 연결되는가
3. FSD가 어느 시점에 실행되고 언제 결제를 차단하는가
4. 승인·취소·복구 결과로 DB와 사용자 권한이 어떻게 바뀌는가
5. 그다음에 각 데이터와 클래스가 어떤 책임을 갖는가

운영 설정과 실행 방법은
[Day 14 결제·구독·FSD 문서](day14-payments-subscriptions-fsd.md)를 함께 참고한다.

---

## 0. 가장 먼저 보는 전체 흐름

세부 클래스와 필드부터 보기 전에 Day 14가 어떻게 만들어졌고, 실행할 때 어떤 순서로
움직이는지 먼저 이해해야 한다.

### 0.1 전체 작동 방식

가장 먼저 전체 실행 순서를 코드 기준으로 보면 다음과 같다.

```text
[1. 사용자가 결제 준비 버튼 클릭]
PaymentCheckout
  → createPaymentOrder()
  → POST /api/payments/orders

[2. 백엔드 주문 생성]
PaymentController.create()
  → PaymentRequestMetadata.from()
  → PaymentService.createOrder()
      → PaymentProduct.require()
      → PaymentAttempt 멱등 요청 확인
      → PaymentPersistenceService.startAttempt()
      → FsdSignalStore.recordOrder()
      → 주문 생성 횟수 FSD 확인
      → PaymentPersistenceService.createOrder()
          → PaymentOrder.premium()
          → READY 주문 저장
      → attempt SUCCEEDED
  ← orderId, orderName, amount, customerKey, successUrl, failUrl

[3. 프런트가 Toss 결제위젯 표시]
PaymentCheckout
  → loadTossPayments(NEXT_PUBLIC_TOSS_CLIENT_KEY)
  → toss.widgets(customerKey)
  → widgets.setAmount(서버가 반환한 5,900원)
  → renderPaymentMethods()
  → renderAgreement()

[4. 사용자가 결제하기 클릭]
PaymentCheckout.requestPayment()
  → widgets.requestPayment()
  → Toss 결제창
  → 사용자가 카드 선택
  → 카드사 앱·비밀번호·생체 인증

[5. Toss 결제 인증 성공]
Toss
  → paymentKey 생성
  → successUrl로 redirect
  → URL에 paymentKey, orderId, amount 전달

주의:
이 시점은 카드 인증이 성공한 상태다.
우리 서비스의 최종 결제 완료 상태인 PAID는 아직 아니다.

[6. 프런트 성공 페이지가 최종 승인 요청]
PaymentResult
  → 주문별 idempotencyKey를 sessionStorage에서 조회 또는 생성
  → confirmPayment()
  → POST /api/payments/confirm

[7. 백엔드 승인 전 검증과 FSD]
PaymentController.confirm()
  → PaymentService.confirm()
      → 주문 소유권 확인
      → 요청 amount와 서버 주문 amount 비교
      → 기존 PaymentTransaction 확인
      → 멱등키와 request fingerprint 확인
      → 최근 결제 실패 횟수 수집
      → 동일 IP 사용 계정 수 수집
      → FsdEngine.evaluatePreConfirm()
          → 소유권 불일치 검사
          → 금액 변조 검사
          → paymentKey 중복 검사
          → 비정상 재승인 검사
          → 실패 burst 검사
          → 동일 IP 다계정 검사
          → 요청 metadata 검사
          → BLOCK > REVIEW > ALLOW 결정

[8. PRE_CONFIRM 결과 처리]
BLOCK
  → FsdEvent 저장
  → attempt BLOCKED
  → Toss 승인 API를 호출하지 않고 종료

REVIEW 또는 ALLOW
  → 승인 흐름 계속
  → REVIEW이면 FsdEvent는 남지만 현재 결제는 계속 진행

[9. 동시 승인 방지와 상태 선점]
PaymentService.confirm()
  → PaymentLockService.acquire("confirm", orderId)
  → PaymentPersistenceService.claimConfirmation()
      → 주문 행 잠금
      → READY인지 확인
      → READY → CONFIRMING
      → 짧은 DB 트랜잭션 commit

[10. 백엔드가 Toss에 최종 승인 요청]
PaymentService.confirm()
  → TossPaymentGateway.confirmPayment()
  → POST Toss /v1/payments/confirm
      ├─ paymentKey
      ├─ 서버 orderId
      ├─ 서버 저장 amount 5,900원
      └─ Idempotency-Key

이 Toss 호출은 DB 트랜잭션 밖에서 실행한다.

[11. Toss와 카드사가 실제 결제를 최종 처리]
Toss 승인 성공
  → HTTP 200
  → Payment 객체 반환
      ├─ status = DONE
      ├─ paymentKey
      ├─ orderId
      ├─ totalAmount
      ├─ method
      └─ approvedAt

우리 서비스는 승인 API가 성공하고 상태가 DONE인 시점을
최종 결제 완료로 판단한다.

[12. 백엔드가 승인 결과 검증]
PaymentPersistenceService.finalizePayment()
  → status가 DONE인가?
  → Toss orderId와 서버 orderId가 같은가?
  → Toss totalAmount와 서버 amount가 같은가?

하나라도 다르면 PAID와 PREMIUM으로 처리하지 않는다.

[13. 결제·구독·이벤트 원자 저장]
PaymentPersistenceService.finalizePayment()
  → PaymentTransaction 저장
  → PaymentOrder: CONFIRMING → PAID
  → SubscriptionService.activate()
      → 결제별 SubscriptionEntitlement 30일 생성
      → 전체 Subscription 기간 계산
      → User.plan = PREMIUM
  → OutboxService.append(PaymentCompletedEvent)
  → OutboxService.append(SubscriptionActivatedEvent)
  → 한 DB 트랜잭션으로 commit

[14. 백엔드가 프런트에 결과 반환]
PaymentResultResponse
  ├─ orderStatus = PAID
  ├─ paymentMethod
  ├─ approvedAt
  ├─ entitlementStart/End
  ├─ subscriptionEnd
  ├─ currentPlan = PREMIUM
  └─ replayed

[15. 프런트가 최신 상태 표시]
PaymentResult
  → me query 갱신
  → subscription query 갱신
  → payments query 갱신
  → usage query 갱신
  → 결제 완료와 PREMIUM 종료일 표시

[16. 결제 후 비동기 FSD]
OutboxPublisher
  → OutboxEvent를 Kafka로 발행
  → PaymentEventConsumer
      → ConsumedEvent로 중복 소비 방지
      → PaymentFraudFactsService.facts(POST_PAYMENT)
      → FsdEngine.evaluatePost(POST_PAYMENT)
      → 반복 주문·계정 전환·공유 IP 패턴 탐지
```

따라서 결제는 크게 세 구간이다.

```text
우리 백엔드
  → 서버 가격의 READY 주문 생성

프런트 + Toss + 카드사
  → 결제창 표시와 사용자 카드 인증
  → paymentKey 발급

우리 백엔드 + Toss Core API
  → PRE_CONFIRM FSD
  → CONFIRMING 선점
  → 최종 승인
  → DONE 검증
  → PAID·구독·Outbox 저장
```

가장 중요한 원칙은 다음과 같다.

> Toss 결제창에서 카드 인증이 성공한 것만으로는 우리 DB의 결제가 완료되지 않는다.
> 프런트가 받은 `paymentKey`를 백엔드에 전달하고, 백엔드가 PRE_CONFIRM FSD와
> 서버 금액 검증을 통과한 뒤 Toss 최종 승인 API에서 `DONE`을 받아야 `PAID`와
> `PREMIUM`이 된다.

### 0.2 구현 흐름

이 기능은 아래 순서로 밑에서부터 조립되어 있다.

```text
[1. DB 구조]
V17 migration
  └─ 주문, 거래, 시도, 취소, 구독, entitlement, FSD, Outbox 테이블

[2. 상태와 엔티티]
PaymentOrder / PaymentTransaction / PaymentAttempt
Subscription / SubscriptionEntitlement
FsdEvent / OutboxEvent / ConsumedEvent
  └─ 무엇을 저장하며 상태가 어떻게 변하는지 구현

[3. 저장소]
각 Repository
  └─ 일반 조회, 소유자 조회, 행 잠금, 통계 조회 구현

[4. 구독 계산]
SubscriptionService
  └─ 승인 시 30일 추가
  └─ 취소 시 해당 결제의 미사용 기간만 제거
  └─ 뒤 entitlement 재배치
  └─ Subscription과 User.plan projection 갱신

[5. 결제 기반 기능]
PaymentGateway / TossPaymentGateway
PaymentLockService / PaymentRequestMetadata
  └─ Toss 통신, 동시 실행 방지, 요청 감사 정보 구현

[6. FSD]
FsdProperties / FsdSignalStore
PaymentFraudFactsService / FsdEngine
  └─ 설정 기반 임계값, 단기 신호, 사후 통계, 판정 구현

[7. 결제 트랜잭션]
PaymentPersistenceService
  └─ 상태 선점과 최종 저장을 짧은 DB 트랜잭션으로 구현

[8. 업무 조립]
PaymentService
  └─ 검증 → FSD → lock → 선점 → Toss → finalize 순서 연결

[9. HTTP API]
PaymentController / SubscriptionController / AdminFsdController
  └─ 프런트 요청을 서비스에 연결

[10. 비동기와 복구]
OutboxPublisher / PaymentEventConsumer
PaymentRecoveryScheduler
  └─ Kafka 발행, 사후 FSD, 불명확 결제 복구 구현

[11. 프런트]
API 모듈 → 결제위젯 → success/fail → 내역/구독/admin 화면
  └─ 사용자가 백엔드 기능을 실행하고 결과를 확인하는 UI 구현
```

즉, 구현의 방향은 다음과 같다.

```text
DB와 상태 정의
  → 기간 계산
  → 외부 결제 연결
  → 사기 탐지
  → 트랜잭션 조립
  → API 공개
  → 이벤트와 복구
  → 프런트 연결
```

### 0.3 위 전체 흐름 중 최종 승인 구간 확대

앞의 주문 생성·위젯·카드 인증 이후, `POST /api/payments/confirm` 요청이 들어온
시점부터를 확대하면 실제 백엔드 클래스는 다음 순서로 작동한다.

```text
PaymentController.confirm()
  │
  └─ PaymentService.confirm()
       ├─ PaymentOrderRepository
       │    └─ 주문, 소유자, 서버 가격 확인
       │
       ├─ PaymentTransactionRepository
       │    └─ 이미 완료된 거래인지 확인
       │
       ├─ PaymentAttemptRepository
       │    └─ 멱등키와 fingerprint 확인
       │
       ├─ FsdSignalStore
       │    └─ 최근 실패와 동일 IP 계정 수 수집
       │
       ├─ FsdEngine.evaluatePreConfirm()
       │    └─ ALLOW / REVIEW / BLOCK 판정
       │
       ├─ PaymentLockService.acquire()
       │    └─ 같은 주문의 동시 승인 방지
       │
       ├─ PaymentPersistenceService.claimConfirmation()
       │    └─ 행 잠금, READY → CONFIRMING
       │
       ├─ TossPaymentGateway.confirmPayment()
       │    └─ DB 트랜잭션 밖에서 Toss 승인
       │
       └─ PaymentPersistenceService.finalizePayment()
            ├─ PaymentTransaction 저장
            ├─ PaymentOrder를 PAID로 변경
            ├─ SubscriptionService.activate()
            │    ├─ SubscriptionEntitlement 생성
            │    ├─ Subscription projection 갱신
            │    └─ User.plan을 PREMIUM으로 변경
            └─ OutboxService.append()
                 └─ 결제 완료와 구독 활성화 event 저장
```

DB commit 이후에는 비동기 흐름이 이어진다.

```text
OutboxPublisher
  → PENDING OutboxEvent 조회
  → PaymentDomainEvent로 변환
  → Kafka 발행
  → PUBLISHED 처리

PaymentEventConsumer
  → ConsumedEvent 선점
  → PaymentFraudFactsService로 통계 계산
  → FsdEngine.evaluatePost(POST_PAYMENT)
  → 필요하면 FsdEvent 저장
```

### 0.4 취소 코드의 전체 호출 흐름

```text
PaymentController.cancel()
  │
  └─ PaymentService.cancel()
       ├─ 주문 소유권 확인
       ├─ cancelRequestId 재요청 확인
       ├─ PaymentTransaction에서 paymentKey 조회
       ├─ SubscriptionService.assertCancellable()
       │    └─ 해당 결제에 미사용 기간이 있는지 확인
       ├─ PaymentLockService.acquire()
       ├─ PaymentPersistenceService.claimCancellation()
       │    └─ PAID → CANCELING
       ├─ TossPaymentGateway.cancelPayment()
       │    └─ DB 트랜잭션 밖에서 전액 취소
       └─ PaymentPersistenceService.finalizeCancellation()
            ├─ SubscriptionService.cancelContribution()
            │    ├─ 해당 entitlement의 미사용 초 계산
            │    ├─ 사용한 기간은 이력으로 유지
            │    ├─ 미사용 기간만 CANCELED 처리
            │    ├─ 뒤 entitlement를 앞으로 이동
            │    ├─ Subscription 종료일 재계산
            │    └─ User.plan 재계산
            ├─ PaymentCancellation 저장
            ├─ PaymentOrder를 CANCELED로 변경
            └─ PaymentCanceledEvent 저장
```

취소에서 제거하는 값은 고정 30일이 아니다.

```text
unused = periodEnd - max(now, periodStart)
```

- 아직 시작하지 않은 결제: 30일 전체가 미사용
- 현재 사용 중인 결제: 지금 이후의 남은 기간만 미사용
- 이미 끝난 결제: 미사용 기간이 0이므로 취소 거부

### 0.5 장애가 발생했을 때의 전체 흐름

```text
Toss가 명확하게 거절
  → FAILED
  → PaymentFailedEvent

timeout 또는 잘못된 응답
  → Toss 결제 조회
      ├─ DONE 확인 → finalizePayment()
      ├─ 전액 CANCELED 확인 → finalizeCancellation()
      └─ 여전히 불명확 → RECOVERY_REQUIRED

오래 남은 CONFIRMING/CANCELING/RECOVERY_REQUIRED
  → PaymentRecoveryScheduler
  → PaymentService.reconcile()
  → Toss 실제 상태로 DB 복구
```

이제부터 나오는 엔티티, 서비스, 메서드 설명은 위 전체 흐름의 각 상자를
자세히 풀어 설명한 것이다.

---

## 1. Day 14에서 구현한 결과

Day 14의 핵심 결과는 다음과 같다.

- 서버가 `PREMIUM_MONTHLY` 주문 가격을 5,900원으로 확정한다.
- Toss가 승인한 결제를 서버가 다시 확인한 후에만 결제 완료로 저장한다.
- 결제 한 건마다 30일짜리 구독 기여분(entitlement)을 별도로 기록한다.
- 결제를 여러 번 하면 각 30일이 앞 결제 종료 시점 뒤에 이어 붙는다.
- 취소할 때는 전체 구독에서 무조건 30일을 빼지 않는다.
- 취소한 결제가 아직 제공하지 않은 기간만 제거하고 뒤의 결제 기간을 앞으로 당긴다.
- 승인·취소 요청이 중복되어도 Toss 호출과 기간 이동이 한 번만 일어나도록 방어한다.
- Toss 응답이 불명확하면 조회와 스케줄러를 이용해 복구한다.
- FSD가 결제 전·결제 후·취소 후 이상 패턴을 탐지한다.
- 결제 트랜잭션과 이벤트 생성을 한 DB 트랜잭션으로 묶고, Kafka 발행은 Outbox가 담당한다.

전체 구조를 한 줄로 줄이면 다음과 같다.

```text
HTTP 요청
  → Controller
  → PaymentService(업무 흐름 지휘)
  → FSD/Redis Lock
  → PaymentPersistenceService(짧은 DB 트랜잭션)
  → TossPaymentGateway(외부 API, DB 트랜잭션 밖)
  → PaymentPersistenceService(결과 확정)
  → SubscriptionService(권한 원장과 projection 갱신)
  → OutboxService
  → OutboxPublisher
  → Kafka
  → PaymentEventConsumer
  → 사후 FSD
```

---

## 2. 먼저 알아야 하는 세 가지 모델

### 2.1 `PaymentOrder`: 우리가 만든 주문

`PaymentOrder`는 FinRisk Radar 서버가 만든 주문이다.
Toss에 결제 요청을 보내기 전부터 존재한다.

주요 값은 다음과 같다.

| 필드 | 의미 |
|---|---|
| `id` | DB 내부에서 사용하는 숫자 PK |
| `orderId` | 프런트와 Toss에도 전달하는 공개 주문 번호 |
| `userId` | 주문 소유 사용자 |
| `productCode` | 구매 상품 코드. 현재는 `PREMIUM_MONTHLY` |
| `orderName` | 결제창에 표시되는 상품명 |
| `amount` | 서버가 정한 결제 금액 5,900원 |
| `currency` | `KRW` |
| `provider` | 결제 제공자 `TOSS` |
| `customerKey` | Toss가 같은 구매자를 구분할 때 사용하는 고객 식별값 |
| `status` | 주문 처리 상태 |
| `version` | JPA 낙관적 잠금용 버전 |

`PaymentOrder.premium()`이 상품 코드, 이름, 가격, 통화, provider를 서버 값으로
채운다. 따라서 프런트가 임의 가격을 보내더라도 서버 주문 가격은 바뀌지 않는다.

주문 상태는 다음과 같이 이동한다.

```text
READY
  └─ 승인 선점 → CONFIRMING
                    ├─ 승인 완료 → PAID
                    ├─ 명확한 실패 → FAILED
                    └─ 결과 불명확 → RECOVERY_REQUIRED

PAID
  └─ 취소 선점 → CANCELING
                    ├─ 취소 완료 → CANCELED
                    ├─ 명확한 실패 → FAILED
                    └─ 결과 불명확 → RECOVERY_REQUIRED
```

상태 변경은 `beginConfirmation()`, `paid()`, `beginCancellation()`,
`canceled()`, `failed()`, `recoveryRequired()`에 모여 있다.
예를 들어 `READY`가 아닌 주문은 일반 승인 흐름에서 `CONFIRMING`이 될 수 없다.

### 2.2 `SubscriptionEntitlement`: 결제 한 건의 30일 기여분

`SubscriptionEntitlement`는 **결제 한 건이 실제로 제공하는 구독 기간 원장**이다.
결제 주문 하나당 정확히 한 행이 생긴다.

| 필드 | 의미 |
|---|---|
| `paymentOrderId` | 이 기간을 만든 결제 주문 |
| `originalDurationSeconds` | 원래 제공하기로 한 기간. 30일 = 2,592,000초 |
| `periodStart` | 이 결제 기여분이 시작하는 실제 시각 |
| `periodEnd` | 이 결제 기여분이 끝나는 실제 시각 |
| `usedUntil` | 이 기여분에서 실제 사용된 마지막 지점 |
| `status` | `SCHEDULED`, `ACTIVE`, `CONSUMED`, `CANCELED` |
| `canceledAt` | 취소 처리 시각 |
| `removedUnusedSeconds` | 취소로 제거한 미사용 초 |

`originalDurationSeconds`와 `periodStart`는 서로 다른 값이다.

- `originalDurationSeconds`: 길이, 즉 “30일짜리인가?”
- `periodStart`: 시각, 즉 “그 30일이 언제 시작하는가?”
- `periodEnd`: `periodStart + 30일`

상태의 뜻은 다음과 같다.

- `SCHEDULED`: 앞 결제 기간이 남아 있어 아직 시작하지 않은 기여분
- `ACTIVE`: 현재 사용 중인 기여분
- `CONSUMED`: 기간을 전부 사용한 기여분
- `CANCELED`: 미사용 기간을 제거한 기여분

### 2.3 `Subscription`: 사용자의 현재 구독 요약

`Subscription`은 사용자의 모든 entitlement를 빠르게 조회하기 위한
**projection(현재 상태 요약)** 이다. 사용자당 한 행만 존재한다.

| 필드 | 의미 |
|---|---|
| `userId` | 구독 사용자 |
| `plan` | 현재 `FREE` 또는 `PREMIUM` |
| `status` | `ACTIVE`, `CANCELED`, `EXPIRED` |
| `currentPeriodStart` | 남아 있는 연속 구독의 시작 |
| `currentPeriodEnd` | 남아 있는 연속 구독의 최종 종료 |
| `activatedByPaymentOrderId` | 지금 사용 중인 entitlement를 만든 주문 |

둘의 차이는 다음 한 문장으로 기억하면 된다.

> `SubscriptionEntitlement`는 결제별 상세 원장이고, `Subscription`은 그 원장을
> 계산해서 만든 사용자 전체 구독 요약이다.

예를 들어 결제 세 건이 있으면 entitlement는 세 행이지만 subscription은 한 행이다.

---

## 3. DB 테이블이 맡는 역할

`V17__create_payments_subscriptions_fsd_outbox.sql`이 아래 테이블을 만든다.

| 테이블 | 역할 |
|---|---|
| `payment_orders` | 서버 주문과 현재 주문 상태 |
| `payment_transactions` | Toss에서 실제 승인된 거래 |
| `payment_attempts` | 주문 생성·승인·취소 시도와 멱등 결과 |
| `payment_cancellations` | Toss 전액 취소 결과 |
| `subscriptions` | 사용자 전체 구독 projection |
| `subscription_entitlements` | 결제별 구독 기여 기간 원장 |
| `fsd_events` | 탐지된 FSD 규칙과 관리자 검토 상태 |
| `outbox_events` | 아직 Kafka에 보내지 않았거나 보낸 이벤트 |
| `consumed_events` | consumer가 이미 처리한 Kafka 이벤트 |

중복 방지에서 중요한 제약은 다음과 같다.

- 공개 `order_id`는 중복될 수 없다.
- Toss `payment_key`는 중복될 수 없다.
- 주문 하나에는 승인 거래가 최대 한 건이다.
- 주문 하나에는 entitlement가 최대 한 건이다.
- `request_id`, `cancel_request_id`는 중복될 수 없다.
- 같은 사용자의 같은 작업 종류와 같은 멱등키는 중복될 수 없다.
- 같은 consumer가 같은 event ID를 두 번 처리할 수 없다.

Redis lock이 만료되거나 서버가 여러 대여도 DB UNIQUE 제약과 `@Version`,
행 잠금이 마지막 안전장치가 된다.

---

## 4. 클래스는 어떤 순서로 공부해야 하는가

컨트롤러부터 읽으면 서비스 내부의 타입과 상태가 낯설다.
아래는 **의존성이 적은 클래스부터 실제 실행을 조립하는 클래스 순서**다.

### 1단계: 값과 상태

1. `PaymentProduct`
2. `PaymentOrderStatus`
3. `PlanType`
4. `FsdDecision`, `FsdPhase`, `FsdSeverity`, `FsdStatus`

먼저 상품 가격, 주문 상태, 사용자 plan, FSD 용어를 익힌다.

### 2단계: DB 엔티티

5. `PaymentOrder`
6. `PaymentTransaction`
7. `PaymentAttempt`
8. `PaymentCancellation`
9. `Subscription`
10. `SubscriptionEntitlement`
11. `FsdEvent`
12. `OutboxEvent`
13. `ConsumedEvent`

여기까지 읽으면 “무엇을 저장하는지”를 알 수 있다.

### 3단계: Repository와 작은 조회 서비스

14. 각 엔티티의 `Repository`
15. `PaymentOrderLookupService`
16. `PaymentApiModels`
17. `SubscriptionResponse`

Repository의 메서드 이름을 보면서 어떤 조회와 행 잠금이 필요한지 확인한다.

### 4단계: 구독보다 먼저 필요한 Outbox 기초

18. `PaymentDomainEvent`
19. `OutboxService`

`SubscriptionService`는 만료 이벤트를 `OutboxService`에 기록하므로,
`SubscriptionService`보다 Outbox의 기본 역할을 먼저 아는 것이 자연스럽다.
단, 발행기와 consumer는 결제 전체 흐름을 익힌 뒤 읽어도 된다.

### 5단계: 구독 계산

20. `SubscriptionService`
21. `SubscriptionController`

특히 `activate()`, `cancelContribution()`, `project()`, `expireDue()` 순서로 읽는다.

### 6단계: 결제 기반 도구

22. `PaymentProperties`, `PaymentConfiguration`
23. `PaymentGateway`, `GatewayPayment`
24. `TossPaymentGateway`
25. `PaymentLockService`
26. `PaymentRequestMetadata`

외부 결제를 어떤 인터페이스로 감쌌는지, Redis lock과 요청 메타데이터를
어떻게 쓰는지 확인한다.

### 7단계: FSD

27. `FsdProperties`
28. `FsdSignalStore`
29. `PaymentFraudFactsService`
30. `FsdEngine`

설정 → 단기 신호 수집 → 사후 통계 계산 → 규칙 판정 순서다.

### 8단계: 결제 핵심

31. `PaymentPersistenceService`
32. `PaymentService`
33. `PaymentController`

`PaymentPersistenceService`가 DB 트랜잭션 단위를 보여 주고,
`PaymentService`가 그것들을 Toss 호출과 함께 조립한다.
마지막으로 `PaymentController`를 보면 HTTP 요청이 어느 서비스 메서드로 들어가는지
쉽게 연결된다.

### 9단계: 비동기와 복구

34. `OutboxPublisher`
35. `PaymentEventConsumer`
36. `PaymentKafkaErrorConfiguration`
37. `PaymentRecoveryScheduler`
38. `AdminFsdService`, `AdminFsdController`

이 순서대로 보면 정상 결제 이후의 비동기 처리와 장애 복구까지 완성된다.

---

## 5. payment 패키지의 클래스별 의미

### `PaymentProduct`

판매 상품의 서버 기준값을 보관한다.
현재 `PREMIUM_MONTHLY`의 코드, 주문명, 금액을 제공한다.
클라이언트가 보낸 금액을 상품 가격으로 믿지 않게 하는 기준점이다.

### `PaymentOrderStatus`

주문 상태 enum이다. 승인과 취소가 허용되는 출발 상태를 제한한다.

### `PaymentOrder`

주문 aggregate의 중심 엔티티다.
필드를 단순히 `setStatus()`로 바꾸지 않고 의미 있는 상태 변경 메서드를 통해 바꾼다.

### `PaymentTransaction`

승인 성공 후 Toss 거래를 저장한다.

- `paymentKey`: Toss가 발급한 실제 결제 식별자
- `providerStatus`: Toss의 거래 상태
- `method`: 카드, 간편결제 등 승인 수단
- `totalAmount`: Toss가 승인했다고 응답한 총액
- `suppliedAmount`: 공급가액
- `approvedAt`: Toss 승인 시각
- `receiptUrl`: 구매자가 볼 수 있는 영수증 주소
- `rawResponse`: Toss 원문 전체가 아니라 서버가 고른 안전한 필드만 담은 JSONB

`payment_orders`가 “결제하려는 주문”이라면 `payment_transactions`는
“Toss에서 실제 승인된 거래”다.

### `PaymentAttempt`

사용자의 한 번의 작업 시도를 감사하고 멱등성을 제공한다.

- `attemptType`: `ORDER_CREATE`, `CONFIRM`, `CANCEL`
- `requestId`: 각 HTTP 요청의 추적 ID
- `idempotencyKey`: 같은 업무 요청인지 구분하는 키
- `requestFingerprint`: 같은 키로 요청 내용이 바뀌었는지 비교하는 해시
- `result`: `STARTED`, `SUCCEEDED`, `FAILED`, `BLOCKED`
- `responsePayload`: 재요청 때 돌려줄 수 있는 결과 snapshot
- `clientIp`: 원본 IP가 아닌 해시
- `userAgent`: 요청을 보낸 브라우저/클라이언트 정보

같은 멱등키와 같은 fingerprint면 기존 결과를 재사용한다.
같은 멱등키인데 fingerprint가 다르면 충돌로 거부한다.

### `PaymentCancellation`

승인 거래를 Toss에서 전액 취소한 결과를 저장한다.
`cancelRequestId`가 같은 재요청을 구분하며 주문당 성공 취소는 한 건만 저장한다.

### `PaymentApiModels`

결제 요청·응답 record가 모여 있다.

- `CreateOrderRequest`: 구매할 `productCode`
- `ConfirmPaymentRequest`: `paymentKey`, `orderId`, `amount`, `idempotencyKey`
- `CancelPaymentRequest`: 취소 이유와 `cancelRequestId`
- `PaymentOrderResponse`: 결제위젯을 시작할 서버 주문 정보
- `PaymentResultResponse`: 승인 거래와 entitlement 및 전체 구독 결과
- `PaymentCancelResponse`: 제거된 미사용 기간과 취소 후 plan
- `PaymentHistoryItem`: 내 결제 내역 한 항목
- `PaymentPageResponse`: 공통 페이지 결과

응답의 `replayed=true`는 이번 요청에서 Toss 결제를 다시 실행한 것이 아니라,
이미 성공해 저장된 동일 작업 결과를 돌려줬다는 뜻이다.

### `PaymentRequestMetadata`

HTTP 요청에서 request ID, 사용자 IP, User-Agent를 추출한다.
현재 구현은 `request.getRemoteAddr()`로 확인한 IP를 SHA-256 해시로 바꿔
FSD와 감사에 사용한다. 프록시 뒤에서 운영할 때는 전달 헤더를 신뢰할 수 있도록
인프라와 애플리케이션의 프록시 설정을 별도로 맞춰야 한다.

### `PaymentProperties`

다음 운영값을 코드 밖 설정으로 받는다.

- 결제 활성화 여부
- 프런트 base URL
- Toss API base URL
- provider 연결·응답 timeout
- Redis lock TTL
- 복구 stale 기준
- Outbox batch와 최대 재시도

Toss secret key 자체는 `PaymentProperties`에 담지 않고 `TOSS_SECRET_KEY`
환경변수에서 직접 주입한다.

### `PaymentConfiguration`

설정에 따라 실제 `TossPaymentGateway` 또는 `DisabledPaymentGateway` bean을 만든다.
결제 기능이 비활성화되었거나 키가 없을 때 실수로 외부 결제를 실행하지 않게 한다.

### `PaymentGateway`

결제 제공자를 추상화한 인터페이스다.

```text
confirmPayment()       승인
cancelPayment()        전액 취소
getPayment()           paymentKey로 조회
getPaymentByOrderId()  orderId로 조회
```

`GatewayPayment`는 Toss 응답을 내부 공통 형태로 바꾼 값이다.
`paid()`는 상태가 `DONE`일 때만 참이고,
`fullyCanceled()`는 `CANCELED`이면서 잔액이 0일 때만 참이다.

### `TossPaymentGateway`

Spring `RestClient`로 Toss Core API를 호출하는 adapter다.

- secret key로 Basic 인증
- POST 요청에 내부 멱등키 전달
- 승인·취소·조회 응답을 `GatewayPayment`로 변환
- 저장 가능한 안전한 응답 필드만 선택
- 명확한 PG 거절과 timeout 같은 불명확 오류를 구분

### `PaymentLockService`

같은 주문을 여러 요청이 동시에 승인하거나 취소하지 못하게 Redis lock을 잡는다.

```text
key   = payment:{operation}:lock:{orderId}
value = 이 요청만 가진 무작위 UUID token
TTL   = 설정값
```

해제할 때 Lua가 Redis에 저장된 token과 현재 요청 token을 비교한다.
그래서 오래 걸린 첫 요청이, 만료 후 lock을 얻은 두 번째 요청의 lock을 지우지 못한다.

### `PaymentPersistenceService`

결제 흐름의 **짧은 DB 트랜잭션 경계**를 담당한다.

| 메서드 | 역할 |
|---|---|
| `createOrder()` | 사용자 확인, ADMIN 구매 차단, READY 주문 생성 |
| `startAttempt()` | 별도 트랜잭션으로 감사 시도 시작 |
| `completeAttempt()` | 성공·실패·차단 결과 저장 |
| `claimConfirmation()` | 행 잠금 후 `READY → CONFIRMING` |
| `finalizePayment()` | 거래·주문·구독·plan·Outbox 원자 저장 |
| `claimCancellation()` | 취소 가능 확인 후 `PAID → CANCELING` |
| `finalizeCancellation()` | 취소·기간 재배치·구독·Outbox 원자 저장 |
| `markFailed()` | 명확한 실패와 실패 이벤트 저장 |
| `markRecoveryRequired()` | 불명확한 상태를 복구 대상으로 표시 |

중요한 점은 이 클래스가 Toss를 직접 호출하지 않는다는 것이다.
외부 네트워크를 기다리는 동안 DB 트랜잭션과 행 잠금을 오래 유지하지 않는다.

### `PaymentService`

결제 use case 전체를 지휘하는 orchestration 서비스다.

- 입력값과 소유권 확인
- 멱등 요청 탐색
- attempt 기록
- FSD 실행
- Redis lock 획득
- DB 상태 선점
- Toss 호출
- 불명확 결과 즉시 조회
- 최종 DB 반영
- 응답 조립

비즈니스 순서를 담당하지만, 세부 DB 원자성은 `PaymentPersistenceService`,
기간 계산은 `SubscriptionService`, 외부 API는 `PaymentGateway`에 맡긴다.

### `PaymentController`

HTTP와 Java 서비스 사이의 얇은 입구다.

- JWT principal에서 `userId` 추출
- request body 검증
- `PaymentRequestMetadata` 생성
- `PaymentService` 호출
- 기존 `ApiResponse<T>`로 반환

컨트롤러에 결제 상태 변경이나 구독 기간 계산 로직은 없다.

### `PaymentRecoveryScheduler`

설정된 주기마다 오래 멈춘 `CONFIRMING`, `CANCELING`,
`RECOVERY_REQUIRED` 주문을 찾는다. 각 주문에 `PaymentService.reconcile()`을 호출해
Toss 실제 상태를 기준으로 로컬 DB를 맞춘다.

---

## 6. 주문 생성 흐름

요청:

```http
POST /api/payments/orders
Idempotency-Key: 선택 UUID

{"productCode":"PREMIUM_MONTHLY"}
```

실행 순서:

```text
PaymentController.create()
  → PaymentService.createOrder()
      1. PaymentProduct.require(): 지원 상품인지 확인
      2. 같은 ORDER_CREATE 멱등 요청이 있는지 확인
      3. PaymentPersistenceService.startAttempt()
      4. FsdSignalStore.recordOrder(): 최근 주문 생성 횟수 기록
      5. 한도 초과 시 FSD event와 BLOCKED attempt 저장
      6. PaymentPersistenceService.createOrder()
          - 사용자 존재 확인
          - ADMIN 구매 차단
          - 5,900원 READY 주문 생성
      7. attempt를 SUCCEEDED로 완료
      8. 결제위젯에 필요한 주문 정보를 응답
```

결과:

- `payment_orders`: `READY` 한 행 생성
- `payment_attempts`: `ORDER_CREATE/SUCCEEDED` 기록
- 응답: `orderId`, `amount`, `customerKey`, 성공·실패 URL 등
- 아직 `payment_transactions`와 entitlement는 생기지 않음

---

## 7. 결제 승인 흐름

프런트가 Toss 결제창을 완료하면 Toss가 success URL에
`paymentKey`, `orderId`, `amount`를 붙여 이동시킨다.
프런트가 이 값을 서버 confirm API로 보낸다.

```text
PaymentController.confirm()
  → PaymentService.confirm()
```

### 7.1 PG 호출 전

1. `orderId`로 서버 주문을 조회한다.
2. JWT 사용자와 주문 소유자가 같은지 확인한다.
3. 요청 `amount`와 서버 저장 `amount`가 같은지 확인한다.
4. 이미 거래가 있으면 같은 `paymentKey`인지 확인하고 저장 결과를 재생한다.
5. `orderId + paymentKey + amount`로 fingerprint를 만든다.
6. 같은 멱등키의 attempt를 검사한다.
7. 최근 실패 횟수와 동일 IP 계정 수를 수집한다.
8. `FsdEngine.evaluatePreConfirm()`을 실행한다.
9. 최종 decision이 `BLOCK`이면 Toss를 호출하지 않는다.
10. `PaymentLockService`로 주문별 승인 lock을 잡는다.
11. `claimConfirmation()`이 행 잠금 후 `READY → CONFIRMING`으로 선점한다.

여기까지의 짧은 DB 트랜잭션이 끝난 뒤 Toss를 호출한다.

### 7.2 PG 호출

```text
PaymentGateway.confirmPayment(
    paymentKey,
    서버 orderId,
    서버 amount,
    idempotencyKey
)
```

Toss 응답이 와도 무조건 성공으로 믿지 않는다.

- Toss 상태가 `DONE`인가
- 응답 `orderId`가 서버 주문과 같은가
- 응답 `totalAmount`가 서버 주문 가격과 같은가

세 조건을 `finalizePayment()`에서 다시 검증한다.

### 7.3 PG 호출 후

`PaymentPersistenceService.finalizePayment()` 한 트랜잭션에서 다음을 처리한다.

1. 주문 행 잠금
2. 기존 거래가 있으면 중복 finalize 없이 기존 결과 반환
3. Toss 응답 검증
4. `PaymentTransaction` 저장
5. 주문 `CONFIRMING → PAID`
6. `SubscriptionService.activate()`로 entitlement 생성
7. `subscriptions` projection과 `app_users.plan` 갱신
8. `PaymentCompletedEvent` Outbox 저장
9. `SubscriptionActivatedEvent` Outbox 저장

이 중 하나라도 DB에서 실패하면 이 트랜잭션은 전부 rollback된다.

정상 결과:

```text
payment_orders.status       = PAID
payment_transactions        = 1행
subscription_entitlements   = 결제 기여분 1행
subscriptions.status        = ACTIVE
app_users.plan              = PREMIUM
outbox_events               = 결제 완료 + 구독 활성화
```

---

## 8. 구독 활성화와 연장 알고리즘

`SubscriptionService.activate(order, approvedAt)`가 실행된다.

### 첫 결제

승인 시각이 `2026-07-01 10:00`이면:

```text
결제 A: 2026-07-01 10:00 ───────── 30일 ─────────> 2026-07-31 10:00
```

- A entitlement: `ACTIVE`
- subscription 종료: `2026-07-31 10:00`
- user plan: `PREMIUM`

### 기존 기간이 남은 상태에서 두 번째 결제

두 번째 결제 승인 시각이 7월 10일이어도 A가 7월 31일까지 남아 있으므로:

```text
결제 A: 07-01 ─────────> 07-31
결제 B:                    07-31 ─────────> 08-30
현재
```

- B의 `periodStart`는 승인 시각이 아니라 기존 마지막 `periodEnd`
- B entitlement는 시작 전까지 `SCHEDULED`
- subscription 종료는 B 종료일

`project()`는 취소되지 않은 entitlement를 기준으로 다음을 다시 계산한다.

- 가장 이른 `periodStart`
- 가장 늦은 `periodEnd`
- 현재 시각을 포함하는 `paymentOrderId`
- `Subscription.plan/status`
- `app_users.plan`

---

## 9. 결제 취소 흐름

요청:

```http
POST /api/payments/{orderId}/cancel

{
  "reason": "사용하지 않을 예정",
  "cancelRequestId": "UUID"
}
```

실행 순서:

```text
PaymentController.cancel()
  → PaymentService.cancel()
      1. 주문 소유권 확인
      2. 같은 cancelRequestId의 완료 결과가 있으면 replay
      3. 승인 거래와 paymentKey 조회
      4. SubscriptionService.assertCancellable()
      5. CANCEL attempt와 fingerprint 확인
      6. cancel lock 획득
      7. claimCancellation(): PAID → CANCELING
      8. DB 트랜잭션 밖에서 Toss 전액 취소
      9. finalizeCancellation()
          - entitlement 미사용분 제거
          - 뒤 entitlement 앞으로 이동
          - subscription/user plan 재계산
          - cancellation과 Outbox 저장
     10. 응답 반환
```

### 9.1 미사용 기간 계산

취소 대상 entitlement에 대해:

```text
effectiveStart = max(now, periodStart)
unused = max(periodEnd - effectiveStart, 0)
```

이 계산이 중요한 이유는 미래 entitlement와 현재 사용 중인 entitlement를
같은 규칙으로 처리할 수 있기 때문이다.

### 9.2 현재 사용 중인 결제를 일부 사용한 뒤 취소

```text
결제 A: 07-01 ───── 현재 07-11 ─────────> 07-31
         사용한 10일          미사용 20일
```

취소 결과:

- 사용한 10일 이력은 유지
- `usedUntil = 07-11`
- `removedUnusedSeconds = 남은 20일`
- entitlement 상태는 `CANCELED`
- Toss에서는 결제 A를 전액 환불
- 현재·미래 권한에서는 A의 미사용 20일만 제거

### 9.3 미래 entitlement 취소

```text
결제 A: 07-01 ─────────> 07-31
결제 B:                    07-31 ─────────> 08-30
결제 C:                                       08-30 ─────────> 09-29
```

B를 7월 10일에 취소하면 B는 아직 하나도 사용하지 않았으므로 B의 30일 전체가
미사용이다. C를 30일 앞으로 당긴다.

```text
결제 A: 07-01 ─────────> 07-31
결제 B:                    취소
결제 C:                    07-31 ─────────> 08-30
```

구독이 A에서 C로 끊기지 않고 이어지며 최종 종료일만 30일 짧아진다.

### 9.4 이미 전부 사용한 결제

`periodEnd <= now`이면 `remaining()`은 0이다.
서버는 Toss를 호출하기 전에 `PAYMENT_CANCEL_NOT_ELIGIBLE`로 거부한다.

### 9.5 같은 취소를 다시 요청

`cancelRequestId`와 기존 cancellation을 확인해 저장된 결과를 반환한다.
`cancelContribution()`을 다시 실행하지 않으므로 뒤 entitlement가 두 번 이동하지 않는다.
응답의 `replayed`가 `true`가 된다.

---

## 10. `SubscriptionService` 메서드 흐름

### `activate()`

1. 사용자 subscription을 행 잠금으로 조회하거나 새로 생성
2. 모든 entitlement를 행 잠금으로 조회
3. 같은 주문 entitlement가 있으면 중복 생성하지 않음
4. 남은 entitlement 중 가장 늦은 종료일을 찾음
5. 새 30일 기여분 생성
6. `project()` 호출

### `cancelContribution()`

1. subscription과 entitlement 목록을 행 잠금
2. 취소 주문의 entitlement 탐색
3. `remaining(now)`로 미사용 초 계산
4. 미사용이 없으면 거부
5. 대상에 `cancelUnused(now)` 적용
6. 뒤의 취소되지 않은 entitlement를 미사용 초만큼 앞으로 이동
7. `project()` 호출

### `project()`

원장이 바뀔 때마다 전체 요약을 다시 만든다.

```text
entitlement 상태 refresh
  → 취소되지 않은 기여분만 선택
  → 전체 start/end 계산
  → 현재 active 주문 계산
  → Subscription 갱신
  → User.plan 갱신
```

`subscriptions`와 `app_users.plan`을 함께 갱신하기 때문에
API가 보는 구독 상태와 권한 검사에 사용하는 plan이 일치한다.

### `getCurrent()`

사용자 plan, 구독 상태, 전체 기간, 남은 일수, 활성 주문,
결제별 entitlement 목록을 `SubscriptionResponse`로 반환한다.

### `expireDue()`

주기적으로 종료 시간이 지난 ACTIVE 구독을 찾는다.

- subscription: `EXPIRED`
- user plan: `FREE`
- entitlement: 현재 시각 기준 상태 refresh
- Outbox: `SubscriptionExpiredEvent`

---

## 11. FSD 클래스와 실행 흐름

FSD는 Fraud/Suspicious Detection, 즉 이상 결제 패턴 탐지 영역이다.

### 기본 enum

- `FsdPhase`
  - `PRE_CONFIRM`: Toss 승인 호출 전
  - `POST_PAYMENT`: 결제가 저장되고 이벤트가 소비된 후
  - `POST_CANCEL`: 취소가 저장되고 이벤트가 소비된 후
- `FsdDecision`
  - `ALLOW`: 통과
  - `REVIEW`: 결제는 진행하지만 관리자 확인 필요
  - `BLOCK`: 차단
- `FsdSeverity`: 위험 심각도
- `FsdStatus`
  - `OPEN`: 새 탐지
  - `REVIEWING`: 관리자 검토 중
  - `RESOLVED`: 처리 완료
  - `FALSE_POSITIVE`: 오탐으로 판단

`ruleCode`는 어떤 규칙에 걸렸는지 나타내는 고정 식별자다.
예: `AMOUNT_TAMPERING`, `PAYMENT_FAILURE_BURST`,
`REPEATED_IMMEDIATE_CANCEL`.

### `FsdProperties`

`app.payment.fsd` 설정을 타입으로 읽고 시작 시 검증한다.

- window와 Redis TTL은 양수
- review count < block count
- ratio는 0~1
- 최소 표본 수는 양수
- priority 중복 금지
- 보안 규칙은 fail-closed
- 통계 규칙은 fail-open

따라서 규칙 클래스에 횟수·시간·비율 임계값을 직접 넣지 않고 YAML과 환경변수로
조정할 수 있다. 잘못된 값이면 애플리케이션이 시작 단계에서 실패한다.

### `FsdSignalStore`

Redis에 시간 구간별 단기 신호를 기록한다.

- 최근 결제 실패 횟수
- 동일 IP를 사용한 계정 수
- 짧은 시간의 주문 생성 횟수

Redis 장애 시 `PaymentService`는 `payment_attempts`와 `payment_orders`의
DB 집계를 fallback으로 사용한다.

### `PaymentFraudFactsService`

결제 후와 취소 후 필요한 통계 fact를 DB에서 만든다.

- 최근 주문 수
- 결제 성공 비율
- 같은 IP의 계정 수
- 실패 후 다른 계정 성공 패턴
- 즉시 취소 횟수
- 전체 결제 대비 취소 비율

### `FsdEngine`

fact/context를 각 규칙과 비교하고 탐지 결과를 `FsdEvent`로 저장한다.
여러 규칙이 동시에 걸리면 최종 decision 우선순위는:

```text
BLOCK > REVIEW > ALLOW
```

현재 결제를 실제로 중단하는 것은 `PRE_CONFIRM`의 `BLOCK`이다.
사후 단계의 `REVIEW`는 이미 완료된 PG 거래를 되돌리는 것이 아니라
관리자 검토 대상을 만든다.

### 단계별 규칙

`PRE_CONFIRM`:

- 주문 소유권 불일치
- 금액 변조
- 다른 주문에서 사용한 paymentKey
- 비정상 재승인
- 짧은 시간 결제 실패 반복
- 동일 IP 다계정
- 주문 생성 과다
- request ID/User-Agent 누락

`POST_PAYMENT`:

- 반복 주문과 낮은 결제 성공 비율
- 실패 후 같은 IP에서 다른 계정 결제 성공
- 교차 계정 IP 공유

`POST_CANCEL`:

- 반복 즉시 취소
- 높은 취소 비율
- 동일 IP 다계정 취소

---

## 12. Outbox와 Kafka 흐름

### 왜 결제 완료 후 바로 Kafka를 보내지 않는가

DB commit은 성공했는데 Kafka 전송이 실패하면 결제는 완료됐지만 이벤트가 사라진다.
반대로 Kafka 전송은 성공했는데 DB가 rollback되면 존재하지 않는 결제 이벤트가 나간다.

그래서 결제 데이터와 `OutboxEvent`를 같은 DB 트랜잭션으로 저장한다.

```text
결제 finalize 트랜잭션
  ├─ PaymentOrder/Transaction 저장
  ├─ Subscription/Entitlement 저장
  └─ OutboxEvent(PENDING) 저장

commit 이후
  └─ OutboxPublisher가 Kafka 전송
```

### `OutboxEvent`

- `aggregateType`: 이벤트 주인의 종류. 현재 `PAYMENT_ORDER`
- `aggregateId`: 어느 주문에서 발생했는지
- `eventType`: 무슨 사건인지
- `payload`: 사건의 상세 데이터
- `occurredAt`: 업무 사건이 DB에서 발생한 시각
- `publishedAt`: Kafka 전송에 성공한 시각
- `status`: `PENDING`, `PUBLISHED`, `FAILED`
- `attemptCount`: 발행 시도 횟수
- `lastError`: 마지막 발행 실패 내용

`occurredAt`과 `publishedAt`이 다른 이유는 사건 발생과 전송이 비동기이기 때문이다.

### `OutboxService`

업무 트랜잭션 안에서 새 Outbox event를 추가한다.
고유 event key를 사용해 같은 사건의 중복 생성을 막는다.

### `OutboxPublisher`

주기적으로 `PENDING` 이벤트를 batch 크기만큼 읽어 Kafka에 보낸다.

- 성공: `PUBLISHED`, `publishedAt` 기록
- 실패: `attemptCount`와 `lastError` 기록
- 최대 시도 이상 실패: `FAILED`

### `PaymentDomainEvent`

Kafka로 실제 전달되는 직렬화 형태다.

```text
eventId + eventType + occurredAt + payload + eventVersion
```

즉, `OutboxEvent`는 DB 발행 작업이고 `PaymentDomainEvent`는 Kafka 메시지다.

### `ConsumedEvent`

consumer가 이미 처리한 `eventId`를 기록한다.
Kafka는 같은 메시지를 다시 전달할 수 있으므로
`(consumerName, eventId)` UNIQUE로 중복 처리를 막는다.

### `PaymentEventConsumer`

1. `ConsumedEventRepository.claim()`으로 event ID를 먼저 선점
2. 이미 처리한 이벤트면 즉시 종료
3. 결제 완료 이벤트면 `POST_PAYMENT` fact와 FSD 실행
4. 취소 이벤트면 `POST_CANCEL` fact와 FSD 실행

---

## 13. 오류와 복구 흐름

### 명확한 PG 실패

예: Toss가 유효하지 않은 결제라고 명확히 거절한다.

```text
PaymentProviderException(ambiguous=false)
  → 주문 FAILED
  → attempt FAILED
  → PaymentFailedEvent Outbox
  → 클라이언트에 provider 오류
```

### 결과가 불명확한 오류

예: timeout이 발생해 서버는 응답을 못 받았지만 Toss에서는 승인이 끝났을 수 있다.

```text
PaymentProviderException(ambiguous=true)
  → paymentKey로 Toss 즉시 조회
      ├─ DONE/CANCELED 확인 → 정상 finalize
      └─ 여전히 불명확 → RECOVERY_REQUIRED
```

불명확한 상황을 바로 `FAILED`로 만들면 실제로 돈은 결제됐는데 로컬 권한은 없는
문제가 생길 수 있으므로 복구 대상으로 남긴다.

### 스케줄러 복구

`PaymentRecoveryScheduler`가 오래된 중간 상태 주문을 찾는다.
`reconcile()`은 order ID로 Toss를 조회한 뒤:

- Toss가 `DONE`이면 `finalizePayment()`
- Toss가 전액 `CANCELED`이면 `finalizeCancellation()`
- 이미 일치하면 `ALREADY_CONSISTENT`

관리자도 `/api/admin/payments/{orderId}/reconcile`로 같은 작업을 실행할 수 있다.

---

## 14. 중복·동시성 방어가 여러 겹인 이유

한 가지 장치만으로는 모든 장애를 막을 수 없다.

| 방어 | 막는 문제 |
|---|---|
| 프런트 sessionStorage 멱등키 | success 화면 재렌더·새로고침 |
| `PaymentAttempt` 멱등키+fingerprint | 같은 업무 재요청과 내용 변경 |
| Toss `Idempotency-Key` | provider 쪽 POST 중복 |
| Redis lock | 여러 서버의 동시 실행 |
| 주문 상태 전이 | 잘못된 출발 상태의 실행 |
| DB 행 잠금 | 같은 주문 finalize 경쟁 |
| `@Version` | 동시에 수정된 엔티티 충돌 |
| UNIQUE 제약 | 중복 거래·entitlement의 최종 차단 |
| `ConsumedEvent` | Kafka 중복 소비 |

`replayed=true`는 이런 방어가 작동해 기존 성공 결과를 안전하게 반환했다는 뜻이지,
결제를 한 번 더 했다는 뜻이 아니다.

---

## 15. API별 최종 결과

| API | 핵심 실행 | 성공 결과 |
|---|---|---|
| `POST /api/payments/orders` | 서버 가격 주문 생성 | READY 주문 |
| `POST /api/payments/confirm` | FSD, lock, Toss 승인, finalize | PAID + 거래 + 30일 기여분 |
| `POST /api/payments/{orderId}/cancel` | 미사용 확인, Toss 취소, 기간 재배치 | CANCELED + 미사용 권한 제거 |
| `GET /api/payments/me` | 사용자 주문과 거래·기여분 조합 | 결제 내역 페이지 |
| `GET /api/payments/orders/{orderId}` | 소유자 주문 조회 | 단일 주문/복구 상태 |
| `GET /api/subscriptions/me` | projection과 entitlement 조회 | 현재 plan과 결제별 기간 |
| `GET /api/admin/fsd-events` | 필터·검색·페이지 조회 | 관리자 탐지 목록 |
| `PATCH /api/admin/fsd-events/{id}` | 검토 상태와 메모 변경 | 갱신된 탐지 |
| `POST /api/admin/payments/{orderId}/reconcile` | Toss 실상태 조회 | 복구 또는 일치 확인 |

---

## 16. 프런트엔드 구현은 어떻게 나뉘는가

프런트는 백엔드보다 가볍게 아래 흐름만 먼저 보면 된다.

### API 모듈

- `frontend/src/lib/api/payments.ts`
  - 주문 생성, 승인, 취소, 내역, 주문 상태
- `frontend/src/lib/api/subscriptions.ts`
  - 현재 구독과 entitlement 조회
- `frontend/src/lib/api/fsd.ts`
  - 관리자 FSD 목록·상세·검토

### 사용자 페이지

- `/pricing`: 상품 가격, 30일, 자동 갱신 없음, PREMIUM 혜택
- `/payment`: 서버 주문 생성 후 Toss V2 위젯 표시
- `/payment/success`: URL 파라미터로 confirm 자동 호출
- `/payment/fail`: 안전한 오류와 서버 주문 상태 확인
- `/payments`: 결제 내역, 영수증, 취소 가능 여부
- `/settings/subscription`: 현재 구독과 결제별 기여 기간

### 관리자 페이지

- `/admin/fsd`: 탐지 목록, 필터, 상세, 상태와 관리자 메모
- `AdminGuard`: 관리자만 페이지 접근

---

## 17. 프런트와 백엔드의 상호작용

### 결제 시작부터 승인까지

```text
사용자 /payment
  → 프런트 POST /api/payments/orders
  ← 서버 orderId, amount, customerKey, successUrl
  → 프런트가 Toss 결제위젯에 서버 값을 전달
  → 사용자가 결제수단 선택 및 승인
  → Toss가 /payment/success로 redirect
  → 프런트 POST /api/payments/confirm
  → 백엔드가 Toss Core API에 승인 요청
  ← 백엔드가 PAID, entitlement, currentPlan 응답
  → 프런트가 결제·구독·사용량·내 정보 query 갱신
```

브라우저에 들어가는 것은 **결제위젯용 client key**다.
Toss Core API 인증에 쓰는 secret key는 백엔드에만 존재한다.

### 취소

```text
/payments에서 취소 확인
  → 프런트가 새 cancelRequestId 생성
  → POST /api/payments/{orderId}/cancel
  → 백엔드가 미사용 entitlement 확인
  → Toss 전액 취소
  → 백엔드가 해당 결제의 미사용 기간만 제거
  ← removedUnusedSeconds, subscriptionEnd, currentPlan
  → 프런트가 결제/구독/사용량/내 정보 갱신
```

### 구독 화면

```text
GET /api/subscriptions/me
  → 전체 currentPeriodStart/currentPeriodEnd
  → 현재 활성 결제 주문
  → 결제별 periodStart/periodEnd/status/remainingSeconds
```

그래서 사용자는 “전체 구독이 언제 끝나는지”뿐 아니라
“각 결제가 어느 기간에 기여하는지”도 확인할 수 있다.

---

## 18. 실제 디버깅할 때 따라갈 순서

### 승인이 이상할 때

1. `PaymentController.confirm()`
2. `PaymentService.confirm()`
3. 해당 `PaymentAttempt`
4. `FsdEngine.evaluatePreConfirm()`
5. `PaymentLockService.acquire()`
6. `PaymentPersistenceService.claimConfirmation()`
7. `TossPaymentGateway.confirmPayment()`
8. `PaymentPersistenceService.finalizePayment()`
9. `SubscriptionService.activate()`
10. `OutboxEvent`

### 취소 기간이 이상할 때

1. 취소 대상 `PaymentOrder`
2. 대상 `SubscriptionEntitlement.periodStart/periodEnd`
3. `SubscriptionEntitlement.remaining()`
4. `SubscriptionService.cancelContribution()`
5. `cancelUnused()`
6. 뒤 entitlement의 `shiftEarlier()`
7. `project()`
8. `Subscription.currentPeriodEnd`
9. `User.plan`

### 이벤트가 처리되지 않을 때

1. `outbox_events.status`
2. `attempt_count`, `last_error`
3. `OutboxPublisher`
4. Kafka topic
5. `PaymentEventConsumer`
6. `consumed_events`
7. `fsd_events`

### 결제는 됐는데 권한이 없을 때

1. Toss 실제 상태
2. `payment_orders.status`
3. `payment_transactions`
4. `subscription_entitlements`
5. `subscriptions`
6. `app_users.plan`
7. `RECOVERY_REQUIRED` 여부
8. scheduler 또는 관리자 reconcile

---

## 19. 핵심만 다시 요약

- `PaymentService`는 전체 순서를 지휘한다.
- `PaymentPersistenceService`는 짧고 원자적인 DB 변경을 담당한다.
- `TossPaymentGateway`는 외부 결제사와 통신한다.
- `SubscriptionService`는 결제별 기간 원장과 사용자 전체 구독을 계산한다.
- `FsdEngine`은 승인 전 차단과 결제·취소 후 검토 대상을 만든다.
- `OutboxService/Publisher`는 DB 사건을 잃지 않고 Kafka로 전달한다.
- `PaymentRecoveryScheduler`는 중간에 끊긴 결제를 Toss 실제 상태와 맞춘다.
- 취소는 전체 종료일에서 고정 30일을 빼는 방식이 아니라,
  **그 결제가 아직 제공하지 않은 기간만 제거하는 방식**이다.
