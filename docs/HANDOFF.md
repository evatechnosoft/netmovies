# NetMovies — Oturum Devri (HANDOFF)

> Bu dosya, projeyi başka bir oturumda (Claude Code / web) kaldığı yerden sürdürmek
> içindir. Yeni oturumda: **"docs/HANDOFF.md oku ve devam et"** demen yeterli.

**Repo:** `evatechnosoft/netmovies`
**Aktif dal:** `claude/stream-app-architecture-86q0sg`  (TÜM iş burada — master ESKİ)
**Açık PR:** #3 → https://github.com/evatechnosoft/netmovies/pull/3 (base: master, henüz merge edilmedi)
**Kullanıcı:** Dean (deancjx@gmail.com) — Türkçe konuşuyor. Kişisel, reklamsız kullanım.

---

## 0. SON OTURUM — 2026-08-27 (TV/kumanda UX + sinema player + yeni kaynaklar + local deploy)

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
- **watchmedo dev-override Windows'ta ÇALIŞMIYOR** → startup takıldı. Stream **base compose** ile kaldırıldı:
  `docker compose -f docker-compose.yml up -d --force-recreate stream` (doğrudan `basla.py`).
  Kaynak değişince yeniden derle: `docker compose -f docker-compose.yml up -d --build --force-recreate stream`.
- **Tünel:** stream recreate → `w.evaitec.com` düşmüş olabilir. Geri: `docker compose --profile tunnel up -d --force-recreate cloudflared`.
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
