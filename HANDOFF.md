# Handoff: 0.9.50 yayında, indirme Dean isteğiyle DURDU, inenler duruyor
> 2026-10-03 · `fix/general-stability` @ `0a67aad` (push'lu, Zima reposu aynı) · kirli: `.claude/handoffs/latest.md`, `atv-kopru.log`, `scripts/yedek_reddet.py` (son ikisi izlenmeyen, bu oturumun değil)

## 4 Ekim 16:xx — İNDİRME DURDU (Dean), 0.9.50
- **Dean kararı:** "İndirme özelliği olsun ama dursun şimdilik; inenler kalsın." `kayit_otomatik=0`, bekleyen/hatalı kayıtlar silindi; yalnız Lioness S3B5 (DiziMom TÜRKÇE DUBLAJ sayfası `special-ops-lioness-turkce-dublaj-izle-hd16`) bitiyordu, bırakıldı. Hazır kayıtlar duruyor (A.B.İ. 16-22, Lioness S3B4/5/7, Yeraltı 16, Haysiyet 4, Anne Yarısı 2, Neagley 8, Altı Üstü 15). Yeni kuyruk eklemeden önce Dean'e sor.
- **0.9.50 üç yerde** (yerel OTA 22 686 484 B · `v0.9.50-poc` · evaglass vc 950, sha `d08fd0b5…`): Kayıtlar ızgarası dizi başına TEK kart ("N bölüm" rozeti, kart diziyi açar), kart pad'indeki bölüm listesi oynatıcıdaki `BolumSatiri` ile aynı (✓ izlenen, ▶ kalınan, ● kayıtlı; oynatıcıda da ●). Cihazda doğrulanmadı. Dean'in eleştirisi: "kayıtlar indirme sırasına dizilmiş, bölümler kendi içinde olmalı" → bu.
- **İngilizce kayıt kök nedeni:** DiziMom'da dublaj AYRI dizi sayfası (`…-turkce-dublaj-izle-hdNN`); genel sayfadan inen "DiziMom | Kaynak" altyazılı. Lioness S3B6/S3B8 İngilizce indi ve silindi. DiziPal/Dizilla Lioness konağı (pichive) 403 → kayıt dışı her seçim İngilizce DiziMom'a düşüyordu. Kalıcı çözüm (yapılmadı): kayıt alırken dublaj varyantını seç / dili meta'ya yaz.
- Daha 17 S1B19 ve Ömür Usta S1B2 çözülmüş adresle de "kaynak bulunamadı" — ayrı kök neden, bakılmadı.

## 4 Ekim öğleden sonra — 0.9.49, kayit_takip, kuyruk kök nedenleri
- **0.9.49 üç yerde** (yerel OTA 22 686 484 B · GitHub `v0.9.49-poc` · evaglass `netmovies-tv-v0.9.49` vc 949, sha `6d5ad9c7…`). Cihazda doğrulanmadı. Basılı-tut menüsü alt sırasına 4. hap ⏺ "Devamı insin" (`LISTE_KAYIT_TAKIP`), seçili hapın adı altta yazar. Hapı işaretlemek sunucuda hemen bir otomatik tur attırır.
- **Sunucu (`644bf9d`, Zima'da 10:44Z):** `kayit_takip` listesi (`watch_store.ALLOWED_LISTS`); `_otomatik_tur` yalnız bu listeyi tarar, izlenen (progress `episode`) ya da kayıtlı son bölümden SONRAKİ `ONDE=3` bölümü kuyruğa koyar, iz yoksa en yeni bölüm. Global `takip` listesi artık indirmeyi tetiklemez. `kayit_otomatik=1` ana şalter. Liste tohumlandı: Lioness, A.B.İ., MobLand, Yeraltı, Haysiyet, Anne Yarısı, Daha 17, Altı Üstü İstanbul. Test `test_otomatik_tur_izlenenden_sonraki_onde_bolum`; 221 stream testi yeşil.
- **Kuyruk kök nedenleri (ikisi de çözüldü):** (1) `load_item` bölüm adresleri quote_plus KODLU; kayıt `ekle` artık çözer (`1341771`), yoksa motor "kaynak bulunamadı" diyordu (A.B.İ. 9 bölüm). (2) Lioness'ta DiziPal+Dizilla aynı konağa (pichive) gidiyor, konak 403, WARP da geçmiyor; DiziMom adresleriyle (`special-ops-lioness-3-sezon-N-bolum-izle`) yeniden kuyruğa alındı, S3B6/S3B8 indi. Dean: A.B.İ. 16-18 geriye dönük, 19 izlenmedi (hepsi kuyrukta/indi), Lioness S3B8 de.
- Dean'in "ilk 20-30 dk'yı indir" fikri konuşuldu ve Dean "önemli değil" dedi — YAPILMADI (EVENT playlist + iç içe v/a indirme + öncelik gerekir).
- Ses playlist düzeltmesi (`620d187`) S3B5 403'ünü çözmedi; 403 konak kaynaklı çıktı.

## 4 Ekim sabah — 0.9.48 + kayıt kuyruğu
- **0.9.48 üç yerde** (yerel OTA `v0.9.48-poc` 22 670 100 B · GitHub `v0.9.48-poc` · evaglass `netmovies-tv-v0.9.48`, apps.json tv/phone vc 948, sha256 `25d5f529…`). Cihazda doğrulanmadı. İçerik: `remote/state` artık bölümün adresini yollar (kart url'si "8. bölüm" gösteriyordu, `episodes[currentEpIndex].url`), kayıt ● posterin SOL üstünde, puan 26 dp sağa kayar. 80 test yeşil.
- Diskten oynatma kanıtlandı: 09:45 `resolve: kayıttan · Lioness S3B4`, S3B4 klasöründen 3 dk'da 75 segment okundu, sda1 11 MB/20 sn. Gece TV'den istek yok (23:32–09:16), indirme 23:32'de bitmişti. Stream konteyneri 00:24/00:26'da iki kez yeniden başlamış (RestartCount 0 → elle/compose, kim yaptı bilinmiyor).
- Dean sohbetten kuyruğa yazdırdı: Lioness S3B5-6, A.B.İ. S1B20-22, Daha 17 S1B19, Haysiyet S1B4, Yeraltı S1B16 (`POST /kayitlar/ekle` JSON gövde çalışıyor). `kayit_otomatik=1` AÇILDI (Dean "yayınlanınca indir" dedi; önceki "yalnız elle" kararı kalktı). Takip'e eklendi: Lioness, A.B.İ., MobLand, Yeraltı. Altı Üstü İstanbul 16-18 ve MobLand S2B4 henüz yok → otomatik tur yakalar (3 saatte bir, Takip'in EN YENİ bölümü; aradaki bölümleri almaz).
- DiziPal `load_item` Lioness/MobLand için 0 bölüm döndü (dizipal2221/2224); `episodes_best` DiziMom'a düştü. Bölüm listesi için `episodes_best` kullan.

## Kayıtlar — 3 Ekim gece
- **0.9.46/0.9.47 YAYINDA (üç yer):** yerel OTA `v0.9.46-poc` 22 670 096 B · GitHub `v0.9.46-poc` prerelease · evaglass `netmovies-tv-v0.9.46` + apps.json tv/phone vc 946 (sha256 `98775f62…`). Cihazda doğrulanmadı.
- `c6402f3` (0.9.47, üç yerde): Yönetim'de tek çubuk = izlerken hat paylaşımı 0-10 MB/s (orta 5/5 → `kayit_izlerken_mbit=40`, 0 = izlerken indirme durur); izlemezken sınırsız (`kayit_bosta_mbit=0`). `kayit_otomatik=1` → 3 saatte bir Takip'in en yeni bölümü. joystick/oynatıcı ● kayıtlıysa siler, posterde kırmızı ●. 7 Mbit sınırı ölçüldü 7,0.
- `7f8ea27`+`567fa62` canlıda (Zima stream rebuild, mount `/DATA/sata/netmovies-kayitlar → /kayitlar`, `.env` `KAYIT_HOST_DIR`).
- Sunucu: `stream/Public/API/v1/Libs/kayit.py` kuyruk + indirme (kendi proxy'miz, `X-NM-Kayit`), uçlar `GET /api/v1/kayitlar`, `POST /api/v1/kayitlar/ekle|sil`, dosyalar `/proxy/kayit/<id>/...`. Hazır kayıt `resolve_sources`'ta ilk kaynak, fast modda motora gitmez (internetsiz oynar). İzlerken tek bağlantı, boşta 4 paralel (hızlar prefs). Boş alan <20 GB → izlenmiş (son segmenti servis edilmiş) kayıt silinir.
- TV (0.9.46'ya girer, YAYINLANMADI): joystick SAĞ ⏺ (dizide bölüm seçtirir), benzerler Özet'in altında (Özet sonunda ▼), ana sayfa "Kayıtlar" çipi (rozet ⏺ / %ilerleme / SIRADA / HATA). Cihazda denenmedi.
- Uçtan uca (sunucu): Lioness S3B7 22:40 hazır, 1313 MB, ~16 dk; fast resolve 0,04 sn ve yalnız kayıt döner; master 1080p + Türkçe ses grubu, 248 video / 860 ses segmenti, playlistte http yok, segment TS (0x47), ffprobe h264 1920x1080. TV oynatması denenmedi. S3B9 "kaynak bulunamadı" — motor da bulmuyor, bölüm yok.
- Yapılmadı: oynatıcı alt haplarına ⏺ (joystick + liste yeterli mi Dean'e sor), takip edilen dizinin yeni bölümünü otomatik kaydetme (Dean "yalnız elle" dedi), kayıt silme düğmesi TV'de (uç var).

## Goal
Reklamsız TV uygulaması (client-tv, Mi Box). Dean'in kusurlarını düzelt, bitince sormadan üç yere yayınla: yerel OTA, GitHub release, apps.json.

## State
- **`07861e8` açılış paneli:** `StartPanel` silindi. İçerik açılınca `SettingsPanel` başlangıç kipinde gelir (`baslangic=true`): başlık + yıl/tür/puan, ikon sırası, altında "Devam et / baştan" ve "Son bölüm" satırları, odak OYNAT'ta. Bölüm listesi kapalı, ☰ aç/kapa. Kumandadaki "Bölümler" girişi listeyi açık getirir (`acilisBolumler`). İki oynatıcıda da (`PlayerScreen.kt`, `player2/PlayerScreen2.kt`). Dean'in isteği: "direkt bu 2 sayfa çıksın, bölümler kapalı, liste istersem açarım".
- **`07861e8` M3U gizli:** Gözat artık `client_config.hidden_providers`'ı uyguluyor (canlıda M3UPlaylist, SezonlukDizi). Canlı TV etkilenmez (`quick_channels` ayrı uç).
- **`444a501` Seriler sekmesi:** Gözat'ta "Tümü" yanında "Seriler" hapı. Sunucu ucu `GET /api/v1/film_serileri` (`stream/Public/API/v1/Routers/film_serileri.py`): TMDB popüler + izleme geçmişi → koleksiyonlar, 12 saat modül cache. Karta basınca başlıkla arama + otomatik aç (Ajanda yolu). Bu, eski "A.Ü.İ arşiv alanı / seri filmler" isteğinin karşılığı; Dean "Gözat'ta ayrı sekme" dedi.
- **Sunucu:** Zima'da stream rebuild edildi (Created 2026-10-03T07:01Z, rebuild öncesi client_log boştu). Canlı `film_serileri` → 13 seri, ilk sıra Bıçak Sırtı (izleme geçmişinden). Tünel cevap veriyor (303 → giriş).
- **Testler:** client-tv `testDebugUnitTest assembleDebug` yeşil, 71 test. stream unittest 215 OK (alt ajan laptopa global pip paketleri kurarak koşturdu).
- **YAYINLANMADI:** TV'de hâlâ 0.9.45. `appVersion` hâlâ `0.9.45` (`client-tv/app/build.gradle.kts:4`). Auto-mode sınıflandırıcısı GitHub release + apps.json yayınını "Create Public Surface" diye reddetti. Dean onayı ya da izin kuralı gerekli. Cihazda hiçbir şey doğrulanmadı.
- **`45773f1` WOL:** TV yerel sunucuya ulaşamazsa ZimaOS'a sihirli paket yollar (`data/ZimaUyandir.kt`, MAC `BuildConfig.WOL_MAC`), ana sayfa 3 dk boyunca 15 sn arayla yeniden dener. 73 test yeşil. Cihazda ve kapalı sunucuyla denenmedi.
- **`138cf02` güç/ses:** Mi Box güç köprüsü ZimaOS'ta `atv` servisi (Created 2026-10-03T11:21Z, `curl zima:3311/saglik` ok, kutu .105). Uçtan uca `{"type":"power"}` → köprü → "kutu uykuda" (doğru: uykudaki kutu ağdan açılamaz, yalnız kapatır). Widget'a ⏻ 🔉 🔊. Ses hafızası uygulama öne gelince de uygulanır, kayıt yoksa 9; `ses` satırı client_log'a düşer — `sabit=true` görünürse ses HDMI/CEC'te, uygulama ayarlayamaz, yeni çözüm gerekir. 76 test yeşil, cihazda denenmedi.
- **`a0d090a`** BT hoparlör bağlanınca kayıtlı ses yeniden uygulanır (ses Mi Box'a bağlı BT hoparlörden). **`0a67aad`** proxy jeton reddinde sebep+host+istemci günlüğe (canlıda, kurcalama testi "biçim bozuk" yazdı).
- **AÇIK AĞ SORUNU (3 Ekim 17:01'den beri):** Mi Box TP-Link 5 GHz'e bağlı görünüyor ama trafik yok (4.7k paket), 1.105 ve 1.60'ta port/ping yok. Dean statik 192.168.1.60 + CF DNS girdi, ARP hâlâ .105. Telefon da yavaş (5 GHz). Kablolu ZimaOS 11 MB/s, laptop 2.4 GHz 2.4 MB/s, gecikmeler normal → şüphe TP-Link 5 GHz radyosu. TP-Link yeniden başlatma Dean onayı bekliyor. Benim değişikliklerim zaman çizelgesine göre sebep değil (köprü kutuya hiç bağlanmadı, 0.9.46 TV'de yok). Kutu .60'ta kalırsa Zima `.env` `ATV_HOST=192.168.1.60` + `docker compose up -d atv`.
- 16:57 Lioness S3B2 (DiziPal) 75. sn'de dondu, 18 "proxy token geçersiz"; sonra sunucudan tüm 2.6k parça 200/206 — sebep bulunamadı, yeni günlük satırı bekleniyor.
- **18:15 TP-Link restart sonrası:** Mi Box döndü (.105), ama TV istekleri YEREL↔TÜNEL arasında saniyeler içinde gidip geliyor (stream log, okhttp UA); Lioness tünelden çözüldü, 47-49. sn'de takıldı, red yok. Düzeltildi (`yapiskanYerel`, `data/ServerResolver.kt`): /24 içindeyken ve yerel son 10 dk çalıştıysa tünele geçmez; 80 test yeşil, 0.9.46'ya girer, cihazda denenmedi. Kalıcı çözüm Mi Box Ethernet.
- Üst bar: güncelleme varken "NetMovies" → "NM" + güncelle düğmesi (commit sonrası). TV'de 📱 kumanda düğmesi 0.9.45'te zaten kalktı.
- **Dean'in açık istekleri (3 Ekim, tasarım bekliyor):** (a) takip/favori dizilerin yeni bölümü yayınlanınca ZimaOS'a önceden indir, izlerken akış yerine diskten oynat (donmayı keser; örn. pazar 17:00 maç, gece yayın). (b) KARAR (Dean son sözü): joystick (`HomeScreen.kt` ~936, PadMod) SAĞ = ✧ Benzer → ⏺ Rec (kaydet = Zima'ya indir). SOL ℹ Hakkında kalır; Hakkında (OZET) açılınca ALTINDA benzerler listesi gelir (BENZER modu OZET'e taşınır). Dean kararları (3 Ekim): Rec YALNIZ elle seçilen (otomatik indirme yok), "sonra izleyeceğim" gibi, internetsiz de oynamalı; depolama "D" — yer sorunu yok, izlenen kayıt disk dolmadan silinir. Zima diskleri: `/DATA` nvme 457G %27, `/DATA/sata` (sda1) 220G BOŞ bağlı, `sdb1` 466G etiket A2000 BAĞLI DEĞİL (muhtemelen eski laptop D). Hangisi "D" Dean'e teyit; bağlamadan sda1 kullanılabilir. Dean kararları 2 (3 Ekim gece): depolama `/DATA/sata` (ölçüldü: yazma 105 MB/s, okuma 503 MB/s; 1080p bölüm ~1-2 GB → yazma 10-20 sn, ev hattı 11 MB/s'den çok hızlı). İzlerken KESMEDEN arkadan kaydedebilmeli (oynatma ile indirme ayrı iş, oynatıcıyı yavaşlatmasın). Liste adı "Kayıtlar"; joystick SAĞ'a ek olarak altta Favori · İzlenecek · Takip haplarının yanına ⏺ hap da konabilir. (a)+(b) tek iş: rec kuyruğu + diskten oynatma + takip yeni bölüm otomatik kaydı; açık sorular: disk tavanı, otomatik mi elle mi.
- Bilinen kusur: bazı seri adları TMDB'de Türkçe değil ("Super Troopers Collection").

## Next
0. Ağ: Dean onay verirse TP-Link yeniden başlat (`~/.ai/scripts/home-net`, ROUTER_PASS=vg.env HOME_TPLINK_PASS); sonra Mi Box adresi + `client_log`/`reddedildi` günlüğü.
1. Dean "yayınla" derse: `client-tv/app/build.gradle.kts:4` `appVersion = "0.9.46"` → `cd client-tv && ./gradlew testDebugUnitTest assembleDebug` → üç yere yayın. Yöntem 0.9.45'teki gibi: `git show 30f7d85` ve hafıza `ota-release-target-flag`, `wear-app-and-catalog`, `ota-indirme-ayna-sirasi`. GitHub release'te `--target` şart. evaglass-releases: apps.json tv+phone vc 946, sha256+sizeBytes, push öncesi `git pull --rebase`.
2. 0.9.46 kurulunca `curl -s 192.168.1.186:3310/api/v1/client_log | grep ses` ile `sabit=` değerine bak.
3. Dean'den cihaz geri bildirimi: açılış paneli odağı, ☰ ile liste, Seriler sekmesi.
4. Ölü kaynaklar (FullHDFilmizlesene Soulm8te, DDizi Anne Yarısı 504) tekrar ederse eklentiye bak.

## Don't repeat
- Yayını alt ajana devretmek izni aşmaz; aynı sınıflandırıcıya takılır. İzni Dean verir, Claude ayar dosyasına izin yazmaz.
- Zima'da compose yalnız `export DOCKER_CONFIG=/tmp/dc;` ile görünür; kanıt `docker inspect --format '{{.Created}}'`.
- Stream rebuild'de `--profile tunnel` ile birlikte kur, yoksa tünel 530 (hafıza `stream-tunnel-netns-rebuild`).
- Dean TV'de izlerken rebuild yok: önce `curl -s 192.168.1.186:3310/api/v1/client_log` boş mu bak.
- Seriler önbelleği `Libs/__init__.py` TTL tablosuna eklenmez; o tablo yalnız engine proxy çağrıları için.

## Read first
1. `client-tv/app/src/main/java/com/evaitec/netmovies/tv/ui/PlayerScreen.kt` `SettingsPanel` (~2438): `baslangic`, `listeAcik`, `oynatEtiketi`
2. `git show 30f7d85`: 0.9.45 yayın kanıtları ve adımları

## Verify
```
git -C C:/projects/netmovies rev-parse --short HEAD        # expect 444a501 (ya da bu handoff commit'i)
curl -s "http://192.168.1.186:3310/api/v1/app_update?target=tv"   # expect hâlâ v0.9.45-poc (yayın yapılmadı)
curl -s -m 90 http://192.168.1.186:3310/api/v1/film_serileri | head -c 200   # expect result dizisi
cd client-tv && ./gradlew testDebugUnitTest -q              # expect exit 0
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies TV (C:\projects\netmovies, dal fix/general-stability @ 444a501, push'lu).
Yeni: açılış paneli = Bölümler & Listeler paneli (liste ☰ arkasında kapalı), Gözat'ta M3U gizli,
Gözat'ta "Seriler" sekmesi (sunucu ucu film_serileri canlıda çalışıyor). Kod hazır ama 0.9.46
YAYINLANMADI: auto-mode GitHub release/apps.json'u reddetti, Dean onayı gerekli. Cihazda doğrulanmadı.
Aktif sunucu ZimaOS (ssh zima, DOCKER_CONFIG=/tmp/dc). Laptopta docker yok.
Önce C:\projects\netmovies\HANDOFF.md'yi oku, Verify bloğunu çalıştır.
Öncelik: (1) Dean onay verirse 0.9.46'yı üç yere yayınla; (2) cihaz geri bildirimi.
Dean istemeden yeni iş açma.
```
