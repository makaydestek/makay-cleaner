@echo off
setlocal EnableExtensions
title Makay Cleaner - GitHub Giris
cd /d "%~dp0"

set "PATH=%ProgramFiles%\GitHub CLI;%LocalAppData%\Programs\GitHub CLI;%PATH%"

echo.
echo ================================================
echo  GitHub CLI giris (kod bu pencerede)
echo ================================================
echo.
echo  Kod XXXX-XXXX biciminde ASAGIDA cikacak.
echo  Ayni anda panoya kopyalanir ^(Ctrl+V^).
echo.
echo  Tarayici acilinca:
echo   - kutuya kodu yapistirin
echo   - Authorize github cli
echo.
pause

where gh >nul 2>&1
if errorlevel 1 (
  echo [HATA] gh yok. winget install --id GitHub.cli
  pause
  exit /b 1
)

gh auth login -h github.com -p https -w -c -s "repo,read:org,gist,workflow"
if errorlevel 1 (
  echo.
  echo [HATA] Giris basarisiz.
  pause
  exit /b 1
)

echo.
gh auth status
echo.
echo [OK] Giris tamam. Simdi GitHub-Release.bat calistirabilirsiniz.
pause
endlocal
exit /b 0
