# Handoff: saat kumandası UI + TV liste satırları

> 2026-09-16 21:05 · `fix/general-stability` @ `07bde9c` · 0 kirli dosya · push EDİLDİ
> Katalog `evaglass-releases` @ `d457d83` · 0 kirli · push EDİLDİ

## Goal

Dean'in cihazda gördüğü UI şikâyetlerini kök nedeniyle kapatmak: televizyonda
bölüm ızgarası okunmuyordu, saatte ana ekran açılışta kayıp üst üste biniyordu.
Uzun devir günlüğü ve mimari: `docs/HANDOFF.md`. Bu oturumun gerekçe kaydı:
`.claude/handoffs/2026-09-16-2038-tv-list-tile-ui.md`.

## State

**Yayınlandı — üç dağıtım yeri de canlı (yerel OTA · GitHub release · apps.json):**
- **NetMovies TV 0.3.9** (vc 309) — bölüm seçici tam genişlik satır listesi,
  Ayarlar menüsü tam yükseklik. Commit `bc3f529`.
- **NetMovies Mini 0.1.10** (vc 110) — sabit ince şerit, üç kip düğmesi,
  mikrofon öne, TV ana ekranı uzun basışta. Commit `07bde9c` (0.1.9 = `dbc29c7`).

Kanıt (komut çıktıları):
- `aapt2 dump badging` → TV `versionCode='309' versionName='0.3.9'`,
  saat `versionCode='110' versionName='0.1.10'`
- `/api/v1/app_update?target=tv` → `v0.3.9-poc` · `?target=wear` → `v0.1.10-poc`
- release asset sha256 = yerel APK: TV `403e2583…`, saat `4d343fd3…`
- `evatechnosoft.github.io/evaglass-releases/apps.json` → `netmovies-tv 0.3.9/309`,
  `netmovies-phone 0.3.9/309`, `netmovies-mini-watch 0.1.10/110`
- saat imzası `20319a76…02d841` — 0.1.4'ten beri aynı, güncelleme imzadan düşmez
- `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:wear:assembleDebug`
  → hepsi BUILD SUCCESSFUL

**HİÇBİRİ CİHAZDA GÖRÜLMEDİ.** Sunucu tarafı kanıtlı, ekran değil. `adb devices`
boş, `client_log` boş.

**KIRMIZI — bu oturumun işiyle ilgisiz, açık kalan:** `bash scripts/smoke.sh`
→ `[HATA] DiziPal · zincir kaynak vermedi`. Motor günlüğü sebebi söylüyor:
`resolve: link — DiziPal · ConnectError:` (adres `dizipal2220.com`). Katalog,
kanallar ve `stream/tests` YEŞİL; yalnız bu sağlayıcı düşük. Desen tanıdık:
domain taşınması / SNI blok (`memory/plugin-domain-moves.md`).

**Tünel:** oturum başında 530'du, `docker compose --profile tunnel up -d` ile
kaldırıldı → `w.evaitec.com/api/v1/health` 200.

## Next

1. **Dean cihazda denesin** (tek kişilik adım, kod değil): televizyonda
   evaitecOTA → NetMovies 0.3.9; saatte ana ekranın en altındaki
   "⬆ 0.1.10 güncelle" şeridi. Geri bildirim gelene kadar saat/TV UI'sine
   dokunma — ölçü ayarı (yay yayvanlığı, düğme boyu) ekranı görmeden tahmindir.
2. **DiziPal'i ayağa kaldır:** `docker logs netmovies-engine | grep -i dizipal`
   ile güncel adresi gör, `python scripts/chain_scan.py --n 2` ile doğrula.
   Adres taşınmışsa `.env` override ile sabitle (desen: `DiziMom→dizimom.food`).
   `bash scripts/smoke.sh` yeşile dönene kadar bitmedi.
3. Dean'den yeni UI geri bildirimi gelirse onu önceliklendir.

## Don't repeat

- **Saat için ADB taraması** — cihaz ağda görünmüyor, "Wi-Fi üzerinden hata
  ayıklama" kapalı. Dean IP vermeden `scripts/saat-kur.sh` boşa koşar.
