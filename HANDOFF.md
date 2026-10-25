# Handoff: TV 0.9.78 kumanda + ev DHCP kök düzeltme
> 2026-10-25 · `fix/general-stability` @ 888cd58 (origin'de) · 1 untracked (`.claude/settings.local.json`, işle ilgisiz)

## Goal
Kumanda düzeltmeleri canlıda (0.9.78); Mi Box'ta doğrulama bekliyor. Ayrıntı: `git show aeed5a0` mesajı.

## State
- **0.9.78 yayında:** `apk_publish.sh` EXIT=0; OTA tag `v0.9.78-poc`, `data/apk/NetMovies-TV-v0.9.78.apk` 8571730 bayt, apps.json commit 005b9434. İçerik: MediaSession (`ui/MedyaOturumu.kt`), medya/Menü tuşu, YUKARI tek basış bölüm listesi, basılı tutma tekrarı yutulur (`input/UzunBasisKapisi.kt` + 5 test).
- **Mi Box doğrulanmadı:** 23 Eki 21:36'dan beri ağda yok (ADB .11/.17/.55 yanıtsız). Dean: "boşver, olmuyorsa". Kutuya bir şey yapılmadı.
- **Ev DHCP (ZimaOS), 25 Eki kök düzeltme:** iki kök neden bulundu. (1) `dhcp-authoritative` + eth0/wlan0 aynı LAN'da → dnsmasq kendi eth0 ACK'ını wlan0'dan `DHCPNAK wrong server-ID` ile bozuyordu (açılıştan beri 9 NAK, aynı xid'de ACK+NAK). `ev.conf`'tan satır kaldırıldı (yedek `ev.conf.bak-20261025`), servis 12:42'de yeniden başladı, sonrasında 0 NAK. (2) `zimaos-app-management` `restart=no`'ya rağmen her açılışta eski `ev-dhcp` konteynerini başlatıyor (12:28:17), health-watch 70 sn sonra durduruyordu → her açılışta ~70 sn ikili DHCP. Yeni `dhcp-container-guard.service` (docker events dinler, start anında stop; enabled). Konteyner silinmedi (auto-mode sınıflandırıcı `compose down`'ı reddetti) — kalıcı çözüm Dean'de: `ssh zima 'export DOCKER_CONFIG=/tmp/dc; cd /DATA/AppData/ev-dhcp && docker compose down'` (imaj/conf/kira kalır).
- `~/.claude/scripts/session-context.py` düzeltildi (`~/.claude` commit 43d4831): alt proje devir notu yalnız git kökünde aranıyor, artık C:\projects'te NetMovies notu basılmıyor.

## Next
1. Kutu ağa dönünce: `adb connect 192.168.0.11:5555` (wifi .17) → `adb shell dumpsys media_session | grep -A3 netmovies` ile MediaSession kaydını gör; `adb logcat -d | grep -iE "FATAL|R8|Serializ"` ile R8 regresyonu ara.
2. Sezon geçişi (SOL/SAĞ) odak testi + Dean'in "kaldır-kur" isteği: prefs yedekle → uninstall → kur → prefs geri (hafıza `imza-anahtari-tasimada-kayboldu`).
3. Board'da WI yok — bu iş için açılmadı; açılacaksa etechlab'da.

## Don't repeat
- DHCP'den `interface=wlan0` çıkarma — Dean: telefonlara gerekli (hafıza `ev-dhcp-wlan0-kalsin`).
- `ev-dhcp` konteynerini yeniden başlatma — ikili DHCP sunucusu interneti düşürür; guard zaten anında durdurur. Konteynere dönüş gerekirse önce `systemctl disable --now dhcp-container-guard ev-dhcp`, sonra `docker start ev-dhcp`.
- `dhcp-authoritative`'i ev.conf'a geri koyma — iki arayüzle kendi kendini NAK'lar (hafıza `ev-dhcp-zimaos`).
- Yayın komutunu Dean'e verme; onay varsa `apk_publish.sh`'yi ajan koşar (hafıza `ota-yayini-kendin-yap`).
- Durdurulan konteynerleri silme/prune etme. Zima'da docker için `export DOCKER_CONFIG=/tmp/dc`.

## Verify
git rev-parse --short HEAD                                                          # 888cd58
curl -s 192.168.0.186:3310/api/v1/app_update?target=tv | grep -o '"tag":"[^"]*"'    # v0.9.78-poc
ssh zima 'systemctl is-active ev-dhcp dhcp-container-guard; sudo ss -ulpn | grep -c ":67 "'   # active active 2 (biri libvirt virbr0)
ssh zima 'sudo journalctl -b -u ev-dhcp --no-pager | grep -c "wrong server-ID"'           # 12:42 sonrası artmamalı (açılış öncesi 9)
ssh zima 'export DOCKER_CONFIG=/tmp/dc; docker ps -a --filter name=^ev-dhcp$ --format "{{.Status}}"'  # Exited

## Yeniden başlangıç promptu (yapıştır)
```
NetMovies devamı. TV 0.9.78 (kumanda: MediaSession, medya/Menü tuşu, YUKARI bölüm listesi,
basılı tutma tekrarı yutma) yayında, fix/general-stability @ 888cd58. Mi Box ağda yok, doğrulanmadı.
Ev DHCP: ZimaOS host ev-dhcp.service tek sunucu; eski ev-dhcp konteyneri durduruldu (ikili DHCP interneti
düşürüyordu; dhcp-container-guard.service start anında durdurur), dhcp-authoritative kaldırıldı (kendi NAK'ı).
Açık iş: Dean onayıyla `docker compose down` (konteyneri kalıcı sil).
Ortam: C:\projects\netmovies, ssh zima, DOCKER_CONFIG=/tmp/dc.
Önce HANDOFF.md oku, Verify bloğunu koş. Sıra: kutu dönerse ADB doğrulama (Next 1-2). Yeni iş açma.
```
