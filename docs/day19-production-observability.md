# Day 19 production observability

The production runtime EC2 hosts Prometheus and Grafana alongside the worker,
Redis, and Kafka. Prometheus discovers Auto Scaling application instances by
their `FinriskDeployment=day19` and `FinriskRole=application` EC2 tags and
scrapes their private addresses on TCP 9100. The security groups permit that
port only from the runtime instance security group.

Prometheus and Grafana bind only to runtime-host loopback. Neither service is
reachable through the ALB, Nginx, an EC2 public address, or an Internet-facing
security-group rule.

## Open Grafana securely

Find the runtime instance:

```powershell
$runtimeInstance = aws ec2 describe-instances `
  --filters Name=tag:FinriskRole,Values=runtime Name=instance-state-name,Values=running `
  --query 'Reservations[0].Instances[0].InstanceId' --output text
```

Start an interactive SSM session once to read the locally generated admin
password:

```powershell
aws ssm start-session --target $runtimeInstance
sudo cat /opt/finrisk/grafana-admin-password
```

Then forward local port 3001 without opening an inbound network port:

```powershell
aws ssm start-session --target $runtimeInstance `
  --document-name AWS-StartPortForwardingSession `
  --parameters '{"portNumber":["3001"],"localPortNumber":["3001"]}'
```

Open `http://127.0.0.1:3001` and sign in as `admin`. The provisioned
`FinRisk AWS Overview` dashboard includes application availability, request
rate and latency, JVM heap, worker outcomes, Redis, and Kafka.

Prometheus can be inspected similarly by forwarding remote and local port
9090. Keep both sessions operator-only and rotate the Grafana password after
sharing it.
