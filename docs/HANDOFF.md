# NetMovies — Oturum Devri (HANDOFF)

> Bu dosya, projeyi başka bir oturumda kaldığı yerden sürdürmek içindir.
> **Üstteki DEVİR bloğu = şu an nerede olduğun ve sıradaki iş.**
> Altındaki oturum günlükleri = kararların gerekçesi (neden böyle yapıldı).

---
# 🧭 DEVİR — buradan devam et

**Son güncelleme:** 16 Eylül 2026, 17:05
**Dal:** `fix/general-stability` @ `2f973ae` · **0 kirli dosya** · push EDİLDİ
**Sürümler:** TV `v0.3.5-poc` · saat `v0.1.5-poc`
**Katalog:** `evaglass-releases/apps.json` @ `1202dec` (push EDİLDİ) —
netmovies-tv/phone **0.3.5 (vc 305)** · netmovies-mini-watch **0.1.5 (vc 105)**, ikisi de CANLI
**Adresler:** yerel `http://192.168.0.29:3310` · tünel `https://w.evaitec.com` (ayakta)
**PIN:** site `1234` · yönetim paneli Basic auth → `.env: ADMIN_PASS`

> **Yayın kuralı DEĞİŞTİ** (Dean, 15 Eylül): "Yayınla sorma artık bitince geliştirme
> yayınla." Kanıt yeşilse sürüm artırılır ve üç dağıtım yeri birden güncellenir —
> yerel OTA (`/data/apk`), GitHub release (`--target main`), `apps.json`. Onay sorulmaz,
> ne yayınlandığı söylenir. Eski "cihazda denenmemiş sürüm OTA'ya konmaz" kuralı KALKTI.
> Hafıza: `memory/yayinlamak-icin-sorma.md`.

## Doğrula (koş, sonra devam et)

```bash
git rev-parse --short HEAD                  # beklenen: 2f973ae
git status --porcelain | wc -l              # beklenen: 0
bash scripts/smoke.sh                       # beklenen: kapı YEŞİL
MSYS_NO_PATHCONV=1 docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests
                                            # beklenen: Ran 132 · OK
MSYS_NO_PATHCONV=1 docker exec netmovies-engine sh -c 'cd /usr/src/KekikStreamAPI && PYTHONPATH=. python -m unittest discover -s tests'
                                            # beklenen: Ran 40 · OK
curl -s "localhost:3310/api/v1/app_update?target=tv"     # beklenen: tag v0.3.5-poc
curl -s "localhost:3310/api/v1/app_update?target=wear"   # beklenen: tag v0.1.5-poc
curl -s -o /dev/null -w "%{http_code}\n" https://w.evaitec.com/api/v1/health   # beklenen: 200
```
`2f973ae` bulunamıyorsa dal ilerlemiş: `git log fe0b39f..HEAD --oneline`.
**Tünel 530 dönüyorsa** `docker compose --profile tunnel up -d` — `cloudflared` ağ ad
alanı stream'e pinli, stream her yeniden kurulduğunda tünel kopuyor. Saat tünele
düştüğünde bu doğrudan "saat çalışmıyor" demek.

## SIRADAKİ İŞ #1 — saat 0.1.5'i ADB ile kur, TV 0.3.4'ü dene

> **TAVUK-YUMURTA — saat.** Bilekte 0.1.4 kurulu ve onda OTA YOK. evaitecOTA
> bileklikte APK kuramıyor (Dean: "kuramıyor"), bu yüzden 0.1.5'i ilk kez koymanın
> tek yolu ADB. 0.1.5 kurulduktan SONRA güncellemeler uygulama içinden gelir.
> ```bash
> # saatte: Ayarlar > Geliştirici seçenekleri > Kablosuz hata ayıklama açık olmalı
> adb connect <saat-ip>:5555
> adb install -r data/apk/NetMovies-Wear-v0.1.5.apk
> ```
> ADB yolu: `C:/Users/Deacjx/AppData/Local/Android/Sdk/platform-tools/adb`

**0.1.5 = kendi kendini güncelleme + yuvarlak liste.** APK ev sunucusundan
`/api/v1/app_update?target=wear` ile iner, PackageInstaller oturumuyla kurulur
(aracı uygulama yok). Ana ekranda yeni sürüm varsa `⬆ 0.1.6 güncelle` şeridi
çıkar; kurulum izni yoksa ilk dokunuş izin ekranını açar, ikinci dokunuş kurar.
Listeler `ScalingLazyColumn` oldu: satırlar kadranın kavisinde kesilmiyor, döner
çerçeve listeyi kaydırıyor (ana ekranda halka hâlâ sarma/ses).
Kod: `wear/.../Guncelleme.kt` · `Network.kt` `Sunucu.indir` · `MainActivity.kt`
`HalkaListesi`.

**0.3.5 = ajanda üç adım.** Geçmiş günler ana listeyi kirletiyordu → üçüncü düğme
`Geçmiş`. Veri tek turdan gelir, adım yalnız süzer (`AjandaAdimi` enum'u,
`AgendaScreen.kt`). Poster 130dp → 110dp (`NmDim.AgendaPoster`).

## TV 0.3.5 ve saat 0.1.5'i cihazda dene

İkisi de yayında ama **hiçbiri cihazda görülmedi**. Sunucu tarafı kanıtlı, ekran değil.

