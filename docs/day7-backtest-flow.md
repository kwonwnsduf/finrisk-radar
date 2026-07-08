# Day 7 Backtest Flow

> 대상 독자: 백테스트 기능의 API, Kafka, DB 상태 전이, 전략 엔진, 커스텀 조건이 어떻게 이어지는지 빠르게 파악하려는 개발자
>
> 핵심 문장: **Day 7 백테스트는 API 요청을 DB Job과 Kafka 요청 이벤트로 바꾸고, Consumer가 가격 데이터를 읽어 전략 시뮬레이션을 실행한 뒤 결과와 상태를 DB에 저장하는 비동기 작업 흐름이다.**

## 1. 먼저 잡아야 할 큰 그림

Day7 백테스트는 한 번의 HTTP 요청 안에서 계산까지 끝내는 기능이 아니다. HTTP 요청은 "작업 등록"까지만 하고, 실제 계산은 Kafka Consumer가 나중에 처리한다.

크게 보면 세 흐름이다.

```text
1. 요청 흐름
   사용자가 백테스트 조건을 보냄
   → Job을 DB에 REQUESTED로 저장
   → Kafka에 "이 jobId 실행해" 이벤트 발행
   → API는 202 Accepted와 jobId 반환

2. 실행 흐름
   Kafka Consumer가 jobId를 받음
   → DB에서 Job 상세 조건 조회
   → Job을 RUNNING으로 변경
   → 가격 데이터 조회
   → 전략 시뮬레이션
   → 성과 지표 계산
   → 결과 저장
   → Job을 COMPLETED 또는 FAILED로 변경

3. 조회 흐름
   프론트가 jobId로 상태를 계속 조회
   → REQUESTED/RUNNING이면 대기 화면
   → COMPLETED이면 result 표시
   → FAILED이면 message 표시
```

이 구조에서 Kafka는 계산 결과를 저장하는 곳이 아니다. Kafka는 "비동기 실행을 시작시키는 신호"이고, 최종 상태와 결과는 PostgreSQL에 저장된다.

```text
Kafka
└─ backtest-requested
   └─ jobId만 전달하는 작업 시작 신호

PostgreSQL
├─ backtest_jobs
│  └─ 요청 조건, 상태, 메시지, 시작/완료 시각
└─ backtest_results
   └─ 수익률, MDD, CAGR, Sharpe, 월별 수익률, 일별 평가금액, 거래 목록
```

## 2. 한눈에 보는 전체 흐름

```text
사용자
→ POST /api/backtests
→ BacktestController
→ BacktestRequestService
→ BacktestJobService가 backtest_jobs에 REQUESTED 저장
→ BacktestEventPublisher가 Kafka backtest-requested 발행
→ BacktestRequestedConsumer
→ BacktestExecutionService
→ BacktestJobService가 RUNNING 전이
→ MarketPriceService에서 기간별 가격 조회
→ DefaultBacktestEngine
→ StrategyFactory가 Strategy 선택
→ Strategy가 TradingSimulator 또는 자체 로직으로 매매 시뮬레이션
→ PerformanceCalculator가 수익률, MDD, CAGR, Sharpe, 월별 수익률 계산
→ BacktestJobService가 backtest_results 저장 및 Job COMPLETED 전이
→ GET /api/backtests/{jobId}
→ BacktestQueryService가 Job과 Result를 합쳐 응답
```

위 흐름을 요청/실행/조회로 나누면 다음과 같다.

```text
[요청 단계]
BacktestController
→ BacktestRequestService
→ BacktestJobService.createRequested()
→ BacktestEventPublisher.publishRequestedAndAwait()
→ HTTP 202 Accepted

[비동기 실행 단계]
BacktestRequestedConsumer
→ BacktestExecutionService.execute()
→ BacktestJobService.markRunning()
→ MarketPriceService.getPrices()
→ DefaultBacktestEngine.execute()
→ StrategyFactory.get()
→ BacktestStrategy.simulate()
→ PerformanceCalculator.calculate()
→ BacktestJobService.completeWithResult()

[조회 단계]
BacktestController
→ BacktestQueryService.getForUser()
→ BacktestJobRepository.findById()
→ BacktestResultRepository.findById()
→ BacktestJobResponse
```

실패 흐름은 다음과 같다.

