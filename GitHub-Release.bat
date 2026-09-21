@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Makay Cleaner - GitHub Release

REM ============================================================
REM  VERSION.txt surumunu okur, APK varsa GitHub Release olusturur
REM  Gereksinim: git + gh (GitHub CLI) oturum acik
REM  Repo: https://github.com/makaydestek/makay-cleaner
REM ============================================================

cd /d "%~dp0"
set "VERSION_FILE=%CD%\VERSION.txt"
set "APK_DIR=%CD%\APK"

if not exist "%VERSION_FILE%" (
  echo [HATA] VERSION.txt yok
  exit /b 1
)

for /f "usebackq delims=" %%V in ("%VERSION_FILE%") do set "VERSION=%%V"
set "TAG=v!VERSION!"
set "APK=%APK_DIR%\MakayCleaner_v!VERSION!.apk"

if not exist "%APK%" (
  echo [HATA] APK yok: %APK%
  echo Once APK-Hazirla.bat veya gradlew assembleRelease calistirin.
  exit /b 1
)

where gh >nul 2>&1
if errorlevel 1 (
  echo [HATA] GitHub CLI ^(gh^) yuklu degil.
  echo https://cli.github.com/ adresinden kurun, sonra: gh auth login
  exit /b 1
)

echo Surum : !VERSION!
echo Tag   : !TAG!
echo APK   : %APK%
echo.

gh release view "!TAG!" --repo makaydestek/makay-cleaner >nul 2>&1
if not errorlevel 1 (
  echo Release !TAG! zaten var. Asset guncelleniyor...
  gh release upload "!TAG!" "%APK%" --repo makaydestek/makay-cleaner --clobber
) else (
  echo Yeni release olusturuluyor...
  gh release create "!TAG!" "%APK%" --repo makaydestek/makay-cleaner --title "Makay Cleaner !VERSION!" --notes "Makay Cleaner !VERSION! sideload surumu. Ayarlar ^> Guncelleme kontrol et ile indirilebilir."
)

if errorlevel 1 (
  echo [HATA] Release basarisiz. gh auth login ile oturum acin.
  exit /b 1
)

echo.
echo [OK] https://github.com/makaydestek/makay-cleaner/releases/tag/!TAG!
endlocal
exit /b 0
