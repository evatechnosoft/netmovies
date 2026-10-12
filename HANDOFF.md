# Handoff: Mi Box/LG/HA aç-kapa + ev ağı (12 Eki akşam)
> 2026-10-12 ~19:30 · `fix/general-stability` @ 28d5afd (+bu commit) · ZimaOS 1.186 ayakta

## State
- TV 0.9.69 üç yerde yayında (`v0.9.69-poc`, evaglass `c68513c`) ve Mi Box'ta açılıyor. ADB kurulumu ANR
  veriyordu → `cmd package compile -m speed -f` (hafıza `adb-kurulum-dexopt-anr`). UyanikTut kaldırıldı (işe yaramıyordu).
- Mi Box: `stay_on_while_plugged_in=7`, screen_off max, deviceidle disable → kendiliğinden uyumaz. Güç tuşu yine derin
  uyku (BT+Wi-Fi kapanır, ağdan açılmaz, kumanda bazen uyandıramaz) → Dean kutunun güç tuşuna basmayacak, LG kapanacak.
  Kutu yeniden başlayınca **ağ ADB (5555) kapanıyor** — Dean USB hata ayıklamayı kapat/aç yapacak. USB'deki
  cihaz Realtek 802.11ac (Wi-Fi anteni); Dean "USB-Ethernet çevirici" dedi, eth0 hiç görünmedi, doğrulanmadı.