```text
요청 검증 실패
→ BusinessException
→ Job 생성 전이면 저장 없음

Kafka 발행 실패
→ 생성된 Job을 FAILED로 보정
→ BACKTEST_REQUEST_FAILED 반환

Consumer 실행 중 실패
→ BacktestExecutionService catch
→ BacktestJobService.markFailed()
→ Job status = FAILED
→ 에러 메시지는 1000자 이하로 저장
```

## 3. 상태 전이와 저장 위치

백테스트 상태는 Kafka 토픽으로 표현하지 않고 `backtest_jobs.status` 컬럼으로 표현한다.

```text
REQUESTED
   |
   | Kafka 발행 성공 후 Consumer가 메시지를 받음
   v
RUNNING
   |
   | 계산 성공
   v
COMPLETED

REQUESTED 또는 RUNNING
   |
   | Kafka 발행 실패, 가격 데이터 없음, 계산 예외 등
   v
FAILED
```

상태를 바꾸는 코드는 다음 위치에 있다.

| 상태 변화 | 호출 위치 | 실제 변경 메서드 |
| --- | --- | --- |
| Job 생성 및 `REQUESTED` | `BacktestRequestService` | `BacktestJobService.createRequested()` → `BacktestJob.requested()` |
| `REQUESTED` → `RUNNING` | `BacktestExecutionService` | `BacktestJobService.markRunning()` → `BacktestJob.start()` |
| `RUNNING` → `COMPLETED` | `BacktestExecutionService` | `BacktestJobService.completeWithResult()` → `BacktestJob.complete()` |
| `REQUESTED/RUNNING` → `FAILED` | 요청 실패 또는 실행 실패 | `BacktestJobService.markFailed()` → `BacktestJob.fail()` |

각 단계의 데이터 주인은 다음과 같다.

| 데이터 | 저장 위치 | 누가 만드나 | 누가 읽나 |
| --- | --- | --- | --- |
| 요청 조건 | `backtest_jobs` | `BacktestRequestService` | `BacktestExecutionService` |
| 실행 신호 | Kafka `backtest-requested` | `BacktestEventPublisher` | `BacktestRequestedConsumer` |
| 상태 | `backtest_jobs.status` | `BacktestJobService` | `BacktestQueryService`, 프론트 |
| 가격 데이터 | `market_prices` 조회 결과 | Day5 수집 파이프라인 | `MarketPriceService`, 백테스트 엔진 |
| 계산 결과 | `backtest_results` | `BacktestJobService.completeWithResult()` | `BacktestQueryService`, 프론트 |

## 4. 이 기능을 읽는 추천 순서

처음 코드를 따라갈 때는 패키지 순서대로 읽는 것보다 요청 하나가 지나가는 순서대로 보는 편이 쉽다.

```text
1. BacktestController
   API 입구를 본다.

2. BacktestRequestService
   요청 검증, Job 생성, Kafka 발행을 본다.

3. BacktestJob / BacktestJobService
   Job 상태가 어떻게 바뀌는지 본다.

4. BacktestEventPublisher / BacktestRequestedConsumer
   HTTP 요청과 실제 계산이 Kafka로 어떻게 분리되는지 본다.

5. BacktestExecutionService
   Consumer 이후 실제 실행 흐름을 본다.

6. DefaultBacktestEngine
   가격 검증, 전략 선택, 성과 계산의 큰 틀을 본다.

7. StrategyFactory / BacktestStrategy / SignalStrategy / CustomStrategy
   전략별 매매 시뮬레이션 방식을 본다.

8. PerformanceCalculator
   최종 지표가 어떻게 계산되는지 본다.

9. BacktestQueryService
   프론트가 jobId로 상태와 결과를 어떻게 읽는지 본다.
```

## 5. Kafka 토픽이 하나인 이유

백테스트 Kafka 토픽은 현재 `backtest-requested` 하나만 있다.

```text
backtest-requested
```

`RUNNING`, `COMPLETED`, `FAILED`는 Kafka 토픽이 아니라 `backtest_jobs.status`의 DB 상태값이다. 즉 Kafka는 "작업 시작 요청을 비동기로 넘기는 큐" 역할만 하고, 상태와 결과의 source of truth는 PostgreSQL이다.

관련 클래스:

