## DEVİR — 2026-10-01 (EN GÜNCEL — tek geçerli blok)
Eski bloklar (USB/E:, D kopyası, 0.9.38 öncesi) bayat bilgi taşıdığı için arşivlendi:
`2026-10-01-0000-arsiv-onceki-latest.md`. Gerekçe zinciri gerekiyorsa oraya bak, durum için BURAYA.

**Kanıtlı durum (2026-10-01):**
- **Tek kopya `C:\projects\netmovies`** (NVMe). Stream mount'ları C (`data/`, `lists/`), health 200.
  D klonu silindi (oturum kilidindeki boş klasör kaldı). `E:\netmovies` (USB) takılı değildi —
  takılınca silinecek. Proje hafızası `~/.claude/projects/C--projects-netmovies/memory`'ye taşındı.
- **Aktif sunucu ZimaOS** (1 Ekim 14:31 açıldı; laptop yığını durduruldu, `.sunucu=yedek`; laptop DB/json/lists
  ZimaOS'a birleştirildi, Zima DB yedeği `data/netmovies.db.bak-*`). LAN 200, tünel 303.
- **ZimaOS diskleri (1 Ekim):** SanDisk 240 GB tek parça ext4 `sata` → `/DATA/sata` (yazma 385 / okuma 301 MB/s).
  Kingston A2000 500 GB (USB, JMS583) exFAT `A2000`, Windows+Linux (343 / 357 MB/s). A2000 SMART: 19 veri
  bütünlüğü hatası + 17 ani kapanma (test boyunca artmadı) — kritik veri koyma. Geçiş:
  `bash scripts/sunucu.sh durum | gec zima | gec laptop` (önce hedef ayağa, sonra kaynak durur — e338228).
  `gec` canlıda hiç çalıştırılmadı; Dean "geç" demeden çalıştırma. PROXY_TOKEN_SECRET iki sunucuda aynı olmalı.
- **0.9.41 yerel OTA'da** (`app_update` → v0.9.41-poc, 22.637.128 B): 5 dk ileri tampon (LoadControl 300 sn/160 MB),
  OkHttp + /proxy/ isteklerinde yeniden keşif, segment hatasında 6 deneme, telefonda 5 sütun.
  GitHub release + apps.json güncellemesi ve cihaz denemesi DOĞRULANMADI.

**Açık işler:**
1. Devam edenler gruplama: Türk dizisi · yabancı dizi · dublaj · film (Dean isteği, yapılmadı).
2. 0.9.41'in TV'de denenmesi; kesintisiz geçişin gerçek `gec` ile sınanması (izleme yokken).
3. Disk kararları Dean'de: F formatı, BM9C1 / A2000 yerleşimi (A2000 Opal kilitli → PSID revert).
4. ZimaOS ağı düzelince `D:\yedek` oraya. Laptop Ethernet 100 Mbps.

**Tekrarlanmayacak:** ikinci klon açma; Dean "yaz" demeden harici diske/ZimaOS'e veri yazma; izlerken rebuild yok;
yarım robocopy'yi sağlam sanma.
