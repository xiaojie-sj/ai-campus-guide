param(
    [int]$Port = 55432
)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$pgHome = Join-Path $env:LOCALAPPDATA "CampusGuide\postgresql14"
$pgCtl = Join-Path $pgHome "bin\pg_ctl.exe"
$dataDir = Join-Path $projectRoot ".runtime\pgdata"
$logFile = Join-Path $projectRoot ".runtime\postgres.log"

if (-not (Test-Path -LiteralPath $pgCtl)) {
    throw "Portable PostgreSQL is not installed at $pgHome"
}
if (-not (Test-Path -LiteralPath $dataDir)) {
    throw "PostgreSQL data directory is missing at $dataDir"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Output "PostgreSQL is already listening on port $Port."
    exit 0
}

& $pgCtl -D $dataDir -l $logFile -o "-p $Port" start
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL failed to start. See $logFile"
}

Write-Output "PostgreSQL with PostGIS, pgRouting and pgvector is running on port $Port."
