# NetMovies — Kişisel Reklamsız İzleme Uygulaması: Mimari Spec & Yol Haritası

> **Durum:** Taslak v1.0 — 2026-08-23
> **Kapsam:** Tek kullanıcılık (self-hosted), reklamsız, "tıkla-izle" odaklı film/dizi uygulaması.
> **Kaynaklar:** KekikStream sağlayıcıları (Dizimom, Filmizle vb.) + kullanıcı M3U/M3U8 listeleri.

---

## 1. Vizyon ve Tasarım İlkeleri

| İlke | Anlamı |
|---|---|
| **Tıkla-izle** | Liste → tek dokunuş → oynatıcı. Ağır metadata/vitrin ekranı yok. |
| **Sıfır reklam** | Kaynak sitelerin HTML'i hiç render edilmez; yalnızca çözülmüş stream URL'i kullanılır. |
| **Liste-öncelikli UI** | Poster ızgarası opsiyonel; asıl arayüz filtrelenebilir/sıralanabilir listedir. |
| **Sürdürülebilirlik** | Extractor bakımı bize ait değil: `pip install -U KekikStream` ile domain değişimleri merkezi çözülür. |
| **Hızlı çıkış (time-to-first-play)** | Önce web/PWA; Flutter istemcisi ancak ihtiyaç doğarsa sonraki faz. |

---

## 2. Mevcut Durum Analizi

### 2.1 Bu repo (`netmovies`)
Next.js 15 tabanlı, mock veriyle çalışan basit bir film vitrini şablonu (`app/`, `components/`, `mocks/`). Gerçek veri kaynağı ve oynatıcı yok. **Karar: çöpe atmıyoruz** — App Router iskeleti, kategori/liste bileşenleri ve tema yeni frontend'in temeli olacak; mocklar yerine FastAPI backend'e bağlanacak.

### 2.2 Gemini taslağının değerlendirmesi
Genel yön (FastAPI + KekikStream backend, hafif istemci) **doğru**, ancak kod üretimden önce düzeltilmesi zorunlu hatalar içeriyor — bkz. §11. En kritiği: **KekikStream'in gerçek API'si tamamen async ve plugin tabanlıdır**; `KekikStream().search()` gibi senkron tek-nesne API'si yoktur.

### 2.3 KekikStream ekosistemi (doğrulanmış, Ağustos 2026)
- **KekikStream** (PyPI `3.8.4`, **Python ≥ 3.14**): 20+ plugin (site başına bir plugin), 40+ extractor. Plugin sözleşmesi: `get_main_page(page, url, category)`, `search(query)`, `load_item(url) → MovieInfo | SeriesInfo`, `load_links(url) → ExtractResult[]` — hepsi `async`.
- **KekikStreamAPI** (ayrı repo): KekikStream'i saran hazır FastAPI servisi. Endpoint'ler: `/api/v1/search`, `/api/v1/get_main_page`, `/api/v1/load_item`, `/api/v1/load_links`, `/api/v1/get_plugin_names`, `/api/v1/ytdlp-extract`. Dockerfile'ı var, `:3310`'da çalışır.

---

## 3. Mimari Karar Kayıtları (ADR)

| # | Karar | Seçenekler | Karar & Gerekçe |
|---|---|---|---|
| ADR-1 | İstemci platformu | (a) Flutter + media_kit, (b) Web/PWA | **(b) Web/PWA (Next.js).** Hızlı çıkış hedefi + repo zaten Next.js. Flutter/media_kit güçlü bir oynatıcı sunar ama her platformda derleme/CI maliyeti getirir. Android'de "rahat oynatıcı" ihtiyacını harici oynatıcı (VLC/MX/mpv intent) karşılar. Flutter, Faz 4'te opsiyonel TV/mobil istemci olarak masada kalır. |
| ADR-2 | Extractor servisi | (a) KekikStreamAPI'yi aynen çalıştır, (b) kendi FastAPI'mizde KekikStream'i kütüphane olarak kullan | **(b) Kendi ince FastAPI servisimiz.** Ek ihtiyaçlarımız var (M3U ayrıştırma, izleme geçmişi/DB, header enjekte eden stream proxy, sıralama). İki ayrı Python servisi taşımak yerine tek "engine" servisinde KekikStream'i import ederiz. Extractor güncellemeleri yine `pip -U` ile gelir. KekikStreamAPI, endpoint tasarımı için **referans sözleşme** ve acil durumda hazır yedek. |
| ADR-3 | İzleme geçmişi deposu | (a) localStorage, (b) backend SQLite | **(b) SQLite (backend).** Tek kullanıcı ama çok cihaz (TV, telefon, PC) — "kaldığın bölüm" cihazlar arası senkron olmalı. localStorage yalnızca çevrimdışı önbellek/fallback. |
| ADR-4 | Tarayıcıda oynatma | (a) stream URL'ini doğrudan `<video>`'ya ver, (b) backend üzerinden proxy | **(b) Proxy zorunlu.** Kaynakların çoğu `Referer`/`User-Agent` header'ı ister ve CORS engeli vardır; tarayıcı `<video>`/hls.js bu header'ları set edemez. Backend `/proxy` endpoint'i header enjekte edip akışı stream eder. Harici oynatıcılar proxy'siz, doğrudan URL + header ile çalışır. |
| ADR-5 | Monorepo düzeni | (a) ayrı repolar, (b) tek repo `web/` + `engine/` | **(b) Tek repo.** Tek geliştirici, tek deploy hedefi; sözleşme değişiklikleri atomik commit olur. |

