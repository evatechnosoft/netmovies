# Handoff: 0.9.53 yayında, donma ve kaynak açık
> 2026-10-05 10:30 · `fix/general-stability` @ `9e0f3d9` (push'lu; Zima reposu `e66e23a`, aradaki yalnız docs) · 2 izlenmeyen dosya (`atv-kopru.log`, `scripts/yedek_reddet.py`, bu oturumun değil, dokunma)

## Goal
Reklamsız TV uygulaması (client-tv, Mi Box) + ZimaOS sunucu. Dean'in kusurlarını düzelt, bitince sormadan üç yere yayınla (yerel OTA, GitHub release, evaglass `apps.json`). Kurallar: `CLAUDE.md`; geçmiş gerekçe: `git log`, `.claude/handoffs/latest.md` GÜNCELLEME 7-10.

## State
- **0.9.53 üç yerde** (yerel OTA `app_update` → `v0.9.53-poc` doğrulandı; GitHub `v0.9.53-poc`; evaglass `netmovies-tv-v0.9.53` + apps.json tv/phone vc 953, sha `d82c98e5…`). Cihazda doğrulanmadı. Dean TV'de henüz 0.9.50/51'de olabilir; TV güncellemeyi kendiliğinden denetlemedi, menüden "Güncellemeyi kontrol et" gerekiyor.
- **Sunucu canlı** (engine 06:25Z, stream 06:34Z): YouTube aramada en önde (`search_all?yt=video|liste|0`), resmi dizi kartı videolardan önce, YouTube tek video film gibi açılır, sıradan oynatma listesi sırayla bölüm. Hızlı yolda YouTube ilk kaynak (`5cca2dd`, kök neden: `load_links` arama adresiyle çağrılıyordu).
- **TV (0.9.51-0.9.53):** arama ekranında "YouTube: ▶ Video / ☰ Oynatma listesi" hapları; Gözat'ta YouTube hapı başta; `CrashLog.donmaBekcisi` (ana iş parçacığı 8 sn kilitlenirse yığın `son_crash.txt` → sonraki açılışta şerit + `client_log`, satır `DONMA:`); WOL yalnız uygulama ekrandayken ve 07-24 (`ZimaUyandir.izinli`).
- **Testler:** client-tv 81/81 (`./gradlew testDebugUnitTest assembleDebug`), stream 228 OK, engine YouTube 5 OK (ikisi de Zima konteynerinde geçici kopyada).
- **AÇIK 1 — donma:** 5 Ekim 09:54:08'de Lioness S3B7 (kayıttan) 27:49'da TV'den sunucuya istekler tamamen kesildi, 10:16'da Dean yeniden açınca döndü. Çökme izi yok, kök neden bilinmiyor. Bekçi bir sonraki donmayı yakalar.
- **AÇIK 2 — "kaynak bulamıyor":** 10:16:50 Lioness `load_item` OK (DiziMom, 24 bölüm, S3B7 var) ama TV `resolve_sources` HİÇ çağırmadı. Dean'in gördüğü ekran doğrulanmadı.
- **Gece kapanma:** kök neden telefon widget'ı dahil her süreçten WOL (`journalctl --list-boots`: 00:00:11 → 00:01:28 açılış). 0.9.53 düzeltir; telefon da 0.9.53'e güncellenmeli. Bu gece doğrulanmadı.

## Next
1. Bu geceyi doğrula (yarın sabah): `ssh zima "journalctl --list-boots | tail -4"` → 00:00'dan sonra sabaha kadar açılış olmamalı. Varsa telefon/TV eski sürümde mi bak (`apps.json` vc 953).
2. Dean donma ya da "kaynak bulunamadı" bildirirse: `curl -s 192.168.1.186:3310/api/v1/client_log | grep -E "DONMA|cokme|resolve"`; stream günlüğünde o dakikada `resolve_sources` var mı (`docker logs --since 10m netmovies-stream | grep resolve`). İstek yoksa sorun TV içinde, `client-tv/.../ui/HomeScreen.kt` Devam Et → oynatıcı akışı.
3. Dean'den bekleyen: YouTube "ilgili videolar" rafı istiyor mu (yapılmadı); yeni `youtube_listem.json` adları.

## Don't repeat
- `client_log` boşluğu "TV boşta" demek değil (bellekte, stream rebuild'de sıfırlanır). Rebuild öncesi: `docker logs --since 2m netmovies-stream | grep -c remote/state` 0 olmalı. Bu oturumda Dean izlerken 3 rebuild yapıldı.
- YouTube yavaşlığını WARP'a bağlamak: ölçüldü, fark yok (2,6 vs 3,6 sn).
- YouTube eklentisinin `search`üne her videoyu katmak: zincir başlık eşleştirmede kullanıyor, rastgele video eşler. Genel arama ayrı (`search_all.youtube_kartlari`).
- Güç köprüsü `current_app` boş dönüyor (`scripts/atv_power.py`), ön plan uygulamasını göstermiyor.
- Engine/stream testleri laptopta koşmaz; konteynerde `/tmp/dd` (engine) ve `/tmp/st` + `unittest discover -s tests` (stream).

## Read first
1. `CLAUDE.md` — çalıştırma, Zima compose (`DOCKER_CONFIG=/tmp/dc`), boşta kuralı
2. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/data/CrashLog.kt` — donma bekçisi, Next #2'nin kanıt kaynağı
3. `stream/Public/API/v1/Routers/search_all.py` — YouTube araması (`youtube_kartlari`, `youtube_one`)

## Verify
```bash
git rev-parse --short HEAD                       # 9e0f3d9 — değilse: git log 9e0f3d9..HEAD --oneline
git status --porcelain | wc -l                   # 2 (izlenmeyen, başkasının)
ssh zima 'curl -s localhost:3310/api/v1/app_update?target=tv | grep -o "\"tag\":\"[^\"]*\""'   # v0.9.53-poc
ssh zima 'export DOCKER_CONFIG=/tmp/dc; docker ps --format "{{.Names}} {{.Status}}" | grep netmovies'   # 6 konteyner Up
cd client-tv && ./gradlew -q testDebugUnitTest   # 81 test yeşil
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability @ 9e0f3d9. 0.9.53 üç yerde yayında (YouTube aramada önde + Video/Oynatma listesi anahtarı, donma bekçisi, gece WOL düzeltmesi), cihazda doğrulanmadı. Açık: (1) 5 Ekim 09:54 TV donması, kök neden bilinmiyor, bekçi yakalayacak; (2) Dean "kaynak bulunamadı" dedi ama TV resolve_sources çağırmamış; (3) gece 00:00 kapanması bu gece doğrulanacak.
Ortam: aktif sunucu ZimaOS (ssh zima, /DATA/AppData/netmovies, compose için export DOCKER_CONFIG=/tmp/dc). Laptop yalnız kod + APK derleme.
Önce HANDOFF.md oku, Verify bloğunu koştur. Sıra: gece kapanmasını doğrula → Dean'in donma/kaynak bildirimi gelirse client_log DONMA satırı + stream resolve günlüğü.
TV oynarken rebuild yok (remote/state sayısı 0 olmalı). Yeni iş açma; Dean istemeden YouTube "ilgili videolar" rafına başlama.
```
