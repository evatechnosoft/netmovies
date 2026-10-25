# Handoff: TV 0.9.81 yayında + ev DHCP tek sunucu + modem yedek
> 2026-10-25 · `fix/dhcp-kok-duzeltme` @ 8655bcb (= origin/fix/general-stability) · 0 dirty (1 untracked `.claude/settings.local.json`, işle ilgisiz)

## Goal
Telefon KUMANDA kipinde arama telefon klavyesiyle, GERİ tuşu ilk basışta çalışır (0.9.80–0.9.81); ev DHCP tek sunucu + modem yedek. Ayrıntı: `git log ff7e03f..8655bcb` mesajları, `.claude/handoffs/latest.md`.

## State
- **TV 0.9.81 yayında:** `apk_publish.sh` EXIT=0; OTA `v0.9.81-poc`, GitHub `v0.9.81-poc`, apps.json. ZimaOS repo `cebc0d3`. Kök neden/kanıt: commit `cebc0d3` mesajı (emülatör, IME arama tuşu; 114/0 birim testi). **Dean'in S24'ünde doğrulanmadı.**
- 0.9.79 (ff7e03f): TV yerel sunucu seçiminde derlenmiş eth0 .186 hatırlanan wlan0 .174'ün önüne; 0.9.80 (f87db8f): `PhoneSearchScreen`. TELEFON kipi (cihazda izle) hâlâ ızgara klavye — PhoneSearchScreen yalnız TV'ye gönderir.
- **Ev DHCP:** ZimaOS host `ev-dhcp.service` active, tek sunucu; `ev-dhcp` konteyneri silindi (docker ps -a boş); NAK sayacı 9 (açılış öncesi, artmıyor); Netmaster DHCP yedek havuz .220–.254/1 gün. Dean kararı: modem NAK riski kabul, cihazlar MAC ile sabitlenir — yeniden raporlama. Plan: `~/.ai/scripts/home-net/ADRES-PLANI.md`.
- Hız ölçümü 25 Eki raporlandı: kablolu gigabit dolu, Wi-Fi ~80–130 Mbit; TV Zima'ya hep .186 ile.
- Mi Box: 0.9.79+ sonrası isteklerin .186'dan geldiği doğrulanmadı.

## Next
1. `adb connect 192.168.0.55:5555 && adb -s 192.168.0.55:5555 shell dumpsys package com.evaitec.netmovies.tv | grep versionName` → 0.9.81 beklenir; sonra `ssh zima "docker logs --since 10m netmovies-stream 2>&1 | grep -oE '192\.168\.0\.[0-9]+' | sort | uniq -c"` ile isteklerin .186'dan geldiğini gör.
2. Dean telefonda GERİ'yi onaylamazsa: `sh ~/.claude/jobs/e12b219d/tmp/back_test5.sh` benzeri akışı emülatörde IME tuşuyla (`input tap 993 1710`) koş; `input keyevent 66` onSearch'ü TETİKLEMEZ, yanıltır.
3. Dal kararı Dean'in: `fix/dhcp-kok-duzeltme` zaten `fix/general-stability` ile aynı commit; worktree silinebilir.

## Don't repeat
- DHCP'den `interface=wlan0` çıkarma (telefonlara gerekli); `dhcp-authoritative` geri koyma; `ev-dhcp` konteynerini geri getirme; Netmaster havuzunu .100–.179 ile çakıştırma (`nm_dhcp.py ac 220 35 86400`).
- Zima docker için `export DOCKER_CONFIG=/tmp/dc` şart. TV izlerken rebuild yok; boşta kontrolü `docker logs --since 1m netmovies-stream | grep -c remote/state`.
- Dean'e link/ADB/elle kurulum önerme — telefon evaitecOTA `netmovies-phone`'dan alır.
- Emülatör testinde donanım Enter ile arama (bkz. Next 2).

## Read first
1. `.claude/handoffs/latest.md` — 25 Eki tüm devir blokları (0.9.79/80/81, DHCP, hız)
2. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/PhoneSearchScreen.kt` — ara() + satır tıklaması, odak/klavye
3. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/MainActivity.kt` — kumanda kipi → PhoneSearchScreen, dispatchKeyEvent GERİ

## Verify
git rev-parse --short HEAD                                                          # 8655bcb
git status --porcelain | wc -l                                                      # 1 (settings.local.json)
curl -s 192.168.0.186:3310/api/v1/app_update?target=tv | grep -o '"tag":"[^"]*"'    # v0.9.81-poc
ssh zima 'cd /DATA/AppData/netmovies && git rev-parse --short HEAD'                 # cebc0d3
ssh zima 'systemctl is-active ev-dhcp; sudo journalctl -b -u ev-dhcp --no-pager | grep -c "wrong server-ID"'   # active, 9 (artmamalı)

## Yeniden başlangıç promptu (yapıştır)
```
NetMovies devamı. TV 0.9.81 yayında (telefon KUMANDA kipinde arama telefon klavyesiyle, GERİ tuşu
ilk basışta: klavye arama/kart adımında kapatılıyor; S24'te doğrulanmadı). fix/general-stability @ 8655bcb,
ZimaOS cebc0d3. Ev DHCP: ZimaOS ev-dhcp.service tek sunucu, konteyner silindi, Netmaster yedek havuz .220+.
Ortam: C:\projects\netmovies (worktree .claude/worktrees/dhcp-kok-duzeltme aynı commit), ssh zima, DOCKER_CONFIG=/tmp/dc.
Önce HANDOFF.md oku, Verify bloğunu koş. Sıra: Next 1 (Mi Box sürüm + .186 kanıtı), Dean dönüşü varsa Next 2.
Yeni iş açma.
```
