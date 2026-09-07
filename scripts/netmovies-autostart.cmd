@echo off
REM NetMovies - PC acilisinda yigini ayaga kaldir.
REM Kok neden: Docker Desktop AutoStart kapali oldugu icin motor hic baslamiyordu;
REM containerlardaki `restart: unless-stopped` de bu yuzden ise yaramiyordu.
REM Kurulum: bu dosyanin kisayolu %APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup icine konur.

set PROJECT=D:\projects\netmovies
set LOG=%PROJECT%\autostart.log

REM 1) Docker Desktop calismiyorsa baslat.
tasklist /FI "IMAGENAME eq Docker Desktop.exe" | find /I "Docker Desktop.exe" >nul
if errorlevel 1 start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"

REM 2) Linux motoru hazir olana kadar bekle (en fazla ~5 dk).
for /L %%i in (1,1,60) do (
  docker info >nul 2>&1 && goto :ready
  timeout /t 5 /nobreak >nul
)
echo Docker motoru acilmadi - yigin baslatilamadi.>>"%LOG%"
exit /b 1

:ready
REM 3) Yigin idempotent baslatilir (zaten ayaktaysa dokunmaz).
cd /d "%PROJECT%"
docker compose up -d >>"%LOG%" 2>&1
echo %DATE% %TIME% yigin baslatildi>>"%LOG%"

REM 4) Kaynak raporu: domaini tasinan/olen eklentiyi ayni gun gor.
REM    Kaynak sessizce kuruyunca katalog kucululyor ama hata vermiyor; tek
REM    uyari isareti KAYNAK-UYARI.txt dosyasinin VARLIGI olsun.
for /L %%i in (1,1,40) do (
  docker exec netmovies-stream curl -sf -o NUL http://localhost:3310/api/v1/health && goto :saglik
  timeout /t 5 /nobreak >nul
)
echo %DATE% %TIME% gateway acilmadi - kaynak raporu atlandi>>"%LOG%"
exit /b 0

:saglik
del /q "%PROJECT%\KAYNAK-UYARI.txt" 2>nul
docker cp "%PROJECT%\scripts\kaynak-raporu.py" netmovies-engine:/tmp/kaynak-raporu.py >nul 2>&1
docker exec netmovies-engine python3 /tmp/kaynak-raporu.py > "%PROJECT%\kaynak-son.txt" 2>&1
REM Cikis 1 = sorunlu kaynak var. Aradaki `type` errorlevel'i ezdigi icin
REM once saklanir; ilk denemede uyari dosyasi tam bu yuzden hic olusmuyordu.
set SONUC=%ERRORLEVEL%
type "%PROJECT%\kaynak-son.txt">>"%LOG%"
if not "%SONUC%"=="0" copy /y "%PROJECT%\kaynak-son.txt" "%PROJECT%\KAYNAK-UYARI.txt" >nul
