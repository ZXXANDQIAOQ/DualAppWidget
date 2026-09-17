@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0.."

rem ============================================================
rem  切换到「可添加版」（add）
rem  覆盖安装 add 版 APK，这个版本在小部件面板里能找到，用来新建实例。
rem  注意：不卸载，实例和配置都不会丢。
rem ============================================================

set "ADB="
for /f "delims=" %%i in ('where adb 2^>nul') do if not defined ADB set "ADB=%%i"
if not defined ADB if exist "D:\App\Android\Sdk\platform-tools\adb.exe" set "ADB=D:\App\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
  echo [x] 找不到 adb。请安装 Android platform-tools 并加进 PATH，或修改本脚本里的 ADB 路径。
  pause
  exit /b 1
)

set "APK=prebuilt\DualAppWidget-add.apk"
if not exist "%APK%" (
  echo [x] 找不到 %APK%，请先构建：gradlew assembleAddableDebug
  pause
  exit /b 1
)

echo [1/4] 检查设备 ...
"%ADB%" devices
echo.

echo [2/4] 覆盖安装 %APK%（不要卸载！）...
"%ADB%" install -r "%APK%"
echo.

echo [3/4] 校验已安装版本（期望 1.0-add）...
"%ADB%" shell dumpsys package com.example.dualappwidget | findstr /C:"versionName"
echo.

echo [4/4] 重启桌面，让它重新读取小部件身份 ...
"%ADB%" shell am force-stop com.miui.home
timeout /t 4 /nobreak >nul

echo.
echo ============================================================
echo  完成。现在长按桌面空白处 -^> 添加小部件 -^> 支持小部件应用「全部」
echo  -^> 找到「双应用快捷启动」，想加几个就加几个。
echo  加好后跑 switch-to-stack.bat 换回可堆叠版。
echo ============================================================
pause
