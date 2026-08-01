[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$Region = "ap-northeast-2",

    [Parameter()]
    [string]$AwsProfile = "",

    [Parameter()]
    [ValidatePattern("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")]
    [string]$RepositoryPrefix = "finrisk",

    [Parameter()]
    [string]$TossWidgetClientKey = "",

    [Parameter()]
    [switch]$IncludeMilestoneTag,

    [Parameter()]
    [switch]$PushLatest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$awsGlobalArgs = @()
if ($AwsProfile) {
    $awsGlobalArgs += @("--profile", $AwsProfile)
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)]
        [string]$Command,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

function Test-EcrTagExists {
    param(
        [Parameter(Mandatory)]
        [string]$RepositoryName,

        [Parameter(Mandatory)]
        [string]$Tag
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & aws @awsGlobalArgs ecr describe-images `
            --region $Region `
            --repository-name $RepositoryName `
            --image-ids "imageTag=$Tag" `
            --query "imageDetails[0].imageDigest" `
            --output text 1>$null 2>$null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Push-ImmutableTag {
    param(
        [Parameter(Mandatory)]
        [string]$LocalImage,

        [Parameter(Mandatory)]
        [string]$RepositoryName,

        [Parameter(Mandatory)]
        [string]$RepositoryUrl,

        [Parameter(Mandatory)]
        [string]$Tag
    )

    if (Test-EcrTagExists -RepositoryName $RepositoryName -Tag $Tag) {
        Write-Host "Skipping existing immutable tag: $RepositoryName`:$Tag"
        return
    }

    $remoteImage = "${RepositoryUrl}:$Tag"
    Invoke-CheckedCommand -Command "docker" -Arguments @("tag", $LocalImage, $remoteImage)
    Invoke-CheckedCommand -Command "docker" -Arguments @("push", $remoteImage)
}

Push-Location $repoRoot
try {
    Invoke-CheckedCommand -Command "git" -Arguments @("rev-parse", "--is-inside-work-tree")

    $dirtyFiles = & git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the Git worktree."
    }
    if ($dirtyFiles) {
        throw "The Git worktree must be clean so the image SHA tag identifies the exact source state."
    }

    $gitSha = (& git rev-parse --short=12 HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $gitSha) {
        throw "Unable to determine the current Git commit SHA."
    }

    $shaTag = "sha-$gitSha"
    $milestoneTag = "day17-$gitSha"
    $backendRepository = "$RepositoryPrefix-backend"
    $frontendRepository = "$RepositoryPrefix-frontend"

    $accountId = (& aws @awsGlobalArgs sts get-caller-identity --query Account --output text).Trim()
    if ($LASTEXITCODE -ne 0 -or $accountId -notmatch "^\d{12}$") {
        throw "Unable to determine the AWS account ID."
    }

    $registry = "$accountId.dkr.ecr.$Region.amazonaws.com"
    $backendUrl = "$registry/$backendRepository"
    $frontendUrl = "$registry/$frontendRepository"

    foreach ($repository in @($backendRepository, $frontendRepository)) {
        Invoke-CheckedCommand -Command "aws" -Arguments @(
            $awsGlobalArgs
            "ecr", "describe-repositories",
            "--region", $Region,
            "--repository-names", $repository
        )
    }

    $loginPassword = & aws @awsGlobalArgs ecr get-login-password --region $Region
    if ($LASTEXITCODE -ne 0 -or -not $loginPassword) {
        throw "Unable to obtain an ECR login password."
    }
    $loginPassword | docker login --username AWS --password-stdin $registry
    if ($LASTEXITCODE -ne 0) {
        throw "Docker login to ECR failed."
    }

    $backendLocalImage = "finrisk-backend:$shaTag"
    $frontendLocalImage = "finrisk-frontend:$shaTag"

    Invoke-CheckedCommand -Command "docker" -Arguments @(
        "build",
        "--provenance=false",
        "--file", "backend/Dockerfile",
        "--tag", $backendLocalImage,
        "backend"
    )

    Invoke-CheckedCommand -Command "docker" -Arguments @(
        "build",
        "--provenance=false",
        "--file", "frontend/Dockerfile",
        "--tag", $frontendLocalImage,
        "--build-arg", "NEXT_PUBLIC_API_BASE_URL=",
        "--build-arg", "NEXT_PUBLIC_OAUTH_BASE_URL=",
        "--build-arg", "NEXT_PUBLIC_TOSS_CLIENT_KEY=$TossWidgetClientKey",
        "frontend"
    )

    Push-ImmutableTag `
        -LocalImage $backendLocalImage `
        -RepositoryName $backendRepository `
        -RepositoryUrl $backendUrl `
        -Tag $shaTag
    Push-ImmutableTag `
        -LocalImage $frontendLocalImage `
        -RepositoryName $frontendRepository `
        -RepositoryUrl $frontendUrl `
        -Tag $shaTag

    if ($IncludeMilestoneTag) {
        Push-ImmutableTag `
            -LocalImage $backendLocalImage `
            -RepositoryName $backendRepository `
            -RepositoryUrl $backendUrl `
            -Tag $milestoneTag
        Push-ImmutableTag `
            -LocalImage $frontendLocalImage `
            -RepositoryName $frontendRepository `
            -RepositoryUrl $frontendUrl `
            -Tag $milestoneTag
    }

    if ($PushLatest) {
        foreach ($image in @(
            @{ Local = $backendLocalImage; Remote = "${backendUrl}:latest" },
            @{ Local = $frontendLocalImage; Remote = "${frontendUrl}:latest" }
        )) {
            Invoke-CheckedCommand -Command "docker" -Arguments @("tag", $image.Local, $image.Remote)
            Invoke-CheckedCommand -Command "docker" -Arguments @("push", $image.Remote)
        }
    }

    Write-Host ""
    Write-Host "ECR push completed."
    Write-Host "Backend: $backendUrl"
    Write-Host "Frontend: $frontendUrl"
    Write-Host "Deployment tag: $shaTag"
    if ($IncludeMilestoneTag) {
        Write-Host "Milestone tag: $milestoneTag"
    }
    if ($PushLatest) {
        Write-Host "Mutable tag: latest"
    }
}
finally {
    Pop-Location
}
