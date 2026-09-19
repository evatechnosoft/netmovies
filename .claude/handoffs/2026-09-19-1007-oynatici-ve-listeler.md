# DEVİR — 19 Eylül 2026, 10:07 · oynatıcı seçeneği + anlamlı listeler + TMDB bölüm özeti

**Dal:** `fix/general-stability` @ `d0baae9` · 0 kirli dosya · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `c4ef676` (push EDİLDİ)
**Sürümler:** TV/telefon **0.8.1 (vc 801)** · saat 0.1.15 (bu oturumda dokunulmadı)
**Adresler:** yerel `http://192.168.0.29:3310` · tünel `https://w.evaitec.com` (200)

## Önce doğrula

```bash
git rev-parse --short HEAD                                   # d0baae9
bash scripts/smoke.sh                                        # kapı YEŞİL
curl -s "localhost:3310/api/v1/app_update?target=tv"         # v0.8.1-poc
curl -s "localhost:3310/api/v1/episode_overviews?title=Neagley&season=1" | head -c 200
curl -s localhost:3310/api/v1/client_log                     # çökme izi buraya düşer
```

## Bu oturumda ne yapıldı (0.7.1 → 0.8.1)

**0.7.1 Orijinal oynatıcı seçeneği.** Ayarlar → 🛠 Araçlar → "Oynatıcı: özel / orijinal".
Varsayılan **özel** (buton-eşlemeli, mevcut davranış). Tercih cihazda:
prefs `player` → `orijinal_kontrol`.

**0.7.2 Çökme raporu.** `data/CrashLog.kt`: uncaught exception yığın izi
`filesDir/son_crash.txt`'e yazılır (çökme anında ağa yazmak işe yaramaz, süreç ölüyor),
sonraki açılışta `/api/v1/client_log`'a gider ve **ancak ulaşınca** silinir.
MainActivity.onCreate'te kurulur.

**0.7.3 Orijinal kontroller alt barda + odak düzeltmesi.** 0.7.1'de PlayerView'e odak
verilmişti (`isFocusable=true`); odak Compose ağacından kopunca tuşlar Media3'e düşüyor,
panele imleç gitmiyor, ayar açılmıyor, basılan tuş arkada oynatmayı başlatıyordu
(Dean'in dört şikâyetinin üçü tek kök neden). Artık PlayerView odağı **hiç** almaz
(`FOCUS_BLOCK_DESCENDANTS`), tuşlar yine buton eşlemesinden geçer — orijinal moddan
gelen tek şey GÖRÜNÜM. Düzen `res/layout/nm_player_controls.xml` (`controller_layout_id`),
PlayerView `nm_player_view.xml`'den şişiriliyor (o öznitelik yalnız XML'de var).

**0.8.0 Anlamlı listeler + dar bölüm seçici.** Dean seçti (soruldu):
- Bölüm seçici: sağda 380dp dar sütun, solda önizleme. Tam genişlik şerit liste gitti.
- Ekleme jesti: **yeni jest yok**, uzun-bas menüsünde liste satırları.
- Raflar: **Devam edenler** (izleme kaydından kendiliğinden) · **İzlenecekler** ·
  **Takip ettiklerim** · **Beğendiklerim**. Sunucuya dokunulmadı —
  `watch_store.ALLOWED_LISTS` ("izlenecek", "planlandi", "takip") + favorites zaten vardı.
  İstemci `Library` artık `lists/{ad}` okuyor, `toggleListe` ile yazıyor.
  Menüdeki ayrı `following()` isteği düştü.

**0.8.1 TMDB bölüm özeti.** Ölçüm: Neagley S1 `tr-TR` → 8/8 bölümde özet dolu + still var.
Yeni uç `/api/v1/episode_overviews?title=&season=`: sezon başına tek istek, 12 saat
önbellek, Türkçe özetlerin HEPSİ boşsa `en-US`'a düşer. Önizlemede bölüm karesi (16:9),
adı ve özeti; sağlayıcıda ad yoksa TMDB'ninki. Still aynı `/proxy/image` hattından.

## Kanıt durumu — ÖNEMLİ

**Doğrulandı:** derlemeler (`BUILD SUCCESSFUL`), `stream/tests` 163 OK (4 yeni test),
app unit testleri, `smoke.sh` YEŞİL, üç dağıtım yerinin sha256'sı GitHub'dan indirilip
yerel APK ile karşılaştırıldı (0.8.1 → `47a40c0a…`), TMDB yanıtı canlı çekildi.

**Cihazda GÖRÜLMEDİ:** 0.7.1'den 0.8.1'e kadar hiçbir şey. Dean'in son fotoğrafı 0.7.1/0.7.2
dönemine ait. Alt kontrol barının, dar sütunun ve 420×236 önizleme karesinin ekranda
nasıl oturduğu bilinmiyor.

## Tekrarlanmayacak şeyler

- PlayerView'e odak verme (`isFocusable=true`). Compose odak ağacını kırıyor.
- AndroidView `factory` içinden Compose state yazma — factory bileşim sırasında koşuyor.
- `assembleRelease` imzasız APK üretir → **`assembleDebug`** kullan.
- `sha256sum "$TEMP/..."` çıktının başına `\` koyar → `sha256sum < dosya`.
- `docker compose up -d --build stream` tüneli düşürür (cloudflared netns'i stream'e pinli)
  → ardından `docker compose --profile tunnel up -d --force-recreate cloudflared`.
- `docker exec -w /usr/src/...` Git Bash'te yol çevirir → `-w //usr/src/...`
  veya `MSYS_NO_PATHCONV=1`.
- Python (Windows) `/tmp` görmez; scratchpad'in tam yolunu kullan.

## SIRADAKİ İŞ — Dean'in cihaz denemesi

Belirlenmiş yeni iş yok. Dean 0.8.1'i kurup bakacak. Beklenen geri bildirim noktaları:
alt kontrol barının yerleşimi, dar sütun genişliği, önizleme karesi.

Açık kalan, sorulmamış gözlem (17 Eylül'den devrediyor): "Sağlayıcı & Kaynak" listesinde
beş kaydın beşi de "dil bilinmiyor" diyor ve ikisi yineleniyor (DiziPal ×2, Dizilla ×2).
Fotoğraf kanıtı var, koda bakılmadı.

Uygulama patlarsa: Dean bir kez daha açsın, sonra `curl -s localhost:3310/api/v1/client_log`
— yığın izi oraya düşer (0.7.2'den beri).
