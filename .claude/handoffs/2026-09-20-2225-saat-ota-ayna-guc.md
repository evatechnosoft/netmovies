# DEVİR — 20 Eylül 2026, 22:25 · saat düzeltmesi + OTA ayna + kutu gücü

**Dallar:** netmovies `fix/general-stability` · evaitec-appkit `feature/ota-ayna-indirme` (PR AÇILMADI)
**Sürümler:** TV/telefon **0.9.12 (912)** · saat **0.1.16 (116)** ·
evaitecOTA tv 0.1.13 / telefon 0.1.11 / saat 0.1.12
**Yerel adres:** `http://192.168.1.185:3310` · kutu (Mi Box) **192.168.1.189**

## Bu oturumda kapanan işler

**0.9.10 — "ağ hatası":** sunucunun LAN adresi DHCP ile kayıyor, `ServerResolver`
adresi yalnız açılışta çözüp cache'liyordu. `BaseUrlInterceptor` artık IOException'da
seçimi sıfırlayıp yeniden keşfediyor, isteği yeni adrese bir kez tekrarlıyor.
`BaseUrlInterceptorTest` 2 test.

**0.9.11 — oynatıcı yön tuşları:** SOL/SAĞ/AŞAĞI'nın basılı tutması da `OPEN_BAR`.
Ekranda iki şerit var: `ControlsOverlay` D-pad ile gezilemez (düğmeleri dokunmatik
için), gezilebilir tek şerit `QuickPad`.

**0.9.12 — ses + widget:** kumanda barına "Sesi kapat/aç" (AudioManager
ADJUST_TOGGLE_MUTE + FLAG_SHOW_UI — cihazın gerçek medya sesi). Widget OkHttp
kullanmadığı için adres toparlanması oraya gelmiyordu; `tabanla()` sarmalayıcısı
eklendi (durum, komut, oynat, sesli komut).
> Dean "ses kapa değil, BOX kapa aç" dedi — ses düğmesi istenmemişti, duruyor.
> Kaldırılsın mı diye soruldu, cevap gelmedi.

**Saat 0.1.16 — açılmıyordu, KÖK NEDEN:** `MainActivity` sınıfı `MainActivity.kt`'den
düşmüştü. Manifest `.MainActivity` arıyor, kaynakta sınıf yok, dex'te yalnız
`MainActivityKt` var → kurulum başarılı, açılışta `ClassNotFoundException`.
R8 suçlu değildi. Kanıt: emülatörde 0.1.15 FATAL, 0.1.16 FATAL yok + süreç ayakta.

**evaitecOTA ayna indirme (evaitec-appkit):** ölçüm — aynı 6,7 MB APK
GitHub **147 KB/s** · Cloudflare tüneli **520 KB/s** · ev ağı **2.164 KB/s**.
Katalog kaydına `mirrors` alanı eklendi, `CatalogClient.download` aday LİSTESİ
alıyor, sırayla deniyor. Sıra: ev → tünel → GitHub (katalogdaki `downloadUrl` hep
son çare). LAN düz HTTP olduğu için http yalnız özel IP aralıklarına açık.
`apps.json`'da netmovies tv/phone/watch kayıtlarına 2'şer ayna yazıldı.
appkit testleri 16/0.

## Yarım kalan — KUTU GÜCÜ (sıradaki iş)

Kutu kendini kapatamaz (DEVICE_POWER sistem izni). Çözüm Android TV Remote v2
protokolü (6466/6467 — Google TV / Xiaomi Home'un kullandığı protokol).
- **Eşleşme TAMAM:** Xiaomi MIBOX4, sertifikalar `data/atv/` (gitignored).
- **Köprü:** `scripts/atv_power.py` — PC'de çalışır, `/saglik` · `/guc` · `/tus/<AD>`.
  Doğrulandı: `{"ok": true, "acik": true, "cihaz": {... MIBOX4 ...}}`.
- **ENGEL:** stream konteyneri köprüye ulaşamıyor. Docker Desktop NAT'ı LAN'a ve
  host'a kapalı (`192.168.1.x` her portta timeout, `172.31.0.1:3311` refused,
  `host.docker.internal` DNS yok).
- **Planlanan çözüm:** yönü ters çevir — köprü sunucuyu yoklasın (poll), `power`
  komutunu görünce kutuya bassın. Dışa giden bağlantı olduğu için firewall kuralı
  gerekmez. Sunucuda `/api/v1/remote/command` type=power kuyruğu + widget düğmesi.
- POWER tuşu CİHAZDA HİÇ DENENMEDİ (Dean izlerken uyutmamak için).

## Doğrulanmadı
- 0.9.10/0.9.11/0.9.12'nin TV'deki davranışı (oynatıcı içi hiçbir şey cihazda görülmedi).
- Saat 0.1.16 gerçek saatte açılmadı (emülatörde açıldı).
- evaitecOTA'nın ayna sırası gerçek cihazda denenmedi.
- evaitec-appkit dalı push'landı ama **PR açılmadı, main'e girmedi**.

## Tuzaklar
- İki OTA kanalı ayrı: uygulama içi OTA `evatechnosoft/netmovies` `/releases`
  (`vX.Y.Z-poc`), mağaza `evaglass-releases` (`netmovies-tv-vX.Y.Z` + `apps.json`).
- evaitecOTA APK'ları **assembleRelease** ile üretilir (imzalı + R8). Debug build
  20 kat büyük çıkıyor (TV 190 KB → 3 MB).
- `gh release create --target main` bu repoda 422 → ana dal `master`.
- evaglass-releases'e push'tan önce `git reset --hard origin/main` + değişikliği
  yeniden uygula (apps.json'a başka projeler de yazıyor).
- Docker konteynerinden ev ağına erişim YOK; LAN işi host'ta çalışmalı.
- `docker exec` + Git Bash: yolları `MSYS_NO_PATHCONV=1` ile geç.

## Temizlik
`data/apk` 2,0 G → 65 M · repo kökündeki 31 APK (589 MB, 12'si takipliydi) silindi.
`.git` 245 M — geçmişteki APK'lar duruyor, temizlik `filter-repo` + force push ister.
