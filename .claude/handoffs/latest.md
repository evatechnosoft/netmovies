# DEVİR — 16 Eylül 2026, 17:00 · TV sürümleri, saat OTA'sı, evaitecOTA arayüzü

İki repo değişti. İkisi de push edildi, ikisinde de 0 kirli dosya.

| Repo | Dal | HEAD | Ne |
|---|---|---|---|
| `D:\projects\netmovies` | `fix/general-stability` | `375c168` | TV 0.3.5 · saat 0.1.6 · ajanda · sarma |
| `D:\projects\evaitec-appkit` | `main` | `bd74499` | evaitecOTA TV 0.1.12 · saat 0.1.10 |
| `D:\projects\evaglass-releases` | `main` | (katalog) | apps.json tüm sürümler canlı |

## Yayındaki sürümler (hepsi kanıtlandı, HİÇBİRİ cihazda denenmedi)

| Uygulama | Sürüm | vc | Kanıt |
|---|---|---|---|
| NetMovies TV | 0.3.7 | 307 | OTA `v0.3.5-poc` · release 200 · katalog canlı |
| NetMovies Mini (saat) | 0.1.8 | 108 | OTA `v0.1.6-poc` · release 200 · katalog canlı |
| evaitecOTA TV | 0.1.12 | 13 | release 200 · katalog API'de vc 13 |
| evaitecOTA saat | 0.1.10 | 6 | release 200 · katalog API'de vc 6 |

## Bu oturumda çözülenler

**TV 0.3.2 sarma motoru.** Her tuş tekrarında `exo.seekTo` çağrılıyordu; HLS'de
seek'ler üst üste biniyor, tuş bırakılınca kuyruk işlenmeye devam ediyordu. Ayrıca
repeatable uzun basışta `longFired` true olmuyordu, parmak kalkınca UP tek basış
sayılıp fazladan 10 sn ekliyordu. Şimdi basışlar `seekTarget`'ta birikir, 350 ms
sonra TEK seek. Basılı tutma kademeli: 10 sn / 30 sn / 1 dk. OK hedefi uygular,
duraklatmaz. SOL/SAĞ çift-basış varsayılanı kaldırıldı (tek basışı 300 ms bekletiyordu).

**TV 0.3.3 bölüm kimliği.** İzleme kaydının `episode` alanına liste İNDEKSİ
yazılıyordu; web ise aynı alana `"S4 E8"` yazıyor. Dizilla'nın sızıntılı listesinde
123. sıra kaydedilmiş, liste 32'ye düşünce panel "Devam et — 123. bölüm" yazmıştı.
Artık `S4B8` yazılır ve listede sezon+bölüm ile aranır (`Library.kt` `episodeRef` /
`episodeIndexOf`, test `EpisodeRefTest` 5 vaka).

**TV 0.3.4 ajanda penceresi.** Aralık bugünden başlıyordu; yayın günü geçen bölüm
düşüyordu ama sağlayıcıya günler sonra düşebiliyor. Geriye 7 gün açıldı
(`agenda.py` `_GECMIS_GUN`). Geçmiş bölüm `next_episode_to_air`ta YOK,
`last_episode_to_air`tadır — ikisi de okunuyor. Filmler `movie/upcoming`ten
geliyordu (yalnız gelecek) → `discover/movie` + tarih aralığı. Hafta 24 → 60 satır.

**TV 0.3.5 ajanda üç adım.** Bu Hafta · Bu Ay · Geçmiş. Veri tek turdan gelir, adım
süzer. Poster 130 → 110dp (`NmDim.AgendaPoster`).

**Saat 0.1.5/0.1.6 kendi kendini güncelleme.** `Guncelleme.kt` — APK ev sunucusundan
`/api/v1/app_update?target=wear` ile iner, PackageInstaller oturumuyla kurulur.
Kurulum sonucu şeride yazılır. Listeler `ScalingLazyColumn` oldu (yuvarlak kadran)
ve döner çerçeve kaydırıyor.

**evaitecOTA saat 0.1.9/0.1.10.** (a) Kurulum sonucu yutuluyordu — alıcı yalnız
`STATUS_PENDING_USER_ACTION` işliyordu; artık `InstallOutcome` + `onResume` banner.
(b) Yarım oturumlar sızıyordu, `abandonSession` yoktu; açık oturum yeni kurulumu
iptal ettiriyor. (c) `setSize`/`setAppPackageName`/`setInstallReason` eksikti,
receiver'daki `startActivity` korumasızdı. (d) 0.1.10: katalogdan önce
`requestNetwork` + `bindProcessToNetwork` — saat Bluetooth vekili üzerindeyken
"bağlanamadı" dönüyordu.

