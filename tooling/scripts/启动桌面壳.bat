@echo off
chcp 936 >nul 2>&1
setlocal EnableExtensions

REM Silent background mode: delegate to the ASCII-named runner so the Chinese script
REM path is never passed to a new cmd/PowerShell process (avoids codepage corruption).
REM The runner sets BEMPDIFF_SHELL=electron then re-enters the backend launcher.
if not "%~1"=="ghost" (
  powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0_silent_start.ps1" desktop_shell
  exit /b 0
)
exit /b 0
cd /d "%~dp0\..\.."

REM ============================================================
REM  BempDiff dev-shell launcher (Electron native window)
REM  Same as start-service.bat, but forces BEMPDIFF_SHELL=electron:
REM  starts backend (18765) + frontend (vite 5180 or build), then
REM  loads the frontend in an Electron native window instead of
REM  a browser. Close the window (or press any key) to stop.
REM ============================================================

set "BEMPDIFF_SHELL=electron"
call "%~dp0��������.bat"
exit /b %errorlevel%
