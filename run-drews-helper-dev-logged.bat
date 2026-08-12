@echo off
setlocal

rem Same as run-drews-helper-dev.bat, but tees the run to a file so a dev session's console
rem output survives the window closing. The plain launcher is deliberately left untouched: if
rem this one ever breaks, that one still works.
cd /d "%~dp0"
set "DREW_DEV_LOG=%USERPROFILE%\.runelite\drews-dev-run.log"
echo Logging this run to "%DREW_DEV_LOG%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { & '.\gradlew.bat' --console=plain run 2>&1 | Tee-Object -FilePath $env:DREW_DEV_LOG -Append; exit $LASTEXITCODE }"
exit /b %ERRORLEVEL%
