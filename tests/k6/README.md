# Production k6 checks

These scenarios target the public HTTPS route so they measure Route 53, the
ALB, Nginx, and the application together. They never use an EC2 public IP.

Run the harmless public smoke check:

```powershell
docker run --rm -i grafana/k6:0.56.0 run - < tests/k6/smoke.js
```

The load scenario is deliberately read-only and capped at five virtual users.
It requires a dedicated non-admin test account and an explicit production
opt-in:

```powershell
$env:TEST_EMAIL = "load-test@example.com"
$env:TEST_PASSWORD = "use-a-secret-test-password"
$env:ALLOW_PRODUCTION_LOAD = "true"
docker run --rm -i `
  -e TEST_EMAIL -e TEST_PASSWORD -e ALLOW_PRODUCTION_LOAD `
  grafana/k6:0.56.0 run - < tests/k6/load.js
```

Do not add payment confirmation, cancellation, report generation, or external
data collection to the production scenario. Run larger tests in a staging
environment after adjusting the thresholds and capacity plan.
