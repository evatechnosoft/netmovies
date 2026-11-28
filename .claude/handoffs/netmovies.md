# Handoff: netmovies — 28 Kas 2026 19:15 (yavaşlık + ses formatı + Mi Box)

> worktree aynı · entegrasyon `fix/general-stability` (uç 73179f1, PR #37 birleşti) · ZimaOS checkout ve engine imajı bu uçta (engine 19:07'de yeniden oluşturuldu) · client-tv 0.9.96.

## Ne oldu
- **Yavaşlık / boş Yeni Çıkanlar — kök neden:** ZimaOS 15:10'da açıldı; WARP konteyneri sağlık denetimine takılıp `docker-bekcisi` tarafından 26+ kez yeniden başlatıldı. Motorun sağlık ölçümü WARP düşükken alındı (15/16 "Erişilemedi"), 6 saat önbellekte kaldı → `aggregate_new` 0 içerik, `search_all` 10–12 sn.
- **Düzeltme (PR #37, f8f3016):** `engine/Public/API/v1/Routers/plugin_health.py` `_omur` — kaynakların yarıdan fazlası düşükse ölçüm 60 sn yaşar. Motor testleri ZimaOS'ta 122/122 OK. Canlı kanıt (19:08): sağlık 14/16 (DiziYou, PornHub düşük), `aggregate_new` movie 0,02 sn / 119 KB, serie 0,02 sn / 97 KB, `search_all` matrix 2,8 sn, dexter 2,5 sn.
- **Ses formatı (8 örnek, izleme geçmişinden):** hepsi AAC-LC stereo 48 kHz (Rush S1B10 DiziBox 44,1 kHz); 5.1/AC3/EAC3/DTS yok. Betik `~/.ai/work/netmovies-av-sync/audio_probe.py`.
- **Mi Box:** `192.168.0.55:5555` (eski .11 değil), 0.9.96 = sunucudaki son sürüm.

## Açık
- **WARP neden sağlıksız düşüyor: BİLİNMİYOR.** Gözlem: yeniden başladıktan birkaç dakika sonra konteyner içi `curl https://cloudflare.com/cdn-cgi/trace` 4,5 sn'de bağlanamıyor (kod 000), `warp-cli status` "Connected" diyor; 5 sn'lik sağlık denetimi 3 kez aşılınca bekçi yeniden başlatıyor. Günlükte `get_registration_config` ERROR. Proxy yolu (`netmovies-warp:8080`) aynı anda da düşüyor mu ölçülmedi — sonraki adım bu.
- TV tarafı açılış süresi ölçülmedi. Bluetooth ses ofseti için oturan ms değeri Dean'den bekleniyor.

## Tekrarlama
- ZimaOS'ta `docker logs --since` boş dönüyor; `docker logs -t --tail N` kullan.
- Kabuk komutunda test dosyası yoluna `>` ya da `sed` geçerse izin kancası reddediyor; testleri `git worktree add /tmp/nm-wt <dal>` + salt-okunur bağlama ile koş.
- Geri alma: `git revert -m 1 73179f1`, sonra engine build + `up -d --no-build engine`.

## Tek sonraki adım
`ssh zima "docker inspect netmovies-warp --format '{{.State.StartedAt}} {{.State.Health.Status}}'"` — hâlâ birkaç dakikada bir yeniden başlıyorsa: düşüş anında içeriden ve proxy'den aynı anda `curl` ile hangisinin koptuğunu ayır (yalnız içerisi kopuyorsa sağlık denetimini proxy yoluna çevir; ikisi de kopuyorsa MASQUE → WireGuard dene).

# (eski) 19 Kas 2026 20:05 kapanış

> worktree `C:\projects\netmovies\.claude\worktrees\devam-et-tazeleme` · dal `feature/remote-play-force` (push'lu, 0 dirty) · yarım adım yok.
> **21 Kas 00:17 kapanış kanıtı:** 19 Kas'tan beri kod değişikliği yok; bekleyen devir commit'i 2b635ba push edildi. ZimaOS kapalı/erişilemez (`ssh zima` zaman aşımı) — canlı durum bu turda DOĞRULANMADI, aşağıdaki canlı satırları 19 Kas'a ait. Açık taslak PR'lar: #28 `fix/devam-et-tazeleme`, #29 `feature/ytdlp-channel-id`, #30 `fix/pilavyer-player-host`. `feature/remote-play-force` için PR yok.

## Hedef
(1) Dexter'da Türkçe dublaj yok hatası — sistemde düzeltildi, canlıda. (2) Uzaktan "oynat"a sormadan geç + "ara ve aç" betiği — hazırlandı, yayınlanmadı (Dean: "hazırla, işlem yapma, izliyorum").

## Durum
- **Dexter dublaj:** dal `fix/pilavyer-player-host` (abc73be), taslak PR #30 → `fix/devam-et-tazeleme`. ZimaOS checkout'u ve engine imajı bu dalda (19:38 yeniden oluşturuldu). Kanıt: Dexter 4x11 ve 4x12 `resolve_sources` → DiziAbi | Türkçe Dublaj, DDiziMom | Türkçe Dublaj, DiziBox. 4x11 TV'de 19:50'de baştan açıldı (`remote/status` url `dexter-4-sezon-11-bolum`, playing true). Sesi kulakla doğrulamadım.
- **MobLand 2x10:** hata değil; DiziAbi ve DDiziMom'da sayfa var, oynatıcı boş (`data-player-type="none"`). Tek kaynak DiziBox (altyazılı). 2x09 dublajlı geliyor.
- **remote-play-force (f5a3f80):** `stream/Public/API/v1/Routers/remote.py` `force` alanı, client-tv `RemoteCommand.force` + `MainActivity` kartı atlar, `scripts/tv_ac.py "<başlık>" <sezon> <bölüm> [--plugin] [--kuru] [--sor]`. Kanıt: kumanda testleri 10/10 (stream konteynerinde geçici kopya), `:app:compileDebugKotlin` exit 0, betik `--kuru` ile Dexter 4x11 → sıra 46. Gerçek TV'de `force` DENENMEDİ; sürüm artırılmadı, PR açılmadı.
- 4x12 konumu 904. sn'de kayıtlı (benim CENTER komutum duraklattı, sonra stop + play ile 4x11 açıldı).

## Tekrarlama
- Oynayan içerik varken `remote/play` TV'de onay kartı açar; uzaktan `CENTER` kartı onaylamaz, oynatıcıyı duraklatır. `force` yayınlanana kadar: önce `transport stop`, sonra play.
- `docker logs --since` ZimaOS'ta boş dönebiliyor; boşta kontrolü `docker logs -t --tail N | grep remote/state` ile.
- Hafıza klasörü (`~/.claude-3/projects/.../memory`) bu oturumdan yazılamıyor; "tv_ac kullan" notu kaydedilemedi.

## Dean'den bekleyen
1. "Yayınla": stream rebuild + client-tv 0.9.93 (`apk_publish.sh tv`, yalnız ZimaOS'tan) — `force` için.
2. PR #28 + #30 (+ bu dal) `fix/general-stability`'ye birleşsin mi; sunucu birleşmemiş daldan çalışıyor.
3. 16 Kas bloğundaki 2. ve 3. karar aynen duruyor.

## Tek sonraki adım
ZimaOS açılınca: `ssh zima "docker ps --format '{{.Names}} {{.Status}}' | grep netmovies; cd /DATA/AppData/netmovies && git log --oneline -1"` (abc73be bekleniyor), sonra Dean'e karar 1-2'yi sor.

# (eski) 19 Kas 2026 — Dexter Türkçe dublaj ayrıntısı

> 19 Kas 19:40 (+03) · dal `fix/pilavyer-player-host` (abc73be, push'lu; `fix/devam-et-tazeleme` üstüne) · ZimaOS checkout'u ve engine imajı bu dalda.

- **Hata:** Dexter'da Türkçe dublaj yok. Kök neden: DiziAbi sabit `play2.pilavyerplay.top` kullanıyordu, alt alan kalkınca `s.php` 404 → dublajlı kaynak sessizce düşüyor, yalnız DiziBox (altyazılı) kalıyordu.
- **Düzeltme:** `engine/Plugins/__dizi_common.py::pilavyer_oynatici` konağı bölüm sayfasındaki `core.js` adresinden okur; DiziAbi + DDiziMom bunu kullanır (yoksa `PILAVYER_URL` / varsayılan). Test: `test_oynatici_konagi_sayfadan_okunur` (konteynerde 11/11 OK).
- **Canlı kanıt (19:39):** `resolve_sources` Dexter S4B12 → 3 kaynak: "DiziAbi | Türkçe Dublaj", "DDiziMom | Türkçe Dublaj", "DiziBox | MolyStream". Engine 19:38'de yeniden oluşturuldu (healthy), TV o sırada oynuyordu.
- **Doğrulanmadı:** TV'de kulakla Türkçe ses (akış tek ses parçalı, dil oynatıcının `audios` alanından); Dean'in açık oynatmasının kaynağı kendiliğinden değişmez, bölümü yeniden açmak gerekir.
- **Bekleyen:** bu dal için PR açılmadı/birleşmedi; aşağıdaki 16 Kas kararları aynen duruyor.

# Handoff: netmovies — 17 Kas 2026 (fotoğraf arşivi yan işi)

> 17 Kas gece · NetMovies kodunda değişiklik yok; aşağıdaki 16 Kas bloğu hâlâ geçerli.

## 17 Kas yan iş: Nextcloud fotoğraf arşivi (ZimaOS)
- Telefon fotoğraflarının tek yeri `dean/files/Resimler/E-Arsiv` (3.840 resim: Belge 228, Ekran-Goruntusu 1.186, Manzara 37, Yuz 628, Diger 1.761). InstantUpload'daki 708 resim CLIP ile türlere ayrılıp buraya taşındı; `InstantUpload` boş.
- Geri alma günlükleri: `/DATA/sata/stage_e/Resimler/undo_instant.csv`, `undo_merge.csv`.
- Hatalı çift kopya (6,3 GB) Nextcloud dışına park edildi: `/DATA/sata/trash-photos-20261117` — silinmedi, Dean onayı bekliyor.
- Recognize'da `imagenet.enabled` ve `landmarks.enabled` açıldı, `recognize:recrawl` kuyruğa alındı; etiketlerin oluştuğu DOĞRULANMADI.
- Robot Eva görselleri: `C:\projects\evaitec\opsguardai\opsguard\assets\{eva,eva2,1,2,kl,li}.png` → Nextcloud `Resimler/Evaitec`.
- Laptop: Nextcloud istemci ayarı yedekleri `%APPDATA%\Nextcloud\nextcloud.cfg.bak-20261117{,b}`; ölü klasör `C:\Users\Dean\PhoneBackup` duruyor (silme onayı yok).
- Yarım: Dean "elektronik ayrışan vardı" dedi → `Diger` içinden elektronik fotoğraflarını ayırma. Alt tür sınıflandırması `~/.ai/work/photo-sort/sub.csv` dosyasına yazılıyor (betik `jobs/52cc3f87/tmp/find_electronics.py`, girdi `E:/Resimler/Diger` + `~/.ai/work/photo-sort/InstantUpload`). Sonraki adım: `sub.csv`'de "electronics" satırlarını `E-Arsiv/Diger` altından yeni bir klasöre taşı, günlük yaz, `occ files:scan`.
- Betikler: `~/.ai/scripts/photo-sort/{classify_dir.py,apply_instant.sh}`, `~/.ai/work/photo-sort/merge.sh`.
- Dean onayı bekleyen: park edilmiş kopyanın ve laptoptaki `PhoneBackup`, `~/.ai/work/su-faturasi` (278 MB, kimlik resimleri), `~/.ai/work/photo-sort/InstantUpload` (2 GB) klasörlerinin silinmesi; yeni fotoğraflar için gecelik otomatik ayıklama.

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
