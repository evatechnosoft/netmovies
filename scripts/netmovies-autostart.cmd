@echo off
REM NetMovies - PC acilisinda yigini ayaga kaldir.
REM Kok neden: Docker Desktop AutoStart kapali oldugu icin motor hic baslamiyordu;
REM containerlardaki `restart: unless-stopped` de bu yuzden ise yaramiyordu.
REM Kurulum: bu dosyanin kisayolu %APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup icine konur.

set PROJECT=D:\projects\netmovies

REM 1) Docker Desktop calismiyorsa baslat.
tasklist /FI "IMAGENAME eq Docker Desktop.exe" | find /I "Docker Desktop.exe" >nul
if errorlevel 1 start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"

REM 2) Linux motoru hazir olana kadar bekle (en fazla ~5 dk).
for /L %%i in (1,1,60) do (
  docker info >nul 2>&1 && goto :ready
  timeout /t 5 /nobreak >nul
)
echo Docker motoru acilmadi - yigin baslatilamadi.>>"%PROJECT%\autostart.log"
exit /b 1

:ready
REM 3) Yigin idempotent baslatilir (zaten ayaktaysa dokunmaz).
cd /d "%PROJECT%"
docker compose up -d >>"%PROJECT%\autostart.log" 2>&1
echo %DATE% %TIME% yigin baslatildi>>"%PROJECT%\autostart.log"
