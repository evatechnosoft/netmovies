# Handoff: ev ağı cihazları, kamera, projektör (15 Eki akşam)
> 2026-10-17 öğle · `fix/general-stability` @ d6dbb7c · 0 dirty · NetMovies kodu temiz; iş çoğunlukla ev ağı/cihaz
> 17 Eki (WORKSTATION, Precision 3551, 192.168.0.119): ZimaOS .186 ssh/3310 zaman aşımı — kapalı/ağsız, WOL denenmedi. Projektör .205 ve Hikvision .218 de cevapsız.
> **19 Eki (DEANLOQ): ZimaOS AYAKTA** — 3310 `v0.9.72-poc`, konteyner jest-hik/netmovies-stream/ha up, ev-dhcp dnsmasq host süreci (konteyner değil; Verify grep'i bulamaz). Dal laptop↔WORKSTATION ayrışması rebase+push ile birleşti (HANDOFF çakışması: 17 Eki metni tutuldu), `31ffe6a`. .205/.218 ve smoke yoklanmadı.
> 19 Eki Next 1-3 bitti: ödev PDF linki 200 application/pdf 1159691 B; geçici isimsiz anahtar artık yok (auth-tokens: yalnız 11/13 Chrome, 12 mirall); 17 Eki düşüşünün kökü bulunamaz — journal ~40 sa tutuyor (10×50 MB, icewhale-files health gürültüsü), 17 Eki silinmiş, boot 17 Eki 15:43.
> Tünel (diff "no changes"): cloud 302, w 303, fit 200 OK. **my** → evaiteclabs :3001 ayakta, yalnız DNS CNAME yok. **dash** → `https://localhost:3000` = AdGuard yönetim paneli (http, DNS de yok). **prod** → `https://192.168.0.186:10443` = Nextcloud 443 (502). Karar Dean'de: my için CNAME açılsın mı; dash (AdGuard paneli dışarı açmak riskli) ve prod (cloud ile mükerrer) silinsin mi.

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

- **5. sınıf matematik ödevi** (17 Eki öğle, WORKSTATION): Jett Fasikül 1'in 4–33. sayfalarından 20 soru,
  cevap işaretleri silinmiş, 4 sayfa + cevap anahtarı. Nextcloud `dean` → `Ders Kitapları/5_Sinif_Matematik_Tema1_20_Soru.pdf`,
  paylaşım `https://cloud.evaitec.com/s/BosMiZbWQFt55i5`. Link oluştu (OCS 200) ama hemen ardından ZimaOS düştü;
  indirme **doğrulanmadı**. Üretici betik WORKSTATION scratchpad'de (`odev.py`), sayfa görselleri `jett/p/`.
- Linki açmak için Nextcloud `dean`'e `occ user:auth-tokens:add -n` ile **isimsiz geçici uygulama anahtarı** eklendi, silinmedi.
- **WORKSTATION (eski PC)** `~/.claude/settings.json`: LOQ'tan eşitlenen `C:/Users/Dean` + `C:/Python312` hook yolları
  her komutu engelliyordu; yalnız yerelde `Deacjx` yollarına çevrildi, **commit edilmedi** (sonraki eşitleme ezer).
  Aynı PC'de D:→C: hafıza kopyası (15 proje) ve skill/betik yol düzeltmesi yapıldı (`~/.claude` c26240f, `~/.ai` 0c9e855);
  bunlar LOQ için de geçerli mi bakılmadı.

## Next
1. ZimaOS ayakta mı: `curl -s -m5 -o /dev/null -w "%{http_code}" http://192.168.0.186:3310/` — değilse
   `python ~/.ai/scripts/home-net/zima_wol.py` (arka planda), sonra `curl -sLo /tmp/o.pdf -w "%{http_code} %{content_type}" https://cloud.evaitec.com/s/BosMiZbWQFt55i5/download` → 200 application/pdf.
2. Geçici anahtarı sil: `ssh zima 'docker exec -u www-data nextcloud php occ user:auth-tokens:list dean'` → en yeni isimsiz kayıt → `user:auth-tokens:delete dean <id>`.
3. ZimaOS 17 Eki ~12:00 düşüşü: `ssh zima 'journalctl -b -1 | tail -50'`, ağ bekçisi logu (hafıza `zima-ag-bekcisi`).
4. Hikvision/HA işleri beklemede — Dean açmadıkça sorma (hafıza `ev-cihaz-sorma`).
5. Açık kalanlar (eski): ZimaOS WOL açılış takılması + BIOS kontrolü; Tab A9+ Wi-Fi kapalı (Dean açacak);
   Lenovo Instagram Lite; Smart Station ESPHome; Evaitec tünel cloud/my/dash/prod düşük.

## Don't repeat
- Dean'in cihazındaki uygulamayı (modlu bile olsa) onaysız kaldırma/değiştirme — Projectivy ayarları gitti.
- Kırık/crack'li APK (Projectivy Premium "C=TryRoom") kurma; korsan ROM sağlama.
- Wi-Fi ADB'de `svc wifi disable` çalıştırma (Tab A9+ Wi-Fi'sız kaldı).
- Git Bash'te `adb push ... /data/local/tmp` → `MSYS_NO_PATHCONV=1` şart, yoksa yol bozulur.
- itch.io indirmesini betikle çekme — `/file/<id>` "invalid key" veriyor; Dean tarayıcıdan indirsin.
- ZimaOS'ta yeni docker bridge ağı açılamıyor (havuz dolu) → `network_mode: host`.
- PowerShell'de `$h` ve `$H` aynı değişken; ssh'a şifre geçerken CR'yi sil (`tr -d '\r'`).
- fliphtml5 sayfa listesi şifreli (`fliphtml5_pages` "v0100..."); düz `files/large/N.jpg` 404. Playwright ile sayfayı açıp
  `window.fliphtml5_pages[].n` oku. Bu kopyada cevaplar kırmızı kutuda — kutu kırmızı zemin + kalın siyah harf; silip normal harf yaz.
