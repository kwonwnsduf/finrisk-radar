# Day 14 결제·구독·FSD

## 범위

Day 14는 Toss Payments V2 결제위젯을 이용한 5,900원 PREMIUM 30일 단건 결제,
결제별 구독 기여 원장, 전액 취소, 복구, FSD, Outbox/Kafka를 구현한다. 자동 갱신,
빌링키, 가상계좌, 부분 취소와 webhook은 지원하지 않는다.

## 결제 흐름

1. `POST /api/payments/orders`가 서버 가격으로 주문을 만든다.
2. 브라우저는 서버가 반환한 주문 정보로 Toss V2 위젯을 표시한다.
3. 성공 redirect는 주문별 UUID 멱등키를 `sessionStorage`에서 재사용한다.
4. 백엔드는 `READY → CONFIRMING`을 선점한 뒤 DB 트랜잭션 밖에서 Toss를 승인한다.
5. Toss의 주문 ID, 금액, `DONE` 상태를 검증한 뒤 거래·구독·Outbox를 원자 저장한다.

취소도 `PAID → CANCELING` 선점과 Toss 전액 취소 후 짧은 finalize 트랜잭션을
사용한다. 명확한 PG 거절만 `FAILED`로 확정하고, timeout이나 불명확한 응답은 즉시
조회한 뒤 결정할 수 없으면 `RECOVERY_REQUIRED`로 남긴다. 60초 scheduler와 관리자
reconcile API가 stale 주문을 다시 조회한다.

## 결제별 구독 기여 원장

각 승인 결제는 `subscription_entitlements`에 정확히 2,592,000초를 기여한다.
첫 결제는 승인 시각부터 시작하고, 연장 결제는 마지막 미취소 기여분의 종료 시각부터
시작한다.

취소 시에는 고정 30일을 현재 구독 종료일에서 빼지 않는다.

```text
unused = max(period_end - max(now, period_start), 0)
```

- 이미 사용한 구간은 `used_until`과 함께 보존한다.
- 해당 결제의 미사용 구간만 제거한다.
- 뒤의 미취소 기여분을 `unused`만큼 앞으로 이동해 PREMIUM 기간을 연속으로 만든다.
- 미사용 구간이 0이면 Toss 호출 전에 `PAYMENT_CANCEL_NOT_ELIGIBLE`로 거부한다.
- 같은 `cancelRequestId` 재요청은 저장된 `used_until`과 제거 초를 반환하며 원장을
  다시 이동하지 않는다.

예를 들어 1월 1일 결제를 1월 11일에 취소하고 그 뒤에 연장 결제가 있다면 첫 결제의
사용한 10일은 남고 미사용 20일만 제거된다. 뒤 결제는 1월 11일부터 30일을 기여한다.

## FSD 설정

모든 FSD window, count, ratio, Redis TTL, 활성화 여부와 priority는
`app.payment.fsd`에서 바인딩된다. 모든 값은 `PAYMENT_FSD_*` 환경변수로 재정의할 수
있다. 보안 규칙은 `CLOSED`, 사후 통계 규칙은 `OPEN` fail mode로 시작 시 검증된다.
또한 양수 window/TTL, `review < block`, 0~1 비율, 양수 표본 수와 priority 중복을
검증한다.

짧은 failure/IP/order 신호는 Redis ZSET에 TTL과 함께 보관한다. Redis 장애 시
감사 테이블을 사용해 보수적으로 폴백하며 PostgreSQL의 UNIQUE, 조건부 상태 전이와
JPA version이 최종 중복 방어선이다.

## 주요 설정

```dotenv
PAYMENT_ENABLED=true
PAYMENT_FRONTEND_BASE_URL=http://localhost:3000
TOSS_WIDGET_CLIENT_KEY=test_gck_...
TOSS_WIDGET_SECRET_KEY=test_gsk_...
PAYMENT_LOCK_TTL=30s
PAYMENT_RECOVERY_DELAY=60s
PAYMENT_OUTBOX_BATCH_SIZE=100
PAYMENT_FSD_ENABLED=true
```

Toss secret은 백엔드에만 전달한다. 프런트 번들에는
Compose는 `TOSS_WIDGET_CLIENT_KEY`를 프런트의
`NEXT_PUBLIC_TOSS_CLIENT_KEY`로, `TOSS_WIDGET_SECRET_KEY`를 백엔드의
`TOSS_SECRET_KEY`로 전달한다. 결제위젯에는 반드시 서로 짝이 맞는 `gck`/`gsk`
키를 사용한다. `ck`/`sk` API 개별 연동 키는 사용할 수 없다. IP는 SHA-256 해시로만 저장하고 Toss
응답은 payment key, 상태, 금액, 수단, 승인 시각, 영수증처럼 허용된 필드만 보관한다.

## 로컬 검증

```powershell
cd backend
.\gradlew.bat test

cd ..\frontend
corepack pnpm typecheck
corepack pnpm test
corepack pnpm lint
corepack pnpm build

cd ..
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml config
```

Fake gateway와 계약 테스트는 서버 가격, 멱등 처리, 복구 및 원장 재배치를 검증한다.
실제 Toss 테스트 결제는 유효한 테스트 client/secret key와 외부 PG 접근이 있는
환경에서 별도로 확인해야 한다.