---

## 4. Hedef Mimari

```
┌────────────────────────────────────────────────────────────────────┐
│  İSTEMCİLER                                                        │
│  PC tarayıcı · Android (PWA) · TV tarayıcı                         │
│         │  liste/arama JSON        │ oynatma                       │
│         ▼                          ▼                               │
│  ┌─────────────────┐   ┌───────────────────────────────┐           │
│  │ Next.js 15 PWA  │   │ Oynatıcı katmanı (3 kademe)   │           │
│  │ (bu repo, web/) │   │ 1. hls.js + /proxy (in-app)   │           │
│  └────────┬────────┘   │ 2. Android intent (VLC/MX/mpv)│           │
│           │            │ 3. Masaüstü: tek öğeli .m3u   │           │
│           │            │    (EXTVLCOPT header'lı)      │           │
│           │            └───────────────────────────────┘           │
└───────────┼────────────────────────────────────────────────────────┘
            ▼  REST /api/v1
┌────────────────────────────────────────────────────────────────────┐
│  ENGINE — FastAPI (engine/)                    Python 3.14         │
│                                                                    │
│  routers/    search · catalog · item · links · playlist ·          │
│              progress · proxy · playerfile                         │
│  services/   kekik_service (PluginManager sarmalayıcı)             │
│              m3u_service (ayrıştırma + normalizasyon)              │
│              progress_service (devam etme/favori)                  │
│  db/         SQLite (SQLModel + aiosqlite)                         │
│              media_cache · watch_progress · favorites · playlists  │
│                                                                    │
│  pip bağımlılığı: KekikStream ≥ 3.8  ◄── domain değişimleri        │
│  (Dizimom, Filmizle… pluginleri + Vidmoly, Filemoon… extractorlar) │
└────────────────────────────────────────────────────────────────────┘
```

### 4.1 Dizin yapısı (hedef)

```
netmovies/
├── web/                          # Next.js 15 PWA (mevcut kök buraya taşınır)
│   ├── app/                      # App Router
│   │   ├── (browse)/page.tsx     # Ana liste (katalog + filtre + sıralama)
│   │   ├── search/page.tsx
│   │   ├── item/[provider]/[id]/ # Detay: sezon/bölüm listesi
│   │   ├── watch/                # In-app oynatıcı (hls.js)
│   │   └── manifest.ts           # PWA manifest
│   ├── components/               # mevcut bileşenler dönüştürülür
│   ├── lib/
│   │   ├── api.ts                # engine istemcisi (typed fetch)
│   │   ├── player-launcher.ts    # intent:// · .m3u indirme · in-app yönlendirme
│   │   └── stores/               # Zustand: ui tercihleri, seçili oynatıcı
│   └── package.json
│
├── engine/                       # FastAPI + KekikStream
│   ├── app/
│   │   ├── main.py
│   │   ├── core/config.py        # pydantic-settings
│   │   ├── routers/              # search.py, catalog.py, item.py, links.py,
│   │   │                         # playlist.py, progress.py, proxy.py, playerfile.py
│   │   ├── services/             # kekik_service.py, m3u_service.py, progress_service.py
│   │   ├── models/               # pydantic şemaları (API sözleşmesi)
│   │   └── db/                   # SQLModel tabloları + session
│   ├── pyproject.toml            # uv ile yönetim
│   └── tests/
│
├── docker-compose.yml            # web + engine tek komutla
└── docs/MIMARI_SPEC.md           # bu doküman
```

