@echo off
setlocal EnableExtensions
cd /d "%~dp0\..\..\bempdiff"

echo ============================================
echo   BempDiff Build (Electron + Web UI + Java sidecar)
echo ============================================
echo Steps: check java artifacts -^> install builder -^> build webui -^> electron-builder [NSIS]
echo Output: release\BempDiff-*-setup.exe  (项目根 18_comparePakage\release)
echo.

REM ---------- 0. Prerequisite: Java backend artifacts ----------
if not exist "dist_input\jre\bin\javaw.exe" (
  echo [ERROR] dist_input\jre not found. Build the Java backend (javac/jar/cfr/jlink) first.
  pause
  exit /b 1
)
if not exist "dist_input\app\cfr.jar" (
  echo [ERROR] dist_input\app\cfr.jar not found. Build the Java backend first.
  pause
  exit /b 1
)
if not exist "dist_input\classes" if not exist "dist_input\app\bempdiff.jar" (
  echo [ERROR] No backend artifact (dist_input\classes or dist_input\app\bempdiff.jar). Build the Java backend first.
  pause
  exit /b 1
)
echo [OK] Java backend artifacts present.

REM ---------- 1. Install electron-builder (here, in bempdiff/) ----------
echo [STEP] Installing electron-builder ...
call npm install --no-audit --no-fund
if errorlevel 1 (
  echo [ERROR] npm install failed.
  pause
  exit /b 1
)

REM ---------- 2. Ensure dev-shell Electron runtime is installed ----------
if not exist "dev-shell\node_modules\.bin\electron.cmd" (
  echo [STEP] Installing Electron runtime into dev-shell ...
  pushd dev-shell
  call npm install --no-audit --no-fund
  popd
  if errorlevel 1 (
    echo [ERROR] electron install failed.
    pause
    exit /b 1
  )
) else (
  echo [OK] dev-shell Electron runtime present.
)

REM ---------- 3. Build webui (produces webui/dist served by backend --webroot) ----------
echo [STEP] Building webui (vite build) ...
pushd webui
call npm install --no-audit --no-fund
if errorlevel 1 (
  echo [ERROR] webui npm install failed.
  pause
  exit /b 1
)
call npm run build
if errorlevel 1 (
  echo [ERROR] webui build failed.
  pause
  exit /b 1
)
popd
echo [OK] webui/dist ready.

REM ---------- 4. Binary mirrors (avoid corporate artifactory block for NSIS/electron) ----------
set "ELECTRON_BUILDER_BINARIES_MIRROR=https://registry.npmmirror.com/-/binary/electron-builder-binaries/"
set "ELECTRON_MIRROR=https://registry.npmmirror.com/-/binary/electron/"

REM ---------- 5. Package with electron-builder (NSIS) ----------
echo [STEP] Running electron-builder (NSIS) ...
call npm run dist
if errorlevel 1 (
  echo [ERROR] electron-builder failed, see output above.
  pause
  exit /b 1
)

echo.
echo [DONE] Build complete.
echo   Installer: release\BempDiff-*-setup.exe  (项目根 18_comparePakage\release)
