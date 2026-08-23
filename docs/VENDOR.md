# Vendor Kayıtları

Bu repo iki upstream projeyi vendor'lar (git geçmişi olmadan, `git archive` ile kopya).
Upstream güncellemesi çekmek için: upstream'i klonla, aşağıdaki SHA ile `git diff <SHA>..HEAD` alıp buradaki kopyaya uygula; "Yerel değişiklikler" listesini koru.

## `stream/` ← WatchBuddy-tv/Stream
- **Upstream:** https://github.com/WatchBuddy-tv/Stream
- **Vendor SHA:** `84cf7b09f07e4e87c4c5f0ca12dc0f362233dca3` (2026-08-22)
- **Rol:** Web UI + header enjekteli video/altyazı proxy'si + API gateway. KekikStream'i kendisi çalıştırmaz; tüm içerik çağrılarını `DEFAULT_PROVIDER_URL` üzerindeki motora yönlendirir.
- **Yerel değişiklikler (reklam/izleme sökümü + branding):**
  1. `Public/Home/Templates/_html_taban.html.j2` — Ahrefs analytics script'i, `quge5.com` Monetag zone script'i ve `omg10.com` Monetag Direct Link popunder bloğu kaldırıldı.
  2. `Public/Home/Static/JS/sw.js` — Monetag push service worker'ı (`5gvci.com` importScripts) yerine no-op SW konuldu.
  3. `Public/Home/Templates/components/header.html.j2` — WatchBuddy App Store/Play Store linkleri kaldırıldı.
  4. `AYAR.yml` — `PROJE: NetMovies`.
  5. `.env.example` — NetMovies kurulumuna göre yeniden yazıldı.

## `engine/` ← keyiflerolsun/KekikStreamAPI
- **Upstream:** https://github.com/keyiflerolsun/KekikStreamAPI
- **Vendor SHA:** `4b657d98a9818234693cb64b1502e361017ce7f1`
- **Rol:** KekikStream motorunu in-process çalıştıran provider API'si (`/api/v1/search|get_main_page|load_item|load_links|extract`). Compose'da yalnızca iç ağa açık; stream servisi buna bağlanır.
- **Yerel değişiklikler:**
  1. `Public/Home/Templates/_html_taban.html.j2` — Ahrefs analytics kaldırıldı (UI'ı zaten dışa kapalı).
  2. `AYAR.yml` — `PROJE: NetMovies-Engine`.

## Bilinen notlar
- KekikStream sürümü `engine/requirements.txt` içindeki `KekikStream>=2.5.0` pini ile gelir; kaynak siteler domain değiştirdiğinde imajı yeniden build etmek (`docker compose build engine`) günceller.
- `stream` içindeki izleme durumu (kaldığın yerden devam, izlenen bölümler) tarayıcı `localStorage`'ındadır — cihazlar arası senkron Faz C işi.
- `basla.py` her açılışta JS/CSS minify eder (`*.min.js`, `*.min.css` üretilir); bunlar `.gitignore`'dadır.