- **Saat OTA zincirini yeniden doğrulama** — yerel APK / release / katalog
  sha256'ları ve imza bu oturumda uçtan uca eşleşti; kalan belirsizlik yalnız
  cihazın kendisinde.
- **Sezon rafı + bölüm listesini aynı ekrana koymak** — denendi, "çok karışık"
  diye geri alındı (D-pad aynı ekranda iki yön). Sayfa-sayfa akış korunacak.
- **Saatte tek düğmeyle dönen kip** (gezinme→sarma→ses) — hangi kipte olunduğu
  akılda tutulamıyordu; üç ayrı düğmeye çevrildi.
- **`gh release create "dosya#ad"` ile yeniden adlandırma** — `#` sadece etiket,
  dosya adı değişmez. Önce doğru adla kopyala, sonra yükle.
- Yeni release asset'i **~40 sn 404 döner** (CDN); 404 görünce yayını bozuk sanma.
- **Bash heredoc + Türkçe kesme işareti** bu kabukta parse hatası veriyor
  (`unexpected EOF looking for matching '`). Çok satırlı Türkçe içerik için
  betiği önce Write ile dosyaya yaz, sonra `python <dosya>` ile koştur.

## Read first

1. `docs/HANDOFF.md` — proje sözleşmesi, doğrulama komutları, mimari
2. `client-tv/wear/src/main/java/com/evaitec/netmovies/wear/MainActivity.kt`
   — saat ana ekranı (`KipYayi`, `PosterYayi`, kip geri dönüş `LaunchedEffect`)
3. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/EpisodePicker.kt`
   — bölüm seçici satır listesi
4. `memory/plugin-domain-moves.md` — Next #2'nin deseni

## Verify

```bash
git rev-parse --short HEAD              # beklenen: 07bde9c (değilse: git log 07bde9c..HEAD --oneline)
git status --porcelain | wc -l          # beklenen: 0
bash scripts/smoke.sh                   # beklenen: DiziPal KIRMIZI, gerisi yeşil
curl -s "localhost:3310/api/v1/app_update?target=tv"    # beklenen: v0.3.9-poc
curl -s "localhost:3310/api/v1/app_update?target=wear"  # beklenen: v0.1.10-poc
curl -s -o /dev/null -w "%{http_code}\n" https://w.evaitec.com/api/v1/health  # beklenen: 200
```
Tünel 530 dönerse: `docker compose --profile tunnel up -d` (cloudflared ağ ad
alanı stream'e pinli, stream her yeniden kurulduğunda tünel kopuyor).

## Yeniden başlangıç promptu (yapıştır)

```
NetMovies projesinde çalışıyoruz (D:\projects\netmovies, dal fix/general-stability).
Geçen oturumda TV 0.3.9 ve saat kumandası 0.1.10 yayınlandı: televizyonda bölüm
seçici tam genişlik satır listesine geçti ve Ayarlar menüsü tam yüksekliğe çıktı;
saatte ana ekran açılışta kaymayı bitiren sabit ince şerit, üç kip düğmesi
(saatte gezin / sarma / ses, 5 sn sonra varsayılana döner), mikrofon öne ve TV
ana ekranı uzun basışa taşındı. Her ikisi de üç dağıtım yerinde canlı ve sunucu
tarafı kanıtlı, ama HİÇBİRİ cihazda denenmedi. İki repo da temiz ve push edilmiş.

Önce HANDOFF.md'yi oku ve Verify bloğunu çalıştır.

Öncelik sırası:
1. smoke.sh'deki tek kırmızıyı çöz: DiziPal zinciri ConnectError veriyor
   (dizipal2220.com). Kök neden domain taşınması mı, SNI blok mu — motor
   günlüğünden teşhis et, chain_scan ile doğrula, gerekirse .env override.
2. Dean cihaz geri bildirimi verirse (TV 0.3.9 / saat 0.1.10 ekran görüntüsü)
   onu önceliklendir; ekranı görmeden saat/TV ölçülerini kurcalama.

Yeni iş açma, HANDOFF.md'deki Next listesinin dışına çıkma. Bir şey bozuksa kök
nedeni bul, semptomu yamalama. Her iddianın arkasında komut çıktısı olsun.
```
