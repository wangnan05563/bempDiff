@echo off
chcp 936 >nul 2>&1
setlocal EnableExtensions

REM Silent background mode: relaunch self in a hidden console (ghost arg) so the main
REM console closes immediately and no terminal log remains visible after startup.
REM To stop, use tooling\scripts\stop-service.bat.
if not "%~1"=="ghost" (
  powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0_silent_start.ps1" start_service
  exit /b 0
)

cd /d "%~dp0\..\.."

set "PORT=18765"
set "DEV_PORT=5180"
if defined BEMPDIFF_DEV_PORT set "DEV_PORT=%BEMPDIFF_DEV_PORT%"

REM Dev-shell mode: when BEMPDIFF_SHELL=electron, launch native window to load frontend; otherwise browser only
set "SHELL=none"
if defined BEMPDIFF_SHELL set "SHELL=%BEMPDIFF_SHELL%"
set "ELECTRON_BIN="
if "%SHELL%"=="electron" (
  if exist "%~dp0..\..\bempdiff\dev-shell\node_modules\.bin\electron.cmd" set "ELECTRON_BIN=%~dp0..\..\bempdiff\dev-shell\node_modules\.bin\electron.cmd"
  if not defined ELECTRON_BIN (
    for /f "usebackq delims=" %%e in (`where electron 2^>nul`) do ( if not defined ELECTRON_BIN set "ELECTRON_BIN=%%e" )
  )
)

if "%SHELL%"=="electron" goto :launch_electron_only

set "LOGDIR=bempdiff\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%"

echo ============================================================
echo   BempDiff dev-mode launcher - run directly without packaging
echo ============================================================
echo.

REM ---------- 1. Locate Java runtime ----------
REM Prefer built-in jlink jre, fall back to toolchain Zulu JDK21, then system PATH / JAVA_HOME
set "JAVA="
if exist "bempdiff\dist_input\jre\bin\java.exe" (
  set "JAVA=bempdiff\dist_input\jre\bin\java.exe"
) else (
  for /d %%d in ("bempdiff\toolchain\zulu21*") do (
    if not defined JAVA if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
  )
)
if not defined JAVA (
  for /f "usebackq delims=" %%j in (`where java 2^>nul`) do (
    if not defined JAVA set "JAVA=%%j"
  )
)
if not defined JAVA (
  echo [ERROR] Java runtime not found. Run the build script to generate the built-in jre, or install JDK21 and add it to PATH.
  pause
  exit /b 1
)

REM Version check: backend compiled for Java21 (class file 65); older versions cannot run it
for /f "tokens=3 delims= " %%v in ('"%JAVA%" -version 2^>^&1') do set "JVER=%%~v"
if not "%JVER%"=="" (
  if %JVER:~0,2% LSS 21 (
    echo [WARN] Current Java version is %JVER%; backend needs JDK21 and may fail to start.
  ) else (
    echo [OK] Java runtime: %JVER%  -^> %JAVA%
  )
) else (
  echo [OK] Java runtime: %JAVA%
)

REM ---------- 1b. Windowless Java launcher (no console window for the long-running server) ----------
set "JAVAW=%JAVA:java.exe=javaw.exe%"
if not exist "%JAVAW%" set "JAVAW=%JAVA%"

REM ---------- 2. Locate backend classpath ----------
REM Prefer dist_input/classes, fall back to bempdiff.jar, then on-the-fly compile from source
set "CFR="
if exist "bempdiff\dist_input\app\cfr.jar" set "CFR=bempdiff\dist_input\app\cfr.jar"

