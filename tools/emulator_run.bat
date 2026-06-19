@echo off
REM ===============================================================
REM tools/emulator_run.bat
REM ---------------------------------------------------------------
REM Step 3.7 -- create the Step37 AVD in the user-owned SDK root
REM and boot a headless Android emulator with SwiftShader software
REM rendering (no GPU/Hyper-V required on non-admin Windows).
REM
REM Pre-requisites (run once):
REM     python scripts/install_emulator_system_image.py
REM
REM Run from the sportstream repo root:
REM     tools\emulator_run.bat
REM
REM After this prints "READY" the AVD is up and `adb devices`
REM shows the emulator. Install + capture:
REM     adb install -r app\build\outputs\apk\debug\app-debug.apk
REM     adb shell am start -n com.sportstream.app/.ui.activities.SplashActivity
REM     adb exec-out screencap -p > ui-reference\qa-step-3.7\01-splash.png
REM ===============================================================
setlocal
set ANDROID_SDK_ROOT=C:\Users\RDP\android-sdk
set ANDROID_HOME=C:\Users\RDP\android-sdk
set PATH=C:\Users\RDP\android-sdk\platform-tools;C:\Users\RDP\android-sdk\emulator;C:\Users\RDP\android-sdk\cmdline-tools\latest\bin;%PATH%

echo === [1/3] create AVD Step37 ===
echo no | "C:\Users\RDP\android-sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n Step37 -k "system-images;android-34;google_apis;x86_64" --force
if errorlevel 1 goto :error_avd

echo === [2/3] verify avdmanager created Step37 ===
"C:\Users\RDP\android-sdk\cmdline-tools\latest\bin\avdmanager.bat" list avd | findstr Step37
if errorlevel 1 goto :error_avd_missing

echo === [3/3] launch emulator headless ===
echo.  (boots in background; close this window only after you're done)
start "" "C:\Users\RDP\android-sdk\emulator\emulator.exe" -avd Step37 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -accel off
goto :end

:error_avd
echo === FAILED at avdmanager create ===
exit /b 2

:error_avd_missing
echo === FAILED: Step37 AVD not found after create ===
exit /b 3

:end
echo READY  -  adb devices should list emulator-5554 within ~60 s
exit /b 0
