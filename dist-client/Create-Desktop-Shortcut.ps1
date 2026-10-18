$WshShell = New-Object -ComObject WScript.Shell
$DesktopPath = [System.Environment]::GetFolderPath('Desktop')
$Shortcut = $WshShell.CreateShortcut("$DesktopPath\WeightTracker-RemoteBridge.lnk")
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$Shortcut.TargetPath = "$ScriptDir\Start-Bridge-Tray.bat"
$Shortcut.WorkingDirectory = "$ScriptDir"
$Shortcut.IconLocation = "$ScriptDir\assets\app.ico, 0"
$Shortcut.Description = "WeightTracker Remote ADB Bridge"
$Shortcut.Save()

Write-Host "Desktop shortcut created successfully: $DesktopPath\WeightTracker-RemoteBridge.lnk" -ForegroundColor Green
