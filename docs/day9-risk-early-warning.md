# Day 9 Corporate Risk Early Warning

전체 실행 흐름, 클래스별 책임, DB·Kafka 변화와 현재 구현 한계는
[`day9-risk-early-warning-flow-guide.md`](./day9-risk-early-warning-flow-guide.md)를 참고한다.

Day 9 provides an explainable rule-based early-warning score for `BOND_ISSUER` assets. It is not an official credit rating or probability-of-default model.

## Flow

`POST /api/risks/assets/{assetId}/calculations` creates a `REQUESTED` job and publishes `risk-score-requested`. The consumer claims the job atomically, evaluates rules in priority order, and stores the score, signals, and `COMPLETED` state in one transaction. Completed and critical-signal events are published after commit. The DB and polling API remain the source of truth; Day 9 intentionally uses neither Outbox nor DLT.

Only transient infrastructure failures are retried twice with exponential backoff. Business, validation, policy, arithmetic, and missing-core-data failures are not retried. Final failure is represented by the job and `risk-score-failed`.

## Policy

- Version: `corporate-risk-v1`, stored once on `RiskScore`; signals inherit it through their score FK.
- Required rules determine confidence. Missing optional Market or Group Contagion data does not by itself lower HIGH confidence.
- Missing data is never treated as a safe zero. Category status distinguishes `CALCULATED`, `NOT_AVAILABLE`, and `INSUFFICIENT_DATA`.
- Risk grade comes from score; default status comes only from registered credit events.

## Development scenario

Set `SEED_RISK_SCENARIOS_ENABLED=true` to load synthetic JTBC_SAMPLE financial, ABSTB/CP maturity, refinancing failure, payment default, and rehabilitation data. This data is for functional testing only.

## Operations

Per-rule elapsed time is logged at DEBUG (INFO for rules slower than 100ms). Completion logs include total elapsed time and rule-status counts. These metrics are not stored in the database. A future Outbox/DLT can be introduced when notifications, email, AI reports, or replay operations require delivery guarantees.
