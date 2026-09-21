# MakayCleaner — VERSION.txt ile build.gradle senkron kontrolu
# Cikti: VERSION|CODE   ornek: 1.0.43|10043

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "VERSION.txt"

if (-not (Test-Path $versionFile)) {
    Write-Error "VERSION.txt yok"
    exit 1
}

$v = (Get-Content -Path $versionFile -Raw).Trim()
if ($v -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
    Write-Error "Gecersiz surum: '$v'"
    exit 1
}

$code = ([int]$Matches[1] * 10000) + ([int]$Matches[2] * 100) + [int]$Matches[3]
Write-Output "$v|$code"
