# Handoff: TV 0.9.45 yayında (üst bar tek satır), sistem denetimi yeşil
> 2026-10-02 · `fix/general-stability` @ `194413c`+ (push'lu) · kirli: yalnız `atv-kopru.log`, `scripts/yedek_reddet.py` (izlenmeyen)

## Goal
Reklamsız TV uygulaması (client-tv, Mi Box) — Dean'in bildirdiği kusurları düzelt, bitince sorma yayınla (üç yer: yerel OTA, GitHub release, apps.json).

## State
- **0.9.45 yayında, üç yer kanıtlı:** yerel OTA `app_update?target=tv` → `v0.9.45-poc` (indirme 200, 22 637 128 B) · GitHub `v0.9.45-poc` (prerelease) · evaglass-releases `netmovies-tv-v0.9.45` + apps.json tv/phone vc 945 (sha256 `2ca34e13…`). Cihazda DOĞRULANMADI.
  Değişiklik: UpdateBanner (üst barın üstünde ayrı satır) silindi → güncelleme NetMovies markasının yanında tek düğme (`HomeScreen.kt` `TopBar`), güncel/boşta görünmez; 📱 kumanda düğmesi TV'den kaldırıldı. 71 birim test yeşil.
- **Sistem denetimi (2 Ekim):** smoke YEŞİL (ZimaOS), chain_scan 54 OK / 3 ölü (FullHDFilmizlesene Soulm8te ×2, DDizi Anne Yarısı 504), tünel 200, proxy_token kurcalama → 403, admin 401, konteynerler 0 restart, /DATA %27.
- **KultFilmler** Dram/Komedi slug'ları `-filmleri-izle-1` oldu (`cac0180`); motor Zima'da rebuild edildi (created 2026-10-02T14:24Z), Komedi 20 kart.
- **Aktif sunucu ZimaOS** (`/DATA/AppData/netmovies`, repo `cac0180`+). Laptopta docker PATH'te yok; `smoke.sh` otomatik `ssh zima` dalına düşer. Zima'da compose için `export DOCKER_CONFIG=/tmp/dc` şart (yoksa "'compose' is not a docker command").
- 0.9.44 cihaz geri bildirimi: Dean Blade Runner 2049'u açtı, biraz izledi, kapattı — "sorun yok". A.Ü.İ arşiv tasarımı hâlâ cevapsız.

## Next
1. Dean'den 0.9.45 cihaz geri bildirimi (üst barda ⬆ düğmesi görünüyor mu, odak tek satırda mı).
2. A.Ü.İ "arşiv alanı / seri filmler gibi" isteği — iki soru hâlâ açık (hangi ekran; katlanır Arşiv kart dizisi mi). Cevapsız kodlama.
3. Ölü kaynaklar: FullHDFilmizlesene Soulm8te (Orijinal+SetPlay) ve DDizi Anne Yarısı 504 — tekrar ederse eklentiye bak.
4. `docs/HANDOFF.md` 24 Eylül'de kalmış; canlı devir bu dosya (kök `HANDOFF.md`). CLAUDE.md'deki `docs/HANDOFF.md` işareti düzeltilmeli.

## Don't repeat
- load_item'ı elle denerken `encoded_url` TEK kez quote_plus; `url` param → 410, çift kodlama → "unknown url type".
- DDizi ham sayfasındaki 16/19/37. bölüm numaraları kenar çubuğundaki BAŞKA dizilerin (Muhtemel Aşk, Sevdiğim Sensin, Halef) — bölüm sayısı kanıtı değil.
- Zima'da docker adres havuzu tükendi → yeni compose ağına `ipam.subnet` ver.
- Kalıcı izin ekleme (`/permissions`) Claude'a engelli; Dean ekler.

## Read first
1. `~/.claude/projects/C--projects-netmovies/memory/MEMORY.md` — özellikle `card-may-be-episode-page`, `episode-list-slug-leak`
2. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/HomeScreen.kt` `PosterMenu` (~787) — bölüm listesi burada

## Verify
git -C C:/projects/netmovies rev-parse --short HEAD          # expect bu handoff commit'i (194413c'nin üstü)
curl -s "http://192.168.1.186:3310/api/v1/app_update?target=tv"   # expect tag v0.9.45-poc
cd client-tv && ./gradlew testDebugUnitTest -q                # expect exit 0
Get-ScheduledTask 'LifeOS Finance Radar*' | % { $_.Actions.Arguments }   # expect C:\projects\evaitec\...

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies TV (C:\projects
etmovies, dal fix/general-stability). 0.9.45 yayında (üç yer), cihazda doğrulanmadı.
Aktif sunucu ZimaOS; laptopta docker yok, smoke.sh ssh ile koşar. 2 Ekim sistem denetimi yeşil.
Önce C:\projects
etmovies\HANDOFF.md'yi oku, Verify bloğunu çalıştır.
Öncelik: (1) Dean'in 0.9.45 cihaz geri bildirimi; (2) A.Ü.İ arşiv tasarımı — iki soru açık, cevapsız kodlama.
Dean istemeden yeni iş açma.
```
