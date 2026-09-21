@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Makay Cleaner - APK Hazirlama

REM ============================================================
REM  MakayCleaner Release APK
REM  Tek surum kaynagi: VERSION.txt
REM  Tek teslimat klasoru: APK\
REM  Cikti: yalnizca MakayCleaner_vX.Y.Z.apk + .apk.sha256
REM ============================================================

cd /d "%~dp0"
if errorlevel 1 (
    echo [HATA] Proje klasorune gidilemedi: %~dp0
    goto :END_FAIL
)

set "PROJECT_DIR=%CD%"
set "APK_DIR=%PROJECT_DIR%\APK"
set "GRADLEW=%PROJECT_DIR%\gradlew.bat"
set "BUMP_PS1=%PROJECT_DIR%\tools\bump-version.ps1"
set "READ_PS1=%PROJECT_DIR%\tools\read-version.ps1"
set "VERSION_FILE=%PROJECT_DIR%\VERSION.txt"

if not exist "%APK_DIR%" mkdir "%APK_DIR%"

echo.
echo ================================================
echo  Makay Cleaner - APK Hazirlama
echo ================================================
echo  Proje   : %PROJECT_DIR%
echo  Teslimat: %APK_DIR%
echo  Surum   : %VERSION_FILE%
echo.

REM JDK 17 zorunlu
if exist "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
) else if exist "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot"
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)

if not defined JAVA_HOME (
    echo [HATA] JDK 17 bulunamadi.
    goto :END_FAIL
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo  JAVA    : %JAVA_HOME%
echo.

if not exist "%GRADLEW%" (
    echo [HATA] gradlew.bat yok: %GRADLEW%
    goto :END_FAIL
)

if not exist "%BUMP_PS1%" (
    echo [HATA] bump-version.ps1 yok: %BUMP_PS1%
    goto :END_FAIL
)

echo [1/4] Surum artiriliyor (VERSION.txt)...
set "OLD_VERSION="
set "VERSION="
for /f "usebackq tokens=1,2 delims=|" %%A in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%BUMP_PS1%"`) do (
    set "OLD_VERSION=%%A"
    set "VERSION=%%B"
)
if not defined VERSION (
    echo [HATA] Surum artirilamadi.
    goto :END_FAIL
)
echo  Onceki : !OLD_VERSION!
echo  Yeni   : !VERSION!
echo.

REM Surum dosyasini dogrula (tek kaynak)
set "CHECK_VER="
for /f "usebackq tokens=1,2 delims=|" %%A in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%READ_PS1%"`) do (
    set "CHECK_VER=%%A"
    set "CHECK_CODE=%%B"
)
if /I not "!CHECK_VER!"=="!VERSION!" (
    echo [HATA] VERSION.txt uyumsuz: dosya=!CHECK_VER! beklenen=!VERSION!
    goto :END_FAIL
)
echo  Kod    : !CHECK_CODE!  (versionCode)
echo.

echo [2/4] Gradle durduruluyor...
call "%GRADLEW%" --stop >nul 2>&1
echo.

echo [3/4] Release APK derleniyor + APK\ klasorune yayinlaniyor (v!VERSION!)...
echo  Bu islem birka dakika surebilir.
echo.
REM assembleRelease bitince publishReleaseApk: eski dosyalari siler, yalnizca vX.Y.Z + sha256 yazar
call "%GRADLEW%" assembleRelease --no-daemon
set "ERR=!ERRORLEVEL!"
if not "!ERR!"=="0" (
    echo.
    echo [HATA] Derleme/yayin basarisiz. Kod: !ERR!
    goto :END_FAIL
)
echo.

echo [4/4] Teslimat dogrulaniyor...
set "APK_TARGET=%APK_DIR%\MakayCleaner_v!VERSION!.apk"
set "SHA_TARGET=%APK_DIR%\MakayCleaner_v!VERSION!.apk.sha256"
if not exist "!APK_TARGET!" (
    echo [HATA] Beklenen APK yok:
    echo  !APK_TARGET!
    goto :END_FAIL
)
if not exist "!SHA_TARGET!" (
    echo [HATA] SHA-256 yok:
    echo  !SHA_TARGET!
    goto :END_FAIL
)

for %%A in ("!APK_TARGET!") do (
    set "SIZE_BYTES=%%~zA"
    set "APK_DATE=%%~tA"
)
set /a SIZE_MB=!SIZE_BYTES! / 1048576

echo.
echo [OK] APK hazir — klasorde yalnizca bu surum:
echo  Dosya : !APK_TARGET!
echo  SHA   : !SHA_TARGET!
echo  Surum : !VERSION!  (versionCode !CHECK_CODE!)
echo  Boyut : !SIZE_MB! MB
echo  Tarih : !APK_DATE!
echo.

start "" explorer "%APK_DIR%"
goto :END_OK

:END_FAIL
echo.
echo ================================================
echo  Islem basarisiz - pencereyi kapatmak icin tus
echo ================================================
echo.
pause
endlocal
exit /b 1

:END_OK
echo ================================================
echo  Tamamlandi - MakayCleaner v!VERSION!
echo  Kurulum: APK\MakayCleaner_v!VERSION!.apk
echo ================================================
echo.
pause
endlocal
exit /b 0
