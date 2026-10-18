#requires -Version 5.1
<#
.SYNOPSIS
    WeightTracker Remote ADB Bridge - Windows System Tray Client
#>

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFile = Join-Path $ScriptDir "config.json"
$IconFile = Join-Path $ScriptDir "assets\app.ico"

# Load Config
if (Test-Path $ConfigFile) {
    try {
        $Config = Get-Content $ConfigFile -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        $Config = [PSCustomObject]@{
            serverHost = "43.160.252.99"
            serverPort = 9876
            token = "weight-tracker-dev-token"
            adbPath = "adb"
            defaultPackage = "org.freeyourgadget.weightrecorder"
            reconnectIntervalSec = 5
        }
    }
} else {
    $Config = [PSCustomObject]@{
        serverHost = "43.160.252.99"
        serverPort = 9876
        token = "weight-tracker-dev-token"
        adbPath = "adb"
        defaultPackage = "org.freeyourgadget.weightrecorder"
        reconnectIntervalSec = 5
    }
}

# Resolve ADB Path
function Find-AdbPath {
    param ($configuredPath)
    if ($configuredPath -and (Test-Path $configuredPath)) {
        return $configuredPath
    }
    $cmd = Get-Command "adb.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    # Check default Android SDK locations
    $candidateLocations = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
    )
    foreach ($cand in $candidateLocations) {
        if (Test-Path $cand) {
            return $cand
        }
    }
    return "adb"
}

$AdbExecutable = Find-AdbPath $Config.adbPath

# Initialize NotifyIcon (Tray Icon)
$NotifyIcon = New-Object System.Windows.Forms.NotifyIcon
try {
    if (Test-Path $IconFile) {
        $NotifyIcon.Icon = New-Object System.Drawing.Icon($IconFile)
    } else {
        $NotifyIcon.Icon = [System.Drawing.SystemIcons]::Application
    }
} catch {
    $NotifyIcon.Icon = [System.Drawing.SystemIcons]::Application
}
$NotifyIcon.Text = "WeightTracker Remote Bridge`n状态: 启动中..."
$NotifyIcon.Visible = $true

# Context Menu
$ContextMenu = New-Object System.Windows.Forms.ContextMenuStrip
$MenuStatus = $ContextMenu.Items.Add("状态: 正在连接...")
$MenuStatus.Enabled = $false

$MenuSep1 = $ContextMenu.Items.Add("-")

$MenuCheckDevices = $ContextMenu.Items.Add("检查本地 ADB 设备")
$MenuCheckDevices.add_Click({
    try {
        $out = & $AdbExecutable devices 2>&1 | Out-String
        [System.Windows.Forms.MessageBox]::Show($out, "ADB 设备列表", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Information)
    } catch {
        [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, "ADB 执行失败", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error)
    }
})

$MenuOpenConfig = $ContextMenu.Items.Add("编辑配置文件")
$MenuOpenConfig.add_Click({
    notepad.exe $ConfigFile
})

$MenuSep2 = $ContextMenu.Items.Add("-")

$MenuExit = $ContextMenu.Items.Add("退出 (Exit)")
$MenuExit.add_Click({
    $script:Running = $false
    $NotifyIcon.Visible = $false
    $NotifyIcon.Dispose()
    [System.Windows.Forms.Application]::Exit()
})

$NotifyIcon.ContextMenuStrip = $ContextMenu

function Show-Notification {
    param([string]$title, [string]$msg, [string]$icon = "Info")
    $NotifyIcon.BalloonTipTitle = $title
    $NotifyIcon.BalloonTipText = $msg
    if ($icon -eq "Error") {
        $NotifyIcon.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Error
    } else {
        $NotifyIcon.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Info
    }
    $NotifyIcon.ShowBalloonTip(3000)
}

# Execute ADB commands helper
function Invoke-AdbCommand {
    param([string]$arguments)
    try {
        $pinfo = New-Object System.Diagnostics.ProcessStartInfo
        $pinfo.FileName = $AdbExecutable
        $pinfo.Arguments = $arguments
        $pinfo.RedirectStandardOutput = $true
        $pinfo.RedirectStandardError = $true
        $pinfo.UseShellExecute = $false
        $pinfo.CreateNoWindow = $true

        $p = New-Object System.Diagnostics.Process
        $p.StartInfo = $pinfo
        $p.Start() | Out-Null
        $stdout = $p.StandardOutput.ReadToEnd()
        $stderr = $p.StandardError.ReadToEnd()
        $p.WaitForExit()
        return [PSCustomObject]@{
            exitCode = $p.ExitCode
            stdout = $stdout
            stderr = $stderr
        }
    } catch {
        return [PSCustomObject]@{
            exitCode = -1
            stdout = ""
            stderr = $_.Exception.Message
        }
    }
}

Show-Notification "WeightTracker Remote Bridge" "托盘客户端已启动，正在连接开发服务器..."

$script:Running = $true

# Background communication loop via Timer or DoEvents
$WorkTimer = New-Object System.Windows.Forms.Timer
$WorkTimer.Interval = 1000

$TcpClient = $null
$Stream = $null
$Reader = $null
$Writer = $null

function Disconnect-Server {
    try { if ($Reader) { $Reader.Close() } } catch {}
    try { if ($Writer) { $Writer.Close() } } catch {}
    try { if ($Stream) { $Stream.Close() } } catch {}
    try { if ($TcpClient) { $TcpClient.Close() } } catch {}
    $script:TcpClient = $null
    $script:Stream = $null
    $script:Reader = $null
    $script:Writer = $null
    $MenuStatus.Text = "状态: 未连接服务器"
    $NotifyIcon.Text = "WeightTracker Remote Bridge`n状态: 未连接"
}

$LastConnectAttempt = [DateTime]::MinValue

