@echo off
chcp 65001 >nul 2>&1
setlocal
cd /d "%~dp0.."

echo ============================================
echo   BempDiff Build Package
echo ============================================
echo.
echo Build steps:
echo   1. Clean old build output
echo   2. Compile core + JavaFX UI (javac)
echo   3. Copy JavaFX runtime jars
echo   4. Build app.jar
echo   5. jpackage self-contained exe
echo.
echo Output: dist\BempDiff\BempDiff.exe
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1" %*

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed, see output above
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Build complete!
echo ============================================
echo   EXE:  dist\BempDiff\BempDiff.exe
echo   Next: double-click scripts\????.bat
echo ============================================
echo.
pause
exit /b 0
