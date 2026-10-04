@echo off
setlocal
cd /d "%~dp0\.."
if not exist ".git\hooks" mkdir ".git\hooks"
copy /Y "tools\git-hooks\prepare-commit-msg" ".git\hooks\prepare-commit-msg" >nul
copy /Y "tools\git-hooks\commit-msg" ".git\hooks\commit-msg" >nul
echo [OK] Git hook'lari kuruldu.
echo  - prepare-commit-msg
echo  - commit-msg
echo Co-authored-by: Cursor commit mesajlarindan silinir.
echo.
echo Ayrica Cursor'da:
echo  Settings - Git and PRs - Attribution - Commit attribution KAPALI
echo.
pause
endlocal
