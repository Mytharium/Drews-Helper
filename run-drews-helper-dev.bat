@echo off
setlocal

cd /d "%~dp0"
call gradlew.bat --console=plain run
exit /b %ERRORLEVEL%
