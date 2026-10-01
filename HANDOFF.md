# Handoff: TV 0.9.44 yayında, A.Ü.İ arşiv tasarımı açık
> 2026-10-01 · `fix/general-stability` @ `2180c74` (push'lu) · kirli: yalnız `atv-kopru.log`, `scripts/yedek_reddet.py` (izlenmeyen, bu oturumun değil)

## Goal
Reklamsız TV uygulaması (client-tv, Mi Box) — Dean'in bildirdiği kusurları düzelt, bitince sorma yayınla (üç yer: yerel OTA, GitHub release, apps.json).

## State
- 0.9.44 yayında (`v0.9.44-poc`), cihazda doğrulanmadı: telefon→TV `key(url)`, ses hafızası (`data/SesHafizasi.kt`), pad'de 3 liste ikonu + odak dönüşü (`HomeScreen.kt` `PosterMenu`).
- D→C taşıma bitti: ev D'den silindi; evaitec C'ye eşit (`robocopy /L` Copied=0), Finance Radar görevleri C yolunda, `_wt-*` + `dashboard-ui-1b` worktree'leri `git worktree repair` ile C'ye bağlı. `D:\projects\evaitec`'te yalnız açık oturumların kilitlediği artık klasörler (`lifeOS\...`, `_wt-*`) kaldı.
- Kapanan sorular: R.J. Decker kaydı → Dean "boşver". Teşkilat 186 → yayınlanan son bölüm zaten 186 (B187 Pazar). Altı Üstü İstanbul 15 → iki kaynakta da 15; B16 12 Ekim'de (web). Resident Evil 2026 filmi DiziPal'de var ve oynuyor (yalnız İngilizce ses); Devam Et'teki "Resident Evil" 2022 dizisi (DiziBox) — karışıklık oradan.

## Next
1. Dean'in cevabını bekle — Altı Üstü İstanbul "eski bölüm görünmüyor, açılabilir arşiv alanı, bölüm sıralı, seri filmler gibi yapı" isteği. Sorulan iki soru: (a) hangi ekranda görünmüyor (Devam Et kartı / oynatıcı listesi / başka)? (b) "seri filmler gibi" = kart altında katlanır "Arşiv", bölümler numara sırasıyla kart dizisi, en yeni üstte mi? Not: sunucu 15 bölümü veriyor (`load_item` DDizi `...-son-bolum-izle` → 15), pad ▲ "Bölümler (N)" listesi (`HomeScreen.kt:1101`) hepsini çiziyor; kodda "seri film" yapısı YOK.
2. Dean'den cihaz geri bildirimi (0.9.44'ün dört değişikliği). Sorun varsa önce `curl -s 192.168.1.186:3310/api/v1/client_log`.
3. Oturumlar kapanınca: `cmd /c rd /s /q "\\?\D:\projects\evaitec"`.

## Don't repeat
- load_item'ı elle denerken `encoded_url` TEK kez quote_plus; `url` param → 410, çift kodlama → "unknown url type".
- DDizi ham sayfasındaki 16/19/37. bölüm numaraları kenar çubuğundaki BAŞKA dizilerin (Muhtemel Aşk, Sevdiğim Sensin, Halef) — bölüm sayısı kanıtı değil.
- Zima'da docker adres havuzu tükendi → yeni compose ağına `ipam.subnet` ver.
- Kalıcı izin ekleme (`/permissions`) Claude'a engelli; Dean ekler.

## Read first
1. `~/.claude/projects/C--projects-netmovies/memory/MEMORY.md` — özellikle `card-may-be-episode-page`, `episode-list-slug-leak`
2. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/HomeScreen.kt` `PosterMenu` (~787) — bölüm listesi burada

## Verify
git -C C:/projects/netmovies rev-parse --short HEAD          # expect bu handoff commit'i (2180c74'ün üstü)
curl -s "http://192.168.1.186:3310/api/v1/app_update?target=tv"   # expect tag v0.9.44-poc
cd client-tv && ./gradlew testDebugUnitTest -q                # expect exit 0
Get-ScheduledTask 'LifeOS Finance Radar*' | % { $_.Actions.Arguments }   # expect C:\projects\evaitec\...

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies TV (C:\projects\netmovies, dal fix/general-stability). 0.9.44 yayında, cihazda doğrulanmadı.
D→C taşıma bitti (D:\projects\evaitec'te yalnız kilitli artık var, oturumlar kapanınca rd). R.J. Decker,
Teşkilat 186, A.Ü.İ 15, Resident Evil 2026 soruları kapandı.
Önce C:\projects\netmovies\HANDOFF.md'yi oku, Verify bloğunu çalıştır.
Öncelik: (1) Dean'in A.Ü.İ "eski bölümler / arşiv alanı / seri filmler gibi" cevabına göre tasarla — iki
soru açık, cevapsız kodlama; (2) cihaz geri bildirimi; (3) D artığını sil.
Dean istemeden yeni iş açma.
```
