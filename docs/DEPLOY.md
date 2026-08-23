# NetMovies — Deploy (Hibrit: evde motor + w.evaitec.com kolay link)

**Neden hibrit?** Kaynak siteler (RecTV, InatBox vb.) **datacenter/bulut IP'lerini engelliyor**
(InatBox guard listesinde `azure, amazon, google, hetzner…` açıkça var). Bu yüzden içerik
motoru **evdeki makinede** (residential IP) çalışır — ban yok, 4K egress bedava. Azure/Cloudflare
sadece `w.evaitec.com` adresini eve **tüneller**; kolay link + çalışan scraper + düşük maliyet.

```
  Telefon / Mibox / PC
        │  https://w.evaitec.com  (Dean / 1234)
        ▼
  Cloudflare Tunnel  (veya Azure reverse-proxy → eve VPN)
        │  şifreli tünel
        ▼
  EVDEKİ MAKİNE (residential IP)
   ├─ stream :3310  (UI + proxy + auth)
   └─ engine        (KekikStream + eklentiler + M3U)
```

---

## 1. Evdeki makinede kurulum

### Linux / macOS
```bash
git clone https://github.com/evatechnosoft/netmovies.git
cd netmovies
git checkout claude/stream-app-architecture-86q0sg   # (birleşene kadar)

cp .env.example .env          # AUTH_USER=Dean, AUTH_PASS=... doldur
docker compose up -d --build
```

### Windows (D:\projects içine)
Docker Desktop kurulu olmalı. PowerShell:
```powershell
cd D:\projects
git clone https://github.com/evatechnosoft/netmovies.git
cd netmovies
git checkout claude/stream-app-architecture-86q0sg
copy .env.example .env        # not defteri ile aç, AUTH_USER=Dean / AUTH_PASS gir
docker compose up -d --build
```
Sonra tarayıcı: `http://localhost:3310` (Dean / 1234).

> Not: Ben (asistan) senin bilgisayarına bağlı değilim; bu komutları **sen kendi
> makinende** çalıştıracaksın. Kod GitHub'da hazır.

- Yerel erişim: `http://<ev-ip>:3310` — kullanıcı **Dean**, şifre **1234** (`.env`).
- `.env` git'e girmez (gitignored); şifre repoda tutulmaz.
- Sağlık/kaynak durumu: `http://<ev-ip>:3310/admin` (Yönetim paneli).

> **1234 zayıf** — dışarı (w.evaitec.com) açacaksan `.env`'de AUTH_PASS'i güçlendir.

---

## 2. Kolay link: `w.evaitec.com` (Cloudflare Tunnel — önerilen)

En kolay yol; port açmadan, sabit IP gerektirmeden.

1. `evaitec.com`'u Cloudflare'e ekle (DNS yönetimi Cloudflare'de olsun).
2. Cloudflare **Zero Trust → Networks → Tunnels** → tünel oluştur.
3. Public hostname ekle: `w.evaitec.com` → **Service:** `http://stream:3310`
   (aynı compose ağında; ya da `http://localhost:3310`).
4. Tünel **token**'ını kopyala → `.env` içine `CF_TUNNEL_TOKEN=...`
5. Tüneli başlat:
   ```bash
   docker compose --profile tunnel up -d
   ```
6. Artık `https://w.evaitec.com` → evdeki NetMovies (Dean / 1234).

Ekstra koruma istersen Cloudflare **Access** (e-posta/OTP) ile basic auth'un önüne bir kapı daha koyabilirsin.

---

## 3. Azure ile (evaitec.com Azure DNS'te ise)

Cloudflare kullanmak istemezsen, Azure DevOps/Azure ekosisteminde iki yol:

- **En pratik:** `evaitec.com`'u (veya sadece `w` alt-alanını) Cloudflare'e devret, yukarıdaki tüneli kullan. Azure hesabına dokunmadan çalışır.
- **Tam Azure:** Azure'da küçük bir reverse-proxy (Container App veya B1s VM: Caddy/nginx) `w.evaitec.com`'u karşılar, oradan eve **WireGuard/Tailscale** ile bağlanıp `stream:3310`'a proxy eder. ADO pipeline ile reverse-proxy'yi deploy edebilirsin. (İçerik motorunu Azure'a KOYMA — datacenter ban.)

> Motoru tümüyle Azure'a koymak istersen çalışır ama kaynakların çoğu "kırmızı" olabilir
> (datacenter IP). O senaryoda giden trafiği bir **residential proxy**'den geçirmen gerekir
> (`.env` → `HTTP_PROXY` / `HTTPS_PROXY`), ki bu ek maliyet ve karmaşadır.

---

## 4. Engelli siteler (DNS engeli) — otomatik aşılıyor

Türkiye'de kaynak siteler genelde **ISP DNS engeli** ile kapatılır. Compose'a
**DNS-over-HTTPS (DoH)** resolver'ı (`doh` servisi) eklendi: engine ve stream tüm
alan adı çözümlemesini HTTPS üzerinden (Cloudflare 1.1.1.1 / Google) yapar, ISP DNS'i
devre dışı kalır. Ekstra ayar gerekmez, `docker compose up` ile aktif.

- Bir domain hâlâ açılmıyorsa **SNI/IP engeli** olabilir (DoH bunu çözmez); o durumda
  giden trafiği `.env` → `HTTP_PROXY`/`HTTPS_PROXY` ile bir proxy'den geçir.
- Oynatıcı kütüphanesi **hls.js** artık self-host (imaj içine indirilir); dış CDN
  (cdnjs/jsDelivr) engellense bile oynatma çalışır. İndirilemezse çalışma anında
  jsDelivr → cdnjs sırasıyla fallback dener.

## 5. Bakım

- **Kaynak kırmızı olduğunda:** `/admin` sağlık panelinde hangi site düştüğünü gör. RecTV domaini
  değiştiyse `.env` → `RECTV_URL=https://b.prectvNN.sbs` yazıp `docker compose up -d`.
- **KekikStream güncelleme:** `docker compose build engine` (extractor/motor tazelenir).
- **4K donma:** `.env` → `SEGMENT_CACHE_MB` artır; en akıcı deneyim için oynatıcıda
  **Harici Oynat → Nova/MX** (Mibox'ta native player tarayıcıdan çok daha iyi).
