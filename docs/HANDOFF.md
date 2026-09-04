# NetMovies — Oturum Devri (HANDOFF)

> Bu dosya, projeyi başka bir oturumda kaldığı yerden sürdürmek içindir.
> **Üstteki DEVİR bloğu = şu an nerede olduğun ve sıradaki iş.**
> Altındaki oturum günlükleri = kararların gerekçesi (neden böyle yapıldı).

---

# 🧭 DEVİR — buradan devam et

**Son güncelleme:** 4 Eylül 2026 (gece)
**Dal:** `fix/general-stability` @ `c8cf6eb` (master ESKİDİR) · çalışma ağacı temiz, push'lı
**TV sürümü:** `v0.1.48-poc` — GitHub Release'te en üstte, APK yüklü
**Cihaz doğrulaması bekliyor** — §3.1 listesi Dean'in televizyonunda koşulmadı.
**Yerel API:** `http://192.168.1.185:3310` · **Tünel:** `https://w.evaitec.com`

## 1. Doğrula (tahmin etme)
```bash
git fetch && git checkout fix/general-stability && git pull
git log --oneline -3                              # en üstte c8cf6eb / v0.1.48-poc olmalı
docker compose up -d --build                      # yığın kapalıysa (WARP varsayılan açık)
bash scripts/smoke.sh                             # kapı: yeşil olmalı
cd client-tv && ./gradlew testDebugUnitTest       # beklenen: 18 test, 0 fail
```
Docker Desktop kapalıysa `smoke.sh` sessizce takılır — önce daemon'u başlat.
`docker exec` çağrılarında Git Bash yolu bozar: `MSYS_NO_PATHCONV=1 docker exec -w //usr/src/Stream ...`

## 2. Sistem şu an ne durumda (bu oturumda kanıtlandı)
| Alan | Durum | Kanıt |
|---|---|---|
| Yığın | doh/engine/stream/cloudflared/warp ayakta, engine+stream healthy | `docker compose ps` |
| Katalog | movie 38 · serie 78 · yerli 25 · yabancı 10 · canlı 173 | `smoke.sh` |
| Eklentiler | 8: DiziBox · DiziMom · DiziYou · Dizilla · HDFilmCehennemi · HQPorner · M3U Listelerim · RecTV | `get_all_plugins` |
| Testler | stream **58/58** · client-tv **18/18** | `unittest` + `gradlew` |
| İzleme senkronu | `/progress`, `/continue_watching`, `/favorites` canlı yanıt veriyor | curl + `WatchSyncApiTest` |
| Sayfalama | DiziBox/DiziYou/Dizilla page=2 döndürüyor; HDFilmCehennemi ana kategoride 500 | `get_main_page` ölçümü |

## 3. SIRADAKİ İŞ
1. **Cihaz doğrulaması — Dean'e bağlı, kod işi değil.** v0.1.48 kurulduktan sonra:
   film ortasında kopuyor mu (jeton 15 dk → 6 saat) · devam etme baştan başlatıyor mu ·
   oynatıcıda tuşlar TEK basışta çalışıyor mu · güncelleme kurulum ekranı geliyor mu
   (PackageInstaller) · Ayarlar → **📋 Listem** takvimi dolu geliyor mu · oynatıcı
   Ayarlar → **🧭 Gezinme** (dakikaya git / bölüm) · APK'yı TELEFONA kurup arama →
   seçim televizyonu açıyor mu · mor çerçeve kalktı mı.
   **Şikâyet gelirse önce Ayarlar → 🩺 Kaynak raporu satırını iste.**
2. **Canlı TV kanal ekranı.** `/api/v1/quick_channels` çalışıyor (173 kanal) ama TV client
   çağırmıyor; canlı yayın `aggregate_new?type=live` rafında düz poster olarak duruyor.
   Başlangıç: `client-tv/.../data/NetMoviesApi.kt` (uç ekle), `ui/BrowseScreen.kt` ShelfList
   desenini kanal listesi olarak kopyala.
3. **İçerik detay ekranı.** Web'de `content.html.j2` (özet, tür, benzerler) var; TV'de poster
   → doğrudan oynatma. Bölüm listesi artık 🧭 Gezinme ekranında, ama özet/oyuncu bilgisi yok.
   TMDB anahtarı `.env`'de mevcut (`TMDB_API_KEY`), `Routers/following.py` deseni kopyalanabilir.
4. **Resmi kaynaklar bölümü.** `stream/Public/Home/Libs/official_sources.py` + `/resmi-kaynak`
   TV'de yok.
5. **`resolve_sources`'a sağlık süzmesi.** Ölü kaynak (RecTV `ConnectError`) her çözümlemede
   tur harcıyor; `aggregate_new`'deki `run_plugin_health` süzmesi buraya da uygulanmalı.
6. **Faz 3/5** — `docs/NETMOVIES-IMPROVEMENT-PLAN-2026-09-02.md`: oynatıcı dayanıklılığı
   (timeout/backoff/circuit-breaker, Media3 güncelleme), tünel açıkken auth zorunluluğu.

## 4. Yapma / tekrar deneme (bu oturumda kapandı)
- **Sanal fare geri gelmesin.** `onKeyEvent` odak istiyordu, ana ekranın odak istekleri onu
  geri çalıyordu; imleç kıpırdamıyor, mod kapanmıyordu. Kaldırıldı (`2312937`). Geri istenirse
  tuşlar Activity `dispatchKeyEvent`'ine taşınmalı — odak yarışıyla çözülmez.
- **Vault PIN yapılmadı** — Dean "PIN'i boşver" dedi. `admin_config.vault_pin` sunucuda duruyor,
  istemci kullanmıyor.
- **Özel Koleksiyon'da göster/gizle bayrağı yok.** İki adımlı akıştı, ikinci adım bulunamıyordu.
  Tek satır → doğrudan açılır; yetişkin kaynak normal Gözat'ta hiç görünmez.

## 5. Bu projede bir daha düşme (sert dersler)
- **`ACTION_VIEW` ile APK kurulumu Android TV'de SESSİZCE yutulur** (intent'i karşılayan
  activity yok, istisna da fırlamaz → "kurulum açıldı" der, ekran gelmez). `PackageInstaller`
  oturumu + `STATUS_PENDING_USER_ACTION` alıcısı gerekir (`update/InstallReceiver.kt`).
- **Tek `requestFocus()` ilk karede sessizce düşer.** Kök kutu odaksız kalınca D-pad
  tuşları `onKeyEvent`'e HİÇ gelmez; ilk basış odağı taşımakla harcanır. `repeat(n) +
  withFrameNanos` ile iste.
- **Compose efekt anahtarı listeye bağlanırsa liste büyüdüğünde iş yeniden koşar.**
  Oynatıcıda `LaunchedEffect(links, …)` kuyruğa kaynak eklendiğinde `prepare()`'ı yeniden
  çağırıp videoyu BAŞA alıyordu; anahtar oynayan öğenin kimliği olmalı.
- **Proxy jetonu manifeste BİR KEZ basılır.** Ömrü film süresinden kısaysa izleme ortada
  kopar ve "kaynak öldü" sanılır (`PROXY_TOKEN_TTL`, varsayılan 6 saat). `PROXY_TOKEN_SECRET`
  boşsa anahtar her açılışta değişir → `stream` restart'ı izlemeyi keser.
- **`clickable`/`combinedClickable` + ayrı `focusable()` = İKİ odak hedefi.** Tıklama birincide,
  `onFocusChanged` ikinciyi gözler (kendisinden SONRAKİ hedefi görür) → OK basışı hiçbir yere
  gitmez. Doğru düzen: `onFocusChanged` → tıklama modifier'ı, ayrı `focusable()` YOK.
  "Ayarlar açılmıyor" bu yüzdendi; altı yerde vardı (`86fea9e`).
- **`stream/` ve `engine/` kaynağı imajın içinde, mount YOK.** Python değişikliği sonrası
  `docker compose restart` ESKİ kodu çalıştırır → `up -d --build <servis>` şart.
- **Sunucu `content_url`'ü HAM tutar, `MediaItem.url` quote_plus KODLU.** Dönüşüm tek yerde:
  `client-tv/.../data/Library.kt` `rawUrl()` / `encodedUrl()`. Karıştırılırsa TV'nin kaydettiğini
  web açamaz.
- **Gövdesiz `@POST` + `@Query` çalışıyor** (stream middleware önce query params'a bakar);
  imza testi bunu kanıtlamaz, `WatchSyncApiTest` gerçek istek üretir.
- **Aggregate tipi `serie`'dir, `series` değil.** Bilinmeyen tip hata vermez, sessizce boş döner.
- **Kaynak boş dönüyorsa önce domaini doğrula.** Site taşınır ve temayı da değiştirir.
- **`X-Sp` benzeri oynatıcı imzaları tek kullanımlıktır** — istemciye imza değil malzeme taşı.
- **Sessiz `catch` = görünmez arıza.** Hata ya loglanır ya kullanıcıya yazılır.

## 6. Kritik dosya haritası
```
stream/Public/API/v1/Routers/watch.py             ← izleme/favori uçları (10 uç)
stream/Public/Home/Libs/watch_store.py            ← SQLite; content_key SİTE-AGNOSTİK
stream/Public/Home/Libs/admin_config.py           ← gizli kaynak/kategori, vault, provider_url
engine/Public/API/v1/Routers/resolve_sources.py   ← oynatma zinciri (TEK uç)
engine/Plugins/HDFilmCehennemi.py                 ← .now + DooPlay zinciri
client-tv/.../data/Library.kt                     ← favori/devam senkronu + URL biçim köprüsü
client-tv/.../data/NetMoviesApi.kt                ← istemci sözleşmesi
client-tv/.../ui/HomeScreen.kt                    ← Devam Et rafı, Ayarlar menüsü
client-tv/.../ui/BrowseScreen.kt                  ← kaynak çipleri, sayfalama, vault modu
client-tv/.../ui/PlayerScreen.kt                  ← kalite/altyazı/bölüm + ilerleme kaydı
scripts/smoke.sh                                  ← kapı kontrolü
```

## 7. Yeni sürüm çıkarma (OTA)
```bash
# client-tv/app/build.gradle.kts → versionCode +1, versionName, RELEASE_TAG (üçü birden)
cd client-tv && ./gradlew testDebugUnitTest assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../NetMovies-TV-vX.Y.Z.apk
gh release create vX.Y.Z-poc ../NetMovies-TV-vX.Y.Z.apk --prerelease \
   --target fix/general-stability --title "..." --notes "..."