$WorkTimer.add_Tick({
    # Handle Connection
    if ($null -eq $TcpClient -or -not $TcpClient.Connected) {
        $now = [DateTime]::Now
        if (($now - $LastConnectAttempt).TotalSeconds -ge $Config.reconnectIntervalSec) {
            $script:LastConnectAttempt = $now
            try {
                $script:TcpClient = New-Object System.Net.Sockets.TcpClient
                $connectTask = $script:TcpClient.ConnectAsync($Config.serverHost, [int]$Config.serverPort)
                if ($connectTask.Wait(3000)) {
                    $script:Stream = $script:TcpClient.GetStream()
                    $script:Reader = New-Object System.IO.StreamReader($script:Stream, [System.Text.Encoding]::UTF8)
                    $script:Writer = New-Object System.IO.StreamWriter($script:Stream, [System.Text.Encoding]::UTF8)
                    $script:Writer.AutoFlush = $true

                    # Send handshake register message
                    $registerMsg = @{
                        type = "register"
                        token = $Config.token
                        hostname = $env:COMPUTERNAME
                    } | ConvertTo-Json -Compress
                    $script:Writer.WriteLine($registerMsg)

                    $MenuStatus.Text = "状态: 已连接开发服务器"
                    $NotifyIcon.Text = "WeightTracker Remote Bridge`n状态: 已连接 ($($Config.serverHost))"
                    Show-Notification "连接成功" "已成功建立与开发服务器的测试通道！"
                } else {
                    Disconnect-Server
                }
            } catch {
                Disconnect-Server
            }
        }
        return
    }

    # If connected, read pending commands
    if ($Stream -and $Stream.DataAvailable) {
        try {
            $line = $script:Reader.ReadLine()
            if ($null -ne $line -and $line.Trim().Length -gt 0) {
                $req = $line | ConvertFrom-Json
                $action = $req.action
                $reqId = $req.id

                switch ($action) {
                    "ping" {
                        $resp = @{ id = $reqId; status = "ok"; data = "pong" } | ConvertTo-Json -Compress
                        $script:Writer.WriteLine($resp)
                    }
                    "devices" {
                        $res = Invoke-AdbCommand "devices"
                        $resp = @{ id = $reqId; status = "ok"; data = $res.stdout } | ConvertTo-Json -Compress
                        $script:Writer.WriteLine($resp)
                    }
                    "install" {
                        # $req.apkBase64 contains APK data or url
                        Show-Notification "正在安装 APK" "收到来自开发服务器的安装请求..."
                        $tempApk = Join-Path $env:TEMP "weight-tracker-remote-install.apk"
                        if ($req.apkBase64) {
                            $bytes = [System.Convert]::FromBase64String($req.apkBase64)
                            [System.IO.File]::WriteAllBytes($tempApk, $bytes)
                        } elseif ($req.apkUrl) {
                            (New-Object System.Net.WebClient).DownloadFile($req.apkUrl, $tempApk)
                        }

                        $res = Invoke-AdbCommand "install -r `"$tempApk`""
                        if (Test-Path $tempApk) { Remove-Item -Force $tempApk }

                        if ($res.exitCode -eq 0 -and $res.stdout -match "Success") {
                            Show-Notification "安装成功" "应用已成功安装到手机！"
                            $resp = @{ id = $reqId; status = "ok"; message = "Installed successfully"; stdout = $res.stdout } | ConvertTo-Json -Compress
                        } else {
                            Show-Notification "安装失败" "ADB 错误: $($res.stderr) $($res.stdout)" "Error"
                            $resp = @{ id = $reqId; status = "error"; message = "Install failed"; stdout = $res.stdout; stderr = $res.stderr } | ConvertTo-Json -Compress
                        }
                        $script:Writer.WriteLine($resp)
                    }
                    "launch" {
                        $pkg = if ($req.package) { $req.package } else { $Config.defaultPackage }
                        $res = Invoke-AdbCommand "shell monkey -p $pkg -c android.intent.category.LAUNCHER 1"
                        $resp = @{ id = $reqId; status = "ok"; stdout = $res.stdout; stderr = $res.stderr } | ConvertTo-Json -Compress
                        $script:Writer.WriteLine($resp)
                        Show-Notification "App 启动完成" "已发送启动意图至应用: $pkg"
                    }
                    "screenshot" {
                        $remoteTmp = "/data/local/tmp/remote_screen.png"
                        $localTmp = Join-Path $env:TEMP "remote_screen_$([Guid]::NewGuid().ToString('N')).png"
                        Invoke-AdbCommand "shell screencap -p $remoteTmp" | Out-Null
                        Invoke-AdbCommand "pull $remoteTmp `"$localTmp`"" | Out-Null
                        Invoke-AdbCommand "shell rm $remoteTmp" | Out-Null
                        
                        if (Test-Path $localTmp) {
                            $bytes = [System.IO.File]::ReadAllBytes($localTmp)
                            $base64 = [System.Convert]::ToBase64String($bytes)
                            Remove-Item -Force $localTmp
                            $resp = @{ id = $reqId; status = "ok"; imageBase64 = $base64 } | ConvertTo-Json -Compress
                        } else {
                            $resp = @{ id = $reqId; status = "error"; message = "Failed to capture screenshot" } | ConvertTo-Json -Compress
                        }
                        $script:Writer.WriteLine($resp)
                    }
                    default {
                        $resp = @{ id = $reqId; status = "error"; message = "Unknown action: $action" } | ConvertTo-Json -Compress
                        $script:Writer.WriteLine($resp)
                    }
                }
            }
        } catch {
            Disconnect-Server
        }
    }
})

$WorkTimer.Start()

# Run Application Loop
[System.Windows.Forms.Application]::Run()
