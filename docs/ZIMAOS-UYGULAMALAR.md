# DeanOS (ZimaOS v1.6.0): Uygulama Envanteri ve Öneriler

> Tarama 2026-09-25 11:42–11:46 (+03). Salt-okunur: hiçbir şey kurulmadı, silinmedi, durdurulmadı. Commit edilmedi.
> `ZIMAOS-DURUM.md`'nin devamıdır. Disk, imaj temizliği, DOCKER_CONFIG, host `cloudflared.service`, RAUC ve log rotasyonu orada; burada tekrar edilmiyor.
> Kaynaklar: `/var/lib/casaos/apps/*/docker-compose.yml` (x-casaos), `curl 127.0.0.1/v2/app_management/web/appgrid` (64 kayıt), `docker ps -a`, `docker inspect`, `docker stats --no-stream`, `du -sh /DATA/AppData/*`, `/etc/casaos/app-management.conf`, `docker logs cloudflared`, `kuma.db` (salt-okunur sqlite).

## 0. Bu taramanın yeni bulguları (öncelik sırasıyla)

1. **`dozzle.evaitec.com` kimlik doğrulamasız, internete açık.** `curl https://dozzle.evaitec.com/` → 200 `<title>Dozzle</title>`, `/api/events/stream` → 200. Dozzle konteynerinde `DOZZLE_*` env yok (auth kapalı). Tüm konteyner logları dışarıdan okunabiliyor. Loglarda yapılandırma ve token sızıntısı riski var.
2. **Tünelde ölü ya da tehlikeli hostname'ler var.** Ana tünel (`cloudflared` konteyneri, son config 2026-09-25 07:40Z) 15 hostname taşıyor. Dışarıdan `curl` sonucu: `db` (postgres 5432'ye http), `it` (8001), `api` (5005), `test` (5006), `prod` (10443), `portal` (85): **502**. `dash` (ZimaOS arayüzü, port 80) ve `my` (3001): 000. Yerelde 8001, 5005, 5006, 85 ve 10443 bağlantıyı reddediyor. `port` (Portainer), `admin` (open-webui) ve `ha` 200 dönüyor. Bu üçü kendi girişiyle korunuyor; Cloudflare Access doğrulanmadı.
3. **`w.evaitec.com` ZimaOS'tan yayınlanmıyor.** Ana tünelin ingress listesinde yok. `netmovies-tunnel` konteyneri (compose `profiles: tunnel`) de yok; yalnız `netmovies-doh` ayakta, o da tünel değil DoH. 3310 yerelde 200 dönüyor.
4. **Uptime Kuma kurulu ama susuyor.** 5 monitör var (`my` ×2 aynı URL, `it`, `cloud`, `ha`), bildirim kanalı 0. NetMovies izlenmiyor.
5. **CasaOS'ta 37 ölü uygulama tanımı var.** `/var/lib/casaos/apps` içindeki 63 klasörden 37'sinin konteyneri yok. Bunların 9'u `_old` kopyası (`evaiteclabs_old…`, `it-inventory_old…`).
6. **Son oturum 2026-08-24.** Kuma'nın bugünden önceki son heartbeat'i `2026-08-24 18:16`. Çoğu "son dosya" izi 08-24 çıkıyor: o gün makine açıktı, loglar ve DB checkpoint'leri yazıldı. **08-24 izi kullanım kanıtı değildir**, yalnız makinenin açık olduğunu gösterir. Daha eski tarihler gerçek son dokunuşa daha yakındır.
7. ZimaOS app API'si yerelden kimliksiz cevap veriyor (`127.0.0.1` → 200), LAN'dan vermiyor (`192.168.1.186` → 401). Sorun değil, bilgi notu.

## 1. Mağazalar (kurulu app store kaynakları)

`/etc/casaos/app-management.conf` → `[server] appstore=` satırları. Klasör: `/var/lib/casaos/appstore`.

