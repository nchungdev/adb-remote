# PowerShell installer for ADB Device Manager
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Cai dat ADB Device Manager cho Windows (PowerShell)" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

$targetDir = "$env:LocalAppData\adb-device-manager"
$binName = "adb-device-manager.exe"
$startupDir = "$env:AppData\Microsoft\Windows\Start Menu\Programs\Startup"

if (!(Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

Stop-Process -Name "adb-device-manager" -Force -ErrorAction SilentlyContinue

Copy-Item -Path "$PSScriptRoot\$binName" -Destination "$targetDir\$binName" -Force

$vbsContent = @"
Set WshShell = CreateObject("WScript.Shell")
WshShell.Run chr(34) & "$targetDir\$binName" & chr(34), 0
Set WshShell = Nothing
"@
Set-Content -Path "$targetDir\start-hidden.vbs" -Value $vbsContent -Force
Copy-Item -Path "$targetDir\start-hidden.vbs" -Destination "$startupDir\adb-device-manager.vbs" -Force

Start-Process -FilePath "wscript.exe" -ArgumentList "`"$targetDir\start-hidden.vbs`""

Start-Sleep -Seconds 2
Start-Process "http://127.0.0.1:8080"

Write-Host ""
Write-Host "[OK] Cai dat hoan tat! Dang chay ngam tai: http://127.0.0.1:8080" -ForegroundColor Green
Write-Host ""
