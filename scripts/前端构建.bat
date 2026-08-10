@echo off
chcp 65001 >nul 2>&1
setlocal
cd /d "%~dp0.."

echo ============================================
echo   BempDiff Frontend (JavaFX UI) Build
echo ============================================
echo.
echo This compiles core + JavaFX UI into:
echo   prototype\javafx_ui\out
echo (no exe packaging; for quick UI iteration)
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-ui.ps1" %*

if errorlevel 1 (
    echo.
    echo [ERROR] Frontend build failed, see output above
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Frontend build complete!
echo ============================================
echo   Run from source: double-click prototype\javafx_ui\run_ui.bat
echo   Full package:     double-click scripts\????.bat
echo ============================================
echo.
pause
exit /b 0
