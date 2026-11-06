#Requires -Version 5.1
<#
.SYNOPSIS
  按课程归档要求打包提交材料：班级+姓名+(项目名).zip
.DESCRIPTION
  归档内容包含：系统需求分析 / 系统数据库脚本 / 系统源代码 / 答辩PPT。
  前端自动排除 node_modules 与 dist，后端排除 target 等构建产物。
.EXAMPLE
  .\scripts\package-submission.ps1 -Class "1班" -Name "张三"
  .\scripts\package-submission.ps1 -Class "1班" -Name "张三" -Project "AI数字人校园导览系统" -OutDir "$HOME\Desktop"
#>
param(
    [Parameter(Mandatory = $true)][string]$Class,
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Project = 'AI数字人校园导览系统',
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $OutDir) { $OutDir = Join-Path $root 'submission' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$zipName = '{0}{1}({2}).zip' -f $Class, $Name, $Project
$stage = Join-Path $root ('submission\_staging\' + [IO.Path]::GetFileNameWithoutExtension($zipName))
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage | Out-Null

Write-Output '==> 01 系统需求分析'
New-Item -ItemType Directory -Force -Path (Join-Path $stage '01_系统需求分析') | Out-Null
Copy-Item (Join-Path $root 'docs\01_requirements\*') -Destination (Join-Path $stage '01_系统需求分析') -Force

Write-Output '==> 02 系统数据库脚本'
New-Item -ItemType Directory -Force -Path (Join-Path $stage '02_系统数据库脚本') | Out-Null
Copy-Item (Join-Path $root 'backend\src\main\resources\db\migration\*.sql') -Destination (Join-Path $stage '02_系统数据库脚本') -Force
$note = @"
数据库脚本说明
==============
- 唯一迁移链：Flyway V1-V8，按版本号顺序在 PostgreSQL 上执行（后端启动时自动执行）。
- V5 起依赖扩展：postgis / pgrouting / vector，建库后先执行：
    CREATE EXTENSION postgis; CREATE EXTENSION pgrouting; CREATE EXTENSION vector;
- 禁止手工修改已执行过的历史脚本；新变更请追加更高版本号的迁移。
"@
$note | Set-Content -LiteralPath (Join-Path $stage '02_系统数据库脚本\README.txt') -Encoding UTF8

Write-Output '==> 03 系统源代码'
$src = Join-Path $stage '03_系统源代码'
New-Item -ItemType Directory -Force -Path $src | Out-Null
robocopy (Join-Path $root 'frontend') (Join-Path $src 'frontend') /E /NFL /NDL /NJH /NJS /XD node_modules dist dist-ssr .vite | Out-Null
robocopy (Join-Path $root 'backend') (Join-Path $src 'backend') /E /NFL /NDL /NJH /NJS /XD target data logs .idea | Out-Null
robocopy (Join-Path $root 'tools') (Join-Path $src 'tools') /E /NFL /NDL /NJH /NJS /XD __pycache__ analysis | Out-Null
robocopy (Join-Path $root 'scripts') (Join-Path $src 'scripts') /E /NFL /NDL /NJH /NJS | Out-Null
foreach ($f in @('README.md', '.gitignore', '.editorconfig')) {
    Copy-Item (Join-Path $root $f) -Destination $src -Force -ErrorAction SilentlyContinue
}

Write-Output '==> 04 答辩PPT'
$pptDir = Join-Path $stage '04_答辩PPT'
New-Item -ItemType Directory -Force -Path $pptDir | Out-Null
$existing = Get-ChildItem (Join-Path $root 'submission') -Filter *.pptx -ErrorAction SilentlyContinue | Select-Object -First 1
if ($existing) {
    Copy-Item $existing.FullName -Destination $pptDir -Force
} else {
    $pptNote = @"
答辩 PPT 待生成。
生成后请将 .pptx 放入本目录（submission\），重新运行 package-submission.ps1 即可自动纳入压缩包。
"@
    $pptNote | Set-Content -LiteralPath (Join-Path $pptDir 'README.txt') -Encoding UTF8
}

Write-Output ('==> 压缩为 ' + $zipName)
$zipPath = Join-Path $OutDir $zipName
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
Compress-Archive -LiteralPath $stage -DestinationPath $zipPath -CompressionLevel Optimal
Remove-Item -LiteralPath $stage -Recurse -Force

$size = [math]::Round((Get-Item -LiteralPath $zipPath).Length / 1MB, 2)
Write-Output ('完成: {0}  ({1} MB)' -f $zipPath, $size)
Write-Output '归档清单:'
Write-Output '  01_系统需求分析   <- docs/01_requirements'
Write-Output '  02_系统数据库脚本 <- backend Flyway V1-V8'
Write-Output '  03_系统源代码     <- frontend(无 node_modules) + backend(无 target) + tools + scripts'
Write-Output '  04_答辩PPT        <- submission/*.pptx（若已生成）'
