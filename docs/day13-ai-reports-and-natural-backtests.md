# Day 13 AI Reports and Natural-Language Backtests

## Configuration

Text generation uses the OpenAI Responses API with strict Structured Outputs. Both of these variables must be set for AI requests:

```dotenv
OPENAI_API_KEY=
OPENAI_LLM_MODEL=gpt-5.6-terra
```

The application code reads the model only from `OPENAI_LLM_MODEL`. Local environment and Docker configuration set it to `gpt-5.6-terra`, which supports the Responses API and Structured Outputs. The application returns `REPORT_LLM_NOT_CONFIGURED` before an external call when either the key or model is missing.

Optional operational settings are `OPENAI_LLM_CONNECT_TIMEOUT`, `OPENAI_LLM_READ_TIMEOUT`, `OPENAI_LLM_MAX_OUTPUT_TOKENS`, and `OPENAI_LLM_MAX_CONTEXT_CHARACTERS`. Prompts, document bodies, questions, and API keys are not logged.

## Report workflow

Supported report types are `RISK_ANALYSIS`, `BACKTEST_ANALYSIS`, and `WATCHLIST_SUMMARY`. Their shared state flow is:

```text
REQUESTED -> RUNNING -> COMPLETED
                     -> FAILED
```

The server, not the model, controls the workflow. Risk analysis resolves the asset, loads bounded risk and type-specific financial data, retrieves at most eight RAG chunks, calls the LLM, validates every evidence ID against the supplied context, and stores structured JSON. Backtest analysis checks job ownership and a completed result. Watchlist summaries load only the authenticated user's bounded watchlist.

The UI displays server progress steps (`ASSET_RESOLUTION`, `RISK_DATA`, `DOCUMENT_SEARCH`, `AI_ANALYSIS`, `REPORT_SAVE`, `COMPLETED`), never hidden chain-of-thought.

Prompt versions are constants in `ReportPromptFactory`:

- `risk-analysis-v1`
- `backtest-analysis-v1`
- `watchlist-summary-v1`
- `backtest-request-parser-v1`

## Usage and Kafka failure policy

Report endpoints reserve usage explicitly and do not use `@UsageLimit`.

1. Validate authentication, input, LLM configuration, idempotency, and recent duplicates.
2. Reserve the applicable usage key.
3. Commit the `REQUESTED` report and reservation key.
4. Publish `report-generation-requested`.
5. On publish success, retain the reservation as consumed usage.
6. On post-commit publish failure, mark the report `FAILED` in a new transaction and release Redis usage only when `usage_compensated_at` was previously null.

After Kafka accepts a request, retryable generation failures are retried twice. A final asynchronous data or LLM failure becomes `FAILED` without usage compensation. Duplicate events are ignored through report state. The scheduler republishes stale `REQUESTED` work and recovers stale `RUNNING` work; failed recovery of already accepted work also does not compensate usage.

## Natural-language backtests

`POST /api/backtests/natural-language` parses a question into a restricted schema, resolves an asset, and delegates to the same `BacktestRequestValidator` and Kafka request path as manual backtests.

- `NEEDS_CLARIFICATION` returns HTTP 200 and does not reserve usage.
- Parse, schema, asset, and validation failures do not reserve usage.
- After successful validation, only `BACKTEST` is reserved.
- Successful `BacktestJob` creation consumes `BACKTEST` exactly once and never consumes `AI_AGENT`.
- Failure before job creation releases the reservation.
- Kafka failure after job creation marks the job failed but retains its `BACKTEST` usage.

The validator enforces supported strategy enums, one to five custom buy/sell conditions, indicator periods from 2 to 500, RSI values from 0 to 100, chronological non-future dates with a maximum ten-year range, and the configured initial-cash bounds.

## API examples

All endpoints require bearer authentication. Report creation also accepts an `Idempotency-Key` header.

```http
POST /api/reports/risk
Idempotency-Key: 52d2b7ca-7a18-44c4-a5ad-830bf045c9c3
Content-Type: application/json

{"assetId": 1, "question": "주요 위험과 근거를 분석해줘"}
```

`assetId` may be omitted when the question identifies exactly one asset.

```http
POST /api/reports/backtest
Content-Type: application/json

{"backtestJobId": "00000000-0000-0000-0000-000000000000", "question": "하락장 취약성을 설명해줘"}
```

```http
POST /api/reports/watchlist-summary
Content-Type: application/json

{"question": "이번 주 우선 확인할 위험을 요약해줘"}
```

```http
POST /api/backtests/natural-language
Content-Type: application/json

{"question": "삼성전자 2024-01-01부터 2025-01-01까지 RSI 전략으로 백테스트해줘", "assetId": 1}
```

Polling endpoints are `GET /api/reports/{reportId}` and `GET /api/backtests/{jobId}`. Report listing supports `reportType`, `status`, `page`, and `size`. Completed backtests are available from `GET /api/backtests?status=COMPLETED&page=0&size=20`.

## Docker and verification

```powershell
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml build
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml up -d
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml ps
```

Verify backend health at `/actuator/health`, Prometheus metrics at `/actuator/prometheus`, the frontend at `/login`, Flyway version 16, the `ai_reports` table, and the `report-generation-requested` topic. AI smoke tests run only when `.env.local` has both required OpenAI variables. Stop without deleting volumes:

```powershell
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml stop
```

Relevant metrics are `llm.calls`, `llm.success`, `llm.failure`, `llm.tokens.input`, `llm.tokens.output`, `llm.duration`, `report.generated`, `report.failed`, `ai.agent.requests`, and `rag.context.chunks`.
