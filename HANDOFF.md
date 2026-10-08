# Handoff: Samba paylaşımları + telefon sadeleştirme (0.9.62 yayında)
> 2026-10-08 17:30 · `fix/general-stability` @ `1ead029` (+ bu handoff commit'i; repo kodu değişmedi) · 3 kirli (`.claude/handoffs/latest.md`, `atv-kopru.log`, `scripts/yedek_reddet.py` — bu oturumun değil, dokunma)

## Goal
Reklamsız TV (client-tv, Mi Box) + ZimaOS sunucu. Kurallar `CLAUDE.md`; bitince sormadan üç yere yayınla (yerel OTA, GitHub release `--target`, evaglass `apps.json`). Mi Box durumu ve geri alma komutları: hafıza `mibox-adb-ve-sadelestirme.md`.

## State
- 0.9.59–0.9.62 üç yerde yayında (0.9.62 sha `03d91bf3…`, ses 0 kayıttan 9 başlar): gece "Tekrar dene" WOL saat engelini aşar; kart rozetleri sağ altta A/D; aramada OK klavyeyi açar; Lioness S3B8'de "LioNess" yemek kanalı açılmaz (engine `e2fc1cb`, canlı). Hepsi cihazda doğrulanmadı.
- TV Quick Actions erişilebilirlik servisi 10:34te NPE ile çöküp D-pad donmasına yol açtı → KAPATILDI (hiç erişilebilirlik servisi açık değil). Chromecast kaldırıldı.
- Mi Box S (192.168.1.105, MAC rezerve): ağdan ADB 5555 (laptop + Zima atv konteyneri yetkili). TV logcat → `zima:/DATA/AppData/netmovies/data/atv/tv.log` (`scripts/tv_log.sh`). Animasyon 0, ekran koruyucu kapalı, launcher Projectivy, klavye LeanKey, bloat + Chromecast kaldırıldı.
- Kayıtlar boş (9 kayıt silindi). Zima: eski OTA betaları + çift yedek silindi (`/DATA` 121 GB).
- Zima Samba (Dean onaylı, canlı): `/etc/samba/casa.conf` (yedek `.bak-20261008`) → `[recs]` kayıtlar anonim salt-okunur, `[shared]` /DATA/shared dean'e yazılır (apps/pictures/documents), `[apps]` /DATA/shared/apps anonim yazılır (TV 0.9.62 + Wear 0.1.19 APK içinde). Dean CX ile telefondan ve Mi Box'tan bağlandı. Hafıza `zima-samba-casa-conf`.
- LG SIMPLINK döngüsü 10:46'da hâlâ sürüyordu (kutu Vendor Id 00 00 00, rootsuz değişmez). Kutu `hdmi_control_auto_device_off_enabled=1`; kutu uyuyunca TV "sinyal yok"ta kalıyor — Standby gidiyor mu görülmedi.
- Dean'in telefonları (NetMovies dışı): S24 42 uygulama kaldırıldı, Fold 8 13 kaldırıldı + 22 devre dışı (sistem uygulaması rootsuz silinmiyor). Liste + geri alma: hafıza `telefon-sadelestirme`. Fold mikrofon çakışması = Hey Google HOTWORD (Dean kalsın dedi), Bixby kapatıldı.
- ZimaOS Plus başvurusu Gmail TASLAĞI (support@icewhale.org, kanıt eki var) — gönderilmedi; panel şifresi yok, ekran görüntüsü eklenemedi.

## Next
1. Kapatma: Dean cevaplamadı (TV ikinci basışta mı, kendiliğinden mi kapanıyor). Önce LG'de SIMPLINK + Otomatik Güç Senkronizasyonu açık mı; sonra Dean kutuyu kapatınca 1 dk içinde `adb -s 192.168.1.105:5555 shell dumpsys hdmi_control | grep -E "\] time" | tail -20` → `<Standby>` gidiyor mu.
2. Fold'da Samsung otomatik yeniden başlatma: ekran açıldı, Dean elle kuracak (04:00, her gün). Arka plan işlem sınırı İSTENMEDİ.
3. Onay bekleyen: Nextcloud cron/indeks; TV arama ızgara klavye (ref `halilozel1903/android-tv-search-keyboard`).
4. Air mouse: USB alıcı Mi Box'a (hub ile) takılmalı, LG'ye takılırsa yalnız LG'yi sürer.
5. Zima konteyner temizliği: Dean "değerlendiririz" dedi — kaldırma yok.

## Don't repeat
- Custom ROM/slimBOXtv: Mi Box S secure boot eFuse'ta, kurulamaz.
- Magic Remote imleci CEC'ten geçmez.
- Samba'yı `casa.dean.conf`/`smb.conf`'a yazma: ZimaOS açılışta yeniden üretir; yalnız `casa.conf`.
- Fold'da arka plan işlem sınırı: 5 seçeneği yok, yeniden başlatmada sıfırlanır, saat/bildirim öldürür — Dean vazgeçti.
- Masaüstü ekran görüntüsü kanıt değil (VS Code önde, kişisel içerik).
- tvQA 3.5.0 erişilebilirliği NPE ile çöküyor (D-pad donar) — güncellemeden açma. Projectivy erişilebilirliğini de açma; HOME zaten Projectivy (tvlauncher disabled).

## Verify
```
git rev-parse --short HEAD            # 7d30a84 (+ handoff commit)
curl -s 192.168.1.186:3310/api/v1/app_update?target=tv   # tag v0.9.62-poc
$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe connect 192.168.1.105:5555 && adb -s 192.168.1.105:5555 shell settings get secure enabled_accessibility_services   # boş (tvQA çöktüğü için kapalı)
ssh zima "tail -3 /DATA/AppData/netmovies/data/atv/tv.log"
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability @ 7d30a84. 8 Ekim oturumu: 0.9.59-0.9.62 (ses 9) yayında (WOL Tekrar dene, A/D rozet, arama klavyesi, Lioness YouTube düzeltmesi); Mi Box S sadeleştirildi (ağdan ADB 192.168.1.105:5555, tv.log Zima'da, Projectivy + LeanKey; tvQA kapalı); ZimaOS Plus başvurusu Gmail taslağında.
Önce HANDOFF.md oku, Verify bloğunu koş, hafıza mibox-adb-ve-sadelestirme.md'ye bak.
Sıra: 1) LG SIMPLINK sonucu (hdmi_control dökümü) 2) Dean onay verirse Samba/Nextcloud ya da ızgara klavye.
Yeni iş açma; onaysız Zima konteyneri silme.
```
