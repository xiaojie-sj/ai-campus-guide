#Requires -Version 5.1
<#
.SYNOPSIS
  独立启动后端：按需启动 PostgreSQL、按需构建 jar、后台运行 Spring Boot。
.EXAMPLE
  .\scripts\start-backend.ps1
  .\scripts\start-backend.ps1 -Port 9080 -Rebuild
  .\scripts\start-backend.ps1 -SkipDatabase
#>
param(
    [int]$Port = 8080,
    [switch]$Rebuild,
    [switch]$SkipDatabase
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $root 'backend'
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

# JDK：优先使用仓库验证过的 Corretto 17
$corretto = Join-Path $env:USERPROFILE '.jdks\corretto-17.0.21'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $corretto)) { $env:JAVA_HOME = $corretto }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }

if (-not $SkipDatabase) {
    & (Join-Path $backend 'scripts\start-postgres.ps1')
}

$jar = Join-Path $backend 'target\campus-guide-1.0.0.jar'
if ($Rebuild -or -not (Test-Path -LiteralPath $jar)) {
    Write-Output '==> 构建后端 jar（首次启动或 -Rebuild）'
    Push-Location $backend
    try {
        & .\mvnw.cmd -B -q clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }
    } finally { Pop-Location }
}

if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
    Write-Output ("后端已在端口 {0} 运行，跳过启动。" -f $Port)
    exit 0
}

Write-Output ("==> 启动后端 :{0}" -f $Port)
Start-Process -FilePath $java -ArgumentList '-jar', $jar, ("--server.port={0}" -f $Port) -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'backend.out.log') -RedirectStandardError (Join-Path $runtime 'backend.err.log')

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    try {
        $health = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/actuator/health" -f $Port) -TimeoutSec 3
        if ($health.status -eq 'UP') {
            Write-Output ("后端就绪: http://127.0.0.1:{0}   接口文档: http://127.0.0.1:{0}/doc.html" -f $Port)
            exit 0
        }
    } catch { }
}
throw '后端 60 秒内未就绪，请查看 .runtime\backend.err.log'
