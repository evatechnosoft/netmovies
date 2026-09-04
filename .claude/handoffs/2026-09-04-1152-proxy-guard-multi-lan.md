# DEVİR — proxy SSRF kapısı + TV çoklu-ağ keşfi (v0.1.38-poc)

**Tarih:** 2026-09-04 11:52 · **Oturum:** bf718d80
**Dal:** `fix/general-stability` (push'landı, `ad638c6..4f9805b`)

## Hedef
Dean: *"kıdemli dev olarak kontrol edelim iyileştirmeleri yapalım"* → denetim.
Sonra: *"0.1 ve 1.1 ağına bağlanabilir iç ağda çalışan sisteme çevir"* → çoklu ağ.
Sonra: *"uygulama gelsin o zaman OTA ile ver"* → v0.1.38-poc yayını.

## Yapılanlar

| Commit | Ne |
|---|---|
| `b96fa8b` | SSRF kapısı tüm proxy uçlarına (video/subtitle; image'de zaten vardı) |
| `1abc453` | `PROXY_ALLOWED_NETS` — iç ağ kaynaklarına izin |
| `4f9805b` | TV yerel sunucu keşfi çoklu aday + paralel yoklama, v0.1.38 |

**Release:** https://github.com/evatechnosoft/netmovies/releases/tag/v0.1.38-poc
(APK 19.942.092 bayt, prerelease, listede en üstte → OTA şeridi düşer.)

### 1. SSRF kapısı (`stream/Public/Proxy/Libs/helpers.py`)
`/proxy` auth'tan **muaf** (`_auth.py:18`, harici oynatıcı auth header taşımaz) ve
cloudflared tünelinden dışa açık. İç-ağ kontrolü yalnız `/proxy/image`'te vardı.
`rewrite_hls_manifest` manifest içindeki **her** URL'ye token bastığı için ele
geçmiş bir kaynak sitesi `http://192.168.1.1/` yazsa ev ağı proxy'lenebiliyordu.
`host_is_public`/`url_is_public` helpers'a taşındı (TTL'li host cache), `image.py`
ikizi silindi. 502 gövdesindeki istisna metni de kaldırıldı (log'da duruyor).

### 2. `PROXY_ALLOWED_NETS`
Kural: **internet serbest · izin listesindeki bloklar serbest · geri kalan iç ağ
kapalı.** Varsayılan `192.168.0.0/16` (hem 0.x hem 1.x, ZimaOS `.1.186` dahil).
Boş = iç ağ tamamen kapalı. compose'da `${PROXY_ALLOWED_NETS:-192.168.0.0/16}`
olduğu için `.env`'e satır eklemeye gerek yok.
Kapalı kalanlar test altında: `127.0.0.1`, `169.254.169.254`, `10.x`.

### 3. TV çoklu-ağ (`ServerResolver.kt` + `build.gradle.kts`)
Tek sabit IP (`192.168.1.185`) yoklanıyordu; 0.x ağında TV tünele düşüyordu.
`LOCAL_URL` artık virgüllü aday listesi, adaylar **paralel** yoklanır (sıralı
olsaydı her ölü aday açılışa 1,5 sn eklerdi). Override: `NETMOVIES_LOCAL_URL`.

## Kanıtlı vs doğrulanmadı

**Kanıtlı:** `smoke.sh` **YEŞİL** (8 eklenti · movie 38 / serie 78 / serie_local 25 /
serie_foreign 10 / live 173 · resolve zinciri HDFilmCehennemi 1 kaynak) ·
`unittest discover -s tests` → **58 test OK** · `gradlew testDebugUnitTest
assembleDebug` **BUILD SUCCESSFUL** · `w.evaitec.com/health` → **200** ·
release API `tag: v0.1.38-poc` + APK boyutu doğrulandı ·
`docker exec printenv PROXY_ALLOWED_NETS` → `192.168.0.0/16`.

**Doğrulanmadı:** ⚠ APK **cihaza kurulmadı**. 0.x ağındaki keşif gerçek cihazda
görülmedi. Sunucu şu an `192.168.1.185` (Wi-Fi); Dean 0.x ağına geçtiğinde PC'nin
son okteti `.185` olmazsa aday listesi güncellenmeli.

## Bir daha düşme
- **`stream` rebuild → tünel kopar.** `cloudflared` `network_mode: service:stream`;
  stream recreate olunca netns değişir → `w.evaitec.com` 530. Çözüm:
  `docker compose --profile tunnel up -d --force-recreate cloudflared`.
  Servis adı **`cloudflared`**, `tunnel` diye bir servis YOK (profil adı o).
- **`smoke.sh` soğuk başlangıçta yanlış alarm verir.** İlk `aggregate_new?type=movie`
  çağrısı 90 sn timeout'unu aşıp "içerik yok" der; ısındıktan sonra aynı uç 38
  içerik döndürür. Kırmızı görünce önce tekrar çağır.
- **`docker exec -w` Git Bash'te "Cwd must be an absolute path" verir** (path
  dönüşümü). PowerShell tool'undan çalıştır.
- Gradle/docker build'leri 3-10 dk; `run_in_background` + notification bekle,
  `tail -f` ile polleme.

## SIRADAKİ İŞ
1. **Cihaz doğrulaması (Dean'e bağlı).** v0.1.38 kurulunca: açılış hızı (yerel yol
   seçiliyor mu), 0.x ağına geçince hâlâ yerelden mi bağlanıyor.
2. Devredeki denetim bulguları — **hiçbirine dokunulmadı**, Dean seçecek:
   - `/proxy/image` token istemiyor (host+boyut+content-type kapılı, ama tünelden
     açık relay). Token eklemek web j2 + JS + TV'de üç URL üreticisini kırar.
   - `engine/` **hiç testi yok** — 8 eklenti + aggregate sözleşmesi korumasız.
   - `PlayerScreen.kt` tek composable içinde 466 satır, 3 ayın en sık değişen dosyası.
3. Önceki devirden devam eden: **AI tooling best-practice derin araştırması**
   (bkz. `docs/HANDOFF.md` içindeki 2026-09-03 bloğu) — bu oturumda hiç başlanmadı.
