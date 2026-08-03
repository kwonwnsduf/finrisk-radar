# Day 19 ALB, HTTPS, and rolling deployment

Day 19 extends the existing Day 18 Terraform state. It does not create a second
copy of the VPC, RDS instance, ECR repositories, or application bucket.

## Public endpoint

```hcl
hosted_zone_name        = "fin-risk.com"
application_domain_name = "app.fin-risk.com"
```

Terraform requests and validates the ACM certificate, creates the Route 53
alias, redirects HTTP to HTTPS, and forwards HTTPS traffic to the application
target group.

The Google OAuth client must contain this authorized redirect URI:

```text
https://app.fin-risk.com/login/oauth2/code/google
```

## Runtime split

- The original Day 18 EC2 instance remains the runtime host for Redis, Kafka,
  ZooKeeper, and the only backend worker.
- The Auto Scaling group runs two web application instances across the two
  public subnets. Each instance runs Nginx, Next.js, and a backend with
  scheduling and Kafka listeners disabled.
- The ALB checks `/readyz`. Full dependency health remains available at
  `/actuator/health` for deployment verification and monitoring.

## First rollout

Keep these values for the first Terraform apply:

```hcl
application_fleet_enabled  = false
legacy_direct_http_enabled = true
```

This creates the ALB, certificate, DNS, target group, launch template, ASG at
zero capacity, IAM permissions, and release parameters without replacing the
original EC2 instance.

Run `Deploy Day19 to AWS` manually with `release_only=true`. This builds and
pushes the images and initializes `/finrisk/day19/releases/current`.

Then set:

```hcl
application_fleet_enabled = true
```

Apply Terraform again and verify two healthy targets. Run the workflow with
`release_only=false` to exercise the Standby rolling deployment and convert the
original host to the runtime Compose layout. After HTTPS, OAuth, payment URLs,
and asynchronous processing pass, set:

```hcl
legacy_direct_http_enabled = false
```

The former Day 18 workflow is manual-only after this change.

## Rolling deployment

The workflow requires two healthy `InService` application instances. For each
instance it enters Standby while decrementing desired capacity, waits for ALB
draining, deploys through SSM, validates local health, exits Standby, and waits
for the target to become healthy. It updates the runtime worker last and only
then promotes the candidate release to `current`.

Failed candidates are restored from the previously approved manifest. An
instance that becomes unhealthy after returning to `InService` is replaced by
the ASG using ELB health checks and the approved bootstrap release.

## Validation

```powershell
$env:TF_CLI_CONFIG_FILE = 'NUL'
terraform -chdir=infra/aws/terraform/environments/day18 fmt -check -recursive
terraform -chdir=infra/aws/terraform/environments/day18 validate
terraform -chdir=infra/aws/terraform/environments/day18 plan
```

The first plan must not show replacement of
`module.compute.aws_instance.this`. Its AMI and user data are intentionally
ignored and `prevent_destroy` protects the local Redis and Kafka volumes.
