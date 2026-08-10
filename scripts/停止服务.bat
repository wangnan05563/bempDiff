@echo off
chcp 65001 >nul 2>&1
setlocal
cd /d "%~dp0.."

echo ============================================
echo   BempDiff Stop
echo ============================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-app.ps1" %*

if errorlevel 1 (
    echo.
    echo [ERROR] Stop failed, see output above
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Stop complete!
echo ============================================
echo.
pause
exit /b 0
