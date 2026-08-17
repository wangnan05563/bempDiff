@echo off
chcp 936 >nul 2>&1
setlocal EnableExtensions
cd /d "%~dp0\..\.."

REM ============================================================
REM  BempDiff dev-shell launcher (Electron native window)
REM  Same as start-service.bat, but forces BEMPDIFF_SHELL=electron:
REM  starts backend (18765) + frontend (vite 5180 or build), then
REM  loads the frontend in an Electron native window instead of
REM  a browser. Close the window (or press any key) to stop.
REM ============================================================

set "BEMPDIFF_SHELL=electron"
call "%~dp0Æô¶¯·þÎñ.bat"
exit /b %errorlevel%
