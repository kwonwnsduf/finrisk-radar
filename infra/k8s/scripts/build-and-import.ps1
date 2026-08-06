$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$k8sRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRoot = (Resolve-Path (Join-Path $k8sRoot "..\..")).Path

docker build -t finrisk/backend:day20 (Join-Path $repoRoot "backend")
if ($LASTEXITCODE -ne 0) { throw "Backend image build failed." }

docker build `
  --build-arg "NEXT_PUBLIC_API_BASE_URL=" `
  --build-arg "NEXT_PUBLIC_OAUTH_BASE_URL=" `
  --build-arg "BACKEND_API_URL=http://backend:8080" `
  -t finrisk/frontend:day20 `
  (Join-Path $repoRoot "frontend")
if ($LASTEXITCODE -ne 0) { throw "Frontend image build failed." }

k3d image import -c finrisk-day20 finrisk/backend:day20 finrisk/frontend:day20
if ($LASTEXITCODE -ne 0) { throw "k3d image import failed." }
Write-Output "Day20 images were built and imported."
