@echo off
setlocal

echo =================================================
echo  Go bo ADB Device Manager
echo =================================================

set "TARGET_DIR=%LocalAppData%\adb-device-manager"
set "BIN_NAME=adb-device-manager.exe"
set "STARTUP_DIR=%AppData%\Microsoft\Windows\Start Menu\Programs\Startup"

taskkill /F /IM %BIN_NAME% >nul 2>&1

del /F /Q "%STARTUP_DIR%\adb-device-manager.vbs" >nul 2>&1
rd /S /Q "%TARGET_DIR%" >nul 2>&1

echo.
echo [OK] Da go bo ADB Device Manager thanh cong.
echo.
pause