set "CP="
if exist "bempdiff\dist_input\classes" (
  set "CP=bempdiff\dist_input\classes"
) else if exist "bempdiff\dist_input\app\bempdiff.jar" (
  set "CP=bempdiff\dist_input\app\bempdiff.jar"
) else (
  echo [INFO] No compiled artifact found; compiling java_core/src on the fly to bempdiff\dist_input\dev_classes ...
  set "JAVAC="
  for /d %%d in ("bempdiff\toolchain\zulu21*") do (
    if not defined JAVAC if exist "%%d\bin\javac.exe" set "JAVAC=%%d\bin\javac.exe"
  )
  if not defined JAVAC for %%x in ("%JAVA%") do set "JAVAC=%%~dpxjavac.exe"
  if not exist "%JAVAC%" (
    echo [ERROR] javac not found; cannot compile on the fly. Run the build script or install JDK21.
    pause
    exit /b 1
  )
  set "BUILDOUT=bempdiff\dist_input\dev_classes"
  if not exist "%BUILDOUT%" mkdir "%BUILDOUT%"
  dir /s /b bempdiff\java_core\src\*.java > "%LOGDIR%\srcs.txt"
  "%JAVAC%" -encoding UTF-8 -cp "%CFR%" -d "%BUILDOUT%" @"%LOGDIR%\srcs.txt"
  if errorlevel 1 (
    echo [ERROR] Source compilation failed. Check java_core/src or run the build script to produce dist_input\classes.
    pause
    exit /b 1
  )
  set "CP=%BUILDOUT%"
)
echo [OK] Backend classpath: %CP%

REM ---------- 3. Frontend launch mode ----------
REM auto: use vite dev server if node + webui/node_modules exist, else built webui/dist, else backend only
REM Override with env var BEMPDIFF_FRONTEND=dev^|build^|none
set "FRONTEND=auto"
if defined BEMPDIFF_FRONTEND set "FRONTEND=%BEMPDIFF_FRONTEND%"

set "USE_VITE=0"
set "SERVE_BUILD=0"
if exist "bempdiff\webui\dist" set "SERVE_BUILD=1"
if "%FRONTEND%"=="dev" set "USE_VITE=1"
if "%FRONTEND%"=="build" ( set "USE_VITE=0" & set "SERVE_BUILD=1" )
if "%FRONTEND%"=="none" ( set "USE_VITE=0" & set "SERVE_BUILD=0" )
if "%FRONTEND%"=="auto" (
  where node >nul 2>&1
  if not errorlevel 1 (
    if exist "bempdiff\webui\node_modules" set "USE_VITE=1"
  )
)

REM ---------- 4. Clean up any leftover old backend instance (by port) ----------
powershell -NoProfile -Command "$p=%PORT%;try{$id=(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1).OwningProcess;if($id -ne $null){Stop-Process -Id $id -Force -ErrorAction SilentlyContinue;Write-Host ('[CLEAN] stopped old process on port '+$p+' PID='+$id)}}catch{}"

REM ---------- 5. Start backend API ----------
set "WEBROOT_ARG="
if "%SERVE_BUILD%"=="1" set "WEBROOT_ARG=--webroot bempdiff\webui\dist"

echo.
echo [START] Starting backend API on port %PORT% (windowless via javaw; logs -^> %LOGDIR%\backend.out.log) ...
start "" /B "%JAVAW%" -cp "%CP%;%CFR%" com.bempdiff.Main server --port %PORT% %WEBROOT_ARG% > "%LOGDIR%\backend.out.log" 2>&1

powershell -NoProfile -Command "$p=%PORT%;$ok=$false;for($i=0;$i -lt 60;$i++){if(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue){$ok=$true;break}Start-Sleep -Seconds 1}if(-not $ok){exit 1}"
if errorlevel 1 (
  echo [WARN] Backend not ready within 60s. Check port usage or the errors above.
) else (
  echo [OK] Backend ready: http://127.0.0.1:%PORT%/
)

REM ---------- 6. Start frontend dev server (vite) ----------
if "%USE_VITE%"=="1" (
  echo [START] Starting frontend dev server vite on port %DEV_PORT% with hot reload ...
  start "" /B cmd /c "cd /d bempdiff\webui && npm run dev -- --port %DEV_PORT% --host 127.0.0.1"
  powershell -NoProfile -Command "$p=%DEV_PORT%;$ok=$false;for($i=0;$i -lt 60;$i++){if(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue){$ok=$true;break}Start-Sleep -Seconds 1}if(-not $ok){exit 1}"
  if errorlevel 1 (
    echo [WARN] Frontend dev server not ready within 60s. Check npm / node, or use BEMPDIFF_FRONTEND=build.
  ) else (
    echo [OK] Frontend ready: http://127.0.0.1:%DEV_PORT%/
  )
)

