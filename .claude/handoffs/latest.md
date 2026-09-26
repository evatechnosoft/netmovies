## 2026-09-26 gece oturumu — özet
- fffb554 stream: episodes_best aynı adlı eski yapımı eliyor (sezon boyu) · 73dc9b2 0.9.26 Bölümler paneli + telefon widget goAsync
- 40e3d27 0.9.27 telefon responsive (600dp) · 4481b44 0.9.28 telefon pad dokunma + saat 0.1.19 ✥ pad tek gesture
- 0da11af 0.9.29 dokun=TV'ye gönder, pad telefona sığar, /api/v1/show_schedule (Özet'te son/sıradaki bölüm)
- ae6508f 0.9.30 ilk açılış cihaz kipi (Televizyon/Kumanda/Bu cihazda izle) — TV'ler güncellemeden sonra bir kez OK ister
- Hepsi 3 yerde yayında; HİÇBİRİ cihazda doğrulanmadı (emülatör açılışta çöktü, Dean "boşver" dedi)
- ZimaOS: /etc/systemd/system/gece-kapanma.timer her gece 00:00 poweroff (overlay /mnt/overlay ext4, kalıcı). Sabah açılış BIOS'ta (Dean) — rtcwake "alarm: off", BIOS Auto-On doğrulanmadı.
- stream konteynerine following.py docker cp ile girdi; imaj rebuild'de commit'ten gelir.

