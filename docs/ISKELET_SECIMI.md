# Hazır İskelet Araştırması & Seçim Kararı

> **Durum:** v1.0 — 2026-08-23 · `MIMARI_SPEC.md` ADR-2'yi revize eder.
> **Karar:** Kendi engine'imizi sıfırdan yazmak yerine **WatchBuddy-tv/Stream** fork'unu temel al, reklam kodlarını söküp üzerine eksik özellikleri ekle.

---

## 1. Aday Envanteri (derin tarama sonucu)

### A) `keyiflerolsun/KekikStreamAPI` (ana repo)
- **Ne var:** FastAPI :3310, tam web UI (ana sayfa/arama/kategori/detay/oynatıcı — Jinja2 + vanilla JS), 10 REST endpoint (`search`, `get_main_page`, `load_item`, `load_links`, `extract`, `ytdlp-extract`…), hls.js oynatıcı (~1.400 satır), Dockerfile.
- **Ne yok (kritik):**
  - ❌ **Stream proxy yok** — oynatıcı linki "direct" yüklüyor; `Referer`/`User-Agent` isteyen kaynaklar tarayıcıda **oynamaz**.
  - ❌ Devam etme / izleme geçmişi yok, favoriler yok, PWA yok, M3U yok, harici oynatıcı yok.
  - ❌ docker-compose yok, 4 commit — iskelet ham.
  - ⚠️ `pymongo` ile IP loglama modülü var.

### B) `WatchBuddy-tv/Stream` ⭐ **SEÇİLEN**
KekikStreamAPI'nin **üretimde koşan** fork'u (stream.watchbuddy.tv, son commit: dün, 11 yıldız). Ana repodaki her şeye ek olarak:
- ✅ **Header enjekte eden video proxy** (`Public/Proxy/Routers/video.py`): HLS manifest yeniden yazımı, segment cache, altyazı proxy'si, `extra_headers` desteği → en zor problemimiz çözülmüş.
- ✅ **Kaldığın yerden devam** (`wb_resume_watching`, son 50 içerik) + **izlenen bölüm işaretleme** (`wb_watched_episodes`).
- ✅ Tercih hafızası: kaynak, altyazı, ses izi (`wb_preferred_*`).
- ✅ ~3.200 satırlık gelişmiş oynatıcı: önizleme (seek preview), çoklu ses izi, altyazı ayar paneli, klavye kısayolları.
- ✅ 7 dilli i18n (TR dahil), `docker-compose.yml`, `pyproject.toml`, CapRover deploy workflow, `/api/v1/schema` ile uzak-provider mimarisi.
- ⚠️ **Sökülecekler (reklam/izleme — toplam ~4 dosya, önemsiz efor):**
  - `Public/Home/Static/JS/sw.js` → **Monetag push-reklam service worker'ı** (`5gvci.com` importScripts) — sil, yerine boş/offline SW koy.
  - `_html_taban.html.j2:96` → `quge5.com/88/tag.min.js` (Monetag popunder zone) — sil.
  - `_html_taban.html.j2:82` → Ahrefs analytics — sil.
  - `Core/Modules/_IP_Log.py` (Mongo IP log) — devre dışı bırak; `pymongo` bağımlılığı da düşer.
- ⚠️ İzleme durumu **localStorage'da** → cihazlar arası senkron yok (bkz. §3 yol haritası).

### C) Diğerleri
- `onurcvnoglu/KekikStreamAPI` fork'u: Mayıs'tan beri ölü, katkı yok.
- GitHub genelinde KekikStream'e bağlanan başka web iskeleti **bulunamadı** (çıkan sonuçlar CloudStream eklenti repoları — web UI değil).
- WatchBuddy ekosistemi (Android/iOS istemci + Telegram bot) fork'un API'siyle konuşuyor; istersek mobilde hazır istemci bonusu.

## 2. Karar Gerekçesi

| Kriter | Sıfırdan (eski ADR-2) | KekikStreamAPI | **WatchBuddy Stream** |
|---|---|---|---|
| İlk çalışır sürüm | 3-4 gün | ~1 gün (proxy'siz, sakat) | **~yarım gün** |
| Header'lı stream tarayıcıda | yazılacaktı | ❌ | ✅ hazır |
| Devam etme / bölüm takibi | yazılacaktı | ❌ | ✅ hazır (tek cihaz) |
| Reklamsızlık | ✅ | ✅ | 3 script sökülünce ✅ |
| Bakım yükü | tamamı bizde | upstream + biz | upstream aktif (dün commit) |

**Sonuç:** "Gerekirse düzenler hızlı çıkarız" hedefine birebir uyan tek aday B. Kendi Next.js frontend'imizi (bu repo) yazma ihtiyacı düşer; `netmovies` repo'su fork'un vendor'landığı yer olur veya ayrı fork repo açılır.

## 3. Uygulama Planı

**Faz A — Temiz kurulum (yarım gün)**
1. `WatchBuddy-tv/Stream`'i fork'la / bu repoya vendor'la (`upstream` remote'u koru — güncellemeleri çekebilmek için).
2. Reklam/izleme sökümü: `sw.js` içeriğini boşalt, `_html_taban.html.j2`'den quge5 + ahrefs satırlarını sil, `_IP_Log`'u kapat.
3. `AYAR.yml` + branding: isim/logo, WatchBuddy rozetlerini kaldır.
4. `docker compose up` → smoke test: Dizimom'da ara → bölüm → tarayıcıda oynat (proxy üzerinden).

**Faz B — Eksik özellikler (1-2 gün)**
5. **Harici oynatıcı butonu** oynatıcı sayfasına: Android `intent:` (VLC/MX/mpv, MX'e header extra'sı) + masaüstü için `EXTVLCOPT` header'lı tek öğeli `.m3u` indirme (spec §7.2-7.3'teki tasarım aynen).
6. Ana sayfaya **"Devam Et" rafı**: `wb_resume_watching`'i ana sayfada listele (yalnızca frontend işi).
7. Sıralama seçenekleri: kategori sayfasına A-Z / son bölüm / ilk bölüm (spec §5.4 normalize edici).

**Faz C — Opsiyonel (ihtiyaç doğarsa)**
8. M3U liste desteği (spec §5.3 parser'ı fork'a router olarak eklenir).
9. İzleme durumunu SQLite'a taşıyıp cihazlar arası senkron (localStorage'dan migration).
10. Upstream takibi: ayda bir `git fetch upstream && merge` + KekikStream sürüm güncellemesi.

## 4. Bu Repo (`netmovies`) İçin Anlamı
Mevcut Next.js şablonu bu planda **kullanılmaz** (fork'un Jinja2 UI'ı yeterli). Next.js kodu ileride özel bir arayüz istenirse fork'un API'sine bağlanabilecek yedek olarak dursun; silinmesi gerekmiyor.