**0.3.2 = sarma motoru** (Dean 16 Eylül sabah: "basılı tutunca 8/10/30 atlama durmuyor,
basmadan da ilerlemiyor, bıraktığım yerde devam etsin, OK ile orada durayım"):
- Basışlar hedefte birikir, sağ altta `+2dk 30sn → 1:12:40`, son basıştan 350 ms sonra TEK seek.
- SAĞ'a 3 kez bas → +30 sn tek atlama (çift basış = 1 dk varsayılanı KALKTI, tek basış anında).
- SAĞ basılı tut → 10 sn adım, 1.5 sn sonra 30 sn, 4 sn sonra 1 dk; bırakınca hedefte oynar,
  fazladan 10 sn eklenmez. Sarma sürerken OK → hedefe hemen gider, DURAKLATMAZ.
- Kumandanın ⏪⏩ tuşları aynı motor. Bardaki −30/+30 de birikir.
Kod: `PlayerScreen.kt` `seekBy/seekHold/commitSeek` · `RemoteInput.kt` `onHold` + `longFired=true`.

**0.3.3 = bölüm kimliği + teşhis kancası.** İzleme kaydının `episode` sütununa
liste İNDEKSİ yazılıyordu; Dizilla'nın sızıntılı listesinde 123. sıra kaydedilmiş,
liste 32'ye düzelince panel "Devam et — 123. bölüm" yazmıştı. Artık "S4B8" yazılır
ve listede sezon+bölüm ile aranır (web'in "S4 E8" biçimi de okunur). Sınır dışına
taşan eski indeks kaydı bölüm etiketi GÖSTERMEZ. Kod: `Library.kt` `episodeRef` /
`episodeIndexOf` · test `EpisodeRefTest`.
Oynatma günlüğü ilk gönderimi 6 sn'ye indi ve ekran kapanırken son bir gönderim
yapıyor — 30 sn dolmadan içerik değişince günlük hiç gitmiyordu, `client_log`
bu yüzden boştu. Açılış sebebi de yazılıyor (`uzak komut / onay` ya da
`kullanıcı seçimi`).

**AÇIK ŞİKÂYET — doğrulanmadı:** "kaynak denemesi 4/9'dayken kendiliğinden başka
içerik (Reacher) açıldı". Kuyruk uçları temiz (`/api/v1/remote/poll` boş, TTL 120 sn)
ve oynatıcı açıkken uzak komut onay kartı gösteriyor (`MainActivity.kt:246`), yani
sessiz geçişin bilinen bir yolu YOK. Tekrarlarsa `curl -s localhost:3310/api/v1/client_log`
→ `açılış — ...` satırı sebebi söyler. Kanıt gelmeden kod değiştirilmedi.

**0.3.4 = ajanda penceresi + poster ölçüsü.** Ajanda aralığı bugünden başlıyordu,
yayın günü geçen bölüm listeden düşüyordu — oysa bölüm sağlayıcıya günler sonra
düşebiliyor. Aralık geriye 7 gün açıldı (`agenda.py` `_GECMIS_GUN`). Geçmiş bölüm
`next_episode_to_air`ta YOK, `last_episode_to_air`ta: ikisi de okunuyor, tarih+bölüm
ile tekilleşiyor. Filmler `movie/upcoming`ten geliyordu (yalnız gelecek) →
`discover/movie` + `primary_release_date` aralığı. TV'de poster ızgarası 150dp →
130dp (ana sayfa rafıyla aynı), odak BUGÜNÜN ilk kartına gidiyor, geçmiş gün başlığı
soluk + "yayınlandı".
Kanıt: hafta görünümü 24 → 60 satır, 09-15 Eylül günleri ve film satırları geldi.
**STREAM KODU İMAJDA — `docker compose restart stream` YETMEZ**, `up -d --build stream` şart.

**Televizyonda** (evaitecOTA → NetMovies 0.3.4 / vc 304 → kur):
0b. Ajanda aç → geçmiş günler üstte soluk, ekran bugünle açılmalı, posterler ana
   sayfa boyunda ve satırda daha çok kart olmalı.
0a. Reacher aç → panelde "Devam et — S4B8 · ..." yazmalı, "123. bölüm" DEĞİL.
0. Bir FİLM aç, SAĞ'ı 3 sn basılı tut → gösterge büyüsün, bırakınca tek seferde oraya
   gitsin ve oynasın. SAĞ'a hızlı 3 kez bas → +30 sn. Sarma sürerken OK → orada dursun.
1. Favorilerden **Altı Üstü İstanbul** ya da **Daha 17** aç → oynamalı.
2. **AŞAĞI** tuşu → alt bar; D-pad ile ⏮ · −5dk · −30sn · ▶ · +30sn · +5dk · ⏭ ·
   Bölümler · Dakika · Ayarlar · Ana sayfa · Kapat gezilmeli. Süre + ilerleme barın üstünde.
3. Bardan **Bölümler** → kutucuk ızgarası: önce **Sezon seç**, seçince bölüm kutucukları,
   GERİ bir sayfa geri. Tek sezonluk dizide sezon sayfası atlanmalı.
4. Bardan **Dakika** → sol altta 300dp kutu; `1 2 3 ⌫ / 4 5 6 0 / 7 8 9 ▶ / ⏮ ⏭ 📑 ✕`.
   45 yaz → ▶ → 45. dakikaya gitmeli. Görüntü kararmamalı.
5. `curl -s localhost:3310/api/v1/client_log` → `sunucu·kapsam` satırı denenen
   sağlayıcıların tamamını yazmalı; `ses · tampon boşaldı` hiç olmamalı.

Alt bar açılmıyorsa AŞAĞI eşlemesi değişmiş olabilir: Ayarlar → Buton Eşleme →
Aşağı ▼ → "Alt kumanda barı".

**Saatte** (evaitecOTA → NetMovies Mini 0.1.4 / vc 104 → kur):
6. Açılışta Devam Et posterleri gelmeli. Gelmezse ekran artık "sunucuya ulaşılamadı —
   dokun, yeniden dene" yazmalı (sonsuz "yükleniyor" DEĞİL); dokunuş adres aramasını
   sıfırdan başlatır.
7. 🎙 → "inception aç" (liste gelmeli, dokununca TV'de açmalı) · 🎙 → "sesi kıs"
   (saat ana ekrana dönüp "Ses kısılıyor" yazmalı, TV'de ses düşmeli).
8. ⏩/🔊 düğmesi → çerçeveyi çevir → kip değişmeli (sarma ⟷ ses).

Mikrofon hiç açılmıyorsa: `adb logcat | grep -i recognizer` (Android 11+ paket
görünürlüğü için `client-tv/wear/src/main/AndroidManifest.xml:11-18` `<queries>` var).

## Tekrarlama — ölen yollar

- **`resolve_sources?...&url=`** diye çağırma: uç `encoded_url` ister, `url` verilince
  **410**. `encoded_url` base64 DEĞİL, **tek kez** yüzde-kodlanmış düz URL. Base64
  verirsen "UnsupportedProtocol: Request URL is missing an 'http://'" görürsün —
  kodda değil, çağrıda hata vardır.
- **`fuck_dmca` yanıtı 180 sn cache'li**: düzeltmeden hemen sonra aynı parametreyle
  çağırırsan ESKİ sonucu görürsün. Parametreyi değiştir (`&episode=0&mode=full`) ya da bekle.
- **Bash tool'unda ters bölü yeniyor**: heredoc'a gömülen Python regex'lerinde ters bölü
  kayboluyor ve "incomplete escape" veriyor. Uzun/kaçışlı metni Write tool'uyla ayrı bir
  dosyaya yaz, script o dosyayı OKUSUN.
- **DDizi'yi "bozuk" sanma**: yalnız resmi YouTube yayınlarını çözüyor. Dizi sayfasında
  YouTube yerleşimi yoksa 0 kaynak — tasarım, hata değil.
- **Motor içinde script**: `cd /usr/src/KekikStreamAPI && PYTHONPATH=. python ...` şart;
  `docker exec -w` tek başına `ModuleNotFoundError: Plugins` veriyor.
- **Saat "çalışmıyor" deyince önce TÜNELE bak**: `https://w.evaitec.com/api/v1/health`
  530 ise saat ölü adrese düşmüştür — sunucu uçları yerelden 200 dönüyor olabilir.

- **Wear'a Gemini'ye ses yüklemek** gereksiz: saatte `RecognizerIntent` var ve
  `/api/v1/voice` düz metni de kabul ediyor. Anahtar sunucuda kalır.
- **`/voice` yanıtını görüp ayrıca `/remote/command` atmak**: uç komutu KENDİ
  kuyruğa yazıyor (`sent:true`), ikinci istek TV'ye çift komut gönderir.
- **`gh release create` `--target main` olmadan**: release `/releases` listesine
  düşmez, OTA görmez.
- **Git Bash'te `docker exec -w /usr/src/Stream`**: yol çevrilir, "Cwd must be an
  absolute path" verir. Başına `MSYS_NO_PATHCONV=1`.
- **Bash heredoc ile Kotlin dosyası yazmak**: tırnak yüzünden "unexpected EOF";
  Write tool kullan.

- **Audio offload** denenmedi ve denenmemeli: HLS/TS'de gapless şartı var,
  kazancı pil — televizyonda karşılığı yok. Tunneling yeter.
- **Global WARP proxy** hâlâ reddedilmiş durumda: WARP çıkış IP'si bazı
  kaynaklarda bloklu (`Proxy/Libs/helpers.py:37-38`). Yalnız engellenen istek düşer.
- **Segment cache tavanını 5→20 MB çıkarmak** diye bir iş YOK: tavan zaten 20 MB
  (`segment_cache.py:190`), `video.py:183`'teki yorum eski durumu anlatıyor.
- **Testte `sys.modules`'dan modül silmek** yasak: FastAPI eski fonksiyon
  referansını tutuyor ve BAŞKA testlerin mock'ları sessizce devre dışı kalıyor.
  Depo yolları çağrı anında okunuyor, yeniden yüklemeye gerek yok.
- **Sabit `encoded_url` ile resolve testi yazmak** yanıltıcı: `fuck_dmca` yanıtı
  180 sn cache'liyor, ikinci test birincinin yanıtını görür.
- **Sunucu tarafı transcode (ffmpeg)** kapalı kalsın — imajlarda kurulu değil,
  ev CPU'su kaldırmaz.

Gerekçe zinciri: aşağıdaki **0.13** oturum günlüğü ve `docs/PLAN-oynatma-kalitesi.md`.

## SONRA — kart aksiyonları (Dean'in 14 Eylül gece listesi)

1. ~~"Sonraki/önceki bölüm player'da yok sanırım, dolaşamıyorum."~~ **BİTTİ** (TV 0.3.1):
   alt barda ⏮/⏭ bölüm düğmeleri D-pad ile erişilebilir, bölüm seçimi ayrı kutucuk
   sayfası (`EpisodePicker.kt`). AŞAĞI tuşu barı açar.
2. **"Buton kartta; sadece D-pad ile gezip seçebilmeliyim."** Kart üzerindeki
   aksiyonlar kumandayla dolaşılabilir olmalı.
3. **"Takip/favori gibi butonlar dışarda ya da rahat ulaşılabilsin."** Şu an
   yalnız uzun basma menüsünde (`HomeScreen.kt:318-319`, `menuItem` + ModalCard).
4. **"TV kanalları gibi sağ kaydırda yazısız ikon olsun."** Kanal ekranındaki
   sağa-kaydır deseni poster kartlarına da gelsin, etiketsiz ikonlarla.
5. **"Poster üstü 4'lü buton açılımı basılı tutma ile geri gelsin."** Uzun basma
   menüsü kodda DURUYOR (`onLongPress = { menuItem = item }`) — cihazda neden
   gelmediği doğrulanmadı; önce bu kontrol edilmeli, sonra 4'lü düzene geçilmeli.

> Not: 2-5 aynı bileşeni (poster kartı) konuşuyor; tek tasarım kararıyla
> çözülmeli — kart odakta iken üstünde ikon şeridi, uzun basmada tam menü.
> Oynatıcıdaki çözüm (kutucuk ızgarası + tek giriş noktası) burada da örnek.

## Dean'e sorulan, cevap bekleyen

> Kapandı: üst bardaki sekme adı **"Listem"** kalacak (Dean onayladı).

- **Ses parmak izi başlasın mı?** "Açılışı Atla" kodda hazır ama işaret gelmiyor:
  açılışın altyazıdaki tek izi `♪`, ölçülen bölümde sıfır tane (DiziYou/One Piece
  `tr.vtt`, 620 cue). Bedeli: `ffmpeg` iki imajda da kapalı
  (`engine/Dockerfile:20`, `stream/Dockerfile:20`), dizi başına bir kerelik ~3-5 dk
  indirme+analiz, ilk bölümde çalışmaz. **Önerim: 2. adım doğrulanmadan başlamamak.**
  Ucuz alternatif (Dean bir kez işaretler, sezon boyu) reddedilmedi.
- **Kaynak-yok çıkışı 2.5 sn yeter mi?** (`KAYNAK_YOK_CIKIS_MS`, PlayerScreen.kt)
- **Canlı kanalda "Dakikaya git" kalsın mı?** Canlıda o dakika DVR penceresinin
  dakikası, programın değil — kafa karıştırıyor. Yerine yalnız ⏮/⏭ bırakılabilir.

## Çözülemeyen iki gözlem

- **"İkinci girişte 12. bölümü son bölüm gibi gösterdi"** (Fırtınaya Doğru) ve
  **"Evlilik Güzeldir / MGM logolu film açıldı"**. İkisi de kanıtsız; ilki paneldeki
  normal `⏭ Son bölüm` satırı olabilir. İkincisi büyük ihtimalle DDizi sızıntısı +
  sıra-bazlı eşleştirmeydi (ikisi de düzeldi). Tekrarlarında `client_log`:
  `resolve: arama — <eklenti> · '<sorgu>' → eşleşti: <başlık>`.

## Kanal grupları — kendi eşleme tablomuz (14 Eylül)

> Ulusal rafında TRT 2/3/Türk/Avaz/Kurdî **yok** — Dean favorilerine alıyor.
> TRT 1 kaldı. Geri kalanlar Genel'de.

Artık `Ulusal` (13) · `Bölgesel` (26) · `Genel` (43) ayrı. iptv-org bu etiketi
vermiyordu, tablo `engine/Plugins/M3UPlaylist.py` içinde:
- `_ULUSAL` — ana yayın kanalı adları. Tematik kanallar (haber/spor/çocuk)
  kasıtlı dışarıda, kendi grupları çalışıyor.
- `_ILLER` + plaka kalıbı + `_BOLGESEL_ADLAR` — şehir yayınları.
- Yeni kanal yanlış grupta görünürse düzeltme yeri bu üç küme; kanal adı
  `_sade()` ile sadeleşiyor ("ATV (1080p)" → "atv").

`_SECME_FILM` — yabancı film kanalı beyaz listesi; `categories/movies.m3u#secme`
soneki bu kümeyi süzer ve "Yabancı Film" grubuna yazar. Aynı tvg-id'nin bölgesel
kopyaları (AMC Europe Bulgary/Czech/Hungary) tekilleştirilir.

Genel'de kalan 47'nin bir kısmı hâlâ yerel olabilir (Aksu TV, Cay TV, Er TV,
Ton TV, Line TV, Bir TV…) — adlarından hangi şehir olduğu anlaşılmıyor, elle
doğrulanmadan eklenmedi. Dean cihazda görüp söylerse `_BOLGESEL_ADLAR`'a
bir satır eklemek yeter.

## 0.15 15 Eylül akşamı — DiziMom zinciri, oynatıcı alt barı, bölüm sayfası, saat kilitlenmesi (TV v0.3.1, saat v0.1.4)

Dean'in sorularıyla açıldı: "favoriye aldığım dizilerim oynanabilir kaynak vermedi
nedendir", ardından oynatıcı arayüzü, ardından "saat uygulaması çalışmıyor".

### 1. DiziMom favorileri — iki ayrı kök neden (`fcac4db`)

Favorilerdeki iki yerli dizi (Daha 17, Altı Üstü İstanbul) hiç oynamıyordu.

**FirePlayer embed'inin iki adres biçimi var.** `fireplayer_sources`
(`engine/Plugins/__dizi_common.py`) yalnız `<origin>/player/index.php?...&do=getVideo`
deniyordu. `/tv/` altındaki kurulumlar (peacemakerst.com, hdstreamable.com) orada 404
verip yalnız sayfanın KENDİSİNE (`<iframe>?do=getVideo`) cevap veriyor; linki de
`securedLink`/`videoSource` yerine `videoSources[].file` içinde döndürüyorlar. İkisi de
deneniyor, üç yanıt biçimi de okunuyor (`_fireplayer_stream`). hdplayersystem kök
kurulum olduğu için çalışıyordu — anime çalışıp yerli dizinin çalışmamasının sebebi bu.

**Proxy referer reddeden CDN'e referer gönderiyordu.** Akış `video.twimg.com` üzerinden
geliyor: referer'sız 200, embed referer'ı ile 403. Kaynak bulunuyor ama oynatıcıda
"Upstream Error: 403" çıkıyordu. `_REFERER_REDDEDEN_HOSTLAR`
(`stream/Public/Proxy/Libs/helpers.py`) — adres zaten imzalı, referer gerekmiyor.

Kanıt: iki dizinin 1. ve son bölümü 0 → 1 kaynak; hdplayersystem'li diziler 1 → 1
(gerileme yok); proxy master 200 → varyant 200 → segment 200.

### 2. "Orası her ortamı taramalı" — kapsam elle yazılmış listeden geliyordu (`ab0cbf7`)

Dean: "tek yerden eklemedim, o dizi olarak favorim." Haklıydı: `ALTERNATIVE_ORDER`
9 adlık bir liste ve aynı zamanda KAPSAMI belirliyordu; motorda 16 eklenti yüklü.
DDizi, FullHDFilmizlesene, JetFilmizle, M3UPlaylist zincire hiç girmiyordu — arama
onları buluyor, çözümleme denemiyordu. `tum_saglayicilar()`
(`engine/Public/API/v1/Libs/tarama_sirasi.py`) kapsamı yüklü eklentilerden, sırayı yine
puan/öncelik listesinden alıyor; yetişkin eklentileri `TARAMA_DISI`. Yeni `kapsam`
teşhisi denenen sağlayıcıların tamamını tek satırda yazıyor (istemci günlüğüne düşüyor).

**Kapsam genişleyince gizli bir tuzak çıktı:** `_anlamli_kelimeler` 2 harften kısa
parçaları atıyordu, "Daha 17" → `{daha}` kalınca "Hızlı ve Öfkeli 2 Daha Hızlı Daha
Öfkeli" eşleşme sayılıyor ve yanlış film oynatma listesine giriyordu. Sayılar uzunluk
sınırından muaf edildi + regresyon testi. Ders: tarama kapsamını genişletmek, eşleşme
kesinliğini de sınar.

### 3. Oynatıcı arayüzü (`ab0cbf7`, `fe0b39f`)

Dean: "ileri sar süre yaz tuşu telefon tuşu 4x4 şeklinde koy, altta açılsın; alt barda
play ve ileri geri bölüm geçme tuşlarında gezinme koy, bölümlerde oraya girsin, küçük
ikonlarla belirsin; sol altta çıkabilir tuş takımı, daha küçük, koca ekran kaplıyor."
Ardından: "çok karışık, player içinde liste seçimi iç içe hep geçiyor, tile olsun ya da
sayfa geçiş."

- `QuickPad` sağ alt dikey kutudan **alt bar**a döndü: küçük vektör ikon + altında ad,
  D-pad gezinir. Bar açıkken `ControlsOverlay` çizilmiyor (ikisi de ekranın altına
  oturuyor, üst üste geliyordu) — süre ve ilerleme çubuğu bu yüzden barın üstüne taşındı.
  AŞAĞI tuşu yeni `RemoteAction.OPEN_BAR`'a bağlandı; Ayarlar artık barın içinde bir
  düğme, böylece oynatıcının tek giriş noktası var.
- `SeekScreen` tam ekran + tek sıra 0-9 iken **sol altta 300dp kutu ve telefon düzeni
  4x4** oldu; son satır bölüm gezinmesi. Zemin karartması kalktı, görüntü açık kalıyor.
- Bölüm seçimi **ayrı sayfa**: `EpisodePicker.kt` · `BolumSecici` — kutucuk ızgarası,
  önce sezon sayfası sonra bölüm sayfası, GERİ bir sayfa geri. StartPanel'in içindeki
  sezon rafı (yatay) + bölüm listesi (dikey) kaldırıldı; panelde tek "Bölümler (N)"
  satırı kaldı. Sebep: aynı ekranda iki ayrı yön + panel üstüne panel, nereye basınca
  nereye gidildiğini belirsiz kılıyordu. Sezon durumu PlayerScreen'de tutuluyor çünkü
  GERİ bu ekranda değil, oynatıcının tuş işleyicisinde yakalanıyor.

### 4. Saat "çalışmıyor" — tünel kopuktu, saat ölü adrese kilitlenmişti (`fe0b39f`)

Belirti: yükleniyorda kalıyor + her düğme "gönderilemedi". Sunucu tarafı temizdi
(yerelden tüm uçlar 200, `/remote/command` çerezsiz 200). `https://w.evaitec.com` **530**
dönüyordu: `cloudflared` ağ ad alanı stream'e pinli ve stream bu oturumda yeniden
kurulmuştu. `docker compose --profile tunnel up -d` ile 200'e döndü.

Asıl kusur saatteydi: `Sunucu.taban()` adayları SIRAYLA yokluyordu (kodun kendi yorumu
"paralel" diyordu — yalan) ve hiçbiri cevap vermeyince tünel adresini AYAKTA MI diye
bakmadan hatırlıyordu. Ölü adrese kilitlenen saat bir daha yerel ağı aramıyordu. Artık
paralel yoklama + yalnız gerçekten ayakta olan adres hatırlanıyor; komut başarısız
olursa adres unutuluyor; ulaşılamayınca ekran "sunucuya ulaşılamadı — dokun, yeniden
dene" yazıyor (sonsuz "yükleniyor" değil).

### 5. Yayın kuralı değişti

Dean: "Yayınla sorma artık bitince geliştirme yayınla." Önceki "cihazda denenmemiş sürüm
televizyona güncelleme diye düşmesin" kuralı kalktı. TV 0.3.0 → 0.3.1, saat 0.1.3 →
0.1.4 yayınlandı. TV APK'sı da artık build'de `NetMovies-TV-vX.Y.Z.apk` adıyla çıkıyor
(`094007c`) — saat tarafındaki `6b3a622` düzeltmesinin eşi; elle yeniden adlandırma bitti.


## 0.14 15 Eylül öğleden sonra — saat: sesli kumanda, halka anahtarı, hızlı açılış (saat v0.1.3)

Dean'in isteği tek cümlede: "saat geç yükleniyor, sunucuda tutsun, arama ekranı
sesle arama, çerçeveyle sarma ve ses — tek tuş switch olsun, yükleme için iç içe
halkamızı kullanalım." Hepsi kodda ve üç dağıtım yerinde yayında; **bilekte
denenmedi** (SIRADAKİ İŞ #1).

**Geç açılışın kök nedeni tek `await` idi.** Saat `continue_watching` ile
`aggregate_new`'i tek beklemede istiyordu; soğuk agregasyon ~40 sn sürdüğü için
en çok istenen liste (Devam Et, sunucunun yerel kaydı, 0.157 sn) yarım dakika
ekrana gelmiyordu. İki aşamaya ayrıldı: Devam Et hemen çizilir, Yeni Çıkanlar
arkadan eklenir.

**"Serverda tutsun" → cache ısıtıcı** (`stream/Core/Modules/__init__.py`,
`_cache_isit`). `aggregate_new` cache TTL'i 600 sn; TTL dolduktan sonra ilk
isteyen soğuk bedeli ödüyordu. 480 sn'de bir movie+serie arka planda tazelenir.
Aralık TTL'in ALTINDA olmalı, yoksa arada soğuk pencere kalır. `params`
istemcininkiyle birebir aynı olmalı — cache anahtarı params'tan üretiliyor.
Ölçüm: 1. çağrı 4.83 sn (ısıtıcı uçuştaydı), 2. çağrı **0.062 sn**.

**Sesli kumanda: tanıma saatte, niyet sunucuda.** Saatin kendi motoru
(`RecognizerIntent`, tr-TR) metni verir, metin `/api/v1/voice`'a gider, Gemini
niyete çevirir. Gemini'ye ses yüklemeye gerek yok — anahtar sunucuda kalır,
saate hiçbir şey gömülmez. **Tuzak:** uç komut niyetini KENDİ kuyruğa yazıyor
(`sent:true`); saat ayrıca `/remote/command` atarsa TV'ye çift gider. Yalnız
`action=search` kuyruğa yazılmaz — TV'de ne açılacağına Gemini karar vermez.
Gemini yoksa (503) düz aramaya düşülür: sesli komut sussa bile arama çalışır.
Hafıza: `memory/voice-endpoint-self-enqueues.md`.

**Halka artık iki iş yapıyor.** Döner çerçeve yalnız sarıyordu; ⏩/🔊 düğmesi
sarma (±10 sn) ile ses (±1 kademe) arasında geçiyor. TV tarafı ses için yalnız
işarete bakıyor (`ADJUST_RAISE`/`LOWER`, `MainActivity.kt:258`).

**Yükleme göstergesi kadran kenarında** iç içe iki ters dönen yay — web'deki
`.wp-spinner` deseninin saat hâli (`CerceveHalkasi`, Canvas). Ortası boş: içerik
altında görünmeye devam eder.

**APK adı artık build'de veriliyor** (`NetMovies-Wear-v0.1.3.apk`). Kozmetik
değildi: `app_update.py` hedefi `netmovies-wear-` önekinden, sürümü addaki
`vX.Y.Z`den okuyor; `wear-debug.apk` /data/apk'ya kopyalansa bile sürüm
taşımadığı için sunulmuyordu, her yayında elle adlandırma gerekiyordu.

Kanıt: `:wear:assembleDebug` EXIT=0 (23.178.305 bayt) · 132 test OK · smoke YEŞİL ·
`POST /voice {"text":"10 saniye geri al"}` → `sent:true`, `remote/poll` →
`{"type":"transport","action":"seek","value":-10.0}` · `app_update?target=wear` →
`v0.1.3-poc` · release indirme http 200 · katalog canlı `0.1.3 vc 103`.
Commit'ler: `3d880b1`, `3f753ed`, `6b3a622` · evaglass-releases `66acab8` (push edildi).

## 0.13 15 Eylül — oynatma kalitesi: performans, ses, kaynak seçimi, Gemini

Dean: "performans kayıpları, ses kesintileri, kalite, eklenti seçimleri en iyi
şekilde, otonom olsun." Araştırma + kod keşfi `docs/PLAN-oynatma-kalitesi.md`'de;
dört faz da uygulandı.

**Ses kesintisinin en güçlü şüphelisi ikinci oynatıcıydı.** Scrub önizleme
oynatıcısı (`previewExo`) ekran açılır açılmaz kuruluyor ve AYNI HLS akışını
paralel hazırlıyordu: ikinci kod çözücü, ikinci indirme zinciri — kullanıcı
scrub yapmasa bile. Mi Box sınıfı cihazda ses tamponunu boşaltacak yük tam da
budur. Artık scrub ile doğuyor, scrub ile ölüyor. Yanında: `DefaultLoadControl`
30/90 sn (varsayılan tampon ev upload'ı için kısaydı), tunneling açık (Android
TV'de ses-video aynı donanım hattından), segment hatasında üç deneme
(`DefaultLoadErrorHandlingPolicy(3)` — eskiden tek 4xx kaynağı düşürüyordu).
**Tunneling kendi kendini kapatıyor:** kod çözücü/ses hattı hatasında kalıcı
kapanır ve AYNI kaynak yeniden denenir, kaynak harcanmaz.

**Proxy'de her segment iki upstream isteği yiyordu.** WARP'ın da 403 verdiği
host kaydedilmiyordu: doğrudan 403 → WARP → yine 403, segment başına. Günlük
`↻ WARP denemesi: four.pichive.online · 403` ile doluydu. Artık 10 dk sessizlik.
Ön-yükleme de manifest anındaki ilk 3 segmentle bitiyordu — 20. dakikada ölüydü;
segment zinciri hatırlanıyor, servis edilenin ardındakiler çekiliyor.

**Kaynak sırası elle yazılı listeydi** (`_ONCELIKLI = [DiziPal, DiziMom,
HDFilmCehennemi]`). Zincir ilk çalışan kaynakta durduğu için sıra doğrudan
bekleme süresi; o hafta bozulan sağlayıcı her çözümlemede 25 sn harcıyordu.
Artık puan: başarı +50 / hata −50, 7 gün yarı ömür, yıldızlı sağlayıcı +100.
**Hiçbir sağlayıcı yasaklanmaz** — yalnız sıraya girer. Kanıtı televizyon
veriyor (`POST /api/v1/source_event`): sunucu "link buldum" der, gerçekten
açıldığı yalnız oynatıcıda belli olur. Veri yokken sıra engine'in eski
listesinin aynısı — puanlama ilk gün hiçbir şeyi bozmaz (test edilmiş).

**Kalite varsayılanı hiçbir yere bağlı değilmiş.** `window.DEFAULT_QUALITY`
okunuyordu ama hiçbir yer yazmıyordu; admin'de alan bile yoktu. Panele alan,
`client_config`'e alan, TV'ye `setMaxVideoSize` — ve kaynak geçişinde korunuyor.

**Gemini yalnız zincir boşa düşünce konuşuyor.** Sağlayıcı aramaları harfi
harfine: "the odyssey" sıfır, "odyssey" iki sonuç. Tüm zincir boş dönerse
başlığın başka yazılışları soruluyor (yapısal çıktı, en çok 2 deneme).
Normal akışta hiç maliyeti yok. Anahtar/model aynı yerden — `Libs/gemini.py`
tek kapı, `voice.py` de oradan geçiyor.

**Yapılmadı:** hiçbiri cihazda denenmedi. Kalite tavanı panelde varsayılan
"auto" — Dean seçmeden davranış değişmez.

## 0.12 14-15 Eylül gecesi — kaynak yıldızı, kare kodla APK aktarımı

**Gözat'ta kaynaklara yıldız** (TV v0.2.7 → v0.2.8). 16 eklentilik çip şeridinde
en çok kullanılana ulaşmak için sona kadar gitmek gerekiyordu. Yıldızlılar başta
ve kendi aralarında alfabetik; kayıt sunucuda (`prefs` → `fav_providers`), kanal
favorileriyle aynı yer. **İlk denemede yıldızı SAĞ oka bağlamak hataydı**: çip
şeridi yatay, SAĞ/SOL zaten çipler arasında geziniyor — kanal listesi dikey
olduğu için orada boştaydı, burada değil. v0.2.8'de OK'a basılı tutmaya alındı.

**Telefondan televizyona APK aktarımı** (evaitecOTA 0.1.6 → 0.1.7). Wear veri
katmanı yalnız saat için çalışıyor; TV'ye gönderme yolu yoktu. LocalSend mantığı,
küçük bir HTTP el sıkışması:
- TV: "📥 Telefondan APK al" → dinleyici açılır, adresi KARE KOD olarak gösterilir
  (`ApkAlimActivity`, zxing core). Ekran açık kaldıkça art arda dosya alır, her
  APK kendi adıyla kaydedilip kurulum kapısına gider.
- Telefon: katalogdaki TV uygulamalarında "Televizyona gönder" → kodu okut →
  APK iner ve yerel ağdan akar. Adres hatırlanır; ikinci dosyada kod okutulmaz,
  gönderim hata verirse unutulur (TV'de alım ekranı kapanmış olabilir).
- Çekirdek `appkit/transfer/LocalApkTransfer.kt`: ServerSocket alıcı,
  Content-Length ile tam boyut doğrulaması, HttpURLConnection gönderici.
- **Kasıtlı basitlik:** keşif (mDNS/NSD) yok — kare kod hem daha az kod hem
  "hangi cihaz benim" sorusunu bitiriyor. Eşleştirme kodu da yok (Dean: "aynı ağ
  yeter"); güvenlik sınırı dinleyicinin ömrü — yalnız alım ekranı açıkken ayakta.
- **Cihazda DENENMEDİ** — yalnız derlendi.

**evaitecOTA 0.1.5:** katalog elle tazelenebiliyor (telefonda aşağı çekme, TV'de
"↻ Listeyi yenile") ve `netmovies-mini-watch` 0.1.1'de bayat kalmıştı, 0.1.2'ye
çekildi.

## 0.11 14 Eylül gecesi — Dizilla sezonları, evaitecOTA listesi

**Reacher'da bölüm listesi 124 satırdı, hepsi "1. sezon".** Seçici
`a[href*='-sezon']` `-sezon` geçen her bağlantıyı sezon sayfası sanıyordu; bölüm
adresleri de `-1-sezon-3-bolum` kalıbında olduğu için her bölüm ayrı bir sezon
sayfası sanılıp içindeki bölümler tekrar tekrar toplanıyordu. Artık adres
`-<n>-sezon` ile bitmeli; sezon numarası o adresten, bölüm numarası bölüm
adresinden okunuyor (bölüm adı sayfada yalnız sıra numarası, `season_episode`
metinde "1. Sezon" arayıp bulamıyordu). **124 → 31 bölüm, {1:8, 2:8, 3:8, 4:7}.**

**evaitecOTA listesi sıkılaştı (0.1.4):** kartlar küçüldü, tanıtım satırı yalnız
odaktaki kartta açılıyor — katalog dokuz uygulamayı geçince ekrana üç kart
sığıyordu.

## 0.10 14 Eylül gecesi — dağıtım zinciri ve üst bar (TV v0.2.6, evaitecOTA 0.1.3)

**evaitecOTA TV Mi Box'a kurulmuyordu:** `minSdk 30` (Android 11) istiyordu,
hiçbir Mi Box Android 10'u geçmiyor. minSdk 26'ya indirildi; tek engel
`PackageInfo.getLongVersionCode` (API 28) idi, eski cihazlarda deprecated
`versionCode` alanına düşüyor. Kaynak: `/d/projects/evaitec-appkit/ota-tv`.

**"Kurdu ama Aç yok":** OTA kartı, uygulama kurulu ve güncelken düğmesiz
kalıyordu. Artık açılabilir giriş varsa "Aç" gösteriliyor — TV'de
LEANBACK_LAUNCHER, telefonda normal launcher. TV + mobil **0.1.3**.

**Katalog (`evaglass-releases/apps.json`) bayattı:** `netmovies-tv` ve
`netmovies-phone` 0.1.80'de (11 Eylül) duruyordu — Dean'in "eski gözüküyor,
telefona yüklenmiyor" dediği bu; telefonda daha yeni sürüm kuruluyken katalog
eskiyi verince Android downgrade'i reddediyor. İkisi de 0.2.6'ya çekildi.
Ayrıca girdideki `versionCode` 20005 yazılmıştı, APK'daki gerçek değer 205 —
düzeltildi (sürüm şeması: major*10000 + minor*100 + patch).

**Üst bar yalnız ikon** (v0.2.6): metinli düğmeler dar ekranda satır sarıp
"Aja/nda" gibi kırpılıyordu. Sıra: 🔎 ▦ 📡 🗓 ★ · 📱 ⚙ — Gözat aramanın yanında.

**Morphe Manager (üçüncü parti) incelendi, katalogdan çıkarıldı.** Dean "Android
11 istiyor" diyordu; APK'da öyle bir engel YOK: minSdk 26, TV launcher tanımlı,
imza eski API'lerde geçerli. "Android 11" metni yalnız bir ayarın açıklaması
(`settings_system_process_runtime_description_not_available`). Açılıştaki
takılmanın sebebi doğrulanmadı — cihazdan `adb logcat` gerekiyor.

> Dağıtım artık üç yerde birden: `data/apk/` (uygulama içi OTA) ·
> `evaglass-releases` release'leri · `apps.json` (evaitecOTA). Yeni TV sürümünde
> üçünü de güncelle, yoksa katalog bayat kalıyor.

## 0.9 14 Eylül gecesi — güncelleme önbelleği ve katalog (TV v0.2.5)

**Her güncellemede APK baştan iniyordu.** `downloadApk` tek bir `update.apk`
dosyasına yazıp her çağrıda siliyordu; inmiş dosyanın hangi sürüm olduğu
bilinemediği için saklanamıyordu da. Dosya adı artık sürümü taşıyor
(`update-<tag>.apk`), indirme öncesi `apkHazir()` bakıyor: dosya var ve boyutu
sunucunun bildirdiğine eşitse doğrudan kuruluma gidiyor. Boyut doğrulaması şart
— yarım dosya kuruluma gitmesin. Eski sürüm artıkları yeni indirmede siliniyor.

**evaitecOTA katalogunda NetMovies TV vardı ama 0.1.80'de kalmıştı** (11 Eylül,
13 sürüm geride) — Dean'in "bulamadım" dediği bu. Release
`netmovies-tv-v0.2.5` açıldı (evaglass-releases), `apps.json` → `netmovies-tv`
girdisi ona bağlandı. Katalogdaki `netmovies-phone` girdisi hâlâ 0.1.80; aynı
APK telefonda kumanda olarak çalışıyor, güncellenmedi.

> Üç dağıtım yeri ayrı ve elle: (1) ev sunucusu `data/apk/` → uygulama içi OTA,
> (2) `evatechnosoft/netmovies` release'leri (v0.1.56'da duruyor, kullanılmıyor),
> (3) `evaglass-releases` release + `apps.json` → evaitecOTA. Yeni sürümde (1)
> her zaman, (3) Dean'in evaitecOTA'dan kurması gerekiyorsa.

## 0.8 14 Eylül gecesi — kanal gezme, arama ekranı, ajanda kayması (TV v0.2.4)

**Canlı yayında kanal değiştirmek için çıkmak gerekmiyordu.** YUKARI sonraki,
AŞAĞI önceki kanal — klasik TV davranışı. O iki tuşun canlıdaki eski işi
(scrub / ayarlar) zaten boştaydı: akışın "kaldığın yeri" yok. Dizi ve filmde
davranış değişmedi. Kanal listesi Canlı TV ekranından oynatıcıya taşınıyor
(`ChannelsScreen.onKanallar` → `MainActivity.kanalListesi` → `PlayerScreen`).

**Ajanda ızgarada kayıyordu, iki ayrı sebep:**
1. Hafta/ay SAĞ-SOL tuşuna bağlıydı; ızgarada satır sonuna gelince yatay tuş
   aralığı değiştirip listeyi baştan yüklüyordu. Aralık iki düğmeye taşındı.
2. İlk karta odak HER veri tazelemesinde isteniyordu → aşağı inerken liste başa
   sıçrıyordu. Odak yalnız bir kez isteniyor (`odakVerildi`).

**Arama kendi ekranı oldu** (`SearchScreen.kt`). Büyüteç üst barın SOL BAŞINDA ve
doğrudan aramayı açıyor; Gözat ayrı düğme, kendi arama kutusu yerinde.
- Geçmiş cihazda: `netmovies_search` prefs, en fazla 40, aynı metin tekrar
  aranınca başa taşınır. Üstte son 6 + "Hepsini göster" + "Geçmişi temizle".
- Sorgu ve sonuçlar ekranın DIŞINDA (`SearchState`, MainActivity'de). `remember`
  ekran bileşimden çıkınca ölüyor, GERİ'den dönen kullanıcı aramayı baştan
  yazmak zorunda kalıyordu — `compose-screen-state-dies` dersinin aynısı, bu kez
  aramada. Sonuçtayken GERİ arama geçmişine, oradan ana ekrana döner.

> Bu beş değişikliğin hiçbiri CİHAZDA denenmedi; yalnız derlendi ve testler
> geçti. Kanal geçişinde YUKARI/AŞAĞI kullanıcının kendi buton eşlemesiyle
> değiştirilmişse davranış oradan gelir (Ayarlar → Buton Eşleme).

## 0.7 14 Eylül akşamı — ajanda kullanılabilir oldu (TV v0.2.3 + gateway)

Dean TV'de ajandayı gezdi; dört ayrı arıza, dört ayrı kök neden:

**Odak listeye inmiyordu** ("sadece en üsttekini seçiyor"). Dış Column
`focusable()` idi, odak orada takılıyordu. Odak artık doğrudan ilk karta gider;
hafta/ay değiştiren SAĞ/SOL, odaktaki karttan yukarı kabaran tuş olayıyla
çalışır — kapsayıcının odak alması gerekmiyor.

**OK diziyi açmıyordu.** Ajanda TMDB takviminden geliyor, öğede oynatma adresi
yok; arama zorunlu. Artık sonuç listede bırakılmıyor: başlığı birebir tutan tek
kayıt (ya da tek sonuç) doğrudan açılır. Birden çok sonuçta liste kalır —
yanlış diziyi açmaktansa seçtirmek doğru.

**Geri, ajandaya dönmüyordu.** Gözat'a ajandadan girildiği izlenmiyordu.

**Ajanda liste, geri kalan ızgaraydı.** Ajanda da Gözat'la aynı poster
ızgarasına geçti: gün başlığı tam satır, altında kartlar.

**"Hafta 26 ama bugün 6, ay 36 ama bugün 5"** — kanıtsız değilmiş: hafta ve ay
AYRI TMDB turuyla çekiliyordu, `discover` iki aralık için farklı "ilk N popüler"
listesi döndürüyor, ay haftanın alt kümesi olmuyordu. Artık tek aylık tur
çekilip hafta ondan süzülüyor (`aralikla`, saf fonksiyon — router'ı testten
import etmek dairesel import veriyor, `ajanda_grup` dersinin aynısı). `discover`
sayfa başına 20 kayıt verdiği için iki sayfa okunuyor: bugün iki görünümde de 7,
haftalık toplam 24 → 30.

## 0.6 14 Eylül öğleden sonra — ölü sağlayıcı, kirli katalog, yeni kanallar (engine)

**FullHDFilmizlesene hiçbir filmde kaynak vermiyordu** — denenen dört filmin
dördünde de sıfır. Kök neden `rapidvid`: `av('<şifre>')` çağrısını bırakıp aynı
şifreyi `window._p8` değişkenine taşımış, çözülen yük de düz adres yerine JSON
olmuş (akış `cm`/`tm`, altyazılar `ct`). Şifreleme zinciri değişmedi — ters →
base64 → "K9L" kaydırması → base64 — yalnız taşıyıcı ve yük biçimi değişti; iki
biçim de destekleniyor. Yan kazanç: sağlayıcının kendi Türkçe altyazısı artık
`ExtractResult`'a giriyor, jenerik geri sayımı bunu okuyor.

**Dizilla katalogunda 12 kaydın 7'si dizi değildi:** "Forum", "Gizlilik
Politikası", "İletişim", "Dizi Önerileri", site kökü. `div.grid a` seçicisi
altbilgi ızgarasını da yakalıyordu. Dizi adresi her zaman `/dizi/<slug>`;
kalıba uymayan kart eleniyor. Katalog 12 → 5 gerçek dizi.

**Canlı kanal süzgeci akış adresi başına çalışıyor**, host başına değil: sunucu
ayakta olduğu hâlde tek kanalın yolu 404 dönebiliyor (ATV ve Beyaz TV böyle
ölüydü, host süzgeci ikisini de canlı sayıyordu). İlk hâli fazla katıydı —
tek kötü deneme ATV'yi yarım gün düşürdü; önbellek asimetrik yapıldı: canlı
6 saat, ölü 15 dakika, yoklama zaman aşımı 6 → 12 sn.

**Kanal kaynakları genişledi** (159 → ~173): yurtdışı Türkçe kanallar
(`languages/tur`) ve 7/24 Türkçe film/dizi kanalları (`categories/movies|series`
— beIN Box Office 1-3, beIN Movies Turk/Stars). Kategori listeleri dünya çapında
olduğu için `liste.m3u#tr` ülke soneki eklendi (tvg-id son ekinden okunur) ve
aynı akış birden çok listede geçtiği için URL bazlı tekilleştirme geldi. Aynı
ADLI farklı adres bilerek kalıyor — biri ölürse diğeri kanalı ayakta tutuyor.

**Araştırılıp eklenMEyenler** (tekrar araştırmaya gerek yok):
- Pluto TV · Samsung TV Plus · Plex · Roku: `i.mjh.nz` artık yalnız EPG XML
  yayınlıyor, playlist'ler kaldırılmış (`all.m3u8` → 404, WARP'la da). Üstelik
  TR'de geo-bloklu.
- Tubi · Freevee · Kanopy: resmî uç yok, TR'ye kapalı.
- Archive.org public domain: erişilebilir ama Türkçe içerik/altyazı yok.

**Bilerek dokunulmayan:** `chain_scan --n 2` sonucunda 12 ölü kaynaktan 6'sı
`ABStream` (SezonlukDizi'nin bir sunucusu) — jetonlu adresleri 403 veriyor, dış
sunucu politikası. Aynı içerikte 13 kaynak çıkıyor ve oynatıcı ilk çalışanı
seçiyor; kaynak başına ön yoklama her çözümlemeye 13 istek ekler, getirisi yok.

## 0.5 14 Eylül gecesi — canlı yayın kaydedilmiyor, favori kanallar Listem'de (TV v0.2.2)

**Show TV izlerken Devam Et'te "Catfish" film afişi çıkıyordu.** Kök neden: canlı
kanal da ilerleme kaydı yazıyordu. Kanalın "kaldığın yeri" yok, akış akıp gidiyor;
kayıt hem rafı dolduruyor hem yanlış eşleşiyordu — `quick_channels` kaydında
`poster: null` (kanalın kendi afişi yok), raf da başlığa göre eşleştirme yapıyor.
Artık `exo.isCurrentMediaItemLive` ile hem periyodik kayıt hem çıkıştaki kayıt
atlanıyor. İki şikâyet (rafa düşmesin + yanlış poster) tek düzeltmeyle kapandı.

**Favori kanallar Listem'de** ayrı bölüm olarak, en üstte. Kaynak aynı
(`prefs` → `fav_channels`), ikinci liste tutulmuyor. Satır sade: kanalda bölüm/tarih
yok, adı ve varsa "şu an ne oynuyor" yazıyor; OK ile panelsiz açılıyor.
**Favoriler kendi aralarında alfabetik** (Türkçe `Collator`) — ekleme sırası rastgele
görünüyor, sabit kanalı gözle aramak gerekiyordu.

## 0.4 14 Eylül gece yarısı — arayüz gezinmesi ve canlı yayın (TV v0.1.99 → v0.2.1)

Dean'in TV'de gördükleri üzerine, hepsi arayüz:

**Üst bar gezinme çubuğu oldu** (v0.1.99). Canlı TV, Ajanda ve Listem Ayarlar
menüsüne gömülüydü — en çok kullanılan ekranlar iki adım uzaktaydı. Arama tam
genişlikte bir çubuktu ve bandın tamamını yiyordu. Yeni düzen:
`NetMovies · 📡 Canlı TV · 🗓 Ajanda · ★ Listem · … · 🔎 📱 ⚙`. Ayarlar menüsündeki
girişler kapatılmadı (ikinci yol dursun).

**Ajanda satırları açılabiliyor** (v0.1.99). Kayıtlar TMDB takviminden geliyor,
öğede oynatma adresi YOK — bu yüzden OK doğrudan oynatmıyor, başlığı telefon
kumandasıyla aynı kanaldan (`remoteQuery`) Gözat'ın aramasına düşürüyor. Metinler
tek satıra kırpılıyordu; odaktaki satır tam metni gösteriyor (başlık 2, özet 6),
diğerleri kısa kalıyor ki liste taranabilsin. Kayan yazı tercih edilmedi — TV'de
okumayı zorlaştırır.

**Gezinme paneli sadeleşti** (v0.2.0). Sarma çipleri (±5dk ±1dk ±10sn) kaldırıldı:
sarma zaten SAĞ/SOL ve ⏪⏩'de, panel kendini tekrar ediyordu (Dean: "çok abartı
olmuş"). Panelde kumandadan yapılamayan iki iş kaldı: dakikaya gitmek, bölüm
değiştirmek. `JUMPS`/`onSeekBy` silindi; sarma satırı gidince `firstFocus` sahipsiz
kaldığı için ilk odak ilk rakama bağlandı.

**Kanal panelsiz açılıyor** (v0.2.0): kanalda seçilecek bölüm/kaynak yok, başlangıç
paneli boşuna bir adımdı → `autoplay = true`.

**Canlı yayında DVR** (v0.2.1). Kanal akışı geriye doğru tampon taşıyor (Show TV
~59 dk) ama oraya inmenin yolu 10-30 sn'lik adımlardı. ⏮ tamponun başına, ⏭ canlıya
— yalnız canlı içerikte (`exo.isCurrentMediaItemLive`); dizide bölüm atlama, filmde
±1 dk sarma davranışı korundu. Aynı ikisi Gezinme panelinde satır olarak da var.

> Canlıda "geçen/kalan" süre programın değil **DVR penceresinin** süresidir:
> "5. dakikaya git" o pencerenin 5. dakikasıdır, izlenen bölümün değil. Dean'in
> "5. dk'ya geçti ama sahne farklıydı" gözlemi bu.

## 0.3 13 Eylül gecesi — bölüm listesi eksikti, yanlış bölüm açılıyordu (engine + TV v0.1.98)

Dean TV'de v0.1.97'yi denedi; üç bulgu geldi, üçü de ayrı kök nedendi.

**"1 ve 2. bölüm yok"** (Fırtınaya Doğru, liste 3'ten başlıyordu). Slug filtresi
(0.2) suçsuzdu — sayfada o linkler zaten yoktu. **DDizi bölümleri sayfalıyor**
(`/sayfa-0`, `/sayfa-1`) ve eklenti yalnız ilk sayfayı okuyordu, o da son ~10 bölümü
gösteriyor. Diziye baştan başlamak mümkün değildi. Artık sayfa bağlantıları
izleniyor (`_SAYFA`, en çok `_MAX_SAYFA`=6; bir sayfa düşerse diğerleri yine girer).
Kanıt: Fırtınaya Doğru 10→12 `[1..12]` · Gönül Dağı 10→60 `[163..222]` · Mercan Köşk 2.

**"3. bölüme basınca geri attı"** — iki sebep üst üste binmiş:
- Alternatif sağlayıcıda bölüm **sırayla** eşleştiriliyordu
  (`episodes[episode_index]`, resolve_sources.py). Sağlayıcılar aynı diziyi farklı
  kapsamda veriyor: DDizi listesi 3'ten başlarken DiziMom 1'den başlıyor, yani
  "3. bölüm"e basmak alternatifte BAŞKA bölümü açıyordu. Artık seçili sağlayıcının
  listesindeki **gerçek bölüm numarası** alternatiflere taşınıyor (`episode_no`),
  orada numaraya göre aranıyor, bulunamazsa sıraya düşülüyor.
  Kanıt: `resolve: bölüm — DiziMom · seçilen bölüm 3/12 · bölüm no 3`.
- Diğer sebep beklenen davranıştı: kaynak bulunamayınca otomatik çıkış (0.2).
  Dean çıkışı gördü, sebebini fark etmedi — süre sorusu yukarıda.

> Bu ikisi **sunucu tarafı**: istemci değişmedi, düzeltme APK'sız canlı.

**"Sağlayıcı üst bantta ama ortalarda bir posterde duruyor"** — Gözat'ta kaynak
seçilince `state.shelf` ve dikey kaydırma sıfırlanıyor ama `state.card`
sıfırlanmıyordu: liste başa dönüyor, odak o raftaki ESKİ kart indeksinde kalıyordu.
İki yerde (kaynak seçimi + "Tümü"ne dönüş) `state.card = 0` eklendi.

## 0.2 13 Eylül akşamı — jenerik işaretleri, DDizi sızıntısı, kanal favorileri (v0.1.95 → v0.1.97)

**Bölüm bitince ANINDA sıradakine geçiliyordu** (`STATE_ENDED → goToEpisode`): son
sahneyi kaçıran kişi kendini yeni bölümde buluyordu. "Sonraki bölüm" kartı da sabit
90 sn penceresine bağlıydı, jeneriğin nerede başladığıyla ilgisi yoktu.

Kaynaklar chapter metadata vermiyor, ffmpeg imajlarda kapalı — **altyazı** iki
bilgiyi de bedava taşıyor. Sunucu tarafı: `GET /api/v1/markers` (Libs/markers.py
saf ayrıştırma + Routers/markers.py). Jenerik tespitinde iki tuzak ölçülerek çözüldü
(DiziYou/One Piece, 63 dk):

| Tuzak | Gerçek |
|---|---|
| "Son replikten sonrası jeneriktir" | Jeneriğin ARDINDAN tanıtım cue'su geliyor ("TÜM BÖLÜMLERİ ŞİMDİ İZLEYİN") — son replik jeneriğin BİTTİĞİ yeri işaretliyor |
| "En erken uzun boşluk jeneriktir" | Son çeyrekte 75 ve 89 sn'lik sessiz SAHNELER var; jenerik 160 sn'lik boşluk → **en uzun** kazanır |

Uçtan uca doğrulandı: `credits_start=3555.3` = **59:15**, jeneriğin tam yeri.

Oynatıcıda: jenerikte (işaret yoksa bölüm bitince) 10 sn geri sayımlı kart, GERİ
durdurur ve o bölümde bir daha başlamaz; ilerleme çubuğunda açılış/jenerik
belirteçleri.

**DDizi başka dizilerin bölümlerini listeliyordu.** Dizi sayfasının kenar çubuğundaki
bölümler de aynı `/izle/<id>/...-<n>-bolum-...htm` kalıbında; Mercan Köşk listesine
"daha-17-16-bolum" ve "masterchef-2026-88-bolum" giriyordu (Dean: "1 bölüm
yayınlanmasına rağmen 16 88 gibi sayılar"). Artık slug eşleşmesi şart. Gizli tuzak:
dizi adresinde bölüm numarası slug'ın İÇİNDE kalıyor (`gonul-dagi-171-son-bolum-izle`
→ `gonul-dagi-171`), bölüm adreslerinde kalmıyor — sondaki sayı atılmazsa **filtre
sessizce devre dışı kalıyor**. Gönül Dağı'nda ölçülerek yakalandı.
Kanıt: Mercan Köşk 4→2 `[1,2]`, Gönül Dağı 12→10 `[213..222]`.

> Ölçüm sırasında `/load_item` bir kez 200 (4 bölüm) döndü: **ağ geçidi cache'i**
> (1 saat) eski sonucu veriyordu — eklenti düzeltmesini sınarken `docker compose
> restart stream` şart. Motor 500 de verdi, site kaynaklı geçiciydi (3 denemede 200).

**Durum kutusu** sağ alttaydı, başlangıç paneliyle ve bölüm sonu kartlarıyla üst üste
biniyordu → sol alt köşeye alındı. **Kaynak bulunamayınca** dönen halka dönmeye devam
ediyor ve kullanıcı boş ekranda bekletiliyordu; arama bittiğine göre halka yerine ✕,
ve 2.5 sn sonra kendiliğinden çıkılıyor (geç kaynak gelirse çıkış iptal).

**Canlı TV'de favori + arama** (v0.1.97): 170+ kanalda hep aynı 7-8 kanal izleniyor.
Liste dikey olduğu için **SAĞ ok** boştaydı → favori aç/kapat (oynatıcıdaki "boşta
duran tuşu kullan" deseni). Favoriler listenin başında + `★ Favoriler (N)` çipi.
Kayıt sunucuda (`prefs` → `fav_channels`), yeni tablo/uç açılmadı. Arama Gözat'ın
barını paylaşıyor (`BrowseTopBar` → `NmSearchHeader`), sorgu sunucuya gitmiyor.
Türkçe küçültme `Locale("tr")` ile: varsayılan `lowercase()` "İ"yi birleşik noktalı
i'ye çeviriyor, "İZLE" araması "izle" ile eşleşmiyordu.

**Araştırıldı, uygulanmadı:** Shorebird (kod değişimini APK'sız göndermek) yalnız
Flutter için — TV istemcisi Kotlin/Compose, kullanılamaz; zaten mantığın çoğu
sunucuda olduğu için bugünkü DDizi düzeltmesi APK gerektirmedi. iptv-org zaten bağlı
(`M3U_SOURCES` varsayılanı, `countries/tr.m3u` 174 kanal); `languages/tur.m3u`
eklemenin net kazancı **+15 kanal**, Dean istemedi. Bird IPTV ücretli ($11-16/ay),
M3U bağlantısı `M3U_SOURCES`'a eklenir, kod değişikliği gerekmez.

## 0.0 13 Eylül — kumandayla okunmayan rapor, 30 satırlık bölüm listesi (TV v0.1.91)

Dördü de "TV'de kumandadan yapılamıyor" ailesinden, hepsi ayrı sebep:

| Belirti | Kök neden | Ne yapıldı |
|---|---|---|
| Kaynak raporu okunmuyor, liste başa/sona sıçrıyor | Satırlar `MutedRow` (düz metin) — odak almıyor, D-pad aradan atlıyor | Satırlar odak alır oldu **+** günlük sunucuya gider: `GET /api/v1/client_log` düz metin (telefondan/PC'den okunur), TV 30 sn'de bir yollar |
| 3 sezonluk dizide 30 satır kaydırma, "direkt bölümlere girmiyor" | Ayarlar ve Gezinme ekranı bölümleri **düz** listeliyordu; sezon rafı yalnız başlangıç panelinde | İki düz liste kaldırıldı → tek satır sezon panelini açıyor; panel liste modunda açılınca odak **oynayan bölümde** başlıyor |
| Dublajlı filmde altyazı akıyor (DiziMom) | Cihaz dili Türkçe → ExoPlayer `tr` altyazıyı kendiliğinden seçiyordu | Kaynak `language.rank == 0` (dublaj) ise altyazı kapalı başlar (`isDubbed`) |
| Kumandanın ⏪ ⏩ ⏮ ⏭ tuşları ölü | Medya tuşları D-pad değil → buton eşlemesine girmiyor, `useController=false` olduğu için ExoPlayer de dinlemiyor | Oynatıcıda sabit: ⏪⏩ ±30 sn · ⏮⏭ önceki/sonraki bölüm (filmde ±1 dk) · ⏯/⏹. Ayrıca Gezinme ekranında "Önceki/Sonraki bölüm" satırları |

**Kanıt:** `assembleDebug` + `testDebugUnitTest` BUILD SUCCESSFUL · `smoke.sh` YEŞİL ·
`POST/GET /api/v1/client_log` gidiş-dönüş doğrulandı · APK `data/apk/NetMovies-TV-v0.1.91.apk`
(versionCode 191) OTA'da · tünel 303 (stream recreate sonrası cloudflared yeniden kuruldu).

## 0.1 13 Eylül — kumanda tek basışa indi (TV v0.1.92 → v0.1.94)

Dean çift basış / basılı tutma öğrenmek istemiyor; kumandasında keymapper ile
boşta duran düğmeler (Netflix/Prime) var. Karar: **her şey tek basış + ekranda düğme.**

- **Tuş göstergesi (v0.1.92):** kök `onPreviewKeyEvent`'te her tuş yakalanır, sol üstte
  2,5 sn `AD (kod) → karşılık` şeridi. Kumandanın hangi keycode'u ürettiği ancak böyle
  görülüyordu. Ayarlar → Tuş göstergesi ile kapanır (`netmovies_keymap/show_keys`).
- **Hızlı pad (v0.1.93):** `padAcarMi()` — **başka bir işe bağlı olmayan her tuş** sağ
  altta pad açar/kapar (ses/güç/home/geri/menü/medya hariç). İçinde ±30sn/±5dk, oynat,
  önceki/sonraki bölüm, bölümler, dakikaya git, ayarlar, ana sayfa. Böylece hangi tuşa
  atandığını uygulamanın bilmesi gerekmiyor.
- **Durum yazıları sağ alta (v0.1.93):** ortadaki büyük kutu kalktı; yükleniyor/aranıyor/
  tazeleniyor ve sarma göstergesi tek biçimde sağ altta. Yükleme animasyonu `NmLoader`:
  uçları açık iki halka, ters yönde döner, mavi→yeşil.
- **MENÜ + ana sayfa (v0.1.94):** MENÜ tek basış ayarlar (ACTION_UP'ta), basılı tutma
  tüketilmez → sisteme kalır. Ana sayfa sıfırlaması `MainActivity.anaSayfa` olarak tek
  yere toplandı (telefon kumandası HOME/nav-home + pad aynı lambda).

**HOME tuşu yakalanamaz** — Android `KEYCODE_HOME`'u launcher'a verir, normal uygulamaya
hiç dağıtmaz. Çözüm değil, gerçek: ana sayfa pad'de düğme. Keymapper'da Home'u boş bir
keycode'a alırsan o tuş zaten pad'i açar.

**Kanıt:** `assembleDebug` + `testDebugUnitTest` BUILD SUCCESSFUL (versionCode 194) ·
`smoke.sh` YEŞİL · `/api/v1/app_update?target=tv` → `v0.1.94-poc` · tünel 303.

## Sıradaki iş — hepsi CİHAZDA doğrulama (kod tarafı bitti)

TV'de v0.1.94'e güncelle, bir dizi aç ve sırayla bak:

1. **Pad hangi tuşla açılıyor?** Keymapper'daki Netflix/Prime düğmesine bas; sol üstteki
   şeritte `... → hızlı pad (aç/kapa)` yazmalı. Yazmıyorsa şeritteki **keycode numarasını**
   al → `PlayerScreen.kt: PAD_DISI` / `padAcarMi()` ayarlanır.
2. **Medya tuşları** (⏪⏩⏮⏭) kumandada fiziken var mı, hangi kodu üretiyor.
3. **Dublaj + altyazı:** DiziMom'da Türkçe dublaj bir bölüm — altyazı KAPALI açılmalı
   (`isDubbed`, `language.rank == 0`).
4. **Ses kesintisi:** tekrarlarsa telefondan `http://192.168.1.185:3310/api/v1/client_log`
   aç; TV 30 sn'de bir yolluyor, `ses — tampon boşaldı / çıkış hatası / biçim` satırlarına bak.
5. **Halkalar ve sağ alt yazılar** TV ekranında okunur mu, pad 340dp genişlikle taşıyor mu.

Bulgu çıkarsa kök neden → düzelt → `appVersion` (`client-tv/app/build.gradle.kts:4`) artır →
`./gradlew assembleDebug` → APK'yı `data/apk/NetMovies-TV-v<sürüm>.apk` olarak kopyala.

**Doğrulama:**
```bash
git rev-parse --short HEAD                  # c309342 bekleniyor
bash scripts/smoke.sh                       # kapı YEŞİL
curl -s "http://localhost:3310/api/v1/app_update?target=tv"   # v0.1.94-poc
curl -s -o /dev/null -w "%{http_code}\n" https://w.evaitec.com/   # 303
```
**Not:** `docker compose up -d --build stream` tüneli düşürür (cloudflared netns'i
stream'e pinli) → ardından `docker compose --profile tunnel up -d --force-recreate cloudflared`.

## 0.0 12 Eylül gece yarısı — bölüm seçimi gerçekten çalışıyor (TV v0.1.88)

Bir akşamda dört ayrı katmanda aynı belirti çıktı: "bölüm seçemiyorum / hep aynı
bölüm açılıyor". Dördü de ayrı sebepti, sırayla:

| Katman | Neydi | Kanıt |
|---|---|---|
| **Eklenti** | DiziMom kartı DİZİ değil BÖLÜM sayfası (`...-3-sezon-7-bolum-izle/`) — o sayfada bölüm listesi yok | `load_item` artık `/diziler/...` sayfasına geçiyor → 20 bölüm |
| **Ağ geçidi cache** | `/load_item` **1 saat** cache'li; eklenti düzeltilse de TV eski (boş) yanıtı görüyordu — motor 20 bölüm verirken ağ geçidi 0 | Boş bölüm listesi artık cache'lenmiyor (`_cacheable`); ağ geçidi 20 bölüm |
| **Zincir** | `_links_for` ÖNCE karttaki adresi deniyordu; kart zaten tek bölüm olduğu için hep tutuyor ve seçilen bölüm hiç kullanılmıyordu | `episode=14` ve `episode=19` artık FARKLI master.m3u8 (önce üçü de aynıydı) |
| **Oynatıcı** | `/embed/<id>` iframe'i FirePlayer'a hiç verilmiyordu (yalnız `/video/<id>`) → 1. sezonun tamamı kaynaksız | S1B3 0 → 1 kaynak |

**Arama:** Gözat eklenti eklenti `/search` çağırıyordu — o uç HAM liste döner
(DDizi "walking dead city" için 45 alakasız dizi, xHamster/HQPorner 46'şar).
Süzme + varyant + Özel Koleksiyon elemesi zaten `/search_all`'daydı; istemci artık
onu çağırıyor (39 sonuç, ilk 10'u Walking Dead).

**Diğer düzeltmeler:** ayarlar paneli başlangıç panelinin ÜSTÜNE açılıyordu (iki
modal → odak gidip geliyor, hiçbir satır seçilmiyordu) · telefonda postere dokunmak
içeriği ANINDA TV'ye yolluyordu, artık menü açılıyor · panel boşlukları yarıya indi ·
izleme kaydı dizi başına tutulduğu için 5. bölüm açılırken "7. bölüm · 9:12" deyip o
dakikaya atlıyordu, artık devam etme yalnız kaydın bölümünde uygulanıyor.

**v0.1.89:** "Takip et / bırak" kör satırdı — menü açılınca `/api/v1/following`
okunuyor, satır durumu söylüyor ("Takipte ✓ — bırak"), dokununca sunucunun
döndürdüğü `saved` satıra yazılıyor, menü kapanmıyor; favori satırı da aynı dilde.
Gözat'ta kaynak seçilince odak hep ikinci banda düşüyordu (raflar paralel çekiliyor,
önce dolan odağı kapıyordu) → yedek raflar 900ms bekliyor.
**v0.1.90:** ortadaki geniş paneller ekranı kaplıyordu → oynatıcı paneli sağ kenarda
ince şerit (arkadaki afiş görünür), poster menüsü ve bölüm seçici sağ altta dar sütun;
perde `Scrim` yerine `ScrimSoft`.
**Listelerin yeri:** favoriler ana ekranda "Favoriler" rafı · takip edilenler
Ayarlar → "📋 Listem — Takip Ettiklerim" (Ajanda'yı da bu besliyor).

**Ses kesintisi — İZLEMEDE (Dean: "devam etmiyor, bakarım olursa").** Tekrarlarsa ölçüm yolu hazır: Dean'in telefon kaydında iki gerçek kesinti: 4.5–4.9sn
(~0.4sn) ve 5.2–5.9sn (~0.6sn); seviye −69 dBFS'e (oda tabanı) düşüyor, öncesi/sonrası
−50…−53 dBFS, video akmaya devam ediyor. v0.1.83'te `AnalyticsListener` eklendi
(underrun / AudioSink hatası / ses biçimi değişimi → Ayarlar "Kaynak raporu").
**Cihazda okunmadı.** Sunucu logu bu belirtiyi göremez: segmentler doğrudan CDN'den.

**Tuzaklar (bu oturumda yakıldı):**
- `/load_item` ağ geçidinde 1 saat, `/get_main_page` 30 dk cache'li. Eklenti
  düzeltmesini test ederken **başka bir adresle** ya da doğrudan motordan doğrula.
- `docker exec -w /usr/src/...` Git Bash'te `Cwd must be an absolute path` verir →
  PowerShell'den koş. Bu yüzden bir commit mesajı koşmamış teste "14/14" dedi.
- Bir kaynakta "bölüm seçimi yok" deniyorsa önce **kartın ne olduğuna** bak: katalog
  "son bölümler" besliyorsa kart dizi değil bölümdür.

## 0.0 12 Eylül gecesi — arama, bölüm seçimi, ses teşhisi (TV v0.1.85 · saat v0.1.2)

| Konu | Durum | Kanıt / not |
|---|---|---|
| **Arama alakasız sonuç veriyordu** | ✔ kök neden istemcide | Gözat eklenti eklenti `/search` çağırıyordu; o uç HAM liste döner. "walking dead city" için DDizi 45 alakasız dizi, xHamster/HQPorner 46'şar sonuç döndürüyor. Süzme + varyant + Özel Koleksiyon elemesi zaten `/search_all`'daydı — istemci artık onu çağırıyor (`/search_all` aynı sorguda 39 sonuç, ilk 10'u Walking Dead) |
| **DiziMom kartı = BÖLÜM sayfası** | ✔ | "Son Bölümler" ve arama `...-3-sezon-7-bolum-izle/` veriyor; o sayfada bölüm listesi yok, kart açılınca doğrudan o bölüm oynuyordu. `load_item` artık dizinin sayfasına (`/diziler/...`) geçiyor — dublaj/altyazı ayrımı tıklanan bölüme göre. Tıklanan bölümün sırası istemcide adresten bulunuyor, yoksa zincir 1. bölümü açardı |
| **Bölüm seçimi üç cihazda** | ✔ | TV/telefon: poster uzun-bas → "📑 Bölüm seç (n)" · telefon `remote/play`'e `episode` ekledi · saat: poster'a dokununca dizi ise bölüm ekranı (film ise doğrudan gider) |
| Telefondan gelen dizi | ✔ | `episode < 0` ise TV'de başlangıç paneli açılıyor; panelde "⏭ Son bölüm" satırı. Bölüm listesi zinciri beklemiyor, `load_item`tan geliyor |
| **Ses kısa kısa kesiliyor** | ⚠ teşhis kondu, SEBEP AÇIK DEĞİL | Dean'in kaydında iki gerçek kesinti: 4.5–4.9sn (~0.4sn) ve 5.2–5.9sn (~0.6sn), seviye −69 dBFS'e (oda tabanı) düşüyor; öncesi/sonrası −50…−53 dBFS. Video akmaya devam ediyor. v0.1.83'te `AnalyticsListener` eklendi: underrun / AudioSink hatası / ses biçimi değişimi → Ayarlar "Kaynak raporu". **Sunucu logu bu belirtiyi göremez** — segmentler doğrudan CDN'den çekiliyor |
| Kanıt | ✔ | engine 14/14 · `assembleDebug` + `:wear:assembleDebug` + `testDebugUnitTest` exit 0 · `/api/v1/app_update` → `v0.1.85-poc`, `?target=wear` → `v0.1.2-poc` · `load_item` (DiziMom bölüm adresi) → 33 bölüm |

**Teşhis notu:** bir kaynakta "bölüm seçimi yok" deniyorsa önce **kartın ne olduğuna**
bak — dizi sayfası mı, bölüm sayfası mı. Katalog "son bölümler" besliyorsa kart bölümdür.

**Cache tuzağı:** `load_item` yanıtı ağ geçidinde 180 sn cache'lenir (`fuck_dmca`).
Eklenti düzeltmesini test ederken aynı adres eski sonucu döndürebilir — başka bir
adresle ya da doğrudan motordan doğrula.

## 0.0 12 Eylül akşamı — yer koruma, sonraki bölüm, bölüm listesi (TV v0.1.82)

| Konu | Durum | Kanıt / not |
|---|---|---|
| **GERİ ile diziden çıkınca başka yere atıyordu** | ✔ kök neden bileşimde | Oynatıcı açılınca `BrowseScreen` bileşimden TAMAMEN çıkıyor, içindeki `remember` ölüyordu: kaynak seçimi (DiziMom), kaydırma ve odak sıfırlanıp "Tümü"nün tepesine düşülüyordu. Durum `ui/BrowseState.kt` olarak MainActivity'ye taşındı — dönüşte aynı kaynak, aynı raf, **aynı poster** |
| **Sonraki bölüm** | ✔ | Bitmeye 90sn kala sağ altta teklif kartı, **SAĞ ok** kabul eder; pencere dışında SAĞ hâlâ ileri sarma (buton eşlemesi bozulmadı). `STATE_ENDED`'de kendiliğinden geçiş |
| **Bölüm listesi butonu** | ✔ | Kontrol çubuğunda "Bölümler" (dokunmatik) + kumandada **YUKARI basılı tutma** → yeni `RemoteAction.OPEN_EPISODES` (Buton Eşleme'den değiştirilebilir). Aynı sezon/bölüm paneli oynatmayı kesmeden açılır; orada GERİ yalnız paneli kapatır |
| Kanıt | ✔ | `assembleDebug` + `testDebugUnitTest` exit 0 · `data/apk/NetMovies-TV-v0.1.82.apk` · `/api/v1/app_update` → `v0.1.82-poc` · indirme ucu `200 20101246` |
| **Ana ekranda da aynı tuzak** | ✔ v0.1.82 | `ui/HomePosition.kt` — aynı sebep, aynı çözüm. GERİ ile "en üste dön" akışı korundu: `position.toTop()` odak isteyicisini ilk postere taşır, yoksa GERİ kullanıcıyı kaldığı rafa geri çekerdi |
| GitHub release | — | Çıkarılmadı: OTA `v0.1.56`'dan beri **ev sunucusundan** okunuyor, GitHub yolu isteğe bağlı |

**Cihazda denenmedi:** üçü de ekran/odak davranışı — gerçek testi TV'de. Özellikle
poster odağının geri verilmesi (raf kısaldıysa sona kırpılır) ve SAĞ ok teklifi.

## 0.0 12 Eylül — WARP 503'te zincir donuyordu (engine)

| Konu | Durum | Kanıt / not |
|---|---|---|
| **Zincir 30sn donup 504 veriyordu** (DiziPal) | ✔ kök neden `__dizi_common.fetch_html` | "önce doğrudan, olmazsa WARP" varsayıyordu; DiziPal `_client()` ile **zaten WARP istemcisini** geçiyor → üç deneme de aynı bozuk yoldan (7+12+12sn). Aynı adres doğrudan `curl` ile 200. Artık her deneme farklı istemciyle: verilen → WARP → doğrudan |
| WARP 503'ün kaynağı | bilgi | `gost`, WARP tüneli anlık koparken dial edemeyince 503 döner ([gost#591]). Konteyner 15sn'de bir kendini yokluyor, 3 hatada yeniden başlıyor → kısa boşluklar YAPISAL, yedek yol şart. `httpx` `retries` ayarı proxy yolunda zaten çalışmıyor ([httpx#2988]) |
| Kanıt | ✔ | engine 14/14 · `smoke.sh` YEŞİL (movie 395 · serie 445 · canlı 151 · manifest `#EXTM3U`) · yeni test `engine/tests/test_fetch_html_fallback.py` |
| İstemci tarafı | — | APK'da değişiklik YOK; TV/telefon/saat hiçbir şey indirmeden faydalanır, yeni OTA sürümü çıkarılmadı |

[gost#591]: https://github.com/ginuerzh/gost/issues/591
[httpx#2988]: https://github.com/encode/httpx/discussions/2988

**Teşhis notu:** bir sağlayıcı "uzun süre donup sonra kaynak vermedi" diyorsa önce
**hangi istemcinin geçtiğine** bak — aynı istemciyle üç kez denemek yedek yol değil,
3× timeout demektir.

**Ortam tuzağı:** Docker Desktop'ın `docker-desktop` WSL dağıtımı düşünce `docker`
komutları `500 Internal Server Error ... dockerDesktopLinuxEngine` verir ve yığın
komple kapanır (`localhost:3310` yanıtsız). `wsl -l -v` ile teşhis, Docker Desktop'ı
yeniden başlatmak çözer. Engine içindeki çalışma dizini `/usr/src/KekikStreamAPI`
(`KekikStream` değil).

## 0.0 11 Eylül akşamı — saat uygulaması, oynatma kesilmesi, GERİ

| Konu | Durum | Kanıt / not |
|---|---|---|
| **Oynatma birkaç sn sonra kesiliyordu** (The Ark) | ✔ kök neden proxy'de | alt playlist manifest sayılmıyordu → segmentler ham CDN'e gidiyordu; artık gövdede `#EXTM3U` aranıyor. 1033 segment sarıldı, ilk segment 1.504.376 bayt |
| **Saat uygulaması** — `client-tv/wear` | ✔ cihazda ÇALIŞTI | Wear OS 3+, standalone; halka ile sarma (10 sn/adım), ana menü düğmesi |
| Saat dağıtımı | ✔ | release `netmovies-wear-v0.1.1` + `evaglass-releases/apps.json` → evaitecOTA görüyor |
| Telefon dağıtımı | ✔ | release `netmovies-v0.1.80` + katalogda `netmovies-phone` (aynı APK TV'de de çalışır; telefonda kumanda) |
| Katalog ikonları | ✔ | `assets/netmovies-phone.svg` · `netmovies-watch.svg` — evaglass renk geçişi (#2DD4BF→#6366F1→#EC4899), simge NetMovies'e özgü |
| OTA hedef ayrımı | ✔ | `?target=tv\|wear`, dosya adı öneki `NetMovies-TV-` / `NetMovies-Wear-` |
| Tek GERİ uygulamadan çıkarıyordu | ✔ | çıkış artık GERİ'yi **basılı tutmak**; `dispatchKeyEvent` `repeatCount>0` |
| Gözat'ta GERİ üç basış sürüyordu | ✔ | ara "en üste kaydır" adımı kaldırıldı: arama → sonuç → tüm kaynaklar → ana ekran |
| Gözat'ta yıldızlar yoktu | ✔ | puan zenginleştirmesi `get_main_page`'e de eklendi (Gözat o uçtan besleniyor) |
| Telefondan gelen içerik devam noktasını sormuyordu | ✔ | yarım kayıt varsa `autoplay` atlanıp panel açılıyor |

**Onay kartı tutarsız DEĞİL:** telefondan gelen içerik yalnız TV'de bir şey
oynarken sorar, boş ekranda doğrudan açar (kasıtlı).

## 0.2 SIRADAKİ İŞ

0. **TV'ye `v0.1.90`, saate `v0.1.2` kur ve dene** — panel ölçüleri CİHAZDA
   DOĞRULANMADI. Üç sayı ayarlanabilir: genişlik (`PanelWidth` · menüde
   `DialogWidth * 0.72f`), yükseklik (`fillMaxHeight` 0.92 / 0.70), konum
   (`CenterEnd` / `BottomEnd`). Dean "çok ince / hâlâ uzun / yukarı alalım"
   derse tek sayı değişir. (cihaz işi, ilk sıradaki). Bakılacaklar: Gözat →
   DiziMom → bir dizi → GERİ **aynı posterde mi kalıyor** (aynısı ana ekranda da) ·
   ana ekranda aşağıdayken GERİ hâlâ en üste dönüyor mu · bölüm sonunda SAĞ ok
   teklifi geliyor mu · kontrol çubuğundaki "Bölümler" ve YUKARI basılı tutma
   bölüm panelini açıyor mu (orada GERİ yalnız paneli kapatmalı).
1. **Saat: liste ve bölüm seçme** (Dean istedi, yapılmadı). Şu an tek ekran:
   yuvarlak posterler + yüzey + iki düğme. İstenen: başlıklı/alt alta liste,
   Yeni Eklenenler ayrımı, dizide bölüm seçme. `client-tv/wear/.../MainActivity.kt`.
2. **Saat: Tile (ana ekran karesi)** — evaglass'ta `ShortcutTileService` örneği var
   (`androidx.wear.tiles` + `protolayout`). Modül bağımlılıkları henüz eklenmedi.
3. **Dil rozeti (TR dub/sub) kartlarda** — bilgi sağlayıcıda VAR (FullHDFilmizlesene
   `span.trz`, FilmMakinesi `poster-lang`) ama katalogda taşınamıyor: `MainPageResult`
   yalnız `category/title/url/poster` alıyor, fazladan alan `model_dump()`'ta düşüyor
   (denendi). Eklentiden API'ye ayrı bir taşıma yolu gerekiyor.
4. **İki ölü kaynak** (`chain_scan --n 1 movie`): FullHDFilmizlesene "Örümcek Adam:
   Yepyeni Bir Gün"de kaynak vermiyor · JetFilmizle "Menajerimi Arayın!"da
   `Non-2xx response`.
5. **Telefon için AYRI arayüz** — TV APK'sı telefonda kumanda olarak çalışıyor ve
   katalogda (`netmovies-phone`), ama ekran TV için tasarlandı; `/rc`'deki telefon
   düzeni (iki sekme, klavye, sesli komut) native karşılığını beklemiyor.
6. Sesli komut cihazda hâlâ denenmedi (telefon mikrofonu → WAV yolu).

7. **Saate kurulum ve gönderme göstergesi — BU REPODA DEĞİL.** Cihazda iki kusur
   çıktı, ikisi de yükleyici tarafında: saatte "Kur" düğmesi çalışmıyor
   (`ACTION_VIEW` Wear'da güvenilir değil, `PackageInstaller` Session API gerekiyor)
   ve "saate gönder"de aktarım yüzdesi görünmüyor (`ApkSender.onProgress` var,
   `:ota-mobile` ekranı bağlamıyor). Ayrıntı + kod taslağı:
   `D:/projects/evaitec-appkit/HANDOFF.md` (commit `af06a85`).

**Teşhis notu:** oynatma "başlıyor sonra kesiliyor" derse önce **alt playlist'i indirip
ilk segment satırına bak** — `/proxy/video?` ile başlamıyorsa manifest tespiti kaçmıştır.

**Kurulum notu:** saate APK ulaştırmanın evaitecOTA dışındaki kesin yolu
`adb -s <watch> install -r <apk>`; yükleyici sorunu çözülene kadar bu yol açık.

## 0.0 11 Eylül öğleden sonra — kaynak turu, canlı kanal katmanı, oynatma onarımları

**Katalog:** movie **222 → 389** · serie **364 → 445** · canlı **179 kanal**

| Konu | Durum | Kanıt |
|---|---|---|
| FilmMakinesi `.co`'ya taşındı, eklenti baştan yazıldı | ✔ | oynatıcı `oynatloload.top`, çerez→`/api/video-bilgi` |
| **Sessiz film** — kalite manifestinde ses yok | ✔ | ses ayrı rendition, yalnız MASTER'da; master seçiliyor |
| **Çift proxy sarması** (2. tıklamada 403) | ✔ | cache'teki yanıt yerinde değiştiriliyordu; `{**result}` |
| **Yanlış film** ("örümcek adam" → çizgi film) | ✔ | varyantla ara, doğrulamayı ASIL başlıkla yap; `results[0]` düşüşü kaldırıldı |
| Arama varyantları ("the odyssey" 0 → 18) | ✔ | `arama_varyant.py` · engine 12/12 test |
| **DDizi** (77 dizi) | ✔ | bölümler yayıncının resmi YouTube yayını → yt-dlp |
| **JetFilmizle** (66 film) | ✔ | `film_id` → `/jetplayer` → videopark → worker API |
| **FullHDFilmizlesene** (76 film) | ✔ | `scx` ROT13+b64 → rapidvid → `av()` ters/kaydırma |
| **Canlı kanal katmanı** (panelden düzenlenir) | ✔ | Yönetim → Canlı Kanallar · `Ad \| Adres \| Grup` |
| "TV'ye yaz" ayarının sunucu karşılığı | ✔ | `rc_text_to_tv=false` → uç 	"kapalı" diyor |
| TV: uzun basışla açılan panel kendini kapatıyordu | ⚠ kod var, **cihazda denenmedi** | `consumesPendingUp` |
| **Ajanda** — `/ajanda` + `/api/v1/agenda` + TV ekranı | ✔ | hafta 26 kayıt/8 gün · ay 36 kayıt/14 gün (20 dizi + 16 film) |
| **EPG** — "şu an ne oynuyor" web + TV kanal listesinde | ✔ | XMLTV (epgshare01 TR) · 150 kanalın 27'sinde program |
| **Mini kumanda** — `/mini` (saat / ana ekran kısayolu) | ✔ | yay + yön pad + yüzey modu · PWA kısayolu |

**Canlı kanal kartı nasıl çalışır:** satır satır `Ad | Adres | Grup`. Adres
`.m3u8` olabilir ya da yayıncının **resmi YouTube canlı yayını** (M3UPlaylist
yt-dlp ile çözer). Engine `./lists`'i salt-okunur, stream `/lists` altında
yazılabilir görüyor; dosyanın değişme zamanına bakıldığı için **restart gerekmez**.
Çözülebilen beşi yazılı: Show TV · TRT Haber · NTV · CNN Türk · A Haber.
**ATV / Kanal D / TRT Çocuk canlı ama yt-dlp format listesi boş** (yayın korumalı) —
bunlar için başka adres gerekir, listeye konmadı.

## 0.2 SIRADAKİ İŞ

1. **TV'ye `v0.1.77` kur ve dene** (cihaz işi). Bakılacaklar: OK'i basılı tutunca
   ayar menüsü AÇIK KALIYOR mu ve D-pad ile geziliyor mu · ana menüde **Ajanda**
   açılıyor mu, SAĞ/SOL hafta ↔ ay geçişi çalışıyor mu · Canlı TV listesinde
   kanal adının altında program adı görünüyor mu (▶ ...).
2. Sesli komut cihazda hâlâ denenmedi (telefon mikrofonu → WAV yolu).
3. **Wear OS / Samsung saat NATIVE uygulaması** — yapılmadı. `/mini` sayfası
   telefon ve ana ekran kısayolu için hazır ama Galaxy Watch'ta tarayıcı yok;
   saatte çalışması için ayrı Gradle modülü (Wear OS) ve cihazda test gerekir.
4. **EPG kapsamı** — 150 kanalın 27'si eşleşiyor. Eşleşme kanal ADI üzerinden
   (`Libs/epg.py: sadelestir`); iptv-org adları ile XMLTV adları tutmayan
   kanallarda rehber boş kalıyor. Elle eşleme tablosu genişletebilir.

**Kapanan sorular:** +18 içerik açıkta değil — katalogda ve aramada yok
(`adult_providers`), Özel Koleksiyon'da; oraya **logoya 5 hızlı tık veya 3 sn
basılı tutarak** girilir (`main.js: setupSecretVault`). `vault_pin` boş, PIN
istenmiyor. Knightfall film rafında çünkü **DiziPal onu `/film/` yolunda
yayınlamış**; zincir telafi ediyor (4 kaynak + 18 bölüm, dublaj/altyazı ayrı).

## 0.1 11 Eylül sabahı — kaynak ve zincir onarımı

| Ne | Kanıt |
|---|---|
| FilmMakinesi `.to` NXDOMAIN, `.de` SSL EOF → `filmmakinesi.co` | tema de değişti; kart `a.poster`, tür rafı `/<tur>-filmleri-hd-izle/`, arama WP `?s=` |
| Yeni oynatıcı `oynatloload.top` — JS çalıştırmaya gerek yok | embed'e Referer ile gir → `ultra_embed_auth` çerezi → `/api/video-bilgi/<id>` 1080p+480p + altyazı (çerezsiz `domain_not_allowed`) |
| Arşivin ilk kartları fragman (vizyona girmemiş) | ayırt eden işaret kartta: `poster-lang` rozeti yoksa oynatıcı yok — 20 kartın 9'u eleniyor |
| Arama varyantları | `the odyssey` → 0, `odyssey` → 2 · `search_all "The Odyssey"` 0 → **18 sonuç** |
| Alternatif link toplama paralelleşti | seri döngüde 9 kaynak istemcinin 60sn'sini aşıyordu |
| Proxy sarması cache'i mutasyona uğratıyordu | 1./2./3. çağrı → 1/2/3 kat sarma → 403. `result = {**result}` ile kopya; üç ardışık çağrıda da tek kat, manifest `#EXTM3U` |
| Sayılar | movie **222 → 247** (HDFC 124 · KultFilmler 78 · FilmMakinesi 25 · DiziPal 20) |
| Testler | engine 9/9 · stream 87/87 · `smoke.sh` YEŞİL |

**Yeni testler:** `engine/tests/test_query_variants.py`,
`stream/tests/test_search_variants.py`, `stream/tests/test_resolve_cache_isolation.py`.

**Tuzak:** `aggregate_new` stream'de 600 sn cache'li ve `type=movie` ile
`media_type=movie` **ayrı cache anahtarı**. Katalog düzeltmesi yaptıktan sonra
`smoke.sh` 10 dk daha eski listeyi denemeye devam eder — panikleme, bekle.

## 0. Bu oturumda ne oldu (tek cümle)
**9 Eylül:** Film kaynak turu — **KultFilmler** ve **FilmMakinesi** eklendi, movie
**144 → 313** (HDFC 124 · FilmMakinesi 91 · KultFilmler 78 · DiziPal 20); 12 film artık
iki kaynakta. Keşifte 11 aday elendi (aşağıda). `smoke.sh` artık ilk kaynağın
manifest'ini proxy'den indirip `#EXTM3U` görmeden yeşil demiyor.

**8 Eylül:** Telefon kumandası (`/rc`) baştan sona kuruldu: D-pad + oynatma kontrolü +
arama + TV'ye metin yazma + Gemini ile sesli komut; tünel açıldı ve site PIN kapısına alındı.

**Kumanda iki sekme:** *Kumanda* (D-pad, ⏯/⏪10/⏩30, ses, ⏹, ekran kısayolları) ve
*Ara* (tek kutu: Enter'la telefonda arar, 🎤 sesli, **📺 TV'ye yaz** aynı metni TV'nin
arama kutusuna yollar). Ara sekmesi açılınca **Yeni Çıkanlar** gelir (film/dizi
dönüşümlü). Son ikisi Yönetim → **Telefon Kumandası** kartından açılıp kapanır
(`rc_show_recent`, `rc_text_to_tv`), değerler `admin.json`'da ve `client_config`
ile TV'ye de gider.

## 1. Doğrula (tahmin etme)
```bash
git fetch && git checkout fix/general-stability && git pull   # b858602 bekleniyor
docker compose ps                                  # 5 kap ayakta olmalı
bash scripts/smoke.sh                              # kapı YEŞİL · 13 eklenti · movie 313
docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests   # 78 test
curl -s localhost:3310/api/v1/app_update           # v0.1.57-poc · 20019326 bayt
curl -s -o /dev/null -w "%{http_code}
" https://w.evaitec.com/giris   # 200
```
`docker exec`/curl'de Git Bash yolu ve Türkçe karakteri bozar: `MSYS_NO_PATHCONV=1`
kullan, Türkçe metinli isteği **Python'la** at (curl `sıcak`→`sicak` yapıyor).

## 2. SIRADAKİ İŞ — #1 cihazda, kod işi değil
1. **TV'ye v0.1.57'yi kur ve dene.** Ev ağındayken (OTA tünelden değil LAN'dan iner).
   Bakılacaklar: `/rc` → D-pad ana ekranda geziyor mu · film açıkken ⏯ ve ⏪10 ·
   Ara sekmesinde **📺 TV'ye yaz** metni Gözat'ın arama kutusuna düşürüyor mu ·
   Yeni Çıkanlar listesi geliyor mu · Menü tuşu oynatıcı ayarlarını açıyor mu ·
   mikrofon (yalnız **https://w.evaitec.com/rc**'de, LAN'da tarayıcı mikrofonu vermez).
   Kurulum "Uygulama yüklenemedi" derse: imza uyuşmazlığı, eskiyi kaldırıp kur.
2. **Sesli komut cihazda denenmedi.** Sunucu tarafı kanıtlı (aşağıda), ama
   telefon mikrofonundan WAV üretip gönderen yol (`rc.html.j2: wavYap`) hiç
   çalıştırılmadı — `decodeAudioData` telefonun webm/opus kaydını çözemezse orada
   patlar. İlk gerçek konuşmada tarayıcı konsoluna bak.
3. **TV'de kumanda ayarlarının karşılığını bağla** (Dean: "karşılığı bağlarız").
   `/api/v1/client_config` artık `rc_show_recent` ve `rc_text_to_tv` veriyor ama
   `client-tv` bunları OKUMUYOR — uç hazır, tüketici yok. `ClientConfig` veri
   sınıfına (`data/ApiModels.kt`) iki alan eklenip ilgili ekranlarda kullanılacak.
4. Film kaynakları — ikinci tur (isteğe bağlı): **JetFilmizle** canlı (`jetfilmizle.now`,
   upstream v19) ama site yeniden tasarlanmış, `.kt` seçicileri bayat → orta zorluk.
   **Selcukflix** (upstream IzleAI) katalog veriyor (Dizilla ile aynı macellan altyapısı,
   `Selcukflix(Dizilla)` türetmesi yazılmıştı) ama oynatıcısı `sn.dplayer82.site`
   Cloudflare **403** — httpx, curl, curl_cffi chrome impersonate, WARP hepsi; port
   edilmedi, dosya silindi. **Ölü (tekrar deneme):** FilmModu (domain kumar sitesine
   gitmiş) · FullHDFilm (`.site` NXDOMAIN, `.pro` WARP 429) · SineWix (`ythls.kekikakademi.org`
   yok) · UgurFilm (parklanmış) · Watch2Movies (NXDOMAIN + WebView bağımlı) ·
   SetFilmIzle (Plesk) · SuperFilmGeldi (410) · SinemaCX / WebteIzle (NXDOMAIN).
5. Yeni film kaynaklarında **admin görünürlüğü ve kalite etiketi** kontrol edilmedi
   (KultFilmler "KULT Altyazılı" tek kalite; FilmMakinesi master'ında çoklu varyant var mı bakılmadı).

## 3. Bu oturumda kanıtlanan (tekrar denemene gerek yok)
| Ne | Kanıt |
|---|---|
| KultFilmler zinciri | `a.mcard` kart · `kf-srcdata` JSON · FirePlayer `getVideo` → `securedLink`; manifest iframe Referer'sız 403, WARP'tan 403 → `_ALWAYS_PROXY_PLUGINS`; proxy'den 2 film `#EXTM3U` 200 |
| FilmMakinesi zinciri | `filmmakinesi.to` (`.de` ölü), kısa UA 403 → tam Chrome UA; closeload embed'de `sources:[{file: <var>}]` → `_js_player` tüm inline script'leri çalıştırıyor; JSON-LD `contentUrl` sahte (404); master Referer'sız 404 → proxy; 2 film `#EXTM3U` 200 |
| Eski film zinciri hâlâ sağlam | HDFC ×3 + DiziPal ×4: manifest → segment 94–300 KB gerçek veri |
| Uzun-yoklama | boş kuyrukta `wait=5` → 5.009s, komut varken → 0.008s |
| Komut şeması kapalı | `{"key":"POWER"}` → `gecersiz key: POWER` |
| PIN kapısı (tünelden) | `/` → 303 · `/giris` → 200 · PIN'siz `remote/command` → **401** · çerezle → 200 |
| Sesli niyet | 7 cümlenin 7'si doğru (seek −10 / seek 300 / volume −0.2 / play_pause / search / nav / none) |
| APK içeriği | dex'te `RemoteBus`, `tusGonder`, `remoteQuery`, `kumandaMetni` VAR |
| Arama temizliği | "inception" 60 sonuç → **1** (Özel Koleksiyon + sorguyu yok sayan kaynak elendi) |
| Testler | stream 78/78 · engine 4/4 · client-tv 0 fail + assembleDebug |

## 4. Yapma / bir daha düşme
- **Azure'da vault arama.** `evaitec-shared-kv` dahil 8 vault 8 Eylül 12:35'te
  **maliyet için bilerek silindi**; kurtarma yok. Gemini anahtarı yalnız
  `/data/admin.json → gemini_api_key`'de. `~/.ai/vg.env` pointer'ı ölüydü, yorumlandı.
- **Model adını tahmin etme.** `gemini-2.5-flash` bu anahtarla **429 kota** veriyor.
  Çalışan: `gemini-3.5-flash-lite`. Gerçek liste:
  `GET https://generativelanguage.googleapis.com/v1beta/models` (`x-goog-api-key`).
- **`AUTH_USER`/`AUTH_PASS` DOLDURMA.** TV istemcisi Basic Auth taşımıyor, 401 alıp
  katalogsuz kalır. Koruma çerezle: `Core/Modules/_pin.py`.
- **Yeni istemci ucu eklerken sor: TV mi çağırıyor, tarayıcı mı?** Tarayıcıysa
  `_pin.py: _KORUMALI_API`'ye ekle; TV'ninkini eklersen televizyon kırılır.
  **Ev ağı muaf (`0d15a6e`):** Host özel IP/localhost ise kapı yok (`lan_istegi`) — telefondaki
  uygulama çerezsiz `remote/play` atıp 401 alıyordu. Tünelden (w.evaitec.com) hâlâ PIN şart;
  uygulama tünel üzerinden TV'ye komut gönderemez (çerez yok) — evde LAN'dan çalışır.
- **`Permissions-Policy`** mikrofonu sessizce öldürüyordu (izin penceresi hiç
  açılmıyordu) → `microphone=(self)` yapıldı, geri alma.
- **Stream'e her dokunuş tüneli düşürür** — recreate'te 530, sadece `docker restart`'ta
  **502**. Kurtarma (stream'e dokunmadan):
  `docker compose --profile tunnel up -d --force-recreate --no-deps cloudflared`
- Tünelden 20 MB APK indirme bağlantıyı doyurup tüneli geçici düşürüyor; TV zaten
  LAN'ı önce deniyor.
- Sanal fare geri gelmesin (`2312937`) · Vault PIN yapılmadı · RecTV'yi geri ekleme.

## 5. Önce oku (sırayla)
1. `stream/Public/API/v1/Routers/remote.py` — komut şeması ve kuyruk, her şeyin merkezi
2. `stream/Public/Home/Templates/pages/rc.html.j2` — kumanda arayüzü + ses yolu
3. `client-tv/.../MainActivity.kt` — tek yoklama döngüsü, `tusGonder` ile KeyEvent enjeksiyonu
4. `stream/Core/Modules/_pin.py` — kapının neyi koruyup neyi korumadığı

## 6. Yeni TV sürümü çıkarma
```bash
# build.gradle.kts içindeki `val appVersion` TEK KAYNAK — versionCode ondan türer.
cd client-tv && ./gradlew testDebugUnitTest assembleDebug && cd ..
cp client-tv/app/build/outputs/apk/debug/app-debug.apk data/apk/NetMovies-TV-vX.Y.Z.apk
curl -s localhost:3310/api/v1/app_update      # yeni sürümü göstermeli
```
Yerel OTA bu kadar. GitHub release İSTEĞE BAĞLI — anonim `/releases` listesi
dakikalarca gecikir, **silip yeniden oluşturma, bekle**; doğrulamayı `gh api` ile yap.


---

## 📋 7 Eylül 2026 (öğle) — kaynak genişletme turu (EN SON, v0.1.54)

**Ne yapıldı:** Dizi/film kaynağı iki katına çıktı, ölü kaynak düştü, açılışa
sağlık raporu eklendi.

- **SezonlukDizi** (`fab3df3`) — bölüm başına dublaj + altyazı ayrı oynatıcı listesi,
  yani aynı bölüm için gerçek bir yedek. Portta üç tuzak çıktı: windows-1254 charset
  (Türkçe başlıklar bozuluyordu), WAF'ın eksik parametreye 403 vermesi + arama
  sorgusunun cp1254 kodlanma zorunluluğu, ve 12 oynatıcının ardışık çözülüp 25 sn'lik
  alternatif bütçesini doldurması.
- **RecTV düştü** (aynı commit) — `b.prectv36-60` hem doğrudan hem WARP'tan ölü,
  upstream `.kt` de hâlâ ölü 38'i gösteriyor. Zaten admin'de gizliydi; canlı TV
  M3UPlaylist'ten geliyor, katalogdan hiçbir şey eksilmedi.
- **DiziPal** (`1637968`) — turun en büyük kazancı. Site v57 ile yeniden yazılmış,
  upstream `.kt` tamamen bayat. Oynatma adresi sayfaya AES şifreli gömülü:
  `[data-rm-k]` → PBKDF2-SHA512(999, 32B) + AES-CBC (parola `pageload.js`'te açık)
  → iframe → `openPlayer('<blob>')` → `source2.php` → `m.php` yerine `master.m3u8`.
  Bölüm listesi HTML yerine JSON-LD'den okunuyor. Domain keşfi imza doğrulamasına
  bağlandı; terk edilmiş adresler 200 döndürdüğü için ilk denemede `main_url` boş bir
  kabuğa (2200) kaymış ve katalog sessizce boşalmıştı.
- **Video proxy WARP yedeği** — kaynak bulunuyor ama oynamıyordu: VidMoly Türkiye'den
  doğrudan 403, DiziPal CDN'i Referer'sız 404 veriyor. Proxy artık 403/451'de isteği
  WARP'tan tekrarlıyor (host bazlı hatırlar), iki sağlayıcı proxy'ye zorlandı.
- **Açılış kaynak raporu** (`9054b3e`) — domain taşındığında katalog sessizce küçülüyor
  ama hiçbir yerde hata görünmüyordu. Açılış betiği `plugin_health`'i taze soruyor,
  sorun varsa kökte `KAYNAK-UYARI.txt` bırakıyor (dosyanın VARLIĞI uyarıdır).
- **v0.1.54-poc yayında** (`ce17349`) — APK'da kod değişikliği YOK; istemci kaynak
  listesini sunucudan aldığı için v0.1.53 kurulu cihaz da yeni kaynakları görüyor.

**Katalog:** movie 124 → 144 · serie 210 → 335 · yerli 25 → 48 · yabancı 10 → 35.

**Araştırıldı, sonuç olumsuz:** KekikStream pip paketinin `Plugins/` klasörü boş,
`KekikStreamAPI` reposunda `Plugins/` yok — hazır Python eklenti havuzu diye bir şey
yok, her kaynak Kotlin'den elle port. `Kekik-cloudstream`'de toplam **44 klasör** var
(200+ değil). WatchBuddy = KekikStreamAPI fork'u + provider boilerplate'i; merkezi
eklenti listesi barındırmıyor, herkes kendi provider servisini ayrı yayınlıyor.

**Dokunulmadı:** cihaz doğrulaması (Dean'e bağlı), tünel, içerik detay ekranı,
Gözat/arama puanları.

---

## 📋 5 Eylül 2026 (öğle) — kalıcılaştırma turu (v0.1.50)

**Commit'ler:** `f52be9c` · `f6265a1` · `8d1f512`

Dean: *"pm olarak kıdemli konuştur ve artık bozulmayacak şekilde yapalım, pi olarak
sahiplen ve fable mode da kanıtlarla yap"* → dört kırılma ekseni (oynatma / kaynak
sağlığı / erişim / regresyon) teşhis edildi, Dean "hepsini değerlendir" dedi; tünel
sonradan kapsam dışına alındı.

### Film başa dönmesi — kök neden
Kaynak düşünce `currentLinkIndex++` yeni `prepare()` tetikliyor, o da konumu sıfırlıyordu;
devam-etme `resumeApplied` ile yalnız ilk hazır oluşta uygulandığından ikinci kaynakta
seek hiç çalışmıyordu. Geçişte `carryOverMs = exo.currentPosition`, prepare sonrası geri
verilir. **Cihazda doğrulanmadı** — derleme ve testler yeşil, gerçek TV'de bakılacak.

### Devam Et'te çift poster
Dean: *"2 sağlayıcıdan olan filmler devam ederken 2 tane poster oluyor fakat aynı süre"*.
Tahmin sağlayıcı çokluğuydu, gerçek neden `content_key`'in `media_type` içermesi: canlı
veride `gorge|movie` (3763sn) ve `gorge` (2884sn) yan yanaydı. Tür anahtardan çıkarıldı,
`canonical_key()` hazır anahtarın son ekini kırpar, `_migrate_type_suffix()` açılışta
mükerrerleri birleştirir (en son güncellenen konum kazanır). DB **8 → 7** satır.

### Ölü kaynak zinciri yormuyor
`run_plugin_health` süzmesi `resolve_sources`'a da geldi + sağlayıcı başına 25sn bütçe.
Sağlık bilinmiyorsa hepsi denenir. Teşhis: `atlanan sağlıksız kaynak: HQPorner, RecTV`.

### Bir daha kırılmasın diye
- `.github/workflows/gate.yml` — repoda hiç CI yoktu; her push'ta üç iş koşuyor.
- `val appVersion` tek sürüm kaynağı — v0.1.49'da versionCode 48'de kalmıştı.
- `scripts/netmovies-autostart.cmd` + Startup kısayolu — Docker Desktop `AutoStart: False`
  olduğu için PC açılışında yığın hiç kalkmıyordu.

---

## 📋 4 Eylül 2026 (gece, devam) — Listem/takip takvimi + gezinme ekranı (v0.1.48)

**Commit:** `c8cf6eb`

Dean: *"ileri geri sarma, süre girme, bölüm seçme, kalanları daha net gösterme — ayrı
ekranda o · listem olabilir, takip ettiklerim, yeni yayınlanacak tarihleri, sonraki
bölüm günü bilgi olarak liste olsun, Türkçe ve yabancı · uygulamaya göm, aynı uygulama
içinde komut veririm ararım"*.

### Listem — Takip Ettiklerim
`GET /api/v1/following` (`Routers/following.py`): `watch_store` "takip" listesindeki her
başlık TMDB'de eşleştirilip `next_episode_to_air` ile döner. **Türkçe/yabancı ayrımı TMDB
`origin_country`'den** — kaynak sitenin kategorisinden DEĞİL (aynı dizi farklı sitede
farklı kategoride). Takvim 6 saat bellekte; `TMDB_API_KEY` yoksa liste yine döner, yalnız
tarihler boşalır. TV: `ui/FollowingScreen.kt`, satır biçimi
"18 Eylül Cuma · 3 gün sonra · S5B1 · 139. Bölüm"; tarih yoksa dizinin durumu yazılır
(boş satır "eksik mi, bölüm mü yok" ayrımını gizliyordu). Takip etme: poster uzun-bas.

`user_lists` tablosunda da `content_url` YOKTU (favorilerdeki aynı eksik) → idempotent
ALTER + `toggle_user_list`/router parametresi. Bu üçüncü tabloydu; şema eklerken üçünü
birlikte düşün.

### Gezinme ekranı
`ui/SeekScreen.kt`, oynatıcı Ayarlar → 🧭 Gezinme: geçen/**kalan**/toplam, ±10sn ±1dk
±5dk, rakam tuşlarıyla "dakikaya git", bölüm listesi. Kontrol şeridine de kalan süre
eklendi. Kalan süre hiç yazmıyordu ve belirli dakikaya gitmenin yolu yoktu.

### Telefon kumanda modu
Manifest zaten telefonu destekliyordu (leanback `required=false`), ayrı uygulama
gerekmedi: `MainActivity`'de tek `pick` fonksiyonu — cihaz TV değilse seçim
`remote/play`'e gider, Toast ile bildirilir. **Yoklama yalnız televizyonda** (`isTv`
kontrolü `HomeScreen`'de): telefon da yoklasaydı kendi komutunu yakalayıp kendinde açardı.

### Ölçümler
`/following` → Kızılcık Şerbeti 2026-09-18 S5B1 (turkish), The Last of Us (foreign) ·
`user_lists` content_url sütunu doğrulandı, test kayıtları silindi · stream 58 test ·
TV 18 test · derleme yeşil.

---

## 📱 4 Eylül 2026 (gece) — Oynatıcı onarımı + telefondan TV'de oynat (v0.1.45→v0.1.47)

**Commit'ler:** `3cbb526` · `f757077` · v0.1.47 commit'i

Dean cihazda denedi: *"atlar gibi geziyor · kalite butonu gelmedi · admin panel yok ·
devam et başlıyor sonra başa alıyor · playerda tuşa basınca ayarlara girmiyor, 2 kere
basınca giriyor · kurulum başladı diyor ama ekrana düşmedi · mor oynatıcı çerçevesi ·
telefondan seçip TV'de oynatalım, yansıtma değil kontrol olsun"*.

### Kök nedenler (hepsi sessiz arıza)
- **Başa alma:** `LaunchedEffect(links, currentLinkIndex)` — `absorb()` alternatif
  kaynakları kuyruğa ekleyince liste değişiyor, oynayan kaynak aynı olmasına rağmen
  `setMediaSource + prepare` yeniden koşuyordu. Anahtar artık oynayan linkin URL'i.
- **İki kere basma:** kök kutu odağı tek `requestFocus()` ile isteniyordu, ilk karede
  düşüyordu. Kare kare denenir; `showSettings/scrubMode/ready` değişiminde geri alınır.
- **Film ortasında kopma:** proxy jetonu 15 dk ömürlüydü → segment 403 → "çalışan kaynak
  bulunamadı". 6 saat, `PROXY_TOKEN_TTL` ile ayarlanır.
- **Kurulum ekranı gelmiyor:** `ACTION_VIEW` TV'de yutuluyordu → `PackageInstaller`.
- **Admin okuması hiç çalışmıyormuş:** `/api/admin/config` ADMIN_PASS korumalı, istemci
  401 alıp sessizce yerleşik listeye düşüyordu → yeni auth'suz `/api/v1/client_config`.
- **media_type uyuşmazlığı:** TV boş gönderiyordu, web `movie`/`serie` → aynı film iki
  ayrı `content_key`. TV artık web'in kuralını kullanıyor.

### Yeni: telefondan TV'de oynat
`POST /api/v1/remote/play` + `GET /api/v1/remote/poll` (`Routers/remote.py`), tek slotluk
BELLEK kuyruğu, 120 sn TTL. Web `content.html.j2`'de "TV'de oynat" düğmesi; TV yalnız ANA
EKRANDA yoklar (oynatıcı açıkken izlenen film telefondan değişmesin). Yansıtma değil:
akışı TV çözer. Gerekçe hafızada: `phone-to-tv-remote-play`.

### Ayrıca
Yumuşak odak kaydırması (`BringIntoViewSpec`, kenarda %22 tampon), sıkı yerleşim
(RowGap 22→12, başlık 19sp→15sp), kalite bölümü tek varyantta da görünür, TV'de
🛠 Yönetim Paneli (WebView + kumandayla parola), OTA durumu Ayarlar'da satır olarak,
oynatıcı zemini saf siyah + mor çerçeveler nötrlendi.

### Ölçümler
`remote/play` → `poll` zinciri doğrulandı (komut okununca kuyruk boşalıyor) ·
`client_config` auth'suz 200 · stream 58 test · TV 18 test · `smoke.sh` yeşil.

### Açık kalan
Cihazda hiçbiri çalıştırılmadı. Mor çerçeve için oynatıcı zemini siyaha çekildi ve
Primary kenarlıklar nötrlendi; hangi çizginin kaldığı Dean'in ekranında görülmeli.

---

## 🖥️ 4 Eylül 2026 (akşam) — TV UI onarımı + izleme senkronu (v0.1.40→v0.1.44)

**Commit'ler:** `95be460` · `2312937` · `b5b2a06` · `7133e45` · `86fea9e`

Dean sırayla: *"çok fazla aşağı liste var, geri dönmek zor · kanallar iç içe ·
biraz aydınlansın · poster odakta küçülsün"* → *"mouse kaldıralım ya da düzeltelim"* →
*"OTA patch"* → *"watchbuddy yapısına bakıp eksikleri listele"* → *"docker çalıştır,
kanıtla, bitir"* → *"özel koleksiyon açılmıyor, ayarlar açılmıyor"*.

### Ne yapıldı
- **Gözat kaynak çipleri** (`95be460`): tüm eklentilerin kategorileri alt alta 40+ raf
  oluşturuyordu. `Tümü + eklenti` çipleri; GERİ zinciri arama → sonuç → en üste → Tümü → çık.
- **Odak büyüteci küçültmeye çevrildi**: `FocusScaleCard 1.14f → 0.97f` (komşu kartları eziyordu).
  Zeminler bir kademe açıldı.
- **Sanal fare kaldırıldı** (`2312937`) — gerekçe §4.
- **İzleme senkronu** (`b5b2a06`): sunucudaki 10 uç web tarafından kullanılıyordu, TV hiçbirine
  dokunmuyordu. Library artık `/favorites` + `/continue_watching` okuyor; SharedPreferences
  yalnız önbellek; yereldeki favoriler ilk açılışta bir kez `addFavorite` (idempotent) ile
  taşınıyor. Oynatıcı 15 sn'de bir + çıkışta `POST /progress`, açılışta 30sn–%92 aralığında
  otomatik seek. "İzlenenler" → **Devam Et** + ilerleme çubuğu.
  Sunucuda `favorites` tablosunda `content_url` YOKTU (favori açılamıyordu) → idempotent
  `ALTER` + parametre; boş URL kayıtlıyı ezmiyor. `docs/VENDOR.md` madde 6.
- **Kalite seçimi** + **raf sayfalaması** aynı commit'te.
- **Gizli kaynak listesi sunucudan** (`7133e45`): `/api/admin/config`. Filtre İKİSİ BİRDEN —
  sunucu tam ad, yerleşik yedek parça eşleşmesi; yalnız sunucuya güvenilse listede olmayan
  yeni kaynak sızardı.
- **Ayarlar + Özel Koleksiyon onarımı** (`86fea9e`): kök nedenler §5 ve §4'te.

### Ölçümler
`POST /progress` → `{"ok":true,"content_key":"inception|2010|movie"}` · favori `content_url`
ile dönüyor · HQPorner 5 kategori / 46 içerik · sayfalama 3/4 kaynakta çalışıyor ·
`smoke.sh` yeşil · stream 58 test · TV 18 test.

### Açık kalan
TV cihazında hiçbiri çalıştırılmadı — kanıt sunucu yanıtları, birim testleri ve derleme.
Ayarlar'ın gerçekten açıldığı Dean'in cihazında görülmeli (§3 madde 1).

---

## 🎛️ 3 Eylül 2026 (2. oturum, devam) — GERİ tuşu + OTA teşhisi (v0.1.35-poc)

**Commit:** `5edae3e` · **Release:** `v0.1.35-poc`

Dean: *"geri tuşu direkt çıkıyor, listede aşağı inince bir üst satıra çıkmalı ·
[uygulama] açmıyor · güncelleme de hiç gelmedi."*

### 1. GERİ tuşu — ana ekranda BackHandler HİÇ YOKTU
Sistem geri tuşu doğrudan uygulamayı kapatıyordu; kullanıcı raflarda gezerken
yanlışlıkla çıkıyordu. Artık:
- **HomeScreen**: liste aşağıdaysa GERİ → en üste kaydırır + odağı ilk karta verir;
  en üstteyken çıkar (`onExit`, MainActivity `finish()`).
- **BrowseScreen**: arama/sonuç açıksa onu kapatır → değilse en üste döner → en üstte ana ekrana.

### 2. "Güncelleme hiç gelmedi" — kök neden: hata SESSİZCE yutuluyordu
`UpdateViewModel.check()` içi `catch (_: Exception)` idi. Ağ kesintisi, **GitHub hız
sınırı** (kimliksiz istek **saatte 60, IP başına** — ev ağı ve geliştirme testleri aynı
IP'yi paylaşıyor) veya bozuk yanıt olduğunda ekranda hiçbir iz kalmıyordu.
Artık: banner'da görünür hata + "Tekrar" · her adım `PlaybackLog`'a yazılır ·
**Ayarlar → "Sürüm: v0.1.35-poc"** satırı + **"Güncellemeyi kontrol et"**.
⚠ Bu teşhis yalnız v0.1.35+ ile gelir; eski APK hâlâ sessiz kalır (elle kurulum gerekebilir:
release sayfasındaki APK).

### 3. Eski istemciler de imzalı kaynakları oynatsın
`route_through_proxy` ortak modüle alındı (`stream/Public/API/v1/Libs/source_proxy.py`)
ve **`load_links`** ucunda da uygulanıyor. Böylece güncelleme alamamış APK'lar bile
HDFilmCehennemi'yi oynatabiliyor — imza (X-Sp) ve Referer işini sunucu yapıyor.

**Kanıt:** stream **48/48** OK · client-tv **5/5** OK · `assembleDebug BUILD SUCCESSFUL` ·
smoke **kapı YEŞİL** · yeni yol `master/varyant/segment 200 (2.4MB)` ·
eski yol `load_links → proxied: True` · GitHub API en yeni `v0.1.35-poc`.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi**: geri tuşunun yeni davranışı, güncelleme şeridinin görünmesi.
- OTA'nın Dean'in cihazında **neden** gelmediği hâlâ kesin değil (hız sınırı en olası;
  artık ekranda sebebi yazacak).
- "Açmıyor" şikâyeti muhtemelen eski APK + eski `load_links` yolundandı; sunucu tarafı
  düzeltildi ama cihazda doğrulanmadı.

---

## 🎬 3 Eylül 2026 (2. oturum, devam) — HDFilmCehennemi `.now`: site taşındı + tema değişti

**Commit:** `6960e50`

Dean ekrandan gördü: *"hdfilmcehennemi.now'da diziler de var, ekle; ama `setplay.shop/player/…`
açılmıyor (`ERR_CONNECTION_REFUSED`)."*

### Kök neden
Site `hdfilmcehennemi.nl` → **`.now`** taşınmış **ve tema değişmiş** (WordPress `oldmovie`,
DooPlay türevi). Eski parser'ın hiçbir seçicisi tutmuyordu:
`.nl` **403** · `/search?q=` **404** · `/yabancidiziizle-2` **404** → eklenti sessizce boş dönüyordu.
(TV'de görünen ekran uygulama değil, sitenin kendi sayfasıydı — client-tv'de WebView yok.)

### Yeni oynatma zinciri (dördü de zorunlu)
```
1. içerik sayfası      → videoAjax.nonce + data-post-id + data-player-name
2. wp-admin/admin-ajax → action=get_video_url        → setplay.shop/player/?t=…
3. setplay             → SPG.cerceve(id, veri, key)  → XOR çöz → fastplay.mom/video/<id>
4. fastplay            → window.FSP.stream (HLS)     + SPG_A koruma parametreleri
```

### Ölçümle bulunan iki tuzak
1. **`X-Sp` tek kullanımlık.** Manifest, "oynatıcı kanıtı" başlığı olmadan 404 (sitenin kendi
   yorumu: *"IDM ve curl ADRESİ tekrar oynatıyor, başlığı değil"*). Dahası **aynı imza ikinci
   istekte yine 404, tazesi 200** (ölçüldü). Bu yüzden eklenti imzayı değil **malzemesini**
   taşır (`X-Sp-Secret`/`X-Sp-Time`); proxy her istekte yeniden üretir →
   `stream/Public/Proxy/Libs/player_proof.py`. İmza malzemesi istemciye **gönderilmez**.
2. **Segmentler başka CDN host'unda ve Referer istiyor** (`srv.…cfd`, Referer'sız 403).
   Normalde segmentler bant tasarrufu için doğrudan CDN'den çekiliyordu; ek başlık isteyen
   kaynaklar artık `force_proxy` ile sunucudan geçiyor. Ayrıca **manifest'ten türeyen her
   adrese kendi proxy token'ı** veriliyor (token host'a bağlı; eski hâlinde yalnız ilk host
   kapsandığı için segment 403 alıyordu).

### Ayrıca
- `engine` `load_links`/`resolve_sources` artık `extra_headers`'ı yanıta taşıyor (düşüyordu).
- `main_page`'e **Diziler** ve **Son Bölümler** eklendi; tür sayfaları yeni yollara güncellendi.

**Kanıt:** stream **48/48** OK · smoke **kapı YEŞİL** · film **20 → 38** içerik ·
uçtan uca istemci akışı: `master 200 4872B → varyant 200 486KB → segment 200 2.4MB` ·
dizi tarafı: `Ted Lasso → 34 bölüm → oynatma 200 #EXTM3U`.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi.** TV APK'sı bu iş için yeniden yayınlanmadı (sunucu tarafı değişikliği
  olduğu için mevcut APK da yararlanır; yine de cihazda oynatma görülmedi).
- Proxy'den geçen akış **ev bağlantısının yükünü artırır** (segmentler artık sunucudan geçiyor).
  Yalnız ek başlık isteyen kaynaklar için geçerli.
- `X-Sp` şeması sitenin JS'inden türetildi; site şemayı değiştirirse bu kaynak yeniden kırılır
  (teşhis: Kaynak raporu / `docker logs netmovies-engine`).

---

## 🔗 3 Eylül 2026 (2. oturum, devam) — Zincir tek uca taşındı, istemci ayrımı kalktı (v0.1.34-poc)

**Commit:** `c8e4b4f` · **Release:** `v0.1.34-poc` (OTA'da en yeni — doğrulandı)

Dean: *"her şey arkada olsun, ortam tüketsin, TV/telefon/web ayrımından kurtul."*

### Önce ne yanlıştı
Aynı iş **iki yerde, iki farklı davranışla** duruyordu: TV uygulaması altı sağlayıcıyı
kendi arıyor, kendi sıralıyordu; web yalnız seçili sağlayıcının linkleriyle yetiniyordu.
Bir kural değişince iki yerde değiştirmek gerekiyordu ve ikisi kaçınılmaz olarak ayrışıyordu.

### Yeni tek uç
```
GET /api/v1/resolve_sources?plugin=&encoded_url=&title=&episode=&mode=fast|full
  seçili sağlayıcı → (dizi ise bölüm çözme) → alternatif sağlayıcılarda arama
  → link toplama → dil sıralaması → teşhis kaydı
```
- `engine/.../resolve_sources.py` — zincir + `Diagnostics` (her adım hem konsola hem
  **yanıta** yazılır). Alternatif aramalar `asyncio.gather` ile paralel.
- `stream/.../resolve_sources.py` — dil kuralını uygular (`language.py`) ve her kaynağa
  okunur `language: {rank, label}` ekler. **Tek kural, tek yer.**
- `mode=fast`: yalnız seçili sağlayıcı (ilk oynatma beklemesin) · `mode=full`: alternatifler dahil.

### İstemciler artık sadece tüketiyor
- **client-tv**: arama/eşleştirme/sıralama kodu **silindi**. `SourceResolver.kt` yalnız
  sunum yardımcısı (etiket + altyazı dil kodu). İki çağrı: fast → full.
  Sunucunun teşhis kaydı istemci günlüğüne karışıyor → **Kaynak raporu tek yerde**.
- **web (`izle.py`)**: aynı ucu kullanıyor → web artık **alternatif sağlayıcıları da görüyor**.

### smoke.sh — yanlış alarmın kök nedeni bulundu
Rebuild'in hemen ardından çalıştırıldığında container ayakta ama `healthy` değildi;
ısınmadan yapılan çağrılar boş dönüp "katalog boş" alarmı üretiyordu (önceki oturumda
"kök nedeni doğrulanmadı" diye not düşülen kırmızı buydu). **Isınma adımı** eklendi
(healthy olana dek en fazla 120 sn bekler) + zincir adımı eklendi.

**Kanıt:** stream **43/43** OK · client-tv **5/5** OK · smoke **kapı YEŞİL** ·
gerçek veriyle `resolve_sources` → `HDFilmCehennemi · 1 kaynak · Türkçe altyazı`,
teşhis 8 satır: `fail arama — RecTV · ConnectError: Name or service not known`,
`warn arama — DiziYou · sonuç yok` → **ölü kaynak artık sessiz değil** ·
GitHub API en yeni release `v0.1.34-poc`.

### ⚠ Doğrulanmadı / açık
- Cihazda denenmedi (Dean deneyecek).
- `RecTV` domaini ölü (`ConnectError`) — zincirde her seferinde bir tur harcıyor.
  Sağlık süzmesi `resolve_sources`'a da uygulanabilir (aggregate'te var, burada yok).
- Uzak sağlayıcı (`provider_url` dolu) yolunda web hâlâ tekil `load_links` kullanıyor;
  yalnız yerel engine yolu tek uçtan geçiyor.

---

## 🩺 3 Eylül 2026 (2. oturum, devam) — Teşhis günlüğü + dil kuralı tek yerde (v0.1.33-poc)

**Commit:** `0ad3d36` · **Release:** `v0.1.33-poc` (OTA'da en yeni)

Dean: *"hata yakala, log tut, artık bak sonra duruma · o olursa bu, netleştir."*

### 1. Hiçbir hata sessizce yutulmuyor (TV)
`client-tv/.../data/PlaybackLog.kt` — arama, bölüm çözme, link çekme ve oynatma
denemelerinin **her adımı** kayda geçer: logcat (`NetMoviesPlayback` tag) + 200 satırlık
halka tampon. `runCatching{}.getOrNull()` yerine `loggedOrNull(stage, detail)`: zincir
devam eder ama hata **görünür**.
**Cihazda okunur:** oynatıcı → Ayarlar → **🩺 Kaynak raporu** → "Son denemeleri göster".
PC/adb gerekmiyor — Dean hatayı gördüğü anda ne olduğunu okuyabilir.

### 2. Dil kuralı netleştirildi + tek yere alındı
Kural tek cümle: **Türkçe dublaj → Türkçe altyazı → dil bilinmiyor.**
- Sunucu: `stream/Public/API/v1/Libs/language.py` (`language_rank/order_by_language`).
  `/api/v1/load_links` **ve** web `izle` akışı artık sıralı liste döndürüyor → web ve TV
  aynı sırayı görür. (TV ayrıca kendi tarafında da uygular — çift güvence.)
- Görünürlük: kaynak listesi ve durum satırı `"DiziBox · Türkçe dublaj"` yazıyor.
  Etiketsiz kaynak "dil bilinmiyor" grubuna düşer ve **öyle görünür** — sessiz tahmin yok.

### 3. Sunucu tarafı sessiz `except`'ler loglandı
- `aggregate_new`: kategori eşleşmedi / kaynak boş döndü / kaynak hata verdi + özet satırı.
  **Canlı TV rafının aylarca boş kalması tam bu körlüktü.**
- `load_links` + `izle`: kaç kaynak geldi, ilk sıradaki dil ne.

**Kanıt:** stream **39/39** test OK · client-tv **8/8** OK · `assembleDebug BUILD SUCCESSFUL` ·
smoke **kapı YEŞİL** · `docker logs netmovies-engine` → `∑ aggregate: type=serie_foreign ·
10 içerik · 6 kaynak tarandı` · `docker logs netmovies-stream` → `▶ load_links: M3UPlaylist ·
1 kaynak · ilk sıra: dil bilinmiyor` · GitHub API'de en yeni release `v0.1.33-poc`.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi** (Dean deneyecek): kaynak raporu ekranının okunabilirliği, zincirin
  gerçekten sıradakine geçmesi, dublaj önceliğinin doğru linki seçmesi.
- Dil tespiti hâlâ **kaynağın etiketine** bakıyor. Etiketsiz veren site "dil bilinmiyor"
  grubunda kalır — kaynak raporunda bu görünür; sık çıkarsa plugin'e dil alanı eklenmeli.
- Web'de alternatif **sağlayıcı** araması yok (TV'de var). Web yalnız seçili sağlayıcının
  linkleri arasında otomatik geçiş yapıyor (bu zaten vardı) + artık dil sırasıyla.
- Bir smoke koşusunda rebuild'in hemen ardından 1 adım kırmızı görüldü, tekrarında yeşil;
  **kök nedeni doğrulanmadı** (muhtemelen container yeni ayaktayken soğuk çağrı).

---

## ▶️ 3 Eylül 2026 (2. oturum, devam) — Oynatma zinciri yeniden yazıldı + v0.1.32-poc OTA

**Commit'ler:** `b6df088` (oynatıcı) · `bc5b11c` (APK) · **Release:** `v0.1.32-poc` (prerelease, OTA yayında)

Dean'in şikâyeti: *"izletmiyor, 'bulunamadı / tekrar dene' diyor; aynı filmi bir sürü
site veriyor, çalışandan çeksin; önce dublaj sonra Türkçe altyazı; ekrandan çıkarmasın,
ekranda yazsın, geri tuşuna kendim basarım."*

### Yeni davranış (`data/SourceResolver.kt` + `ui/PlayerScreen.kt`)
- **Aşamalı kaynak toplama.** Eskiden oynatıcı açılırken altı sağlayıcı SIRAYLA taranıyor,
  kullanıcı hepsi bitene kadar bekliyordu. Artık seçili sağlayıcı hemen denenir, **ilk link
  gelir gelmez oynatma başlar**; diğerleri arka planda taranıp kuyruğa eklenir.
- **Dil önceliği.** Kuyruk `orderByLanguage()` ile sıralanır: **Türkçe dublaj → Türkçe
  altyazı → diğer**. Grup içi sıra korunur (stable sort). Zaten oynayan link yerinde kalır.
- **Hata kutusu kaldırıldı.** Çalmayan link sessizce sıradakine geçer; ekranda yalnız durum
  satırı: *"Kaynak açılmadı, sıradaki deneniyor (2/5)…"*. Hiçbiri çalışmazsa *"çıkmak için
  GERİ tuşuna bas"* yazısı ekranda kalır. `PlayerOverlay`+"Tekrar dene" oynatma akışından
  çıkarıldı — geçiş kendiliğinden oluyor.
- Altyazı dil tahmini tek yerde: `guessSubtitleLang()`.

**Kanıt:** `testDebugUnitTest` **8/8 OK** (7 yeni SourceResolver testi) ·
`assembleDebug BUILD SUCCESSFUL` · GitHub API'de en yeni release `v0.1.32-poc`
(prerelease, asset `NetMovies-TV-v0.1.32.apk`) → eski APK'lar güncelleme şeridini görür.

### ⚠ Doğrulanmadı / açık
- **Cihazda denenmedi**: zincirin gerçekten sıradaki kaynağa geçtiği, dublaj önceliğinin
  doğru linki seçtiği, durum satırının okunabilirliği. Dean deneyecek.
- Dil etiketleri kaynak adına bakıyor (`"dublaj"`, `"altyazı"`). Etiketsiz veren kaynakta
  içerik dili anlaşılmaz → 2. gruba düşer. Gerekirse plugin'lerde dil alanı eklenmeli.
- **Web oynatıcısı bu davranışı ALMADI** — aynı zincir web tarafında hâlâ eski hâlinde.

---

## 🖼️ 3 Eylül 2026 (2. oturum, devam) — Faz 2: tek poster hattı

**Commit:** `fb3f68c` — `feat(poster): tek poster hattı`

Poster fallback zinciri **dört ayrı yerde** kuruluydu ve davranışları farklıydı:
her Jinja şablonunda inline `onerror`, `content-browser.js`'te ikinci deneme,
`home.html.j2` inline script'inde üçüncü bir varyant, TV'de Coil `onError` state'i.

**Yeni sözleşme — zincirin tamamı sunucuda:**
`/proxy/image?url=<kaynak>&title=<başlık>` → kaynak → proxy LRU cache → TMDB (302) → placeholder

- `image.py`: `title` parametresi + fallback halkası. **Kırık poster negatif cache'i**
  (10 dk): ölü poster her rafta tekrarlanıp her seferinde CDN'e TLS + timeout turu
  yapıyordu. `X-Cache` artık nedeni de söylüyor: `MISS/HIT/NEG/UPSTREAM_404/
  NOT_IMAGE/BLOCKED_HOST/BAD_SCHEME/TOO_LARGE/FETCH_ERROR` → teşhis header'dan okunur.
- Tek helper üç yerde aynı URL'i üretir: Jinja `poster(url, title)`,
  yeni `Static/JS/utils/poster.js` → `posterUrl(poster, title)`,
  Kotlin `proxiedPoster(url, title)`. Şablonlardaki kopya `onerror` zincirleri silindi.
- `stream/tests/test_poster_pipeline.py` — 13 test (helper sözleşmesi, cache HIT/MISS,
  negatif cache, TMDB fallback, MIME reddi, SSRF reddi).

**Kanıt:** `31/31 OK` · gerçek poster `MISS 0.162s → HIT 0.018s` · kırık poster
`302 → /tmdb-poster` (2. istek `NEG 0.013s`, ağ turu yok) · `url=&title=Dark` →
TMDB'den `200 image/jpeg 70331B` · ana sayfada eski `tmdb-poster` onerror kalıntısı **0**,
`title`'lı poster URL **36** · TV `compileDebugKotlin` + `testDebugUnitTest` **exit 0**.

CSS'te `aspect-ratio: 2/3` zaten vardı → layout shift maddesi ek iş istemedi.

⚠ **Doğrulanmadı:** TV cihazında posterlerin göründüğü; APK sürüm bump'ı ve OTA
yayını yapılmadı (Kotlin değişikliği sadece derlendi).

---

## 🧪 3 Eylül 2026 (2. oturum) — Faz 1: sözleşme testleri + Canlı TV rafı fix

**Commit'ler (dal: `fix/general-stability`)**

| Commit | Ne |
|---|---|
| `45c2060` | api/v1 gateway sözleşme testleri + `scripts/smoke.sh` kapı kontrolü |
| `3dce3c4` | Canlı TV rafı boş dönüyordu + `quick_channels` ucu istemcilere açıldı |

### 0. Yığın yeniden ayağa kalktı
Docker Desktop kapalıydı (`localhost:3310` bağlantı yok, tünel `530`).
`docker compose --profile tunnel up -d --build` → engine + stream **healthy**,
`/api/v1/health` 200, `https://w.evaitec.com` **200**.

### 1. Sözleşme testleri (planın Faz 1 / madde 4 boşluğu)
`stream/tests/test_api_contract.py` — 14 test. Upstream provider `httpx.MockTransport`
ile taklit edilir, **gerçek kaynak sitelere çıkılmaz** (CI'da kırılgan olmaz).
Sabitlenen sözleşme: `encoded_url` parametre adı (eski `url` regresyonu), katalog/arama/
link parametreleri, cache kuralları (arama cache'lenir, **`load_links` asla**, boş
`aggregate` cache'lenmez, hata cache'lenmez), `provider_error` zarfı, istemci kimlik
header'larının upstream'e taşınması.
**Kanıt:** container içinde `Ran 18 tests ... OK` (mevcut `test_provider_routing` dahil).
Çalıştırma: `docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests`
⚠ `tests/` image'a build ile girer; kod değiştirmeden test denemek için `docker cp stream/tests netmovies-stream:/usr/src/Stream/`.

### 2. `scripts/smoke.sh` — tek komutluk kapı
Container health → `/api/v1/health` → eklenti listesi → **beş aggregate tipi** →
`quick_channels` → sözleşme testleri. `BASE=https://w.evaitec.com bash scripts/smoke.sh`
ile tünel üzerinden de çalışır. Kırmızı adım varsa çıkış kodu 1.

### 3. Canlı TV rafı boştu (smoke'un yakaladığı gerçek hata)
- **Kök neden:** `aggregate_new` kategori **adında** Türkçe ipucu arıyor (`"canlı"`,
  `"kanal"`); M3U grup adları listeden geliyor ve İngilizce (`Animation`, `News`) →
  hiç eşleşme yok → `type=live` **her zaman 0 item**. TV home'un `OTHER_TYPES`'ında
  `live` var, yani cihazda Canlı TV rafı boş görünüyordu.
- **İkinci kusur:** `/api/v1/quick_channels` **stream gateway'inde yoktu**. Web bunu
  server-side `fuck_dmca` ile çekiyor, native istemci ise canlı listeye hiç erişemiyordu.
- **Fix:** engine'de toplayıcı `collect_live_channels()` olarak ayrıldı; `aggregate_new`
  live tipinde bunu kullanıyor (url'ler diğer tiplerle aynı sözleşmede `quote_plus`'lı).
  Gateway'e `quick_channels` router'ı + 10 dk cache eklendi.
- **Kanıt:** `type=live` 0 → **173** item · `/api/v1/quick_channels` → 173 kanal ·
  `Disney Jr.` → `load_links` → `saran-live.ercdn.net/.../index.m3u8` **HTTP 200**.

### ⚠ Sözleşme tuzağı (bir daha düşme)
Aggregate tip adı **`serie`**, `series` değil. Bilinmeyen tip hata vermez, **sessizce boş
liste** döner — bu yüzden yanlış tip yazmak "kaynak öldü" gibi görünür.
Geçerli tipler: `movie`, `serie`, `serie_local`, `serie_foreign`, `live`.

### ⚠ Doğrulanmadı / açık (bu oturum)
- Canlı rafın **TV cihazında** dolduğu görülmedi (sunucu ucu uçtan uca doğrulandı).
- TV'de canlı kanal kategorileri İngilizce görünecek (`Animation;Kids`) — kozmetik, açık.
- Önceki oturumun cihaz doğrulamaları (DiziBox oynatma, Gözat D-pad, TMDB fallback,
  oynatıcı hata ekranı GERİ tuşu) hâlâ **denenmedi**.

---

## 🚀 3 Eylül 2026 — DiziBox/MolyStream + Gözat Yeniden Tasarım + TMDB

**Commit'ler (dal: `fix/general-stability`)**

| Commit | Ne |
|---|---|
| `9e4b865` | Gözat ekranı poster raflarına dönüştü + büyüteç arama (v0.1.29-poc) |
| `d0b40e8` | DiziBox/MolyStream çözücü + poster proxy LRU cache (v0.1.30-poc) |
| `8187ac9` | TV'de poster TMDB fallback (v0.1.31-poc) |

### 1. DiziBox — "Oynatılacak kaynak bulunamadı" çözüldü (KÖK NEDEN)
Zincir **üç katman**, eskiden ikincisi atlanıyordu:
1. Bölüm sayfası → `div#video-area iframe` → `dizibox.live/player/king/king.php?v=<hex>`
2. **`king.php` sadece SARMALAYICI** — gerçek oynatıcı içindeki **ikinci iframe'de**:
   `dbx.molystream.org/embed/<id>`. Bu katman izlenmediği için `load_links` hep `[]` dönüyordu.
3. MolyStream sayfa gövdesi **CryptoJS ile şifreli**: `CryptoJS.AES.decrypt("<ct>", "<pw>")`
   → OpenSSL `"Salted__"` formatı, **EVP_BytesToKey (MD5, 1 tur)**, AES-256-CBC, PKCS#7.
   Çözülen HTML'deki jwplayer `file:` alanı doğrudan HLS master playlist veriyor.

→ `DiziBox.py`: `_evp_bytes_to_key()`, `_cryptojs_decrypt()`, `_molystream()` eklendi;
`load_links` iç iframe'i takip ediyor.
**Kanıt:** `load_links` `[]` → `1` kaynak · `dbx.molystream.org/embed/sheila/9344-...` →
`HTTP 200, 149 B, #EXTM3U, RESOLUTION=1920x1080` · engine log `GET - 200 - 5.34 sn`.
⚠ **TV cihazında oynatma denenmedi.**

### 2. Poster proxy cache — "çok geç yükleniyor"
`stream/Public/Proxy/Routers/image.py`'ye süreç-içi **LRU + bayt sınırlı** cache
(`IMAGE_CACHE_MB`, varsayılan 128MB) eklendi; `X-Cache: HIT/MISS` header'ı ile ölçülebilir.
**Kanıt:** aynı poster `MISS 0.538 sn` → `HIT 0.022 sn`.
⚠ In-memory → `WEB_WORKERS=1` şart (zaten öyle).

### 3. Gözat ekranı yeniden tasarlandı (v0.1.29)
Dean'in üç şikâyeti karşılandı:
- Düz metin listesi → her (eklenti, kategori) bir **poster rafı** (`Shelf`), ana sayfadaki gibi.
- Kaba arama çubuğu → sadece **büyüteç**; seçilince metin alanına açılıyor.
- "Çıkınca liste en üstten başlıyor" → `rememberLazyListState()` + son odaklı raf indeksi
  **ekran seviyesine hoist** edildi; arama sonucundan dönünce kaldığı yerden devam.
- İlk 6 raf paralel önyükleniyor (`PREFETCH_SHELVES`); boş dönen raf hiç çizilmiyor.

### 4. TMDB anahtarı aktif + TV fallback (v0.1.31)
Kök neden: anahtar hiç yoktu (`TMDB=[]`) → `/tmdb-poster` 404 dönüyordu. Dean anahtarı verdi,
**gitignored `.env`'e** yazıldı (kodda ASLA).
**Kanıt:** container içi `TMDB key: DOLU (32 karakter)` · `/tmdb-poster?title=Dark` →
`302 → image.tmdb.org/t/p/w500/lUXX0AeneW2v0FK9YuXlrTwkI3H.jpg`.
TV tarafı: yeni `ui/PosterImage.kt` ortak bileşeni — Coil `onError` tetiklenince
`/tmdb-poster?title=`'a düşüyor (poster hiç yoksa doğrudan TMDB ile başlıyor).
Home + Gözat posterleri bu bileşene geçti.

### ⚠ Doğrulanmadı / açık (bu oturum)
- DiziBox'ın TV'de **gerçekten oynadığı** doğrulanmadı.
- Yeni Gözat raflarının D-pad davranışı ve odak geri-yükleme cihazda denenmedi.
- TMDB fallback'in TV ekranında dolduğu görülmedi (sunucu ucu doğrulandı).
- Oynatıcı hata ekranı GERİ tuşu fix'i hâlâ cihazda denenmedi.

---

## 🎬 2 Eylül 2026 — Kaynak Onarımı + v0.1.28-poc OTA

**Commit'ler (dal: `fix/general-stability`)**

| Commit | Ne |
|---|---|
| `595a7c9` | Dizilla oynatma linki çözüldü + ölü domainler gömülü yedeğe alındı |
| `07ae1ed` | Canlı TV M3UPlaylist'e taşındı, ölü RecTV varsayılan gizlendi |
| `e543a04` | DiziYou araması WP AJAX ucuna taşındı |
| `2ec7dd2` | v0.1.28-poc sürüm bump + APK |

### 1. Dizilla — oynatma tamamen kırıktı, düzeldi
Kök neden **üç katmanlıydı**:
1. **Oynatıcı iframe HTML'de yok.** `div#playerLsDizilla` sunucudan boş geliyor; kaynaklar
   `__NEXT_DATA__ → props.pageProps.secureData` içindeki **AES-256-CBC** bloğundan client-side
   basılıyor. Anahtar arama ucuyla aynı: `base64(sha256(b"!!22xx!!90!!"))[:32]`, IV = 16 sıfır bayt,
   PKCS#7. → `Dizilla._secure_data()` eklendi, `load_links` bu bloğu çözüyor.
2. **`source2.php` 403** — token base64 ("+", "/", "=") içeriyor; `params=` ile göndermek
   yüzde-kodluyor. → ham f-string query string kullanıldı (sitenin kendi JS'i de öyle yapıyor).
3. **Yine 403** — `PluginBase.httpx` sabit `accept-encoding: gzip, deflate` + `connection: keep-alive`
   gönderiyor; Cloudflare bunu bot parmak izi sayıyor. → pichive akışı için **düz `httpx.AsyncClient`**
   (sadece tarayıcı UA) kullanılıyor (`_pichive_sources`).

Ayrıca `h1` metnindeki `"Darknetİzle"` artefaktı temizlendi (`re.sub(r"\s*İzle$", ...)`).

**Kanıt:** `search → 15` · `load_item 'Darknet' → 12 bölüm` · `load_links → 1` ·
`master.m3u8 → HTTP 200, 6962 B, geçerli #EXTM3U`.

### 2. Ölü domainler — `.env` bağımlılığı kaldırıldı
Fallback'ler gitignored `.env`'den koda taşındı (temiz kurulumda da çalışsın):
- `Dizilla.py` → `https://dizilla.now`
- `DiziMom.py` → `https://www.dizimom.diy` (zincir: `.plus → .work → .food → .diy`; upstream `.kt`
  hâlâ ölü `.plus`'ı gösterdiği için upstream sonucu bu ailedeyse override ediliyor)

### 3. DiziYou — arama düzeldi
`/?s=` artık boş kabuk dönüyor. Sonuçlar tema'nın `wp-admin/admin-ajax.php`
(`action=data_fetch`) ucundan `div#searchelement` kartları olarak geliyor. WARP yedeği eklendi.
**Kanıt:** `dark` → "Dark", "Dark Desire".

### 4. Canlı TV — RecTV öldü, M3UPlaylist devraldı
`docker-compose.yml`'de varsayılan: `M3U_SOURCES=https://iptv-org.github.io/iptv/countries/tr.m3u`.
`admin_config.py → DEFAULT_CONFIG.hidden_providers`'a `"RecTV"` eklendi (silinmedi — yeni adres
çıkarsa `/admin`'den görünür yapmak yeter).
**Kanıt:** 174 kanal, örneklenen 20 kanalın 16'sı 200 + `#EXTM3U`; `a haber` → 1 sonuç → oynanabilir
`.m3u8`; ana sayfada "RecTV" grep → 0.

### 5. Alternatif kaynak araştırması — hepsi ölü (kanıtlandı)
| Kaynak | Durum |
|---|---|
| RecTV | `b.prectv{30..90}.sbs` **NXDOMAIN**, `rectv.me` A kaydı yok, Telegram probe `None` |
| InatBox | `dizibox.rest` + `boxbc.sbs` NXDOMAIN, upstream son commit 2025-02-23 |
| SineWix | `ythls.kekikakademi.org` NXDOMAIN |
| GolgeTV | `panel.cloudgolge.shop` NXDOMAIN |
| CanliTV | bağımlı repo `keyiflerolsun/IPTV_YenirMi` → GitHub **451** (kaldırılmış) |

→ Yeni tek-API canlı TV kaynağı eklenmedi; M3U listesi bu boşluğu dolduruyor.

### 6. v0.1.28-poc OTA
`versionCode 27` / `versionName 0.1.28` / `RELEASE_TAG "v0.1.28-poc"`.
`assembleDebug` → **BUILD SUCCESSFUL (1m 43s)**, APK 19.822.686 B, kökte `NetMovies-TV-v0.1.28.apk`.
Release doğrulaması uygulamanın kullandığı API yolundan: `tag_name: v0.1.28-poc`, `draft: false`,
asset `state: uploaded`.

### ⚠ Doğrulanmadı / açık
- **TV cihazında OTA indirme + kurulum ve Faz 2 tema görünümü test edilmedi.**
- Oynatıcı hata ekranında **GERİ tuşu** düzeltmesi hâlâ cihazda denenmedi.
- `/admin` parolası internete açık tünelde `1234` — Dean şimdilik kabul etti.

### 🔁 Tekrar düşülen tuzaklar (bir daha düşme)
- **`stream`/`engine` Python kodu imaja gömülü.** `restart` ve `--force-recreate` kaynak değişikliğini
  ALMAZ — sadece `up -d --build <svc>`. Bu oturumdaki 1 numaralı kök neden buydu.
- `cloudflared` netns'i `stream`'e pinli → `stream` recreate edilirse `cloudflared` de edilmeli.
- `docker exec ... python3 /tmp/x.py` Windows'ta path-mangle olur → **`//tmp/x.py`** yaz.
- Karmaşık regex'ler `python -c` içinde Git Bash escaping'inde kırılır → script dosyası yaz + `docker cp`.
- Arama testinde **kötü sorgu ≠ bozuk kaynak**: `kara` hiçbir sitede eşleşmiyor; `dark`/`breaking` kullan.

---

## 🎯 2 Eylül 2026 Oturumu — Faz 1 Stabilizasyon + Media3 Geçişi (SON DURUM)

**Commit'ler (dal: `fix/general-stability`):**
- `542a450` — Faz 1: TV `load_item` `encoded_url` sözleşmesi + kontrat testi (JVM PASSED),
  provider cache izolasyonu (`_cache_key` provider_url içerir, `_active_provider_url` admin
  config'ten → web+TV birleşik provider) + pytest 3/3, poster hattı birleştirildi
  (content/search → `poster()` proxy + tmdb fallback), compose `service_healthy` +
  `WEB_WORKERS=1`, `.gitignore` `data/`→`/data/` (tv/data/ paketi tuzağı).
- `7d4ed47` — Codex yarım-UI refactoru (pages/*.css ayrımı, header sadeleştirme, HomeScreen).

- `54d76d2` — TV oynatıcı hata ekranında GERİ tuşu fix'i (`BackHandler` `error != null → onBack()`,
  `onKeyEvent` hata state'inde tuş yutmuyor, `ActionRow.focusGroup()`) + **Media3 1.4.1 → 1.11.0**.
  Gereken zincir: compileSdk 36, AGP 8.9.1, gradle 8.11.1, **Kotlin plugin 2.0.20 → 2.2.10**
  (Media3 1.11 kotlin-stdlib 2.2.x çekiyor; eskisinde "metadata 2.2.0, expected 2.0.0" ile
  derleme kırılıyordu). Kanıt: `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL**.
  ⚠ Cihazda (gerçek TV) davranış testi YAPILMADI — APK'yı yükleyip hata ekranında BACK'i dene.

**⚠ Docker/canlı (502):**
- Kök neden 2 katman: (1) compose `service_started` cold-start yarışı → `542a450` ile çözüldü
  (canlıda henüz doğrulanmadı), (2) Docker Desktop'ın kendisi hastaydı (API 500, buildkit
  "no such job", iç DNS timeout) → restart edildi; `cloudflared:2026.1.2` imajı yeniden
  çekildi (IMAGE_OK). Oturum sonunda `COMPOSE_BAKE=false docker compose up -d --build`
  arka planda ateşlendi — **sonucu doğrulanamadı**.
- **Sonraki adım:** `docker ps` → hepsi healthy mi + `curl localhost:3310/health` +
  w.evaitec.com 200 mü. Stream rebuild olduysa cloudflared'i de recreate et
  (netns pinli — bkz. memory: stream rebuild → tünel kopar).

---

## 🎯 1 Eylül 2026 Oturumu — Web Hata Düzeltmeleri, TV UI & Stabilizasyon

1. **🛠 `unexpected char '\' at 1028` ve JS/Jinja Kaçış Hatası Giderildi (`category.html.j2`, `home.html.j2`)**:
   - Resim posterlerindeki TMDB fallback `onerror` satırlarında tek tırnakları kaçırmaya çalışan kırılgan `replace("'", "\\'")` filtreleri temizlendi.
   - Kartlara doğrudan `data-title="{{ item.title }}"` niteliği eklendi ve JS fallback'i `this.dataset.title` üzerinden hatasız hale getirildi.
   - `home.html.j2` içindeki `loadLazy` fonksiyonunda tırnak kaçış regex'i sadeleştirilerek parse çökmesi engellendi.

2. **🖱️ Web & Mobil Kart Tıklama Davranışı Düzeltildi (`tv-home-actions.js`)**:
   - `tv-home-actions.js` dosyasında TV kumandası için tek tıklamayı yutup sadece kartı odaklayan (`preventDefault()`) blok kaldırıldı.
   - Web ve fareli kullanımda kartlara doğrudan tek tıklandığında film/dizi sayfasına kesintisiz geçiş sağlandı.

3. **🎨 UI & Izgara Yoğunluğu Optimizasyonu (`card.css`, `home.html.j2`, `category.html.j2`)**:
   - Poster ızgara kart genişliği `110px` boyutuna getirilerek daha sık ve yoğun bir poster görünümü elde edildi.
   - Uzun başlıklar için `marquee` animasyonu eklendi.
   - Sayfalardaki mükerrer/gereksiz başlıklar (`breadcrumbs`) temizlendi.

4. **📺 Android TV Client Geliştirmeleri & Derleme (`HomeScreen.kt`)**:
   - Üst kısma geniş ve modern `HomeSearchBarButton` ("🔎 Film, Dizi veya Tür Ara...") eklendi.
   - Ayarlar menüsü `SettingsMenu` modal yapısına dönüştürülerek buton eşleme, sanal fare modu ve özel koleksiyon erişimi derlendi.
   - `./gradlew.bat assembleDebug` ile APK başarıyla üretildi (`app-debug.apk` ~19.99 MB, `BUILD SUCCESSFUL`).

---

## 🎯 v0.1.26-poc ile Çözülen Sorunlar & Önceki Yetenekler

1. **TV Kumanda D-Pad Üst Menü Navigasyonu (`HomeScreen.kt`)**:
   - `[🔎 Gözat]`, `[⚙ Buton Eşleme]` ve `[🔒 Özel Koleksiyon]` butonları ana sayfa raylarının en üstüne `LazyColumn` içine odaklanabilir TV bileşenleri olarak taşındı.
2. **Dizi Oynatma ("Daha 17" ve Diğer Diziler) & Bölüm Desteği (`PlayerScreen.kt` & `NetMoviesApi.kt`)**:
   - Dizi sayfalarına tıklandığında linkin doğrudan video linki olmaması durumunda (`resp.result.isEmpty()`), otomatik olarak `/api/v1/load_item` ile bölüm listesi çözülür ve 1. bölümden oynatma başlar.
3. **Özel Koleksiyon (18+) Doğrudan Açılış (`BrowseScreen.kt` & `HQPorner.py`)**:
   - "🔒 Özel Koleksiyon" seçildiğinde boş arama yerine doğrudan yetişkin kategorileri (Popüler, 1080p, 4K vb.) ve içerik ızgarası yüklenir. Eklenti başlatma hatası giderildi.

---

## 0. SON OTURUM — 2026-08-31 — Özel Koleksiyon (18+ Stealth Vault) + HQPorner Entegrasyonu + Android TV & Web Gizli Tetikleyici (CANLI, DOĞRULANDI)

**Durum: Docker Desktop'ta modüler profil mimarisiyle uçtan uca doğrulandı. w.evaitec.com ✅ · Android TV Client v0.1.23-poc OTA Yayında ✅.**

### 1. Özel Koleksiyon (18+ Stealth Vault Modu)
- **Kamufle İsim:** Menülerde ve ayarlarda doğrudan 18+ veya yetişkin ibaresi yerine **"Özel Koleksiyon"** ismiyle yer alır.
- **Gizli Tetikleyici Kombinasyonları:**
  - **Web:** Sol üstteki Logoya 5 kez arka arkaya tıklama VEYA 3 saniye basılı tutma (Long-Press) ile mini bildirim eşliğinde açılır/kilitlenir (`sessionStorage`).
  - **Android TV / Mobil:** Üst menüdeki "🔎 Gözat" butonuna 3 saniye basılı tutma (D-pad Center / OK uzun basma) ile kilit açılır, açıldığında ek olarak "🔒 Özel Koleksiyon" butonu belirir.
- **Varsayılan Durum:** Normal kullanımda tüm 18+ içerikler ve sağlayıcılar tamamen gizlidir (`hidden_providers` & `hidden_categories`).

### 2. HQPorner 4K/1080p Yerel Motor Entegrasyonu (`engine/Plugins/HQPorner.py`)
- WARP proxy desteğiyle ISP engelleri aşılarak doğrudan 4K/1080p ve 60FPS video linkleri parse edilip oynatıcıya teslim edilir.
- `SpankBang`, `FullPorner`, `PornHub`, `xHamster`, `OxAx`, `UncutMaza` gibi uzak eklentiler de havuzda hazır olarak listelenir.

### 3. Android TV Client (v0.1.23-poc OTA)
- `client-tv` derlendi (`versionCode: 22`, `versionName: "0.1.23"`, `RELEASE_TAG: "v0.1.23-poc"`).
- GitHub Release `v0.1.23-poc` yayınlandı ve `NetMovies-TV-v0.1.23.apk` eklendi.
- Cihaz açılışında OTA güncelleme bildirimi otomatik olarak görünecektir.

---

**Durum: Docker'da uçtan uca doğrulandı. w.evaitec.com ✅. Android client v0.1.20 OTA.**
Dıştan (telefon yolu) tutarlı: **movie 20 · serie 68 · serie_foreign 10 · live 0** (2x aynı).

### Android client (client-tv) — v0.1.11 → v0.1.20 (hepsi OTA, prerelease)
- **Poster büyüteç** (focus scale+glow), **çark menüsü** (Kaynak/Dil/Altyazı/Hız), **10sn seek**, **altyazı sideload**, **çoklu kaynak**.
- **Buton-eşleme sistemi** (`input/RemoteInput.kt`, `ui/KeyMapScreen.kt`): her tuş×basış → aksiyon, SharedPreferences. Oynatıcı `useController=false` tam input sahipliği. → memory `input-mapping-architecture`.
- **Scrub önizleme (thumbnail)**: ikinci ExoPlayer düşük kalite kare (`ScrubOverlay`).
- **Dokunmatik oynatıcı kontrolleri** (telefon): videoya dokun→kontroller, tıklanabilir seekbar (pointerInput, D-pad'i bozmaz). Emoji sarı çıkıyordu → **v0.1.21: kompakt material vektör ikonlar** (`material-icons-extended`): Replay10 · play/pause (mor 44dp) · Forward10, ince 3dp bar, tek satır süre-kontrol-süre. (Metin pill'ler "kocaman" olduğu için küçültüldü.)
- **Ana sayfa çoklu tip** (`HomeViewModel`): movie+serie+serie_local+serie_foreign+live paralel → çoklu satır (önce tek "yeni filmler"di).
- **Favoriler + İzlenenler** (`data/Library.kt`, SharedPreferences), poster uzun-bas menü, oynatınca İzlenenler dolar.
- **Gözat tarayıcı** (`ui/BrowseScreen.kt`): tüm eklenti kategorileri inline + **arama** (tüm kaynaklarda paralel, per-plugin 12s timeout). ActionPicker perde dokunuş fix.

### Engine / kaynaklar (CANLI, ev sunucusunda — .env gitignored)
- **DiziMom kurtarıldı**: domain taşınmış `dizimom.work`→**`dizimom.food`** (`.env DIZIMOM_URL`). → Yabancı Diziler 10.
- **WARP egress** (`docker-compose.yml` `warp` servisi, gost HTTP proxy `netmovies-warp:8080`): TR SNI/DPI engelini aşar. **SEÇİCİ** kullanım — sadece bloklu plugin. Global proxy YASAK (movie=0 yapıyor: CF IP HDFC/RecTV'yi bloklar).
- **Dizilla kurtarıldı**: `dizilla.nl`→**`dizilla.club`** (SNI-bloklu) → `.env DIZILLA_URL` + `Dizilla.__init__` self.httpx'i **proxy'li düz httpx.AsyncClient** ile değiştirir (PluginBase FallbackHTTPX proxy'yi uygulamıyor). → 14 item. Diğerleri direkt (movie korunur).
- **ENGINE_WORKERS=1** (`.env`): 2 worker × ayrı in-memory cache → aggregate her seferinde cold-scrape → stream "Server disconnected"/boş oluyordu. Tek worker = paylaşılan cache = tutarlı.
- Docker PC'de kapalıydı (telefon 530'un asıl sebebi) → açıldı; tünel netns tuzağı: engine recreate GÜVENLİ, stream recreate→`docker compose --profile tunnel up -d --force-recreate cloudflared`.

### KALAN (sıradaki oturum)
1. **Dizipal portu**: upstream `DiziPal.kt` (247 satır, `/tmp` yok artık — GitHub'dan çek) → `engine/Plugins/DiziPal.py` PluginBase türevi. SNI-bloklu + numaralı döner domain (dizipal950 ölü) → WARP proxy (Dizilla deseni: `__init__` proxy'li httpx) + güncel domain keşfi. Kanıt kapısı: get_main_page item>0 + load_links stream verir + **movie 20 bozulmaz**.
2. **Canlı TV** (live=0): RecTV live endpoint boş — WARP'la düzelir mi bak; düzelirse web'de iframe "eski player" emekli, her yer zengin player (bkz. #3).
3. **Web player birleştirme**: `/resmi-kaynak`→`official_player.html.j2` (iframe, "eski") vs `/izle`→`player.html.j2` (zengin). Resmi kaynaklar harici sayfa (HLS yok) → canlı TV HLS gelince iframe kartları kaldır.
4. **Kalıcı tünel fix** (opsiyonel): cloudflared'i stream netns'inden çıkar + CF panelde origin `stream:3310` → restart'lara dayanır.

### Servis güncelleme (bu oturum)
`docker compose --profile tunnel up -d --build --pull always` → engine/stream rebuild (base imaj `--pull`), engine/stream/tunnel birlikte recreate (tünel yeni netns'e "Registered tunnel connection" ist05). doh pinli (2026.1.2) kalır, warp/cloudflared son sürüm. Docker Desktop **uygulaması** güncellenmedi (elle: `winget upgrade Docker.DockerDesktop`, restart ister).

### Bu oturum commit'leri (dalda, push edildi)
client-tv: `c403710 8e18a30 054e9da 0525815 bad97c0 b139e9b ed6b1c8 f92ccaf c3d987a fcbe69b f3e0f5a`(v0.1.21 kompakt ikon) · infra/engine: `acf8a29 0d66096`(WARP) + Dizilla commit + HANDOFF. Memory: `input-mapping-architecture`, `plugin-domain-moves`.

---

## 1. ÖNCEKİ OTURUM — 2026-08-29 — Uzak Sağlayıcı (Geniş Katalog) + Canlı Sağlık + Tünel Fix (CANLI, DOĞRULANDI)

**Durum: TAMAM, Docker'da uçtan uca doğrulandı. 2 commit dalda (`d90cea2`, `61c4a81`). Yerel app ✅ · w.evaitec.com ✅.**

### Talep (Dean)
"DiziPal ekleyelim ya da KekikAPI'yi ekleyip eklenti seçimi sunalım + 'eklentileri güncelle' butonu;
180 küsur eklenti var, hepsini alıp o yenileyince bizde de otomatik yenilensin."

### Kanıtla çürütülen 3 varsayım (araştırma + Docker testi)
- ❌ CloudStream'in 180 eklentisi = **Kotlin**, Python motoruna yüklenemez (`.cs3` Android).
- ❌ pip `KekikStream>=2.5.0` paketi eklenti **bundle etmiyor** — `Plugins/` klasöründe sadece `__init__.py`
  (container'da kanıtlandı: `GLOBAL_COUNT: 0`). Her scraper el-porttur; hazır Python kataloğu YOK.
- ❌ **DiziPal self-host portu YAPILMADI:** `dizipal950` domaini ölü + site **Cloudflare 403**
  (profesyonel watchbuddy sunucusunda bile DiziPal 403). Bozuk eklenti eklenmedi.

### Çözüm — Uzak Sağlayıcı "Geniş Katalog" (Dean'in fikrinin çalışan hali)
Motora dokunmadan, stream'in mevcut remote-provider desteği üzerine kuruldu:
- **`provider_url`** admin_config'e eklendi (sunucu-taraflı → telefon/Mibox/PC hepsi aynı sağlayıcıyı görür).
  `detect_provider` sırası: query > cookie > **admin.provider_url** > yerel.
- Admin panelde **"Uzak Sağlayıcı"** kartı: URL alanı + **"watchbuddy"** hızlı-set + **"Yerel'e dön"**.
- **KANIT (Docker uçtan uca):** `provider_url=https://stream.watchbuddy.tv` → `/api/admin/catalog`
  **203 eklenti** (DiziPal dahil!) netmovies'in kendi `RemoteProviderClient`'ıyla geldi. CF/domain
  bakımı upstream'de. DiziPal remote katalogda otomatik gelir — bizde iş gerektirmez.
- **"Şimdi canlı tara"** butonu: `/api/admin/health?force=1` → engine'in 6 saatlik cache'ini atlar
  (kanıt: yerel 7 kaynak, 4 canlı).

### KÖK NEDEN #1 — remote provider URL (fix `61c4a81`)
`RemoteProviderClient` uç noktalara `/api/v1/...`'i **kendi ekler**. provider_url'e `/api/v1` yazmak →
`.../api/v1/api/v1/get_all_plugins` çift path → **403**. **Kök adres ver** (`https://stream.watchbuddy.tv`,
`/api/v1` YOK). watchbuddy UA/TLS değil, path bug'ıydı. → Memory: `remote-provider-url-root`.

### KÖK NEDEN #2 — tünel netns (bu oturumda operasyonel, kod değil)
`docker compose up -d --build stream` (tek servis rebuild) → stream container recreate → cloudflared
`network_mode: service:stream` ile eski stream'e pinli kaldı → öksüz ("network is unreachable",
restart edilemiyor) → **w.evaitec.com 530**. **Fix:** `docker rm -f netmovies-tunnel &&
docker compose --profile tunnel up -d cloudflared` → tünel 4 bağlantı kaydetti, `w.evaitec.com`→200.
→ Memory: `stream-tunnel-netns-rebuild`. **Kural:** stream'i tek başına rebuild ettiysen tüneli recreate et.

### TV CLIENT ÇİLESİ (aynı oturum, ikinci yarı) — v0.1.4 → v0.1.10 + firewall
Dean APK'yı kurdu, sırayla çıkan sorunlar ve kökleri (hepsi client-tv, `client-tv/app/src/main`):
1. **192 bağlanmıyor** → yayınlı v0.1.3 APK `NETMOVIES_BASE_URL=192.168.x` ile derlenmişti. Fix: boş bırak → varsayılan `w.evaitec.com` (v0.1.4). `build.gradle.kts`.
2. **Siyah-üstüne-siyah yazı** → tv-material3 `Text` rengini `LocalContentColor`'dan alır, içerik Surface'te değildi → varsayılan siyah. Fix: `MainActivity` `CompositionLocalProvider(LocalContentColor=#EDEDF2)` (v0.1.5). Ekran görüntüsüyle DOĞRULANDI.
3. **D-pad'de seçilemiyor** → ilk karta focus yok. Fix: `HomeScreen` `FocusRequester`+`requestFocus` (v0.1.5).
4. **OTA "İndir" çalışmıyor (TV)** → `Updater.openDownload` `ACTION_VIEW(url)` = tarayıcı, TV'de tarayıcı YOK. Fix: uygulama-içi OkHttp indir + `FileProvider` ile kur (v0.1.6). `Updater.kt`+`UpdateViewModel.kt`.
5. **IPv6 bağlanamıyor** (`.../[2a06:98c1..]:443`) → CF IPv6, ağda bozuk. Fix: `PreferIpv4Dns` (v0.1.7). `data/HttpDns.kt`.
6. **Dokunmatik yok (telefon)** → tv-material3 D-pad odaklı. Fix: foundation `clickable` (`ui/TouchButton.kt`, PosterCard Box) (v0.1.9).
7. **CF-TR IP bloku** (`188.114.96.7:443` açmıyor) → Cloudflare cihaz ağına TR'de bloklu IP döndürüyor; gerçek kayıtlar 172.67.143.235/104.21.55.13 çalışıyor. **KÖK ÇÖZÜM = yerel yol** (aşağıda) + palyatif IP-pin/timeout (v0.1.10).

**ASIL ÇÖZÜM — "önce local, olmazsa uzak" (v0.1.8) + firewall:**
`data/ServerResolver.kt`: açılışta `LOCAL_URL` (192.168.1.185:3310) `/api/v1/health` 1.5s probe → canlıysa yerel, değilse `BASE_URL`. `BaseUrlInterceptor` tüm istekleri+posterleri aktif sunucuya yönlendirir. `HomeViewModel.load` başında `ServerResolver.reset()` (retry taze seçer). **Windows firewall "NetMovies 3310" inbound AÇILDI** (bu oturum, admin PS). TV aynı WiFi → yerele düşer, CF bypass. → Memory: `client-cf-tr-local-first`.

**Not:** `LOCAL_URL` gradle property `NETMOVIES_LOCAL_URL` (vars. `http://192.168.1.185:3310`). PC LAN IP değişirse burayı güncelle. OTA download da CF/GitHub IP'ye takılabiliyor → gerek/kesin durumda APK elle.

### Değişen dosyalar (stream tarafı)
- `stream/Public/Home/Libs/admin_config.py` — `provider_url` default + normalize
- `stream/Public/Home/Libs/helpers.py` — `detect_provider` admin fallback
- `stream/Public/Home/Routers/admin.py` — `admin_health ?force=1` forward
- `stream/Public/Home/Static/JS/admin.js` — provider alanı render/collect + watchbuddy(kök) + force health
- `stream/Public/Home/Templates/pages/admin.html.j2` — Uzak Sağlayıcı kartı + dürüst notlar

### Canlı durum (bu oturum sonu)
- Yerel: stream+engine `healthy`, `/api/v1/health`→200, ana sayfa(auth)→200, provider=**yerel** (varsayılan).
- Dış: `https://w.evaitec.com/`→200 (tünel yeniden bağlandı).

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-28 (3. çeyrek) — "dizi sayfası eski düzen" = tarayıcı cache (KÖK NEDEN + kalıcı fix)

**Durum: KÖK NEDEN BULUNDU + DOĞRULANDI, kalıcı önlem commit'li (`de54619`). Kod bug'ı DEĞİLDİ.**

### Şikâyet
Dean: "Diziler/Türkçe diziler sayfasına girince eski düzene geçiyor, posterler her sayfada aynı
boyutta olsun." (Poster boyutu sorunu.) Dean tercihi: **hepsi küçük 130px, her sayfada** (AskUserQuestion).

### Kök neden (kanıtlı) — sunucu DEĞİL, tarayıcı cache
- Küçük 130px grid kuralı (`a1b63fa`, "arama/kategori posterleri... 130px") **zaten canlıda**.
- **KANIT (curl, uçtan uca):** `curl localhost:3310/static/home/CSS/style.bundle.min.css` →
  `grid.grid-results{grid-template-columns:repeat(auto-fill,minmax(130px,1fr))}` — sunucu doğru servis ediyor.
- **Asıl sorun:** `_html_taban.html.j2` L66/L134'te CSS/JS bundle linkinde **cache-bust YOKTU**
  (`style.bundle.min.css` sabit URL). Bundle içeriği değişse de URL sabit → tarayıcı eski bundle'ı
  süresiz cache'liyor → Dean hard-refresh yapmadan eski büyük grid düzenini görüyordu. Dizi sayfasında
  fark etmesi: o sayfayı fix'ten sonra ilk kez o cache'le açtığı için.
- Not: host diskindeki `style.bundle.min.css` (08-27) `card.css`'ten (08-28) bayat GÖRÜNÜYOR ama önemsiz —
  bundle gitignored, boot'ta `basla.py` üretiyor; **container'ın bundle'ı doğruydu** (grep=1).

### Fix (commit `de54619`)
- `helpers.py` → `build_context`'e `asset_version` (CSS+JS bundle mtime'ının max'ı, `_asset_version()`).
- `_html_taban.html.j2` → CSS (L66) + JS main (L134) linkleri `?v={{ asset_version }}` ile versiyonlandı.
- Etki: bundle her rebuild'de (boot/elle minify) URL değişir → tarayıcı otomatik taze çeker.
  Sözdizimi doğrulandı (`py_compile` OK). **Bir sonraki rebuild'de aktif olur** (bu oturumda restart YAPILMADI → tünel korundu).

### Dean'in yapacağı (anında çözer, deploy beklemeden)
1. **Tarayıcıda Ctrl+Shift+R (hard refresh)** → posterler her sayfada 130px olur (sunucu zaten doğru).
   Telefon/TV Bro: önbelleği temizle.
2. Cache-bust'ı kalıcı aktive etmek için müsaitken normal deploy: `git pull && docker compose up -d --build`.

### Uygulama (native TV) durum özeti — Dean sordu (bu oturum başı)
- **Web (PWA): HAZIR/canlı.** **Native Compose-TV: POC** — derleniyor, OTA'lı release var, içerik listeleniyor;
  🔴 HLS oynatma Mi Box'ta uçtan uca DOĞRULANMADI (risk kapısı). Süre tahmini: oynatma bugün geçerse
  "izlenebilir native app" ~1 hafta; cast+diziler+arama tam cila ~2-3 hafta. Detay: `memory/client-tech-decision.md`.

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-28 (2. yarı) — POC BUILD DOĞRULANDI + PUBLIC/RELEASE/OTA + İÇERİK FIX (canlı)

**Durum: UÇTAN UCA ÇALIŞIYOR + DOĞRULANDI (ev makinesi + tünel).** Bu oturumda POC gerçekten
derlendi, GitHub'a public + release + OTA kondu ve "ana ekran boş" kök nedeni bulunup canlıya alındı.

### Client (Compose-TV) — derlendi + yayında
- **BUILD DOĞRULANDI:** temiz checkout'tan `./gradlew.bat assembleDebug` → BUILD SUCCESSFUL,
  `app-debug.apk`. Stack: AGP8.5.2/Kotlin2.0.20/Gradle8.9/Java21, minSdk26.
- **Repo PUBLIC yapıldı** (önce 78 commit secret taraması TEMİZ — `.env`/token yok).
- **Release + OTA:** `v0.1.0..v0.1.3-poc` release'leri. OTA = app açılışta GitHub `/releases`
  kontrol → "Güncelleme mevcut" şeridi → **İndir (tarayıcıda)** → kur. (In-app FileProvider kurulum
  telefonda tökezledi → tarayıcı-indirmeye çevrildi.) `/releases/latest` prerelease'i atlar → `/releases` listesi.
- **v0.1.3 kilit sürüm:** `BASE_URL=w.evaitec.com` (tünel) → **her ağdan çalışır**, ev WiFi şart değil.
  Ayrıca dark tema (tv `darkColorScheme`), adaptive/yuvarlak ikon, okunur banner.
- **İyileştirmeler:** kategori-raylı home, player yükleniyor/hata-retry + keepScreenOn, posterler `/proxy/image`.
- APK direkt: `https://github.com/evatechnosoft/netmovies/releases/download/v0.1.3-poc/netmovies-tv-v0.1.3-poc.apk`

### 🔴 "Ana ekran boş" KÖK NEDEN + FIX (PR #5, canlı) — en kritik
1. **Route bug:** stream'de `/api/v1/aggregate_new` route'u **kayıtlı değildi** (ilk build docker-cache
   registration'ı atlamıştı) → istek `/`'a **302** → app hiç veri almıyordu. Düzeltildi (Routers/__init__ cp + rebuild; artık image'da kalıcı, grep=1).
2. **Boş-cache zehiri (asıl içerik fix, `_cacheable`):** `fuck_dmca` boş agregasyonu da 10dk cache'liyordu →
   geçici kaynak timeout'unda 0 cache'lenip kaynak düzelse bile boş servis. Fix: `/aggregate_new` yalnız
   `items` doluysa cache'lenir. **KANIT: stream `aggregate_new?type=movie` → count=20** (serie_local=15).
3. **Dayanıklılık:** engine `_istek.py` aggregate timeout 30→120s; `WEB_WORKERS 1→2` (tek worker yavaş
   agregasyonda blokluyordu); `load_links` timeout 10→25s; provider_client split timeout.
- **PR #5 merge edildi** → `claude/...` (`a4cfd76`). Stream image merged kodla rebuild → **durable**.

### Kaynak sağlığı (kanıt) + kalan
- **4/7 canlı:** HDFilmCehennemi, DiziBox, DiziYou, M3U · **ÖLÜ:** DiziMom, Dizilla, RecTV.
- HDFC `get_main_page` ham çağrıda **20 film** dönüyor (çalışıyor). DiziBox **yavaş** (ReadTimeout, bazen düşer).
- **Kalan (bloke değil):** DiziYou kategorileri tür-bazlı → generic "serie" hint'ine uymuyor (serie_local/foreign çalışıyor);
  DiziBox scrape hızı. İçerik zenginliği için sonraki tur.

### ⚙️ Operasyon dersleri (bu oturumdan — gelecek dikkat)
- **Stream restart = `basla.py` her açılışta 51 dosya minify (~1dk boot)** → restart sonrası 3310 geç 200 verir; "down" sanma.
- **`docker compose up --build stream` engine'i de recreate edebilir** → engine cold-boot (~40s domain keşfi) → o an aggregate boş.
  Kod değişikliğini **restart'la boğmadan** yay: tek `docker cp` + tek restart; art arda eşzamanlı probe = tek-worker'ı tıkar.
- **Git Bash docker exec/cp mutlak yol** `/usr/src/...` → `MSYS_NO_PATHCONV=1` şart (yoksa `C:/Program Files/Git/usr/...` olur).
- Stream kök: `/usr/src/Stream`, Engine kök: `/usr/src/KekikStreamAPI`.
- Reusable rehber: `~/.ai/guides/android-client-engine-ota.md` (gitignore `data/` tuzağı + OTA/release reçetesi).

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-28 (1. yarı) — 4 web şikayeti fix + Kotlin Compose-TV POC iskeleti

**Durum: KODLANDI + COMMIT'Lİ, runtime doğrulaması Dean'de.** Commit'ler: `a1b63fa..a052479`.
Dean'in bildirdiği 4 web şikayetinin kök-neden çözümü + Compose-TV client POC'u başlatıldı.

**Web fix'leri (stream):**
- `a1b63fa` **Sayfa düzeni birliği**: `.grid.grid-results` sabit 6-grid (geniş ekranda ~190px büyük)
  → `repeat(auto-fill, minmax(130px,1fr))` = ana sayfa/provider (130px carousel) ile aynı küçük boy.
  **Deploy:** bundle yeniden üret + hard refresh (aşağıda).
- `c445633` **#2 Poster proxy** (`/proxy/image`, SSRF korumalı: http(s)+public IP, 8MB, 7g cache) —
  posterler artık stream/residential IP + doğru Referer ile; Jinja `poster()` global + JS wrap ile
  TÜM poster sayfalarında. **#3 Kaynak dayanıklılığı**: provider_client split timeout (connect=5s,
  read=20s) → ölü kaynak hızlı düşer; error.html.j2'ye "Tekrar dene". **#4 Nav**: ilk mousemove'da
  mouse-mode otomatik (D-pad tetiklemez → TV korunur).

**Kotlin Compose-TV POC (`client-tv/`)** — `a052479`:
- Liste (`aggregate_new` → 6'lı poster grid, D-pad) + Media3/ExoPlayer HLS (load_links referer/UA
  header enjekte, proxy'siz). Retrofit+kotlinx.serialization. gradlew ile buildable (wrapper jar dahil).
- Java21/AGP8.5.2/Kotlin2.0.20/Gradle8.9, minSdk26. Build+sideload: `client-tv/README.md`.
- stream'e `/api/v1/aggregate_new` client-facing proxy eklendi (client bunu çekebilsin diye).
- ✅ **BUILD DOĞRULANDI** (`524d13d`): temiz checkout'tan `./gradlew.bat assembleDebug` → BUILD
  SUCCESSFUL, `app-debug.apk` ~12.7MB. Stack (AGP8.5.2/Kotlin2.0.20/Gradle8.9/Java21) tutuyor.
- ⚠️ **OYNATMA (HLS) doğrulanmadı** — sadece DERLEME doğrulandı. Uçtan uca Mi Box'ta test edilecek
  (encoded_url çift-kodlama + segment header riskleri `client-tv/README.md`).
- **İyileştirmeler** (hepsi derlenip doğrulandı): kategori-raylı home + player yükleniyor/hata-retry
  + posterler `/proxy/image`'den + `keepScreenOn`.
- 🐞 **GITIGNORE TUZAĞI (gelecek oturumlar dikkat):** kök `.gitignore`'daki `data/` kuralı Kotlin
  `tv/data/` KAYNAK paketini de gizliyordu → ilk commit'te data katmanı sessizce atlanmıştı.
  `client-tv/.gitignore`'a negasyon eklendi (`!.../tv/data/`). Yeni `data/` adlı KAYNAK dizini
  eklerken aynı tuzağa dikkat.

**POC'un sıradaki adımı:** Diziler (serie) sekmesi — `aggregate_new type=serie` + `load_item` →
bölüm seçimi → oynat. **ÖNCE oynatma Mi Box'ta doğrulanmalı** (üstüne feature bindirmeden).

**Dean'in yapacağı doğrulama:**
1. Web fix'lerini canlıya al (restart yok, tünel korunur):
   `MSYS_NO_PATHCONV=1 docker exec -w /usr/src/Stream netmovies-stream python3 -c "from build_assets import minify_assets, bundle_css; minify_assets(); bundle_css()"`
   → Ctrl+F5. (Yeni Python endpoint'ler/timeout için stream restart de gerekir: `docker compose up -d`.)
2. TV client: `cd client-tv` → `gradle.properties`'e ev IP → `./gradlew.bat assembleDebug` → `adb install`.

---

## 0.1 ÖNCEKİ OTURUM — 2026-08-27 (akşam) — cloudstream UI + ölü-kaynak dayanıklılığı + CLIENT KARARI

**Durum: AYAKTA + DOĞRULANDI (ev makinesi, `localhost:3310`).** UI yeniden düzenlendi, deploy `docker cp`
+ Jinja auto-reload / CSS minify ile yapıldı (restart YOK → tünel korundu). Commit'ler: `c48cbd7..9c224ef`.

**Bu oturumun işleri (commit'li):**
- `c48cbd7` **ölü kaynak dayanıklılığı** (KÖK NEDEN): `home_categories` sadece SAĞLIKLI kaynağı kart yapar;
  `aggregate_new` ölü kaynağı `plugin_health`'ten atlar (30s→~6s hız); `_is_alive` 403/451/404/410'u ÖLÜ
  sayar (eskiden bloke domaini "canlı" seçip içeriksiz kart üretiyordu). Engine: `/home_categories` endpoint
  + `serie_local`/`serie_foreign`/`live` ipuçları. **Kanıt:** 4/7 kaynak canlı (HDFC/DiziBox/DiziYou/M3U),
  home 200/191 poster; ölü DiziMom/RecTV kartları gizlendi.
- `86ddafa` **cloudstream tarzı kompakt ana sayfa**: üstte tek satır ince pill buton bandı (`quick-nav`) —
  kategoriler + Favori/İzlenecek/Devam + Kaynaklar + Kanallar. Büyük kutular (Resmi Kaynaklar/Öne Çıkanlar/
  Kanallar dropdown) kaldırıldı → butona basınca PANEL açar (accordion + JS lazy fetch: `/api/v1/favorites`,
  `/lists/izlenecek`). Header'a "reklamsız izle" tagline.
- `68fc088` **player**: sinema modunda geri butonu autohide (sol-üst köşe hover/focus'ta çıkar; hep görünmüyordu).
- `9c224ef` **tutarlı 6'lı grid**: `.grid.grid-results` sabit `repeat(6,1fr)` (küçük ekran 5/4/3/2), specificity
  ile responsive `.grid`'i geçer → kategori/arama/eklenti "her sayfa aynı". `.page-header-title` xxl→lg (küçüldü).
  **Not:** CSS bundle değişti → tarayıcıda hard refresh (Ctrl+F5) gerekir.

### 🎯 YENİ YÖN — CLIENT: Kotlin + Jetpack Compose for TV (2026-08-27 kararı)
**PWA Android TV'de çalışmıyor** (Chrome yok → TWA sağlayıcı yok; "bubblespan"=Bubblewrap da TWA ürettiği için
çözüm değil). Karar: **Kotlin + Compose for TV + Media3/ExoPlayer** (TV'de D-pad+HLS en olgun/resmi-stable).
Kapsam: Android (TV öncelik + telefon); **web=mevcut PWA kalır**. Mi Box'a sideload (ARM ABI). Engine DEĞİŞMEZ
(client-agnostic) — client sadece API tüketir. Detay: `memory/client-tech-decision.md`.
**Cast/kumanda (WatchBuddy modeli — Dean isteği):** Telefon (mevcut PWA) = **controller** (hızlı arama/seç,
OYNATMAZ → sadece "TV'de oynat" komutu); TV (Compose) = **player** (oynatır + kendi D-pad'i); Engine = **relay**
(telefon `POST /api/v1/cast` → TV `GET /cast` long-poll/WS dinler; merkezî, tünelle uzaktan da çalışır). Telefon
PWA controller rolünde ideal (oynatma yok → TV kısıtı yok). Backlog'daki "kendi watch-party" bu.

**İlk adım (POC, 3 parça):** (1) Compose-TV app: `aggregate_new` listele + HLS oynat (Media3) + `/cast` dinle;
(2) Telefon PWA'ya "TV'de oynat" butonu → `POST /cast`; (3) Engine `/cast` relay endpoint (scrape/proxy DEĞİŞMEZ).
Mi Box + telefon uçtan uca test. **Bu yeni iş taze oturumda başlamalı** (bu oturum uzun). Detay: `memory/client-tech-decision.md`.

### Bekleyen (bu oturumdan devir)
1. **Sıralama/"eski diziler"**: aggregate DiziBox "Yerli Diziler" ARŞİVİ çekiyor (eski dahil), "yeni eklenen"
   feed'i değil → yeni sıralama yok. Kaynak feed davranışı incelenmeli (dikkatli, içerik bozmadan).
2. Yabancı dizi kartı: DiziMom (yabancı kaynağı) sandbox'ta ölü → ev'de canlı mı doğrula.
3. Kalıcı image: değişiklikler `docker cp` ile canlı + git commit'te; image güncel değil → müsaitken
   `git pull && docker compose up -d --build` (build Docker Desktop'ta ara ara 500/asılma yaşadı).

---

## SON OTURUM — 2026-08-27 (TV/kumanda UX + sinema player + yeni kaynaklar + local deploy)

**Durum: AYAKTA + DOĞRULANDI (ev makinesi).** `localhost:3310` → HTTP 200 (home + `/api/v1/health`).
Auth **bilerek KAPALI** (Dean kararı — kumandayla şifresiz giriş). Container'lar rebuild edildi;
9 commit push'landı (`e9d6acc..547c496`), dal `origin` ile eşit.

**Bu oturumun commit'leri (yeni→eski):**
- `547c496` sayfalar: basılı-tutma menüsü + odak çerçevesi HER sayfada (tv-home-actions base'e taşındı; `link` modu = arama/kategori kartında tek tık AÇAR, gezinme korunur)
- `73f0929` **player sinema modu**: `body.cinema-mode` → sadece video (header/footer/başlık/kaynak-listesi/benzer/geri gizli, video 100dvh contain). Kaynak + Harici oynatıcı **dişli menüsüne** taşındı (overlay, `data-cinema-open`). sources.js/loadVideo'ya DOKUNULMADI. Geri-alma: body sınıfı kalksın.
- `e181c0c` global `:focus-visible` çerçevesi (her sayfa, D-pad+klavye+arama kutusu) + kanallar hafif metin listesine çevrildi
- `7e9371e` mouse-modu güçlü imleç vurgusu + **auth KAPALI** (compose'da `AUTH_USER/PASS: ""`) + kurulamayan tarayıcıda ölü PWA butonu gizle
- `335ab86` PWA kur butonu + mouse modu + dokunma efekti
- `c23c53b` merkezi izleme ilerlemesi (content_url migration + `/api/v1/progress`) + kullanıcı listeleri (izlenecek/planlandı/takip, SQLite `user_lists`)
- `caac111` hızlı kanallar rafı + izlemeye-devam kutusu + aggregate timeout(6s/3s) + "Son Bölümler" kategori yakalama
- `8de0d0e` **yeni kaynaklar**: DiziBox, Dizilla, DiziMom + ortak `__dizi_common.py` (engine'de YÜKLÜ doğrulandı)
- (`docs/DENETIM-2026-08-26.md` = eski `search.md` salt-okunur denetim raporu, docs'a taşındı)

### ⚠️ Doğrulama durumu (kanıtlı)
- CSS bundle canlı: `mouse-mode`/`focus-visible`/`channels-list-item` bundle'da mevcut (grep).
- Player cinema + linkMode image'da mevcut (container grep).
- 3 dizi plugin engine'de yüklü (`get_plugin_names`).
- **Runtime tam test EDİLMEDİ**: film oynatma/sinema modu/overlay'ler tarayıcıda Dean'in gözüyle doğrulanacak.

### 🔴 Kanallar BOŞ (kök neden: data, kod değil)
`quick_channels` → `result:[]`. Sebep: `.env`'de **`M3U_SOURCES` boş** + RecTV bloke. Liste UI'si hazır
ama kaynak yok. Çözüm: `.env`'e M3U listesi ekle VEYA güncel `RECTV_URL` bul.

### ⚙️ Deploy operasyon notları (ÖNEMLİ)
- **watchmedo override → `docker-compose.dev.yml` (OPT-IN) olarak yeniden adlandırıldı.**
  Eskiden `docker-compose.override.yml` otomatik uygulanıyordu → Windows'ta startup'ı kilitliyordu.
  Artık **varsayılan `docker compose up -d --build` = temiz production** (her yerde güvenli, doğrulandı: 0 watchmedo).
  - **Production / ZimaOS 7/24:** `docker compose -f docker-compose.yml up -d --build` (güncelleme: `git pull && ... up -d --build`).
  - **Dev auto-reload (opt-in, sadece Linux/ZimaOS):** `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`.
    Windows'ta KULLANMA (inotify geçmez) → base + elle rebuild.
- **w.evaitec.com CANLI** (bu oturumda tünel yeniden bağlandı, HTTP 200 doğrulandı). Tünel `network_mode: service:stream`
  → stream her recreate olunca kopar; geri: `docker compose -f docker-compose.yml --profile tunnel up -d --no-deps --force-recreate cloudflared`.
- Engine container'ı bir ara `<hash>_netmovies-engine` adıyla kaldı (kozmetik); `docker compose -f docker-compose.yml up -d` normalize eder.

### Kalan iş (bu oturumdan)
1. **Dean runtime testi**: film aç → sinema modu (sadece video), kontroller TV Bro imleç modunda çıkıyor mu, dişli→Kaynak/Harici oynatıcı overlay + Nova/VLC çalışıyor mu, "ana sayfa açıyor" hâlâ var mı (varsa hangi film/kaynak).
2. **Kanal kaynağı**: `M3U_SOURCES` veya `RECTV_URL` gir.
3. Yeni dizi kaynakları (DiziBox/Dizilla/DiziMom) + DiziYou **uçtan uca** test (içerik dönüyor mu, selector tutuyor mu).
4. P0 (devam): CF token rotate + kalıcı tünel origin `stream:3310` (Dean CF panel).
5. ZimaOS 7/24 deploy (PC o ağa gelince).

---

## SON OTURUM — 2026-08-24 akşam (canlı + oynatıcı/UI iyileştirmeleri)

**Durum: ÇALIŞIYOR.** İzleme her yerden: `localhost:3310`, `192.168.0.28:3310` (LAN),
`w.evaitec.com` (tünel). Auth: **`dean` / 1234** (kullanıcı adı KÜÇÜK harf). Web player'da
film oynuyor (Dean doğruladı). `.env` içinde `CF_TUNNEL_TOKEN` dolu (netmovies tunnel `46f5bbe3`).

**Bu oturumun commit'leri:**
- `2fb5cb1` dev-reload YAML katlama fix + cloudflared origin (network_mode: service:stream)
- `4822141` oynatıcı Türkçe dublaj/altyazı otomatik (yeni `lang-utils.js`) + i18n header reload
- `c422fa6` mobil dokunmatik: tek dokunuş oynat/duraklat + orta çift-dokunuş tam ekran (sol/sağ seek)
- `7695919` pagination: "sonraki sayfa" yalnızca `SAYFA` placeholder'lı (gerçek sayfalayan) kaynaklarda
- `f99eff0` layout: ana sayfa/player üst boşluğu azaldı, ilk raf arama çubuğu altına
- `5228bea` canlı arama (yazdıkça, 300ms debounce, min 3 karakter)
- `3b7891d` harici oynatıcı (Nova/VLC/MX): `force_proxy=1` ile segmentlere header enjekte

### ⚠️ KRİTİK ORTAM NOTLARI (Windows'ta geliştirenler için)
1. **watchmedo (otonom reload) Windows/Docker Desktop'ta ÇALIŞMIYOR** (volume inotify container'a
   geçmiyor). Kaynak `.js/.css` değişince `.min.js/.min.css` OTOMATİK üretilmez. Elle tetikle
   (container restart YOK → tünel korunur):
   ```
   MSYS_NO_PATHCONV=1 docker exec -w /usr/src/Stream netmovies-stream \
     python3 -c "from build_assets import minify_assets, bundle_css; minify_assets(); bundle_css()"
   ```
   (Git Bash'te `MSYS_NO_PATHCONV=1` şart, yoksa `-w` path'i bozulur: "Cwd must be absolute".)
2. **cloudflared `network_mode: service:stream`** → stream her `restart`/`recreate` olduğunda tünel
   KOPAR (`w.evaitec.com` → HTTP 530). Düzeltme: `docker compose --profile tunnel up -d
   --force-recreate cloudflared`. **Kalıcı çözüm (yapılmadı):** Cloudflare panelinde tünel origin'i
   `localhost:3310` → `stream:3310` yap, cloudflared'i normal `internal` network'e al → restart'lara dayanır.

### Kalan iş (aciliyet sırası)
1. **Harici oynatıcı testi** — Dean force_proxy sonrası Nova/VLC ile film başlıyor mu doğrulayacak.
2. **Kalıcı tünel** — CF panel origin `stream:3310` (yukarıda; Dean panel erişimi).
3. **ZimaOS 7/24 deploy** — PC şu an ZimaOS ağında DEĞİL ("Deancjx"/`192.168.1.x` WiFi menzilde yok;
   PC "Huntercjx"/`192.168.0.x`'te). `ssh deanos` timeout. PC o ağa gelince: `ssh deanos` → git clone
   `/DATA/AppData/netmovies` + `.env` (dean/1234 + CF_TUNNEL_TOKEN) + `docker compose --profile tunnel up -d --build`.
4. **Mi Box** — JioSphere/TubeMate genel web açamıyor; **TV Bro** (Android TV browser) öner. w.evaitec.com çalışıyor.
5. **"Smallville" gibi eski diziler** — kaynak ana sayfa sıralaması; "yeni çıkanlar önceliği" olarak ele alınacak (pagination'dan AYRI).
6. Mi Box native D-pad oynatıcı kontrolleri — ertelendi (TV Bro imleç modu yeterli olabilir).

---

## 1. Proje nedir?
Reklamsız, kişisel, "tıkla-izle" odaklı **film / dizi / canlı TV** uygulaması. İki servis + iki yardımcı:
- **engine/** — KekikStream 3.8.x (Python **3.14**) sağlayıcı API. Kendi eklentilerimiz `engine/Plugins/`.
- **stream/** — Web arayüzü + header-enjekteli video/altyazı **proxy** + API gateway. (WatchBuddy-tv/Stream fork'u vendor'landı, reklamları söküldü.)
- **doh** — DNS-over-HTTPS resolver (ISP DNS engelini aşar), docker-compose servisi.
- **cloudflared** — `w.evaitec.com` → eve tünel (profile: tunnel).

Tek komut: `docker compose up -d --build` → `http://localhost:3310` (auth: **dean / 1234**).

---

## 2. Kaynaklar (engine/Plugins/)
| Eklenti | İçerik | Not |
|---|---|---|
| `RecTV.py` | Canlı TV + Son Filmler + Son Diziler | Tek API, extractorsuz. Domain `b.prectvNN.sbs` sık değişir → `.env` `RECTV_URL` ile güncelle |
| `HDFilmCehennemi.py` | Film (TR dublaj+altyazı) | Gömülü P.A.C.K.E.R unpacker |
| `DiziYou.py` | Dizi (yerli/yabancı, dublaj+altyazı) | Extractorsuz, doğrudan storage m3u8 |
| `M3UPlaylist.py` | Kendi M3U/M3U8 listelerin | `M3U_SOURCES` env; EXTVLCOPT/EXTHTTP header desteği |

Yeni eklenti ekleme rehberi: `docs/KURULUM.md` §6. Referans scrape mantığı: `keyiflerolsun/Kekik-cloudstream` (Kotlin).

---

## 3. Öne çıkan özellikler ve dosya haritası
- **Yönetim paneli:** `stream/Public/Home/Routers/admin.py`, `stream/Public/Home/Libs/admin_config.py`, `stream/Public/Home/Static/JS/admin.js`, template `pages/admin.html.j2`. Kaynak/kategori gizleme (Asya vb. varsayılan gizli), öne çıkanlar, puan eşiği, canlı sağlık göstergesi. Merkezi JSON config (`/data/admin.json`).
- **Sağlık kontrolü:** `engine/Public/API/v1/Routers/plugin_health.py` (açılışta + 24 saatte bir; `/api/v1/plugin_health`).
- **Harici oynatıcı:** `stream/Public/Home/Static/JS/external-player.js` + `pages/player.html.j2`. Nova/MX/VLC/kopyala (Android intent + proxy URL). Kaynağı `netmovies:playback` event'i ile `VideoPlayer.js` yayınlıyor (~satır 2094).
- **PWA:** `stream/Public/Home/Static/manifest.webmanifest`, `_html_taban.html.j2` (meta), `main.js` (SW register), `sw.js` (no-op).
- **Basic auth:** `stream/Core/Modules/_auth.py` (env `AUTH_USER`/`AUTH_PASS`; proxy/health/static/manifest muaf). `Core/__init__.py`'de register.
- **4K donma:** `stream/Public/Proxy/Libs/segment_cache.py` (256MB, env `SEGMENT_CACHE_MB`), `video-utils.js` hls config (buffer 60s + retry).
- **DoH + hls.js fallback:** `docker-compose.yml` (`doh` servisi), `VideoPlayer.js` `loadHlsLibrary` (self-host → jsDelivr → cdnjs), `stream/Dockerfile` (hls.js build-time indirme).

Karar/kurulum dokümanları: `docs/MIMARI_SPEC.md`, `docs/ISKELET_SECIMI.md`, `docs/VENDOR.md`, `docs/KURULUM.md`, `docs/DEPLOY.md`.

---

## 4. Deploy modeli (kullanıcının kararı)
**Hibrit:** motor **evde** çalışır (residential IP — kaynaklar datacenter/Azure IP'sini engelliyor), `w.evaitec.com` **Cloudflare Tunnel** ile eve bağlanır. Auth: dean/1234 (`.env`, gitignored). Detay: `docs/DEPLOY.md`.

---

## 5. ⚠️ Test durumu / bilinen sınırlar
- **HDFilmCehennemi: CANLI DOĞRULANDI (Dean'in evinden).** Film oynuyor. Site player'ı her istekte yapısı değişen JS obfuscation'a (`dc_*` fonksiyonları) geçti; elle regex çözümü kırıldı. Çözüm: **V8 (py_mini_racer) ile sitenin kendi player JS'ini çalıştırıp `jwplayer().setup(cfg)` yakalama** → `engine/Plugins/_js_player.py` (rapidrame/CloseLoad ailesi için ortak, diğer kaynaklarda da kullanılacak).
- **DiziYou:** domain düzeltildi (upstream Kotlin `diziyou3.com` = ÖLÜ; artık `diziyou.one`). `__kekik_domain.discover_main_url` artık adayları **canlılık kontrolünden** geçiriyor. Selector'ların diziyou.one'da tuttuğu henüz uçtan uca doğrulanmadı.
- **RecTV: BLOKE.** Tüm `b.prectvNN.sbs` (38/39/40 + 41-60 taraması) ölü; güncel domain tahmin edilemiyor. Güncel domain bulununca `.env` `RECTV_URL` ile gir. API-only kaynak (kendi sitesi yok).
- **Egress/IP:** Kaynaklar datacenter IP'sini engelliyor (KANIT: watchbuddy.tv hosted API HDFC load_links'te 403; ev-engine başardı). **Azure ACA + AFD/DNS Zone bunu ÇÖZMEZ** — AFD sadece inbound; scraping outbound=Azure egress IP=bloklu. Engine residential (ev) egress'te kalmalı; inbound için CF Tunnel (veya AFD sadece inbound).
- **hls.js** kullanıcının makinesinde Dockerfile ile indi; ABR config iyi (480p başlar→1080p ramp, 60s buffer, capLevelToPlayerSize, nudge).

---

## 6. Sonraki adımlar / backlog

### Stratejik karar (Dean, 2026-08-24)
- **Kendi upstream'imizi biz yazıyoruz** — vendored fork'u yamamak yerine kaynakları tersine mühendislikle çözüp (V8 yaklaşımı) kendi Python plugin/extractor'larımızı maintain ediyoruz. Referans: Kekik-cloudstream + recloudstream/extensions (Kotlin). Gerekirse Kotlin de kendimiz yazarız (clone/rebase).
- **Client = PWA** (Flutter değil, şimdilik). Engine client-agnostik (MIMARI_SPEC ADR-1) — Flutter sonra opsiyonel.
- Yapı **modern/genişletilebilir** olsun; aşağıdaki roadmap sonradan eklenecek.

### Yakın backlog
1. ~~**Birleşik "Yeni Çıkanlar" UI (task 1):**~~ ✅ **YAPILDI** (commit `0440ff9`). `provider_client.get_aggregate_new` + `ana_sayfa.py` iki çağrıyı `asyncio.gather` ile paralel çekiyor, `admin_config.filter_aggregate_items` (gizli kaynak/kategori + puan eşiği) süzüyor, `home.html.j2` `yeni_rafi` makrosu iki yatay raf (carousel CSS reuse, JS'siz). Kaynak-bağımsız liste hedefi tamam. **Runtime doğrulaması Dean'in evinde** (engine residential IP + Py3.14 gerekir): `git pull && docker compose up -d --build` → localhost:3310. En az HDFilmCehennemi "Yeni Filmler" rafını doldurmalı.
2. **RecTV güncel domain** bul (bloke) → `RECTV_URL`.
3. **DiziYou uçtan uca** doğrula (diziyou.one selector'ları) + V8 extractor gerekiyorsa `_js_player`'a bağla.
4. **Daha çok kaynak port et** (recloudstream/extensions'tan): FullHDFilmizlesene (RapidVid ailesi — V8 ile hazır çözülür), JetFilmizle, Dizilla, SezonlukDizi...
5. **İzleme geçmişini SQLite'a taşı** (localStorage → cihazlar arası senkron; ADR-3) — öneri motorunun da temeli.

### Roadmap (Dean'in istediği "sonra" eklentileri)
- **Telefondan doğal-dil komut** ("şunu aç" → bulur/açar): telefon → **LiteLLM proxy** intent parse → engine `/search` → otomatik oynat. `POST /api/v1/command` AI gateway.
- **Spotify mantığı öneri** ("izlediklerime benzer"): izleme geçmişi (SQLite) + katalog → LiteLLM sıralama → `/api/v1/recommendations` + ana sayfa "Sana Özel" rafı.
- **Client-server telefon kontrolü / kendi watch-party** (watchbuddy söküldü; kendimizinki).
- InatBox premium, Font/FA self-host, masaüstü harici oynatıcı (.m3u/mpv).

---

## 7. Ortam notları
- engine **Python 3.14** ister (KekikStream 3.8.x). Yerel geliştirme: `uv venv --python 3.14 .venv && uv pip install -r requirements.txt`. (Eski `uv` 3.14 stabil bilmiyordu; `pip install -U uv` ile güncellendi.)
- `.env` gitignored (dean/1234 içinde, repoya girmez). `.env.example` commit'li (şifresiz).
- `basla.py` her açılışta JS/CSS minify eder (`*.min.js`/`*.min.css`, gitignored).
- Bu sandbox'ta foreground `sleep` bloklu; servisleri `run_in_background` veya `nohup` + `curl --retry` ile test et. `pkill -f basla.py` shell'in kendini de öldürebilir — dikkat.

---

## 8. Commit geçmişi (özet)
`docs/` specler → vendor+dereklam → engine plugin altyapısı (HDFilmCehennemi) → hibrit provider + M3U → sağlık kontrolü → admin panel + 4K → harici player + PWA → RecTV → auth + hibrit deploy → DoH + hls.js self-host. Hepsi `claude/stream-app-architecture-86q0sg` dalında, PR #3.

---

## `/clear` sonrası başlangıç promptu (yapıştır)

```
NetMovies (D:\projects\netmovies, dal fix/general-stability @ fe0b39f, 0 kirli dosya,
push edilmedi). Bu oturumda kapanan iş: DiziMom favorilerinin oynamama nedeni (FirePlayer
/tv embed adresi + video.twimg.com referer 403), kaynak zincirinin yüklü TÜM sağlayıcıları
taraması, oynatıcı alt kumanda barı, sol altta 4x4 dakika tuş takımı, bölüm seçiminin ayrı
kutucuk sayfası olması, ve saatin ölü sunucu adresine kilitlenmesi. TV 0.3.1 (vc 301) ve
saat Mini 0.1.4 (vc 104) üç dağıtım yerinde de yayında — hiçbiri CİHAZDA denenmedi.

Önce docs/HANDOFF.md oku ve içindeki "Doğrula" bloğunu koş; repo ile doküman çelişirse
repo doğrudur.

Ortam: yığın docker compose ile evde ayakta (yerel http://192.168.0.29:3310, tünel
https://w.evaitec.com). Tünel 530 dönerse: docker compose --profile tunnel up -d.
Yayın kuralı: geliştirme bitip kanıt yeşilse SORMADAN yayınla (yerel OTA + GitHub release
--target main + evaglass-releases/apps.json), ne yayınladığını söyle.

Öncelik sırası:
1. SIRADAKİ İŞ #1 — TV 0.3.1 ve saat 0.1.4'ü cihazda dene (adımlar HANDOFF'ta).
   Dean'in cihaz geri bildirimi gelene kadar oynatıcı arayüzünde yeni iş açma.
2. Dean bir kusur bildirirse onun kök nedeni.
3. SONRA listesi (kart aksiyonları, madde 2-5) — yalnız Dean isterse.

Yeni iş açma: "isimlendirme standardı / pipeline / environment ayrımı" isteği Dean
tarafından "pardon yanlış oldu" ile İPTAL edildi, kendiliğinden başlama.
```