REM ---------- 7. Open frontend after startup (native shell or browser) ----------
set "OPEN_URL="
if "%USE_VITE%"=="1" set "OPEN_URL=http://127.0.0.1:%DEV_PORT%/"
if "%USE_VITE%"=="0" if "%SERVE_BUILD%"=="1" set "OPEN_URL=http://127.0.0.1:%PORT%/"

if not "%OPEN_URL%"=="" (
  if "%SHELL%"=="electron" (
    if defined ELECTRON_BIN (
      echo [OPEN] Launching native Electron shell to load %OPEN_URL%
      start "" /B cmd /c "set BEMPDIFF_DEV_URL=%OPEN_URL% && cd /d bempdiff\dev-shell && %ELECTRON_BIN% ."
    ) else (
      echo [WARN] electron not found - bempdiff\dev-shell\node_modules\.bin\electron.cmd or global PATH.
      echo        Run  cd bempdiff\dev-shell ^&^& npm install  first, or use browser mode by unsetting BEMPDIFF_SHELL.
      echo [OPEN] Falling back to browser: %OPEN_URL%
      start "" "%OPEN_URL%"
    )
  ) else (
    set "OPEN_BROWSER=1"
    if defined BEMPDIFF_NO_OPEN set "OPEN_BROWSER=0"
    if "%OPEN_BROWSER%"=="1" (
      echo [OPEN] Opening frontend page: %OPEN_URL%
      start "" "%OPEN_URL%"
    )
  )
)

REM ---------- 8a. Silent background: do not keep the console open; leave services running ----------
if "%~1"=="ghost" exit /b 0

REM ---------- 8. Prompt and wait for any key to close ----------
echo.
echo ============================================================
echo   BempDiff dev mode running
echo   Backend API : http://127.0.0.1:%PORT%/
if "%USE_VITE%"=="1" echo   Frontend dev : http://127.0.0.1:%DEV_PORT%/
if "%USE_VITE%"=="0" if "%SERVE_BUILD%"=="1" echo   Frontend build : http://127.0.0.1:%PORT%/
if "%USE_VITE%"=="0" if "%SERVE_BUILD%"=="0" echo   Frontend     : not started (no node/node_modules and no webui/dist); backend API callable directly
if "%SHELL%"=="electron" echo   Runtime      : native shell (Electron window)
echo.
echo   Press any key to stop the service and close this window ...
echo ============================================================
pause >nul

REM ---------- 9. Stop service: by port + process command-line signature (double safety) ----------
echo.
echo [STOP] Stopping BempDiff service ...
powershell -NoProfile -Command "$ports=@(%PORT%,%DEV_PORT%);foreach($pt in $ports){try{$id=(Get-NetTCPConnection -LocalPort $pt -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1).OwningProcess;if($id -ne $null){Stop-Process -Id $id -Force -ErrorAction SilentlyContinue;Write-Host ('[STOP] port '+$pt+' PID='+$id)}}catch{}};$ps=Get-CimInstance Win32_Process -ErrorAction SilentlyContinue;foreach($pr in $ps){if($pr.CommandLine -match 'com\.bempdiff\.Main server' -or ($pr.Name -eq 'node.exe' -and $pr.CommandLine -match 'vite') -or $pr.CommandLine -match 'dev-shell'){try{Stop-Process -Id $pr.ProcessId -Force -ErrorAction SilentlyContinue;Write-Host ('[STOP] '+$pr.Name+' PID='+$pr.ProcessId)}catch{}}}"
echo [DONE] Stopped. Window will close shortly.
exit /b 0

:launch_electron_only
if not defined ELECTRON_BIN (
  echo [WARN] electron not found at bempdiff\dev-shell\node_modules\.bin\electron.cmd or global PATH.
  echo        Run  cd bempdiff\dev-shell ^&^& npm install  first.
  exit /b 1
)
echo [OPEN] Launching native Electron shell; it starts backend + frontend internally and runs silently.
start "" /B cmd /c "cd /d bempdiff\dev-shell && %ELECTRON_BIN% ."
exit /b 0
