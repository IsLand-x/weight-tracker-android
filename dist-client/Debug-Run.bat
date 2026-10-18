@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ====================================================
echo  WeightTracker Remote Bridge - 调试启动模式
echo ====================================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-tray.ps1"
echo.
echo ====================================================
echo  进程已结束，按任意键关闭窗口...
echo ====================================================
pause >nul
