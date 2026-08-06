param(
  [string]$SecretFile = (Join-Path $PSScriptRoot "..\secrets\.env.k3s")
)

$ErrorActionPreference = "Stop"
$k8sRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRoot = (Resolve-Path (Join-Path $k8sRoot "..\..")).Path

k3d cluster delete finrisk-day20
docker compose --env-file $SecretFile `
  -f (Join-Path $repoRoot "infra\docker\docker-compose.local.yml") `
  -f (Join-Path $k8sRoot "k3d\docker-compose.k3d-dependencies.yml") `
  stop postgres redis zookeeper kafka
Write-Output "The Day20 k3d cluster was deleted. Compose data volumes were retained."
