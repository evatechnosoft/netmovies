# Handoff: Mi Box sadeleştirme + 0.9.61 yayında
> 2026-10-08 10:50 · `fix/general-stability` @ `7d30a84` (push'lu) · 3 kirli (`.claude/handoffs/latest.md`, `atv-kopru.log`, `scripts/yedek_reddet.py` — bu oturumun değil, dokunma)

## Goal
Reklamsız TV (client-tv, Mi Box) + ZimaOS sunucu. Kurallar `CLAUDE.md`; bitince sormadan üç yere yayınla (yerel OTA, GitHub release `--target`, evaglass `apps.json`). Mi Box durumu ve geri alma komutları: hafıza `mibox-adb-ve-sadelestirme.md`.

## State
- 0.9.59–0.9.62 üç yerde yayında (0.9.62 sha `03d91bf3…`, ses 0 kayıttan 9 başlar): gece "Tekrar dene" WOL saat engelini aşar; kart rozetleri sağ altta A/D; aramada OK klavyeyi açar; Lioness S3B8'de "LioNess" yemek kanalı açılmaz (engine `e2fc1cb`, canlı). Hepsi cihazda doğrulanmadı.
- TV Quick Actions erişilebilirlik servisi 10:34te NPE ile çöküp D-pad donmasına yol açtı → KAPATILDI (hiç erişilebilirlik servisi açık değil). Chromecast kaldırıldı.
- Mi Box S (192.168.1.105, MAC rezerve): ağdan ADB 5555 (laptop + Zima atv konteyneri yetkili). TV logcat → `zima:/DATA/AppData/netmovies/data/atv/tv.log` (`scripts/tv_log.sh`). Animasyon 0, ekran koruyucu kapalı, launcher Projectivy, klavye LeanKey, bloat kaldırıldı, tek erişilebilirlik servisi TV Quick Actions (Projectivy'ninki kapatıldı).
- Kayıtlar boş (9 kayıt silindi). Zima: eski OTA betaları + çift yedek silindi (`/DATA` 121 GB).
- ZimaOS Plus başvurusu Gmail TASLAĞI (support@icewhale.org, kanıt eki var) — gönderilmedi; panel şifresi yok, ekran görüntüsü eklenemedi.

## Next
1. Dean'den LG SIMPLINK sonucu: `adb -s 192.168.1.105:5555 shell dumpsys hdmi_control | grep -E "\] time" | tail -6` — LG `Give Device Vendor Id` döngüsü (kutu 00 00 00) bitmiş mi.
2. Air mouse USB alıcısı + HDD: tek USB port → güçlü hub önerildi; "USB hata ayıklama açıkken alıcı çalışmıyor" iddiası doğrulanmadı.
3. Onay bekleyen işler (Dean "evet" demedi): Zima Samba `[Kayitlar]` salt-okunur + `[Paylasim]` + Nextcloud cron/indeks (systemd timer — auto-mode sınıflandırıcısı reddetti, Dean açık onay vermeli); TV arama ızgara klavye (Türkçe alfabe, sarmalı; ref `halilozel1903/android-tv-search-keyboard`); Chromecast (mediashell) kalsın mı.
4. Zima konteyner temizliği: envanter yapıldı (HA supervised, Coolify %42 CPU, çift Flowise vb.), Dean "kontrol eder değerlendiririz" dedi — kaldırma yok.

## Don't repeat
- Custom ROM/slimBOXtv: Mi Box S secure boot eFuse'ta, kurulamaz.
- Magic Remote imleci CEC'ten geçmez.
- Masaüstü ekran görüntüsü kanıt değil (VS Code önde, kişisel içerik).
- Projectivy erişilebilirliğini açma: tvQA ile çakışır; HOME zaten Projectivy (tvlauncher disabled).

## Verify
```
git rev-parse --short HEAD            # 7d30a84 (+ handoff commit)
curl -s 192.168.1.186:3310/api/v1/app_update?target=tv   # tag v0.9.62-poc
$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe connect 192.168.1.105:5555 && adb -s 192.168.1.105:5555 shell settings get secure enabled_accessibility_services   # yalnız tvquickactions
ssh zima "tail -3 /DATA/AppData/netmovies/data/atv/tv.log"
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability @ 7d30a84. 8 Ekim oturumu: 0.9.59-0.9.62 (ses 9) yayında (WOL Tekrar dene, A/D rozet, arama klavyesi, Lioness YouTube düzeltmesi); Mi Box S sadeleştirildi (ağdan ADB 192.168.1.105:5555, tv.log Zima'da, Projectivy + LeanKey + TV Quick Actions); ZimaOS Plus başvurusu Gmail taslağında.
Önce HANDOFF.md oku, Verify bloğunu koş, hafıza mibox-adb-ve-sadelestirme.md'ye bak.
Sıra: 1) LG SIMPLINK sonucu (hdmi_control dökümü) 2) Dean onay verirse Samba/Nextcloud ya da ızgara klavye.
Yeni iş açma; onaysız Zima konteyneri silme.
```
