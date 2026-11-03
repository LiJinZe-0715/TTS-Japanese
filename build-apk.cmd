@echo off
rem One-click release build: double-click this file, or run build-apk from cmd/PowerShell.
rem ASCII only on purpose - cmd misparses multi-byte text in batch files. The real work and
rem all messages live in scripts\build-apk.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-apk.ps1"
set "STATUS=%ERRORLEVEL%"
rem Keep the window open when started by double-click, so the result can be read.
echo %cmdcmdline% | findstr /i /c:"%~nx0" >nul && pause
exit /b %STATUS%
