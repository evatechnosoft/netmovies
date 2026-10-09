# Handoff: 0.9.64 klavye seçimi + yedek reddet
> 2026-10-09 10:30 · `fix/general-stability` @ `0fea3ca` (+ bu handoff commit'i) · temiz

## Goal
Reklamsız TV (client-tv, Mi Box) + ZimaOS sunucu. Kurallar `CLAUDE.md`; bitince sormadan üç yere yayınla (yerel OTA, GitHub release `--target`, evaglass `apps.json`). Mi Box durumu ve geri alma komutları: hafıza `mibox-adb-ve-sadelestirme.md`.

## State
- 0.9.64 üç yerde (`0fea3ca`, sha `208418f0…`, GitHub + yerel OTA indirme sha eşleşti; ota-ayna 10 dk'da kendi çeker): aramada "Klavye:" hapları ABC (7x6) / QWERTY (Türkçe Q 12x4, 38 dp tuş, ' - .) / Sistem (ızgara gizli, alan odaklı, LeanKey; sonuçlar tam genişlik). Seçim prefs `netmovies_search/klavye`. Emülatörde (evabench_shot) üç mod + canlı arama "lioness" + D-pad odağı + kalıcılık görüldü; Mi Box'ta DOĞRULANMADI.
- `sunucu.sh`: laptop yedeğe geçince `yedek_reddet.py` gizli başlar (:3310 RST, TV anında yeniden keşfeder), laptop aktife geçerken durur; elle `sunucu.sh reddet`, `durum` sayıyı gösterir (penv'de 2 süreç = 1 örnek). Şu an laptopta çalışıyor (yeniden başlatmada kalkmaz — autostart'a eklenmedi).
- 0.9.63 üç yerde (`157f4db`, sha `517eeeb9…`, indirme sha eşleşti): aramada Türkçe ızgara klavye (`SearchKeys.kt`/`SearchKeyboard.kt`, 4 test), canlı arama 2+ karakter 600 ms. Cihazda/emülatörde DOĞRULANMADI (TV oynatıyordu; evabench_shot emülatörü açılışta kaldı). Sonuç ızgarası klavye yanında ~3 sütuna daralabilir — Dean'den bak.
- Nextcloud: compose'a /mnt/shared + /mnt/recs(ro), files_external ile dean'e bağlı, cron modu + `nextcloud-cron.timer` 5 dk. Web arayüzünde görülmedi.
- 0.9.59–0.9.62 üç yerde yayında (0.9.62 sha `03d91bf3…`, ses 0 kayıttan 9 başlar): gece "Tekrar dene" WOL saat engelini aşar; kart rozetleri sağ altta A/D; aramada OK klavyeyi açar; Lioness S3B8'de "LioNess" yemek kanalı açılmaz (engine `e2fc1cb`, canlı). Hepsi cihazda doğrulanmadı.
- TV Quick Actions erişilebilirlik servisi 10:34te NPE ile çöküp D-pad donmasına yol açtı → KAPATILDI (hiç erişilebilirlik servisi açık değil). Chromecast kaldırıldı.
- Mi Box S (192.168.1.105, MAC rezerve): ağdan ADB 5555 (laptop + Zima atv konteyneri yetkili). TV logcat → `zima:/DATA/AppData/netmovies/data/atv/tv.log` (`scripts/tv_log.sh`). Animasyon 0, ekran koruyucu kapalı, launcher Projectivy, klavye LeanKey, bloat + Chromecast kaldırıldı.
- Kayıtlar boş (9 kayıt silindi). Zima: eski OTA betaları + çift yedek silindi (`/DATA` 121 GB).
- Zima Samba (Dean onaylı, canlı): `/etc/samba/casa.conf` (yedek `.bak-20261008`) → `[recs]` kayıtlar anonim salt-okunur, `[shared]` /DATA/shared dean'e yazılır (apps/pictures/documents), `[apps]` /DATA/shared/apps anonim yazılır (TV 0.9.62 + Wear 0.1.19 APK içinde). Dean CX ile telefondan ve Mi Box'tan bağlandı. Hafıza `zima-samba-casa-conf`.
- LG SIMPLINK döngüsü 10:46'da hâlâ sürüyordu (kutu Vendor Id 00 00 00, rootsuz değişmez). Kutu `hdmi_control_auto_device_off_enabled=1`; kutu uyuyunca TV "sinyal yok"ta kalıyor — Standby gidiyor mu görülmedi.
- Dean'in telefonları (NetMovies dışı): S24 42 uygulama kaldırıldı, Fold 8 13 kaldırıldı + 22 devre dışı (sistem uygulaması rootsuz silinmiyor). Liste + geri alma: hafıza `telefon-sadelestirme`. Fold mikrofon çakışması = Hey Google HOTWORD (Dean kalsın dedi), Bixby kapatıldı.
- ZimaOS Plus başvurusu Gmail TASLAĞI (support@icewhale.org, kanıt eki var) — gönderilmedi; panel şifresi yok, ekran görüntüsü eklenemedi.

## Next
1. Zima konteyner temizliği: Dean "değerlendiririz" dedi — kaldırma yok.
(9 Ekim: Dean "kapatma işleri tamam, klavye de aynı" dedi — LG kapatma/SIMPLINK, Fold yeniden başlatma, air mouse, 0.9.63 klavye geri bildirimi KAPANDI; tekrar açma. Açık iş YOK, yeni iş Dean'den gelir.)

## Don't repeat
- Custom ROM/slimBOXtv: Mi Box S secure boot eFuse'ta, kurulamaz.
- Magic Remote imleci CEC'ten geçmez.
- Samba'yı `casa.dean.conf`/`smb.conf`'a yazma: ZimaOS açılışta yeniden üretir; yalnız `casa.conf`.
- Fold'da arka plan işlem sınırı: 5 seçeneği yok, yeniden başlatmada sıfırlanır, saat/bildirim öldürür — Dean vazgeçti.
- Masaüstü ekran görüntüsü kanıt değil (VS Code önde, kişisel içerik).
- tvQA 3.5.0 erişilebilirliği NPE ile çöküyor (D-pad donar) — güncellemeden açma. Projectivy erişilebilirliğini de açma; HOME zaten Projectivy (tvlauncher disabled).

## Verify
```
git rev-parse --short HEAD            # 0fea3ca (+ handoff commit)
curl -s 192.168.1.186:3310/api/v1/app_update?target=tv   # tag v0.9.64-poc
curl -s -m3 192.168.1.185:3310/ ; echo $?   # 56 (reset) = yedek reddet ayakta
$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe connect 192.168.1.105:5555 && adb -s 192.168.1.105:5555 shell settings get secure enabled_accessibility_services   # boş (tvQA çöktüğü için kapalı)
ssh zima "tail -3 /DATA/AppData/netmovies/data/atv/tv.log"
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability @ 0fea3ca. 9 Ekim: 0.9.64 aramada klavye seçimi (ABC/QWERTY/Sistem) yayında; yedek laptop :3310 RST reddet sunucu.sh'e bağlı. 8 Ekim oturumu: 0.9.59-0.9.62 (ses 9) yayında (WOL Tekrar dene, A/D rozet, arama klavyesi, Lioness YouTube düzeltmesi); Mi Box S sadeleştirildi (ağdan ADB 192.168.1.105:5555, tv.log Zima'da, Projectivy + LeanKey; tvQA kapalı); ZimaOS Plus başvurusu Gmail taslağında.
Önce HANDOFF.md oku, Verify bloğunu koş, hafıza mibox-adb-ve-sadelestirme.md'ye bak.
Sıra: 1) LG SIMPLINK sonucu (hdmi_control dökümü) 2) Dean onay verirse Samba/Nextcloud ya da ızgara klavye.
Yeni iş açma; onaysız Zima konteyneri silme.
```
