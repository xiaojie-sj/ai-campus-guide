$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $projectRoot "backend"
$frontendDir = Join-Path $projectRoot "frontend"
$runtimeDir = Join-Path $projectRoot ".runtime"
$jarFile = Join-Path $backendDir "target\campus-guide-1.0.0.jar"

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
& (Join-Path $backendDir "scripts\start-postgres.ps1")

if (-not (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)) {
    if (-not (Test-Path -LiteralPath $jarFile)) {
        throw "Backend jar is missing. Run backend\mvnw.cmd package -DskipTests first."
    }
    $java = (Get-Command java.exe).Source
    Start-Process -FilePath $java `
        -ArgumentList "-jar", $jarFile, "--spring.profiles.active=postgres" `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput (Join-Path $runtimeDir "backend.out.log") `
        -RedirectStandardError (Join-Path $runtimeDir "backend.err.log") `
        -WindowStyle Hidden
}

if (-not (Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue)) {
    $npm = (Get-Command npm.cmd).Source
    Start-Process -FilePath $npm `
        -ArgumentList "run", "dev", "--", "--host", "0.0.0.0" `
        -WorkingDirectory $frontendDir `
        -RedirectStandardOutput (Join-Path $runtimeDir "frontend.out.log") `
        -RedirectStandardError (Join-Path $runtimeDir "frontend.err.log") `
        -WindowStyle Hidden
}

Write-Output "Campus guide is starting with PostgreSQL, PostGIS, pgRouting and pgvector."
Write-Output "Frontend: http://localhost:5173"
Write-Output "Backend:  http://localhost:8080"
