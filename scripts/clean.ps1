# Deletes everything the build and scripts\fetch-assets.mjs produce, leaving the repository as
# it is after a fresh clone. clean.cmd in the repo root is the double-click entry point.
#
#   -KeepAssets  keep the ~300 MB of downloaded binaries and voice data (.cache\, jniLibs\,
#                assets\), so the next build does not fetch them again
#   -DryRun      only list what would be deleted
#
# IDE settings (.idea\, *.iml), local.properties and signing keys (*.jks) are ignored by git
# too, but they are the user's configuration rather than build output, so they stay.
#
# Saved as UTF-8 *with* BOM and written for Windows PowerShell 5.1, like build-apk.ps1.

param(
    [switch]$KeepAssets,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Build output: Gradle, CMake, and the APK build-apk.cmd copies to the root.
$targets = @(
    'build',
    '.gradle',
    'app\build',
    'app\.cxx',
    'domain\build',
    'application\build',
    'VoiceVox_TTS.apk'
)
# Downloaded by scripts\fetch-assets.mjs; see .gitignore.
if (-not $KeepAssets) {
    $targets += @(
        '.cache',
        'app\src\main\jniLibs',
        'app\src\main\assets\vvm',
        'app\src\main\assets\open_jtalk_dict.zip'
    )
}

function Get-Size([string]$path) {
    $item = Get-Item -LiteralPath $path -Force
    if (-not $item.PSIsContainer) { return $item.Length }
    $sum = (Get-ChildItem -LiteralPath $path -Recurse -Force -File -ErrorAction SilentlyContinue |
            Measure-Object -Property Length -Sum).Sum
    if ($sum) { return [long]$sum }
    return 0
}

if ($DryRun) {
    Write-Host '=== VOICEVOX TTS 清理（只列出，不删除）===' -ForegroundColor Cyan
} else {
    Write-Host '=== VOICEVOX TTS 清理构建产物 ===' -ForegroundColor Cyan
}

$total = 0
$failed = New-Object System.Collections.Generic.List[string]
foreach ($target in $targets) {
    $path = Join-Path $root $target
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $size = Get-Size $path
    $total += $size
    $label = '{0,10:N1} MB  {1}' -f ($size / 1MB), $target
    if ($DryRun) {
        Write-Host "  $label"
        continue
    }
    try {
        Remove-Item -LiteralPath $path -Recurse -Force
        Write-Host "  [已删除] $label"
    } catch {
        $failed.Add($target)
        Write-Host "  [失败]   $label  ($($_.Exception.Message))" -ForegroundColor Yellow
    }
}

# assets\ only ever holds downloaded files; drop it once they are gone.
$assets = Join-Path $root 'app\src\main\assets'
if (-not $DryRun -and (Test-Path $assets) -and -not (Get-ChildItem $assets -Force)) {
    Remove-Item $assets -Force
}

Write-Host ''
if ($total -eq 0) {
    Write-Host '没有需要清理的东西。' -ForegroundColor Green
} elseif ($DryRun) {
    Write-Host ('共 {0:N1} MB。去掉 -DryRun 即可删除。' -f ($total / 1MB)) -ForegroundColor Green
} elseif ($failed.Count -gt 0) {
    Write-Host ('有 {0} 项没删掉，多半是文件被占用。关掉 Android Studio / WebStorm，' -f $failed.Count) -ForegroundColor Red
    Write-Host '或运行 gradlew --stop 停掉 Gradle 守护进程，再重跑本脚本。' -ForegroundColor Red
    exit 1
} else {
    Write-Host ('=== 完成，释放 {0:N1} MB ===' -f ($total / 1MB)) -ForegroundColor Green
    if (-not $KeepAssets) {
        Write-Host '下次运行 build-apk.cmd 会重新下载核心、词典和内置声音（约 102 MB）。'
    }
}
exit 0