| 클래스 | 의미 |
| --- | --- |
| `BacktestTopics` | `backtest-requested` 토픽 이름 상수 |
| `BacktestKafkaConfiguration` | `NewTopic` Bean으로 `backtest-requested` 생성 |
| `BacktestEventPublisher` | 요청 이벤트를 Kafka에 발행하고 broker ack를 최대 5초 대기 |
| `BacktestRequestedConsumer` | `backtest-requested`를 구독하고 `BacktestExecutionService.execute()` 호출 |
| `BacktestStatus` | `REQUESTED`, `RUNNING`, `COMPLETED`, `FAILED` DB 상태 enum |

마켓데이터 파이프라인처럼 `requested`, `fetched`, `failed` 토픽을 분리하지 않은 이유는 현재 백테스트 완료 이벤트를 별도 서비스가 구독하지 않고, 프론트가 조회 API를 polling해서 DB 상태를 보는 구조이기 때문이다.

## 6. API 계층

### `BacktestController`

엔드포인트는 두 개다.

```text
POST /api/backtests
GET  /api/backtests/{jobId}
```

`POST /api/backtests`는 `@UsageLimit(UsageType.BACKTEST)`가 붙어 있어 사용량 제한 대상이다. 인증 사용자의 `userId`와 요청 body를 `BacktestRequestService.request()`로 넘기고, 성공하면 HTTP 202 Accepted로 `jobId`와 초기 status를 반환한다.

`GET /api/backtests/{jobId}`는 인증 사용자의 `userId`, `role`, `jobId`를 `BacktestQueryService.getForUser()`로 넘겨 Job 상태와 완료 결과를 조회한다.

### `BacktestCreateRequest`

백테스트 생성 요청 DTO다.

| 필드 | 의미 |
| --- | --- |
| `assetId` | 백테스트할 자산 ID |
| `strategyType` | 사용할 전략 타입 |
| `startDate`, `endDate` | 가격 조회 기간 |
| `initialCash` | 시작 현금. 없으면 `10000000.000000` |
| `buyConditions` | CUSTOM 전략의 매수 조건 목록 |
| `sellConditions` | CUSTOM 전략의 매도 조건 목록 |

생성자 compact block에서 `initialCash`, 조건 리스트의 기본값을 채운다. null 리스트는 빈 리스트가 되고, 리스트는 `List.copyOf()`로 불변 복사된다.

### `BacktestCreateResponse`

요청 직후 반환되는 최소 응답이다.

```text
jobId
status
```

보통 Kafka 발행까지 성공하면 `REQUESTED` 상태가 반환된다.

### `BacktestJobResponse`

조회 API의 응답 DTO다. Job 메타데이터와 결과를 함께 담는다.

```text
jobId, assetId, strategyType, startDate, endDate, initialCash,
status, message, startedAt, completedAt, result
```

아직 완료 전이면 `result`는 null일 수 있다.

### `BacktestResultResponse`

`backtest_results`의 JSON 문자열 필드들을 실제 리스트로 역직렬화해서 응답한다.

| 필드 | 의미 |
| --- | --- |
| `totalReturnRate` | 전략 최종 수익률 |
| `cagr` | 연환산 수익률 |
| `mdd` | 최대 낙폭 |
| `winRate` | 매도 거래 기준 승률 |
| `tradeCount` | 거래 기록 수 |
| `sharpeRatio` | 일간 수익률 기반 Sharpe ratio |
| `benchmarkReturnRate` | 단순 보유 기준 수익률 |
| `monthlyReturns` | 월별 수익률 배열 |
| `dailyPortfolioValues` | 날짜별 현금, 보유수량, 종가, 평가금액 |
| `trades` | 매수/매도 거래 기록 |

## 7. 요청 서비스 흐름

### `BacktestRequestService`

백테스트 요청의 입구 역할을 한다.

처리 순서:

```text
1. 날짜 검증: startDate <= endDate
2. 사용자 존재 확인
3. 자산 존재 확인
4. 전략 요청 검증
5. BacktestJob REQUESTED 생성
6. BacktestRequestedEvent 발행
7. jobId와 status 반환
```

CUSTOM 전략이면 `buyConditions`, `sellConditions`가 반드시 있어야 한다. 매수 조건에 매도용 조건이 들어오거나, 매도 조건에 매수용 조건이 들어오면 `INVALID_INPUT`이 난다.

조건별 필수값도 여기서 먼저 걸러진다.

