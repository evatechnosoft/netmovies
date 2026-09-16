# DEVİR — 16 Eylül 2026, 17:47 · TV/saat sürümleri, evaitecOTA, motor düzeltmeleri

Üç repo değişti, üçü de push edildi, üçünde de 0 kirli dosya.

| Repo | Dal | HEAD |
|---|---|---|
| `D:\projects\netmovies` | `fix/general-stability` | `2bcfa7a` |
| `D:\projects\evaitec-appkit` | `main` | `bd74499` |
| `D:\projects\evaglass-releases` | `main` | katalog, tüm sürümler canlı |

## Yayındaki sürümler — hepsi kanıtlı, HİÇBİRİ cihazda denenmedi

| Uygulama | Sürüm | vc | Kanıt |
|---|---|---|---|
| NetMovies TV | 0.3.8 | 308 | OTA `v0.3.7-poc` · release 200 · katalog canlı |
| NetMovies Mini (saat) | 0.1.8 | 108 | OTA `v0.1.8-poc` · release 200 · katalog canlı |
| evaitecOTA TV | 0.1.12 | 13 | release 200 · katalog API'de vc 13 |
| evaitecOTA saat | 0.1.10 | 6 | release 200 · katalog API'de vc 6 |

## Bu oturumda çözülenler (kök nedenleriyle)

**Sarma (TV 0.3.2).** Her tuş tekrarında `exo.seekTo` çağrılıyordu; HLS'de seek'ler
üst üste biniyor, tuş bırakılınca kuyruk işlenmeye devam ediyordu. Ayrıca repeatable
uzun basışta `longFired` true olmuyordu, UP olayı tek basış sayılıp fazladan 10 sn
ekliyordu. Basışlar `seekTarget`'ta birikiyor, 350 ms sonra TEK seek. Kademeli hız
10/30/60 sn. SOL-SAĞ çift basış varsayılanı kaldırıldı (tek basışı 300 ms bekletiyordu).

**Bölüm kimliği (TV 0.3.3).** Kayda liste İNDEKSİ yazılıyordu, web ise `"S4 E8"`.
Sızıntılı listede 123. sıra kaydedilmiş, liste 32'ye düşünce "Devam et — 123. bölüm"
çıkmıştı. Artık `S4B8`, listede sezon+bölüm ile aranıyor (`Library.kt` `episodeRef` /
`episodeIndexOf`, test `EpisodeRefTest`).

**Ajanda (TV 0.3.4/0.3.5).** Aralık bugünden başlıyordu; geriye 7 gün açıldı
(`agenda.py` `_GECMIS_GUN`). Geçmiş bölüm `next_episode_to_air`ta YOK,
`last_episode_to_air`ta. Filmler `movie/upcoming`ten (yalnız gelecek) →
`discover/movie` + tarih aralığı. Hafta 24 → 60 satır. Sonra üç adım: Bu Hafta ·
Bu Ay · Geçmiş; poster 130 → 110dp.

**Ayarlar paneli (TV 0.3.6).** 11 kaynak satırı ana akıştaydı; ayrı alt sayfaya
taşındı, ana panelde seçili kaynak tek satır (`SettingsPanel`, `kaynakListesiAcik`).

**Film ortasında "kaynak bulunamadı" (proxy).** Dizilla'nın `l.php?v=<jeton>` adresi
TEK KULLANIMLIK: ilk istek 200, oynatıcı manifesti yeniden isteyince 403. **WARP
çıkışıyla da 403 gelmesi ayırt edici kanıt** — IP engeli olsa WARP çözerdi. Proxy
artık başarılı manifestin yeniden yazılmış hâlini saklıyor, upstream 400+ dönünce onu
200 ile veriyor (`Proxy/Libs/manifest_cache.py`, 5 test). Normal akışta önbelleğe
bakılmaz, canlı yayın etkilenmez. **Sunucu tarafı — TV güncellemesi gerekmez.**

**Sezon seçilemiyordu (TV 0.3.7).** Kök neden çift odak hedefi: sezon sayfasında
`if (i == 0 || simdiki)` koşulu AYNI `FocusRequester`'ı iki kutucuğa bağlıyordu
(kullanıcı 1. sezonda değilse ikisi de true). Odak hiçbirine net yerleşmiyor, D-pad
panele ulaşmıyordu. Tek `sezonHedef` indeksi (`EpisodePicker.kt`). Hafızadaki
`tv-focus-and-install-traps` kalıbının aynısı. Kutucuklar da küçüldü (180→150dp min,
yükseklik 92→68dp).

