# DEVİR — 19 Eylül 2026, 11:05 · Media3 denemesi geri alındı, tek oynatıcı bizimki

**Dal:** `fix/general-stability` @ `09a3b20` · push EDİLDİ (yalnız handoff dosyaları kirli)
**Katalog:** `evaglass-releases/apps.json` @ `0c1825c` (push EDİLDİ)
**Sürümler:** TV/telefon **0.9.1 (vc 901)** · saat 0.1.15 (bu oturumda dokunulmadı)
**Adresler:** yerel `http://192.168.0.29:3310` · tünel `https://w.evaitec.com`

## Önce doğrula

```bash
git rev-parse --short HEAD                              # 09a3b20
bash scripts/smoke.sh                                   # kapı YEŞİL
curl -s "localhost:3310/api/v1/app_update?target=tv"    # v0.9.1-poc
curl -s localhost:3310/api/v1/client_log                # çökme izi buraya düşer
```

## Hedef

Dean'in oynatıcı şikâyetlerini kapatmak. Bu oturumda oynatıcı **üç kez yön değiştirdi**;
son karar Dean'in: **"Medya3'e gerek yok artık bizimkini uyarla."**

## Nerede duruyoruz (0.9.1)

Tek oynatıcı var: bizim `ControlsOverlay`. Media3'ün hazır arayüzü tamamen söküldü.
- Kaldırılanlar: `media3-ui-compose-material3` bağımlılığı (APK 25,4 → 20,4 MB),
  `res/layout/nm_player_view.xml`, `nm_player_controls.xml`, "Oynatıcı: yeni/klasik"
  seçeneği ve ona bağlı tuş/odak dalları.
- Korunan kök neden (0.7.3'te bulundu, DEĞİŞTİRME): `PlayerView` odağı **almaz**
  (`isFocusable=false` + `FOCUS_BLOCK_DESCENDANTS`). Odak aldığında Compose odak
  ağacından kopuyor; panele imleç gitmiyor, ayar açılmıyor, basılan tuş arkada
  oynatmayı başlatıyordu.
- Dean'in "bölüm yazısı kayıp, geçişler yok" şikâyeti bunun sonucuydu: Media3 arayüzü
  açıkken bizim overlay hiç çizilmiyordu (sol üstteki "Dizi · S1B3" şeridi ve çubuktaki
  ⏮⏭ orada yok). Kod hep yerindeydi, başka ekran görülüyordu.

## Bu oturumun tamamı (0.7.1 → 0.9.1)

0.7.1 orijinal oynatıcı seçeneği · 0.7.2 çökme raporu (`data/CrashLog.kt`) ·
0.7.3 kontroller alt barda + odak kök nedeni · 0.8.0 anlamlı listeler
(Devam edenler / İzlenecekler / Takip ettiklerim / Beğendiklerim) + dar bölüm seçici ·
0.8.1 TMDB bölüm özeti (`/api/v1/episode_overviews`) · 0.8.2 harici oynatıcı
(VLC/Nova/MX) · 0.9.0 Media3 Compose arayüzü · **0.9.1 geri alındı**.

Duran ve çalışan işler: TMDB bölüm özeti + bölüm karesi, dört anlamlı liste,
çökme raporu, harici oynatıcı satırı (Araçlar'da).

## Kanıt durumu

**Doğrulandı:** derlemeler, `stream/tests` 163 OK, app unit testleri, `smoke.sh` YEŞİL,
her sürümün sha256'sı GitHub'dan indirilip yerel APK ile karşılaştırıldı
(0.9.1 → `9c06ea0f…`), TMDB yanıtı canlı çekildi (Neagley S1, tr-TR, 8/8 özet dolu).

**Cihazda GÖRÜLMEDİ:** 0.7.1'den 0.9.1'e kadar hiçbir şey. `client_log` boş
("Kayıt yok") — Dean'in hangi sürümü kurduğu bilinmiyor, bu oturumdaki tüm teşhis
onun cümlelerine dayanıyor.

## Tekrarlanmayacak şeyler

- **Media3'ün hazır UI'sini yeniden denemek.** Denendi (0.9.0), Dean istemedi.
  D-pad'i kutudan geliyor ama bizim overlay'i (bölüm yazısı, bölüm geçişi, kaynak
  raporu, QuickPad) yanında götürüyor.
- PlayerView'e odak vermek.
- AndroidView `factory` içinden Compose state yazmak (bileşim sırasında koşuyor).
- `assembleRelease` → imzasız APK. **`assembleDebug`** kullan.
- `sha256sum "$yol"` → çıktıya `\` ekler; `sha256sum < dosya` kullan.
- `docker compose up -d --build stream` tüneli düşürür → ardından
  `docker compose --profile tunnel up -d --force-recreate cloudflared`.
- `docker exec -w /usr/src/...` Git Bash'te yol çevirir → `-w //usr/src/...`.
- Python (Windows) `/tmp` görmez; scratchpad'in tam yolunu ver.

## SIRADAKİ TEK ADIM

Dean 0.9.1'i cihazda dener. Beklenen: bölüm yazısı sol üstte, çubukta ⏮⏭,
AŞAĞI ok ile QuickPad. Kırılırsa: uygulamayı bir kez daha açtır, sonra
`curl -s localhost:3310/api/v1/client_log` — çökme izi oraya düşer (0.7.2'den beri).

Açık, sorulmamış gözlem (17 Eylül'den devrediyor): "Sağlayıcı & Kaynak" listesinde
beş kayıt da "dil bilinmiyor" diyor, ikisi yineleniyor (DiziPal ×2, Dizilla ×2).
