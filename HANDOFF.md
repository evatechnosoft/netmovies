# Handoff: 0.9.56 kişiselleştirme yayında, cihaz doğrulaması bekliyor
> 2026-10-05 22:10 · `fix/general-stability` @ `19bd3eb` (push'lu) · 2 izlenmeyen dosya (`atv-kopru.log`, `scripts/yedek_reddet.py`, bu oturumun değil, dokunma)

## Goal
Reklamsız TV uygulaması (client-tv, Mi Box) + ZimaOS sunucu. Dean'in isteklerini yap, bitince sormadan üç yere yayınla (yerel OTA, GitHub release, evaglass `apps.json`). Kurallar: `CLAUDE.md`. Bu oturumun işi: TV kişiselleştirme — plan ve gerekçe `docs/PLAN-kisisellestirme.md` (web kaynaklarıyla).

## State
- **0.9.56 üç yerde:** yerel OTA `app_update` → `v0.9.56-poc`; GitHub `v0.9.56-poc`; evaglass `netmovies-tv-v0.9.56` + apps.json tv/phone vc 956, sha `747e4e48…`, sizeBytes 22719252, indirme 200.
- **Plan maddeleri 1-9 yapıldı** (`docs/PLAN-kisisellestirme.md` tablosu, 10-11 reddedildi):
  - Ortak jest `client-tv/.../ui/Tasima.kt`: OK basılı tut → sarı, SOL/SAĞ (dikey listede ▲▼) taşı, OK bırak+kaydet, GERİ iptal.
  - Gözat kaynak çipleri (`BrowseScreen.kt` `SourceChips`): taşı; taşırken ▼ = kaynağı gizle (admin `hidden_providers`); elle dizilmemişler `source_score`'a göre (`varsayilanKaynakSirasi`).
  - Ana sayfa kişisel çipleri taşınır; Ayarlar → "☰ Rafları düzenle" (`HomeScreen.kt` `RafDuzenleMenu`): raf sırala/gizle + "raflarda gösterme" listesi.
  - Poster menüsü ▼ Listeler: "Devam Et'ten çıkar" (iki basış, geri alınamaz) ve "Raflarda gösterme".
  - Düzen durumu `data/KisiselDuzen.kt`; prefs anahtarları `home_segment_order`, `home_row_order`, `home_row_hidden`, `hidden_titles`, `provider_order`.
- **Doğrulama:** `./gradlew testDebugUnitTest assembleDebug` → 87 test, 0 hata. Emülatörde DOĞRULANDI: Gözat ve ana sayfa çip taşıma, ipucu, GERİ iptal, değişmeyen sıra prefs'e yazılmıyor. Emülatörde DOĞRULANMADI: Rafları düzenle taşı/gizle (yalnız açılıp listelediği görüldü), poster menüsünün yeni iki düğmesi, Gözat ▼ gizle. Cihazda hiçbiri doğrulanmadı.
- **Sunucu prefs:** 21:20'de 5 anahtar (`fav_channels`, `kayit_otomatik`, `rc_dokunmatik`, `rc_olcek`, `rc_sira`); yeni anahtarlar Dean kullanınca oluşur.
- **Yan etki:** emülator testinde Altı Üstü İstanbul yanlışlıkla açıldı → Devam Et'te en öne geçti, konumu korundu (S1B15, 146 sn).
- **Öncekinden açık kalanlar** (detay `git show aff2db6:HANDOFF.md`): 5 Ekim 09:54 TV donması (bekçi `CrashLog.donmaBekcisi` yakalayacak), "kaynak bulunamadı" ama `resolve_sources` çağrılmamış, gece 00:00 kapanması (0.9.53 düzeltmesi) doğrulanmadı.

## Next
1. Gece kapanmasını doğrula: `ssh zima "journalctl --list-boots | tail -4"` → 6 Ekim 00:00'da kapanış (`gece-kapanma.timer` → `systemctl poweroff`), sabaha kadar YENİ AÇILIŞ OLMAMALI. Önceki gece (fix öncesi) 00:00:11 kapanıp 00:01/00:15/00:19'da üç kez WOL ile açılmıştı. Açılış varsa: telefon/TV 0.9.53+ mı (WOL kuralı `ZimaUyandir.izinli`: yalnız `onResume`–`onPause` arası ve 07-24; asıl suçlu telefon widget'ıydı). TV APK'sı 5 Ekim 21:52'de 192.168.1.105'ten indirildi, kurulumu doğrulanmadı; telefon sürümü bilinmiyor.
2. Dean 0.9.56'yı TV'de denediğinde geri bildirime göre düzelt. Önce denenmeyenler: Ayarlar → Rafları düzenle (OK gizle, basılı tut ▲▼), poster ▼ menüsünde son iki ikon, Gözat'ta çip taşırken ▼. Prefs'i kontrol: `ssh zima 'curl -s localhost:3310/api/v1/prefs'`.
3. Donma / "kaynak bulunamadı" bildirimi gelirse: `curl -s 192.168.1.186:3310/api/v1/client_log | grep -E "DONMA|cokme|resolve"` ve `ssh zima 'docker logs --since 10m netmovies-stream | grep resolve'`.

## Don't repeat
- Emülatörde ekrana DOKUNDUKTAN sonra D-pad: dokunma kipi modalın odak isteğini düşürüyor, tuşlar arkadaki posterlere gidiyor (bir dizi yanlışlıkla açıldı). Emülatörde yalnız `input keyevent` kullan, dokunma yok; ya da cihazda dene.
- Dean TV'de izlerken emülatörü "Televizyon" kipinde açmak: o da bir TV istemcisi (ilerleme yazar, kumanda kuyruğunu yoklar). Önce `ssh zima "docker logs --since 2m netmovies-stream 2>&1 | grep -c remote/state"` 0 olmalı.
- Gerçek sunucuya karşı test öncesi prefs yedeği al (`curl .../api/v1/prefs > yedek`), sonra karşılaştır.
- Kumandada uzun basış `combinedClickable(onLongClick)` ile yakalanmaz; `Tasima.kt` `tasimaTuslari` key event yolunu kullan.
- `client_log` boşluğu "TV boşta" demek değil; rebuild öncesi `remote/state` sayısına bak.
- evaglass-releases push reddedilirse `git pull --rebase` çakışır (apps.json başkası da yazıyor): `git reset --hard @{u}` + değişikliği yeniden uygula.

## Read first
1. `docs/PLAN-kisisellestirme.md` — ne yapıldı, nerede, ne doğrulanmadı
2. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/Tasima.kt` — tüm taşıma/gizleme jestinin tek kaynağı
3. `.claude/agents/tv-urun.md` — trend/ürün işleri için persona (Pi + Fable)

## Verify
```bash
git rev-parse --short HEAD                       # 19bd3eb — değilse: git log 19bd3eb..HEAD --oneline
git status --porcelain | wc -l                   # 2 (izlenmeyen, başkasının)
ssh zima 'curl -s localhost:3310/api/v1/app_update?target=tv | grep -o "\"tag\":\"[^\"]*\""'   # v0.9.56-poc
cd client-tv && ./gradlew -q testDebugUnitTest   # 87 test yeşil
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability @ 19bd3eb. 0.9.56 üç yerde yayında: TV kişiselleştirme (çip taşıma, Rafları düzenle, Devam Et'ten çıkar, Raflarda gösterme, Gözat'ta ▼ gizle, puana göre kaynak sırası) — plan docs/PLAN-kisisellestirme.md, ortak jest ui/Tasima.kt, durum data/KisiselDuzen.kt. Cihazda doğrulanmadı; Rafları düzenle taşı/gizle ve poster menüsünün yeni iki düğmesi emülatörde de denenmedi. Açık: gece 00:00 kapanması doğrulanacak, eski donma/"kaynak bulunamadı" bildirimleri bekleniyor.
Ortam: aktif sunucu ZimaOS (ssh zima, /DATA/AppData/netmovies, compose için export DOCKER_CONFIG=/tmp/dc). Laptop yalnız kod + APK derleme.
Önce HANDOFF.md oku, Verify bloğunu koştur. Sıra: gece kapanmasını doğrula → Dean'in 0.9.56 cihaz geri bildirimine göre düzelt → donma/kaynak bildirimi gelirse client_log + resolve günlüğü.
Emülatörde dokunma yok, Dean izlerken emülatörü TV kipinde açma, TV oynarken rebuild yok. Yeni iş açma.
```
