param([switch]$RunBackendLoadTest)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$k8sRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRoot = (Resolve-Path (Join-Path $k8sRoot "..\..")).Path
$composeBase = Join-Path $repoRoot "infra\docker\docker-compose.local.yml"
$composeOverride = Join-Path $k8sRoot "k3d\docker-compose.k3d-dependencies.yml"

function Assert-KafkaMessage([string]$Actual, [string]$Expected, [string]$Route) {
  if ($Actual -notmatch [regex]::Escape($Expected)) {
    throw "Kafka $Route produce/consume check failed."
  }
}

function Wait-BackendReplicaCount([int]$Expected, [int]$TimeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $replicas = kubectl get hpa backend -n finrisk -o jsonpath='{.status.currentReplicas}'
    if ([int]$replicas -eq $Expected) { return }
    Start-Sleep -Seconds 10
  }
  throw "Backend HPA did not reach $Expected replica(s) within $TimeoutSeconds seconds."
}

kubectl wait --namespace finrisk --for=condition=Available deployment/backend deployment/frontend deployment/worker --timeout=10m
kubectl wait --namespace monitoring --for=condition=Available deployment/kafka-exporter deployment/redis-exporter --timeout=5m

$hpa = kubectl get hpa backend -n finrisk -o json | ConvertFrom-Json
if ($hpa.status.currentMetrics.Count -eq 0) { throw "Backend HPA has no CPU metric yet." }
if ($hpa.spec.minReplicas -ne 1 -or $hpa.spec.maxReplicas -ne 2) { throw "Backend HPA bounds are incorrect." }
if (kubectl get hpa -n finrisk -o name | Select-String -Pattern "worker") { throw "Worker must not have an HPA." }

$worker = kubectl get deployment worker -n finrisk -o json | ConvertFrom-Json
if ($worker.spec.replicas -ne 1 -or $worker.spec.strategy.rollingUpdate.maxSurge -ne 0 -or $worker.spec.strategy.rollingUpdate.maxUnavailable -ne 1) {
  throw "Worker single-replica update policy is not enforced."
}

$frontend = Invoke-WebRequest -UseBasicParsing http://localhost/
$backend = Invoke-WebRequest -UseBasicParsing http://localhost/api/health
if ($frontend.StatusCode -ne 200 -or $backend.StatusCode -ne 200) { throw "Ingress verification failed." }
$actuatorStatus = 0
try { $actuatorStatus = (Invoke-WebRequest -UseBasicParsing http://localhost/actuator/prometheus).StatusCode } catch { $actuatorStatus = $_.Exception.Response.StatusCode.value__ }
if ($actuatorStatus -eq 200) { throw "Actuator metrics must not be exposed through Ingress." }

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$composeTopic = "day20-compose-$stamp"
docker compose -f $composeBase -f $composeOverride exec -T kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic $composeTopic --partitions 1 --replication-factor 1
"compose-route" | docker compose -f $composeBase -f $composeOverride exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic $composeTopic
$composeMessage = docker compose -f $composeBase -f $composeOverride exec -T kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic $composeTopic --from-beginning --max-messages 1 --timeout-ms 30000
Assert-KafkaMessage $composeMessage "compose-route" "Compose :9092"

$hostTopic = "day20-host-$stamp"
if (-not (Test-NetConnection -ComputerName localhost -Port 29092 -InformationLevel Quiet)) {
  throw "Windows host cannot reach Kafka on localhost:29092."
}
docker compose -f $composeBase -f $composeOverride exec -T kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic $hostTopic --partitions 1 --replication-factor 1
"host-route" | docker compose -f $composeBase -f $composeOverride exec -T kafka kafka-console-producer --bootstrap-server localhost:29092 --topic $hostTopic
$hostMessage = docker compose -f $composeBase -f $composeOverride exec -T kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic $hostTopic --from-beginning --max-messages 1 --timeout-ms 30000
Assert-KafkaMessage $hostMessage "host-route" "Windows host :29092"

kubectl delete pod kafka-k3d-smoke -n finrisk --ignore-not-found
$k3dTopic = "day20-k3d-$stamp"
$k3dCommand = "kafka-topics --bootstrap-server host.k3d.internal:39092 --create --if-not-exists --topic $k3dTopic --partitions 1 --replication-factor 1 && echo k3d-route | kafka-console-producer --bootstrap-server host.k3d.internal:39092 --topic $k3dTopic && kafka-console-consumer --bootstrap-server host.k3d.internal:39092 --topic $k3dTopic --from-beginning --max-messages 1 --timeout-ms 30000"
kubectl run kafka-k3d-smoke -n finrisk --image=confluentinc/cp-kafka:7.6.1 --restart=Never --command -- sh -c $k3dCommand
kubectl wait -n finrisk --for=jsonpath='{.status.phase}'=Succeeded pod/kafka-k3d-smoke --timeout=180s
$k3dMessage = kubectl logs -n finrisk pod/kafka-k3d-smoke
Assert-KafkaMessage $k3dMessage "k3d-route" "k3d :39092"

foreach ($cron in @("market-data", "document-collection", "risk-recalculation")) {
  $job = "${cron}-manual-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
  kubectl create job -n finrisk --from=cronjob/$cron $job
  kubectl wait -n finrisk --for=condition=Complete job/$job --timeout=15m
  $logs = kubectl logs -n finrisk job/$job
  if ($logs -notmatch "event=cron_task_start task=$cron schedulingEnabled=false" -or $logs -notmatch "event=cron_task_complete task=$cron status=success") {
    throw "$cron did not emit the required isolated execution logs."
  }
  if ($logs -match "Payment reconciliation|outbox|SubscriptionExpired|report.*recovery") {
    throw "$cron emitted evidence of an unrelated scheduler."
  }
}

if ($RunBackendLoadTest) {
  kubectl delete pod backend-load -n finrisk --ignore-not-found
  $load = 'for i in 1 2 3 4 5 6 7 8; do while true; do wget -q -O /dev/null http://backend:8080/api/health; done & done; wait'
  kubectl run backend-load -n finrisk --image=busybox:1.37.0 --restart=Never --command -- sh -c $load
  Wait-BackendReplicaCount 2 360
  kubectl delete pod backend-load -n finrisk --ignore-not-found
  Wait-BackendReplicaCount 1 720
  Write-Output "Backend HPA completed the required 1 -> 2 -> 1 scale cycle."
}

kubectl get podmonitor,servicemonitor -n monitoring
kubectl top pods -n finrisk
Write-Output "Verification passed. Use port-forward to inspect Prometheus and Grafana dashboards."
