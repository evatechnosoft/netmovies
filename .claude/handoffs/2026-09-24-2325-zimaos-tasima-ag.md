# Handoff: Ev ağı düzeni + Samsung (Tizen) + ZimaOS'a taşıma kararı
> 2026-09-24 · `fix/general-stability` @ `e85dcd4` (PUSH EDİLMEDİ: 7338ef1, 1679bcb, 16f93d9, e85dcd4) · kirli: `.claude/handoffs/*`, `.claude/worktrees/`, `atv-kopru.log`

## Goal
NetMovies'i tüm ekranlarda (Mi Box, LG webOS, Samsung M7 Tizen) kesintisiz izlemek; sunucuyu
Dean'in çalışma laptopundan ayrı, 7/24 açık ZimaOS makinesine taşımak. Plan: `docs/TIZEN-PLAN.md`, persona `.claude/agents/web-tv.md`.

## State — KANITLI
- Samsung 43" Smart Monitor M7 `192.168.1.184`, `/tv` tarayıcıdan çalışıyor (Dean: "çalışıyor, donma az, çok şık değil").
- Topoloji (tracert + iki modem UI): internet → Netmaster 192.168.0.1 (Huntercjx) → TP-Link MR200 v2 (WAN 0.11 / LAN 1.1, Deancjx). LG TV Huntercjx'te (0.63).
- **CGNAT:** Netmaster WAN `100.107.213.209`, dış IP `24.133.237.93` → dışa açma yalnız tünel.
- TP-Link (reload sonrası okundu): rezervasyon PC Wi-Fi `F0:77:C3:D5:DA:04`→.185, monitör `BC:45:5B:B1:6A:A8`→.184;
  DHCP havuzu 100–199; Virtual Server 3310→1.185:3310 (`curl 192.168.0.11:3310/api/v1/health` healthy);
  2.4 ch1/20M, 5G ch36/80M. Netmaster 2.4 ch6/20M (uptime o gün 5 sa, SNR 40.8 temiz).
- PC: Huntercjx "manuel bağlan", Deancjx öncelik 1. Wi-Fi günlüğü: 15:57–16:08 "driver disconnect".
- webOS kabuğu 0.1.5 (`0.11:3310` adayı) commit `e85dcd4`, ipk `client-webos/dist/…0.1.5_all.ipk` paketli.
- Bu laptop = Precision 3551, i7-10850H, 64 GB (Dean'in çalışma makinesi, silinmez).

## Believed / doğrulanmadı
- ZimaOS makinesi "aynı özelliklerde" (Dean'in sözü, ölçülmedi). Kapalı; eski not 1.186 zaman aşımı.
- 0.x cihazından 0.11:3310 (yalnız PC'den loopback). Deancjx düşme kök nedeni. TP-Link LAN 10/100 (spec).
- LG'ye 0.1.5 KURULMADI: 0.63:9922 connection refused (Dev Mode kapalı/süresi dolmuş).
- ZimaOS Plus: ücretsiz terfi 30 Haziran 2026'da bitti; Dean'in cihazında rozet var mı bakılmadı.

## Decisions
- Sunucu ZimaOS makinesine taşınır; laptop çalışma makinesi kalır.
- Geçişte adres takası: ZimaOS'a `.185` verilir, laptop başka adrese → istemcilerde değişiklik yok.
- Dışa açma: Cloudflare Tunnel yeter (Dean ev içi testleri port + tünel + custom domain ile yapıyor). VPS yok. Öneri: test alt alan adlarına Cloudflare Access.
- İşletim sistemi: ZimaOS Free işini görüyorsa kalır; Plus/salt-okunur kök engel olursa Debian + Docker + CasaOS.
- Netmaster Wi-Fi kapatılmaz (başka cihazlar üzerinde).

## Next
1. **Dean ZimaOS'u açıp TP-Link'e kablolayınca:** MAC/CPU/RAM/sürüm/Plus rozeti oku → rezervasyon → `/DATA/AppData/netmovies`'e depo + `.env` + veri → `docker compose up` paralel → `smoke.sh` + `chain_scan.py` yeşil → tünel geçişi → `.185` takası → TV'de film.
   7/24 laptop ayarı: kapak kapalı uyumasın (ZimaOS'ta logind ayarı yapılabilir mi doğrula), BIOS "Primarily AC Use" / %80 şarj.
2. LG Dev Mode açılınca 0.1.5 ipk'yı SSH yoluyla kur (reçete handoff `2026-09-22-1123`, host artık 192.168.0.63).
3. Sonra: `/tv` client_log (donma teşhisi) → `/tv` görünüm → Tizen tuşları (TIZEN-PLAN Faz 1).
4. Commit'leri push et.

## Don't repeat
- Router şifrelerini dosyaya/hafızaya yazma (Dean'de). Betikler scratchpad'de: `tp_act.py`, `tp_wifi.py`, `tp_res.py`, `nm_dump.py`.
- TP-Link select'leri gizli: `#_channel .select-box` + `li[data-val]` tıkla; jQuery `.val()` işlemiyor. Tek oturum: `#confirm-yes`.
- Laptopa ZimaOS kurmayı önerme; internetten port yönlendirme önerme (CGNAT).

## Verify
curl -s localhost:3310/api/v1/health; curl -s 192.168.0.11:3310/api/v1/health   # healthy x2
netsh wlan show interfaces | grep -E " SSID|Channel"                              # Deancjx, 36
git log --oneline -1                                                              # e85dcd4

## <yeniden başlangıç> promptu
```
NetMovies (D:\projects\netmovies, fix/general-stability @ e85dcd4, push bekliyor). Ev ağı düzenlendi
(TP-Link rezervasyon/havuz/kanal, 0.11:3310 yönlendirme; CGNAT var). Karar: sunucu ZimaOS makinesine
taşınacak, laptop çalışma makinesi kalır, geçişte .185 adres takası. Önce latest.md'yi oku, Verify'ı çalıştır.
Dean ZimaOS'u açınca taşıma adımlarına başla; LG Dev Mode açılırsa 0.1.5 ipk kur.
```