curl -s "https://api.github.com/repos/evatechnosoft/netmovies/releases?per_page=1"  # doğrula
```
⚠ GitHub API kimliksiz **saatte 60 istek/IP**; ev ağı ve testler aynı kotayı paylaşır.
OTA gelmiyorsa ilk şüpheli budur. Uygulama `/releases` listesini okur (`/releases/latest`
prerelease'i atlar) → prerelease yayınlamak yeterli.

---

## 📋 4 Eylül 2026 (gece, devam) — Listem/takip takvimi + gezinme ekranı (EN SON, v0.1.48)

**Commit:** `c8cf6eb`

Dean: *"ileri geri sarma, süre girme, bölüm seçme, kalanları daha net gösterme — ayrı
ekranda o · listem olabilir, takip ettiklerim, yeni yayınlanacak tarihleri, sonraki
bölüm günü bilgi olarak liste olsun, Türkçe ve yabancı · uygulamaya göm, aynı uygulama
içinde komut veririm ararım"*.

### Listem — Takip Ettiklerim
`GET /api/v1/following` (`Routers/following.py`): `watch_store` "takip" listesindeki her
başlık TMDB'de eşleştirilip `next_episode_to_air` ile döner. **Türkçe/yabancı ayrımı TMDB
`origin_country`'den** — kaynak sitenin kategorisinden DEĞİL (aynı dizi farklı sitede
farklı kategoride). Takvim 6 saat bellekte; `TMDB_API_KEY` yoksa liste yine döner, yalnız
tarihler boşalır. TV: `ui/FollowingScreen.kt`, satır biçimi
"18 Eylül Cuma · 3 gün sonra · S5B1 · 139. Bölüm"; tarih yoksa dizinin durumu yazılır
(boş satır "eksik mi, bölüm mü yok" ayrımını gizliyordu). Takip etme: poster uzun-bas.

`user_lists` tablosunda da `content_url` YOKTU (favorilerdeki aynı eksik) → idempotent
ALTER + `toggle_user_list`/router parametresi. Bu üçüncü tabloydu; şema eklerken üçünü
birlikte düşün.

### Gezinme ekranı
`ui/SeekScreen.kt`, oynatıcı Ayarlar → 🧭 Gezinme: geçen/**kalan**/toplam, ±10sn ±1dk
±5dk, rakam tuşlarıyla "dakikaya git", bölüm listesi. Kontrol şeridine de kalan süre
eklendi. Kalan süre hiç yazmıyordu ve belirli dakikaya gitmenin yolu yoktu.

### Telefon kumanda modu
Manifest zaten telefonu destekliyordu (leanback `required=false`), ayrı uygulama
gerekmedi: `MainActivity`'de tek `pick` fonksiyonu — cihaz TV değilse seçim
`remote/play`'e gider, Toast ile bildirilir. **Yoklama yalnız televizyonda** (`isTv`
kontrolü `HomeScreen`'de): telefon da yoklasaydı kendi komutunu yakalayıp kendinde açardı.

### Ölçümler
`/following` → Kızılcık Şerbeti 2026-09-18 S5B1 (turkish), The Last of Us (foreign) ·
`user_lists` content_url sütunu doğrulandı, test kayıtları silindi · stream 58 test ·
TV 18 test · derleme yeşil.

---

## 📱 4 Eylül 2026 (gece) — Oynatıcı onarımı + telefondan TV'de oynat (v0.1.45→v0.1.47)

**Commit'ler:** `3cbb526` · `f757077` · v0.1.47 commit'i

Dean cihazda denedi: *"atlar gibi geziyor · kalite butonu gelmedi · admin panel yok ·
devam et başlıyor sonra başa alıyor · playerda tuşa basınca ayarlara girmiyor, 2 kere
basınca giriyor · kurulum başladı diyor ama ekrana düşmedi · mor oynatıcı çerçevesi ·
telefondan seçip TV'de oynatalım, yansıtma değil kontrol olsun"*.

### Kök nedenler (hepsi sessiz arıza)
- **Başa alma:** `LaunchedEffect(links, currentLinkIndex)` — `absorb()` alternatif
  kaynakları kuyruğa ekleyince liste değişiyor, oynayan kaynak aynı olmasına rağmen
  `setMediaSource + prepare` yeniden koşuyordu. Anahtar artık oynayan linkin URL'i.
- **İki kere basma:** kök kutu odağı tek `requestFocus()` ile isteniyordu, ilk karede
  düşüyordu. Kare kare denenir; `showSettings/scrubMode/ready` değişiminde geri alınır.
- **Film ortasında kopma:** proxy jetonu 15 dk ömürlüydü → segment 403 → "çalışan kaynak
  bulunamadı". 6 saat, `PROXY_TOKEN_TTL` ile ayarlanır.
- **Kurulum ekranı gelmiyor:** `ACTION_VIEW` TV'de yutuluyordu → `PackageInstaller`.
- **Admin okuması hiç çalışmıyormuş:** `/api/admin/config` ADMIN_PASS korumalı, istemci
  401 alıp sessizce yerleşik listeye düşüyordu → yeni auth'suz `/api/v1/client_config`.
- **media_type uyuşmazlığı:** TV boş gönderiyordu, web `movie`/`serie` → aynı film iki
  ayrı `content_key`. TV artık web'in kuralını kullanıyor.

### Yeni: telefondan TV'de oynat
`POST /api/v1/remote/play` + `GET /api/v1/remote/poll` (`Routers/remote.py`), tek slotluk
BELLEK kuyruğu, 120 sn TTL. Web `content.html.j2`'de "TV'de oynat" düğmesi; TV yalnız ANA
EKRANDA yoklar (oynatıcı açıkken izlenen film telefondan değişmesin). Yansıtma değil:
akışı TV çözer. Gerekçe hafızada: `phone-to-tv-remote-play`.

### Ayrıca
Yumuşak odak kaydırması (`BringIntoViewSpec`, kenarda %22 tampon), sıkı yerleşim
(RowGap 22→12, başlık 19sp→15sp), kalite bölümü tek varyantta da görünür, TV'de
🛠 Yönetim Paneli (WebView + kumandayla parola), OTA durumu Ayarlar'da satır olarak,
oynatıcı zemini saf siyah + mor çerçeveler nötrlendi.

### Ölçümler
`remote/play` → `poll` zinciri doğrulandı (komut okununca kuyruk boşalıyor) ·
`client_config` auth'suz 200 · stream 58 test · TV 18 test · `smoke.sh` yeşil.

### Açık kalan
Cihazda hiçbiri çalıştırılmadı. Mor çerçeve için oynatıcı zemini siyaha çekildi ve
Primary kenarlıklar nötrlendi; hangi çizginin kaldığı Dean'in ekranında görülmeli.

---

## 🖥️ 4 Eylül 2026 (akşam) — TV UI onarımı + izleme senkronu (v0.1.40→v0.1.44)

**Commit'ler:** `95be460` · `2312937` · `b5b2a06` · `7133e45` · `86fea9e`

Dean sırayla: *"çok fazla aşağı liste var, geri dönmek zor · kanallar iç içe ·
biraz aydınlansın · poster odakta küçülsün"* → *"mouse kaldıralım ya da düzeltelim"* →
*"OTA patch"* → *"watchbuddy yapısına bakıp eksikleri listele"* → *"docker çalıştır,
kanıtla, bitir"* → *"özel koleksiyon açılmıyor, ayarlar açılmıyor"*.

### Ne yapıldı
- **Gözat kaynak çipleri** (`95be460`): tüm eklentilerin kategorileri alt alta 40+ raf
  oluşturuyordu. `Tümü + eklenti` çipleri; GERİ zinciri arama → sonuç → en üste → Tümü → çık.
- **Odak büyüteci küçültmeye çevrildi**: `FocusScaleCard 1.14f → 0.97f` (komşu kartları eziyordu).
  Zeminler bir kademe açıldı.
- **Sanal fare kaldırıldı** (`2312937`) — gerekçe §4.
- **İzleme senkronu** (`b5b2a06`): sunucudaki 10 uç web tarafından kullanılıyordu, TV hiçbirine
  dokunmuyordu. Library artık `/favorites` + `/continue_watching` okuyor; SharedPreferences
  yalnız önbellek; yereldeki favoriler ilk açılışta bir kez `addFavorite` (idempotent) ile
  taşınıyor. Oynatıcı 15 sn'de bir + çıkışta `POST /progress`, açılışta 30sn–%92 aralığında
  otomatik seek. "İzlenenler" → **Devam Et** + ilerleme çubuğu.
  Sunucuda `favorites` tablosunda `content_url` YOKTU (favori açılamıyordu) → idempotent
  `ALTER` + parametre; boş URL kayıtlıyı ezmiyor. `docs/VENDOR.md` madde 6.
- **Kalite seçimi** + **raf sayfalaması** aynı commit'te.
- **Gizli kaynak listesi sunucudan** (`7133e45`): `/api/admin/config`. Filtre İKİSİ BİRDEN —
  sunucu tam ad, yerleşik yedek parça eşleşmesi; yalnız sunucuya güvenilse listede olmayan
  yeni kaynak sızardı.
- **Ayarlar + Özel Koleksiyon onarımı** (`86fea9e`): kök nedenler §5 ve §4'te.

### Ölçümler
`POST /progress` → `{"ok":true,"content_key":"inception|2010|movie"}` · favori `content_url`
ile dönüyor · HQPorner 5 kategori / 46 içerik · sayfalama 3/4 kaynakta çalışıyor ·
`smoke.sh` yeşil · stream 58 test · TV 18 test.

### Açık kalan
TV cihazında hiçbiri çalıştırılmadı — kanıt sunucu yanıtları, birim testleri ve derleme.
Ayarlar'ın gerçekten açıldığı Dean'in cihazında görülmeli (§3 madde 1).

---

## 🎛️ 3 Eylül 2026 (2. oturum, devam) — GERİ tuşu + OTA teşhisi (v0.1.35-poc)

**Commit:** `5edae3e` · **Release:** `v0.1.35-poc`

Dean: *"geri tuşu direkt çıkıyor, listede aşağı inince bir üst satıra çıkmalı ·
[uygulama] açmıyor · güncelleme de hiç gelmedi."*

### 1. GERİ tuşu — ana ekranda BackHandler HİÇ YOKTU
Sistem geri tuşu doğrudan uygulamayı kapatıyordu; kullanıcı raflarda gezerken
yanlışlıkla çıkıyordu. Artık:
- **HomeScreen**: liste aşağıdaysa GERİ → en üste kaydırır + odağı ilk karta verir;
  en üstteyken çıkar (`onExit`, MainActivity `finish()`).
- **BrowseScreen**: arama/sonuç açıksa onu kapatır → değilse en üste döner → en üstte ana ekrana.

### 2. "Güncelleme hiç gelmedi" — kök neden: hata SESSİZCE yutuluyordu
`UpdateViewModel.check()` içi `catch (_: Exception)` idi. Ağ kesintisi, **GitHub hız
sınırı** (kimliksiz istek **saatte 60, IP başına** — ev ağı ve geliştirme testleri aynı
IP'yi paylaşıyor) veya bozuk yanıt olduğunda ekranda hiçbir iz kalmıyordu.
Artık: banner'da görünür hata + "Tekrar" · her adım `PlaybackLog`'a yazılır ·
**Ayarlar → "Sürüm: v0.1.35-poc"** satırı + **"Güncellemeyi kontrol et"**.
⚠ Bu teşhis yalnız v0.1.35+ ile gelir; eski APK hâlâ sessiz kalır (elle kurulum gerekebilir:
release sayfasındaki APK).

### 3. Eski istemciler de imzalı kaynakları oynatsın
`route_through_proxy` ortak modüle alındı (`stream/Public/API/v1/Libs/source_proxy.py`)
ve **`load_links`** ucunda da uygulanıyor. Böylece güncelleme alamamış APK'lar bile
HDFilmCehennemi'yi oynatabiliyor — imza (X-Sp) ve Referer işini sunucu yapıyor.

**Kanıt:** stream **48/48** OK · client-tv **5/5** OK · `assembleDebug BUILD SUCCESSFUL` ·
smoke **kapı YEŞİL** · yeni yol `master/varyant/segment 200 (2.4MB)` ·
eski yol `load_links → proxied: True` · GitHub API en yeni `v0.1.35-poc`.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi**: geri tuşunun yeni davranışı, güncelleme şeridinin görünmesi.
- OTA'nın Dean'in cihazında **neden** gelmediği hâlâ kesin değil (hız sınırı en olası;
  artık ekranda sebebi yazacak).
- "Açmıyor" şikâyeti muhtemelen eski APK + eski `load_links` yolundandı; sunucu tarafı
  düzeltildi ama cihazda doğrulanmadı.

---

## 🎬 3 Eylül 2026 (2. oturum, devam) — HDFilmCehennemi `.now`: site taşındı + tema değişti

**Commit:** `6960e50`

Dean ekrandan gördü: *"hdfilmcehennemi.now'da diziler de var, ekle; ama `setplay.shop/player/…`
açılmıyor (`ERR_CONNECTION_REFUSED`)."*

### Kök neden
Site `hdfilmcehennemi.nl` → **`.now`** taşınmış **ve tema değişmiş** (WordPress `oldmovie`,
DooPlay türevi). Eski parser'ın hiçbir seçicisi tutmuyordu:
`.nl` **403** · `/search?q=` **404** · `/yabancidiziizle-2` **404** → eklenti sessizce boş dönüyordu.
(TV'de görünen ekran uygulama değil, sitenin kendi sayfasıydı — client-tv'de WebView yok.)

### Yeni oynatma zinciri (dördü de zorunlu)
```
1. içerik sayfası      → videoAjax.nonce + data-post-id + data-player-name
2. wp-admin/admin-ajax → action=get_video_url        → setplay.shop/player/?t=…
3. setplay             → SPG.cerceve(id, veri, key)  → XOR çöz → fastplay.mom/video/<id>
4. fastplay            → window.FSP.stream (HLS)     + SPG_A koruma parametreleri
```

### Ölçümle bulunan iki tuzak
1. **`X-Sp` tek kullanımlık.** Manifest, "oynatıcı kanıtı" başlığı olmadan 404 (sitenin kendi
   yorumu: *"IDM ve curl ADRESİ tekrar oynatıyor, başlığı değil"*). Dahası **aynı imza ikinci
   istekte yine 404, tazesi 200** (ölçüldü). Bu yüzden eklenti imzayı değil **malzemesini**
   taşır (`X-Sp-Secret`/`X-Sp-Time`); proxy her istekte yeniden üretir →
   `stream/Public/Proxy/Libs/player_proof.py`. İmza malzemesi istemciye **gönderilmez**.
2. **Segmentler başka CDN host'unda ve Referer istiyor** (`srv.…cfd`, Referer'sız 403).
   Normalde segmentler bant tasarrufu için doğrudan CDN'den çekiliyordu; ek başlık isteyen
   kaynaklar artık `force_proxy` ile sunucudan geçiyor. Ayrıca **manifest'ten türeyen her
   adrese kendi proxy token'ı** veriliyor (token host'a bağlı; eski hâlinde yalnız ilk host
   kapsandığı için segment 403 alıyordu).

### Ayrıca
- `engine` `load_links`/`resolve_sources` artık `extra_headers`'ı yanıta taşıyor (düşüyordu).
- `main_page`'e **Diziler** ve **Son Bölümler** eklendi; tür sayfaları yeni yollara güncellendi.

**Kanıt:** stream **48/48** OK · smoke **kapı YEŞİL** · film **20 → 38** içerik ·
uçtan uca istemci akışı: `master 200 4872B → varyant 200 486KB → segment 200 2.4MB` ·
dizi tarafı: `Ted Lasso → 34 bölüm → oynatma 200 #EXTM3U`.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi.** TV APK'sı bu iş için yeniden yayınlanmadı (sunucu tarafı değişikliği
  olduğu için mevcut APK da yararlanır; yine de cihazda oynatma görülmedi).
- Proxy'den geçen akış **ev bağlantısının yükünü artırır** (segmentler artık sunucudan geçiyor).
  Yalnız ek başlık isteyen kaynaklar için geçerli.
- `X-Sp` şeması sitenin JS'inden türetildi; site şemayı değiştirirse bu kaynak yeniden kırılır
  (teşhis: Kaynak raporu / `docker logs netmovies-engine`).

---

## 🔗 3 Eylül 2026 (2. oturum, devam) — Zincir tek uca taşındı, istemci ayrımı kalktı (v0.1.34-poc)

