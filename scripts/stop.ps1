#Requires -Version 5.1
<#
.SYNOPSIS
  停止独立运行的前端 / 后端进程；默认保留 PostgreSQL。
.EXAMPLE
  .\scripts\stop.ps1
  .\scripts\stop.ps1 -IncludeDatabase
#>
param([switch]$IncludeDatabase)

function Stop-ByPort {
    param([int]$Port, [string]$Label)
    $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Output ("{0} :{1} 未在运行" -f $Label, $Port)
        return
    }
    $conns | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        Write-Output ("已停止 {0}（PID {1}，端口 {2}）" -f $Label, $_, $Port)
    }
}

Stop-ByPort -Port 5173 -Label '前端 Vite'
Stop-ByPort -Port 8080 -Label '后端 Spring Boot'
if ($IncludeDatabase) { Stop-ByPort -Port 55432 -Label 'PostgreSQL' }
