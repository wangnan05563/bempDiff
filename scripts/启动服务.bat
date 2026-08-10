@echo off
chcp 65001 >nul 2>&1
setlocal
cd /d "%~dp0.."

echo ============================================
echo   BempDiff Start
echo ============================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-app.ps1" %*

if errorlevel 1 (
    echo.
    echo [ERROR] Start failed, see output above
    echo.
    pause
    exit /b 1
)

echo.
pause
exit /b 0
