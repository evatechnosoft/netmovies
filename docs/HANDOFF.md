# NetMovies — Oturum Devri (HANDOFF)

> Bu dosya, projeyi başka bir oturumda (Claude Code / web) kaldığı yerden sürdürmek
> içindir. Yeni oturumda: **"docs/HANDOFF.md oku ve devam et"** demen yeterli.

**Repo:** `evatechnosoft/netmovies`
**Aktif dal:** `claude/stream-app-architecture-86q0sg`  (TÜM iş burada — master ESKİ)
**PR #3:** MERGED (tarihte). Dal ondan sonra ana hat olarak devam etti → **bu dal tek kaynak, master ESKİ** (42+ commit ileride). Herkes bu daldan `git pull` yapar.
**Son Release (TV client, POC):** `v0.1.20-poc` — https://github.com/evatechnosoft/netmovies/releases/tag/v0.1.20-poc
**APK (indir):** https://github.com/evatechnosoft/netmovies/releases/download/v0.1.20-poc/netmovies-tv-v0.1.20-poc.apk
**Ev sunucusu = bu Windows PC** (Docker Desktop). `.env` (gitignored) güncel: `DIZIMOM_URL=dizimom.food · DIZILLA_URL=dizilla.club · AUTO_DISCOVER_DOMAINS=1 · ENGINE_WORKERS=1 · CF_TUNNEL_TOKEN dolu`. WARP servisi compose'da.
> **EV ÇÖZÜMÜ (asıl):** Windows'ta **3310 inbound firewall kuralı AÇILDI** ("NetMovies 3310"). TV aynı WiFi'da (192.168.1.x) → app yerel sunucuya (192.168.1.185:3310) düşer, **Cloudflare hiç devreye girmez** → CF-TR IP blokları (188.114.x) sorunu evde biter. TV'de app'i kapat-aç (veya "Tekrar dene") ile yerele geçer.
> v0.1.10: OkHttp timeout (connect6/read45/call50s → sonsuz "Yükleniyor" biter) + CF IP-pin (w.evaitec.com → 172.67.143.235/104.21.55.13, TR bloklu 188.114 baypas — uzaktayken).
> v0.1.9: Dokunmatik desteği — tv-material3 Card/Button D-pad odaklıydı, telefonda basılamıyordu → foundation `clickable` (`ui/TouchButton.kt`, PosterCard Box+clickable). Telefon+TV her ikisi.
> v0.1.8: "önce local, olmazsa uzak" — Cloudflare TR'de cihaza bloklu IP (188.114.x) döndürüyordu. `ServerResolver` önce `LOCAL_URL` (192.168.1.185:3310, /api/v1/health 1.5s probe) dener, ulaşamazsa `BASE_URL` (w.evaitec.com). `BaseUrlInterceptor` tüm istekleri + posterleri aktif sunucuya yönlendirir. NOT: yerel yol için Windows'ta **3310 inbound firewall izni** gerekebilir (`New-NetFirewallRule -DisplayName "NetMovies 3310" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 3310`).
> v0.1.7: IPv6 fix — `PreferIpv4Dns` (içerik+OTA+indirme). UI fix (kontrast/focus) ekran görüntüsüyle DOĞRULANDI. OTA ile dağıtım.
> v0.1.6: OTA "İndir" fix — TV'de tarayıcı yok, ACTION_VIEW(url) çalışmıyordu → APK uygulama-içi OkHttp ile indirilip FileProvider ile kuruluyor. Eski buggy sürüm OTA edemez → v0.1.6 bir kez elle kuruldu, sonrası OTA.
> v0.1.5: UI fix — siyah-üstüne-siyah yazı (tv-material3 LocalContentColor=#EDEDF2) + D-pad ilk kart initial focus. (kod fix, ekranda DOĞRULANMADI — kullanıcı testinde)
> v0.1.4: BASE_URL `https://w.evaitec.com` (v0.1.3 yanlışlıkla 192.168 ile derlenmişti). Debug-imzalı → ilk geçişte önce kaldır; sonraki OTA'lar aynı keystore ile sorunsuz.
**Kullanıcı:** Dean (deancjx@gmail.com) — Türkçe konuşuyor. Kişisel, reklamsız kullanım.

---

## 0. SON OTURUM — 2026-08-30 — TV/Web UX + Kaynak kurtarma + WARP egress (CANLI, DOĞRULANDI)

**Durum: Docker'da uçtan uca doğrulandı. w.evaitec.com ✅. Android client v0.1.20 OTA.**
Dıştan (telefon yolu) tutarlı: **movie 20 · serie 68 · serie_foreign 10 · live 0** (2x aynı).

### Android client (client-tv) — v0.1.11 → v0.1.20 (hepsi OTA, prerelease)
- **Poster büyüteç** (focus scale+glow), **çark menüsü** (Kaynak/Dil/Altyazı/Hız), **10sn seek**, **altyazı sideload**, **çoklu kaynak**.
- **Buton-eşleme sistemi** (`input/RemoteInput.kt`, `ui/KeyMapScreen.kt`): her tuş×basış → aksiyon, SharedPreferences. Oynatıcı `useController=false` tam input sahipliği. → memory `input-mapping-architecture`.
- **Scrub önizleme (thumbnail)**: ikinci ExoPlayer düşük kalite kare (`ScrubOverlay`).
- **Dokunmatik oynatıcı kontrolleri** (telefon): videoya dokun→kontroller, tıklanabilir seekbar, `TouchTapButton` (pointerInput, D-pad'i bozmaz). Emoji kaldırıldı (sarı görünüyordu) → sade metin.
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

### Bu oturum commit'leri (dalda, push edildi)
client-tv: `c403710 8e18a30 054e9da 0525815 bad97c0 b139e9b ed6b1c8 f92ccaf c3d987a fcbe69b` · infra/engine: `acf8a29 0d66096` (WARP) + Dizilla commit. Memory: `input-mapping-architecture`, `plugin-domain-moves`.

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
