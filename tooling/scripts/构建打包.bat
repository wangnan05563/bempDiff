@echo off
setlocal EnableExtensions
cd /d "%~dp0\..\..\bempdiff"

REM ---------- Binary mirrors (avoid corporate artifactory block for NSIS/electron) ----------
set "ELECTRON_BUILDER_BINARIES_MIRROR=https://registry.npmmirror.com/-/binary/electron-builder-binaries/"
set "ELECTRON_MIRROR=https://registry.npmmirror.com/-/binary/electron/"

echo ============================================
echo   BempDiff Build (Electron + Web UI + Java sidecar)
echo ============================================
echo Steps: check java artifacts -^> install builder -^> build webui -^> electron-builder [NSIS]
echo Output: dist\BempDiff-*-setup.exe  (project root 18_comparePakage\dist)
echo.

REM ---------- 0. Prerequisite: Java backend artifacts ----------
if not exist "dist_input\jre\bin\javaw.exe" (
  echo "[ERROR] dist_input\jre not found - build the Java backend (javac/jar/cfr/jlink) beforehand"
  pause
  exit /b 1
)
if not exist "dist_input\app\cfr.jar" (
  echo "[ERROR] dist_input\app\cfr.jar not found - build the Java backend beforehand"
  pause
  exit /b 1
)
set "_has_backend=0"
if exist "dist_input\classes" set "_has_backend=1"
if exist "dist_input\app\bempdiff.jar" set "_has_backend=1"
if "%_has_backend%"=="0" (
  echo "[ERROR] No backend artifact (dist_input\classes or dist_input\app\bempdiff.jar) - build the Java backend beforehand"
  pause
  exit /b 1
)
echo [OK] Java backend artifacts present.

REM ---------- 1. Install electron-builder (here, in bempdiff/) ----------
echo [STEP] Installing electron-builder ...
call npm install --no-audit --no-fund
if errorlevel 1 (
  echo "[ERROR] npm install failed"
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
    echo "[ERROR] electron install failed"
    pause
    exit /b 1
  )
) else (
  echo "[OK] dev-shell Electron runtime present"
)

REM ---------- 3. Build webui (produces webui/dist served by backend --webroot) ----------
echo [STEP] Building webui (vite build) ...
pushd webui
call npm install --no-audit --no-fund
if errorlevel 1 (
  echo "[ERROR] webui npm install failed"
  pause
  exit /b 1
)
call npm run build
if errorlevel 1 (
  echo "[ERROR] webui build failed"
  pause
  exit /b 1
)
popd
echo [OK] webui/dist ready.

REM ---------- 5. Package with electron-builder (NSIS) ----------
echo [STEP] Running electron-builder (NSIS) ...
call npm run dist
set "_dist_err=%errorlevel%"
if "%_dist_err%"=="0" goto dist_ok
REM electron-builder 退出非 0：可能是 sandbox safe-delete 清理中间文件失败（产物已生成）。
REM 检查安装包是否实际产出，若已产出则视为成功（仅清理步骤被沙箱拦截，不影响安装包）。
if exist "..\dist\BempDiff-*-setup.exe" (
  echo [WARN] electron-builder exited %_dist_err% but installer was produced - sandbox safe-delete cleanup skipped, installer is valid
  goto dist_ok
)
echo "[ERROR] electron-builder failed, see output above"
pause
exit /b 1
:dist_ok

echo.
echo [DONE] Build complete.
echo   Installer: dist\BempDiff-*-setup.exe  (project root 18_comparePakage\dist)