# Handoff: ZimaOS'a taşıma (yarım) + LG /tv iki tur + DeanOS taraması
> 2026-09-25 · `fix/general-stability` @ `5289574` (push'lu) · kirli: `.claude/handoffs/*`, `.claude/worktrees/`, `atv-kopru.log`

## Goal
NetMovies sunucusunu laptoptan 7/24 ZimaOS'a (DeanOS) taşımak; LG webOS / Samsung Tizen `/tv` deneyimini Android TV seviyesine getirmek; DeanOS'u temizlemek.

## State — KANITLI
- **ZimaOS açıldı.** Siyah ekranın sebebi BIOS'ta SATA=RAID On'du, AHCI'ye alınınca açıldı. Makine: Precision 3551, i7-10850H, 30 GB, ZimaOS v1.6.0, /DATA 294 GB boş.
- ZimaOS IP **192.168.1.186**: nmcli ile statik, bağlantı adı "Supervisor eth0". `ssh zima` alias'ı .186'ya bakıyor, anahtar `~/.ssh/deanos`.
- **NetMovies ZimaOS'ta ayakta:** `/DATA/AppData/netmovies`, `.env` içinde `NM_NET=10.231.0` (172.31/16 orada dolu), `smoke.sh` YEŞİL. Kod o sırada `4d1cb5c`'deydi; ZimaOS 5289574 çekildi ve yeniden kuruldu. Yeniden başlatma sonrası 82/82 konteyner ayakta, health OK. Dozzle internete şifresiz açıktı: durduruldu (restart=no). mosquitto ve portainer-agent restart=unless-stopped yapıldı. /DATA/.bashrc dosyasına DOCKER_CONFIG eklendi (yedek: /DATA/AppData/.docker-dean/bashrc.bak).
- **Tünel (w.evaitec.com) hâlâ laptopta** ve 200 dönüyor. ZimaOS'ta cloudflared başlatılmadı; aynı token iki connector olur, başlatma.
- LG TV 192.168.1.175 (TP-Link ağında), kabuk 0.1.5 kurulu. `/tv` commit'leri: 264f8a7 günlük, 4d1cb5c kapanma/poster/tekerlek/tuş/7 poster, 5289574 pad/bilgi/dil/altyazı/açılışı geç.
- LG'de 41. sn kapanmanın kökü: IP başına 180/dk istek sınırı ve tüm evin tek NAT IP'si. `/proxy/video` ve `/proxy/subtitle` sınırdan muaf tutuldu.
- DeanOS taraması: `docs/ZIMAOS-DURUM.md`, 14 adım, 1. adım bitti.
- **2026-09-25 öğleden sonra (ZimaOS / Evaitec):**
  - **Evaitec tüneli** (1d2e8f02) ZimaOS'taki `cloudflared` konteynerinde çalışıyor. Ingress artık dosyadan yönetiliyor: `~/.ai/scripts/home-net/evaitec_tunnel.yml` + `evaitec_tunnel.py pull|diff|push`. Güncel sürüm v55, yedekler `evaitec_tunnel_backups/`. Ayrıntı hafızada: `evaitec-tunel-zimada`.
  - portal.evaitec.com → Homer :8090. Yeni tema/logolar: `/DATA/AppData/portal/assets/{config.yml,custom.css}`, yedek `config.yml.bak`. CSS `?v=2` ile yükleniyor (Cloudflare 4 sa cache).
  - Ölü rotalar (api, test, it, dozzle, db) kaldırıldı.
  - `cloudflared` ve `eva-portal` konteynerleri restart=unless-stopped yapıldı.
  - Host `cloudflared.service` (ayrı tünel 9162d6ae, binary yok) disable edildi.
  - **nexus-memora düzeldi:** compose'da katlanmış `command` bölünüyordu, liste formuna çevrildi. `MEMORA_HOST=0.0.0.0` ve `MEMORA_PORT=8080` eklendi. MCP `http://192.168.1.186:8081/mcp` initialize 200 döndü, `memories.db` oluştu. Graph `:8765/` 404 verdi, doğru yol doğrulanmadı. Henüz bağlı istemci yok.
  - **Kaldırılanlar:** yedekler `/DATA/backups/{pg,apps}` altında.
    - Postgres: agentops-nexus-db, apiflow-postgres, volley-db-1 + volume'ları, sport-app_postgres_data, shared içindeki `inventory` DB'si.
    - Uygulamalar: modularcrm, WeKnora, LibreChat (CasaOS app dizinleri dahil).
    - opik volume'ları (yedeksiz).
    - `image prune -a` ile 13,29 GB açıldı.
  - Eski "49 GB imaj + 16 GB cache" rakamı bayattı: prune öncesi geri kazanılabilir alan 0,8 GB, cache 0 B.

## Believed / doğrulanmadı
- LG'de renkli tuşlar, pad ve açılışı geç yalnız sahte tuş olaylarıyla denendi. AWOX kumandanın gerçekte hangi kodları gönderdiği bilinmiyor; `client_log`'daki `TUŞ kod=` satırlarına bak.
- LG'de altyazının ekranda göründüğü doğrulanmadı. Teşkilat (googlevideo) LG'de `HATA kod=4` veriyor.
- TP-Link rezervasyonu YAPILDI (38:14:28:35:9A:AE → .186, router şifresi Dean'de).

## Decisions
- Laptoptaki `.185` ile adres takası yerine ZimaOS `.186`'da kalır. TV'ler `ServerResolver` / kabuk adres listesiyle yeni adrese geçirilecek. Kabuk ADRESLER listesine `.186` eklenmeli.
- `/` %100 dolu görünmesi ZimaOS'ta normal (squashfs). Temizlik `/DATA` ve Docker üzerinde yapılır.

## Next
0. **SIRADAKİ TEK İŞ:** Portal'dan LibreChat kartını çıkar (`/DATA/AppData/portal/assets/config.yml`). Ardından Dean onaylarsa Nexus memora'yı Claude'a MCP olarak bağla (`http://192.168.1.186:8081/mcp`).
   - Açık: `deanfit` DB'si boş. Silmeden önce fit.evaitec.com verisinin nerede tutulduğu doğrulanmalı.
   - Açık: Coolify `failed_jobs` tablosunda 2045 kayıt var.
   - Güvenlik: 9162d6ae tünel token'ı chat'e düştü, döndürülmeli. `EVAITEC_CF_API_TOKEN_READ` aslında yazma yetkili, adı ya da yetkisi düzeltilmeli.
1. ZimaOS: `chain_scan.py --n 2` çalıştır.
2. **Tünel geçişi (Dean onayı):** laptopta cloudflared'ı durdur, ZimaOS'ta `--profile tunnel up -d` ile başlat, `w.evaitec.com` 200 dönmeli. Ardından istemci adresleri: webOS kabuk ADRESLER listesine `.186` (0.1.6), Android TV ServerResolver, Samsung `/tv` URL'si. Laptop stack'i en son durdurulur.
3. `docs/ZIMAOS-DURUM.md` adımları:
   - `DOCKER_CONFIG` ayarını `/DATA/.bashrc`'ye ekle.
   - Hafızada düz SSH parolası var, parolayı döndür. **Dean onayı**
4. LG'de gerçek kumandayla dene: renkli tuşlar, pad, Dark Matter "kaldığın bölüm", altyazı. Ardından `client_log` oku.
6. Uygulama envanteri: `docs/ZIMAOS-UYGULAMALAR.md` (TUT/BİRLEŞTİR/KALDIR + mağaza önerileri). Kurulum ve kaldırmalar Dean onayıyla.
7. Çalışma kökü: ZimaOS projeleri `/DATA/projects/` altında (apiflow-monitor-prod, evaiteclabs, it-inventory, quiz_bank, volley). NetMovies `/DATA/AppData/netmovies`'ten `/DATA/projects/netmovies`'e taşınacak: compose down, mv, up, smoke. Erişim bilgisi hafızada: ev-agi-erisim (vg.env HOME_*). TP ve Netmaster şifreleri vg.env içinde, ikisi de doğrulandı.
8. Uygulamalar: llama.cpp kaldırıldı (model dosyası yoktu, LLM için Ollama llama3.2 + Open WebUI kaldı). node-red durduruldu, flows yok, veri duruyor. quiz-bank frontend düzeltildi: backend'e `backend` alias verildi, :9310 200 dönüyor; `/api` 404, backend yolu doğrulanmadı.
9. Laptop `D:projects` (224 GB diskin 159 GB'ı dolu) eski projeleri tutuyor: codeplay, esp, ev, evaglass*, evaitec*, layers, Layersmig-pm, life-os-finance, sides, templates, test, tools. Her birini git remote durumuna göre sınıflandır: aktif → ZimaOS `/DATA/projects`, arşiv → yedekle ve sil (Dean onayı).
10. **KARAR (Dean, 2026-09-25): tüm projeler ZimaOS `/DATA/projects` altında toplanacak.** Kaynaklar laptopta `D:\MainProjects` (11 GB; Azure, claudex, ev_sound_simulator, Findtalent, ha-ops, ipfunc-wt, ITLayers, layers-API, LayersCX, layers-HRCenter, LayersProjects; `_migtmp` ve `_syncstage` geçici) ve `D:\projects` (madde 9). NetMovies de `/DATA/projects/netmovies`'e geçecek. Sıra: git'li olanları clone et, git'sizleri rsync/tar ile kopyala, laptoptaki kopyayı silme (Dean onayı).
11. Dark Matter S2B3 (Android TV'de izlenen, konum 2691 sn): Dean "1. sezon finali gibi" diyor. Bizim eşleme doğru: DiziYou ID'leri 93508 (S1E8), 93509 (S1E9), 93510–93513 (S2E1–E4) sıralı. İçeriğin sağlayıcıda yanlış yüklenip yüklenmediği doğrulanmadı; altyazı ilk satırları ya da süre karşılaştırılmalı, başka sağlayıcıyla (mavi tuş) çapraz kontrol edilmeli.
5. Açık işler: `/proxy/image` hâlâ 180/dk sınırında; Teşkilat googlevideo kaynağı LG'de açılmıyor (kod=4).

## Don't repeat
- Router şifresini dosyaya/hafızaya yazma (Dean verir).
- Evaitec tünel ingress'ini Cloudflare panelinden ya da elle PUT ile değiştirme; `evaitec_tunnel.py` kullan (yedek alır).
- ZimaOS'ta `systemctl status cloudflared*` çıktısı token'ı düz basar. Çıktıyı redakte et.
- Canlı veritabanlı uygulamayı `tar` ile yedeklemeden önce durdur (mongo "file changed" hatası).
- ZimaOS'ta `docker compose` için `DOCKER_CONFIG=/DATA/AppData/.docker-dean` ve `sudo -E` şart, yoksa `/DATA/.docker` izin hatası verir.
- Stream restart sonrası laptopta `docker compose --profile tunnel up -d --force-recreate cloudflared` şart, yoksa tünel 530 döner.

## Verify
curl -s 192.168.1.186:3310/api/v1/health; curl -s localhost:3310/api/v1/health; curl -s -o /dev/null -w "%{http_code}" https://w.evaitec.com/api/v1/health
ssh zima 'cd /DATA/AppData/netmovies && git log --oneline -1'

## <yeniden başlangıç> promptu
```
NetMovies (D:\projects\netmovies, fix/general-stability @ 5289574). ZimaOS 192.168.1.186'da NetMovies ayakta
(smoke YEŞİL), tünel hâlâ laptopta. Önce .claude/handoffs/latest.md ve docs/ZIMAOS-DURUM.md oku, Verify çalıştır.
Sıra: ZimaOS'u 5289574'e çek → tünel geçişi (onayla) → istemci adresleri .186 → DeanOS temizliği (onayla).
```
