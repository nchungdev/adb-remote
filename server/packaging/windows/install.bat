@echo off
setlocal enabledelayedexpansion

echo =================================================
echo  Cai dat ADB Device Manager cho Windows
echo =================================================

set "TARGET_DIR=%LocalAppData%\adb-device-manager"
set "BIN_NAME=adb-device-manager.exe"
set "VBS_NAME=start-hidden.vbs"
set "STARTUP_DIR=%AppData%\Microsoft\Windows\Start Menu\Programs\Startup"

if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

taskkill /F /IM %BIN_NAME% >nul 2>&1

copy /Y "%~dp0%BIN_NAME%" "%TARGET_DIR%\%BIN_NAME%" >nul

(
echo Set WshShell = CreateObject^("WScript.Shell"^)
echo WshShell.Run chr^(34^) ^& "%TARGET_DIR%\%BIN_NAME%" ^& chr^(34^), 0
echo Set WshShell = Nothing
) > "%TARGET_DIR%\%VBS_NAME%"

copy /Y "%TARGET_DIR%\%VBS_NAME%" "%STARTUP_DIR%\adb-device-manager.vbs" >nul

wscript.exe "%TARGET_DIR%\%VBS_NAME%"

timeout /t 2 >nul

start http://127.0.0.1:8080

echo.
echo [OK] Cai dat hoan tat!
echo ADB Device Manager dang chay ngam tai: http://127.0.0.1:8080
echo Chuong trinh se tu dong khoi dong cung Windows.
echo.
pause
