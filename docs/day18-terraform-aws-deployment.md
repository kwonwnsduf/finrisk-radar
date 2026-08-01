# Day 18: Terraform AWS deployment

Day 18 deploys the existing Day 17 ECR images to one EC2 instance and one private RDS PostgreSQL instance. It deliberately does not manage the ECR repositories or the existing `finrisk-raw-data` bucket.

## State boundaries

| Stack | State | Manages |
| --- | --- | --- |
| Day 17 root | Existing local Day 17 state | ECR repositories and lifecycle policies |
| Day 18 state bootstrap | Independent bootstrap state | S3 remote-state bucket only |
| Day 18 environment | `finrisk-radar/day18/terraform.tfstate` | VPC, EC2, RDS, IAM, SSM access policy, CloudWatch |

The Day 18 environment reads ECR repositories, ECR images, and `finrisk-raw-data` through data sources. Destroying Day 18 therefore cannot delete those reused resources.

## CI/CD deployment

Day 18 uses a GitHub-hosted Actions runner. It is not a self-hosted runner and it never connects to EC2 over SSH.

```text
GitHub-hosted runner
  -> AWS OIDC temporary credentials
  -> build and push immutable ECR images
  -> SSM SendCommand
  -> EC2 pulls the image digests and restarts Compose
```

The AWS trust policy accepts only `kwonwnsduf/finrisk-radar` runs from the `main` branch. No long-lived AWS access key is stored in GitHub. EC2 needs outbound HTTPS for ECR and SSM, but its security group does not allow GitHub runner IP addresses or SSH.

Before the first workflow run, create one non-secret GitHub repository variable:

```text
TOSS_WIDGET_CLIENT_KEY=<test_gck_...>
```

The workflow is `.github/workflows/deploy-day18.yml`. A push to `main` affecting the backend, frontend, or deployment files triggers it; `workflow_dispatch` also permits a manual run. Secret application values remain in `/finrisk/day18/*` SSM SecureString parameters and are never copied into GitHub.

The migration was validated in two stages. GitHub Actions deployment was proven against the original instance first; Terraform now supplies only the static `modules/compute/files/bootstrap.sh`. Compose, Nginx, CloudWatch, and deployment files are ordinary files downloaded from the exact Git commit by the SSM deployment. No `.tftpl` files remain.

## Existing local configuration analysis

Only presence was inspected; no secret value was printed or copied.

| Value | Current local status | Day 18 decision |
| --- | --- | --- |
| PostgreSQL password | Placeholder/test | Generate a new production value immediately before the first plan/apply |
| Redis password | Placeholder/test | Generate a new production value immediately before the first plan/apply |
| JWT secret | Configured | Reuse only if it is a production-safe Base64 key and rotation is acceptable |
| Google ID/secret | Configured | Reuse only if the OAuth client can later accept the Day 19 HTTPS callback |
| DART key | Configured | Reuse if its quota and account are intended for the portfolio deployment |
| Naver ID/secret | Configured | Reuse if its quota and account are intended for the portfolio deployment |
| OpenAI key/model | Configured | Reuse only after confirming billing limits and intended model access |
| Toss widget keys | Missing | Create or supply test-widget keys before enabling the payment verification |
| S3 bucket | `finrisk-raw-data` | Reuse; do not create another application bucket |

Redis authentication is mandatory. Redis stores refresh tokens, revoked access-token IDs, OAuth exchange codes, usage counters, payment locks, and FSD signals.

## Public local configuration

Public identifiers are loaded automatically from a Git-ignored local tfvars file, not from command arguments or `TF_VAR_*` variables.

```powershell
Copy-Item `
  infra\aws\terraform\environments\day18\day18.auto.tfvars.example `
  infra\aws\terraform\environments\day18\day18.auto.tfvars
```

Edit `day18.auto.tfvars` and set:

- Google client ID;
- Toss widget client key;
- Naver client ID;
- OpenAI LLM model;
- optional HTTP source CIDRs and existing-bucket KMS key ARN.

The file is covered by the repository's `*.tfvars` ignore rule. It must never contain a password, API secret, token, or private key.

