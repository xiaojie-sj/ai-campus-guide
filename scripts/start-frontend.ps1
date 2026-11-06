#Requires -Version 5.1
<#
.SYNOPSIS
  独立启动前端 Vite dev server；缺少 node_modules 时自动 npm ci。
.EXAMPLE
  .\scripts\start-frontend.ps1
  .\scripts\start-frontend.ps1 -Port 5174 -ApiTarget http://localhost:9080
#>
param(
    [int]$Port = 5173,
    [string]$ApiTarget = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontend = Join-Path $root 'frontend'
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules'))) {
    Write-Output '==> 未检测到 node_modules，执行 npm ci（约 1 分钟）'
    Push-Location $frontend
    try {
        npm ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    } finally { Pop-Location }
}

if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
    Write-Output ("前端已在端口 {0} 运行，跳过启动。" -f $Port)
    exit 0
}

$env:VITE_API_PROXY_TARGET = $ApiTarget
Write-Output ("==> 启动前端 :{0}，/api 代理到 {1}" -f $Port, $ApiTarget)
Start-Process -FilePath (Get-Command npm.cmd).Source -ArgumentList 'run', 'dev', '--', '--host', '0.0.0.0', '--port', "$Port" -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'frontend.out.log') -RedirectStandardError (Join-Path $runtime 'frontend.err.log')

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    try {
        $null = Invoke-WebRequest -Uri ("http://127.0.0.1:{0}" -f $Port) -UseBasicParsing -TimeoutSec 3
        Write-Output ("前端就绪: http://127.0.0.1:{0}" -f $Port)
        exit 0
    } catch { }
}
throw '前端 60 秒内未就绪，请查看 .runtime\frontend.err.log'