- HA (http://192.168.1.186:8123, HA_TOKEN vg.env): webostv `LG webOS TV 65A6500USWF` (1.175) kapatma OK, açma YOK —
  TV kapanınca 1.175 ağdan düşüyor (Hızlı Başlatma+/Mobil TV Açma Dean'de doğrulanmadı). Otomasyon `lg_tv_ac_wol`
  (webostv.turn_on → button.wake_on_lan_d0_a4_6f_c9_28_80). 0.63'teki LGwebOSTV BAŞKA bir LG (wrong_device).
  samsungtv `43" eva` (192.168.0.27, Huntercjx; DHCP kayar, Netmaster rezervasyonu YOK) kapatma OK, açma NAT ötesi
  çalışmadı. M7 1.184: eşleşme 3 kez auth_missing → Samsung muhtemelen reddi hatırlıyor; Cihaz Bağlantı Yöneticisi'nden izin.
  `media_player.mybox` (androidtv_remote) var.
- ZimaOS eth0 "Supervisor eth0": `ipv4.method auto` + ek sabit 192.168.1.186 (nmcli); MR200 rezervasyonu
  38:14:28:35:9A:AE→.186. Netmaster'a taşınma (tek ağ, 200 Mb) PLANLI, yapılmadı. Netmaster: 200/20, SNR 40 dB,
  Huntercjx yalnız 2,4 GHz (LG 52 Mb, eva 13 Mb).
- Yedek donanım: SG108E/SG1008D gigabit switch, EAP115 (2,4 GHz, PoE) + Tenda PoE, WR940N/WR841N/W9960/Everest (çekmece).
  Kurgu: Netmaster yerinde, odaya tek kablo → SG108E; MR200 AP kipi TV yanında (5 GHz). TV konumu sorulmadı/cevapsız.

## KARAR (Dean, 12 Eki 19:50): TEK AĞ. Öneri 0.x (Netmaster), MR200 AP kipi TV yanında. Tarih ve TV konumu Dean'den bekleniyor.
Samsung M7 (1.184) menüsünde "Mobil ile Güç Açma" etkin, IP Kumandası açık (fotoğraf) ama cihaz taramada görünmedi;
eva (0.27) HA'da yeniden eklendi ve açık. Mi Box ağ ADB hâlâ kapalı.

## Next
1. Mi Box ADB açılınca: `adb connect 192.168.1.105:5555`; eth0 var mı bak; yoksa Ethernet fikrinden vazgeç.
2. LG 1.175: Dean iki ayarı doğrulayınca kapat → `media_player.turn_on` → 1.175:3000 testi. Olmazsa kablo (MR200 AP) planı.
3. M7 1.184 izin verilince samsungtv flow host=192.168.1.184; eva için Netmaster'da 68:FC:CA:B5:0F:6C rezervasyonu.
4. Netmaster'a taşınma: ayrı slot; .186 referansları (apps.json ayna, APK adayları, ssh, HA cihaz adresleri).

## Don't repeat
- Kutuda PARTIAL_WAKE_LOCK ile "ağda tut": uykuda radyo kapanıyor, hiçbir şey tutmuyor.
- ZimaOS'u sabit IP ile başka modeme takmak: ulaşılamaz olur (artık DHCP+ek adres).
- Dean'e kurulum linki/ADB eşleme kodu önerme (hafıza `telefona-kurulum-evaitecota`).

# Handoff: Lioness S3B8 = DiziAbi (12 Eki)
> 2026-10-12 · `fix/general-stability` @ ce84535 · ZimaOS stream+engine yeniden derlendi (06:53Z, engine 09:28Z)

- Rebuild yapıldı: çocuk modu `/api/v1/cocuk` 200, konteyner testleri 253 OK.
- DiziMom'un Lioness S3B8 sayfası S1E8 videosunu taşıyor (54:55; gerçek final 70 dk). Yeni eklenti `engine/Plugins/DiziAbi.py`
  (pilavyer oynatıcı, AES HLS, tr+en ses, tr/en altyazı). S3B8 = 4084 sn. Emülatörde uçtan uca oynadı (position ilerledi).
- Devam Et kaydı `lioness` → DiziAbi S3B8 0 sn; yinelenen `special ops lioness` kaydı silindi.
- Mi Box o sırada kapalıydı, gerçek cihazda denenmedi.
- `scripts/chain_scan.py` bir load_item 504'ünde tüm taramayı düşürüyor (DiziAbi ile ilgisi yok, düzeltilmedi).
- Çocuk paketi Samsung'a değil ayrı cihaza; Dean IP + cihaz tipini iletecek.

# Handoff: Samsung M7 uygulaması + çocuk modu (12 Eki yüklenecek)
> 2026-10-11 gece · `fix/general-stability` @ 8671d93 · push edildi

## State
- Samsung M7 (192.168.1.184): `client-tizen` 0.1.5 kurulu, `/tv` açıyor; hls.js ile oynatma, GERİ=461, OK basılı tutma, oynatıcıda YUKARI menüsü. tv.html.j2 docker cp ile CANLI (rebuild'siz).
- CANLI DEĞİL (rebuild bekliyor): çocuk modu Python (`1712eaa`: /api/v1/cocuk, admin "Çocuk Modu", admin.js) + engine `8671d93` (alternatif sağlayıcı detay adı koruması).
- Lioness: /tv'de `kurtar()` (load_item) canlı — DiziMom 24 bölüm, S3B8 idx 23. Dean bu sorunu defalarca bildirdi; cihazda doğrulanmadı.
- HA: Tuya Local + `remote.ir_rf_hub` (S11, 192.168.1.106). HA_TOKEN `~/.ai/vg.env`. Tuş öğretme yapılmadı.

## Next (12 Eki, Mi Box boşta iken)
1. `ssh zima "export DOCKER_CONFIG=/tmp/dc; cd /DATA/AppData/netmovies && git pull -q && docker compose --profile tunnel up -d --build stream engine cloudflared"` + konteyner unittest.
2. Samsung'da Lioness Devam Et → S3B8 açılıyor mu (client_log).
3. Panelden kanal ekle → çocuk paketi 0.1.6 kur (`scratchpad\tizen_app_016` ya da client-tizen'den paketle; README) → CDP kanıt. Çocuk paketi Samsung M7'ye DEĞİL, ayrı bir cihaza kurulacak — Dean IP'sini iletecek (12 Eki).

# Handoff: Mi Box uyanık + Lioness + tvQA
> 2026-10-11 · `fix/general-stability` @ `93d800a` (+ bu handoff commit'i) · 0 dirty · origin ile eşit

## Goal
Reklamsız TV (client-tv, Mi Box) + ZimaOS sunucu; kurallar `CLAUDE.md`. Bu oturum: Mi Box uykuda ağdan
düşüyordu, Lioness yanlış videoyu açıyordu, kumanda eşleyicisi (tvQuickActions, tvQA) toparlandı.

## State
- TV 0.9.68 (`UyanikTut` PARTIAL_WAKE_LOCK) üç yerde yayında (sha `351e41f1…`, GitHub `v0.9.68-poc`,
  evaglass `netmovies-tv-v0.9.68` + apps.json vc 968 `7673d6b`) ve Mi Box'a ADB ile kuruldu (`versionName=0.9.68`).
  Kök neden: 0.9.68 10 Eki commit edilmiş ama yayınlanmamıştı; kutu 0.9.67'de kaldı, uykuda Wi-Fi'den düştü
  (TP-Link istemci listesinde yoktu, WOL tutmaz). unverified: kapama tuşundan sonra kutunun ağda kaldığı — Dean denemedi.
- Lioness: `load_item` kurtarması DiziPal ölü adresini YouTube yemek kanalına bağlıyordu. `e591cdd` (dizi
  kurtarması ≥2 bölüm) + `93d800a` (kurtarılan detayın adı başlığı tutmalı). Canlıda: `load_item` →
  `DiziMom Special Ops: Lioness 24`. Stream testleri 245 OK. ZimaOS stream `.Created` 2026-10-11 ~13:2xZ.
- tvQA: erişilebilirlik servisi kutu fişten açılınca kapanmıştı → `settings put secure ...` ile açıldı (BACK çift = son uygulamalar).
  Ayar yedeği/değişikliği: scratchpad `tvqa/` (orijinal `tvqa.ab`, yeni `tvqa_v2.ab`); ZimaOS atv konteynerinde
  `/app/data/atv/tvqa.ab` (orijinal) ve `tvqa_v2.ab`. v2 = Settings menüsünden Wi-Fi/BT toggle çıktı (OTA + fare),
  Apps menüsü kurulu uygulamalar, dock temizlendi (+OTA), boot tetikleyicisi NetMovies'i açar. Netflix tuşu (keycode 1000 satırı) AYNEN.
  `adb restore tvqa_v2.ab` çıkış 0 (Dean ekranda onayladı) ama restore erişilebilirlik ayarını SİLDİ → yeniden açıldı
  (`dumpsys accessibility` tvQuickActions 1). Dean'in 16:40 fotoğrafı: dock hâlâ ESKİ (tvQA, mor oynat, Play Store, Projectivy; OTA yok)
  → restore ayarları UYGULAMADI. Tekrar deneme yok; Dean isterse tvQA arayüzünden elle düzenlenir. Netflix = Dean'in "papatya düğmesi", dokunma.
- Prime tuşu tvQA ile Xiaomi'de değişmez (tvdevinfo.com/tvquickactions/troubleshooting). Sistem
  `com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity` başlatır; tek yol o paket adlı stub APK — yazılmadı, Dean'e soruldu.
- ZimaOS LiteLLM: `yerel` (gemma4:12b) `reasoning_effort: none` ile dolu cevap; yeni `yerel-hizli` (gemma4:e2b GPU, 18–25 tok/s). Ayrıntı `~/.ai/contracts/zimaos-infrastructure.md`.

## Next
1. Dean'den 0.9.68 kapama/açma geri bildirimi: kapattıktan 1–2 dk sonra `ssh zima "nc -z -w3 192.168.1.105 5555 && echo agda"`.
   `agda` değilse UyanikTut Wi-Fi'yi tutmuyor → `WifiManager.WifiLock(WIFI_MODE_FULL_HIGH_PERF)` ekle.
2. tvQA restore Dean onayıyla bittiyse doğrula: `adb shell settings get secure enabled_accessibility_services` dolu mu,
   menüler/dock tvQA'da yeni hâlinde mi. Bozuksa `adb restore /app/data/atv/tvqa.ab` (orijinal).
3. Prime → Ayarlar istenirse: client-tv'de ayrı küçük modül, paket `com.amazon.amazonvideo.livingroom`, aktivite `com.amazon.ignition.IgnitionActivity` → `Settings.ACTION_SETTINGS`.
4. `/saglik` (atv köprüsü) kutu ağda yokken `acik/uyanik:true` dönüyor — bayat durum; düzeltilmedi.

## Don't repeat
- Commit ≠ yayın: TV değişikliği bitince üç yere yayınla (hafıza `yayinlamak-icin-sorma`), kutuda `versionName` ile doğrula.
- Kutu Wi-Fi'de yokken WOL göndermek: Mi Box WoWLAN yok, paket boşa gider. Önce TP-Link `tp_dump.py "Network Map>Wireless Clients"`.
- TV izlerken kurulum/rebuild: `/tmp/tv_kur.sh` ve `scripts/izleme-bitince-kur.sh` boşta bekler; `remote/status` ile bak.
- tvQA'da Netflix tuşunu adb_command'a bağlamak / tuşları getevent ile denemek: Dean "deneme yapma, Netflix kalsın" dedi; getevent+backup tvQA'yı kilitledi.
- adb backup/restore TV ekranında onay ister — Dean izlerken başlatma. Restore sonrası erişilebilirlik servisi kapanır: hemen `settings put secure enabled_accessibility_services dev.vodik7.tvquickactions/.KeyAccessibilityService` + `accessibility_enabled 1`.

## Verify
```
git rev-parse --short HEAD                                   # 93d800a veya handoff commit'i
bash scripts/smoke.sh | tail -1                              # YEŞİL
ssh zima "docker exec netmovies-atv adb -s 192.168.1.105:5555 shell dumpsys package com.evaitec.netmovies.tv | grep -m1 versionName"   # 0.9.68
curl -s -G 192.168.1.186:3310/api/v1/load_item --data-urlencode plugin=DiziPal --data-urlencode encoded_url=https://dizipal2221.com/dizi/lioness --data-urlencode title=Lioness --data-urlencode type=serie | head -c 200   # DiziMom
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability. 11 Ekim: TV 0.9.68 (kutuyu ağda tutan UyanikTut) yayında ve Mi Box'a kurulu;
kapama sonrası ağda kalma Dean'de doğrulanmadı. Lioness yanlış YouTube videosu sorunu sunucuda düzeldi (DiziMom).
tvQuickActions ayarları adb restore ile toparlandı (Netflix tuşu aynı; Prime Xiaomi'de değişmez).
Önce HANDOFF.md oku, Verify bloğunu koş.
Sıra: 1) kapama sonrası kutu ağda mı (nc .105:5555) 2) tvQA restore doğrula 3) Prime stub yalnız Dean isterse.
Yeni iş açma; TV oynarken rebuild/kurulum yapma.
```
