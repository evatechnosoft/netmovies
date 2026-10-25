# Handoff: TV 0.9.78 kumanda + ev DHCP tek sunucu + modem yedek
> 2026-10-25 · `fix/general-stability` @ 888cd58 (origin'de) · 1 untracked (`.claude/settings.local.json`, işle ilgisiz)

## Goal
Kumanda düzeltmeleri canlıda (0.9.78); Mi Box'ta doğrulama bekliyor. Ayrıntı: `git show aeed5a0` mesajı.

## State
- **0.9.78 yayında:** `apk_publish.sh` EXIT=0; OTA tag `v0.9.78-poc`, `data/apk/NetMovies-TV-v0.9.78.apk` 8571730 bayt, apps.json commit 005b9434. İçerik: MediaSession (`ui/MedyaOturumu.kt`), medya/Menü tuşu, YUKARI tek basış bölüm listesi, basılı tutma tekrarı yutulur (`input/UzunBasisKapisi.kt` + 5 test).
- **Mi Box doğrulanmadı:** 23 Eki 21:36'dan beri ağda yok (ADB .11/.17/.55 yanıtsız). Dean: "boşver, olmuyorsa". Kutuya bir şey yapılmadı.
- **Ev DHCP (ZimaOS), 25 Eki kök düzeltme + tek sunucu + modem yedek:** (1) `dhcp-authoritative` + eth0/wlan0 aynı LAN'da → dnsmasq kendi eth0 ACK'ını wlan0'dan `DHCPNAK wrong server-ID` ile bozuyordu; satır `ev.conf`'tan kaldırıldı (yedek `ev.conf.bak-20261025`), 12:42 sonrası 0 NAK (açılıştan beri sayaç 9'da sabit). (2) `zimaos-app-management` eski `ev-dhcp` konteynerini her açılışta başlatıyordu → konteyner `compose down` ile SİLİNDİ (imaj/conf/kira duruyor), `dhcp-container-guard.service` artık gereksiz ama zararsız. (3) Dean isteği: ZimaOS düşünce cihazlar modemden alsın → Netmaster DHCP AÇIK, ayrık havuz .220–.254, 1 gün (`~/.ai/scripts/home-net/nm_dhcp.py ac 220 35 86400`; geri alma `nm_dhcp.py kapat`). Ölçüm (`dhcp_probe.py`, `dhcp_request_probe.py`, Sonnet alt ajan + tekrar): DISCOVER'a .186→.161 ve .1→.220 teklifleri; modem havuz dışı INIT-REBOOT'a NAK veriyor (.150 → NAK). Risk: yeniden açılan cihaz NAK'la DISCOVER'a döner, modem teklifi önce gelirse rezervasyon yerine .220+ alır. Ayrıntı `~/.ai/scripts/home-net/ADRES-PLANI.md`.
- `~/.claude/scripts/session-context.py` düzeltildi (`~/.claude` commit 43d4831): alt proje devir notu yalnız git kökünde aranıyor, artık C:\projects'te NetMovies notu basılmıyor.

## Next
1. Kutu ağa dönünce: `adb connect 192.168.0.11:5555` (wifi .17) → `adb shell dumpsys media_session | grep -A3 netmovies` ile MediaSession kaydını gör; `adb logcat -d | grep -iE "FATAL|R8|Serializ"` ile R8 regresyonu ara.
2. Sezon geçişi (SOL/SAĞ) odak testi + Dean'in "kaldır-kur" isteği: prefs yedekle → uninstall → kur → prefs geri (hafıza `imza-anahtari-tasimada-kayboldu`).
3. Board'da WI yok — bu iş için açılmadı; açılacaksa etechlab'da.

## Don't repeat
- DHCP'den `interface=wlan0` çıkarma — Dean: telefonlara gerekli (hafıza `ev-dhcp-wlan0-kalsin`).
- `ev-dhcp` konteynerini geri getirme (`compose up`) — ikili DHCP interneti düşürür; guard start anında durdurur. Host servisi tek sunucudur.
- `dhcp-authoritative`'i ev.conf'a geri koyma — iki arayüzle kendi kendini NAK'lar (hafıza `ev-dhcp-zimaos`).
- Yayın komutunu Dean'e verme; onay varsa `apk_publish.sh`'yi ajan koşar (hafıza `ota-yayini-kendin-yap`).
- Netmaster DHCP havuzunu .100–.179 ile çakıştırma; `nm_dhcp.py ac` çağrısı her zaman `220 35 86400` ile. Zima'da docker için `export DOCKER_CONFIG=/tmp/dc`.

## Verify
git rev-parse --short HEAD                                                          # 888cd58
curl -s 192.168.0.186:3310/api/v1/app_update?target=tv | grep -o '"tag":"[^"]*"'    # v0.9.78-poc
ssh zima 'systemctl is-active ev-dhcp dhcp-container-guard; sudo ss -ulpn | grep -c ":67 "'   # active active 2 (biri libvirt virbr0)
ssh zima 'sudo journalctl -b -u ev-dhcp --no-pager | grep -c "wrong server-ID"'           # 12:42 sonrası artmamalı (açılış öncesi 9)
ssh zima 'export DOCKER_CONFIG=/tmp/dc; docker ps -a --filter name=^ev-dhcp$ --format "{{.Status}}"'  # boş (konteyner silindi)

## Yeniden başlangıç promptu (yapıştır)
```
NetMovies devamı. TV 0.9.78 (kumanda: MediaSession, medya/Menü tuşu, YUKARI bölüm listesi,
basılı tutma tekrarı yutma) yayında, fix/general-stability @ 888cd58. Mi Box ağda yok, doğrulanmadı.
Ev DHCP: ZimaOS host ev-dhcp.service tek sunucu; eski ev-dhcp konteyneri durduruldu (ikili DHCP interneti
düşürüyordu; dhcp-container-guard.service start anında durdurur), dhcp-authoritative kaldırıldı (kendi NAK'ı).
Konteyner silindi; Netmaster DHCP yedek havuz .220–.254/1 gün açık (nm_dhcp.py). Dal fix/dhcp-kok-duzeltme → general-stability'ye merge bekliyor.
Ortam: C:\projects\netmovies, ssh zima, DOCKER_CONFIG=/tmp/dc.
Önce HANDOFF.md oku, Verify bloğunu koş. Sıra: kutu dönerse ADB doğrulama (Next 1-2). Yeni iş açma.
```
