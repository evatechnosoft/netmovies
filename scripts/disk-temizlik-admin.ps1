# NetMovies / Dean — yonetici gerektiren disk temizligi.
# Calistirma: Yonetici PowerShell -> powershell -ExecutionPolicy Bypass -File <bu dosya>

Write-Host "== 1/3  Hazirda bekletme kapatiliyor (hiberfil.sys ~25 GB)" -ForegroundColor Cyan
powercfg -h off
if (Test-Path C:\hiberfil.sys) { Write-Host "  hiberfil.sys hala duruyor" -ForegroundColor Yellow }
else { Write-Host "  silindi" -ForegroundColor Green }

Write-Host "== 2/3  Docker sanal diski sikistiriliyor (~35 GB)" -ForegroundColor Cyan
$vhdx = "$env:LOCALAPPDATA\Docker\wsl\disk\docker_data.vhdx"
Get-Process "Docker Desktop" -EA SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 8
wsl --shutdown
Start-Sleep -Seconds 6
"{0:N1} GB -> sikistiriliyor..." -f ((Get-Item $vhdx).Length/1GB)
@"
select vdisk file="$vhdx"
attach vdisk readonly
compact vdisk
detach vdisk
exit
"@ | Set-Content -Encoding ascii "$env:TEMP\compact.txt"
diskpart /s "$env:TEMP\compact.txt"
"{0:N1} GB" -f ((Get-Item $vhdx).Length/1GB)
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"

Write-Host "== 3/3  WinSxS bilesen temizligi (2-5 GB, birkac dakika)" -ForegroundColor Cyan
Dism.exe /Online /Cleanup-Image /StartComponentCleanup /ResetBase

Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" |
    Select-Object DeviceID, @{n='BosGB';e={[math]::Round($_.FreeSpace/1GB,1)}} | Format-Table -AutoSize
Write-Host "Bitti. Docker acildiktan sonra: docker compose --profile tunnel up -d" -ForegroundColor Green
