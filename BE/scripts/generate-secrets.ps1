# Generate-secrets.ps1
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\generate-secrets.ps1

$ErrorActionPreference = "Stop"

$EnvFile = Join-Path $PSScriptRoot "..\.env"

if (Test-Path $EnvFile) {
    Write-Host ".env already exists. Refusing to overwrite." -ForegroundColor Red
    exit 1
}

$ExampleFile = Join-Path $PSScriptRoot "..\.env.example"
if (-not (Test-Path $ExampleFile)) {
    Write-Host ".env.example not found in BE folder." -ForegroundColor Red
    exit 1
}

Copy-Item $ExampleFile $EnvFile

function Set-EnvValue {
    param([string]$Key, [string]$Value)
    $content = Get-Content $EnvFile
    $pattern = "^${Key}=.*$"
    $replacement = "${Key}=${Value}"
    $found = $false
    $newContent = foreach ($line in $content) {
        if ($line -match $pattern) {
            $found = $true
            $replacement
        } else {
            $line
        }
    }
    if (-not $found) {
        $newContent += $replacement
    }
    $newContent | Set-Content $EnvFile
}

function New-SecretHex { param([int]$Bytes) (1..$Bytes | ForEach-Object { '{0:x2}' -f (Get-Random -Maximum 256) }) -join '' }
function New-SecretBase64 { param([int]$Bytes) [Convert]::ToBase64String((1..$Bytes | ForEach-Object { Get-Random -Maximum 256 })) }

Set-EnvValue "JWT_SECRET" (New-SecretHex 32)
Set-EnvValue "JWT_REFRESH_SECRET" (New-SecretHex 32)
Set-EnvValue "AUDIO_AES_MASTER_KEY" (New-SecretHex 16)
Set-EnvValue "IAM_OUTBOX_ENCRYPTION_KEY" (New-SecretBase64 32)
Set-EnvValue "AUDIO_STREAM_COOKIE_SECRET" (New-SecretBase64 32)
Set-EnvValue "AUDIO_IP_HASH_SALT" (New-SecretBase64 32)
Set-EnvValue "REDIS_PASSWORD" (New-SecretBase64 24)
Set-EnvValue "MINIO_ACCESS_KEY" "pwb-minio-admin"
Set-EnvValue "MINIO_SECRET_KEY" (New-SecretBase64 32)
Set-EnvValue "SEED_ADMIN_PASSWORD" (New-SecretBase64 18)
Set-EnvValue "SEED_USER_PASSWORD" (New-SecretBase64 18)
Set-EnvValue "SEED_ARTIST_PASSWORD" (New-SecretBase64 18)

Write-Host "Generated .env with random secrets." -ForegroundColor Green