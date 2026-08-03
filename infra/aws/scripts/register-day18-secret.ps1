[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet(
        "postgres_password",
        "redis_password",
        "jwt_secret",
        "google_client_secret",
        "toss_widget_secret_key",
        "dart_api_key",
        "naver_client_secret",
        "openai_api_key"
    )]
    [string]$Secret,

    [Parameter()]
    [string]$Region = "ap-northeast-2",

    [Parameter()]
    [string]$AwsProfile = "",

    [Parameter()]
    [switch]$ReuseLocalValue,

    [Parameter()]
    [string]$LocalEnvFile = ".env.local"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$definitions = @{
    postgres_password = @{
        Parameter = "/finrisk/day18/postgres/password"
        Env       = "POSTGRES_PASSWORD"
    }
    redis_password = @{
        Parameter = "/finrisk/day18/redis/password"
        Env       = "REDIS_PASSWORD"
    }
    jwt_secret = @{
        Parameter = "/finrisk/day18/jwt/secret"
        Env       = "JWT_SECRET"
    }
    google_client_secret = @{
        Parameter = "/finrisk/day18/google/client-secret"
        Env       = "GOOGLE_CLIENT_SECRET"
    }
    toss_widget_secret_key = @{
        Parameter = "/finrisk/day18/toss/widget-secret-key"
        Env       = "TOSS_WIDGET_SECRET_KEY"
    }
    dart_api_key = @{
        Parameter = "/finrisk/day18/dart/api-key"
        Env       = "DART_API_KEY"
    }
    naver_client_secret = @{
        Parameter = "/finrisk/day18/naver/client-secret"
        Env       = "NAVER_CLIENT_SECRET"
    }
    openai_api_key = @{
        Parameter = "/finrisk/day18/openai/api-key"
        Env       = "OPENAI_API_KEY"
    }
}

$definition = $definitions[$Secret]
$parameterName = $definition.Parameter
$environmentName = $definition.Env
$plainValue = $null

try {
    if ($ReuseLocalValue) {
        $resolvedEnvFile = (Resolve-Path -LiteralPath $LocalEnvFile).Path
        $line = Get-Content -LiteralPath $resolvedEnvFile |
            Where-Object { $_ -match "^$([regex]::Escape($environmentName))=" } |
            Select-Object -First 1
        if (-not $line) {
            throw "$environmentName is not present in $resolvedEnvFile."
        }

        $plainValue = ($line -split "=", 2)[1].Trim()
        if ([string]::IsNullOrWhiteSpace($plainValue) -or
            $plainValue -match "^(replace-|change-me|test_g[cs]k_docs_)") {
            throw "$environmentName is empty, a placeholder, or a documentation test value."
        }
    }
    else {
        $secureValue = Read-Host "Enter $environmentName for $parameterName" -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        try {
            $plainValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        }
        finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
    }

    if ([string]::IsNullOrWhiteSpace($plainValue)) {
        throw "The secret value must not be empty."
    }

    $awsArguments = @()
    if ($AwsProfile) {
        $awsArguments += @("--profile", $AwsProfile)
    }
    $awsArguments += @(
        "ssm", "put-parameter",
        "--region", $Region,
        "--name", $parameterName,
        "--type", "SecureString",
        "--tier", "Standard",
        "--value", $plainValue,
        "--overwrite"
    )

    & aws @awsArguments
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI failed to register $parameterName."
    }

    Write-Host "Registered SecureString: $parameterName"
}
finally {
    $plainValue = $null
}