| Mağaza | Uygulama sayısı (compose) | Not |
|---|---|---|
| CasaOS resmi (`cdn.jsdelivr.net/gh/IceWhaleTech/CasaOS-AppStore`) + gömülü `default/Apps` | 143 | 2fauth, nextcloud, obsidian, n8n, adminer, portainer, flowise, librechat, weknora buradan |
| BigBear (`github.com/bigbeartechworld/big-bear-casaos`) | 240 | `big-bear-*` uygulamaları; Diun, Beszel, Kopia, ntfy, Tugtainer, Scrutiny, Dockge burada |
| LinuxServer (`casaos-appstore.paodayag.dev/linuxserver.zip`) | 246 | `linuxserver-*` (yazar WisdomSky); Duplicati, Healthchecks, Apprise |
| Coolstore (`paodayag.dev/coolstore.zip`) | 45 | cloudflared-web, postgresql, portainer-tools; Caddy, Traefik |
| HomeAutomation (`mr-manuel`) | 11 | influxdb, mosquitto, portainer-agent |
| Pentest-Docker (`arch3rPro`) | 12 | kullanılan yok. Güvenlik test araçları; **kaldırma adayı mağaza** |
| ZimaOS-AppStore (`justserdar` v0.0.8) | 4 | kullanılan yok |

Watchtower hiçbir kurulu mağazada yok (`find … -name docker-compose.yml | grep -i watchtower` boş).

## 2. Envanter

Sütunlar:
- **RAM/CPU:** `docker stats --no-stream`, 11:42.
- **AppData:** `du -sh`.
- **Son iz:** 2026-09-25 10:30'dan önce değişmiş en yeni dosyanın tarihi (python `os.walk`). "08-24" yalnız makinenin açık olduğunu gösterir (bkz. §0.6).
- **StartedAt:** hepsi bugünkü boot (07:39–07:41Z). Kullanım ayırt etmez, bu yüzden tabloya alınmadı.
- **Kaynak:** x-casaos `author` ve `store_app_id` alanları, compose etiketi `com.docker.compose.project.working_dir`. "docker run" = compose etiketi yok.

### 2a. Altyapı ve araçlar

| Uygulama | Kaynak | Kont. | RAM | CPU | AppData | Son iz | Port / URL |
|---|---|---|---|---|---|---|---|
| NetMovies (engine, stream, doh, warp) | elle compose `/DATA/AppData/netmovies` | 4 | 437 M | %0,5 | 2,5 G | bugün (segment cache) | 3310 |
| cloudflared (ana tünel) | Coolstore, WisdomSky `cloudflared-web:2025.2.1` | 1 | 98 M | %0,2 | 12 K (`casaos-cloudflared`) + volume | log canlı | UI 14333, host ağı; 15 hostname (§0.2) |
| Uptime Kuma | docker run `louislam/uptime-kuma:1` | 1 | 170 M | %0,4 | 27 M (volume `uptime-kuma`) | heartbeat canlı | 3002, kuma.evaitec.com |
| Dozzle | docker run `amir20/dozzle` | 1 | 57 M | %0,2 | — (yalnız docker.sock) | — | 8888, **dozzle.evaitec.com (açık)** |
| Portainer + agent + tools | resmi (Cp0204) + HomeAutomation + Coolstore | 3 | 211 M | ~0 | 1,4 M + 24 K | portainer 03-26, tools 03-28 | 9000/9443, 9994, 9995; port.evaitec.com |
| Dockhand | BigBear | 1 | 136 M | %0,3 | 150 M | 03-27 (yalnız `.encryption_key`) | 3003 |
| Coolify v4 | elle compose `/DATA/AppData/coolify-v4` | 3 | 313 M | **%34** | 408 M | 08-24 | 9800 |
| coolify-sentinel | CasaOS User (0.0.21) + docker run (1.0.1, Created) | 1+1 | 25 M | ~0 | — | — | 4545 |
| Prometheus + Grafana | docker run + CasaOS User | 2 | 383 M | %0,3 | 8 K + 50 M | 04-06 (config) | 9090, 4500 |
| AdGuard Home | docker run | 1 | 74 M | ~0 | 5,5 M | 05-18 (querylog) | 53, 3000, 8085, 4433 |
| code-server | BigBear | 1 | 102 M | ~0 | 104 K | 04-26 (heartbeat) | 8901 |
| Adminer | resmi | 1 | 24 M | 0 | — | — | 8083 |
| postgresql 17.4 | Coolstore (WisdomSky) | 1 | 58 M | 0 | 103 M | 04-20 | 5432; db.evaitec.com (502) |
| big-bear-casaos-user-management | BigBear | 1 | 63 M | %0,2 | — | — | 5000 |
| MinIO | BigBear | 1 | 140 M | ~0 | 232 K | 04-10 (tek `test` bucket) | 9010/9011 |
| 2FAuth | resmi | 1 | 48 M | 0 | 440 K | 04-06 (`database.sqlite`) | 8000 |
| Nextcloud 32 | resmi | 1 | 169 M | 0 | 3,2 G | 04-08 | 10081/10443; cloud.evaitec.com |
| Obsidian (web) | resmi | 1 | **774 M** | %3,1 | 108 M (log 274 M, önceki rapor) | 08-24 (cache) | 15323/15324 |

