# NetMovies

Reklamsız, kişisel, "tıkla‑izle" odaklı **film / dizi / canlı TV** uygulaması.
İki servis (Docker) + PWA arayüz:

- **`engine/`** — KekikStream tabanlı sağlayıcı API (Python 3.14). Kendi eklentilerimiz `engine/Plugins/` altında (HDFilmCehennemi, DiziYou, RecTV, M3UPlaylist).
- **`stream/`** — Web arayüzü (PWA) + header‑enjekteli video/altyazı proxy + API gateway.
- **`doh`** — DNS‑over‑HTTPS resolver (ISP DNS engelini aşar).
- **`warp`** — Cloudflare WARP çıkış proxy'si (TR SNI/DPI engelini aşar). Engine
  yalnız bloklu kaynaklar için seçici kullanır; ayakta değilse WARP'sız devam eder.
- **`cloudflared`** — `w.evaitec.com` → eve tünel (opsiyonel, `--profile tunnel`).

## Çalıştırma

```bash
cp .env.example .env        # AUTH_USER / AUTH_PASS düzenle

# Uygulama (stream + engine + doh + warp)
docker compose up -d --build

# Gerekirse Cloudflare Tüneli eklemek için (opt-in):
docker compose --profile tunnel up -d
# → http://localhost:3310
```

Motoru buluta/Azure'a **koyma**: kaynaklar datacenter IP'sini engeller; engine
evde (residential IP) çalışmalı. Dış erişim için `docs/DEPLOY.md` (Cloudflare Tunnel).

## Dokümantasyon

| Dosya | İçerik |
|---|---|
| `CLAUDE.md` | Agent talimatı — her oturumda ilk okunan |
| `docs/HANDOFF.md` | Güncel durum, dosya haritası, backlog |
| `docs/MIMARI_SPEC.md` | Mimari kararlar (ADR) |
| `docs/KURULUM.md` | Kurulum + yeni eklenti ekleme rehberi |
| `docs/DEPLOY.md` | Hibrit deploy (ev + tünel) |

## Mimari (özet)

Client (PWA) → `stream` (arayüz + proxy + auth) → `engine` (eklenti API) → kaynak siteler.
Kaynak domainleri otomatik keşfedilir; elle domain takibi gerekmez.
