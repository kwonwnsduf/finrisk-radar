# Day 10 REIT Risk Flow Guide

1. `POST /api/risks/assets/{assetId}/calculations` creates a `reit-risk-v1` job.
2. The existing publisher emits `risk-score-requested`.
3. The existing worker builds a context with period REIT metrics and debt snapshots.
4. The shared registry selects REIT rules by asset type.
5. The shared engine sums every result using the existing category caps.
6. The existing transaction stores `RiskScore` and positive `RiskSignal` rows.
7. Existing completion and critical-signal events are published after commit.

No REIT-specific engine, topic, worker, score table, or calculation endpoint is introduced.
