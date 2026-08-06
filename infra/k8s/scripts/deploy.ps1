param(
  [string]$SecretFile = (Join-Path $PSScriptRoot "..\secrets\.env.k3s")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$chartVersion = "86.0.0"
$k8sRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if (-not (Test-Path -LiteralPath $SecretFile)) {
  throw "Create $SecretFile from infra/k8s/secrets/app.env.example first."
}

function Read-EnvValue([string]$Name) {
  $line = Get-Content -LiteralPath $SecretFile | Where-Object { $_ -match "^$([regex]::Escape($Name))=" } | Select-Object -Last 1
  if (-not $line) { throw "$Name is missing from $SecretFile" }
  return ($line -split "=", 2)[1]
}

kubectl apply -f (Join-Path $k8sRoot "base\namespace.yaml")
$appSecretFile = [IO.Path]::GetTempFileName()
try {
  $appSecretLines = @(Get-Content -LiteralPath $SecretFile | Where-Object { $_ -notmatch '^(GRAFANA_ADMIN_|NEXT_PUBLIC_)' })
  [IO.File]::WriteAllLines($appSecretFile, $appSecretLines, [Text.UTF8Encoding]::new($false))
  kubectl create secret generic finrisk-app-secrets -n finrisk --from-env-file=$appSecretFile --dry-run=client -o yaml | kubectl apply -f -
  if ($LASTEXITCODE -ne 0) { throw "Application Secret creation failed." }
} finally {
  Remove-Item -LiteralPath $appSecretFile -Force -ErrorAction SilentlyContinue
}

$grafanaUser = Read-EnvValue "GRAFANA_ADMIN_USER"
$grafanaPassword = Read-EnvValue "GRAFANA_ADMIN_PASSWORD"
$redisPassword = Read-EnvValue "REDIS_PASSWORD"
kubectl create secret generic grafana-admin -n monitoring --from-literal=admin-user=$grafanaUser --from-literal=admin-password=$grafanaPassword --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic finrisk-exporter-secrets -n monitoring --from-literal=redis-password=$redisPassword --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -k (Join-Path $k8sRoot "overlays\local")
if ($LASTEXITCODE -ne 0) { throw "Application manifests failed to apply." }

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
helm repo update
helm show chart prometheus-community/kube-prometheus-stack --version $chartVersion | Select-String -Pattern "version: 86.0.0|kubeVersion:"
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack `
  --namespace monitoring `
  --version $chartVersion `
  --values (Join-Path $k8sRoot "monitoring\kube-prometheus-stack-values.yaml") `
  --wait `
  --timeout 10m
if ($LASTEXITCODE -ne 0) { throw "kube-prometheus-stack installation failed." }

kubectl apply -k (Join-Path $k8sRoot "monitoring")
if ($LASTEXITCODE -ne 0) { throw "Monitoring resources failed to apply." }
kubectl rollout status deployment/backend -n finrisk --timeout=10m
kubectl rollout status deployment/frontend -n finrisk --timeout=5m
kubectl rollout status deployment/worker -n finrisk --timeout=10m
Write-Output "Day20 application and monitoring resources are deployed."
