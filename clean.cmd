@echo off
rem Deletes all build output and downloaded binaries: double-click this file, or run
rem `clean -KeepAssets` / `clean -DryRun` from cmd/PowerShell. ASCII only on purpose - cmd
rem misparses multi-byte text in batch files. The real work lives in scripts\clean.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\clean.ps1" %*
set "STATUS=%ERRORLEVEL%"
rem Keep the window open when started by double-click, so the result can be read.
echo %cmdcmdline% | findstr /i /c:"%~nx0" >nul && pause
exit /b %STATUS%