```text
RSI_LESS_THAN, RSI_GREATER_THAN, STOP_LOSS, TAKE_PROFIT, TRAILING_STOP
→ value 필요

PRICE_ABOVE_MA, MA_CROSS_UP, BOLLINGER_LOWER_TOUCH, VOLUME_SPIKE, BREAKOUT,
MA_DISCOUNT, DONCHIAN_HIGH_BREAKOUT, MOMENTUM_POSITIVE, PRICE_BELOW_MA,
MA_CROSS_DOWN, BOLLINGER_UPPER_TOUCH, MA_PREMIUM, DONCHIAN_LOW_BREAKDOWN,
MOMENTUM_NEGATIVE
→ period 필요

MACD_GOLDEN_CROSS, MACD_DEAD_CROSS
→ 별도 필수값 없음. short/long/signal period가 없으면 evaluator에서 기본값 사용
```

`shouldUseLegacyCreate()`는 기존 기본 백테스트 호환용 분기다. 시작 현금이 기본값이고 커스텀 조건이 없으면 `strategy_config`를 저장하지 않는 옛 형태로 Job을 만든다. 그 외에는 조건 목록을 `BacktestStrategyConfig`로 묶어 JSON 문자열로 저장한다.

## 8. Job과 Result 도메인

### `BacktestJob`

`backtest_jobs` 테이블의 JPA Entity다. 작업 메타데이터와 상태를 가진다.

중요 필드:

| 필드 | 의미 |
| --- | --- |
| `jobId` | UUID 작업 ID |
| `requestedByUserId` | 요청한 사용자 ID |
| `assetId` | 대상 자산 |
| `strategyType` | 전략 종류 |
| `startDate`, `endDate` | 백테스트 기간 |
| `initialCash` | 시작 현금 |
| `strategyConfig` | CUSTOM 조건 JSON |
| `status` | REQUESTED/RUNNING/COMPLETED/FAILED |
| `message` | 상태 메시지 |
| `startedAt`, `completedAt` | 실행 시작/완료 시각 |

상태 전이는 Entity 메서드 안에 있다.

```text
requested()
→ status = REQUESTED
→ message = "Backtest requested."

start()
→ REQUESTED일 때만 RUNNING으로 전이
→ 이미 실행된 Job이면 false

complete()
→ RUNNING일 때만 COMPLETED로 전이

fail()
→ COMPLETED 또는 FAILED가 아니면 FAILED로 전이
```

`start()`가 boolean을 반환하는 이유는 Kafka 메시지가 중복 전달되어도 이미 RUNNING/COMPLETED/FAILED인 Job을 다시 실행하지 않기 위해서다.

### `BacktestJobService`

Job과 Result 저장을 담당하는 도메인 서비스다.

| 메서드 | 의미 |
| --- | --- |
| `createRequested()` | `BacktestJob.requested()` 생성 후 저장 |
| `markRunning()` | Job을 RUNNING으로 전이 |
| `completeWithResult()` | Result 저장 후 Job을 COMPLETED로 전이 |
| `markFailed()` | 별도 트랜잭션으로 Job을 FAILED 전이 |
| `getInternal()` | 내부 실행용 Job 조회 |

`markFailed()`는 `REQUIRES_NEW` 트랜잭션이다. 실행 중 예외로 기존 트랜잭션이 롤백되더라도 실패 상태는 별도 트랜잭션으로 남기려는 의도다.

### `BacktestResult`

`backtest_results` 테이블의 JPA Entity다. 계산 결과를 저장한다.

단일 숫자 지표는 컬럼으로 저장하고, 길이가 가변인 배열 데이터는 JSONB 문자열로 저장한다.

```text
monthly_returns
daily_portfolio_values
trades
```

`BacktestResult.from()`은 `BacktestCalculationResult`와 JSON 문자열을 받아 Entity로 옮기는 팩토리 메서드다.

## 9. Kafka 계층

### `BacktestRequestedEvent`

Kafka payload다.

```text
jobId
requestedAt
```

Kafka 메시지에는 전체 백테스트 조건을 싣지 않는다. 조건과 기간은 이미 `backtest_jobs`에 저장되어 있고, Consumer는 `jobId`로 DB에서 다시 읽는다. 그래서 메시지가 작고, DB가 작업 원본이 된다.

### `BacktestEventPublisher`

`KafkaTemplate<String, Object>`로 `backtest-requested`에 이벤트를 발행한다.

