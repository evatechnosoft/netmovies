# DeanOS (ZimaOS) — Durum, Temizlik ve Yol Planı

> Tarama: 2026-09-25 11:16–11:23 (sunucu saati +03). Salt-okunur; hiçbir şey silinmedi, durdurulmadı, değiştirilmedi.
>
> **Güncelleme 11:40:** Adım 1 yapıldı. NetMovies ZimaOS'ta `.env` `NM_NET=10.231.0` ile ayakta, `smoke.sh` YEŞİL (commit 3d6d209). IP 192.168.1.186 statik (nmcli); `.185` takası yerine `.186` seçildi. TP-Link rezervasyonu şifre kabul edilmediği için yapılamadı.
> Erişim: `ssh zima` (dean, uid 999, gruplar: samba, wheel, docker). Docker komutları `DOCKER_CONFIG=/DATA/AppData/.docker-dean` ile.
> Bu dosya commit edilmedi.

---

## 1. Geçmiş (ne yapıldı, nasıl ilerledi)

| Tarih | Olay | Kaynak |
|---|---|---|
| 2026-04-02 | İki elle alınmış tam yedek: `deanos_daily_backup_20260402_0825/0826.tar.gz` (2,5 + 2,8 GB) | `/DATA/Backup/` (ls) |
| 2026-04-03 | `ollama.service.d/override.conf` (`OLLAMA_HOST=0.0.0.0`), `gh` CLI `/DATA/bin`'e açıldı, `docker-config/` oluşturuldu | `/mnt/overlay/upper_etc/...` ve `/DATA/bin` (ls, tarih) |
| 2026-04-04 | Container haritası çıkarıldı (≈26 servis); nexus-brain restart döngüsü; BusyBox sed tuzağı; `DOCKER_CONFIG` izin uyarısı "ignorable" diye not edildi | `C:\Users\Deacjx\.ai\claude-rules-archive\memory-decisions-archive-20260414.md` §"ZimaOS Altyapı Bulguları" |
| 2026-04-04 | Node/Claude Code için Docker sarmalayıcı: `claude.sh`, `custom_profile.sh` (npm/npx alias, `DOCKER_CONFIG=/DATA/AppData/claude-config/.docker`), `claude-config/` | `/DATA/AppData/claude.sh`, `custom_profile.sh` (cat), `claude-config/` (ls, 4 Nisan) |
| 2026-04-06 | Dizin kuralı: paylaşımlı config `/DATA/AppData/`, proje işi `/DATA/projects/`; nexus-brain → `nexus` | aynı arşiv §"ZimaOS Dizin Yapısı" |
| 2026-04-07 | pip çözümü: pip'i volume'e yazdırma, imaja göm (startup 110 s → 1 s); `DOCKER_CONFIG=/tmp` ile build | `C:\Users\Deacjx\.claude\projects\C--\memory\feedback_zimaos-docker-pip.md`, `project_zimaos-environment.md` |
| 2026-04-08 | Nexus MCP v3.0.0 (8900), vault `/DATA/AppData/nexus/data/vault` | `C:\Users\Deacjx\.claude\projects\C--\memory\project_nexus-phase3.md` |
| 2026-04-12/14 | `npm-config/`, `python-config/` (ikisi de BOŞ), `docker_config/` oluşturuldu; `AppData/config/` (sahipsiz HA kurulumu) | ls tarihleri |
| — (tarih yok) | Gemini tarafı kuralı: `export DOCKER_CONFIG=/DATA/AppData/docker-config` + `NPM_CONFIG_USERCONFIG=/DATA/AppData/npm-config` | `C:\Users\Deacjx\.ai\tool-extras\gemini-added-memories-archive.md:11` |
| — | Kanonik kural: "`DOCKER_CONFIG=/DATA/AppData/docker-config` zorunlu", "Root dolarsa temizlik yap" | `C:\Users\Deacjx\.ai\contracts\zimaos-infrastructure.md` |
| 2026-04-21 | `/etc` overlay'e kalıcı değişiklik: `cloudflared.service` + enable, `systemd-networkd-wait-online` maskelendi | `/mnt/overlay/upper_etc/systemd/system` (ls, tarih) |
| 2026-07-06 | ZeroTier `362c178cf315e443` = IceWhale-RemoteAccess (Central'dan yönetilmez) | `C:\Users\Deacjx\.claude\projects\D--MainProjects-Azure\memory\rustdesk-zerotier-remote-access.md` |
| 2026-08-21 | Claude OTel yığınını ZimaOS'a taşıma planı (yapılmadı) | `C:\Users\Deacjx\.ai\guides\claude-code-otel-observability.md:78-100` |
| 2026-09-10 | ZimaOS erişilemez (ping/SSH yok) → "kendi işim için lokal Docker" kuralı; 4 Cloudflare tüneli, `evaiteclabs` 0 bağlantı | `...\D--projects\memory\kendi-isim-icin-lokal-docker.md`, `...\D--projects-evaglass\memory\cloudflared-container-per-project.md` |
| 2026-09-24 | Karar: NetMovies laptop'tan ZimaOS'a taşınır; geçişte ZimaOS'a `.185` verilir | `D:\projects\netmovies\.claude\handoffs\2026-09-24-2325-zimaos-tasima-ag.md` |
| 2026-09-25 | ZimaOS DHCP'de `192.168.1.103` görüldü (hafıza notu); bugün `Supervisor eth0` bağlantısına manuel `192.168.1.186/24`, gw `192.168.1.1`, DNS `192.168.1.1,1.1.1.1` | `...\D--projects-netmovies\memory\ev-agi-iki-modem.md`; `nmcli -g ipv4.* con show "Supervisor eth0"` |
| 2026-09-25 ~10:39 | Makine açıldı (uptime 37 dk @ 11:16); 11:14 `/DATA/AppData/netmovies` kopyalandı; ~11:22 `netmovies-engine`/`netmovies-stream` imajları build edildi | `uptime`, `ls -la`, `docker images` |

**Nerede kalındı:** Taşıma yarıda. İmajlar var, NetMovies konteyneri yok (11:23'te `docker ps -a | grep netmovies` boş, `curl localhost:3310/api/v1/health` cevapsız). `.185` takası yapılmadı; bugünkü adres `.186`.

---

## 2. Şu anki durum (komut çıktısından)

**Sistem** — ZimaOS v1.6.0 (`/etc/os-release`), 12 çekirdek, RAM 30 GiB (9,0 kullanılan, 21 GiB available), swap: zram 2 G + `/DATA/.swapfile` 10,2 G (kullanım 256 K).

**Disk yerleşimi** (`lsblk`, `/proc/mounts`):

| Bölüm | Boyut | Ne | Bağlama |
|---|---|---|---|
| p3 / p5 | 6 G / 6 G | squashfs rootfs A / B (RAUC) | p5 = `/` (ro) |
| p7 `casaos-overlay` | 96 M (%1) | kalıcı `/etc` üst katmanı, rauc, zerotier | `/mnt/overlay` |
| p8 `casaos-data` | 465 G | tüm veri | `/DATA`, `/var/lib/docker`, `/var/log`, `/opt`, `/media`, `/var/lib/casaos` … |
| tmpfs | 16 G | `/tmp` (4 K dolu), `/var` (3,5 M) | RAM |
| sysext overlay | — | `/usr` (ro, ZimaOS eklentileri) | |

`/DATA`: 457 G, 168 G dolu, 290 G boş (%37). `du -xh --max-depth=2 /` → yalnız **5,9 M**.

**Docker** (`docker info`, `docker system df`):
- Sürüm 27.5.1, overlay2, json-file log, Root Dir `/var/lib/docker` = **`/DATA/.docker`** (aynı inode 28049409, `stat`).
- 84 konteyner: 77 çalışıyor, 7 durmuş/oluşturulmuş. 116 imaj.

| Tür | Toplam | Aktif | Boyut | Geri kazanılabilir |
|---|---|---|---|---|
| Images | 116 | 76 | 101,5 GB | 49,32 GB |
| Containers | 84 | 78 | 5,1 GB | ~0 |
| Local Volumes | 32 | 13 | 1,85 GB | 1,52 GB |
| Build Cache | 369 | 0 | 16,18 GB | 16,18 GB |

`/DATA` en büyükler (`du -sh`): `.docker` overlay2 130 G · `AppData` 16 G · `.swapfile` 11 G · `Backup` 4,9 G · `.ota` 4,4 G · `.docker/containers` (loglar) 992 M · `.log` 397 M (journal 348 M).
(`du -sh /DATA/.docker` 193 G diyor; overlay "merged" bağlamalarını da saydığı için şişkin — `df` ve `docker system df` esas.)

**Konteynerler — compose projesine göre** (CPU/RAM: `docker stats --no-stream`, 11:18):

| Grup | Konteynerler | RAM toplamı (yaklaşık) | Not |
|---|---|---|---|
| Home Assistant ×2 | `ha` (host ağı, 2025.11) 557 M · `ha_supervised` (8124) 438 M · `hassio_supervisor` 209 M · `hassio_cli/dns/multicast/observer` · `hassio_audio` (Exited 0) | ~1,2 G | **İki ayrı HA**; supervisor yığını `supervisor-firewall-gateway.service` failed |
| AI/LLM | open-webui (`c5899…`, 8080) 966 M · `ollama-ollama-1` 50 M · `librechat` ×5 ~537 M · `flowise` (3.0.11, 3025) 450 M · `big-bear-flowise` (3.1.2, 8009) 545 M · `weknora` ×7 ~480 M · `llma.cpp` (Exited 1, 5 ay) | ~3 G | **İki Flowise**; ollama projesi `tmp` (`/tmp/nexus-others.yml`, dosya artık yok) |
| Nexus | `nexus-mcp` 49 M · `nexusbot` 24 M · `nexus-vault` 166 M · `nexus-memora` **Restarting, 52 kez** (`sh -c apt-get update`) · `agentops-nexus-db` 17 M · `prometheus` 93 M · `grafana` 289 M | ~640 M | memora döngüde |
| PaaS/yönetim | `coolify-v4` **%91 → %29 CPU**, 367 M · `coolify-v4-db/redis` · `coolify-sentinel-1` (0.0.21) + `coolify-sentinel` (1.0.1, Created) · `portainer` + `portainer-agent` + `portainer-tools` · `big-bear-dockhand` 135 M · `dozzle` · `code-server` · `adminer` · `big-bear-casaos-user-management` | ~1 G | Coolify en çok CPU yiyen; 4 ayrı konteyner yönetim aracı |
| İş uygulamaları | `anadolu-spor` ×3 · `call_center` ×3 · `flexcrm` ×3 · `modularcrm` ×4 (rabbitmq 131 M) · `randevu-container` · `sportapp-backend` · `apiflow-postgres` · `quiz-bank-backend_old` (+ `quiz-bank-frontend` Exited 1, 5 ay) · `volley` (backend Exited 1, frontend Created, db Up) · `it-inventory-test` · `evaiteclabs` · `ev-dashboard` · `status-dashboard` · `eva-portal` | ~1,2 G | quiz/volley yarım |
| Ev/IoT | `esphome` · `mosquitto` · `node-red` · `influxdb` 160 M · `adguard` | ~400 M | |
| Diğer | `nextcloud` 159 M · `obsidian` 773 M (log 274 M) · `big-bear-minio` · `2fauth` · `uptime-kuma` · `postgresql` (17.4) · `cloudflared` (wisdomsky web, 97 M) | ~1,5 G | |

Cloudflared: konteyner olarak **1 adet** (`cloudflared`). Ayrıca host'ta `/etc` overlay'inde `cloudflared.service` etkin ama `/usr/bin/cloudflared` yok → **510 kez yeniden deneme**, `status=203/EXEC` (`systemctl show`, `journalctl -u cloudflared`).

**/DATA/AppData** (`du -sh`, 60+ klasör): nextcloud 3,2 G · netmovies 2,5 G · esphome 2,5 G · ollama 2,2 G · open-webui 890 M · big-bear-open-webui 889 M · librechat 529 M · coolify-v4 408 M · anadolu-spor 405 M · nexus 382 M · flexcrm 314 M · … (tam liste §4).

**Yönlendirmeler (env / rc):**
- `/etc/profile.d/zimaos-home.sh`: `HOME=/DATA`. `mc-home.sh`: `MC_PROFILE_ROOT=/tmp/MCHOME`. Başka `TMPDIR/PIP_/NPM_/DOCKER_*/XDG_` yok (`env` çıktısı yalnız `HOME` ve `PATH=/usr/bin:/usr/sbin`).
- `/DATA/.bashrc` ve `.bash_profile`: yalnız PATH/alias/prompt; `custom_profile.sh`, `claude.sh`, `nexus-init.sh` **hiçbir yerden source edilmiyor** (grep boş).
- Host'ta `pip3`, `npm`, `node` yok; `python3` ve `git` var (`which`).
- `/etc/docker/daemon.json`: `registry-mirrors` = daocloud / dockerproxy / baidubce, `dns` 1.1.1.1/8.8.8.8, `mtu 1400`.
- DOCKER_CONFIG adayı **6 klasör**: `docker-config/`, `docker_config/`, `.docker/`, `.docker-dean/`, `claude-config/.docker/`, `nexus-configs/docker` (script'te). Hepsi yalnız buildx durumu tutuyor.

**Güç / ağ:**
- `HandleLidSwitch=ignore`, `HandleLidSwitchDocked=ignore` (`systemd-analyze cat-config`); `/etc` üst katmanında logind dosyası yok → ZimaOS varsayılanı. Kapak `open`; `sleep/suspend.target` inactive. BIOS güç ayarı doğrulanmadı.
- `eth0 192.168.1.186/24` statik, varsayılan rota `192.168.1.1`. `wlan0` DOWN. `hassio` 172.30.32.1/23. ZeroTier `ztxlpqe2fq` 10.246.0.1/16 = **IceWhale-RemoteAccess** (`zerotier-cli listnetworks`). `virbr0`, `thunderboltbr0` down.
- RAUC: slot B (kernel.1/rootfs.1) boot, good; **slot A `boot status: bad`**. `/DATA/.ota/online/` içinde 1.7.0, 1.7.0-beta2, 1.8.0-beta1 indirilmiş (her biri 1,5 G), `release.json` 1.6.1 duyuruyor — ama çalışan sürüm 1.6.0.

**NetMovies** (dokunulmadı): `/DATA/AppData/netmovies` (2,5 G, `.env` + `data/netmovies.db` mevcut), imajlar `netmovies-engine` 524 MB, `netmovies-stream` 317 MB; konteyner yok, 3310 cevap vermiyor (11:23). `caomingjun/warp:latest` (3 hafta, 1,03 GB) imajı var ama konteyneri yok — NetMovies yığınının parçası, temizlik dışı.

---

## 3. Sorunlar

1. **`/` %100 dolu — sorun DEĞİL.** `/` = `/dev/nvme0n1p5` squashfs, `ro` (`/proc/mounts`). Squashfs'in boş alanı yoktur, `df` her zaman %100 gösterir. Yazılabilir yerler: `/etc` (p7 overlay, %1), `/var` ve `/tmp` (tmpfs), gerisi p8'e bind. Kanonik not "Root dolarsa sistem read-only'ye geçer — temizlik yap" (`contracts/zimaos-infrastructure.md`) bu yapıda **yanlış**; düzeltilmeli. Asıl izlenecek: `/DATA` (%37) ve p7 overlay (85 M).
2. **`/DATA/.docker/config.json: permission denied` kök nedeni:** `HOME=/DATA` → docker CLI varsayılanı `$HOME/.docker` = `/DATA/.docker` = **Docker'ın kendi data-root'u** (aynı inode), `drwx--x--- root`. dean (uid 999) okuyamaz. Yani uyarı "ignorable" değil, yol çakışması; `DOCKER_CONFIG` ile başka yere almak doğru çözüm.
3. **DOCKER_CONFIG dağınık:** 4 farklı not 4 farklı yol söylüyor (`docker-config`, `/tmp`, `claude-config/.docker`, bugün `.docker-dean`). Hiçbiri shell'e kalıcı bağlı değil.
4. **Host `cloudflared.service` ölü döngüde** (binary yok, 510 restart) ve **tünel token'ı düz metin** olarak `/etc/systemd/system/cloudflared.service` içinde (kalıcı overlay). Token değeri bu rapora yazılmadı.
5. **`nexus-memora` restart döngüsü** (52), her başlangıçta `apt-get update` — 2026-04-07 pip dersinin aynısı.
6. **Coolify yüksek CPU** (anlık %91, sonra %29; `horizon:work`). Kullanılıyor mu doğrulanmadı.
7. **İki Home Assistant** (`ha` host ağında + supervised yığın 8124) ve `supervisor-firewall-gateway.service` failed.
8. **Ollama compose tanımı kayıp:** proje `tmp`, config `/tmp/nexus-others.yml` — tmpfs, reboot'ta silinmiş. Yeniden oluşturmak gerekirse tanım yok.
9. **RAUC slot A "bad"** + indirilmiş ama kurulmamış 1.7.0/1.8.0-beta OTA'lar. Güncelleme denenip başarısız olmuş olabilir — doğrulanmadı.
10. **daemon.json'da Çin registry aynaları** — kim ekledi doğrulanmadı; Türkiye'den yavaş/güvenilmez olabilir.
11. **Hafıza notunda düz metin SSH parolası:** `C:\Users\Deacjx\.ai\claude-rules-archive\memory-decisions-archive-20260414.md` (§"ZimaOS Docker Container Haritası" başlığı altı). Anahtarlı erişim var; parola notu kaldırılmalı / parola döndürülmeli.
12. **Adres kararı çatışması:** handoff "ZimaOS'a `.185`" diyor, bugün `.186` statik verildi. Ayrıca statik adres TP-Link DHCP havuzunun (100–199) **içinde** — rezervasyon yoksa çakışma riski.
13. Konteynerlerin çoğu `restart=no` (`nexus-mcp`, `ollama`, `nexusbot`, `randevu`, `evaiteclabs`) ama boot sonrası ayakta — ZimaOS app-management başlatıyor olabilir; doğrulanmadı.

---

## 4. Temizlik adayları (yalnız öneri — hiçbiri yapılmadı)

Boyutlar paylaşılan katmanlar yüzünden toplanamaz; gerçek kazanç `docker system df` "Reclaimable" sütunudur (imaj 49,3 G + build cache 16,2 G + volume 1,5 G).

| Öğe | Boyut | Risk | Gerekçe |
|---|---|---|---|
| Build cache (369 kayıt, 0 aktif) | 16,18 GB | Düşük | Sonraki build yavaşlar, veri kaybı yok. NetMovies build'i bitince. |
| `ghcr.io/open-webui/open-webui:ollama` (kullanılmıyor) | 9,74 GB | Düşük | Çalışan open-webui `:main` |
| `open-webui:git-33e54a9` (22 ay) | 4,22 GB | Düşük | eski |
| `mintplexlabs/anythingllm` + `AppData/anythingllm` (8 K) | 3,27 GB | Düşük | konteyneri yok |
| `flowiseai/flowise:3.1.1` | 3,22 GB | Düşük | 3.0.11 ve 3.1.2 çalışıyor |
| `linuxserver/chrome:145…` | 3,13 GB | Düşük | konteyneri yok |
| `myoung34/github-runner` | 2,37 GB | Düşük | konteyneri yok (`AppData/nexus-runner` 8 K) |
| HA eski imajları: `generic-x86-64-homeassistant:2026.4.2`, `home-assistant:stable`, `:2025.7`, `ghcr…:2025.3.1` | 8,4 GB | Düşük | çalışan: `homeassistant/home-assistant:2025.11` + `ghcr…:stable` |
| `ollama/ollama:0.9.5` | 2,27 GB | Düşük | çalışan `ollama:latest` |
| `nextcloud:31.0` | 1,44 GB | Düşük | çalışan 32.0 |
| Opik yığını imajları (python-backend, backend, frontend) + `clickhouse`, `zookeeper`, `mysql:8.4.2`, `minio:2025-03`, `mc`, `redis:7.2.4` | ~4,4 GB | Düşük | opik konteyneri yok |
| Opik volume'leri (`opik-opik_clickhouse`, `_clickhouse-server`, `_mysql`, `_zookeeper`, `_minio-data`, `_redis-data`, `_clickhouse-config`) | ~1,43 GB | Orta | veri silinir; opik geri istenmeyecekse |
| 11 anonim dangling volume + `sport-app_postgres_data` | ~50 K | Düşük | boş |
| `jc21/nginx-proxy-manager` ×2 | 2,26 GB | Düşük | konteyneri yok |
| `code-server:4.114.1`, `4.109.2`; `n8n:1.84.0`; `grafana:12.4.2`; `adminer:4.8.1`; `cloudflared-web:2026.3.0`; `cloudflare/cloudflared:2026.1.2`; `sentinel:0.0.22` | ~3,3 GB | Düşük | yenisi çalışıyor |
| `nexus-brain-nexus-brain`, `agentops-nexus-nexus-web`, `it-inventory:latest`, `codex.docs`, `node:22-slim`, `node:20-alpine`, `python:3-slim`, `python:3.12-slim`, `linuxserver/python`, `alpine` | ~2,4 GB | Orta | taban imajlar tekrar build'de lazım olabilir (python:3.12-slim → nexus-mcp) |
| Durmuş: `llma.cpp` (Exited 1, 5 ay), `quiz-bank-frontend` (Exited 1, 5 ay), `coolify-sentinel` 1.0.1 (Created) | küçük | Düşük | hiç çalışmıyor |
| Durmuş: `volley-backend-1` (Exited 1), `volley-frontend-1` (Created), `hassio_audio` (Exited 0) | küçük | Orta | volley yarım proje; hassio_audio supervisor'ın parçası |
| `nexus-memora` (döngüde) | — | Orta | ya imaja göm ya durdur; veri `AppData/nexus-memora` 12 K |
| `/DATA/.ota/online/1.7.0`, `1.7.0-beta2`, `1.8.0-beta1` | 4,5 GB | Orta | OTA önbelleği; güncelleme kararı verilmeden silme |
| `/DATA/Backup/deanos_daily_backup_20260402_*.tar.gz` ×2 | 5,2 GB | **Yüksek** | 6 aylık tek yedek; önce başka yere kopya |
| `AppData/big-bear-open-webui` (mount yok) | 889 MB | Orta | çalışan open-webui `AppData/open-webui`'yi kullanıyor |
| `AppData/config` (sahipsiz HA kurulumu, 12 Nisan) | 364 K | Düşük | hiçbir konteyner bağlamıyor |
| Mount'suz küçük klasörler: `agentops-nexus`, `anythingllm`, `applications`, `big-bear-cloudflared-web`, `big-bear-codex-docs`, `file-editor`, `mqtt`, `nexus-registry`, `nexus-runner`, `nodered`, `redis`, `vault`, `zigbee2mqtt` | <1 MB toplam | Düşük | ölü uygulama verisi adayı; içerik tek tek bakılmadı |
| DOCKER_CONFIG kopyaları: `docker-config`, `docker_config`, `.docker`, `claude-config/.docker` | <1 MB | Düşük | tek yola indirildikten sonra |
| `AppData/claude-config` (32 M, Ollama'lı Claude denemesi) + `claude.sh`, `custom_profile.sh` | 32 MB | Düşük | kullanılmıyor (source edilmiyor, Ollama 11434 değil 4602/iç ağ) |
| Konteyner logları: obsidian 274 M, nexus-mcp 187 M, coolify 96 M | ~560 MB | Düşük | log rotasyonu yok (daemon.json'da `log-opts` yok) |
| Host `cloudflared.service` + enable linki | — | Orta | binary yok, token düz metin; konteyner `cloudflared` tüneli taşıyor mu önce doğrula |

**Yinelenen/aday servisler (kullanım Dean'e sorulmalı):** 2× HA, 2× Flowise (3025/8009), open-webui + LibreChat + WeKnora + Flowise (4 LLM arayüzü), Portainer + Dockhand + Portainer-tools + Dozzle + Coolify (5 yönetim aracı), `postgresql` 17.4 bağımsız + 8 ayrı postgres. Silmek değil, "hangisini kullanıyorsun" sorusu.

---

## 5. Yol yönlendirme planı

| Ne | Mevcut (kanıt) | Önerilen |
|---|---|---|
| Docker data-root | `/var/lib/docker` → bind `/DATA/.docker` (p8) | Aynı kalsın. Taşımaya gerek yok. |
| Docker CLI config | `HOME=/DATA` → `/DATA/.docker` = data-root, izin hatası. 6 dağınık klasör. | Tek yol: `/DATA/AppData/.docker-dean` (bugün kullanılan, dean sahibi). `/DATA/.bashrc`'ye `export DOCKER_CONFIG=/DATA/AppData/.docker-dean`. Kanonik notu güncelle. |
| TMP | `/tmp` tmpfs 16 G (RAM), reboot'ta silinir. `DOCKER_CONFIG=/tmp` ve `/tmp/nexus-others.yml` bu yüzden kayboldu. | Kısa ömürlü iş `/tmp`'de kalabilir. Kalıcı olması gereken compose/config **asla** `/tmp`'de değil: `/DATA/AppData/<app>/`. İstenirse `TMPDIR=/DATA/tmp` yalnız büyük build'ler için (RAM korunur). |
| pip | Host'ta pip yok. Konteyner içi: imaja göm (2026-04-07 dersi). `python-config/` boş, `nexus-configs/pip` script'i çalışmamış. | Host'a pip kurma. Kural: `Dockerfile`'da `RUN pip install`. Boş `python-config/` ve `nexus-init.sh` emekli. |
| npm / node | Host'ta yok. `custom_profile.sh` alias'ı `npm-global/` kullanıyor ama klasör yok, dosya source edilmiyor. `npm-config/` boş. | `node:20-slim` sarmalayıcı kuralı kalsın; gerekiyorsa `custom_profile.sh`'ı `.bashrc`'den source et ve volume'ü `/DATA/AppData/npm-global` olarak oluştur. Kullanılmıyorsa emekli. |
| HOME | `/DATA` (ZimaOS varsayılanı) | Dokunma. |
| journald / docker log | `/var/log` → p8, journal 348 M; konteyner loglarında rotasyon yok | daemon.json'a `"log-opts": {"max-size":"20m","max-file":"3"}` (yalnız yeni konteynerlere etki eder). |
| Kalıcı sistem ayarı | `/etc` overlay p7 (85 M) | Küçük tut; yalnız systemd override/unit. |

---

## 6. Sıradaki oturumun adımları

1. **NetMovies'i bitir (öncelik).** `cd /DATA/AppData/netmovies && DOCKER_CONFIG=/DATA/AppData/.docker-dean docker compose ps`; ayakta değilse `docker compose up -d`.
   Doğrulama: `curl -s localhost:3310/api/v1/health` → healthy; `bash scripts/smoke.sh`.
2. **DOCKER_CONFIG'i tekle.** `/DATA/.bashrc` sonuna `export DOCKER_CONFIG=/DATA/AppData/.docker-dean`.
   Doğrulama: yeni SSH'ta `docker ps >/dev/null && echo ok` uyarısız. Kanonik `contracts/zimaos-infrastructure.md`'yi aynı yolla güncelle; "root dolarsa" satırını squashfs gerçeğiyle değiştir.
3. **Build cache temizliği** (NetMovies build'i bittikten sonra): `docker builder prune -f`. **Dean onayı.**
   Doğrulama: `docker system df` → Build Cache ≈ 0.
4. **Kullanılmayan imajlar:** önce liste `docker image ls` + §4 tablosu; Dean'le işaretle, sonra `docker image rm <id>` tek tek (`prune -a` değil; NetMovies `warp` imajı listede kalsın). **Dean onayı.**
   Doğrulama: `docker system df` Images reclaimable düşüşü; `docker ps` sayısı değişmedi (77+).
5. **Durmuş konteynerler:** `llma.cpp`, `quiz-bank-frontend`, `coolify-sentinel`(Created) → `docker rm`. **Dean onayı.** Doğrulama: `docker ps -a --filter status=exited`.
6. **Opik volume'leri** (1,4 G): Dean "opik gerekmez" derse `docker volume rm opik-opik_*`. **Dean onayı (geri alınamaz).** Doğrulama: `docker volume ls -qf dangling=true | wc -l`.
7. **Host cloudflared.service:** önce konteyner `cloudflared` hangi tüneli taşıyor bak (Cloudflare panel/API, `docker logs cloudflared | grep -i registered`). Aynı tünelse `sudo systemctl disable --now cloudflared` ve unit dosyasını kaldır; token'ı döndür. **Dean onayı.** Doğrulama: `systemctl show cloudflared -p NRestarts` artmıyor; tünel sağlıklı.
8. **nexus-memora döngüsü:** `docker logs nexus-memora` son hata → ya imaja göm ya `docker stop`. Doğrulama: `docker inspect -f '{{.RestartCount}}' nexus-memora` sabit.
9. **Ollama compose tanımını kalıcılaştır:** `docker inspect ollama-ollama-1` → `/DATA/AppData/ollama/docker-compose.yml` yaz (yalnız dosya; konteyneri yeniden yaratma ayrı karar). Doğrulama: `docker compose -f … config` hatasız.
10. **Yinelenenler için Dean'e soru listesi:** 2× HA, 2× Flowise, Coolify (CPU), 4 LLM arayüzü, 5 yönetim aracı. Kapatılacaklar `docker stop` ile önce 1 hafta bekletilir, sonra kaldırılır. **Dean onayı.** Doğrulama: `free -h`, `docker stats --no-stream`.
11. **Log rotasyonu:** `/etc/docker/daemon.json`'a `log-opts` ekle; Çin aynalarını kaldırmayı değerlendir; `sudo systemctl restart docker` (**tüm konteynerler yeniden başlar — Dean onayı**). Doğrulama: `docker info | grep -A3 "Registry Mirrors"`, `docker ps` sayısı.
12. **Ağ adresi kararı:** `.185` takası mı `.186` mı? Seçilen adres TP-Link'te rezervasyon (MAC `38:14:28:35:9A:AE`) ya da havuz dışına alınmalı. Doğrulama: TP-Link DHCP tablosu + `ip -br addr show eth0`.
13. **Yedek + OTA:** 2026-04 yedeklerini PC'ye/Drive'a kopyala, sonra sil; RAUC slot A "bad" nedenini `sudo rauc status --detailed` ve ZimaOS ayarlarından oku, 1.6.1/1.7.0 güncellemesine karar ver; sonra `.ota/online` temizliği. **Dean onayı.** Doğrulama: `df -h /DATA`, `rauc status`.
14. **Hafıza hijyeni:** arşiv notundaki düz SSH parolasını kaldır, parolayı döndür; `project_zimaos-environment.md` ve `feedback_zimaos-docker-pip.md` içindeki `DOCKER_CONFIG=/tmp` tavsiyesini yeni yolla güncelle.