**Commit:** `c8e4b4f` · **Release:** `v0.1.34-poc` (OTA'da en yeni — doğrulandı)

Dean: *"her şey arkada olsun, ortam tüketsin, TV/telefon/web ayrımından kurtul."*

### Önce ne yanlıştı
Aynı iş **iki yerde, iki farklı davranışla** duruyordu: TV uygulaması altı sağlayıcıyı
kendi arıyor, kendi sıralıyordu; web yalnız seçili sağlayıcının linkleriyle yetiniyordu.
Bir kural değişince iki yerde değiştirmek gerekiyordu ve ikisi kaçınılmaz olarak ayrışıyordu.

### Yeni tek uç
```
GET /api/v1/resolve_sources?plugin=&encoded_url=&title=&episode=&mode=fast|full
  seçili sağlayıcı → (dizi ise bölüm çözme) → alternatif sağlayıcılarda arama
  → link toplama → dil sıralaması → teşhis kaydı
```
- `engine/.../resolve_sources.py` — zincir + `Diagnostics` (her adım hem konsola hem
  **yanıta** yazılır). Alternatif aramalar `asyncio.gather` ile paralel.
- `stream/.../resolve_sources.py` — dil kuralını uygular (`language.py`) ve her kaynağa
  okunur `language: {rank, label}` ekler. **Tek kural, tek yer.**
- `mode=fast`: yalnız seçili sağlayıcı (ilk oynatma beklemesin) · `mode=full`: alternatifler dahil.

### İstemciler artık sadece tüketiyor
- **client-tv**: arama/eşleştirme/sıralama kodu **silindi**. `SourceResolver.kt` yalnız
  sunum yardımcısı (etiket + altyazı dil kodu). İki çağrı: fast → full.
  Sunucunun teşhis kaydı istemci günlüğüne karışıyor → **Kaynak raporu tek yerde**.
- **web (`izle.py`)**: aynı ucu kullanıyor → web artık **alternatif sağlayıcıları da görüyor**.

### smoke.sh — yanlış alarmın kök nedeni bulundu
Rebuild'in hemen ardından çalıştırıldığında container ayakta ama `healthy` değildi;
ısınmadan yapılan çağrılar boş dönüp "katalog boş" alarmı üretiyordu (önceki oturumda
"kök nedeni doğrulanmadı" diye not düşülen kırmızı buydu). **Isınma adımı** eklendi
(healthy olana dek en fazla 120 sn bekler) + zincir adımı eklendi.

**Kanıt:** stream **43/43** OK · client-tv **5/5** OK · smoke **kapı YEŞİL** ·
gerçek veriyle `resolve_sources` → `HDFilmCehennemi · 1 kaynak · Türkçe altyazı`,
teşhis 8 satır: `fail arama — RecTV · ConnectError: Name or service not known`,
`warn arama — DiziYou · sonuç yok` → **ölü kaynak artık sessiz değil** ·
GitHub API en yeni release `v0.1.34-poc`.

### ⚠ Doğrulanmadı / açık
- Cihazda denenmedi (Dean deneyecek).
- `RecTV` domaini ölü (`ConnectError`) — zincirde her seferinde bir tur harcıyor.
  Sağlık süzmesi `resolve_sources`'a da uygulanabilir (aggregate'te var, burada yok).
- Uzak sağlayıcı (`provider_url` dolu) yolunda web hâlâ tekil `load_links` kullanıyor;
  yalnız yerel engine yolu tek uçtan geçiyor.

---

## 🩺 3 Eylül 2026 (2. oturum, devam) — Teşhis günlüğü + dil kuralı tek yerde (v0.1.33-poc)

**Commit:** `0ad3d36` · **Release:** `v0.1.33-poc` (OTA'da en yeni)

Dean: *"hata yakala, log tut, artık bak sonra duruma · o olursa bu, netleştir."*

### 1. Hiçbir hata sessizce yutulmuyor (TV)
`client-tv/.../data/PlaybackLog.kt` — arama, bölüm çözme, link çekme ve oynatma
denemelerinin **her adımı** kayda geçer: logcat (`NetMoviesPlayback` tag) + 200 satırlık
halka tampon. `runCatching{}.getOrNull()` yerine `loggedOrNull(stage, detail)`: zincir
devam eder ama hata **görünür**.
**Cihazda okunur:** oynatıcı → Ayarlar → **🩺 Kaynak raporu** → "Son denemeleri göster".
PC/adb gerekmiyor — Dean hatayı gördüğü anda ne olduğunu okuyabilir.

### 2. Dil kuralı netleştirildi + tek yere alındı
Kural tek cümle: **Türkçe dublaj → Türkçe altyazı → dil bilinmiyor.**
- Sunucu: `stream/Public/API/v1/Libs/language.py` (`language_rank/order_by_language`).
  `/api/v1/load_links` **ve** web `izle` akışı artık sıralı liste döndürüyor → web ve TV
  aynı sırayı görür. (TV ayrıca kendi tarafında da uygular — çift güvence.)
- Görünürlük: kaynak listesi ve durum satırı `"DiziBox · Türkçe dublaj"` yazıyor.
  Etiketsiz kaynak "dil bilinmiyor" grubuna düşer ve **öyle görünür** — sessiz tahmin yok.

### 3. Sunucu tarafı sessiz `except`'ler loglandı
- `aggregate_new`: kategori eşleşmedi / kaynak boş döndü / kaynak hata verdi + özet satırı.
  **Canlı TV rafının aylarca boş kalması tam bu körlüktü.**
- `load_links` + `izle`: kaç kaynak geldi, ilk sıradaki dil ne.

**Kanıt:** stream **39/39** test OK · client-tv **8/8** OK · `assembleDebug BUILD SUCCESSFUL` ·
smoke **kapı YEŞİL** · `docker logs netmovies-engine` → `∑ aggregate: type=serie_foreign ·
10 içerik · 6 kaynak tarandı` · `docker logs netmovies-stream` → `▶ load_links: M3UPlaylist ·
1 kaynak · ilk sıra: dil bilinmiyor` · GitHub API'de en yeni release `v0.1.33-poc`.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi** (Dean deneyecek): kaynak raporu ekranının okunabilirliği, zincirin
  gerçekten sıradakine geçmesi, dublaj önceliğinin doğru linki seçmesi.
- Dil tespiti hâlâ **kaynağın etiketine** bakıyor. Etiketsiz veren site "dil bilinmiyor"
  grubunda kalır — kaynak raporunda bu görünür; sık çıkarsa plugin'e dil alanı eklenmeli.
- Web'de alternatif **sağlayıcı** araması yok (TV'de var). Web yalnız seçili sağlayıcının
  linkleri arasında otomatik geçiş yapıyor (bu zaten vardı) + artık dil sırasıyla.
- Bir smoke koşusunda rebuild'in hemen ardından 1 adım kırmızı görüldü, tekrarında yeşil;
  **kök nedeni doğrulanmadı** (muhtemelen container yeni ayaktayken soğuk çağrı).

---

## ▶️ 3 Eylül 2026 (2. oturum, devam) — Oynatma zinciri yeniden yazıldı + v0.1.32-poc OTA

**Commit'ler:** `b6df088` (oynatıcı) · `bc5b11c` (APK) · **Release:** `v0.1.32-poc` (prerelease, OTA yayında)

Dean'in şikâyeti: *"izletmiyor, 'bulunamadı / tekrar dene' diyor; aynı filmi bir sürü
site veriyor, çalışandan çeksin; önce dublaj sonra Türkçe altyazı; ekrandan çıkarmasın,
ekranda yazsın, geri tuşuna kendim basarım."*

### Yeni davranış (`data/SourceResolver.kt` + `ui/PlayerScreen.kt`)
- **Aşamalı kaynak toplama.** Eskiden oynatıcı açılırken altı sağlayıcı SIRAYLA taranıyor,
  kullanıcı hepsi bitene kadar bekliyordu. Artık seçili sağlayıcı hemen denenir, **ilk link
  gelir gelmez oynatma başlar**; diğerleri arka planda taranıp kuyruğa eklenir.
- **Dil önceliği.** Kuyruk `orderByLanguage()` ile sıralanır: **Türkçe dublaj → Türkçe
  altyazı → diğer**. Grup içi sıra korunur (stable sort). Zaten oynayan link yerinde kalır.
- **Hata kutusu kaldırıldı.** Çalmayan link sessizce sıradakine geçer; ekranda yalnız durum
  satırı: *"Kaynak açılmadı, sıradaki deneniyor (2/5)…"*. Hiçbiri çalışmazsa *"çıkmak için
  GERİ tuşuna bas"* yazısı ekranda kalır. `PlayerOverlay`+"Tekrar dene" oynatma akışından
  çıkarıldı — geçiş kendiliğinden oluyor.
- Altyazı dil tahmini tek yerde: `guessSubtitleLang()`.

**Kanıt:** `testDebugUnitTest` **8/8 OK** (7 yeni SourceResolver testi) ·
`assembleDebug BUILD SUCCESSFUL` · GitHub API'de en yeni release `v0.1.32-poc`
(prerelease, asset `NetMovies-TV-v0.1.32.apk`) → eski APK'lar güncelleme şeridini görür.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi**: zincirin gerçekten sıradaki kaynağa geçtiği, dublaj önceliğinin
  doğru linki seçtiği, durum satırının okunabilirliği. Dean deneyecek.
- Dil etiketleri kaynak adına bakıyor (`"dublaj"`, `"altyazı"`). Etiketsiz veren kaynakta
  içerik dili anlaşılmaz → 2. gruba düşer. Gerekirse plugin'lerde dil alanı eklenmeli.
- **Web oynatıcısı bu davranışı ALMADI** — aynı zincir web tarafında hâlâ eski hâlinde.

---

## 🖼️ 3 Eylül 2026 (2. oturum, devam) — Faz 2: tek poster hattı

**Commit:** `fb3f68c` — `feat(poster): tek poster hattı`

Poster fallback zinciri **dört ayrı yerde** kuruluydu ve davranışları farklıydı:
her Jinja şablonunda inline `onerror`, `content-browser.js`'te ikinci deneme,
`home.html.j2` inline script'inde üçüncü bir varyant, TV'de Coil `onError` state'i.

**Yeni sözleşme — zincirin tamamı sunucuda:**
`/proxy/image?url=<kaynak>&title=<başlık>` → kaynak → proxy LRU cache → TMDB (302) → placeholder

- `image.py`: `title` parametresi + fallback halkası. **Kırık poster negatif cache'i**
  (10 dk): ölü poster her rafta tekrarlanıp her seferinde CDN'e TLS + timeout turu
  yapıyordu. `X-Cache` artık nedeni de söylüyor: `MISS/HIT/NEG/UPSTREAM_404/
  NOT_IMAGE/BLOCKED_HOST/BAD_SCHEME/TOO_LARGE/FETCH_ERROR` → teşhis header'dan okunur.
- Tek helper üç yerde aynı URL'i üretir: Jinja `poster(url, title)`,
  yeni `Static/JS/utils/poster.js` → `posterUrl(poster, title)`,
  Kotlin `proxiedPoster(url, title)`. Şablonlardaki kopya `onerror` zincirleri silindi.
- `stream/tests/test_poster_pipeline.py` — 13 test (helper sözleşmesi, cache HIT/MISS,
  negatif cache, TMDB fallback, MIME reddi, SSRF reddi).

**Kanıt:** `31/31 OK` · gerçek poster `MISS 0.162s → HIT 0.018s` · kırık poster
`302 → /tmdb-poster` (2. istek `NEG 0.013s`, ağ turu yok) · `url=&title=Dark` →
TMDB'den `200 image/jpeg 70331B` · ana sayfada eski `tmdb-poster` onerror kalıntısı **0**,
`title`'lı poster URL **36** · TV `compileDebugKotlin` + `testDebugUnitTest` **exit 0**.

CSS'te `aspect-ratio: 2/3` zaten vardı → layout shift maddesi ek iş istemedi.

⚠ **Doğrulanmadı:** TV cihazında posterlerin göründüğü; APK sürüm bump'ı ve OTA
yayını yapılmadı (Kotlin değişikliği sadece derlendi).

---

## 🧪 3 Eylül 2026 (2. oturum) — Faz 1: sözleşme testleri + Canlı TV rafı fix

**Commit'ler (dal: `fix/general-stability`)**

| Commit | Ne |
|---|---|
| `45c2060` | api/v1 gateway sözleşme testleri + `scripts/smoke.sh` kapı kontrolü |
| `3dce3c4` | Canlı TV rafı boş dönüyordu + `quick_channels` ucu istemcilere açıldı |

### 0. Yığın yeniden ayağa kalktı
Docker Desktop kapalıydı (`localhost:3310` bağlantı yok, tünel `530`).
`docker compose --profile tunnel up -d --build` → engine + stream **healthy**,
`/api/v1/health` 200, `https://w.evaitec.com` **200**.

