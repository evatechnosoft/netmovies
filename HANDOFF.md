# Handoff: ev ağı cihazları, kamera, projektör (15 Eki akşam)
> 2026-10-15 ~18:30 · `fix/general-stability` @ 8484065 · 0 dirty · NetMovies kodu temiz; bu oturumun işi çoğunlukla ev ağı/cihaz

## Goal
Dean'in ev cihazlarını düzenlemek: DHCP ZimaOS'ta (ev-dhcp), kameralar HA'da, projektör tek uygulama (NetMovies),
eski Android kutu retro konsol. Ayrıntı hafızada: `ev-dhcp-zimaos`, `hikvision-kup-kamera`, `jest-servisi-c920`,
`kurulu-uygulamaya-onaysiz-dokunma` (memory dizini `~/.claude-team/projects/C--projects-netmovies/memory/`).

## State
- **TV 0.9.72 yayında** (8484065): kapalı `HomeAlias` (HOME kategorisi). GitHub `v0.9.72-poc`, evaglass
  `netmovies-tv-v0.9.72`, apps.json vc 972 (`8e502ff`), yerel OTA `data/apk` → `/api/v1/app_update` size 22768680.
  Yayınlanan APK **debug imzalı** (`assembleDebug`); `assembleRelease` İMZASIZ çıkıyor, onu yayınlama.
- **Projektör** (192.168.0.205:5555, HY300 Pro RK3326): 0.9.72 kurulu, `HomeAlias` enable + `set-home-activity` →
  HOME = NetMovies (adb çıktısıyla doğrulandı). Projectivy 4.70 kurulu duruyor. Yeniden başlatma testi yapılmadı.
- **El hareketi servisi = Hikvision** (`jest-hik`, `/DATA/AppData/jest`, host ağı, RTSP .218:554, UI :8178) → HA event `jest`.
  C920 emekli (gece görüşü zayıf): `jest` servisi compose'da `profiles: [c920]` arkasında, `up -d` onu başlatmaz.
  16 Eki: `jest-hik` ayakta ama kamera .218 "No route to host". Hareketler HA otomasyonuna bağlı değil.
- **Hikvision DS-2CD2423G0-IW**: sıfırlandı, aktive edildi (şifre Bitwarden), Wi-Fi Huntercjx, ev-dhcp .218 (Wi-Fi MAC
  d0:c5:d3:f5:d0:75) / .219 (kablo). HA `camera.192_168_0_218` 1080p snapshot alındı ~17:01.
  **18:30'da .218 cevap vermiyor, ARP'ta yok** — muhtemelen Dean gücü/kabloyu çekti; doğrulanmadı.
  Tek kamera kullanılıyor; başka kamera kurulumu yok (Dean, 16 Eki). Hareket algılama ve iki yönlü ses kapalı.
- **Eski Android kutu** (Amlogic MBX g02ref, Android 4.1.2, MAC 00:1c:89:3e:03:c0, ev-dhcp .206): ADB ağdan kapalı,
  şu an ağda yok. ZimaOS'ta `python3 -m http.server 8177` (`/DATA/AppData/kutu-indir`, 19:40'ta `timeout` ile kapanır):
  RetroArch 1.22.2 (F-Droid, minSdk 16), fceumm + gpsp çekirdekleri, 3 açık lisanslı NES oyunu.
- **Fold**: normal Instagram kaldırıldı; Facebook Lite + Instagram Lite kurulu; Messenger zaten yoktu.

## Next
1. Hikvision/HA işleri beklemede — Dean açmadıkça sorma (hafıza `ev-cihaz-sorma`).
2. Açık kalanlar (eski): ZimaOS WOL açılış takılması + BIOS kontrolü; Tab A9+ Wi-Fi kapalı (Dean açacak);
   Lenovo Instagram Lite; Smart Station ESPHome; Evaitec tünel cloud/my/dash/prod düşük.

## Don't repeat
- Dean'in cihazındaki uygulamayı (modlu bile olsa) onaysız kaldırma/değiştirme — Projectivy ayarları gitti.
- Kırık/crack'li APK (Projectivy Premium "C=TryRoom") kurma; korsan ROM sağlama.
- Wi-Fi ADB'de `svc wifi disable` çalıştırma (Tab A9+ Wi-Fi'sız kaldı).
- Git Bash'te `adb push ... /data/local/tmp` → `MSYS_NO_PATHCONV=1` şart, yoksa yol bozulur.
- itch.io indirmesini betikle çekme — `/file/<id>` "invalid key" veriyor; Dean tarayıcıdan indirsin.
- ZimaOS'ta yeni docker bridge ağı açılamıyor (havuz dolu) → `network_mode: host`.
- PowerShell'de `$h` ve `$H` aynı değişken; ssh'a şifre geçerken CR'yi sil (`tr -d '\r'`).

## Verify
git rev-parse --short HEAD                          # 8484065
curl -s 192.168.0.186:3310/api/v1/app_update?target=tv | grep -o '"tag":"[^"]*"'   # v0.9.72-poc
ssh zima 'docker ps --format "{{.Names}}" | grep -E "^(jest-hik|ev-dhcp|ha)$"'      # üçü de
adb -s 192.168.0.205:5555 shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1   # .HomeAlias

## Yeniden başlangıç promptu (yapıştır)
```
Ev ağı/cihaz oturumunun devamı. Durum: TV 0.9.72 yayında (projektör HOME = NetMovies), el hareketi servisi
Hikvision'dan (jest-hik → HA event; C920 emekli), Hikvision küp kamera kuruldu (Wi-Fi .218, HA camera.192_168_0_218, şifre Bitwarden) ama
son kontrolde çevrimdışıydı; eski Android kutu retro konsol için RetroArch bekliyor.
Ortam: C:\projects\evaitec\netmovies, PC DEANLOQ (fix/general-stability), ZimaOS ssh zima, ev-dhcp /DATA/AppData/ev-dhcp.
Önce HANDOFF.md oku, Verify bloğunu koş. Sıra: 1) Hikvision geri geldi mi, oda adı + panel 2) hareket→eylem
eşlemesi. Dean'in cihazında onaysız kaldırma yok. Yeni iş açma.
```
