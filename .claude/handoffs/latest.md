# DEVİR — dizi kaynakları · başlangıç gecikmesi · TV başlangıç paneli

**Tarih:** 10 Eylül 2026, 13:05 · **Dal:** `fix/general-stability` @ `426eb88` (push'lu)
**Yığın:** doh · engine · stream · tunnel · warp — `smoke.sh` YEŞİL (bu oturumda çalıştı)
**TV sürümü:** `v0.1.66-poc` — `data/apk/`, OTA `app_update` onu veriyor · **cihaza kurulmadı**
(v0.1.65 cihazda: Dean "bastığım anda buldu" — LAN çözümü çalışıyor)

## Bu oturumda yapılan — KANITLI
| İş | Commit | Kanıt |
|---|---|---|
| 4 dizi kaynağı hiç oynamıyordu (DiziBox 69 · DiziMom 45 · Dizilla 12) | `07b060e` | `chain_scan` serie 8/8 `#EXTM3U` (öncesi: 403 / 0 kaynak) |
| Tarayıcı her sağlayıcının HER kaynağını deniyor | `1d3c495` | `--n 2`: 24 kaynaktan 23 OK; tek ölü SezonlukDizi·Lovesick·Altyazı·VidMoly (upstream edge 502) |
| Segment ön-yükleme + 5MB cache kapısı kalktı | `0b569df` | ilk 3 segment 0.46/1.04/1.04s → 0.03/0.04/0.04s |
| `load_item` filmde resolve'u önden ısıtıyor; resolve cache 180s | `fd0aceb` | ısıtılmış resolve 0.06s · soğuk 1.92s · tests 81/81 |
| TV v0.1.66: StartPanel (OYNAT odaklı, devam/1.bölüm, bölüm listesi, kaynak·dil) · `/rc` basılı-tut onayı · telefondan gelen içerik paneli atlar (`autoplay`) | `426eb88` | gradle test+assemble EXIT 0 · dex'te `StartPanel`/`autoplay` VAR · rc JS `node --check` temiz |

## DOĞRULANMADI — cihazda denenecek (sıradaki koşunun ilk işi)
1. **v0.1.66'yı TV'ye kur** (OTA LAN'dan). Bak: içerik açılınca panel geliyor mu, odak
   OYNAT'ta mı, dizide bölüm listesi doluyor mu (çözümleme bitince dolar), "Devam et —
   S?B? · dk" doğru mu, GERİ panelde çıkıyor mu, OYNAT'tan sonra ses/altyazı normal mi.
2. **Telefon `/rc`:** kartı basılı tut → "TV'de oynat" çıkıyor mu; tek dokunuş hiçbir
   şey yapmamalı; onaya basınca TV'de panel ATLANIP doğrudan oynamalı.
3. **"2 kere döndü" gitti mi:** film aç → afişten OYNAT'a kadar bekleme + ilk kare süresi.
4. DiziBox / DiziMom / Dizilla'dan birer dizi TV'de gerçekten oynuyor mu (sunucuda kanıtlı).

## Bilinen açıklar / sıradaki geliştirme
- **İmajlar bayat:** `0b569df` `fd0aceb` `426eb88`'in stream dosyaları container'a `docker cp`
  ile girdi (restart'ta kalır, **recreate'te kaybolur**). İlk fırsatta
  `docker compose up -d --build stream` + tünel kurtarma (stream'e her dokunuş tüneli düşürür:
  `docker compose --profile tunnel up -d --force-recreate --no-deps cloudflared`).
- Dizide ön-ısıtma yok (hangi bölüm belirsiz). Fikir: oynarken **sonraki bölümü** ısıt.
- StartPanel'de bölüm listesi resolve'dan geliyor → panel ilk 1-2 sn bölümsüz. `load_item`
  ile erken çekmek mümkün, cihazda rahatsız ederse yap.
- DiziMom'un ikinci embed'i `hdstreamable.com` FirePlayer yolu farklı (404) — hdplayersystem
  yeter, peşine düşülmedi.
- Kaynak/dil seçimi panelde ayrı ekran (SettingsPanel) açıyor; Dean "dilersem seçilebilir"
  dedi, yeterli. Tek panele gömmek istenirse ayrı iş.
- HANDOFF.md'deki "rc_show_recent/rc_text_to_tv TV'de bağla" maddesi **GEREKSİZ**: `/rc`
  WebView'da sunucudan render ediliyor, bayraklar zaten uygulanıyor.
- JetFilmizle şu an 403 (engine'den de) — port işi askıda.

## Doğrulama komutları
```bash
git fetch && git checkout fix/general-stability && git pull      # 426eb88
bash scripts/smoke.sh                                             # YEŞİL
python scripts/chain_scan.py --n 2                                # her sağlayıcı × her kaynak
docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests   # 81
curl -s localhost:3310/api/v1/app_update                          # v0.1.66-poc
cd client-tv && ./gradlew testDebugUnitTest assembleDebug
```
Git Bash: `MSYS_NO_PATHCONV=1`; katalog `url` zaten encoded, `encoded_url=` ile HAM geçir
(quote edersen engine 0 kaynak döner).