---

## 5. Engine (Backend) Tasarımı

### 5.1 API sözleşmesi

Tüm cevaplar `{ "ok": true, "data": ... }` zarfında; hata: `{ "ok": false, "error": {code, message} }`.

| Method | Endpoint | Amaç |
|---|---|---|
| GET | `/api/v1/providers` | Aktif KekikStream plugin listesi (Dizimom, Filmizle…) + M3U kaynakları. Frontend'in filtre çipleri buradan gelir. |
| GET | `/api/v1/search?q=&providers=a,b` | Seçili sağlayıcılarda paralel arama (`asyncio.gather`), sağlayıcı etiketli birleşik sonuç. |
| GET | `/api/v1/catalog?provider=&category=&page=` | Plugin `get_main_page` → "Yeni Bölümler", "Yeni Filmler" gibi site kategorileri. **"Yeni gelenler" ekranının kaynağı budur.** |
| GET | `/api/v1/item?provider=&url=` | `load_item` → `MovieInfo` veya `SeriesInfo` (sezonlar/bölümler dahil). |
| GET | `/api/v1/links?provider=&url=` | `load_links` → oynatılabilir `ExtractResult[]`: `{name, url, referer, headers, subtitles[]}`. |
| POST | `/api/v1/playlist` | M3U içeriği/URL'i kaydet-ayrıştır (DB'ye kalıcı). |
| GET | `/api/v1/playlist/items?playlist_id=&group=&sort=` | Ayrıştırılmış M3U öğeleri; grup/sıralama parametreleri. |
| GET/PUT | `/api/v1/progress` | Devam etme durumu: `{media_key, season, episode, position_sec, duration_sec, updated_at}`. |
| GET/PUT/DELETE | `/api/v1/favorites` | Favori içerik/diziler. |
| GET | `/api/v1/proxy?token=` | **Stream proxy:** imzalı token → orijinal URL + header; `httpx.AsyncClient.stream()` ile chunk aktarımı, `Range` desteği. HLS manifest'lerinde segment URL'lerini de proxy'ye yeniden yazar. |
| GET | `/api/v1/playerfile?token=` | Masaüstü için tek öğeli `.m3u` üretir (bkz. §7.3). |

> Güvenlik: `/proxy` açık relay olmasın diye URL asla düz parametre alınmaz; `/links` cevabında sunucunun HMAC ile imzaladığı kısa ömürlü `token` döner.

### 5.2 KekikStream entegrasyonu (gerçek API'ye göre)

```python
# engine/app/services/kekik_service.py  (eskiz — sınıf/metot adları 3.8.x'te doğrulanacak)
import asyncio
from KekikStream.Core import PluginManager  # plugin yükleyici

class KekikService:
    def __init__(self):
        self.manager = PluginManager()
        self.plugins = {name: self.manager.select_plugin(name)
                        for name in self.manager.get_plugin_names()}

    async def search(self, query: str, providers: list[str] | None = None):
        targets = [p for n, p in self.plugins.items()
                   if not providers or n in providers]
        batches = await asyncio.gather(
            *(p.search(query) for p in targets), return_exceptions=True
        )
        # Hata veren plugin tüm aramayı düşürmez; sonuçlar provider etiketiyle birleşir
        ...

    async def load_item(self, provider: str, url: str):
        return await self.plugins[provider].load_item(url)   # MovieInfo | SeriesInfo

    async def load_links(self, provider: str, url: str):
        return await self.plugins[provider].load_links(url)  # ExtractResult[]
```