```text
topic = backtest-requested
key = jobId.toString()
value = BacktestRequestedEvent
timeout = 5 seconds
```

발행 결과를 기다리는 이유는 API가 "작업이 큐에 들어갔다"는 최소 보장을 받고 202를 반환하기 위해서다. 발행 실패 시 `BacktestEventPublishException`을 던지고, `BacktestRequestService`가 Job을 FAILED로 보정한다.

### `BacktestRequestedConsumer`

`@KafkaListener`로 `backtest-requested`를 구독한다.

```text
groupId = finrisk-backtest-worker
```

메시지를 받으면 `BacktestExecutionService.execute(jobId)`만 호출한다. 예외는 로그로 남긴다. 실제 실패 상태 저장은 `BacktestExecutionService` 내부 catch에서 처리한다.

## 10. 실행 서비스 흐름

### `BacktestExecutionService`

Consumer 이후 실제 백테스트 실행을 오케스트레이션한다.

처리 순서:

```text
1. jobs.markRunning(jobId)
2. false면 이미 처리 중이거나 처리 완료된 Job이므로 return
3. jobs.getInternal(jobId)로 Job 상세 조회
4. MarketPriceService.getPrices(assetId, startDate, endDate)
5. BacktestContext 구성
6. engine.execute(...)
7. jobs.completeWithResult(jobId, calculation)
8. 예외 발생 시 jobs.markFailed(jobId, safeMessage)
```

`shouldUseLegacyEngineCall()`은 기존 기본 백테스트 호환용이다. `strategyConfig`가 없고 시작 현금이 기본값이면 예전 시그니처인 `engine.execute(strategyType, priceData)`를 호출한다. 그렇지 않으면 `BacktestContext`를 만들어 `initialCash`, `strategyConfig`, 수수료/슬리피지 0을 넘긴다.

실패 메시지는 두 갈래다.

```text
BusinessException
→ ErrorCode의 사용자용 메시지

그 외 RuntimeException
→ "Backtest failed while processing market prices."
```

## 11. 엔진 계층

### `BacktestEngine`

엔진 인터페이스다.

```text
execute(StrategyType, prices)
execute(BacktestContext, prices)
```

기본 메서드는 `BacktestContext`에서 `strategyType`만 꺼내 예전 방식으로 위임한다. 실제 구현체인 `DefaultBacktestEngine`은 두 메서드를 모두 구현한다.

### `BacktestContext`

전략 실행에 필요한 설정 묶음이다.

```text
strategyType
initialCash
strategyConfig
feeRate
slippageRate
```

현재 실행 서비스는 fee/slippage를 `BigDecimal.ZERO`로 넘긴다. 즉 필드는 준비되어 있지만 현재 매매 시뮬레이션에 비용 반영은 없다.

### `DefaultBacktestEngine`

백테스트 계산의 중심 구현체다.

```text
1. 가격 데이터 null/empty 검증
2. 날짜 오름차순 정렬
3. OHLCV null 및 양수 검증
4. StrategyFactory에서 전략 선택
5. 전략 simulate()
6. PerformanceCalculator.calculate()
```

가격 데이터가 없으면 `BACKTEST_PRICE_DATA_NOT_FOUND`, 가격 값이 비정상이면 `BACKTEST_PRICE_DATA_INVALID`를 던진다.

### `BacktestCalculationResult`

엔진의 최종 계산 결과 record다. `BacktestResult` 저장과 API 응답의 원본 데이터 구조다.

```text
firstPriceDate, lastPriceDate,
initialClose, finalClose,
totalReturnRate, cagr, mdd, winRate,
tradeCount, sharpeRatio, benchmarkReturnRate,
monthlyReturns, dailyPortfolioValues, trades
```

### `BacktestSimulationResult`

전략 시뮬레이션 결과다.

```text
dailyPortfolioValues
trades
```

전략은 수익률을 직접 계산하지 않고, 날짜별 포트폴리오 가치와 거래 목록만 만든다. 최종 성과 지표는 `PerformanceCalculator`가 공통 계산한다.

### `TradingSimulator`

일반적인 매수/매도 시그널 전략이 공유하는 시뮬레이터다.

`run()`은 현금 100% 또는 보유 100%의 단순 포지션 모델이다.

```text
보유 수량 > 0 && sellSignal == true
→ 전량 매도

보유 수량 == 0 && 현금 > 0 && buySignal == true
→ 전액 매수

매일 DailyPortfolioValue 저장
```

