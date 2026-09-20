# DEVİR — 20 Eylül 2026, 15:36 · DHCP adres kayması + temizlik

**Dal:** `fix/general-stability` · **Sürüm:** TV/telefon **0.9.10 (vc 910)**
**Yerel adres DEĞİŞTİ:** `http://192.168.1.185:3310` (eski `192.168.0.29` ölü)
**Tünel:** `https://w.evaitec.com`

## Hedef
Dean "TV'de ağ hatası görüyorum, her an değişen sisteme sabitleme ya da uyum getir" dedi.
Kök neden bulunup düzeltilecek, sürüm çıkılacaktı.

## Durum — DOĞRULANMIŞ (tool çıktısı var)
- `smoke.sh` YEŞİL: 16 eklenti · movie 431 · serie 450 · live 236 · zincir + manifest + gateway testleri.
- `testDebugUnitTest` failures=0, yeni `BaseUrlInterceptorTest` 2/2 · `assembleDebug` BUILD SUCCESSFUL.
- 0.9.10 üç yerde: yerel OTA `app_update?target=tv` → `v0.9.10-poc` · netmovies release
  `v0.9.10-poc` indirme 200 / 20.402.682 bayt · `evaglass-releases/apps.json` tv+phone 0.9.10 vc 910, APK 200.
  sha256 `3e027a669f02f0757d36ffbb737199f2316962868468b5208eca0057a28b059c`.
- Commit'ler: `40e015a` (fix) · `f00295e` (handoff) · `194b6ea` (devir) · `4e9fbf2` (APK temizliği) ·
  evaglass `cda3f37`. Hepsi push'lu.

## Durum — DOĞRULANMADI
- **0.9.10 cihazda denenmedi.** Ağ-hatası düzeltmesi de, 0.9.9'un oynatıcı içi işleri de
  (ok tuşlarıyla bar gezinmesi, yarım tuş, GERİ=OYNAT) TV'de görülmedi. Emülatör oynatıcıyı ayakta tutamıyor.

## Kararlar ve neden
- **Kök neden:** `ServerResolver` adresi yalnız AÇILIŞTA çözüp cache'liyordu. DHCP adresi kaydırınca
  her istek ölü hedefe gidiyor, "ağ hatası" elle "Tekrar dene"ye basılana kadar kalıyordu.
  Keşif zinciri (hatırlanan → aday listesi → /24 tarama → tünel) zaten vardı; eksik olan
  çalışma anındaki tetik. `BaseUrlInterceptor` artık IOException'da `reset()` + yeniden keşif +
  tek tekrar; keşif aynı adresi verirse hata yükselir (döngü yok).
- Interceptor test edilebilmek için iki lambda alıyor (`current`, `rediscover`), üretimde varsayılanlar
  ServerResolver'a bağlı — MockWebServer ile ölü/canlı adres senaryosu test edilebilsin diye.
- APK temizliği: `data/apk` 2.0 G → 65 M (TV son 3 sürüm + saat son 2), repo kökündeki 31 APK
  (589 MB, 12'si git'te takipli) silindi + indeksden düşürüldü. `.gitignore`'da kural zaten vardı.
- TV'de 0.9.8 bilerek duruyor: 0.9.10 cihazda doğrulanmadı, geri dönülecek sürüm lazım.

## Tekrarlanmayacak / tuzaklar
- **İki ayrı OTA kanalı var, ikisi de güncellenmeli:** uygulama içi OTA `evatechnosoft/netmovies`
  `/releases` (tag `vX.Y.Z-poc`, asset `NetMovies-TV-vX.Y.Z.apk`), mağaza ise `evaglass-releases`
  (tag `netmovies-tv-vX.Y.Z`, asset `netmovies-tv-X.Y.Z.apk` + `apps.json`).
  netmovies reposunda 0.9.4–0.9.9 release'leri HİÇ YOKTU — o aralıkta TV güncelleme görmedi.
- `gh release create --target main` 422 verir: bu repoda ana dal `master`.
- evaglass-releases'e push'tan önce `git reset --hard origin/main` + değişikliği yeniden uygula;
  apps.json'da rebase çakışması çıkıyor (başka projeler de yazıyor).
- Dokümandaki yerel adres bayat olabilir; "ağ hatası" duyunca önce `ipconfig` ile gerçek adresi doğrula.

## Sıradaki TEK iş
Dean 0.9.10'u TV'ye kursun ve ağ hatası tekrar ediyor mu baksın. Ederse:
`docker logs --since 10m netmovies-stream` ile isteğin sunucuya ulaşıp ulaşmadığına bak —
ulaşmıyorsa keşif turu (aday listesi / /24 tarama) çalışmıyordur.

**Açık, Dean'in işi:** router'da sunucu PC'ye DHCP rezervasyonu (kalıcı sabitleme).
Kod kaymayı tolere ediyor ama sabit adres keşif turunu tamamen ortadan kaldırır.
Windows statik IP de mümkün — ağ ayarı olduğu için onaysız yapılmadı.

**Açık, isteğe bağlı:** `.git` 245 M, geçmişteki APK'lar duruyor; temizlik `filter-repo` + force push ister.