Notlar:
- Plugin nesneleri **uygulama ömrü boyunca tek instance** (startup'ta yüklenir, `lifespan` içinde kapatılır).
- Her plugin çağrısına `asyncio.timeout(15)` + sonuçlara kısa süreli in-memory cache (`aiocache`/TTL dict) — aynı diziye art arda girişte siteyi dövmemek için.
- Arama sonuçları `media_cache` tablosuna yazılır → geçmiş/favori kayıtları başlık+poster'ı offline gösterebilir.

### 5.3 M3U ayrıştırıcı — düzeltilmiş yaklaşım

Gemini'nin tek-regex yaklaşımı attribute **sıra bağımlı** (group-title'ı logo'dan sonra yazan listelerde patlar). Doğrusu iki aşama:

```python
_ATTR = re.compile(r'([\w-]+)="([^"]*)"')          # tüm key="value" çiftleri, sıradan bağımsız

def parse_extinf(line: str) -> dict:
    attrs = dict(_ATTR.findall(line))
    title = line.rsplit(",", 1)[-1].strip()        # son virgülden sonrası görünen ad
    return {
        "title": title,
        "group": attrs.get("group-title", "Genel"),
        "logo":  attrs.get("tvg-logo", ""),
        "tvg_id": attrs.get("tvg-id", ""),
    }
```

Ek olarak `#EXTVLCOPT:` ve `#EXTHTTP:` satırları okunur (bazı listeler header'ı böyle taşır) ve öğeye `headers` olarak bağlanır.

### 5.4 Bölüm meta & sıralama

`S02E05`, `2. Sezon 5. Bölüm`, `2x05` kalıpları tek normalize ediciden geçer. Sıralama modları (API'de `sort=` enum):

- `latest_episode` — en yeni bölüm üstte (devam eden diziler)
- `first_episode` — S01E01 üstte (yeni başlanacaklar)
- `title` — A-Z
- `recently_watched` — `watch_progress.updated_at` DESC ("kaldığım yerden devam" rafı)
- `release` — katalog/`get_main_page` sırası (site zaten yayın tarihine göre verir; M3U'da dosya sırası)

### 5.5 Veritabanı şeması (SQLite)

```sql
media_cache    (media_key PK, provider, url, title, poster, type, extra_json, seen_at)
watch_progress (media_key PK→media_cache, season, episode, position_sec, duration_sec, updated_at)
favorites      (media_key PK→media_cache, added_at)
playlists      (id PK, name, source_url NULL, added_at)
playlist_items (id PK, playlist_id FK, title, group_name, logo, stream_url, headers_json, season, episode)
```

`media_key = sha1(provider + ":" + url)` — sağlayıcı URL değiştirirse eski kayıt düşer, kabul edilmiş sınırlama (başlık-bazlı eşleştirme Faz 3 backlog).

---

## 6. Web (Frontend) Tasarımı

### 6.1 Ekranlar

1. **Katalog (ana ekran):** Sağlayıcı çipleri (Dizimom, Filmizle, M3U listeleri…) + kategori sekmesi + sıralama. Üstte iki yatay raf: **"Devam Et"** (progress'ten) ve **"Yeni Bölümler"** (catalog endpoint'inden). Altta sade dikey liste: başlık, SxxEyy rozeti, sağlayıcı, "Kaldığın bölüm" etiketi, tek **Oynat** butonu.
2. **Arama:** debounce'lu input, sağlayıcı-etiketli birleşik sonuç listesi.
3. **Detay:** dizi ise sezon akordeonu + bölüm listesi (izlenenler işaretli, sıradaki bölüm vurgulu); film ise doğrudan kaynak listesi.
4. **Oynat sayfası (in-app):** hls.js + `/proxy`; süre konumu 10 sn'de bir `PUT /progress`.
5. **Ayarlar:** varsayılan oynatıcı (in-app / VLC / MX / mpv), M3U kaynak yönetimi, engine adresi.

### 6.2 Teknik seçimler

| Konu | Seçim | Not |
|---|---|---|
| Framework | **Next.js 15+ (App Router)** | Repo mevcudu; `next.config` içinde engine'e rewrite → CORS derdi kalmaz. |
| Dil | **TypeScript'e geçiş** | Mevcut `.jsx` dosyaları taşınırken dönüştürülür; API sözleşmesi tipli olur. |
| Veri katmanı | **TanStack Query v5** | Cache, retry, `staleTime`; arama/katalog için ideal. |
| UI durumu | **Zustand** (persist) | Seçili oynatıcı, filtre tercihleri. |
| Stil | **Tailwind CSS v4** | Mevcut CSS modülleri kademeli taşınır. |
| PWA | **Serwist** (next-pwa halefi) | App-shell offline; manifest + install prompt. Video önbelleklenmez. |
| Oynatıcı | **hls.js** + ince özel kontrol | Vidstack/artplayer gibi tam paket gerekirse Faz 2'de değerlendirilir; MVP'de bağımlılığı küçük tut. |

---

## 7. Oynatıcı Stratejisi (3 kademe)

Kaynakların çoğu `Referer` + `User-Agent` ister; her kademenin header taşıma yolu farklıdır:

### 7.1 In-app (tarayıcı)
`hls.js` → `/api/v1/proxy?token=…`. Header'ları proxy enjekte eder. Konum takibi (saniye bazlı devam etme) **yalnızca bu modda** mümkündür — diğer modlarda bölüm bazlı takip yapılır.

### 7.2 Android — intent URL'leri (PWA içinden)
```
// VLC (header destekli değil; çoğu kaynakta yine çalışır, gerekirse proxy URL'i verilir)
intent:<URL>#Intent;package=org.videolan.vlc;type=video/*;end

// MX Player (header'ları extra olarak taşıyabilir)
intent:<URL>#Intent;package=com.mxtech.videoplayer.ad;type=video/*;
  S.title=<başlık>;S.headers=Referer:<ref>&User-Agent:<ua>;end

// mpv-android
intent:<URL>#Intent;package=is.xyz.mpv;type=video/*;end
```
Kademeli düşüş: seçili paket yoksa `S.browser_fallback_url` ile chooser'a düşer.

### 7.3 Masaüstü (PC) — `vlc://` **kullanılmaz**
`vlc://` şeması masaüstü VLC'de kayıtlı değildir (Gemini taslağındaki hata). Bunun yerine `/api/v1/playerfile` tek öğeli bir `.m3u` indirir; VLC/MPV dosya ilişkilendirmesiyle açılır ve header'lar dosyanın içinde taşınır:
```m3u
#EXTM3U
#EXTINF:-1,Dizi Adı S02E05
#EXTVLCOPT:http-referrer=https://kaynak-site.example
#EXTVLCOPT:http-user-agent=Mozilla/5.0 ...
https://cdn.example/stream.m3u8
```
Alternatif olarak "URL'yi kopyala" butonu + `mpv --http-header-fields=...` komutunu panoya kopyalama.

---

## 8. Paket Listesi (Ağustos 2026 itibarıyla sağlam sürümler)

**engine/pyproject.toml** — Python **3.14** (KekikStream 3.8.x zorunluluğu), yönetim: **uv**
```toml
dependencies = [
  "fastapi>=0.116",
  "uvicorn[standard]>=0.30",
  "KekikStream>=3.8",
  "httpx>=0.27",
  "sqlmodel>=0.0.22",
  "aiosqlite>=0.20",
  "pydantic-settings>=2.4",
]
[dependency-groups]
dev = ["pytest>=8", "pytest-asyncio", "respx", "ruff"]
```

**web/package.json (ekler)**
```jsonc
{
  "dependencies": {
    "next": "^15",                      // mevcut
    "@tanstack/react-query": "^5",
    "zustand": "^5",
    "hls.js": "^1.5"
  },
  "devDependencies": {
    "typescript": "^5",
    "tailwindcss": "^4",
    "@serwist/next": "^9"
  }
}
```

---

## 9. Fazlı Yol Haritası

### Faz 0 — Zemin (½ gün)
- Kök Next.js dosyalarını `web/`'e taşı; `engine/` iskeletini kur (uv + FastAPI + healthcheck).
- `docker-compose.yml` (web:3000, engine:8000), Next rewrite `/api/v1/* → engine`.
- **Çıkış kriteri:** `docker compose up` ile iki servis ayakta, `/api/v1/health` yeşil.

### Faz 1 — MVP: "Ara → listele → harici oynatıcıda izle" (2-3 gün)
- `kekik_service` + `/providers`, `/search`, `/item`, `/links` (KekikStream API yüzeyi burada doğrulanır).
- M3U yükleme + kalıcı saklama + grup/sıralama.
- Katalog + arama + detay ekranları (mevcut bileşenler dönüştürülür).
- Android intent + masaüstü `.m3u` playerfile; bölüm-bazlı izleme kaydı (`PUT /progress`).
- **Çıkış kriteri:** Telefonda PWA'dan Dizimom'da dizi ara → bölüm seç → MX Player'da reklamsız izle; ertesi gün "Devam Et" rafında sıradaki bölüm görünsün.

### Faz 2 — In-app oynatıcı & konum takibi (2 gün)
- `/proxy` (HMAC token, Range, HLS manifest yeniden yazımı) + hls.js oynatma sayfası.
- Saniye-bazlı devam etme, altyazı seçimi (ExtractResult.subtitles).
- Favoriler + "Yeni Bölümler" rafı (`/catalog`).

### Faz 3 — Cila (sürekli)
- Serwist PWA offline shell, TV tarayıcısı için d-pad navigasyonu.
- Başlık-bazlı media_key eşleştirme, TMDB poster zenginleştirme (opsiyonel).
- Yeni bölüm bildirimi (catalog diff → PWA badge).

### Faz 4 — Opsiyonel Flutter/media_kit istemcisi
Yalnızca web player TV/Android'de yetersiz kalırsa. Engine API'si aynen kullanılır — bu yüzden sözleşme (§5.1) istemci-bağımsız tasarlandı.

---

## 10. Riskler ve Önlemler

| Risk | Etki | Önlem |
|---|---|---|
| Python 3.14 zorunluluğu (KekikStream 3.8) | Deploy ortamı kısıtı | `python:3.14-slim` Docker imajı; engine her yerde konteynerde koşar. |
| KekikStream iç API'sinin sürümler arası değişmesi | `kekik_service` kırılır | Tüm KekikStream erişimi tek servis sınıfının arkasında; sürüm pinle, güncellemeyi smoke testle. |
| Sağlayıcı sitelerin çökmesi/yavaşlığı | Arama takılır | Plugin başına timeout + `return_exceptions=True`; UI'da sağlayıcı bazlı kısmi sonuç göstergesi. |
| Proxy'nin açık relay'e dönmesi | Kötüye kullanım | HMAC imzalı, süreli token; yalnızca `/links`'in ürettiği URL'ler proxylenir. |
| Hukuki/İçerik | — | Kişisel kullanım, tek kullanıcı, self-hosted; uygulama herkese açık yayınlanmaz, kimlik doğrulamasız internete açılmaz (en azından basic auth). |

---

## 11. Gemini Taslağındaki Somut Hatalar (düzeltme referansı)

1. **`KekikStream().search()` / `.load_links()` yok.** Kütüphane async + plugin tabanlı; `PluginManager` üzerinden plugin seçilip `await plugin.search(...)` çağrılır (§5.2). Ayrıca `load_links` tek dict değil, `ExtractResult` listesi döner (bir bölümün birden çok kaynağı olur — UI kaynak seçtirmeli).
2. **`KekikStream>=1.0.0` pini geçersiz** — güncel sürüm 3.8.x ve Python ≥3.14 ister.
3. **EXTINF regex'i sıra-bağımlı ve içinde literal satır sonu var** (`,(?P<title>[^\n]+)` bozuk yazılmış). Attribute'lar sırasız `key="value"` taramasıyla alınmalı (§5.3).
4. **`vlc://` ve `mpv://` masaüstünde kayıtlı protokol değil** — çalışmaz. Masaüstü çözümü `.m3u` playerfile'dır (§7.3). `intent:` yalnızca Android'de geçerlidir.
5. **Header gerektiren akışlar tarayıcıda doğrudan oynamaz** (taslakta in-app oynatma hiç çözülmemiş) — proxy katmanı şart (ADR-4).
6. **İzleme geçmişi localStorage'da** — cihazlar arası senkron olmaz; backend SQLite'a alındı (ADR-3).
7. **M3U listesi bellekte global değişkende** (`in_memory_playlist`) — restart'ta kaybolur, kalıcı DB'ye alındı.
8. **`allow_origins=["*"]` CORS** — Next rewrite/proxy kullanınca gereksiz; kalacaksa bile origin kısıtlı olmalı.
9. Frontend'de ayrı Vite SPA öneriliyordu — mevcut Next.js repo'su varken ikinci bir frontend iskeleti gereksiz (ADR-1/5).

---

## 12. Açık Sorular (kullanıcıyla netleşecek)

1. Engine nerede koşacak — evde sürekli açık bir makine/NAS var mı, yoksa ucuz bir VPS mi? (PWA'nın telefonda çalışması için engine'e ağdan erişim gerekir; Tailscale önerilir.)
2. MVP'de in-app oynatıcı beklenmeli mi, yoksa Faz 1 yalnızca harici oynatıcıyla mı çıksın? (Öneri: harici ile çık, Faz 2'de in-app.)
3. TMDB ile poster/metadata zenginleştirme istenir mi? ("Görsel yüklü olmasın" ilkesiyle çelişmiyor; yalnızca küçük poster + özet.)