`dca()`는 월이 바뀔 때마다 일정 금액을 나눠 매수하는 적립식 전략용 메서드다.

현재 수수료와 슬리피지는 `BacktestContext`에 있지만 `TradingSimulator` 계산에는 반영되지 않는다.

### `PerformanceCalculator`

전략별로 공통인 성과 지표를 계산한다.

| 메서드 | 의미 |
| --- | --- |
| `percent()` | `(end / start - 1) * 100` |
| `cagr()` | 시작/종료 포트폴리오 가치와 기간으로 연환산 수익률 계산 |
| `mdd()` | 누적 최고점 대비 최악 하락률 |
| `winRate()` | 매수 후 매도 가격이 높았던 비율 |
| `sharpe()` | 일별 포트폴리오 수익률 평균/표준편차 * sqrt(252) |
| `monthlyReturns()` | 월 단위 포트폴리오 수익률 |

`benchmarkReturnRate`는 같은 기간 첫 종가 대비 마지막 종가의 단순 보유 수익률이다. 전략 성과와 비교하기 위한 기준값이다.

## 12. 전략 계층

### `StrategyType`

지원 전략 목록이다.

```text
BUY_AND_HOLD
MOVING_AVERAGE
RSI
BOLLINGER_BAND
MACD
VOLATILITY_BREAKOUT
DCA
MA_DEVIATION
DONCHIAN_CHANNEL
MOMENTUM
CUSTOM
```

### `StrategyFactory`

`StrategyType`을 실제 `BacktestStrategy` 구현체로 연결한다.

생성자에서 모든 전략을 register하고, `get(type)` 호출 시 해당 전략을 반환한다. 지원하지 않는 타입이면 `IllegalArgumentException`을 던진다.

### `BacktestStrategy`

모든 전략 구현체의 인터페이스다.

```text
supports()
simulate(context, prices)
```

`supports()`는 이 전략이 어떤 `StrategyType`인지 알려준다. `simulate()`는 가격 데이터와 실행 설정을 받아 `BacktestSimulationResult`를 만든다.

### `SignalStrategy`

매수/매도 signal만 다르고 실행 방식은 같은 전략들의 추상 클래스다.

하위 클래스는 두 메서드만 구현하면 된다.

```text
buySignal(index, prices)
sellSignal(index, prices)
```

`SignalStrategy`는 이 signal을 `TradingSimulator.run()`에 넘긴다. 그래서 이동평균, RSI 같은 전략은 "언제 사고 언제 팔지"만 정의하고, 현금/수량/거래 기록 처리는 공통 시뮬레이터가 맡는다.

예시:

| 전략 | 매수 | 매도 |
| --- | --- | --- |
| `BuyAndHoldStrategy` | 첫 날짜에 매수 | 매도 안 함 |
| `MovingAverageStrategy` | 종가 > 20일 이동평균 | 종가 < 20일 이동평균 |
| `RsiStrategy` | RSI < 30 | RSI > 70 |

다른 기본 전략들도 같은 패턴으로 지표 계산기를 사용해 signal을 만든다.

## 13. CUSTOM 전략

### `BacktestStrategyConfig`

CUSTOM 전략 조건 묶음이다.

```text
buyConditions
sellConditions
```

`BacktestRequestService`가 요청 조건을 이 record로 감싼 뒤 JSON으로 직렬화해서 `backtest_jobs.strategy_config`에 저장한다. 실행 시 `BacktestExecutionService.readConfig()`가 다시 역직렬화한다.

### `StrategyCondition`

조건 하나의 DTO다.

```text
type
period
shortPeriod
longPeriod
signalPeriod
value
```

조건마다 필요한 필드가 다르다. 예를 들어 RSI 임계값은 `value`, 이동평균 조건은 `period`, MACD 조건은 `shortPeriod`, `longPeriod`, `signalPeriod`를 사용할 수 있다.

### `CustomConditionType`

CUSTOM 조건 타입 enum이다. 앞쪽 일부는 매수 조건, 뒤쪽 일부는 매도 조건으로 분류된다.

매수 조건:

```text
RSI_LESS_THAN
PRICE_ABOVE_MA
MA_CROSS_UP
BOLLINGER_LOWER_TOUCH
MACD_GOLDEN_CROSS
VOLUME_SPIKE
BREAKOUT
MA_DISCOUNT
DONCHIAN_HIGH_BREAKOUT
MOMENTUM_POSITIVE
```

