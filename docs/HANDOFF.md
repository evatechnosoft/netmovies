# NetMovies — Oturum Devri (HANDOFF)

> Bu dosya, projeyi başka bir oturumda (Claude Code / web) kaldığı yerden sürdürmek
> içindir. Yeni oturumda: **"docs/HANDOFF.md oku ve devam et"** demen yeterli.

**Repo:** `evatechnosoft/netmovies`
**Aktif dal:** `claude/stream-app-architecture-86q0sg`  (TÜM iş burada — master ESKİ)
**Açık PR:** #3 → https://github.com/evatechnosoft/netmovies/pull/3 (base: master, henüz merge edilmedi)
**Kullanıcı:** Dean (deancjx@gmail.com) — Türkçe konuşuyor. Kişisel, reklamsız kullanım.

---

## 1. Proje nedir?
Reklamsız, kişisel, "tıkla-izle" odaklı **film / dizi / canlı TV** uygulaması. İki servis + iki yardımcı:
- **engine/** — KekikStream 3.8.x (Python **3.14**) sağlayıcı API. Kendi eklentilerimiz `engine/Plugins/`.
- **stream/** — Web arayüzü + header-enjekteli video/altyazı **proxy** + API gateway. (WatchBuddy-tv/Stream fork'u vendor'landı, reklamları söküldü.)
- **doh** — DNS-over-HTTPS resolver (ISP DNS engelini aşar), docker-compose servisi.
- **cloudflared** — `w.evaitec.com` → eve tünel (profile: tunnel).

Tek komut: `docker compose up -d --build` → `http://localhost:3310` (auth: **Dean / 1234**).

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
**Hibrit:** motor **evde** çalışır (residential IP — kaynaklar datacenter/Azure IP'sini engelliyor), `w.evaitec.com` **Cloudflare Tunnel** ile eve bağlanır. Auth: Dean/1234 (`.env`, gitignored). Detay: `docs/DEPLOY.md`.

---

## 5. ⚠️ Test edilmemiş / bilinen sınırlar
- **Gerçek içerik akışı (canlı arama/oynatma) HİÇ doğrulanmadı.** Geliştirme ortamından hedef siteler egress-bloklu. Kod/parse/endpoint/admin/PWA/auth doğrulandı; **gerçek izleme kullanıcının makinesinde test edilecek.**
- İlk çalıştırmada `/admin` sağlık panelinden kaynak durumuna bak. Kırmızıysa: RecTV → `RECTV_URL` güncelle; diğerleri → domain/selector eskimişse plugin güncelle (Kekik-cloudstream'e bak).
- **hls.js** bu ortamda indirilemedi (proxy CDN'leri blokluyor); kullanıcının makinesinde Dockerfile indirir veya çalışma anında fallback.

---

## 6. Sonraki adımlar / backlog (öncelik sırasız)
1. **Gerçek test:** kullanıcı `docker compose up` sonrası her kaynağı canlı dener; kırılan selector/extractor düzeltilir.
2. **InatBox premium** (TOD, EXXEN, Netflix, Disney+, BluTV, HBO Max, Tabii) — kullanıcı şimdilik "yeterli" dedi. Karmaşık: 4-5 şifreli extractor (CDNJWPlayer, Dzen, Vk, DiskYandex + `pichive.online` gibi dinamik CDN). Yüksek bakım.
3. **SezonlukDizi** — "Son Bölümler" akışı güçlü ama loadLinks AJAX + genel extractor zinciri gerektiriyor.
4. **Font Awesome + Google Fonts self-host** (şu an cdnjs/google'dan; engellenirse UI görselleri bozulur, işlev değil).
5. **İzleme geçmişini SQLite'a taşıma** (şu an localStorage — cihazlar arası senkron yok). `docs/MIMARI_SPEC.md` ADR-3.
6. **Masaüstü harici oynatıcı** (.m3u dosyası / mpv komutu) — şu an harici player Android intent odaklı.

---

## 7. Ortam notları
- engine **Python 3.14** ister (KekikStream 3.8.x). Yerel geliştirme: `uv venv --python 3.14 .venv && uv pip install -r requirements.txt`. (Eski `uv` 3.14 stabil bilmiyordu; `pip install -U uv` ile güncellendi.)
- `.env` gitignored (Dean/1234 içinde, repoya girmez). `.env.example` commit'li (şifresiz).
- `basla.py` her açılışta JS/CSS minify eder (`*.min.js`/`*.min.css`, gitignored).
- Bu sandbox'ta foreground `sleep` bloklu; servisleri `run_in_background` veya `nohup` + `curl --retry` ile test et. `pkill -f basla.py` shell'in kendini de öldürebilir — dikkat.

---

## 8. Commit geçmişi (özet)
`docs/` specler → vendor+dereklam → engine plugin altyapısı (HDFilmCehennemi) → hibrit provider + M3U → sağlık kontrolü → admin panel + 4K → harici player + PWA → RecTV → auth + hibrit deploy → DoH + hls.js self-host. Hepsi `claude/stream-app-architecture-86q0sg` dalında, PR #3.
