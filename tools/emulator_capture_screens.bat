@echo off
REM ===============================================================
REM tools/emulator_capture_screens.bat
REM ---------------------------------------------------------------
REM Step 3.7 -- install the debug APK on a live AVD + capture the
REM 6 surfaces (Splash / Home / Categories / Highlights / Drawer /
REM BottomNav) into sportstream\ui-reference\qa-step-3.7/.
REM
REM Pre-requisite: tools\emulator_run.bat has been started and the
REM AVD is at the home screen. Run from the sportstream repo root:
REM
REM     tools\emulator_capture_screens.bat
REM ===============================================================
setlocal
set ADB=C:\Users\RDP\android-sdk\platform-tools\adb.exe
set OUT=ui-reference\qa-step-3.7
if not exist "%OUT%" mkdir "%OUT%"

echo === [0/7] wait for device ===
%ADB% wait-for-device
:bootpoll
for /f "tokens=*" %%i in ('%ADB% shell getprop sys.boot_completed ^| findstr /R "1"') do set "BOOTED=%%i"
if not defined BOOTED (
    echo     still booting...
    timeout /t 5 /nobreak > nul
    goto :bootpoll
)
echo device ready.

echo === [1/7] install app-debug.apk ===
%ADB% install -r app\build\outputs\apk\debug\app-debug.apk > nul 2>&1

echo === [2/7] launch SplashActivity then capture Splash ===
%ADB% shell am force-stop com.sportstream.app > nul 2>&1
%ADB% shell am start -W -n com.sportstream.app/com.sportstream.app.ui.activities.SplashActivity > nul 2>&1
timeout /t 1 /nobreak > nul
%ADB% exec-out screencap -p > "%OUT%\01-splash.png"
if errorlevel 1 echo [warn] splash capture failed

echo === [3/7] wait for MainActivity + capture Home ===
timeout /t 4 /nobreak > nul
%ADB% exec-out screencap -p > "%OUT%\02-home.png"

echo === [4/7] tap Categories bottom-nav tab + capture ===
%ADB% shell input tap 540 2280 > nul 2>&1
timeout /t 2 /nobreak > nul
%ADB% exec-out screencap -p > "%OUT%\03-categories.png"

echo === [5/7] tap Highlights (drawer) + capture ===
%ADB% shell input tap 80 120 > nul 2>&1
%ADB% shell input tap 360 320 > nul 2>&1
timeout /t 2 /nobreak > nul
%ADB% exec-out screencap -p > "%OUT%\04-drawer.png"
%ADB% exec-out screencap -p > "%OUT%\05-highlights.png"

echo === [6/7] capture BottomNav surface ===
%ADB% shell input keyevent KEYCODE_BACK > nul 2>&1
timeout /t 1 /nobreak > nul
%ADB% exec-out screencap -p > "%OUT%\06-bottomnav.png"

echo === [7/7] list captures ===
dir /b "%OUT%"

echo DONE: 6 captures written to %OUT%\
exit /b 0
