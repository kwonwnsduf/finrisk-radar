# Day 15 Admin Operations Console

Day 15 provides a focused SaaS operations console. It intentionally does not expose every
asset, risk signal, completed backtest, completed AI report, or successful collection job.
No operational log tables or retry controls were added.

## Routes

- `/admin`: exact user, subscription, payment, asynchronous-job, and review counts
- `/admin/users`: paged user and subscription status tabs
- `/admin/payments`: paged payment investigation and reconciliation
- `/admin/fsd`: fraud-screening review
- `/admin/credit-event-candidates`: document-derived credit-event review
- `/admin/operational-issues`: failed jobs and reports stale under the existing recovery policy

All `/api/admin/**` endpoints require `ROLE_ADMIN`. List endpoints cap `size` at 100, and
page enrichment bulk-loads related users, assets, subscriptions, orders, attempts, and FSD
events.

## Metric semantics

- Active subscriptions are `ACTIVE` records whose `currentPeriodEnd` is after the query time.
- New subscriptions are newly created subscription records, not renewals.
- Approved and canceled payment money is grouped by stored currency without conversion.
- Failed payment counts use failed `PaymentAttempt` records.
- Backtest and collection issue screens contain `FAILED` records only.
- AI report stale detection shares `ReportRecoveryPolicy` with
  `ReportDispatchRecoveryScheduler`: REQUESTED after one minute and RUNNING after five minutes.

Metrics that cannot be reconstructed accurately from the current model—such as subscription
termination history, auto-renewal, user suspension, and arbitrary stale thresholds—are omitted.

## Review concurrency and data safety

FSD review and credit-event candidate approval/rejection acquire pessimistic row locks and
validate the transition again after locking. The reviewer is always read from the authenticated
principal. Candidate approval keeps the existing `CandidateApprovedNotification` and downstream
document risk recalculation flow.

Admin DTOs exclude payment keys, customer keys, fingerprints, IP addresses, user agents, raw
gateway responses, provider payloads, passwords, JWTs, and refresh tokens.