매도 조건:

```text
RSI_GREATER_THAN
PRICE_BELOW_MA
MA_CROSS_DOWN
BOLLINGER_UPPER_TOUCH
MACD_DEAD_CROSS
STOP_LOSS
TAKE_PROFIT
TRAILING_STOP
MA_PREMIUM
DONCHIAN_LOW_BREAKDOWN
MOMENTUM_NEGATIVE
```

### `CustomStrategy`

커스텀 조건 기반 전략 실행체다. 일반 `SignalStrategy`를 상속하지 않고 자체 루프를 가진다. 이유는 손절, 익절, 트레일링 스탑처럼 진입가와 고점 상태가 필요한 매도 조건이 있기 때문이다.

실행 규칙:

```text
보유 중이면
→ highestPrice 갱신
→ sellConditions 중 하나라도 맞으면 전량 매도

미보유이고 현금이 있으면
→ buyConditions가 모두 맞을 때 전액 매수

매일 DailyPortfolioValue 저장
```

매수 조건은 `allMatch`, 매도 조건은 `anyMatch`다. 즉 커스텀 전략은 "매수는 모든 조건 충족", "매도는 하나라도 충족" 구조다.

### `CustomPositionState`

CUSTOM 매도 조건에 필요한 포지션 상태다.

```text
entryPrice
highestPrice
```

`STOP_LOSS`, `TAKE_PROFIT`은 `entryPrice` 기준 수익률을 보고, `TRAILING_STOP`은 `highestPrice` 대비 하락률을 본다.

### `BuyConditionEvaluator`

CUSTOM 매수 조건을 실제 boolean으로 평가한다.

예시:

| 조건 | 평가 방식 |
| --- | --- |
| `RSI_LESS_THAN` | RSI가 value보다 낮으면 true |
| `PRICE_ABOVE_MA` | 종가가 이동평균보다 높으면 true |
| `MA_CROSS_UP` | 전일 종가가 MA 이하이고 금일 종가가 MA 초과면 true |
| `BOLLINGER_LOWER_TOUCH` | 종가가 볼린저 하단 이하이면 true |
| `MACD_GOLDEN_CROSS` | MACD와 signal의 차이가 음수/0에서 양수로 바뀌면 true |
| `VOLUME_SPIKE` | 거래량 비율이 value 이상이면 true. value 없으면 2 |
| `BREAKOUT` | 고가가 변동성 돌파 목표가 이상이면 true |
| `MA_DISCOUNT` | 이동평균 대비 괴리율이 value 이하이면 true. value 없으면 -5 |
| `DONCHIAN_HIGH_BREAKOUT` | 종가가 Donchian 상단보다 높으면 true |
| `MOMENTUM_POSITIVE` | momentum이 양수이면 true |

### `SellConditionEvaluator`

CUSTOM 매도 조건을 평가한다.

예시:

| 조건 | 평가 방식 |
| --- | --- |
| `RSI_GREATER_THAN` | RSI가 value보다 높으면 true |
| `PRICE_BELOW_MA` | 종가가 이동평균보다 낮으면 true |
| `MA_CROSS_DOWN` | 전일 종가가 MA 이상이고 금일 종가가 MA 미만이면 true |
| `BOLLINGER_UPPER_TOUCH` | 종가가 볼린저 상단 이상이면 true |
| `MACD_DEAD_CROSS` | MACD와 signal의 차이가 양수/0에서 음수로 바뀌면 true |
| `STOP_LOSS` | 진입가 대비 수익률이 value 이하이면 true |
| `TAKE_PROFIT` | 진입가 대비 수익률이 value 이상이면 true |
| `TRAILING_STOP` | 고점 대비 하락률이 value의 음수 이하이면 true |
| `MA_PREMIUM` | 이동평균 대비 괴리율이 value 이상이면 true. value 없으면 5 |
| `DONCHIAN_LOW_BREAKDOWN` | 종가가 Donchian 하단보다 낮으면 true |
| `MOMENTUM_NEGATIVE` | momentum이 음수이면 true |

## 14. 지표 계산기

`indicator` 패키지는 전략과 커스텀 조건 평가기가 공유하는 기술적 지표 계산 계층이다.

