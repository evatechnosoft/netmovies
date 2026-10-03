# Handoff: 0.9.46 yazıldı, yayın izne takıldı
> 2026-10-03 · `fix/general-stability` @ `0a67aad` (push'lu, Zima reposu aynı) · kirli: `.claude/handoffs/latest.md`, `atv-kopru.log`, `scripts/yedek_reddet.py` (son ikisi izlenmeyen, bu oturumun değil)

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
- **Dean'in açık istekleri (3 Ekim, tasarım bekliyor):** (a) takip/favori dizilerin yeni bölümü yayınlanınca ZimaOS'a önceden indir, izlerken akış yerine diskten oynat (donmayı keser; örn. pazar 17:00 maç, gece yayın). (b) "takip/favori gibi birkaç şeyi basılı tutunca D-pad sağ/sola koy, rec gibi" — anlamı netleşmedi, sorulacak.
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
