# Handoff: netmovies — 16 Kas 2026 gece kapanışı

> 2026-11-16 23:32 (+03) · worktree `C:\projects\netmovies\.claude\worktrees\devam-et-tazeleme` · dal `fix/devam-et-tazeleme` (push'lu, 0 dirty) · yarım kalan adım yok.

## Hedef
Dean'in üç şikâyeti (12 Kas): telefonda "son izlenenler / nerede kaldığım" bazen görünmüyor · kumanda modunda aynı liste bayat · Yeraltı açılmıyor. Üçü de düzeltildi ve canlıda; birleştirme kararı bekliyor.

## Durum (16 Kas 23:32 kanıtı)
- **Kod:** `fix/devam-et-tazeleme`, taslak PR #28 → `fix/general-stability`, açık, birleşmedi. İçinde:
  - 0af76a6 — client-tv: Devam Et öne gelince + 60 sn'de bir tazelenir (`MainActivity` `repeatOnLifecycle(RESUMED)`, `Library.sync` Mutex). Sürüm 0.9.92.
  - 8576d15 — `/rc` (kumanda sayfası) "Son izlenenler" 60 sn + `visibilitychange` ile tazelenir.
  - 64b3e05 — engine `bolum_sirasi`: sezonsuz numara (DDizi S1B22) sezonlu listede baştan sayılır (DiziAbi S2B6).
  - daf6ef6 — pilavyer kaynak adı ses dilinden (başka oturumdan; 64b3e05'in atası, canlı imajda).
- **Canlı (ZimaOS, ssh ile okundu):** checkout 64b3e05 (`fix/devam-et-tazeleme`, `fix/general-stability`'ye DÖNDÜRÜLMEDİ — bilerek). engine + stream "Up 14 hours (healthy)"; warp "Up About a minute" (az önce yeniden başlamış, nedeni bakılmadı). `/rc` düzeltmesi konteynerde (`sonImza` 3 eşleşme) — imajda değil, `docker cp` ile. `app_update` → `v0.9.92-poc`. TV boşta (2 dk'da 0 `remote/state`).
- **Yayın 0.9.92:** ev OTA + GitHub release `v0.9.92-poc` + evaglass-releases `netmovies-tv-v0.9.92`; imza `7328a32d` (ZimaOS `apk_publish.sh tv`).
- **Açık taslak PR'lar:** #28 (bu iş), #29 `feature/ytdlp-channel-id` (başka oturumdan, incelenmedi).

## Doğrulanan / doğrulanmayan
- Doğrulandı: emülatörde tazeleme istekleri (önde dakikada bir, arka planda yok, öne dönünce 3 sn); Yeraltı S1B22 `resolve_sources` HTTP 200, DiziAbi 1 kaynak, 26,5 sn; Dean "Başladı".
- Doğrulanmadı: gerçek telefonda kumanda ekranı · Mi Box/telefonun 0.9.92'yi kurduğu · `apps.json` satırı · `ddizi.tel` geri geldi mi (12 Kas'ta 522/zaman aşımı) · "Yeraltı" (ı ile) aramasının yedek kaynaklarda neden boş döndüğü · 15 ve 16 Kas'taki konteyner yeniden başlamalarının nedeni.

## Kararlar ve nedenleri
- ZimaOS checkout'u bu dalda bırakıldı: `fix/general-stability`'ye dönülürse sonraki engine derlemesi bölüm düzeltmesini geri alır.
- `/rc` düzeltmesi rebuild'siz `docker cp` ile alındı: o sırada TV oynuyordu.
- Laptopta derlenen APK yayınlanmadı: imzası farklı (`20319a76`), OTA reddeder. Yayın yalnız ZimaOS'tan.
- 13 Kas ev sunucusu denetimi SALT-OKUNUR yapıldı, hiçbir şey değiştirilmedi; maddeler `latest.md`'de.

## Tekrarlama
- TV oynarken (`remote/state` > 0) rebuild/recreate yapma.
- ZimaOS'ta compose: `/tmp/dc` dean'e kapalı çıktı (12 Kas) → `export DOCKER_CONFIG=/tmp/dc-dean`.
- Engine testini laptopta paket olarak koşma (`KekikStream` yok); `bolum_esle` testleri modül tek başına yüklenerek 11/12 geçti, 12.si o import yüzünden.
- İki seat aynı worktree'ye yazdı; commit öncesi `git pull`.

## Dean'den bekleyen kararlar
1. PR #28 birleştirilsin mi? Birleşmeden stream yeniden derlenirse `/rc` düzeltmesi geri gider.
2. Sağlıksız işaretli SEÇİLİ kaynağı (DDizi) beklemeden yedeğe geçen düzeltme yapılsın mı? (açılış ~26 sn → birkaç sn; `engine/Public/API/v1/Routers/resolve_sources.py`, `_links_for(selected…)` çağrısından önce sağlık kontrolü)
3. 13 Kas denetim maddeleri (SSH sıkılaştırma, dış yedek, log rotasyonu, restart policy) — ayrıntı `latest.md` üst bloğunda.

## Tek sonraki adım
ZimaOS açılınca: `ssh zima "export DOCKER_CONFIG=/tmp/dc-dean; docker ps --format '{{.Names}} {{.Status}}' | grep netmovies; docker exec netmovies-stream grep -c sonImza Public/Home/Templates/pages/rc.html.j2"` — konteynerler ayakta mı ve `/rc` kopyası yeniden başlatmadan sağ çıktı mı (0 ise PR #28 birleşmeden düzeltme kaybolmuş demektir). Sonra Dean'e karar 1'i sor.
