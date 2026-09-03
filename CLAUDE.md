# NetMovies — Agent Talimatı (bu dosyayı Claude Code otomatik okur)

Bu, reklamsız kişisel film/dizi/canlı TV uygulamasıdır. Başka bir oturum/agent
(masaüstü Claude Code, deploy agent, yeni web oturumu) buraya girdiğinde:

## Her oturumda ilk yap
1. **Güncel kal:** `git fetch && git checkout fix/general-stability && git pull`
   (Tüm iş bu daldadır; `master` ESKİDİR.)
2. **Bağlamı al:** `docs/HANDOFF.md` oku — en üstteki "DEVİR" bloğu durumu ve sıradaki
   işi verir; altındaki oturum günlükleri gerekçeyi taşır.
3. **Kapıyı çal:** `bash scripts/smoke.sh` — yığın ayakta mı, katalog/zincir/testler yeşil mi.
   Kırmızıysa önce onu çöz; kod yazmadan önce ne bozuk olduğunu bil.

## Çalıştırma (kullanıcının makinesinde — evde)
```bash
cp .env.example .env      # AUTH_USER=dean, AUTH_PASS=1234

# 1. Varsayılan (Çekirdek: stream + engine + doh)
docker compose up -d --build

# 2. WARP proxy ile çalıştırma (Dizilla / Dizipal vb. engelli siteler için opt-in)
docker compose --profile warp up -d

# 3. Dış erişim tüneli ile çalıştırma (w.evaitec.com)
docker compose --profile tunnel up -d

# http://localhost:3310  (dean / 1234)
```
Kolay dış erişim (w.evaitec.com): `docs/DEPLOY.md` — hibrit (motor evde + Cloudflare Tunnel).
Motoru buluta/Azure'a KOYMA: kaynaklar datacenter IP'sini engeller.

## Güncelleme / senkron nasıl olur
- Kod tek kaynak: bu git dalı. Kim çalışırsa `git pull` ile güncel olur.
- **Kaynak domainleri otomatik güncellenir** (eklentiler Kekik-cloudstream'den çeker) —
  elle domain takibi yok. Yeni domain çıkınca `docker compose restart engine` yeter.
- Yeni özellik/düzeltme → aynı dala commit + push → herkes `git pull` ile alır.

## Mimari (özet)
- `engine/` — KekikStream (Python 3.14) + `engine/Plugins/` (RecTV, HDFilmCehennemi, DiziYou, M3UPlaylist).
- `stream/` — web UI + video proxy + auth + admin. `DEFAULT_PROVIDER_URL` ile engine'e bağlı.
- `docker-compose.yml` — engine + stream + doh (DNS-over-HTTPS) + cloudflared (tünel, profile).

## Doğrulama (iddia etmeden önce çalıştır)
```bash
bash scripts/smoke.sh                     # kapı: health + katalog + zincir + testler
docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests
cd client-tv && ./gradlew testDebugUnitTest assembleDebug
docker logs netmovies-engine | grep -E "aggregate:|resolve:"   # kaynak teşhisi
```
`tests/` imaja build ile girer; kod değiştirmeden test denemek için:
`docker cp stream/tests netmovies-stream:/usr/src/Stream/`

## Kurallar
- Eklenti eklerken `engine/Plugins/` altına `PluginBase` türevi koy; domain için
  `discover_main_url` (bkz. `__kekik_domain.py`) kullan.
- `.env` gitignored (şifre repoya girmez). Minify çıktıları (`*.min.js`) gitignored.
- Sandbox'ta foreground `sleep` bloklu; test için `run_in_background` + `curl --retry`.

## Kullanıcı tercihleri (Dean)
- Türkçe konuşur. Reklamsız, sade, tıkla-izle. Asya/anime istemez (admin'de varsayılan gizli).
- "Site site gezmek istemiyorum" → birleşik **Yeni Çıkanlar** akışı (çalışan kaynaktan otomatik).
- Kalite: varsayılan 1080/720 admin'den ayarlanır; oynatıcıda kalite değiştirme butonu.