### 1. Sözleşme testleri (planın Faz 1 / madde 4 boşluğu)
`stream/tests/test_api_contract.py` — 14 test. Upstream provider `httpx.MockTransport`
ile taklit edilir, **gerçek kaynak sitelere çıkılmaz** (CI'da kırılgan olmaz).
Sabitlenen sözleşme: `encoded_url` parametre adı (eski `url` regresyonu), katalog/arama/
link parametreleri, cache kuralları (arama cache'lenir, **`load_links` asla**, boş
`aggregate` cache'lenmez, hata cache'lenmez), `provider_error` zarfı, istemci kimlik
header'larının upstream'e taşınması.
**Kanıt:** container içinde `Ran 18 tests ... OK` (mevcut `test_provider_routing` dahil).
Çalıştırma: `docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests`
⚠ `tests/` image'a build ile girer; kod değiştirmeden test denemek için `docker cp stream/tests netmovies-stream:/usr/src/Stream/`.

### 2. `scripts/smoke.sh` — tek komutluk kapı
Container health → `/api/v1/health` → eklenti listesi → **beş aggregate tipi** →
`quick_channels` → sözleşme testleri. `BASE=https://w.evaitec.com bash scripts/smoke.sh`
ile tünel üzerinden de çalışır. Kırmızı adım varsa çıkış kodu 1.

### 3. Canlı TV rafı boştu (smoke'un yakaladığı gerçek hata)
- **Kök neden:** `aggregate_new` kategori **adında** Türkçe ipucu arıyor (`"canlı"`,
  `"kanal"`); M3U grup adları listeden geliyor ve İngilizce (`Animation`, `News`) →
  hiç eşleşme yok → `type=live` **her zaman 0 item**. TV home'un `OTHER_TYPES`'ında
  `live` var, yani cihazda Canlı TV rafı boş görünüyordu.
- **İkinci kusur:** `/api/v1/quick_channels` **stream gateway'inde yoktu**. Web bunu
  server-side `fuck_dmca` ile çekiyor, native istemci ise canlı listeye hiç erişemiyordu.
- **Fix:** engine'de toplayıcı `collect_live_channels()` olarak ayrıldı; `aggregate_new`
  live tipinde bunu kullanıyor (url'ler diğer tiplerle aynı sözleşmede `quote_plus`'lı).
  Gateway'e `quick_channels` router'ı + 10 dk cache eklendi.
- **Kanıt:** `type=live` 0 → **173** item · `/api/v1/quick_channels` → 173 kanal ·
  `Disney Jr.` → `load_links` → `saran-live.ercdn.net/.../index.m3u8` **HTTP 200**.

### ⚠ Sözleşme tuzağı (bir daha düşme)
Aggregate tip adı **`serie`**, `series` değil. Bilinmeyen tip hata vermez, **sessizce boş
liste** döner — bu yüzden yanlış tip yazmak "kaynak öldü" gibi görünür.
Geçerli tipler: `movie`, `serie`, `serie_local`, `serie_foreign`, `live`.

### ⚠ Doğrulanmadı / açık (bu oturum)
- Canlı rafın **TV cihazında** dolduğu görülmedi (sunucu ucu uçtan uca doğrulandı).
- TV'de canlı kanal kategorileri İngilizce görünecek (`Animation;Kids`) — kozmetik, açık.
- Önceki oturumun cihaz doğrulamaları (DiziBox oynatma, Gözat D-pad, TMDB fallback,
  oynatıcı hata ekranı GERİ tuşu) hâlâ **denenmedi**.

---

## 🚀 3 Eylül 2026 — DiziBox/MolyStream + Gözat Yeniden Tasarım + TMDB

**Commit'ler (dal: `fix/general-stability`)**

| Commit | Ne |
|---|---|
| `9e4b865` | Gözat ekranı poster raflarına dönüştü + büyüteç arama (v0.1.29-poc) |
| `d0b40e8` | DiziBox/MolyStream çözücü + poster proxy LRU cache (v0.1.30-poc) |
| `8187ac9` | TV'de poster TMDB fallback (v0.1.31-poc) |

### 1. DiziBox — "Oynatılacak kaynak bulunamadı" çözüldü (KÖK NEDEN)
Zincir **üç katman**, eskiden ikincisi atlanıyordu:
1. Bölüm sayfası → `div#video-area iframe` → `dizibox.live/player/king/king.php?v=<hex>`
2. **`king.php` sadece SARMALAYICI** — gerçek oynatıcı içindeki **ikinci iframe'de**:
   `dbx.molystream.org/embed/<id>`. Bu katman izlenmediği için `load_links` hep `[]` dönüyordu.
3. MolyStream sayfa gövdesi **CryptoJS ile şifreli**: `CryptoJS.AES.decrypt("<ct>", "<pw>")`
   → OpenSSL `"Salted__"` formatı, **EVP_BytesToKey (MD5, 1 tur)**, AES-256-CBC, PKCS#7.
   Çözülen HTML'deki jwplayer `file:` alanı doğrudan HLS master playlist veriyor.

→ `DiziBox.py`: `_evp_bytes_to_key()`, `_cryptojs_decrypt()`, `_molystream()` eklendi;
`load_links` iç iframe'i takip ediyor.
**Kanıt:** `load_links` `[]` → `1` kaynak · `dbx.molystream.org/embed/sheila/9344-...` →
`HTTP 200, 149 B, #EXTM3U, RESOLUTION=1920x1080` · engine log `GET - 200 - 5.34 sn`.
⚠ **TV cihazında oynatma denenmedi.**

### 2. Poster proxy cache — "çok geç yükleniyor"
`stream/Public/Proxy/Routers/image.py`'ye süreç-içi **LRU + bayt sınırlı** cache
(`IMAGE_CACHE_MB`, varsayılan 128MB) eklendi; `X-Cache: HIT/MISS` header'ı ile ölçülebilir.
**Kanıt:** aynı poster `MISS 0.538 sn` → `HIT 0.022 sn`.
⚠ In-memory → `WEB_WORKERS=1` şart (zaten öyle).

### 3. Gözat ekranı yeniden tasarlandı (v0.1.29)
Dean'in üç şikâyeti karşılandı:
- Düz metin listesi → her (eklenti, kategori) bir **poster rafı** (`Shelf`), ana sayfadaki gibi.
- Kaba arama çubuğu → sadece **büyüteç**; seçilince metin alanına açılıyor.
- "Çıkınca liste en üstten başlıyor" → `rememberLazyListState()` + son odaklı raf indeksi
  **ekran seviyesine hoist** edildi; arama sonucundan dönünce kaldığı yerden devam.
- İlk 6 raf paralel önyükleniyor (`PREFETCH_SHELVES`); boş dönen raf hiç çizilmiyor.

### 4. TMDB anahtarı aktif + TV fallback (v0.1.31)
Kök neden: anahtar hiç yoktu (`TMDB=[]`) → `/tmdb-poster` 404 dönüyordu. Dean anahtarı verdi,
**gitignored `.env`'e** yazıldı (kodda ASLA).
**Kanıt:** container içi `TMDB key: DOLU (32 karakter)` · `/tmdb-poster?title=Dark` →
`302 → image.tmdb.org/t/p/w500/lUXX0AeneW2v0FK9YuXlrTwkI3H.jpg`.
TV tarafı: yeni `ui/PosterImage.kt` ortak bileşeni — Coil `onError` tetiklenince
`/tmdb-poster?title=`'a düşüyor (poster hiç yoksa doğrudan TMDB ile başlıyor).
Home + Gözat posterleri bu bileşene geçti.

### ⚠ Doğrulanmadı / açık (bu oturum)
- DiziBox'ın TV'de **gerçekten oynadığı** doğrulanmadı.
- Yeni Gözat raflarının D-pad davranışı ve odak geri-yükleme cihazda denenmedi.
- TMDB fallback'in TV ekranında dolduğu görülmedi (sunucu ucu doğrulandı).
- Oynatıcı hata ekranı GERİ tuşu fix'i hâlâ cihazda denenmedi.

---

## 🎬 2 Eylül 2026 — Kaynak Onarımı + v0.1.28-poc OTA

**Commit'ler (dal: `fix/general-stability`)**

| Commit | Ne |
|---|---|
| `595a7c9` | Dizilla oynatma linki çözüldü + ölü domainler gömülü yedeğe alındı |
| `07ae1ed` | Canlı TV M3UPlaylist'e taşındı, ölü RecTV varsayılan gizlendi |
| `e543a04` | DiziYou araması WP AJAX ucuna taşındı |
| `2ec7dd2` | v0.1.28-poc sürüm bump + APK |

### 1. Dizilla — oynatma tamamen kırıktı, düzeldi
Kök neden **üç katmanlıydı**:
1. **Oynatıcı iframe HTML'de yok.** `div#playerLsDizilla` sunucudan boş geliyor; kaynaklar
   `__NEXT_DATA__ → props.pageProps.secureData` içindeki **AES-256-CBC** bloğundan client-side
   basılıyor. Anahtar arama ucuyla aynı: `base64(sha256(b"!!22xx!!90!!"))[:32]`, IV = 16 sıfır bayt,
   PKCS#7. → `Dizilla._secure_data()` eklendi, `load_links` bu bloğu çözüyor.
2. **`source2.php` 403** — token base64 ("+", "/", "=") içeriyor; `params=` ile göndermek
   yüzde-kodluyor. → ham f-string query string kullanıldı (sitenin kendi JS'i de öyle yapıyor).
3. **Yine 403** — `PluginBase.httpx` sabit `accept-encoding: gzip, deflate` + `connection: keep-alive`
   gönderiyor; Cloudflare bunu bot parmak izi sayıyor. → pichive akışı için **düz `httpx.AsyncClient`**
   (sadece tarayıcı UA) kullanılıyor (`_pichive_sources`).

Ayrıca `h1` metnindeki `"Darknetİzle"` artefaktı temizlendi (`re.sub(r"\s*İzle$", ...)`).

**Kanıt:** `search → 15` · `load_item 'Darknet' → 12 bölüm` · `load_links → 1` ·
`master.m3u8 → HTTP 200, 6962 B, geçerli #EXTM3U`.

### 2. Ölü domainler — `.env` bağımlılığı kaldırıldı
Fallback'ler gitignored `.env`'den koda taşındı (temiz kurulumda da çalışsın):
- `Dizilla.py` → `https://dizilla.now`
- `DiziMom.py` → `https://www.dizimom.diy` (zincir: `.plus → .work → .food → .diy`; upstream `.kt`
  hâlâ ölü `.plus`'ı gösterdiği için upstream sonucu bu ailedeyse override ediliyor)

### 3. DiziYou — arama düzeldi
`/?s=` artık boş kabuk dönüyor. Sonuçlar tema'nın `wp-admin/admin-ajax.php`
(`action=data_fetch`) ucundan `div#searchelement` kartları olarak geliyor. WARP yedeği eklendi.
**Kanıt:** `dark` → "Dark", "Dark Desire".

### 4. Canlı TV — RecTV öldü, M3UPlaylist devraldı
`docker-compose.yml`'de varsayılan: `M3U_SOURCES=https://iptv-org.github.io/iptv/countries/tr.m3u`.
`admin_config.py → DEFAULT_CONFIG.hidden_providers`'a `"RecTV"` eklendi (silinmedi — yeni adres
çıkarsa `/admin`'den görünür yapmak yeter).
**Kanıt:** 174 kanal, örneklenen 20 kanalın 16'sı 200 + `#EXTM3U`; `a haber` → 1 sonuç → oynanabilir
`.m3u8`; ana sayfada "RecTV" grep → 0.

### 5. Alternatif kaynak araştırması — hepsi ölü (kanıtlandı)
| Kaynak | Durum |
|---|---|
| RecTV | `b.prectv{30..90}.sbs` **NXDOMAIN**, `rectv.me` A kaydı yok, Telegram probe `None` |
| InatBox | `dizibox.rest` + `boxbc.sbs` NXDOMAIN, upstream son commit 2025-02-23 |
| SineWix | `ythls.kekikakademi.org` NXDOMAIN |
| GolgeTV | `panel.cloudgolge.shop` NXDOMAIN |
| CanliTV | bağımlı repo `keyiflerolsun/IPTV_YenirMi` → GitHub **451** (kaldırılmış) |

→ Yeni tek-API canlı TV kaynağı eklenmedi; M3U listesi bu boşluğu dolduruyor.

### 6. v0.1.28-poc OTA
`versionCode 27` / `versionName 0.1.28` / `RELEASE_TAG "v0.1.28-poc"`.
`assembleDebug` → **BUILD SUCCESSFUL (1m 43s)**, APK 19.822.686 B, kökte `NetMovies-TV-v0.1.28.apk`.
Release doğrulaması uygulamanın kullandığı API yolundan: `tag_name: v0.1.28-poc`, `draft: false`,
asset `state: uploaded`.

### ⚠ Doğrulanmadı / açık
- **TV cihazında OTA indirme + kurulum ve Faz 2 tema görünümü test edilmedi.**
- Oynatıcı hata ekranında **GERİ tuşu** düzeltmesi hâlâ cihazda denenmedi.
- `/admin` parolası internete açık tünelde `1234` — Dean şimdilik kabul etti.

### 🔁 Tekrar düşülen tuzaklar (bir daha düşme)
- **`stream`/`engine` Python kodu imaja gömülü.** `restart` ve `--force-recreate` kaynak değişikliğini
  ALMAZ — sadece `up -d --build <svc>`. Bu oturumdaki 1 numaralı kök neden buydu.
- `cloudflared` netns'i `stream`'e pinli → `stream` recreate edilirse `cloudflared` de edilmeli.
- `docker exec ... python3 /tmp/x.py` Windows'ta path-mangle olur → **`//tmp/x.py`** yaz.
- Karmaşık regex'ler `python -c` içinde Git Bash escaping'inde kırılır → script dosyası yaz + `docker cp`.
- Arama testinde **kötü sorgu ≠ bozuk kaynak**: `kara` hiçbir sitede eşleşmiyor; `dark`/`breaking` kullan.

---

## 🎯 2 Eylül 2026 Oturumu — Faz 1 Stabilizasyon + Media3 Geçişi (SON DURUM)

**Commit'ler (dal: `fix/general-stability`):**
- `542a450` — Faz 1: TV `load_item` `encoded_url` sözleşmesi + kontrat testi (JVM PASSED),
  provider cache izolasyonu (`_cache_key` provider_url içerir, `_active_provider_url` admin
  config'ten → web+TV birleşik provider) + pytest 3/3, poster hattı birleştirildi
  (content/search → `poster()` proxy + tmdb fallback), compose `service_healthy` +
  `WEB_WORKERS=1`, `.gitignore` `data/`→`/data/` (tv/data/ paketi tuzağı).
- `7d4ed47` — Codex yarım-UI refactoru (pages/*.css ayrımı, header sadeleştirme, HomeScreen).

- `54d76d2` — TV oynatıcı hata ekranında GERİ tuşu fix'i (`BackHandler` `error != null → onBack()`,
  `onKeyEvent` hata state'inde tuş yutmuyor, `ActionRow.focusGroup()`) + **Media3 1.4.1 → 1.11.0**.
  Gereken zincir: compileSdk 36, AGP 8.9.1, gradle 8.11.1, **Kotlin plugin 2.0.20 → 2.2.10**
  (Media3 1.11 kotlin-stdlib 2.2.x çekiyor; eskisinde "metadata 2.2.0, expected 2.0.0" ile
  derleme kırılıyordu). Kanıt: `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL**.
  ⚠ Cihazda (gerçek TV) davranış testi YAPILMADI — APK'yı yükleyip hata ekranında BACK'i dene.

**⚠ Docker/canlı (502):**
- Kök neden 2 katman: (1) compose `service_started` cold-start yarışı → `542a450` ile çözüldü
  (canlıda henüz doğrulanmadı), (2) Docker Desktop'ın kendisi hastaydı (API 500, buildkit
  "no such job", iç DNS timeout) → restart edildi; `cloudflared:2026.1.2` imajı yeniden
  çekildi (IMAGE_OK). Oturum sonunda `COMPOSE_BAKE=false docker compose up -d --build`
  arka planda ateşlendi — **sonucu doğrulanamadı**.
- **Sonraki adım:** `docker ps` → hepsi healthy mi + `curl localhost:3310/health` +
  w.evaitec.com 200 mü. Stream rebuild olduysa cloudflared'i de recreate et
  (netns pinli — bkz. memory: stream rebuild → tünel kopar).

---

## 🎯 1 Eylül 2026 Oturumu — Web Hata Düzeltmeleri, TV UI & Stabilizasyon

1. **🛠 `unexpected char '\' at 1028` ve JS/Jinja Kaçış Hatası Giderildi (`category.html.j2`, `home.html.j2`)**:
   - Resim posterlerindeki TMDB fallback `onerror` satırlarında tek tırnakları kaçırmaya çalışan kırılgan `replace("'", "\\'")` filtreleri temizlendi.
   - Kartlara doğrudan `data-title="{{ item.title }}"` niteliği eklendi ve JS fallback'i `this.dataset.title` üzerinden hatasız hale getirildi.
   - `home.html.j2` içindeki `loadLazy` fonksiyonunda tırnak kaçış regex'i sadeleştirilerek parse çökmesi engellendi.

2. **🖱️ Web & Mobil Kart Tıklama Davranışı Düzeltildi (`tv-home-actions.js`)**:
   - `tv-home-actions.js` dosyasında TV kumandası için tek tıklamayı yutup sadece kartı odaklayan (`preventDefault()`) blok kaldırıldı.
   - Web ve fareli kullanımda kartlara doğrudan tek tıklandığında film/dizi sayfasına kesintisiz geçiş sağlandı.

3. **🎨 UI & Izgara Yoğunluğu Optimizasyonu (`card.css`, `home.html.j2`, `category.html.j2`)**:
   - Poster ızgara kart genişliği `110px` boyutuna getirilerek daha sık ve yoğun bir poster görünümü elde edildi.
   - Uzun başlıklar için `marquee` animasyonu eklendi.
   - Sayfalardaki mükerrer/gereksiz başlıklar (`breadcrumbs`) temizlendi.

4. **📺 Android TV Client Geliştirmeleri & Derleme (`HomeScreen.kt`)**:
   - Üst kısma geniş ve modern `HomeSearchBarButton` ("🔎 Film, Dizi veya Tür Ara...") eklendi.
   - Ayarlar menüsü `SettingsMenu` modal yapısına dönüştürülerek buton eşleme, sanal fare modu ve özel koleksiyon erişimi derlendi.
   - `./gradlew.bat assembleDebug` ile APK başarıyla üretildi (`app-debug.apk` ~19.99 MB, `BUILD SUCCESSFUL`).

---

## 🎯 v0.1.26-poc ile Çözülen Sorunlar & Önceki Yetenekler

1. **TV Kumanda D-Pad Üst Menü Navigasyonu (`HomeScreen.kt`)**:
   - `[🔎 Gözat]`, `[⚙ Buton Eşleme]` ve `[🔒 Özel Koleksiyon]` butonları ana sayfa raylarının en üstüne `LazyColumn` içine odaklanabilir TV bileşenleri olarak taşındı.
2. **Dizi Oynatma ("Daha 17" ve Diğer Diziler) & Bölüm Desteği (`PlayerScreen.kt` & `NetMoviesApi.kt`)**:
   - Dizi sayfalarına tıklandığında linkin doğrudan video linki olmaması durumunda (`resp.result.isEmpty()`), otomatik olarak `/api/v1/load_item` ile bölüm listesi çözülür ve 1. bölümden oynatma başlar.
3. **Özel Koleksiyon (18+) Doğrudan Açılış (`BrowseScreen.kt` & `HQPorner.py`)**:
   - "🔒 Özel Koleksiyon" seçildiğinde boş arama yerine doğrudan yetişkin kategorileri (Popüler, 1080p, 4K vb.) ve içerik ızgarası yüklenir. Eklenti başlatma hatası giderildi.

---

## 0. SON OTURUM — 2026-08-31 — Özel Koleksiyon (18+ Stealth Vault) + HQPorner Entegrasyonu + Android TV & Web Gizli Tetikleyici (CANLI, DOĞRULANDI)

**Durum: Docker Desktop'ta modüler profil mimarisiyle uçtan uca doğrulandı. w.evaitec.com ✅ · Android TV Client v0.1.23-poc OTA Yayında ✅.**

### 1. Özel Koleksiyon (18+ Stealth Vault Modu)
- **Kamufle İsim:** Menülerde ve ayarlarda doğrudan 18+ veya yetişkin ibaresi yerine **"Özel Koleksiyon"** ismiyle yer alır.
- **Gizli Tetikleyici Kombinasyonları:**
  - **Web:** Sol üstteki Logoya 5 kez arka arkaya tıklama VEYA 3 saniye basılı tutma (Long-Press) ile mini bildirim eşliğinde açılır/kilitlenir (`sessionStorage`).
  - **Android TV / Mobil:** Üst menüdeki "🔎 Gözat" butonuna 3 saniye basılı tutma (D-pad Center / OK uzun basma) ile kilit açılır, açıldığında ek olarak "🔒 Özel Koleksiyon" butonu belirir.
- **Varsayılan Durum:** Normal kullanımda tüm 18+ içerikler ve sağlayıcılar tamamen gizlidir (`hidden_providers` & `hidden_categories`).

### 2. HQPorner 4K/1080p Yerel Motor Entegrasyonu (`engine/Plugins/HQPorner.py`)
- WARP proxy desteğiyle ISP engelleri aşılarak doğrudan 4K/1080p ve 60FPS video linkleri parse edilip oynatıcıya teslim edilir.
- `SpankBang`, `FullPorner`, `PornHub`, `xHamster`, `OxAx`, `UncutMaza` gibi uzak eklentiler de havuzda hazır olarak listelenir.

### 3. Android TV Client (v0.1.23-poc OTA)
- `client-tv` derlendi (`versionCode: 22`, `versionName: "0.1.23"`, `RELEASE_TAG: "v0.1.23-poc"`).
- GitHub Release `v0.1.23-poc` yayınlandı ve `NetMovies-TV-v0.1.23.apk` eklendi.
- Cihaz açılışında OTA güncelleme bildirimi otomatik olarak görünecektir.

---

**Durum: Docker'da uçtan uca doğrulandı. w.evaitec.com ✅. Android client v0.1.20 OTA.**
Dıştan (telefon yolu) tutarlı: **movie 20 · serie 68 · serie_foreign 10 · live 0** (2x aynı).

### Android client (client-tv) — v0.1.11 → v0.1.20 (hepsi OTA, prerelease)
- **Poster büyüteç** (focus scale+glow), **çark menüsü** (Kaynak/Dil/Altyazı/Hız), **10sn seek**, **altyazı sideload**, **çoklu kaynak**.
- **Buton-eşleme sistemi** (`input/RemoteInput.kt`, `ui/KeyMapScreen.kt`): her tuş×basış → aksiyon, SharedPreferences. Oynatıcı `useController=false` tam input sahipliği. → memory `input-mapping-architecture`.
- **Scrub önizleme (thumbnail)**: ikinci ExoPlayer düşük kalite kare (`ScrubOverlay`).
- **Dokunmatik oynatıcı kontrolleri** (telefon): videoya dokun→kontroller, tıklanabilir seekbar (pointerInput, D-pad'i bozmaz). Emoji sarı çıkıyordu → **v0.1.21: kompakt material vektör ikonlar** (`material-icons-extended`): Replay10 · play/pause (mor 44dp) · Forward10, ince 3dp bar, tek satır süre-kontrol-süre. (Metin pill'ler "kocaman" olduğu için küçültüldü.)
- **Ana sayfa çoklu tip** (`HomeViewModel`): movie+serie+serie_local+serie_foreign+live paralel → çoklu satır (önce tek "yeni filmler"di).
- **Favoriler + İzlenenler** (`data/Library.kt`, SharedPreferences), poster uzun-bas menü, oynatınca İzlenenler dolar.
- **Gözat tarayıcı** (`ui/BrowseScreen.kt`): tüm eklenti kategorileri inline + **arama** (tüm kaynaklarda paralel, per-plugin 12s timeout). ActionPicker perde dokunuş fix.

### Engine / kaynaklar (CANLI, ev sunucusunda — .env gitignored)
- **DiziMom kurtarıldı**: domain taşınmış `dizimom.work`→**`dizimom.food`** (`.env DIZIMOM_URL`). → Yabancı Diziler 10.
- **WARP egress** (`docker-compose.yml` `warp` servisi, gost HTTP proxy `netmovies-warp:8080`): TR SNI/DPI engelini aşar. **SEÇİCİ** kullanım — sadece bloklu plugin. Global proxy YASAK (movie=0 yapıyor: CF IP HDFC/RecTV'yi bloklar).
- **Dizilla kurtarıldı**: `dizilla.nl`→**`dizilla.club`** (SNI-bloklu) → `.env DIZILLA_URL` + `Dizilla.__init__` self.httpx'i **proxy'li düz httpx.AsyncClient** ile değiştirir (PluginBase FallbackHTTPX proxy'yi uygulamıyor). → 14 item. Diğerleri direkt (movie korunur).
- **ENGINE_WORKERS=1** (`.env`): 2 worker × ayrı in-memory cache → aggregate her seferinde cold-scrape → stream "Server disconnected"/boş oluyordu. Tek worker = paylaşılan cache = tutarlı.
- Docker PC'de kapalıydı (telefon 530'un asıl sebebi) → açıldı; tünel netns tuzağı: engine recreate GÜVENLİ, stream recreate→`docker compose --profile tunnel up -d --force-recreate cloudflared`.

### KALAN (sıradaki oturum)
1. **Dizipal portu**: upstream `DiziPal.kt` (247 satır, `/tmp` yok artık — GitHub'dan çek) → `engine/Plugins/DiziPal.py` PluginBase türevi. SNI-bloklu + numaralı döner domain (dizipal950 ölü) → WARP proxy (Dizilla deseni: `__init__` proxy'li httpx) + güncel domain keşfi. Kanıt kapısı: get_main_page item>0 + load_links stream verir + **movie 20 bozulmaz**.
2. **Canlı TV** (live=0): RecTV live endpoint boş — WARP'la düzelir mi bak; düzelirse web'de iframe "eski player" emekli, her yer zengin player (bkz. #3).
3. **Web player birleştirme**: `/resmi-kaynak`→`official_player.html.j2` (iframe, "eski") vs `/izle`→`player.html.j2` (zengin). Resmi kaynaklar harici sayfa (HLS yok) → canlı TV HLS gelince iframe kartları kaldır.
4. **Kalıcı tünel fix** (opsiyonel): cloudflared'i stream netns'inden çıkar + CF panelde origin `stream:3310` → restart'lara dayanır.

### Servis güncelleme (bu oturum)
`docker compose --profile tunnel up -d --build --pull always` → engine/stream rebuild (base imaj `--pull`), engine/stream/tunnel birlikte recreate (tünel yeni netns'e "Registered tunnel connection" ist05). doh pinli (2026.1.2) kalır, warp/cloudflared son sürüm. Docker Desktop **uygulaması** güncellenmedi (elle: `winget upgrade Docker.DockerDesktop`, restart ister).

### Bu oturum commit'leri (dalda, push edildi)
client-tv: `c403710 8e18a30 054e9da 0525815 bad97c0 b139e9b ed6b1c8 f92ccaf c3d987a fcbe69b f3e0f5a`(v0.1.21 kompakt ikon) · infra/engine: `acf8a29 0d66096`(WARP) + Dizilla commit + HANDOFF. Memory: `input-mapping-architecture`, `plugin-domain-moves`.

---

## 1. ÖNCEKİ OTURUM — 2026-08-29 — Uzak Sağlayıcı (Geniş Katalog) + Canlı Sağlık + Tünel Fix (CANLI, DOĞRULANDI)

**Durum: TAMAM, Docker'da uçtan uca doğrulandı. 2 commit dalda (`d90cea2`, `61c4a81`). Yerel app ✅ · w.evaitec.com ✅.**

### Talep (Dean)
"DiziPal ekleyelim ya da KekikAPI'yi ekleyip eklenti seçimi sunalım + 'eklentileri güncelle' butonu;
180 küsur eklenti var, hepsini alıp o yenileyince bizde de otomatik yenilensin."

### Kanıtla çürütülen 3 varsayım (araştırma + Docker testi)
- ❌ CloudStream'in 180 eklentisi = **Kotlin**, Python motoruna yüklenemez (`.cs3` Android).
- ❌ pip `KekikStream>=2.5.0` paketi eklenti **bundle etmiyor** — `Plugins/` klasöründe sadece `__init__.py`
  (container'da kanıtlandı: `GLOBAL_COUNT: 0`). Her scraper el-porttur; hazır Python kataloğu YOK.
- ❌ **DiziPal self-host portu YAPILMADI:** `dizipal950` domaini ölü + site **Cloudflare 403**
  (profesyonel watchbuddy sunucusunda bile DiziPal 403). Bozuk eklenti eklenmedi.

### Çözüm — Uzak Sağlayıcı "Geniş Katalog" (Dean'in fikrinin çalışan hali)
Motora dokunmadan, stream'in mevcut remote-provider desteği üzerine kuruldu:
- **`provider_url`** admin_config'e eklendi (sunucu-taraflı → telefon/Mibox/PC hepsi aynı sağlayıcıyı görür).
  `detect_provider` sırası: query > cookie > **admin.provider_url** > yerel.
- Admin panelde **"Uzak Sağlayıcı"** kartı: URL alanı + **"watchbuddy"** hızlı-set + **"Yerel'e dön"**.
- **KANIT (Docker uçtan uca):** `provider_url=https://stream.watchbuddy.tv` → `/api/admin/catalog`
  **203 eklenti** (DiziPal dahil!) netmovies'in kendi `RemoteProviderClient`'ıyla geldi. CF/domain
  bakımı upstream'de. DiziPal remote katalogda otomatik gelir — bizde iş gerektirmez.
- **"Şimdi canlı tara"** butonu: `/api/admin/health?force=1` → engine'in 6 saatlik cache'ini atlar
  (kanıt: yerel 7 kaynak, 4 canlı).

### KÖK NEDEN #1 — remote provider URL (fix `61c4a81`)
`RemoteProviderClient` uç noktalara `/api/v1/...`'i **kendi ekler**. provider_url'e `/api/v1` yazmak →
`.../api/v1/api/v1/get_all_plugins` çift path → **403**. **Kök adres ver** (`https://stream.watchbuddy.tv`,
`/api/v1` YOK). watchbuddy UA/TLS değil, path bug'ıydı. → Memory: `remote-provider-url-root`.

### KÖK NEDEN #2 — tünel netns (bu oturumda operasyonel, kod değil)
`docker compose up -d --build stream` (tek servis rebuild) → stream container recreate → cloudflared
`network_mode: service:stream` ile eski stream'e pinli kaldı → öksüz ("network is unreachable",
restart edilemiyor) → **w.evaitec.com 530**. **Fix:** `docker rm -f netmovies-tunnel &&
docker compose --profile tunnel up -d cloudflared` → tünel 4 bağlantı kaydetti, `w.evaitec.com`→200.
→ Memory: `stream-tunnel-netns-rebuild`. **Kural:** stream'i tek başına rebuild ettiysen tüneli recreate et.

### TV CLIENT ÇİLESİ (aynı oturum, ikinci yarı) — v0.1.4 → v0.1.10 + firewall
Dean APK'yı kurdu, sırayla çıkan sorunlar ve kökleri (hepsi client-tv, `client-tv/app/src/main`):
1. **192 bağlanmıyor** → yayınlı v0.1.3 APK `NETMOVIES_BASE_URL=192.168.x` ile derlenmişti. Fix: boş bırak → varsayılan `w.evaitec.com` (v0.1.4). `build.gradle.kts`.
2. **Siyah-üstüne-siyah yazı** → tv-material3 `Text` rengini `LocalContentColor`'dan alır, içerik Surface'te değildi → varsayılan siyah. Fix: `MainActivity` `CompositionLocalProvider(LocalContentColor=#EDEDF2)` (v0.1.5). Ekran görüntüsüyle DOĞRULANDI.
3. **D-pad'de seçilemiyor** → ilk karta focus yok. Fix: `HomeScreen` `FocusRequester`+`requestFocus` (v0.1.5).
4. **OTA "İndir" çalışmıyor (TV)** → `Updater.openDownload` `ACTION_VIEW(url)` = tarayıcı, TV'de tarayıcı YOK. Fix: uygulama-içi OkHttp indir + `FileProvider` ile kur (v0.1.6). `Updater.kt`+`UpdateViewModel.kt`.
5. **IPv6 bağlanamıyor** (`.../[2a06:98c1..]:443`) → CF IPv6, ağda bozuk. Fix: `PreferIpv4Dns` (v0.1.7). `data/HttpDns.kt`.
6. **Dokunmatik yok (telefon)** → tv-material3 D-pad odaklı. Fix: foundation `clickable` (`ui/TouchButton.kt`, PosterCard Box) (v0.1.9).
7. **CF-TR IP bloku** (`188.114.96.7:443` açmıyor) → Cloudflare cihaz ağına TR'de bloklu IP döndürüyor; gerçek kayıtlar 172.67.143.235/104.21.55.13 çalışıyor. **KÖK ÇÖZÜM = yerel yol** (aşağıda) + palyatif IP-pin/timeout (v0.1.10).

**ASIL ÇÖZÜM — "önce local, olmazsa uzak" (v0.1.8) + firewall:**
`data/ServerResolver.kt`: açılışta `LOCAL_URL` (192.168.1.185:3310) `/api/v1/health` 1.5s probe → canlıysa yerel, değilse `BASE_URL`. `BaseUrlInterceptor` tüm istekleri+posterleri aktif sunucuya yönlendirir. `HomeViewModel.load` başında `ServerResolver.reset()` (retry taze seçer). **Windows firewall "NetMovies 3310" inbound AÇILDI** (bu oturum, admin PS). TV aynı WiFi → yerele düşer, CF bypass. → Memory: `client-cf-tr-local-first`.

**Not:** `LOCAL_URL` gradle property `NETMOVIES_LOCAL_URL` (vars. `http://192.168.1.185:3310`). PC LAN IP değişirse burayı güncelle. OTA download da CF/GitHub IP'ye takılabiliyor → gerek/kesin durumda APK elle.

### Değişen dosyalar (stream tarafı)
- `stream/Public/Home/Libs/admin_config.py` — `provider_url` default + normalize
- `stream/Public/Home/Libs/helpers.py` — `detect_provider` admin fallback
- `stream/Public/Home/Routers/admin.py` — `admin_health ?force=1` forward
- `stream/Public/Home/Static/JS/admin.js` — provider alanı render/collect + watchbuddy(kök) + force health
- `stream/Public/Home/Templates/pages/admin.html.j2` — Uzak Sağlayıcı kartı + dürüst notlar

### Canlı durum (bu oturum sonu)
- Yerel: stream+engine `healthy`, `/api/v1/health`→200, ana sayfa(auth)→200, provider=**yerel** (varsayılan).
- Dış: `https://w.evaitec.com/`→200 (tünel yeniden bağlandı).

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-28 (3. çeyrek) — "dizi sayfası eski düzen" = tarayıcı cache (KÖK NEDEN + kalıcı fix)

**Durum: KÖK NEDEN BULUNDU + DOĞRULANDI, kalıcı önlem commit'li (`de54619`). Kod bug'ı DEĞİLDİ.**

### Şikâyet
Dean: "Diziler/Türkçe diziler sayfasına girince eski düzene geçiyor, posterler her sayfada aynı
boyutta olsun." (Poster boyutu sorunu.) Dean tercihi: **hepsi küçük 130px, her sayfada** (AskUserQuestion).

### Kök neden (kanıtlı) — sunucu DEĞİL, tarayıcı cache
- Küçük 130px grid kuralı (`a1b63fa`, "arama/kategori posterleri... 130px") **zaten canlıda**.
- **KANIT (curl, uçtan uca):** `curl localhost:3310/static/home/CSS/style.bundle.min.css` →
  `grid.grid-results{grid-template-columns:repeat(auto-fill,minmax(130px,1fr))}` — sunucu doğru servis ediyor.
- **Asıl sorun:** `_html_taban.html.j2` L66/L134'te CSS/JS bundle linkinde **cache-bust YOKTU**
  (`style.bundle.min.css` sabit URL). Bundle içeriği değişse de URL sabit → tarayıcı eski bundle'ı
  süresiz cache'liyor → Dean hard-refresh yapmadan eski büyük grid düzenini görüyordu. Dizi sayfasında
  fark etmesi: o sayfayı fix'ten sonra ilk kez o cache'le açtığı için.
- Not: host diskindeki `style.bundle.min.css` (08-27) `card.css`'ten (08-28) bayat GÖRÜNÜYOR ama önemsiz —
  bundle gitignored, boot'ta `basla.py` üretiyor; **container'ın bundle'ı doğruydu** (grep=1).

### Fix (commit `de54619`)
- `helpers.py` → `build_context`'e `asset_version` (CSS+JS bundle mtime'ının max'ı, `_asset_version()`).
- `_html_taban.html.j2` → CSS (L66) + JS main (L134) linkleri `?v={{ asset_version }}` ile versiyonlandı.
- Etki: bundle her rebuild'de (boot/elle minify) URL değişir → tarayıcı otomatik taze çeker.
  Sözdizimi doğrulandı (`py_compile` OK). **Bir sonraki rebuild'de aktif olur** (bu oturumda restart YAPILMADI → tünel korundu).

### Dean'in yapacağı (anında çözer, deploy beklemeden)
1. **Tarayıcıda Ctrl+Shift+R (hard refresh)** → posterler her sayfada 130px olur (sunucu zaten doğru).
   Telefon/TV Bro: önbelleği temizle.
2. Cache-bust'ı kalıcı aktive etmek için müsaitken normal deploy: `git pull && docker compose up -d --build`.

### Uygulama (native TV) durum özeti — Dean sordu (bu oturum başı)
- **Web (PWA): HAZIR/canlı.** **Native Compose-TV: POC** — derleniyor, OTA'lı release var, içerik listeleniyor;
  🔴 HLS oynatma Mi Box'ta uçtan uca DOĞRULANMADI (risk kapısı). Süre tahmini: oynatma bugün geçerse
  "izlenebilir native app" ~1 hafta; cast+diziler+arama tam cila ~2-3 hafta. Detay: `memory/client-tech-decision.md`.

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-28 (2. yarı) — POC BUILD DOĞRULANDI + PUBLIC/RELEASE/OTA + İÇERİK FIX (canlı)

**Durum: UÇTAN UCA ÇALIŞIYOR + DOĞRULANDI (ev makinesi + tünel).** Bu oturumda POC gerçekten
derlendi, GitHub'a public + release + OTA kondu ve "ana ekran boş" kök nedeni bulunup canlıya alındı.

### Client (Compose-TV) — derlendi + yayında
- **BUILD DOĞRULANDI:** temiz checkout'tan `./gradlew.bat assembleDebug` → BUILD SUCCESSFUL,
  `app-debug.apk`. Stack: AGP8.5.2/Kotlin2.0.20/Gradle8.9/Java21, minSdk26.
- **Repo PUBLIC yapıldı** (önce 78 commit secret taraması TEMİZ — `.env`/token yok).
- **Release + OTA:** `v0.1.0..v0.1.3-poc` release'leri. OTA = app açılışta GitHub `/releases`
  kontrol → "Güncelleme mevcut" şeridi → **İndir (tarayıcıda)** → kur. (In-app FileProvider kurulum
  telefonda tökezledi → tarayıcı-indirmeye çevrildi.) `/releases/latest` prerelease'i atlar → `/releases` listesi.
- **v0.1.3 kilit sürüm:** `BASE_URL=w.evaitec.com` (tünel) → **her ağdan çalışır**, ev WiFi şart değil.
  Ayrıca dark tema (tv `darkColorScheme`), adaptive/yuvarlak ikon, okunur banner.
- **İyileştirmeler:** kategori-raylı home, player yükleniyor/hata-retry + keepScreenOn, posterler `/proxy/image`.
- APK direkt: `https://github.com/evatechnosoft/netmovies/releases/download/v0.1.3-poc/netmovies-tv-v0.1.3-poc.apk`

### 🔴 "Ana ekran boş" KÖK NEDEN + FIX (PR #5, canlı) — en kritik
1. **Route bug:** stream'de `/api/v1/aggregate_new` route'u **kayıtlı değildi** (ilk build docker-cache
   registration'ı atlamıştı) → istek `/`'a **302** → app hiç veri almıyordu. Düzeltildi (Routers/__init__ cp + rebuild; artık image'da kalıcı, grep=1).
2. **Boş-cache zehiri (asıl içerik fix, `_cacheable`):** `fuck_dmca` boş agregasyonu da 10dk cache'liyordu →
   geçici kaynak timeout'unda 0 cache'lenip kaynak düzelse bile boş servis. Fix: `/aggregate_new` yalnız
   `items` doluysa cache'lenir. **KANIT: stream `aggregate_new?type=movie` → count=20** (serie_local=15).
3. **Dayanıklılık:** engine `_istek.py` aggregate timeout 30→120s; `WEB_WORKERS 1→2` (tek worker yavaş
   agregasyonda blokluyordu); `load_links` timeout 10→25s; provider_client split timeout.
- **PR #5 merge edildi** → `claude/...` (`a4cfd76`). Stream image merged kodla rebuild → **durable**.

### Kaynak sağlığı (kanıt) + kalan
- **4/7 canlı:** HDFilmCehennemi, DiziBox, DiziYou, M3U · **ÖLÜ:** DiziMom, Dizilla, RecTV.
- HDFC `get_main_page` ham çağrıda **20 film** dönüyor (çalışıyor). DiziBox **yavaş** (ReadTimeout, bazen düşer).
- **Kalan (bloke değil):** DiziYou kategorileri tür-bazlı → generic "serie" hint'ine uymuyor (serie_local/foreign çalışıyor);
  DiziBox scrape hızı. İçerik zenginliği için sonraki tur.

### ⚙️ Operasyon dersleri (bu oturumdan — gelecek dikkat)
- **Stream restart = `basla.py` her açılışta 51 dosya minify (~1dk boot)** → restart sonrası 3310 geç 200 verir; "down" sanma.
- **`docker compose up --build stream` engine'i de recreate edebilir** → engine cold-boot (~40s domain keşfi) → o an aggregate boş.
  Kod değişikliğini **restart'la boğmadan** yay: tek `docker cp` + tek restart; art arda eşzamanlı probe = tek-worker'ı tıkar.
- **Git Bash docker exec/cp mutlak yol** `/usr/src/...` → `MSYS_NO_PATHCONV=1` şart (yoksa `C:/Program Files/Git/usr/...` olur).
- Stream kök: `/usr/src/Stream`, Engine kök: `/usr/src/KekikStreamAPI`.
- Reusable rehber: `~/.ai/guides/android-client-engine-ota.md` (gitignore `data/` tuzağı + OTA/release reçetesi).

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-28 (1. yarı) — 4 web şikayeti fix + Kotlin Compose-TV POC iskeleti

**Durum: KODLANDI + COMMIT'Lİ, runtime doğrulaması Dean'de.** Commit'ler: `a1b63fa..a052479`.
Dean'in bildirdiği 4 web şikayetinin kök-neden çözümü + Compose-TV client POC'u başlatıldı.

**Web fix'leri (stream):**
- `a1b63fa` **Sayfa düzeni birliği**: `.grid.grid-results` sabit 6-grid (geniş ekranda ~190px büyük)
  → `repeat(auto-fill, minmax(130px,1fr))` = ana sayfa/provider (130px carousel) ile aynı küçük boy.
  **Deploy:** bundle yeniden üret + hard refresh (aşağıda).
- `c445633` **#2 Poster proxy** (`/proxy/image`, SSRF korumalı: http(s)+public IP, 8MB, 7g cache) —
  posterler artık stream/residential IP + doğru Referer ile; Jinja `poster()` global + JS wrap ile
  TÜM poster sayfalarında. **#3 Kaynak dayanıklılığı**: provider_client split timeout (connect=5s,
  read=20s) → ölü kaynak hızlı düşer; error.html.j2'ye "Tekrar dene". **#4 Nav**: ilk mousemove'da
  mouse-mode otomatik (D-pad tetiklemez → TV korunur).

**Kotlin Compose-TV POC (`client-tv/`)** — `a052479`:
- Liste (`aggregate_new` → 6'lı poster grid, D-pad) + Media3/ExoPlayer HLS (load_links referer/UA
  header enjekte, proxy'siz). Retrofit+kotlinx.serialization. gradlew ile buildable (wrapper jar dahil).
- Java21/AGP8.5.2/Kotlin2.0.20/Gradle8.9, minSdk26. Build+sideload: `client-tv/README.md`.
- stream'e `/api/v1/aggregate_new` client-facing proxy eklendi (client bunu çekebilsin diye).
- ✅ **BUILD DOĞRULANDI** (`524d13d`): temiz checkout'tan `./gradlew.bat assembleDebug` → BUILD
  SUCCESSFUL, `app-debug.apk` ~12.7MB. Stack (AGP8.5.2/Kotlin2.0.20/Gradle8.9/Java21) tutuyor.
- ⚠️ **OYNATMA (HLS) doğrulanmadı** — sadece DERLEME doğrulandı. Uçtan uca Mi Box'ta test edilecek
  (encoded_url çift-kodlama + segment header riskleri `client-tv/README.md`).
- **İyileştirmeler** (hepsi derlenip doğrulandı): kategori-raylı home + player yükleniyor/hata-retry
  + posterler `/proxy/image`'den + `keepScreenOn`.
- 🐞 **GITIGNORE TUZAĞI (gelecek oturumlar dikkat):** kök `.gitignore`'daki `data/` kuralı Kotlin
  `tv/data/` KAYNAK paketini de gizliyordu → ilk commit'te data katmanı sessizce atlanmıştı.
  `client-tv/.gitignore`'a negasyon eklendi (`!.../tv/data/`). Yeni `data/` adlı KAYNAK dizini
  eklerken aynı tuzağa dikkat.

**POC'un sıradaki adımı:** Diziler (serie) sekmesi — `aggregate_new type=serie` + `load_item` →
bölüm seçimi → oynat. **ÖNCE oynatma Mi Box'ta doğrulanmalı** (üstüne feature bindirmeden).

**Dean'in yapacağı doğrulama:**
1. Web fix'lerini canlıya al (restart yok, tünel korunur):
   `MSYS_NO_PATHCONV=1 docker exec -w /usr/src/Stream netmovies-stream python3 -c "from build_assets import minify_assets, bundle_css; minify_assets(); bundle_css()"`
   → Ctrl+F5. (Yeni Python endpoint'ler/timeout için stream restart de gerekir: `docker compose up -d`.)
2. TV client: `cd client-tv` → `gradle.properties`'e ev IP → `./gradlew.bat assembleDebug` → `adb install`.

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-27 (akşam) — cloudstream UI + ölü-kaynak dayanıklılığı + CLIENT KARARI

**Durum: AYAKTA + DOĞRULANDI (ev makinesi, `localhost:3310`).** UI yeniden düzenlendi, deploy `docker cp`
+ Jinja auto-reload / CSS minify ile yapıldı (restart YOK → tünel korundu). Commit'ler: `c48cbd7..9c224ef`.

**Bu oturumun işleri (commit'li):**
- `c48cbd7` **ölü kaynak dayanıklılığı** (KÖK NEDEN): `home_categories` sadece SAĞLIKLI kaynağı kart yapar;
  `aggregate_new` ölü kaynağı `plugin_health`'ten atlar (30s→~6s hız); `_is_alive` 403/451/404/410'u ÖLÜ
  sayar (eskiden bloke domaini "canlı" seçip içeriksiz kart üretiyordu). Engine: `/home_categories` endpoint
  + `serie_local`/`serie_foreign`/`live` ipuçları. **Kanıt:** 4/7 kaynak canlı (HDFC/DiziBox/DiziYou/M3U),
  home 200/191 poster; ölü DiziMom/RecTV kartları gizlendi.
- `86ddafa` **cloudstream tarzı kompakt ana sayfa**: üstte tek satır ince pill buton bandı (`quick-nav`) —
  kategoriler + Favori/İzlenecek/Devam + Kaynaklar + Kanallar. Büyük kutular (Resmi Kaynaklar/Öne Çıkanlar/
  Kanallar dropdown) kaldırıldı → butona basınca PANEL açar (accordion + JS lazy fetch: `/api/v1/favorites`,
  `/lists/izlenecek`). Header'a "reklamsız izle" tagline.
- `68fc088` **player**: sinema modunda geri butonu autohide (sol-üst köşe hover/focus'ta çıkar; hep görünmüyordu).
- `9c224ef` **tutarlı 6'lı grid**: `.grid.grid-results` sabit `repeat(6,1fr)` (küçük ekran 5/4/3/2), specificity
  ile responsive `.grid`'i geçer → kategori/arama/eklenti "her sayfa aynı". `.page-header-title` xxl→lg (küçüldü).
  **Not:** CSS bundle değişti → tarayıcıda hard refresh (Ctrl+F5) gerekir.

### 🎯 YENİ YÖN — CLIENT: Kotlin + Jetpack Compose for TV (2026-08-27 kararı)
**PWA Android TV'de çalışmıyor** (Chrome yok → TWA sağlayıcı yok; "bubblespan"=Bubblewrap da TWA ürettiği için
çözüm değil). Karar: **Kotlin + Compose for TV + Media3/ExoPlayer** (TV'de D-pad+HLS en olgun/resmi-stable).
Kapsam: Android (TV öncelik + telefon); **web=mevcut PWA kalır**. Mi Box'a sideload (ARM ABI). Engine DEĞİŞMEZ
(client-agnostic) — client sadece API tüketir. Detay: `memory/client-tech-decision.md`.
**Cast/kumanda (WatchBuddy modeli — Dean isteği):** Telefon (mevcut PWA) = **controller** (hızlı arama/seç,
OYNATMAZ → sadece "TV'de oynat" komutu); TV (Compose) = **player** (oynatır + kendi D-pad'i); Engine = **relay**
(telefon `POST /api/v1/cast` → TV `GET /cast` long-poll/WS dinler; merkezî, tünelle uzaktan da çalışır). Telefon
PWA controller rolünde ideal (oynatma yok → TV kısıtı yok). Backlog'daki "kendi watch-party" bu.

**İlk adım (POC, 3 parça):** (1) Compose-TV app: `aggregate_new` listele + HLS oynat (Media3) + `/cast` dinle;
(2) Telefon PWA'ya "TV'de oynat" butonu → `POST /cast`; (3) Engine `/cast` relay endpoint (scrape/proxy DEĞİŞMEZ).
Mi Box + telefon uçtan uca test. **Bu yeni iş taze oturumda başlamalı** (bu oturum uzun). Detay: `memory/client-tech-decision.md`.

### Bekleyen (bu oturumdan devir)
1. **Sıralama/"eski diziler"**: aggregate DiziBox "Yerli Diziler" ARŞİVİ çekiyor (eski dahil), "yeni eklenen"
   feed'i değil → yeni sıralama yok. Kaynak feed davranışı incelenmeli (dikkatli, içerik bozmadan).
2. Yabancı dizi kartı: DiziMom (yabancı kaynağı) sandbox'ta ölü → ev'de canlı mı doğrula.
3. Kalıcı image: değişiklikler `docker cp` ile canlı + git commit'te; image güncel değil → müsaitken
   `git pull && docker compose up -d --build` (build Docker Desktop'ta ara ara 500/asılma yaşadı).

---

## SON OTURUM — 2026-08-27 (TV/kumanda UX + sinema player + yeni kaynaklar + local deploy)

**Durum: AYAKTA + DOĞRULANDI (ev makinesi).** `localhost:3310` → HTTP 200 (home + `/api/v1/health`).
Auth **bilerek KAPALI** (Dean kararı — kumandayla şifresiz giriş). Container'lar rebuild edildi;
9 commit push'landı (`e9d6acc..547c496`), dal `origin` ile eşit.

**Bu oturumun commit'leri (yeni→eski):**
- `547c496` sayfalar: basılı-tutma menüsü + odak çerçevesi HER sayfada (tv-home-actions base'e taşındı; `link` modu = arama/kategori kartında tek tık AÇAR, gezinme korunur)
- `73f0929` **player sinema modu**: `body.cinema-mode` → sadece video (header/footer/başlık/kaynak-listesi/benzer/geri gizli, video 100dvh contain). Kaynak + Harici oynatıcı **dişli menüsüne** taşındı (overlay, `data-cinema-open`). sources.js/loadVideo'ya DOKUNULMADI. Geri-alma: body sınıfı kalksın.
- `e181c0c` global `:focus-visible` çerçevesi (her sayfa, D-pad+klavye+arama kutusu) + kanallar hafif metin listesine çevrildi
- `7e9371e` mouse-modu güçlü imleç vurgusu + **auth KAPALI** (compose'da `AUTH_USER/PASS: ""`) + kurulamayan tarayıcıda ölü PWA butonu gizle
- `335ab86` PWA kur butonu + mouse modu + dokunma efekti
- `c23c53b` merkezi izleme ilerlemesi (content_url migration + `/api/v1/progress`) + kullanıcı listeleri (izlenecek/planlandı/takip, SQLite `user_lists`)
- `caac111` hızlı kanallar rafı + izlemeye-devam kutusu + aggregate timeout(6s/3s) + "Son Bölümler" kategori yakalama
- `8de0d0e` **yeni kaynaklar**: DiziBox, Dizilla, DiziMom + ortak `__dizi_common.py` (engine'de YÜKLÜ doğrulandı)
- (`docs/DENETIM-2026-08-26.md` = eski `search.md` salt-okunur denetim raporu, docs'a taşındı)

### ⚠️ Doğrulama durumu (kanıtlı)
- CSS bundle canlı: `mouse-mode`/`focus-visible`/`channels-list-item` bundle'da mevcut (grep).
- Player cinema + linkMode image'da mevcut (container grep).
- 3 dizi plugin engine'de yüklü (`get_plugin_names`).
- **Runtime tam test EDİLMEDİ**: film oynatma/sinema modu/overlay'ler tarayıcıda Dean'in gözüyle doğrulanacak.

### 🔴 Kanallar BOŞ (kök neden: data, kod değil)
`quick_channels` → `result:[]`. Sebep: `.env`'de **`M3U_SOURCES` boş** + RecTV bloke. Liste UI'si hazır
ama kaynak yok. Çözüm: `.env`'e M3U listesi ekle VEYA güncel `RECTV_URL` bul.

### ⚙️ Deploy operasyon notları (ÖNEMLİ)
- **watchmedo override → `docker-compose.dev.yml` (OPT-IN) olarak yeniden adlandırıldı.**
  Eskiden `docker-compose.override.yml` otomatik uygulanıyordu → Windows'ta startup'ı kilitliyordu.
  Artık **varsayılan `docker compose up -d --build` = temiz production** (her yerde güvenli, doğrulandı: 0 watchmedo).
  - **Production / ZimaOS 7/24:** `docker compose -f docker-compose.yml up -d --build` (güncelleme: `git pull && ... up -d --build`).
  - **Dev auto-reload (opt-in, sadece Linux/ZimaOS):** `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`.
    Windows'ta KULLANMA (inotify geçmez) → base + elle rebuild.
- **w.evaitec.com CANLI** (bu oturumda tünel yeniden bağlandı, HTTP 200 doğrulandı). Tünel `network_mode: service:stream`
  → stream her recreate olunca kopar; geri: `docker compose -f docker-compose.yml --profile tunnel up -d --no-deps --force-recreate cloudflared`.
- Engine container'ı bir ara `<hash>_netmovies-engine` adıyla kaldı (kozmetik); `docker compose -f docker-compose.yml up -d` normalize eder.

### Kalan iş (bu oturumdan)
1. **Dean runtime testi**: film aç → sinema modu (sadece video), kontroller TV Bro imleç modunda çıkıyor mu, dişli→Kaynak/Harici oynatıcı overlay + Nova/VLC çalışıyor mu, "ana sayfa açıyor" hâlâ var mı (varsa hangi film/kaynak).
2. **Kanal kaynağı**: `M3U_SOURCES` veya `RECTV_URL` gir.
3. Yeni dizi kaynakları (DiziBox/Dizilla/DiziMom) + DiziYou **uçtan uca** test (içerik dönüyor mu, selector tutuyor mu).
4. P0 (devam): CF token rotate + kalıcı tünel origin `stream:3310` (Dean CF panel).
5. ZimaOS 7/24 deploy (PC o ağa gelince).

---

## SON OTURUM — 2026-08-24 akşam (canlı + oynatıcı/UI iyileştirmeleri)

**Durum: ÇALIŞIYOR.** İzleme her yerden: `localhost:3310`, `192.168.0.28:3310` (LAN),
`w.evaitec.com` (tünel). Auth: **`dean` / 1234** (kullanıcı adı KÜÇÜK harf). Web player'da
film oynuyor (Dean doğruladı). `.env` içinde `CF_TUNNEL_TOKEN` dolu (netmovies tunnel `46f5bbe3`).

**Bu oturumun commit'leri:**
- `2fb5cb1` dev-reload YAML katlama fix + cloudflared origin (network_mode: service:stream)
- `4822141` oynatıcı Türkçe dublaj/altyazı otomatik (yeni `lang-utils.js`) + i18n header reload
- `c422fa6` mobil dokunmatik: tek dokunuş oynat/duraklat + orta çift-dokunuş tam ekran (sol/sağ seek)
- `7695919` pagination: "sonraki sayfa" yalnızca `SAYFA` placeholder'lı (gerçek sayfalayan) kaynaklarda
- `f99eff0` layout: ana sayfa/player üst boşluğu azaldı, ilk raf arama çubuğu altına
- `5228bea` canlı arama (yazdıkça, 300ms debounce, min 3 karakter)
- `3b7891d` harici oynatıcı (Nova/VLC/MX): `force_proxy=1` ile segmentlere header enjekte

### ⚠️ KRİTİK ORTAM NOTLARI (Windows'ta geliştirenler için)
1. **watchmedo (otonom reload) Windows/Docker Desktop'ta ÇALIŞMIYOR** (volume inotify container'a
   geçmiyor). Kaynak `.js/.css` değişince `.min.js/.min.css` OTOMATİK üretilmez. Elle tetikle
   (container restart YOK → tünel korunur):
   ```
   MSYS_NO_PATHCONV=1 docker exec -w /usr/src/Stream netmovies-stream \
     python3 -c "from build_assets import minify_assets, bundle_css; minify_assets(); bundle_css()"
   ```
   (Git Bash'te `MSYS_NO_PATHCONV=1` şart, yoksa `-w` path'i bozulur: "Cwd must be absolute".)
2. **cloudflared `network_mode: service:stream`** → stream her `restart`/`recreate` olduğunda tünel
   KOPAR (`w.evaitec.com` → HTTP 530). Düzeltme: `docker compose --profile tunnel up -d
   --force-recreate cloudflared`. **Kalıcı çözüm (yapılmadı):** Cloudflare panelinde tünel origin'i
   `localhost:3310` → `stream:3310` yap, cloudflared'i normal `internal` network'e al → restart'lara dayanır.

### Kalan iş (aciliyet sırası)
1. **Harici oynatıcı testi** — Dean force_proxy sonrası Nova/VLC ile film başlıyor mu doğrulayacak.
2. **Kalıcı tünel** — CF panel origin `stream:3310` (yukarıda; Dean panel erişimi).
3. **ZimaOS 7/24 deploy** — PC şu an ZimaOS ağında DEĞİL ("Deancjx"/`192.168.1.x` WiFi menzilde yok;
   PC "Huntercjx"/`192.168.0.x`'te). `ssh deanos` timeout. PC o ağa gelince: `ssh deanos` → git clone
   `/DATA/AppData/netmovies` + `.env` (dean/1234 + CF_TUNNEL_TOKEN) + `docker compose --profile tunnel up -d --build`.
4. **Mi Box** — JioSphere/TubeMate genel web açamıyor; **TV Bro** (Android TV browser) öner. w.evaitec.com çalışıyor.
5. **"Smallville" gibi eski diziler** — kaynak ana sayfa sıralaması; "yeni çıkanlar önceliği" olarak ele alınacak (pagination'dan AYRI).
6. Mi Box native D-pad oynatıcı kontrolleri — ertelendi (TV Bro imleç modu yeterli olabilir).

---

## 1. Proje nedir?
Reklamsız, kişisel, "tıkla-izle" odaklı **film / dizi / canlı TV** uygulaması. İki servis + iki yardımcı:
- **engine/** — KekikStream 3.8.x (Python **3.14**) sağlayıcı API. Kendi eklentilerimiz `engine/Plugins/`.
- **stream/** — Web arayüzü + header-enjekteli video/altyazı **proxy** + API gateway. (WatchBuddy-tv/Stream fork'u vendor'landı, reklamları söküldü.)
- **doh** — DNS-over-HTTPS resolver (ISP DNS engelini aşar), docker-compose servisi.
- **cloudflared** — `w.evaitec.com` → eve tünel (profile: tunnel).

Tek komut: `docker compose up -d --build` → `http://localhost:3310` (auth: **dean / 1234**).

---

## 2. Kaynaklar (engine/Plugins/)
| Eklenti | İçerik | Not |
|---|---|---|
| `RecTV.py` | Canlı TV + Son Filmler + Son Diziler | Tek API, extractorsuz. Domain `b.prectvNN.sbs` sık değişir → `.env` `RECTV_URL` ile güncelle |
| `HDFilmCehennemi.py` | Film (TR dublaj+altyazı) | Gömülü P.A.C.K.E.R unpacker |
| `DiziYou.py` | Dizi (yerli/yabancı, dublaj+altyazı) | Extractorsuz, doğrudan storage m3u8 |
| `M3UPlaylist.py` | Kendi M3U/M3U8 listelerin | `M3U_SOURCES` env; EXTVLCOPT/EXTHTTP header desteği |

Yeni eklenti ekleme rehberi: `docs/KURULUM.md` §6. Referans scrape mantığı: `keyiflerolsun/Kekik-cloudstream` (Kotlin).

---

## 3. Öne çıkan özellikler ve dosya haritası
- **Yönetim paneli:** `stream/Public/Home/Routers/admin.py`, `stream/Public/Home/Libs/admin_config.py`, `stream/Public/Home/Static/JS/admin.js`, template `pages/admin.html.j2`. Kaynak/kategori gizleme (Asya vb. varsayılan gizli), öne çıkanlar, puan eşiği, canlı sağlık göstergesi. Merkezi JSON config (`/data/admin.json`).
- **Sağlık kontrolü:** `engine/Public/API/v1/Routers/plugin_health.py` (açılışta + 24 saatte bir; `/api/v1/plugin_health`).
- **Harici oynatıcı:** `stream/Public/Home/Static/JS/external-player.js` + `pages/player.html.j2`. Nova/MX/VLC/kopyala (Android intent + proxy URL). Kaynağı `netmovies:playback` event'i ile `VideoPlayer.js` yayınlıyor (~satır 2094).
- **PWA:** `stream/Public/Home/Static/manifest.webmanifest`, `_html_taban.html.j2` (meta), `main.js` (SW register), `sw.js` (no-op).
- **Basic auth:** `stream/Core/Modules/_auth.py` (env `AUTH_USER`/`AUTH_PASS`; proxy/health/static/manifest muaf). `Core/__init__.py`'de register.
- **4K donma:** `stream/Public/Proxy/Libs/segment_cache.py` (256MB, env `SEGMENT_CACHE_MB`), `video-utils.js` hls config (buffer 60s + retry).
- **DoH + hls.js fallback:** `docker-compose.yml` (`doh` servisi), `VideoPlayer.js` `loadHlsLibrary` (self-host → jsDelivr → cdnjs), `stream/Dockerfile` (hls.js build-time indirme).

Karar/kurulum dokümanları: `docs/MIMARI_SPEC.md`, `docs/ISKELET_SECIMI.md`, `docs/VENDOR.md`, `docs/KURULUM.md`, `docs/DEPLOY.md`.

---

## 4. Deploy modeli (kullanıcının kararı)
**Hibrit:** motor **evde** çalışır (residential IP — kaynaklar datacenter/Azure IP'sini engelliyor), `w.evaitec.com` **Cloudflare Tunnel** ile eve bağlanır. Auth: dean/1234 (`.env`, gitignored). Detay: `docs/DEPLOY.md`.

---

## 5. ⚠️ Test durumu / bilinen sınırlar
- **HDFilmCehennemi: CANLI DOĞRULANDI (Dean'in evinden).** Film oynuyor. Site player'ı her istekte yapısı değişen JS obfuscation'a (`dc_*` fonksiyonları) geçti; elle regex çözümü kırıldı. Çözüm: **V8 (py_mini_racer) ile sitenin kendi player JS'ini çalıştırıp `jwplayer().setup(cfg)` yakalama** → `engine/Plugins/_js_player.py` (rapidrame/CloseLoad ailesi için ortak, diğer kaynaklarda da kullanılacak).
- **DiziYou:** domain düzeltildi (upstream Kotlin `diziyou3.com` = ÖLÜ; artık `diziyou.one`). `__kekik_domain.discover_main_url` artık adayları **canlılık kontrolünden** geçiriyor. Selector'ların diziyou.one'da tuttuğu henüz uçtan uca doğrulanmadı.
- **RecTV: BLOKE.** Tüm `b.prectvNN.sbs` (38/39/40 + 41-60 taraması) ölü; güncel domain tahmin edilemiyor. Güncel domain bulununca `.env` `RECTV_URL` ile gir. API-only kaynak (kendi sitesi yok).
- **Egress/IP:** Kaynaklar datacenter IP'sini engelliyor (KANIT: watchbuddy.tv hosted API HDFC load_links'te 403; ev-engine başardı). **Azure ACA + AFD/DNS Zone bunu ÇÖZMEZ** — AFD sadece inbound; scraping outbound=Azure egress IP=bloklu. Engine residential (ev) egress'te kalmalı; inbound için CF Tunnel (veya AFD sadece inbound).
- **hls.js** kullanıcının makinesinde Dockerfile ile indi; ABR config iyi (480p başlar→1080p ramp, 60s buffer, capLevelToPlayerSize, nudge).

---

## 6. Sonraki adımlar / backlog

### Stratejik karar (Dean, 2026-08-24)
- **Kendi upstream'imizi biz yazıyoruz** — vendored fork'u yamamak yerine kaynakları tersine mühendislikle çözüp (V8 yaklaşımı) kendi Python plugin/extractor'larımızı maintain ediyoruz. Referans: Kekik-cloudstream + recloudstream/extensions (Kotlin). Gerekirse Kotlin de kendimiz yazarız (clone/rebase).
- **Client = PWA** (Flutter değil, şimdilik). Engine client-agnostik (MIMARI_SPEC ADR-1) — Flutter sonra opsiyonel.
- Yapı **modern/genişletilebilir** olsun; aşağıdaki roadmap sonradan eklenecek.

### Yakın backlog
1. ~~**Birleşik "Yeni Çıkanlar" UI (task 1):**~~ ✅ **YAPILDI** (commit `0440ff9`). `provider_client.get_aggregate_new` + `ana_sayfa.py` iki çağrıyı `asyncio.gather` ile paralel çekiyor, `admin_config.filter_aggregate_items` (gizli kaynak/kategori + puan eşiği) süzüyor, `home.html.j2` `yeni_rafi` makrosu iki yatay raf (carousel CSS reuse, JS'siz). Kaynak-bağımsız liste hedefi tamam. **Runtime doğrulaması Dean'in evinde** (engine residential IP + Py3.14 gerekir): `git pull && docker compose up -d --build` → localhost:3310. En az HDFilmCehennemi "Yeni Filmler" rafını doldurmalı.
2. **RecTV güncel domain** bul (bloke) → `RECTV_URL`.
3. **DiziYou uçtan uca** doğrula (diziyou.one selector'ları) + V8 extractor gerekiyorsa `_js_player`'a bağla.
4. **Daha çok kaynak port et** (recloudstream/extensions'tan): FullHDFilmizlesene (RapidVid ailesi — V8 ile hazır çözülür), JetFilmizle, Dizilla, SezonlukDizi...
5. **İzleme geçmişini SQLite'a taşı** (localStorage → cihazlar arası senkron; ADR-3) — öneri motorunun da temeli.

### Roadmap (Dean'in istediği "sonra" eklentileri)
- **Telefondan doğal-dil komut** ("şunu aç" → bulur/açar): telefon → **LiteLLM proxy** intent parse → engine `/search` → otomatik oynat. `POST /api/v1/command` AI gateway.
- **Spotify mantığı öneri** ("izlediklerime benzer"): izleme geçmişi (SQLite) + katalog → LiteLLM sıralama → `/api/v1/recommendations` + ana sayfa "Sana Özel" rafı.
- **Client-server telefon kontrolü / kendi watch-party** (watchbuddy söküldü; kendimizinki).
- InatBox premium, Font/FA self-host, masaüstü harici oynatıcı (.m3u/mpv).

---

## 7. Ortam notları
- engine **Python 3.14** ister (KekikStream 3.8.x). Yerel geliştirme: `uv venv --python 3.14 .venv && uv pip install -r requirements.txt`. (Eski `uv` 3.14 stabil bilmiyordu; `pip install -U uv` ile güncellendi.)
- `.env` gitignored (dean/1234 içinde, repoya girmez). `.env.example` commit'li (şifresiz).
- `basla.py` her açılışta JS/CSS minify eder (`*.min.js`/`*.min.css`, gitignored).
- Bu sandbox'ta foreground `sleep` bloklu; servisleri `run_in_background` veya `nohup` + `curl --retry` ile test et. `pkill -f basla.py` shell'in kendini de öldürebilir — dikkat.

---

## 8. Commit geçmişi (özet)
`docs/` specler → vendor+dereklam → engine plugin altyapısı (HDFilmCehennemi) → hibrit provider + M3U → sağlık kontrolü → admin panel + 4K → harici player + PWA → RecTV → auth + hibrit deploy → DoH + hls.js self-host. Hepsi `claude/stream-app-architecture-86q0sg` dalında, PR #3.
