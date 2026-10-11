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