**evaitecOTA TV 0.1.12 arayüz.** Tek sütunluk liste → ızgara (kart 250×168dp, sütun
sayısı ekran genişliğinden). Kart içi dolu accent düğmeler kaldırıldı; kart zaten
OK'i işliyor, eylem adı odakta tek satır ipucu. Accent yalnız iş bekleyen kartta.
Üst bardaki eylemler saydam çip. Ölü kod silindi.

**TV 0.3.6 + proxy.** (a) Ayarlar paneli tek sütunda 11 kaynak satırı taşıyordu;
kaynak listesi ayrı alt sayfaya taşındı, ana panelde seçili kaynak tek satır
(`SettingsPanel`, `kaynakListesiAcik`). (b) **Film ortasında "kaynak bulunamadı"nın
kök nedeni:** Dizilla'nın `l.php?v=<jeton>` adresi TEK KULLANIMLIK — ilk istek 200,
oynatıcı manifesti yeniden isteyince 403. WARP çıkışıyla da 403 gelmesi ayırt edici
kanıt (IP engeli olsa WARP çözerdi). Proxy artık başarılı manifesti saklıyor ve
upstream 400+ dönünce onu 200 ile veriyor (`Proxy/Libs/manifest_cache.py`, 5 test).
Sunucu tarafı — TV güncellemesi gerekmez, canlı.

**TV 0.3.7 + saat 0.1.7 + motor.**
- **Sezon seçilemiyordu — kök neden çift odak hedefi:** sezon sayfasında
  `if (i == 0 || simdiki)` koşulu AYNI `FocusRequester`'ı iki kutucuğa bağlıyordu
  (kullanıcı 1. sezonda değilse ikisi de true). Odak hiçbirine net yerleşmiyor,
  D-pad panele ulaşmıyordu. Tek `sezonHedef` indeksi hesaplanıyor
  (`EpisodePicker.kt`). Hafızadaki `tv-focus-and-install-traps` kalıbının aynısı.
- Bölüm kutucukları küçüldü (180→150dp min, yükseklik 92→68dp).
- Saatte posterler kadran kenarında YAY üzerinde, döner çerçeveyle geziliyor,
  merkezdeki büyüyor. Halka üç kipli: GEZİNME / SARMA / SES. ⏯ ve ⬅ ayrı düğme
  (çift görevli tek düğme "önce oynatıyor sonra çıkıyor"a sebep oluyordu).
  Yay sabitleri cihazda kalibre EDİLMEDİ, `ponytail:` ile işaretli.
- **DiziMom posterleri yer tutucuydu:** site tembel yükleme kullanıyor, `src`te
  `data:image/svg+xml`, gerçek adres `data-src`te. Ortak `poster_attr`
  (`__dizi_common.py`) eklendi, DiziMom/DiziBox/Dizilla onu kullanıyor,
  6 test. Kanıt: 45 öğenin yer tutucu posteri 45 → 0.
  **Eklenti düzeltmesi hemen görünmez** — ağ geçidi `/get_main_page`'i 30 dk
  önbelleğe alıyor, `docker compose restart stream` şart.

**Dizilla katalog + saat Wi-Fi (`c1544ee`).**
- Dizilla'dan katalogda yalnız 5 kayıt vardı. Tür sayfaları için ayrı seçici
  `div.grid-cols-3 a` SIFIR düğüm buluyordu: site duyarlı sınıf adlarına geçmiş
  (`class="grid sm:grid-cols-3 …"`). Tek seçici `div.grid a` kaldı. Ölçüm:
  eklenti toplamı 5 → 125, birleşik akışta Dizilla 5 → 99.
  `/tum-bolumler` (5) ve `/dublaj-bolumler` (0) hâlâ zayıf ama bu eklenti hatası
  DEĞİL: sunucudan gelen HTML'de o kadar dizi bağlantısı var.
- **Saat Wi-Fi'yi kapatıyor:** Wear OS pil için radyoyu uyutuyor, ayar ekranı
  uyandırdığı için "girince bağlanıyor" görünüyor. Ev sunucusu LAN'da olduğundan
  Bluetooth vekili işe yaramaz. `WifiKoprusu.uyandir` katalog çekilmeden önce
  `requestNetwork(TRANSPORT_WIFI)` yapıyor, geleni `bindProcessToNetwork` ile
  sürece bağlıyor, geri çağrıyı BIRAKMIYOR (bırakılırsa radyo yeniden uyur).