- Ödev kapsamı: "Açı Ölçümü"ne kadar ve kuşlu paralel sorusunda biter; harita/yamuk soruları işlenmedi (Dean).

## Verify
git rev-parse --short HEAD                          # 4e0f933 (veya bu devir commit'i)
curl -s 192.168.0.186:3310/api/v1/app_update?target=tv | grep -o '"tag":"[^"]*"'   # v0.9.72-poc
ssh zima 'docker ps --format "{{.Names}}" | grep -E "^(jest-hik|ev-dhcp|ha)$"'      # üçü de
adb -s 192.168.0.205:5555 shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1   # .HomeAlias

## Yeniden başlangıç promptu (yapıştır)
```
Önce: ZimaOS 17 Eki öğle düştü. Ödev PDF'i Nextcloud'a yüklendi (link https://cloud.evaitec.com/s/BosMiZbWQFt55i5,
indirme doğrulanmadı) ve dean'de silinmemiş geçici uygulama anahtarı var — Next 1-3'ü yap, Dean'e linkin çalıştığını söyle.
Ev ağı/cihaz oturumunun devamı. Durum: TV 0.9.72 yayında (projektör HOME = NetMovies), el hareketi servisi
Hikvision'dan (jest-hik → HA event; C920 emekli), Hikvision küp kamera kuruldu (Wi-Fi .218, HA camera.192_168_0_218, şifre Bitwarden) ama
son kontrolde çevrimdışıydı; eski Android kutu retro konsol için RetroArch bekliyor.
Ortam: C:\projects\netmovies, PC `hostname` ile kontrol et (DEANLOQ / WORKSTATION) (fix/general-stability), ZimaOS ssh zima, ev-dhcp /DATA/AppData/ev-dhcp.
Önce HANDOFF.md oku, Verify bloğunu koş; ZimaOS cevapsızsa `python ~/.ai/scripts/home-net/zima_wol.py` arka planda. Sıra: 1) Hikvision geri geldi mi, oda adı + panel 2) hareket→eylem
eşlemesi. Dean'in cihazında onaysız kaldırma yok. Yeni iş açma.
```
