@echo off
chcp 936 >nul 2>&1
setlocal EnableExtensions
cd /d "%~dp0\..\.."

set "PORT=18765"
set "DEV_PORT=5180"
if defined BEMPDIFF_DEV_PORT set "DEV_PORT=%BEMPDIFF_DEV_PORT%"
set "PIDFILE=bempdiff\logs\bempdiff-web.pid"

echo ============================================================
echo   BempDiff Stop Script (port + process dual-kill, same as start)
echo ============================================================
echo.

REM ---------- 1. Stop by PID file (if start script wrote it) ----------
if exist "%PIDFILE%" (
  for /f "usebackq" %%i in ("%PIDFILE%") do (
    taskkill /F /PID %%i >nul 2>&1 && echo [STOP] PID file process %%i terminated || echo [WARN] PID %%i not found or no permission
  )
  del /F /Q "%PIDFILE%" >nul 2>&1
)

REM ---------- 2. Stop by port + process command-line signature (same as start step9) ----------
powershell -NoProfile -Command "$ports=@(%PORT%,%DEV_PORT%);$any=$false;foreach($pt in $ports){try{$c=Get-NetTCPConnection -LocalPort $pt -State Listen -ErrorAction SilentlyContinue;$id=if($c){($c|Select-Object -First 1).OwningProcess};if($id -ne $null){Stop-Process -Id $id -Force -ErrorAction SilentlyContinue;Write-Host ('[STOP] port '+$pt+' owner PID='+$id);$any=$true}}catch{}};$ps=Get-CimInstance Win32_Process -ErrorAction SilentlyContinue;foreach($pr in $ps){if($pr.CommandLine -match 'com\.bempdiff\.Main server' -or ($pr.Name -eq 'node.exe' -and $pr.CommandLine -match 'vite') -or $pr.CommandLine -match 'dev-shell'){try{Stop-Process -Id $pr.ProcessId -Force -ErrorAction SilentlyContinue;Write-Host ('[STOP] '+$pr.Name+' (PID='+$pr.ProcessId+') terminated');$any=$true}catch{}}};if(-not $any){Write-Host '[INFO] No BempDiff listener/process found, nothing to stop'}"

echo.
echo [DONE] Stop script finished.
pause
