#Requires -Version 5.1
<#
.SYNOPSIS
  一键启动全部（数据库 + 后端 + 前端），等价于依次调用两个独立启动脚本。
.EXAMPLE
  .\scripts\start-all.ps1
#>
& (Join-Path $PSScriptRoot 'start-backend.ps1')
& (Join-Path $PSScriptRoot 'start-frontend.ps1')
