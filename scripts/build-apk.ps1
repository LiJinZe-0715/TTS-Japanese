# One-click release build for Windows; build-apk.cmd in the repo root is the double-click
# entry point. Finds a usable JDK and the Android SDK, downloads the binaries the repo does not
# carry (scripts\fetch-assets.mjs, skipped once done), builds the release APK and copies it to
# the repo root as VoiceVox_TTS.apk.
#
# Saved as UTF-8 *with* BOM on purpose: Windows PowerShell 5.1 reads a BOM-less script in the
# ANSI code page and would garble the Chinese messages below.
#
# Written for Windows PowerShell 5.1, which every Windows 10/11 has: no `&&`, `?:` or `??`.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail([string]$message) {
    Write-Host ''
    Write-Host "[错误] $message" -ForegroundColor Red
    exit 1
}

Write-Host '=== VOICEVOX TTS 一键出包 ===' -ForegroundColor Cyan

# ------------------------------------------------------------------ Node.js
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Fail '没找到 Node.js。请先安装 https://nodejs.org/ ，装完重新打开本脚本。'
}

# ------------------------------------------------------------------ JDK
# Gradle 9 / AGP 9 need JDK 17 or newer. 26+ is skipped: on this project's build machine a
# JDK 27 could not open the local socket the Gradle daemon talks over.
function Get-JdkMajor([string]$jdkHome) {
    $java = Join-Path $jdkHome 'bin\java.exe'
    if (-not (Test-Path $java) -or -not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) { return 0 }
    $line = (& cmd /c "`"$java`" -version 2>&1") | Select-String 'version' | Select-Object -First 1
    if (-not $line) { return 0 }
    if ($line.ToString() -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

$candidates = New-Object System.Collections.Generic.List[string]
if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
$candidates.Add("$env:ProgramFiles\Android\Android Studio\jbr")
foreach ($vendor in 'Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft') {
    $dir = Join-Path $env:ProgramFiles $vendor
    if (Test-Path $dir) {
        Get-ChildItem $dir -Directory | Sort-Object Name -Descending | ForEach-Object { $candidates.Add($_.FullName) }
    }
}
# JetBrains IDEs (WebStorm, IntelliJ, ...) bundle a full JDK as `jbr`, javac included: on a
# machine with only an IDE installed it is the one usable JDK.
foreach ($dir in "$env:ProgramFiles\JetBrains", "$env:LOCALAPPDATA\Programs") {
    if (Test-Path $dir) {
        Get-ChildItem $dir -Directory | Sort-Object Name -Descending | ForEach-Object { $candidates.Add((Join-Path $_.FullName 'jbr')) }
    }
}

$jdk = $null
$major = 0
foreach ($candidate in $candidates) {
    $major = Get-JdkMajor $candidate
    if ($major -ge 17 -and $major -le 25) { $jdk = $candidate; break }
}
if (-not $jdk) {
    Fail ("没找到 JDK 17~25。请安装 JDK 17，例如在 PowerShell 里运行：`n" +
          "       winget install EclipseAdoptium.Temurin.17.JDK`n" +
          "       装完重新打开本脚本；也可以把 JAVA_HOME 设成 JDK 的目录。")
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
Write-Host "[JDK] $jdk (版本 $major)"

# ------------------------------------------------------------------ Android SDK
$hasSdkDir = (Test-Path 'local.properties') -and (Select-String -Path 'local.properties' -Pattern '^sdk\.dir=' -Quiet)
if (-not $hasSdkDir -and -not $env:ANDROID_HOME) {
    foreach ($sdk in "$env:LOCALAPPDATA\Android\Sdk", 'C:\Android\sdk') {
        if (Test-Path (Join-Path $sdk 'platforms')) { $env:ANDROID_HOME = $sdk; break }
    }
    if (-not $env:ANDROID_HOME) {
        Fail '没找到 Android SDK。请安装 Android Studio，或把 ANDROID_HOME 设成 SDK 目录。'
    }
}
if ($env:ANDROID_HOME) { Write-Host "[SDK] $env:ANDROID_HOME" }

# ------------------------------------------------------------------ build
Write-Host ''
Write-Host '[1/2] 下载 VOICEVOX 核心、词典和内置声音（约 102 MB，下过就跳过）...' -ForegroundColor Cyan
& node scripts\fetch-assets.mjs
if ($LASTEXITCODE -ne 0) { Fail '资源下载失败，见上面的输出。网络不通时重跑即可，已下完的部分会跳过。' }

Write-Host ''
Write-Host '[2/2] 构建 release APK（第一次会下载 Gradle 和依赖，需要几分钟）...' -ForegroundColor Cyan
& "$root\gradlew.bat" assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { Fail 'Gradle 构建失败，见上面的输出。' }

$apk = Join-Path $root 'app\build\outputs\apk\release\VoiceVox_TTS.apk'
if (-not (Test-Path $apk)) { Fail "构建成功但没找到 $apk" }
Copy-Item $apk (Join-Path $root 'VoiceVox_TTS.apk') -Force

Write-Host ''
Write-Host "=== 完成：$root\VoiceVox_TTS.apk ===" -ForegroundColor Green
Write-Host '安装到手机：adb install -r VoiceVox_TTS.apk'
exit 0