## Tekrarlama — ölen yollar

- **Tuş tekrarı başına `seekTo` çağırma.** Hedefte biriktir, tek seek yap.
- **D-pad SOL/SAĞ'a çift basış atama.** Tek basışı bekletir.
- **İzleme kaydına liste indeksi yazma.** Sağlayıcı/liste değişince çöp olur.
- **`docker compose restart stream` ile Python değişikliği alınmaz** — stream kodu
  imajda, `up -d --build stream` şart.
- **Bash heredoc ile Kotlin/XML yazma.** Tırnak ve `\n` kaçışları bozuluyor; Write
  tool ile ayrı `.py` yaz, onu çalıştır.
- **`client_log` boş diye "TV hiç oynatmadı" sanma** — 0.3.3'ten önce günlük yalnız
  30 sn'yi geçen oturumlarda gidiyordu.
- **evaitecOTA "abort" için `remote.py`'a bakma** — kurulum oturumu sorunuydu.
- `gh release create` `--target main` olmadan release listeye düşmez.
- Git Bash'te `docker exec -w /usr/src/Stream` → `MSYS_NO_PATHCONV=1`.

## SIRADAKİ İŞ — cihazda dene

**Televizyonda** (evaitecOTA → kendini 0.1.12'ye güncelle, sonra NetMovies 0.3.5):
1. evaitecOTA yeni düzen: kartlar yan yana, kart içi düğme yok, odakta "OK · Aç".
2. Film aç: SAĞ'ı 3 sn tut → bırakınca tek seferde gitsin, fazladan atlama olmasın.
   SAĞ'a 3 kez hızlı bas → +30 sn. Sarma sürerken OK → orada dursun.
3. Reacher aç → `Devam et — S4B8 · …` yazmalı, "123. bölüm" DEĞİL.
4. Ajanda → Bu Hafta · Bu Ay · Geçmiş üç düğme; posterler küçük.
5. `curl -s localhost:3310/api/v1/client_log` → `açılış — …` satırı görünmeli.

**Saatte** — TAVUK-YUMURTA sürüyor. Bilekte NetMovies Mini 0.1.4 var, onda OTA yok;
evaitecOTA da bilekte kuramıyordu. Sıra:
1. evaitecOTA saatte kendini 0.1.10'a güncellesin (kendi paketi, aynı imza).
   Katalog canlı: GitHub Pages 200, `evaitec-ota-wear` vc 6.
2. Sonra NetMovies Mini 0.1.6'yı kurmayı dene. Düşerse ekran artık sebebini yazar.
0. **ADB'den ÖNCE DENE — telefondan saate gönderme hazır.** `evaitec-appkit`'te
   `transfer/ApkSender` + `ApkReceiverService` var; telefondaki evaitecOTA'da
   "Bağlı saate → Saate gönder" düğmesi (`ota-mobile`). APK Data Layer kanalından
   gidiyor, saatin internete çıkmasına da kablosuz hata ayıklamaya da gerek yok.
   Tavuk-yumurtayı bu kırar: telefona evaitecOTA kur → saate evaitecOTA 0.1.10
   gönder → sonra NetMovies Mini 0.1.8.
3. Son çare ADB: `bash scripts/saat-kur.sh` (netmovies reposunda) ya da
   `APK=<yol> bash scripts/saat-kur.sh`. **16 Eylül denemesi: saat bulunamadı** —
   bilekte "Wi-Fi üzerinden hata ayıklama" kapalı. Makine ağı `192.168.1.x`.

## Açık, doğrulanmamış

- **"Kaynak denemesi 4/9'dayken kendiliğinden Reacher açıldı."** Kuyruk uçları temiz
  (`/api/v1/remote/poll` boş, TTL 120 sn), oynatıcı açıkken uzak komut onay kartı
  gösteriyor (`MainActivity.kt:246`). Sessiz geçişin bilinen yolu YOK. 0.3.3'ten
  itibaren `client_log`'da `açılış — uzak komut / kullanıcı seçimi` satırı var.
- Telefon kumandasının `play` komutu hâlâ 0 tabanlı sıra gönderiyor (`remote.py`
  `build_command`) — aynı indeks varsayımı, çevrilmedi.
- HANDOFF'taki "kart aksiyonları" listesi (poster kartında D-pad ile gezilebilir
  ikon şeridi, favori/takip dışarı, uzun basmada 4'lü menü) duruyor.