### 2b. Ev ve IoT

| Uygulama | Kaynak | Kont. | RAM | CPU | AppData | Son iz | Port / URL |
|---|---|---|---|---|---|---|---|
| Home Assistant `ha` 2025.11 | docker run, host ağı | 1 | 560 M | %0,9 | 279 M (`homeassistant`) | 08-24 | 8123; ha.evaitec.com (200), Kuma izliyor |
| HA Supervised yığını | CasaOS User `hassio_supervisor` + supervisor'ın açtıkları | 6 (audio Exited) | 663 M | %0,3 | 101 M (`hassio`) | 07-27 (`home-assistant.log.fault`) | 8124, 4357 |
| ESPHome | CasaOS User | 1 | 59 M | %2,9 | 2,5 G | 04-14 (`smart-station` build) | 6052 |
| Mosquitto | HomeAutomation | 1 | 7 M | 0 | 24 K | 04-06 (config) | 1883 |
| InfluxDB | HomeAutomation | 1 | 160 M | 0 | 9 M | 04-12 (tsm) | 8086 |
| Node-RED | BigBear | 1 | 96 M | 0 | 76 K | 03-29 | 1880 |
| n8n 1.123 | resmi (YoussofKhawaja) | 1 | 271 M | 0 | 680 K | 08-24 (event log) | 5678 |

### 2c. AI ve LLM

| Uygulama | Kaynak | Kont. | RAM | CPU | AppData | Son iz | Port / URL |
|---|---|---|---|---|---|---|---|
| Open WebUI `:main` | docker run (isimsiz, `c5899…`) | 1 | 966 M | %0,1 | 890 M | 07-27 | 8080; admin.evaitec.com (200) |
| LibreChat | resmi | 5 | 538 M | %0,7 | 529 M | 08-24 (mongo diag) | 3080 |
| WeKnora | resmi (tencent) | 7 | 479 M | %0,6 | 103 M | 04-09 | 1080, 19000/19001, jaeger 16686… |
| Flowise 3.0.11 | resmi | 1 | 450 M | 0 | 640 K | 08-24 (log) | 3025 |
| Flowise 3.1.2 | BigBear | 1 | 545 M | 0 | 676 K | 08-24 (log) | 8009 |
| Ollama | elle compose, proje `tmp` (`/tmp`, tanım kayıp) | 1 | 50 M | 0 | 2,2 G (yalnız llama3.2) | 04-04 | 4602 |
| llama.cpp | docker run | 1 (Exited 1, 40 restart) | — | — | — | 04-14 | — |

### 2d. Dean'in projeleri ve iş uygulamaları

