param(
  [string]$SecretFile = (Join-Path $PSScriptRoot "..\secrets\.env.k3s")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$k8sRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRoot = (Resolve-Path (Join-Path $k8sRoot "..\..")).Path
$clusterConfig = Join-Path $k8sRoot "k3d\cluster-config.yaml"
$composeBase = Join-Path $repoRoot "infra\docker\docker-compose.local.yml"
$composeOverride = Join-Path $k8sRoot "k3d\docker-compose.k3d-dependencies.yml"

foreach ($tool in @("docker", "kubectl", "k3d", "helm")) {
  if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
    throw "$tool is required. Day20 pins k3d v5.9.0 and requires Helm 3."
  }
}
if (-not (Test-Path -LiteralPath $SecretFile)) {
  throw "Create $SecretFile from infra/k8s/secrets/app.env.example first."
}

docker compose --env-file $SecretFile -f $composeBase -f $composeOverride up -d postgres redis zookeeper kafka
if ($LASTEXITCODE -ne 0) { throw "Day20 Compose dependencies failed to start." }

$existing = k3d cluster list -o json | ConvertFrom-Json | Where-Object { $_.name -eq "finrisk-day20" }
if (-not $existing) {
  k3d cluster create --config $clusterConfig
  if ($LASTEXITCODE -ne 0) { throw "k3d cluster creation failed." }
}

kubectl wait --for=condition=Ready node --all --timeout=180s
kubectl wait --namespace kube-system --for=condition=Available deployment/coredns deployment/metrics-server --timeout=180s
kubectl get deployment -n kube-system | Select-String -Pattern "traefik"
Write-Output "Day20 prerequisites are ready. Run build-and-import.ps1 next."
