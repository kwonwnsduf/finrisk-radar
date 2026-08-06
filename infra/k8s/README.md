# FinRisk Radar Day 20: local k3s operations

Day20 adds an isolated Kubernetes validation environment. It does not replace or modify the Day17-19 AWS, Terraform, Docker Compose, Nginx, RDS, S3, or CloudWatch deployment path.

## Pinned versions

- k3d `v5.9.0`
- k3s `v1.33.12+k3s1` (`rancher/k3s:v1.33.12-k3s1`)
- kube-prometheus-stack `86.0.0` (requires Kubernetes 1.25+)
- Kafka exporter `v1.9.0`
- Redis exporter `v1.88.0`

The versions above were checked against their official release repositories. Before changing a pin, verify the image or chart exists, run `helm template --kube-version`, and smoke-test CoreDNS, Traefik, metrics-server, Prometheus, and Grafana in a disposable cluster.

## Architecture

PostgreSQL, Redis, ZooKeeper, and Kafka remain in the existing local Compose environment. k3d workloads reach them through `host.k3d.internal`. The Day20 Compose override adds a third Kafka listener without changing either existing route:

| Client | Address |
|---|---|
| Compose containers | `kafka:9092` |
| Windows host | `localhost:29092` |
| k3d Pods | `host.k3d.internal:39092` |

Kubernetes runs one API Backend, one Frontend, and one fixed-replica Worker. Only Backend has an HPA. The Worker deliberately permits downtime during updates so that payment, outbox, subscription, and AI recovery schedulers never run concurrently in two Pods.

## Prerequisites

- Docker Desktop with at least 12 GB available to the Day20 workload
- k3d 5.9.0
- kubectl compatible with Kubernetes 1.33
- Helm 3
- PowerShell 7 or Windows PowerShell 5.1

Copy the secret template and replace every required placeholder:

```powershell
Copy-Item infra/k8s/secrets/app.env.example infra/k8s/secrets/.env.k3s
```

`CRON_SYSTEM_USER_EMAIL` must identify an existing application user. The market-data CronJob attributes collection jobs to that user.

## Deploy

Run from the repository root:

```powershell
./infra/k8s/scripts/bootstrap.ps1
./infra/k8s/scripts/build-and-import.ps1
./infra/k8s/scripts/deploy.ps1
./infra/k8s/scripts/verify.ps1
```

`bootstrap.ps1` starts only the four Compose dependencies and creates the `finrisk-day20` k3d cluster. `build-and-import.ps1` uses the existing Dockerfiles and imports local images. `deploy.ps1` creates Secrets, applies the application manifests, installs the pinned monitoring chart, then applies PodMonitor, ServiceMonitor, exporters, and the FinRisk dashboard.

## Routing

Traefik exposes:

- `http://localhost/` to Frontend
- `http://localhost/api/*` to Backend
- `http://localhost/oauth2/*` and `/login/oauth2/*` to Backend

Actuator metrics, Prometheus, and Grafana are not exposed through Ingress.

```powershell
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
kubectl port-forward -n monitoring svc/monitoring-grafana 3001:80
```

Grafana provisions `FinRisk Kubernetes Overview` from the committed dashboard ConfigMap.

## Cron isolation

All CronJobs set:

- `APP_WORKER_ENABLED=false`
- `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false`
- `SPRING_TASK_SCHEDULING_ENABLED=false`
- both document scheduler toggles to `false`
- `SPRING_MAIN_WEB_APPLICATION_TYPE=none`

`Day20CronTaskRunner` also refuses to execute if Spring's scheduled annotation processor exists. A successful Job must log only its selected task markers:

```text
event=cron_task_start task=<task> schedulingEnabled=false
event=cron_task_complete task=<task> status=success
```

Manual execution:

```powershell
kubectl create job -n finrisk --from=cronjob/market-data market-data-check
kubectl create job -n finrisk --from=cronjob/document-collection document-collection-check
kubectl create job -n finrisk --from=cronjob/risk-recalculation risk-recalculation-check
```

Market collection includes only STOCK and REIT assets and requests a configurable ten-calendar-day range. This tolerates weekends and common multi-day market closures without adding an exchange-calendar subsystem. Repeated ranges depend on the existing idempotent market-price persistence path.

## HPA and rollout validation

Backend requests `200m` CPU, which gives the HPA a defined utilization denominator. The completion criterion is a visible `1 -> 2 -> 1` Backend scale cycle with a non-unknown CPU metric.

```powershell
./infra/k8s/scripts/verify.ps1 -RunBackendLoadTest
kubectl get hpa backend -n finrisk -w
```

The verification script waits for scale-out, removes the temporary load Pod, and then waits for scale-down. The full cycle can take several minutes because the HPA deliberately uses a five-minute scale-down stabilization window.

Backend uses `maxSurge: 1`, `maxUnavailable: 0`. Worker uses `maxSurge: 0`, `maxUnavailable: 1` and has no HPA. Kafka topics currently use one partition, so additional consumer replicas would not imply additional throughput even after scheduler locking is introduced.

## Recovery and monitoring checks

```powershell
kubectl delete pod -n finrisk -l app.kubernetes.io/name=backend
kubectl get pods -n finrisk -w
kubectl rollout restart deployment/backend -n finrisk
kubectl rollout status deployment/backend -n finrisk
kubectl top pods -n finrisk
```

Prometheus should return data for `up`, `jvm_memory_used_bytes`, `http_server_requests_seconds_count`, `kafka_consumergroup_lag`, `redis_memory_used_bytes`, `kube_pod_status_ready`, and `kube_horizontalpodautoscaler_status_current_replicas`.

## Resource budget

Steady-state requests are approximately 1 GiB for application Pods and 0.8 GiB for monitoring. Compose dependencies are capped at approximately 1.75 GiB. Including k3s and Docker overhead, steady state is expected near 6-7 GiB and transient Backend rollout/HPA/Cron activity near 9-10 GiB. Do not combine image builds, Backend load testing, and other memory-heavy local workloads.

## Cleanup

```powershell
./infra/k8s/scripts/destroy.ps1
```

This deletes only the `finrisk-day20` k3d cluster and stops the four Compose dependencies. Existing Compose data volumes and all AWS resources remain intact.

## Deferred work

- distributed locks for every scheduled Worker task
- role-specific Worker Deployments and listener activation
- Worker HPA and Kafka partition/concurrency expansion
- exchange-specific holiday calendars
- TLS, cert-manager, Alertmanager, NetworkPolicy, GitOps, and EKS
