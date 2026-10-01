# Handoff: TV 0.9.44 yayında + D→C/ZimaOS taşıma yarım
> 2026-10-01 · `fix/general-stability` @ `d50c705` (push'lu) · kirli: yalnız `atv-kopru.log`, `scripts/yedek_reddet.py` (izlenmeyen, bu oturumun değil)
> Katalog `C:\projects\evaglass-releases` @ `6321bc4` (push'lu)

## Goal
Reklamsız TV uygulaması (client-tv, Mi Box) — Dean'in bildirdiği kusurları düzelt, bitince sorma yayınla (üç yer). Yan iş: aktif projeleri D:\projects → C:\projects taşı, laptop konteynerlerini ZimaOS'a al (Zima açıkken oradan, laptop yedek).

## State
- Yayında (app_update `v0.9.44-poc`, GitHub release, apps.json vc 944) — hiçbiri cihazda denenmedi, tv_test emülatörü "offline"da kaldı:
  - `468c95d` PlayerScreen/PlayerScreen2 `key(current.url)` (MainActivity) — telefondan gelen içerik eski ExoPlayer'ı devralıyordu.
  - `3551925` `data/SesHafizasi.kt` — medya sesi oynatıcı açılışında geri yüklenir.
  - `d50c705` HomeScreen PosterMenu: LISTE modu panelsiz 3 ikon (`ListeIkonu`), pad kapanınca `firstFocus` yeniden istenir (raftan kalkan kartla odak kayboluyordu).
- Taşıma (C'de 57 GB boş): sides, layers, evaglass*, codeplay, evaitec-appkit, evaglass-releases, life-os-finance → C'de, D'den silindi. D:\projects\layers boş ama kilitli.
- **Bekleyen:** D:\projects\evaitec ve D:\projects\ev — C'ye kopyalandı ama D'de canlı iş vardı (VS Code D:\projects\evaitec açık, rcmycar commit'leri, ev .pio/Android build). Zamanlanmış görevler "LifeOS Finance Radar Aksam/Gunluk" hâlâ D yolunda.
- ZimaOS: life-os-finance api :8001 / web :8180, claude-otel (OTLP 4317, Grafana 3201), Ollama :4602 (+qwen3:4b-instruct). Ayrıntı: `~/.ai/contracts/zimaos-infrastructure.md`. Laptop konteynerleri durdu, restart=no.

## Next
1. Dean evaitec/ev işini kapattıysa: `robocopy D:\projects\evaitec C:\projects\evaitec /E /XJ /R:1 /W:1` (ev için aynı) → `robocopy ... /L` "Files" satırında Copied=0 doğrula → `cmd /c rd /s /q "\\?\D:\projects\evaitec"`. Sonra iki Finance Radar görevini `Set-ScheduledTask` ile `C:\projects\evaitec\lifeOS\life-os-finance\finance-radar\tools\*.ps1`'e çevir.
2. Dean'den cihaz geri bildirimi: pad 3 ikon, odak dönüşü, ses hafızası, telefon→TV geçişi. Sorun varsa önce `curl -s 192.168.1.186:3310/api/v1/client_log`.
3. Açık sorular: R.J. Decker bozuk Devam Et kaydı (S1B1 @1121 sn, T3 süresiyle yazıldı) silinsin mi; Teşkilat 188 yerine 186 (DDizi load_item 500 → DiziMom kurtarma 186'da bitiyor, doğrulanmadı).

## Don't repeat
- load_item'ı elle denerken `encoded_url` TEK kez quote_plus; `url` param → 410, çift kodlama → "unknown url type".
- Zima'da docker adres havuzu tükendi → yeni compose ağına `ipam.subnet` ver; `prometheus` konteyner adı Zima'da dolu.
- D'yi silmeden önce `robocopy /L` farkı bak — evaitec/ev'de kopyadan sonra dosyalar değişti.
- Kalıcı izin ekleme (`/permissions`) Claude'a self-modification diye engelleniyor; Dean ekler.

## Read first
1. `~/.claude/projects/C--projects-netmovies/memory/MEMORY.md` — proje hafızası
2. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/HomeScreen.kt` `PosterMenu` — son değişiklik

## Verify
git -C C:/projects/netmovies rev-parse --short HEAD          # expect d50c705
curl -s "http://192.168.1.186:3310/api/v1/app_update?target=tv"   # expect tag v0.9.44-poc
cd client-tv && ./gradlew testDebugUnitTest -q                # expect exit 0
curl -s -o /dev/null -w "%{http_code}" http://192.168.1.186:8180/   # expect 200 (finans Zima)

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies TV (C:\projects\netmovies, dal fix/general-stability @ d50c705). 0.9.44 üç yerde yayında:
telefon→TV geçişinde oynatıcı key(url) ile sıfırlanıyor, medya sesi hafızada, pad'de listeler 3 küçük
ikon ve pad kapanınca odak geri. Cihazda doğrulanmadı. Yan iş: D:\projects→C taşıma; evaitec ve ev
D'de bekliyor (orada canlı iş vardı), finans/otel/ollama ZimaOS'a taşındı.
Önce C:\projects\netmovies\HANDOFF.md'yi oku, Verify bloğunu çalıştır.
Öncelik: (1) evaitec/ev farkını aktar-doğrula-D'den sil + Finance Radar görevlerini C'ye çevir
(D'de iş sürüyorsa Dean'e sor); (2) Dean'in cihaz geri bildirimi; (3) R.J. Decker kaydı / Teşkilat 186 sorusu.
Dean istemeden yeni iş açma.
```
