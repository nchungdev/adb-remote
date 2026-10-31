# PowerShell uninstaller for ADB Device Manager
Write-Host "=================================================" -ForegroundColor Yellow
Write-Host " Go bo ADB Device Manager (PowerShell)" -ForegroundColor Yellow
Write-Host "=================================================" -ForegroundColor Yellow

$targetDir = "$env:LocalAppData\adb-device-manager"
$startupDir = "$env:AppData\Microsoft\Windows\Start Menu\Programs\Startup"

Stop-Process -Name "adb-device-manager" -Force -ErrorAction SilentlyContinue

Remove-Item -Path "$startupDir\adb-device-manager.vbs" -Force -ErrorAction SilentlyContinue
Remove-Item -Path $targetDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "[OK] Da go bo ADB Device Manager thanh cong." -ForegroundColor Green
Write-Host ""
