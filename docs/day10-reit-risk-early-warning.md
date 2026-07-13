# Day 10 REIT Risk Early Warning

Day 10 extends the Day 9 rule engine to `REIT` assets. It reuses the existing risk job,
Kafka worker, score/signal persistence, completion events, and Risk API.

## Data policy

`reit_metrics` stores disclosed period snapshots only. Previous values are selected by period;
six-month maturities and coverage are calculated from the latest eligible `debt_maturities`
snapshot. Rates and ratios are percentage values (`65.0` means 65%).

## Scoring policy

REIT rules use `reit-risk-v1`. Every rule contributes independently. Related signals are not
suppressed; the existing category caps (25/35/10/25/5) prevent category overweighting. Missing
input is `NOT_AVAILABLE`, never a safe zero.

Set `SEED_REIT_RISK_SCENARIOS_ENABLED=true` with asset seeding to load a synthetic global-REIT
scenario covering valuation loss, covenant breaches, trapped cash, rollover, funding failures,
downgrade, and rehabilitation.
