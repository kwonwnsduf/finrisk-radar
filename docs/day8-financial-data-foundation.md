# Day 8 Financial Data Foundation

Day 8 builds the DART financial-statement and debt-maturity data foundation for Day 9 risk detection. It does not calculate risk scores.

## Sample Debt Maturity Data

`backend/src/main/resources/debt-maturity/debt_maturity_sample.csv` is development/test sample data only. It is not real investment data and must not be used as investment evidence.

## Day 9 Risk Criteria Notes

Bond/JTBC-style checks:

- Debt ratio >= 200%: risk
- Debt ratio >= 300%: high risk
- Interest coverage ratio < 1: high risk
- Operating cash flow negative for two consecutive quarters: risk
- Debt maturing within 6 months / cash >= 100%: risk
- Debt maturing within 6 months / cash >= 200%: high risk
- Increasing CP/short-term debt share: risk
- Acceleration, rehabilitation, refinancing failure keywords move to Day 11 document parsing

REIT/JR Global REIT-style checks:

- LTV >= 60%: risk
- LTV >= 70%: high risk
- Debt maturity concentration within 6 months: risk
- Refinancing rate +2%p or more versus existing rate: risk
- Dividend payout ratio above 100%: risk
- FX hedge settlement burden, cash trap, failed rights offering, and listed-bond acceleration keywords move to Day 11 document parsing