The Toss client key is a frontend build-time value. GitHub Actions reads it from the non-secret repository variable `TOSS_WIDGET_CLIENT_KEY` and embeds it while building the frontend image.

## SSM parameter contract

Terraform does not create these parameters and does not store their values. The EC2 role reads them during boot. Terraform reads the PostgreSQL password through an ephemeral SSM resource solely to supply RDS's write-only `password_wo` argument; the value is omitted from plan and state.

| Logical name | SSM SecureString name |
| --- | --- |
| PostgreSQL password | `/finrisk/day18/postgres/password` |
| Redis password | `/finrisk/day18/redis/password` |
| JWT secret | `/finrisk/day18/jwt/secret` |
| Google client secret | `/finrisk/day18/google/client-secret` |
| Toss widget secret key | `/finrisk/day18/toss/widget-secret-key` |
| DART API key | `/finrisk/day18/dart/api-key` |
| Naver client secret | `/finrisk/day18/naver/client-secret` |
| OpenAI API key | `/finrisk/day18/openai/api-key` |

Register one parameter at a time. The script prompts without placing the value in PowerShell history:

```powershell
.\infra\aws\scripts\register-day18-secret.ps1 -Secret postgres_password
.\infra\aws\scripts\register-day18-secret.ps1 -Secret redis_password
.\infra\aws\scripts\register-day18-secret.ps1 -Secret jwt_secret
.\infra\aws\scripts\register-day18-secret.ps1 -Secret google_client_secret
.\infra\aws\scripts\register-day18-secret.ps1 -Secret toss_widget_secret_key
.\infra\aws\scripts\register-day18-secret.ps1 -Secret dart_api_key
.\infra\aws\scripts\register-day18-secret.ps1 -Secret naver_client_secret
.\infra\aws\scripts\register-day18-secret.ps1 -Secret openai_api_key
```

For an approved existing local value, explicitly opt in instead of retyping it:

```powershell
.\infra\aws\scripts\register-day18-secret.ps1 `
  -Secret jwt_secret `
  -ReuseLocalValue
```

Do not reuse the current PostgreSQL or Redis placeholders. Generate strong URL-safe values just before registration. Generate the JWT value from at least 32 random bytes and store its Base64 representation. Values should avoid newlines because Docker Compose consumes them from an env file.

Before apply, verify names and types without printing decrypted values:

```powershell
aws ssm describe-parameters `
  --region ap-northeast-2 `
  --parameter-filters "Key=Name,Option=BeginsWith,Values=/finrisk/day18/" `
  --query "Parameters[].{Name:Name,Type:Type,Tier:Tier}" `
  --output table
```

## Bootstrap and validation

Create only the state bucket:

```powershell
terraform -chdir=infra/aws/terraform/bootstrap/day18-state init
terraform -chdir=infra/aws/terraform/bootstrap/day18-state validate
terraform -chdir=infra/aws/terraform/bootstrap/day18-state apply
```

Copy `backend.hcl.example` to the ignored `backend.hcl` and replace the account ID with the bootstrap output. Backend configuration is separate because Terraform backends cannot consume normal input variables.

Initialize Day 18 without touching Day 17 state:

```powershell
terraform -chdir=infra/aws/terraform/environments/day18 init -backend-config=backend.hcl
terraform -chdir=infra/aws/terraform/environments/day18 validate
terraform -chdir=infra/aws/terraform/environments/day18 plan
```

The first plan is the point at which the SSM parameters, AWS login, ECR image tag, existing bucket, and public local configuration must all exist.

## Runtime and cost constraints

The six container limits total 1,504 MiB: Backend 640, Kafka 384, ZooKeeper 192, Frontend 160, Redis 96, and Nginx 32. A 2GB swap file with swappiness 10 is emergency capacity only. Sustained swap activity, OOM kills, or restart loops fail the deployment acceptance test.

Kafka uses a 256MiB heap and ZooKeeper uses a 64–128MiB heap. Broker default, offsets, transactions, minimum ISR, and Confluent internal topic replication are all one. Application topics already declare one partition and one replica.

The deployment creates no NAT Gateway, Elastic IP, ALB, Route53 record, CloudFront distribution, or Multi-AZ database. The automatically assigned public IPv4 address is still billable by AWS.