| Uygulama | Kaynak | Kont. | RAM | AppData | Son iz | Port / URL |
|---|---|---|---|---|---|---|
| call_center | elle compose | 3 | 196 M | 238 M | 05-14 (`.env`) | host ağı |
| flexcrm | elle compose | 3 | 71 M | 314 M | 08-24 (pg) | 3015/3016, 5436 |
| modularcrm | elle compose | 4 | 189 M | 27 M | 04-25 | 3009, 5001, 5433, 5672, 15672 |
| anadolu-spor | elle compose | 3 | 158 M | 405 M | 04-14 | 5601, 5602 |
| randevu | docker run | 1 | 149 M | 1,5 M | 04-14 (git) | 3005 |
| sport-app | elle compose | 1 | 89 M | — | — | host ağı |
| it-inventory-test | elle compose | 1 | 178 M | 136 K | 04-13 | 9600 (it.evaitec.com 8001'i gösteriyor, 502) |
| evaiteclabs | docker run (+5 ölü CasaOS tanımı) | 1 | 9 M | 54 M | 04-13 | 3001; my.evaitec.com |
| ev-telemetry-dashboard | elle compose | 1 | 148 M | 1 M | 04-14 | 3006 |
| status_dashboard | elle compose `/DATA/dean/projects` | 1 | 10 M | — | — | 8088; status.evaitec.com (403) |
| eva-portal (Homer) | elle compose `/DATA/AppData/portal` | 1 | 2 M | 8 K | — | **8090** |
| volley | elle compose `/DATA/projects/volley` | 3 (backend Exited 1, frontend Created) | 18 M | — | 04-14 | 5435 |
| quiz_bank | elle compose `/DATA/projects/quiz_bank` | 2 (frontend Exited 1, 5 ay) | 79 M | — | 04-02 | 9308 |
| apiflow-monitor | elle compose (yalnız db) | 1 | 35 M | — | — | — |
| agentops-nexus | elle compose (yalnız db) | 1 | 17 M | 8 K | — | — |
| Nexus: mcp, bot, vault | docker run + CasaOS User | 3 | 240 M | 382 M + 500 K + 20 K | 04-22 | 8900, 8200 |
| nexus-memora | elle compose | 1 (**Restarting, 72**) | — | 12 K | 04-22 | — |

### 2e. Konteyneri olmayan CasaOS tanımları (37 klasör, `/var/lib/casaos/apps`)

`agentops-nexus-web`, `anythingllm`, `app` (NPM, 85), `big-bear-cloudflared-web`, `big-bear-codex-docs`, `big-bear-coolify`, `big-bear-open-webui`, `evaiteclabs` + `_old`×4, `fascinating_magnus`, `grafana` (3008), `homeassistant`, `homeassistant-homeassistant-1`, `homeassistant-homeassistant-1-homeassistant-homeassistant-1-1`, `it`, `it-inventory`, `it-inventory-prod-app-1`, `it-inventory_old`×5, `linuxserver-build-agent`, `-chrome`, `-code-server`, `-dolphin`, `-github-desktop`, `-python`, `nginx-proxy-manager`, `nginxproxymanager`, `nifty_davinci`, `ollama` (4602; çalışan ollama başka projeden), `open-webui-ollama`, `quiz-bank-backend`.
Kanıt: klasör listesi ile `docker ps -a` konteyner adları ve compose etiketleri karşılaştırıldı.

**Toplam:** 78 konteyner (`docker ps -a` satırı), 72 çalışıyor. Çalışanların RAM toplamı ≈ 13,7 GiB (stats satırları toplandı, yaklaşık).

## 3. Sınıflandırma

| Karar | Uygulama | Gerekçe |
|---|---|---|
| TUT | NetMovies | Ana ürün; bugün 3310 → 200, segment cache yazıyor. |
| TUT | cloudflared (ana tünel) | 15 hostname'i taşıyan tek canlı tünel. Ölü hostname'leri temizlemek gerekiyor (§0.2). |
| TUT | Uptime Kuma | İzleme işini zaten yapıyor; eksik olan monitör ve bildirim kanalı, yeni araç değil. |
| TUT (kısıtla) | Dozzle | Hafif log görüntüleyici (57 M). Ama tünelden açık: ingress kaldırılmalı ya da Access/auth eklenmeli. |
| TUT | Portainer (çekirdek) | port.evaitec.com'da kullanılıyor, kendi girişi var; konteyner yönetiminin tek aracı olsun. |
| TUT | 2FAuth | 2FA sırlarını tutuyor. Kaldırmak veri kaybı riski; önce yedek. |
| TUT | Nextcloud | cloud.evaitec.com canlı (302 /login), Kuma izliyor, 3,2 G veri. |
| TUT | Home Assistant `ha` | ha.evaitec.com ve Kuma bu kopyayı gösteriyor (8123, 200). |
| BİRLEŞTİR | HA Supervised yığını (6 konteyner) | İkinci HA. Tünelde yok, son izi 07-27 `log.fault`, `supervisor-firewall-gateway` failed. `ha`'ya birleştir, sonra kaldır (~660 M). |
| BİRLEŞTİR | Flowise 3025 + Flowise 8009 | Aynı araç iki kez (995 M). Hangisinde flow olduğu doğrulanmadı; birinden export, diğerine import. |
| BİRLEŞTİR | Chat arayüzleri: Open WebUI, LibreChat, WeKnora | Üç arayüz, ~1,9 G RAM, 13 konteyner. WeKnora'nın son izi 04-09. Dean birini seçsin; admin.evaitec.com'daki Open WebUI en olası aday. |
| BİRLEŞTİR | cloudflared kopyaları | Ana tünel konteyneri + ölü host servisi (önceki rapor §3.4) + NetMovies'in tünel profili + ölü `big-bear-cloudflared-web` tanımı. Hedef: NetMovies'in kendi tüneli ("proje başına tünel" kuralı) + ana tünel. Host servisi ve ölü tanım gitsin. |
| BİRLEŞTİR | Portainer agent + Portainer Tools + Dockhand | Portainer yerelken agent gereksiz. Tools'un izi 03-28, Dockhand'inki 03-27 (yalnız anahtar). Beş yönetim aracı bire insin (~265 M). |
| BİRLEŞTİR | Prometheus + Grafana + coolify-sentinel ×2 | Config 04-06'dan beri değişmemiş, izlenen hedef doğrulanmadı. Beszel önerisiyle (§4) tek araca insin (~430 M). |
| KALDIR ADAYI | llama.cpp, quiz-bank-frontend, coolify-sentinel 1.0.1 | Exited/Created, 5 aydır çalışmıyor (önceki raporda da var). |
| KALDIR ADAYI | nexus-memora | 72 kez restart, her açılışta `apt-get update`; çalışmıyor. |
| KALDIR ADAYI | MinIO (big-bear) | İçinde yalnız `test` bucket'ı var (04-10). WeKnora'nın kendi MinIO'su ayrı. |
| KALDIR ADAYI | Node-RED | Son izi 03-29 `.config.runtime.json`; flow dosyası görülmedi. |
| KALDIR ADAYI | 37 ölü CasaOS tanımı + Pentest ve ZimaOS-AppStore(justserdar) mağazaları | Konteyneri yok. Uygulama ızgarasını ve mağaza güncellemesini kirletiyor. |
| KALDIR ADAYI | Tünel hostname'leri: db, it, api, test, prod, portal, dash | 502 veya 000. `db` postgres'i, `dash` ZimaOS yönetimini dışarı açmaya çalışıyor; ikisi de açılmamalı. |
| BELİRSİZ | Coolify v4 | %34 CPU, 313 M. Deploy ettiği uygulama doğrulanmadı. Kullanmıyorsan en büyük CPU kazancı bu. |
| BELİRSİZ | AdGuard Home | Port 53'ü tutuyor. Router DNS'i buna yönleniyorsa kaldırmak ev internetini keser; doğrulanmadı. |
| BELİRSİZ | Obsidian (web) | 774 M RAM ve sürekli %3 CPU, en pahalı tekil uygulama. Web'den kullanıyor musun? |
| BELİRSİZ | ESPHome, Mosquitto, InfluxDB, ev-telemetry-dashboard | ESP32 Dashboard (`pcd`) zinciri olabilir; son izler 04-12/14. |
| BELİRSİZ | n8n, code-server, Adminer, postgresql 17.4, big-bear-casaos-user-management | İz yok ya da yalnız 08-24 açılış izi var. Kimin kullandığı doğrulanmadı. |
| BELİRSİZ | Ollama | Yalnız llama3.2 var (04-04), compose tanımı `/tmp`'de kaybolmuş. Open WebUI'nin arka ucu olabilir. |
| BELİRSİZ | İş uygulamaları: call_center, flexcrm, modularcrm, anadolu-spor, randevu, sport-app, it-inventory-test, evaiteclabs, status_dashboard, eva-portal, volley, quiz_bank backend, apiflow, agentops, Nexus (mcp, bot, vault) | Dean'in projeleri; kaldırma önerilmez. Yalnız yarım olanlar sorulsun (volley backend Exited, quiz frontend). |

## 4. Mağaza önerileri (en fazla 8; hepsi kurulu mağazalarda var)

Kaynak maliyeti **ölçülmedi** (kurulmadı). Parantez içindeki değerler tipik değerlerdir, "doğrulanmadı".

1. **Uptime Kuma (zaten kurulu), yenisini kurma.**
   - Ne işe yarar: dış ve iç sağlık kontrolü, bildirim.
   - Yapılacak: `w.evaitec.com`, `http://netmovies-stream:3310/api/v1/health` (ya da `192.168.1.186:3310`), `port`/`admin`/`cloud` monitörlerini ekle. Mükerrer `my` monitörünü sil. Telegram bildirimi bağla: `/DATA/AppData/.vault_telegram_token` ve `nexusbot` var; token koda değil Kuma'nın ayarına girer.
   - Yerini aldığı araç: Healthchecks, Gatus (kurma).
   - Maliyet: 0 ek.
2. **Dozzle (zaten kurulu), yenisini kurma.**
   - Ne işe yarar: konteyner loglarını canlı okur.
   - Yapılacak: `DOZZLE_AUTH_PROVIDER=simple` + `users.yml` (parola dosyada, repoda değil). Ya da tünel hostname'ini kaldırıp yalnız LAN'da kullan.
   - Yerini aldığı araç: Portainer'ın log sekmesi.
   - Maliyet: 0 ek.
3. **Diun** (BigBear, `crazymax/diun:4.33.0`, UI yok).
   - Ne işe yarar: yeni imaj sürümü çıkınca bildirim atar, **güncelleme yapmaz**.
   - Yerini aldığı araç: Watchtower ve Tugtainer'ın otomatik güncellemesi.
   - Maliyet: (~20–30 M RAM, periyodik registry sorgusu; doğrulanmadı).
   - Telegram veya ntfy'ye bağlanır.
4. **Beszel** (BigBear, `henrygd/beszel:0.18.7`, port_map 8090).
   - Ne işe yarar: host CPU/RAM/disk/ağ ve konteyner başına kullanım, eşik uyarısı.
   - Yerini aldığı araç: Prometheus + Grafana + coolify-sentinel (~430 M), Glances, Netdata, Dashdot (kurma).
   - Maliyet: hub + agent (~50–80 M; doğrulanmadı).
   - **Port çakışması:** 8090'ı eva-portal (Homer) kullanıyor, host portu 8091 verilmeli.
5. **Kopia** (BigBear, `ghcr.io/thespad/kopia-server:v0.17.0`, 51515).
   - Ne işe yarar: şifreli, dedup'lu, zamanlanmış yedek. Hedef evaitec Azure Blob ya da ZimaOS'un bağlı Google Drive'ı (`/DATA/Backup/deancjxvr_google_drive_…` görünüyor, bağlama türü doğrulanmadı).
   - Yerini aldığı yöntem: 2026-04-02'deki elle `tar.gz` yedekleri. Şu an düzenli yedek yok.
   - Önce yedeklenecekler: `2fauth`, `nextcloud`, `homeassistant`, `netmovies/.env` + `data/netmovies.db`, iş uygulamalarının pgdata'ları. Postgres için tutarlı yedek `pg_dump` ile alınmalı; kopya dosya yetmez.
   - Maliyet: yedek sırasında CPU ve RAM (~150–300 M; doğrulanmadı); repo parolası `.env`'de.
   - Duplicati (resmi mağaza, 2.1.0) alternatif. Kopia CLI+UI tek imajda geldiği ve Azure'u doğrudan desteklediği için önde.
6. **Disk temizliği: yeni uygulama önerilmiyor.** Kurulu mağazalarda bu işe özel uygulama bulunamadı (grep boş). Portainer'ın "Unused images" görünümü ve önceki raporun §6/3–4 adımları yetiyor. Beszel'e disk eşiği (`/DATA` %80) uyarısı eklenir.
7. **Ters proxy: yeni uygulama önerilmiyor.** Cloudflare Tunnel hostname yönlendirmesini zaten yapıyor. NPM'in 2 ölü tanımı ve 2 imajı (önceki rapor §4) kaldırılsın. Caddy, Traefik ve NPMplus mağazada var ama kurulmasın.
8. **Watchtower ve otomatik güncelleyiciler: önerilmez.**
   - Watchtower kurulu mağazalarda yok; Tugtainer'da otomatik güncelleme var.
   - Risk: `:latest` ve `-dev` etiketli imajlar (`coolify:latest`, `librechat-dev:latest`, `open-webui:main`, `dockhand:latest`, `grafana:latest`, `influxdb:latest`) sessizce kırılabilir. `postgres` ana sürüm atlaması veri dizinini açılmaz hale getirir. NetMovies imajları yerel build olduğu için zaten güncellenmez.
   - Diun ile bildir, elle güncelle.

## 5. Sonraki oturum adımları (geri alınabilirden geri alınamaza)

Ön koşul: `ssh zima`, `export DOCKER_CONFIG=/DATA/AppData/.docker-dean`.

1. **Dozzle'ı kapat (güvenlik, ilk iş).** Cloudflare panelinde tünelden `dozzle.evaitec.com` hostname'ini kaldır ya da Access policy ekle. **Dean onayı.**
   Geri alma: hostname'i geri ekle.
   Doğrulama: `curl -s -o /dev/null -w "%{http_code}" https://dozzle.evaitec.com/api/events/stream --max-time 4` → 404 ya da 302 (`cloudflareaccess.com`), 200 değil.
2. **Ölü ve tehlikeli hostname'leri tünelden çıkar:** `db`, `dash`, `it`, `api`, `test`, `prod`, `portal`. **Dean onayı.**
   Doğrulama: `docker logs cloudflared 2>&1 | grep "Updated to new configuration" | tail -1` içinde bu adlar yok; `docker logs --since 10m cloudflared | grep -c "Unable to reach the origin"` → 0.
3. **Uptime Kuma'yı konuştur:** NetMovies ve kalan hostname monitörleri + Telegram bildirimi (§4.1). Arayüzden yapılır, geri alınabilir. **Dean onayı** (Telegram hedefi).
   Doğrulama: `sudo python3 -c "import sqlite3;c=sqlite3.connect('file:/var/lib/docker/volumes/uptime-kuma/_data/kuma.db?mode=ro',uri=True);print(c.execute('select count(*) from notification').fetchone(), [r for r in c.execute('select name,url from monitor')])"`.
4. **Konteyner yedeği al (Kopia kurulumundan önce, elle):** `sudo tar czf /DATA/Backup/pre-cleanup-$(date +%F).tar.gz /var/lib/casaos/apps /DATA/AppData/2fauth /DATA/AppData/homeassistant /DATA/AppData/hassio`.
   Doğrulama: `ls -la /DATA/Backup/` ve `tar tzf … | head`.
5. **Deneme süresi: durdur, silme.** Aday konteynerleri `docker stop` ile durdur ve 1 hafta bekle. Adaylar:
   - `nexus-memora`, `big-bear-minio`, `node-red`
   - `portainer-agent`, `portainer-tools-app-1`, `big-bear-dockhand`
   - `weknora*` (7 konteyner)
   - `ha_supervised` ve `hassio_*` (önce `hassio_supervisor-hassio_supervisor-1`)
   - Dean seçtikten sonra: bir Flowise ve bir chat arayüzü
   **Dean onayı.**
   Geri alma: `docker start <ad>`.
   Not: ZimaOS app-management boot'ta CasaOS uygulamalarını yeniden başlatabilir (doğrulanmadı). CasaOS uygulamalarını ZimaOS arayüzünden "Stop" ile durdurmak daha güvenli.
   Doğrulama: `docker ps --format '{{.Names}}' | wc -l` (72 → ~52) ve `free -h`.
6. **Diun kur** (BigBear mağazası, ZimaOS arayüzü). Bildirim hedefi Telegram; token env dosyasında olsun. **Dean onayı.**
   Doğrulama: `docker logs diun 2>&1 | grep -iE "found|notif" | tail`.
7. **Beszel kur** (BigBear), host portu **8091**. **Dean onayı.**
   Doğrulama: `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8091/` → 200; arayüzde ZimaOS agent'ı "up".
8. **Kopia kur** ve ilk yedeği al (hedef Azure Blob ya da Drive; parola `.env`'de, repoda değil). **Dean onayı.**
   Doğrulama: `docker exec kopia kopia snapshot list` en az 1 snapshot; bir dosyayı test geri yüklemesi.
9. **Prometheus, Grafana ve coolify-sentinel'i durdur** (Beszel 1 hafta sorunsuz çalıştıktan sonra). **Dean onayı.**
   Doğrulama: `docker ps | grep -cE "prometheus|grafana|sentinel"` → 0.
10. **Kalıcı kaldırma** (1 hafta sonra, adım 4 yedeği yerindeyken): 5. ve 9. adımda durdurulanlar ZimaOS arayüzünden "Uninstall" ya da `docker compose -p <proje> down` ile kaldırılır; `llma.cpp`, `quiz-bank-frontend`, `coolify-sentinel` (1.0.1) için `docker rm`. **Dean onayı (geri alınamaz; volume silme ayrıca sorulur).**
    Doğrulama: `docker ps -a --filter status=exited --filter status=created` boş, `docker system df`.
11. **37 ölü CasaOS tanımını kaldır:** `sudo mkdir -p /DATA/Backup/casaos-apps-stale && sudo mv /var/lib/casaos/apps/<klasör> /DATA/Backup/casaos-apps-stale/`. Taşıma kullanılıyor, silme değil. **Dean onayı.**
    Doğrulama: `sudo ls /var/lib/casaos/apps | wc -l` (63 → 26) ve `curl -s 127.0.0.1/v2/app_management/web/appgrid | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data']))"`.
12. **Kullanılmayan mağazaları çıkar** (Pentest-Docker, justserdar ZimaOS-AppStore): ZimaOS arayüzü → App Store → kaynakları yönet. **Dean onayı.**
    Doğrulama: `grep appstore /etc/casaos/app-management.conf`.
13. **BELİRSİZ listesi için Dean'e sorular:**
    - Coolify'ı kullanıyor musun (%34 CPU)?
    - Router DNS'i AdGuard'a mı yönleniyor?
    - Obsidian web'i kullanıyor musun (774 M)?
    - ESPHome, Mosquitto ve InfluxDB zinciri ESP32 Dashboard için mi?
    - n8n ve code-server kullanılıyor mu?
    - volley ve quiz_bank devam edecek mi?
    - Hangi chat arayüzü ve hangi Flowise kalsın?
    Cevaplara göre 5. ve 10. adımlar genişletilir.
14. **NetMovies tünelini ZimaOS'ta aç** (w.evaitec.com şu an buradan yayınlanmıyor): `docker compose --profile tunnel up -d` (hafıza notu: cloudflared stream'in netns'ine bağlı). Laptop'taki tünel kapatılmadan iki connector aynı tüneli paylaşır. **Dean onayı** (hangi makine yayınlasın).
    Doğrulama: `curl -s -o /dev/null -w "%{http_code}" https://w.evaitec.com/api/v1/health` → 200 ve `docker logs netmovies-tunnel | grep Registered`.