| 클래스 | 쓰임 |
| --- | --- |
| `MovingAverageCalculator` | 이동평균 |
| `RsiCalculator` | RSI |
| `BollingerBandCalculator` | 볼린저 밴드 |
| `MacdCalculator` | MACD, signal |
| `VolatilityCalculator` | 변동성 돌파 목표가 |
| `VolumeSpikeCalculator` | 평균 대비 거래량 비율 |
| `DonchianChannelCalculator` | Donchian 상단/하단 |
| `MomentumCalculator` | 기간 대비 momentum |

전략 클래스는 직접 가격 배열을 순회하기보다 이 계산기들의 결과 리스트를 보고 signal을 판단한다.

## 15. 조회 흐름

### `BacktestQueryService`

조회 API의 핵심 서비스다.

처리 순서:

```text
1. jobId로 BacktestJob 조회
2. 없으면 BACKTEST_JOB_NOT_FOUND
3. admin이 아니고 요청자와 현재 사용자가 다르면 BACKTEST_JOB_FORBIDDEN
4. backtest_results에서 같은 jobId 조회
5. 결과가 있으면 BacktestResultResponse로 변환
6. BacktestJobResponse로 Job과 Result를 함께 반환
```

결과가 아직 없으면 `result = null`이다. 따라서 프론트는 status가 `REQUESTED` 또는 `RUNNING`이면 polling을 계속하고, `COMPLETED`이면 result를 렌더링하고, `FAILED`이면 message를 보여주는 방식으로 동작한다.

## 16. DB 스키마 관점

Day7 확장 마이그레이션은 `V8__extend_backtest_strategy_results.sql`이다.

`backtest_jobs`에 추가된 필드:

```text
initial_cash
strategy_config
```

`backtest_results`에 추가된 필드:

```text
cagr
mdd
win_rate
trade_count
sharpe_ratio
benchmark_return_rate
monthly_returns
daily_portfolio_values
trades
```

이 확장으로 단순 시작/종료 수익률만 저장하던 구조에서, 프론트 차트와 성과 분석에 필요한 상세 결과를 저장할 수 있게 됐다.

## 17. 클래스 연결 요약

```text
BacktestController
├─ BacktestRequestService
│  ├─ UserRepository
│  ├─ AssetRepository
│  ├─ BacktestJobService
│  │  ├─ BacktestJobRepository
│  │  └─ BacktestResultRepository
│  └─ BacktestEventPublisher
│     └─ KafkaTemplate
│
├─ BacktestQueryService
│  ├─ BacktestJobRepository
│  └─ BacktestResultRepository
│
BacktestRequestedConsumer
└─ BacktestExecutionService
   ├─ BacktestJobService
   ├─ MarketPriceService
   └─ BacktestEngine(DefaultBacktestEngine)
      ├─ StrategyFactory
      │  ├─ 기본 Strategy들
      │  └─ CustomStrategy
      │     ├─ BuyConditionEvaluator
      │     └─ SellConditionEvaluator
      └─ PerformanceCalculator
```

## 18. 운영/설계상 중요한 포인트

1. Kafka 토픽은 `backtest-requested` 하나다. 실행 상태는 Kafka 토픽이 아니라 DB status로 관리한다.
2. Kafka payload에는 `jobId`만 핵심으로 담고, 상세 조건은 DB의 `backtest_jobs`에서 다시 읽는다.
3. `BacktestJob.start()`가 중복 실행을 막는다. Kafka at-least-once 전달을 고려한 최소 방어다.
4. 결과 저장과 Job 완료 전이는 `completeWithResult()` 안에서 같이 일어난다.
5. 실패 저장은 `REQUIRES_NEW`라 실행 트랜잭션 실패와 별개로 FAILED 상태를 남기려는 의도가 있다.
6. 기본 전략은 대체로 `SignalStrategy` + `TradingSimulator` 구조다.
7. CUSTOM 전략은 진입가/고점 상태가 필요해서 자체 시뮬레이션 루프를 가진다.
8. 현재 수수료와 슬리피지는 context 필드만 있고 실제 거래 계산에는 반영되지 않는다.
9. 현재 결과 토픽이 없으므로 완료 후 알림, 리포트 생성, 후속 위험 분석을 이벤트로 연결하려면 `backtest-completed`, `backtest-failed` 같은 토픽을 추가하는 방향이 자연스럽다.
