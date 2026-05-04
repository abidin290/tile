@echo off
echo ==========================================
echo   QuickTile - Android Build Script
echo ==========================================
echo.

:: Cek apakah gradlew.bat ada, jika tidak download dulu
if not exist "gradlew.bat" (
    echo [INFO] gradlew.bat tidak ditemukan.
    echo [INFO] Silakan buka project di Android Studio agar Gradle Wrapper di-generate otomatis.
    echo.
    pause
    exit /b 1
)

echo [1/3] Cleaning project...
call gradlew.bat clean
if errorlevel 1 (
    echo [ERROR] Clean gagal!
    pause
    exit /b 1
)

echo.
echo [2/3] Building Debug APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo [ERROR] Build gagal! Cek output di atas untuk detail error.
    pause
    exit /b 1
)

echo.
echo [3/3] Build selesai!
echo.
echo APK tersimpan di:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
echo Untuk install ke device yang terhubung via USB:
echo   adb install app\build\outputs\apk\debug\app-debug.apk
echo.
pause
