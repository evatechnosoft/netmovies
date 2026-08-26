# NetMovies — Kurulum ve Kullanım

Kişisel, reklamsız film/dizi izleme uygulaması. İki servisten oluşur:

- **stream** (`:3310`) — Web arayüzü + header enjekteli video/altyazı proxy'si + API gateway.
- **engine** (iç ağ) — KekikStream motoru + kendi eklentilerimiz (HDFilmCehennemi) + M3U listeleriniz.

Mimari kararların tamamı için `docs/MIMARI_SPEC.md`, iskelet seçimi için `docs/ISKELET_SECIMI.md`, vendor kayıtları için `docs/VENDOR.md`.

---

## 1. Hızlı Başlangıç (Docker — önerilen)

```bash
# (İsteğe bağlı) Kendi M3U listenizi kullanacaksanız:
#   1) lists/ klasörüne .m3u dosyanızı koyun
#   2) M3U_SOURCES değişkenini verin (aşağıda)

# Kendi M3U listesiyle:
M3U_SOURCES=/data/lists/liste.m3u docker compose up -d --build

# M3U olmadan (sadece scraper + uzak provider):
docker compose up -d --build
```

Ardından tarayıcıdan: **http://localhost:3310**

> Telefondan/başka cihazdan erişim için engine'in koştuğu makineye ağdan ulaşmanız gerekir. Ev ağı dışı için **Tailscale** önerilir (kimlik doğrulamasız internete açmayın).

---

## 2. İçerik Kaynakları (Hibrit)

Uygulama üç kaynağı birlikte kullanır:

| Kaynak | Nasıl | Not |
|---|---|---|
| **Yerel motor (varsayılan)** | Kendi eklentilerimiz — şu an **HDFilmCehennemi** | Tam kontrol, reklamsız, bakımı bizde |
| **Kendi M3U listeleriniz** | `M3U_SOURCES` env → **M3U Listelerim** eklentisi | IPTV/kişisel kaynak, hiçbir dış siteye bağımlı değil |
| **Uzak provider (opsiyonel)** | Arayüzde ⚙️ → "Uzak sağlayıcı URL'si" (örn. `https://stream.watchbuddy.tv`) | Geniş içerik; kapanabilir/yavaşlayabilir. Reklamları **bizim UI'da görünmez**, yalnızca API'si kullanılır |

Arayüzdeki sağlayıcı geçişi kalıcıdır (cookie/localStorage). Uzak sağlayıcıdan yerel motora dönmek için ⚙️ → "Sıfırla".

---

## 3. Kendi M3U Listeniz

`lists/ornek.m3u.example` örnektir. Desteklenenler:

- `#EXTINF` — `group-title` (kategori), `tvg-logo` (poster), `tvg-name` (sıradan bağımsız okunur)
- `#EXTVLCOPT:http-referrer=...` ve `#EXTVLCOPT:http-user-agent=...` → oynatıcıya header olarak taşınır
- `#EXTHTTP:{"Referer":"...","Cookie":"..."}` → ek header'lar

Birden fazla kaynak: `M3U_SOURCES=/data/lists/a.m3u,/data/lists/b.m3u8,https://uzak/liste.m3u`

---

## 4. Günlük Sağlık Kontrolü

engine, açılışta ve her 24 saatte bir tüm eklentilerin hedef sitelerini kontrol eder (kullanıcının "her gün açılışta kontrol" isteği). Sonucu görmek için:

```
http://localhost:3310/api/v1/plugin_health          # cache'ten (hızlı)
http://localhost:3310/api/v1/plugin_health?force=1   # taze kontrol
```

Bir site `ok:false` + "domain değişmiş olabilir" dönerse, ilgili eklentinin `main_url`'ünü güncellemek gerekir (bkz. §6).

---

## 5. Oynatıcı

- **Tarayıcı içi (varsayılan):** hls.js + engine/stream proxy'si header'ları enjekte eder; kaldığın yerden devam, altyazı, çoklu ses izi desteklenir.
- **Harici oynatıcı:** Faz B'de eklenecek (Android `intent:` VLC/MX/mpv, masaüstü için `EXTVLCOPT`'lu `.m3u`). Tasarım `docs/MIMARI_SPEC.md` §7'de.

---

## 6. Yeni Eklenti Ekleme (kendi kaynağınız)

`engine/Plugins/` içine bir `.py` dosyası koyun; `PluginBase`'den türeyip dört metodu doldurun:

```python
from KekikStream.Core import PluginBase, MainPageResult, SearchResult, MovieInfo, SeriesInfo, Episode, ExtractResult, Subtitle, HTMLHelper

class Kaynak(PluginBase):
    name     = "Kaynak"
    main_url = "https://site.example"
    main_page = { f"{main_url}/yeni": "Yeni Eklenenler" }

    async def get_main_page(self, page, url, category): ...
    async def search(self, query): ...
    async def load_item(self, url): ...       # MovieInfo | SeriesInfo
    async def load_links(self, url): ...       # list[ExtractResult]
```

`HDFilmCehennemi.py` tam bir örnektir (gömülü P.A.C.K.E.R unpacker dahil). İstek için `self.httpx.get(...)` veya Cloudflare için `self.async_cf_get(...)` kullanın. engine yeniden başlatıldığında yeni eklenti otomatik yüklenir; `get_plugin_names` ile doğrulayın.

> Not: KekikStream motoru artık eklentileri paketle dağıtmıyor; bu yüzden ihtiyaç duyulan her kaynağın eklentisi bu repoda tutulur ve bakımı buradan yapılır. Referans scrape mantığı için `keyiflerolsun/Kekik-cloudstream` (Kotlin) faydalıdır.

---

## 7. Geliştirme (Docker'sız)

```bash
# engine
cd engine
uv venv --python 3.14 .venv && uv pip install -p .venv -r requirements.txt
M3U_SOURCES=/mutlak/yol/liste.m3u PORT=3310 .venv/bin/python basla.py

# stream (ayrı terminal)
cd stream
uv pip install -p .venv .
DEFAULT_PROVIDER_URL=http://127.0.0.1:3310 PORT=3311 .venv/bin/python basla.py
```

> engine **Python 3.14** gerektirir (KekikStream 3.8.x zorunluluğu). uv 3.14 stabil sürümü indirir.