**Saat arayüzü (0.1.5-0.1.7).** Kendi kendini güncelleme (`Guncelleme.kt`,
PackageInstaller), listeler `ScalingLazyColumn`, sonra posterler kadran kenarında YAY
üzerinde (döner çerçeveyle geziliyor, merkezdeki büyüyor), halka üç kipli
GEZİNME/SARMA/SES, ⏯ ve ⬅ ayrı düğme (çift görevli tek düğme "önce oynatıyor sonra
çıkıyor"a sebep oluyordu), sesli arama sonucunda 📺 işareti.

**Saat Wi-Fi (0.1.8).** Wear OS pil için Wi-Fi radyosunu uyutuyor; ayar ekranı
uyandırdığı için "girince bağlanıyor" görünüyor. Ev sunucusu LAN'da olduğundan
Bluetooth vekili işe yaramaz. `WifiKoprusu.uyandir` katalog çekilmeden önce
`requestNetwork(TRANSPORT_WIFI)` yapıp `bindProcessToNetwork` ile sürece bağlıyor.
Geri çağrı BIRAKILMAZ — bırakılırsa radyo yeniden uyur.

**evaitecOTA saat (0.1.9/0.1.10).** (a) Kurulum sonucu yutuluyordu; alıcı yalnız
`STATUS_PENDING_USER_ACTION` işliyordu → `InstallOutcome` + `onResume` banner.
(b) Yarım oturum sızıntısı, `abandonSession` yoktu; açık oturum yeni kurulumu iptal
ettiriyor. (c) `setSize`/`setAppPackageName`/`setInstallReason` eksikti, receiver'daki
`startActivity` korumasızdı. (d) Katalogdan önce `requestNetwork` (saat "bağlanamadı"
diyordu, katalog ayaktaydı).

**evaitecOTA TV (0.1.12).** Tek sütun liste → ızgara (kart 250×168dp). Kart içi dolu
accent düğmeler kaldırıldı; kart zaten OK'i işliyor, eylem adı odakta tek satır.
Accent yalnız iş bekleyen kartta. Üst bar eylemleri saydam çip.

**Posterler (motor).** DiziMom'da 45 öğenin hepsi yer tutucu geliyordu: site tembel
yükleme kullanıyor (`<img data-lazyloaded="1" src="data:image/svg+xml;…" data-src="…">`).
Ortak `poster_attr` (`__dizi_common.py`), DiziMom/DiziBox/Dizilla kullanıyor, 6 test.
Kanıt: yer tutucu 45 → 0.

**Dizilla katalogu.** Katalogda yalnız 5 kayıt vardı. Tür sayfaları için ayrı seçici
`div.grid-cols-3 a` SIFIR düğüm buluyordu: site duyarlı sınıf adlarına geçmiş
(`class="grid sm:grid-cols-3 …"`). Tek seçici `div.grid a`. Kanıt: eklenti toplamı
5 → 125, birleşik akışta Dizilla 5 → 99, toplam 503 öğe.

**Zincirleme bölüm atlaması (TV 0.3.8).** Dizi 1. bölümden açıldı, saniyeler içinde
17. bölüme ilerledi. Oynayan bölüm DEĞİLDİ: sağlayıcı kaldırılmış bölümün yerine
10-20 sn'lik klip koyuyor ("İÇERİK KALDIRILDI · DMCA", "DUR! GİTME!") — bu metinler
kod tabanında YOK, ekranlar sağlayıcının videosu. Klip anında `STATE_ENDED`'e
ulaşıyor, `akisBitti` koşulsuz kuruluyor, geri sayım sonraki bölüme geçiriyordu.
İkinci yol: "bitmek üzere" penceresi 90 sn, 90 sn'den kısa klip ilk saniyeden
itibaren içindeydi. `MIN_GECERLI_SURE_MS = 90_000` eşiği + `akisGecersiz` bayrağı;
önce başka kaynak, kalmazsa "Bu bölüm sağlayıcıda yok" ve otomatik geçiş YOK.
Canlı yayın muaf.

## Tekrarlama — ölen yollar

- **Tuş tekrarı başına `seekTo` çağırma.** Hedefte biriktir, tek seek yap.
- **D-pad SOL/SAĞ'a çift basış atama.** Tek basışı bekletir.
- **İzleme kaydına liste indeksi yazma.** Sağlayıcı/liste değişince çöp olur.
- **Poster için `first_attr(..., "src")` yazma** — `poster_attr` kullan.
- **CSS seçicide duyarlı sınıf adına bel bağlama** (`grid-cols-3` → `sm:grid-cols-3`).
- **Eklenti düzeltmesi hemen görünmez**: ağ geçidi `/get_main_page`'i 30 dk,
  `/aggregate_new`'i 10 dk önbellekliyor → `docker compose restart stream`.
- **`docker compose restart stream` Python değişikliğini ALMAZ** — kod imajda,
  `up -d --build stream` şart. (restart yalnız önbelleği temizler.)
- **Bash heredoc ile Kotlin/XML yazma** — tırnak ve `\n` kaçışları bozuluyor; Write
  tool ile `.py` yaz, onu çalıştır. (Bu oturumda iki kez ısırdı.)
- **Saate kurulum için ADB'den başlama** — telefondan gönderme hazır (aşağıda).
- **evaitecOTA "abort" için `remote.py`'a bakma** — kurulum oturumu sorunuydu.
- `gh release create` `--target main` olmadan release listeye düşmez.
- Git Bash'te `docker exec -w /usr/src/Stream` → `MSYS_NO_PATHCONV=1`.

## TEK SIRADAKİ İŞ — saate 0.1.8'i koy, sonra cihazda dene

**ADB'den ÖNCE:** telefondaki evaitecOTA'da "Bağlı saate → Saate gönder" var
(`appkit/transfer/ApkSender`, `ota-mobile` strings `action_send_to_watch`). APK veri
katmanı kanalından gidiyor; saatin internete çıkmasına da kablosuz hata ayıklamaya da
gerek yok. Sıra: telefona evaitecOTA kur → saate evaitecOTA 0.1.10 gönder → sonra
NetMovies Mini 0.1.8. Son çare ADB: `bash scripts/saat-kur.sh` (16 Eylül denemesi:
saat bulunamadı, bilekte kablosuz hata ayıklama kapalı; makine ağı `192.168.1.x`).

**Televizyonda** (evaitecOTA kendini 0.1.12'ye güncellesin, sonra NetMovies 0.3.7):
1. Film aç, SAĞ'ı 3 sn tut → bırakınca tek seferde gitsin, fazladan atlama olmasın.
   SAĞ'a 3 kez hızlı bas → +30 sn. Sarma sürerken OK → orada dursun.
2. Reacher aç → `Devam et — S4B8 · …` yazmalı. Bölüm seçicide SEZON seçilebilmeli.
3. Ayarlar → kaynak tek satır, basınca alt sayfa.
4. Ajanda → üç adım, posterler küçük.
5. Film ortasında kesilme tekrarlarsa: `curl -s localhost:3310/api/v1/client_log`
   → `açılış — …` ve kaynak satırları sebebi söyler.

## Açık, doğrulanmamış

- Saatteki yay eğrisi ve düğme yerleşimi cihazda görülmedi; sabitler masabaşı,
  `ponytail:` ile işaretli.
- Dizilla `/tum-bolumler` (5) ve `/dublaj-bolumler` (0) hâlâ zayıf — eklenti hatası
  DEĞİL, sunucudan gelen HTML'de o kadar dizi bağlantısı var. Kartlar bölüm adresi
  taşıyor olabilir (`card-may-be-episode-page` hafızası).
- "Kaynak denemesi 4/9'dayken kendiliğinden Reacher açıldı" — kuyruk uçları temiz,
  sessiz geçişin bilinen yolu yok. 0.3.3'ten beri `client_log`'da açılış sebebi var.
- Telefon kumandasının `play` komutu hâlâ 0 tabanlı sıra gönderiyor (`remote.py`).
- HANDOFF'taki "kart aksiyonları" listesi (poster kartında D-pad ile gezilebilir ikon
  şeridi, favori/takip dışarı, uzun basmada 4'lü menü) duruyor.
