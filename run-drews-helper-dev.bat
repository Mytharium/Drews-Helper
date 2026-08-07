@echo off
setlocal

cd /d "%~dp0"
echo Starting Drew's Helper dev client...
call gradlew.bat --console=plain run
