# NetMovies Sağlamlaştırma ve Modernizasyon Planı

**Tarih:** 2 Eylül 2026  
**Kapsam:** Web/PWA, Kotlin TV istemcisi, sağlayıcı API sözleşmesi, poster hattı,
oynatıcı, güvenlik, test ve işletim.

## Yönetici özeti

NetMovies'in temel ürün fikri doğru: tek katalog, tek arama, tek detay sayfası ve
tek oynatıcı üzerinden farklı sağlayıcıları toplamak. Sorun görsel tasarımdan önce
entegrasyon sınırlarında. Web arayüzü, istemci API'si ve Kotlin uygulaması aynı
sağlayıcı seçimini kullanmıyor; bazı posterler proxy'den, bazıları doğrudan uzak
kaynaktan yükleniyor; dizi detay isteğinin parametresi sunucu sözleşmesiyle
uyuşmuyor. Otomatik test bulunmadığı için dokümantasyonda çözülmüş görünen hatalar
yeniden ortaya çıkmış.

Önerilen hedef, mevcut sistemi yeniden yazmak değil; önce tek ve sürümlü bir
`ProviderGateway` sözleşmesi kurmak, ardından web ve TV deneyimini bu sözleşmenin
üstünde modernleştirmektir.

## Doğrulanmış mevcut durum

| Öncelik | Bulgular | Etki |
|---|---|---|
| P0 | `NetMoviesApi.loadItem()` `url` gönderiyor; engine `encoded_url` bekliyor. Çalışan container logunda aynı istek `410` dönüyor. | Dizi/bölüm çözme akışı kırılıyor. |
| P0 | Kotlin istemcinin `/api/v1/*` çağrıları yalnız `DEFAULT_PROVIDER_URL` üzerinden yerel engine'e gidiyor. Admin'de seçilen Watchbuddy yalnız server-rendered web rotalarında kullanılıyor. | Web ve TV farklı katalog/eklenti görüyor. |
| P0 | `netmovies-engine` ve `netmovies-stream` `unhealthy`; yerel port bağlantıyı reddediyor, canlı tünel `502` dönüyor. | Uygulama bütünüyle erişilemez. |
| P0 | Repository'de gerçek test dosyası yok. Kotlin görevi `testDebugUnitTest NO-SOURCE`, Python yalnız compile kontrolünden geçiyor. | Regresyonlar yakalanmıyor. |
| P1 | Jinja sayfaları `poster()` proxy helper kullanırken `content-browser.js` ve bazı arama/önizleme yolları uzak poster URL'sini doğrudan kullanıyor. | Hotlink koruması ve DNS engeli nedeniyle posterler sayfaya göre açılıp kapanıyor. |
| P1 | Player açılırken başlığa göre altı sağlayıcı sırayla aranıyor; hatalar boş `catch` ile yutuluyor. | Yavaş açılış, gereksiz trafik ve teşhis edilemeyen hata. |
| P1 | Public tünel profili bulunmasına rağmen Compose `AUTH_USER`/`AUTH_PASS` değerlerini boş geçiriyor. Remote provider URL'si sunucu tarafından allowlist olmadan çağrılıyor. | Yetkisiz erişim ve SSRF yüzeyi. |
| P1 | Android manifest genel `usesCleartextTraffic=true`, `allowBackup=true`; release build'de BASIC ağ logu açık. | Gereğinden geniş ağ ve veri sızıntısı yüzeyi. |
| P1 | Reklamsız ürün tanımına rağmen taban HTML'de `monetag` meta kimliği var. | Ürün vaadi ve gizlilik yaklaşımıyla çelişiyor. |
| P2 | Python bağımlılıkları çoğunlukla sürümsüz; Media3 `1.4.1` kullanılıyor, güncel kararlı sürüm `1.11.0`. | Tekrarlanabilir build zayıf; iki yıllık oynatıcı düzeltmeleri kaçırılıyor. |
| P2 | PWA service worker no-op; statik varlık/poster stratejisi ve offline shell yok. | Kurulabilir görünse de gerçek PWA dayanıklılığı sağlamıyor. |

Watchbuddy'nin `/api/v1/schema` ve `/api/v1/get_all_plugins` uçları inceleme anında
HTTP 200 döndü. Bu yalnız erişilebilirliği doğrular; NetMovies içindeki provider
seçimi ve uçtan uca oynatma zincirinin doğru olduğunu kanıtlamaz.

## Hedef mimari

```text
Web / Android TV
       |
       v
NetMovies API v2
  - ProviderGateway
  - Catalog / Meta / Streams / Subtitles
  - PosterService
  - Health + capability registry
       |
       +--> Local KekikStream adapter
       +--> Watchbuddy-compatible adapter
       +--> Personal M3U adapter
```

Her provider aşağıdaki yetenekleri açıkça ilan etmeli:

- `catalog`, `search`, `meta`, `streams`, `subtitles`, `live`
- desteklenen medya tipleri ve stabil provider kimliği
- timeout, son başarı zamanı, hata sınıfı ve geçici devre-dışı süresi
- poster/proxy gereksinimi ve gerekli header'lar

Bu yaklaşım Stremio'nun manifest içinde kaynak ve medya tiplerini ilan eden,
`catalog -> meta -> streams -> subtitles` ayrımına benzer; kodun provider adına
göre dallanmasını engeller. Kaynak: [Stremio Addon SDK manifest](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md)
ve [resource modeli](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/README.md).

## Uygulama fazları

### Faz 1 — P0 stabilizasyon ve regresyon ağı

1. Mevcut değişiklikleri koruyup `fix/general-stability` dalında küçük commit'lere
   ayır.
2. `load_item` parametresini `encoded_url` olarak düzelt; URL encode/decode için
   tek canonical fonksiyon oluştur.
3. Web ve Kotlin API çağrılarını aynı `ProviderGateway` üzerinden geçir; admin
   provider seçimini tüm istemcilere uygula.
4. Contract testleri ekle: schema, plugin listesi, katalog, item, episode, link ve
   hata yanıtları.
5. Container başlangıç/health davranışını düzelt; `stream` engine'i yalnız healthy
   olduktan sonra kabul etsin. Local ve tunnel smoke test ekle.

**Kapı:** iki container healthy; local `/health` 200; tunnel 200; Kotlin film ve
dizi fixture'ları oynatma ekranına ulaşır; provider seçimi web/TV'de aynıdır.

### Faz 2 — Poster ve katalog hattı

1. Tek `PosterService` ve tek istemci URL helper'ı kullan; doğrudan uzak `<img>`
   üretimini kaldır.
2. Kaynak posteri -> güvenli proxy/cache -> metadata fallback -> yerel placeholder
   sırası uygula. Negatif sonuçları kısa süre cache'le.
3. Poster kartlarına sabit en-boy oranı, `width`/`height`, görünür ilk sıra için
   yüksek öncelik ve alt sıralar için lazy-load ekle.
4. Poster proxy için DNS-rebinding koruması, içerik boyutu sınırı, MIME doğrulama
   ve gözlemlenebilir hata kodları ekle.

Responsive boyut ve ilk görünür görsel önceliği ağ tüketimini ve geç görünmeyi
azaltır; [web.dev responsive image rehberi](https://web.dev/articles/preload-responsive-images)
bunu doğrudan önerir.

**Kapı:** fixture'lardaki boş, göreli, hotlink-korumalı, 404 ve geçersiz MIME
posterleri deterministik sonuç verir; layout shift oluşmaz.

### Faz 3 — Sağlayıcı ve oynatıcı dayanıklılığı

1. Başlığa göre altı kaynağı her açılışta taramayı kaldır. Seçili kaynak hızlı
   açılır; alternatifler yalnız hata halinde, stabil TMDB/IMDb kimliğiyle aranır.
2. Provider başına timeout, sınırlı retry/backoff, circuit breaker ve sağlık
   durumu ekle. Boş `catch` bloklarını typed hata sonuçlarıyla değiştir.
3. Web ve Android'de aynı hata sınıflarını göster: bağlantı, provider, extractor,
   format, yetki ve timeout.
4. Media3'ü ayrı migration commit'inde güncelle; API ve oynatıcı için paylaşılan
   OkHttp instance'ı, `MediaSession`, playlist/sonraki bölüm ve kontrollü
   `LoadErrorHandlingPolicy` kullan.

Android, tek ağ stack/instance paylaşımını öneriyor:
[Media3 network stacks](https://developer.android.com/media/media3/exoplayer/network-stacks).
Güncel Media3 kararlı sürümü 1.11.0'dır:
[Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3).
Hatalar `PlaybackException` koduna göre ayrıştırılabilir:
[Player events](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

**Kapı:** ilk oynatma isteği gereksiz provider araması yapmaz; seçili kaynak
bozulduğunda kontrollü fallback çalışır; dizi sonraki bölüme geçer; hata kullanıcıya
eyleme dönük biçimde gösterilir.

### Faz 4 — Modern web ve TV deneyimi

Ana navigasyon: **Ana Sayfa · Ara · Kitaplığım · Canlı · Ayarlar**.

Ana sayfa sırası:

1. İzlemeye devam et
2. Öne çıkan/hero
3. Yeni filmler
4. Yerli diziler
5. Yabancı diziler
6. Canlı kanallar
7. Türler ve kullanıcı listeleri

Film tek tıkla oynayabilir; dizi önce detay/bölüm seçimine gider. Kaynak adı ana
deneyimi işgal etmez, oynatma hatasında veya gelişmiş menüde görünür. TV'de her
ekranda ilk odak ve geri dönüş odağı korunur; yatay eksen içerik, dikey eksen raf
gezinmesi olur. Android'in TV rehberi az tık, öngörülebilir D-pad navigasyonu ve
daima görünür odak ister: [TV navigation](https://developer.android.com/training/tv/get-started/navigation)
ve [TV app checklist](https://developer.android.com/training/tv/publishing/checklist).

Jellyfin'den kullanıcıya göre düzenlenebilir ana sayfa/raflar, Plex'ten birleşik
arama ve tek watchlist deseni alınabilir; marka ve ekranlar kopyalanmamalıdır:
[Jellyfin](https://jellyfin.org/) ve [Plex Discover](https://www.plex.tv/discover/).

Web oynatıcı klavye/kumanda ile tamamen kullanılabilir, görünür focus, açıklayıcı
etiket, yeterli kontrast ve altyazı ayarlarına sahip olmalı:
[W3C erişilebilir medya oynatıcı rehberi](https://www.w3.org/WAI/media/av/player/).

**Kapı:** klavye, D-pad, dokunmatik ve fare matrisi; 720p/1080p/4K TV ölçüleri;
mobil/masaüstü responsive test; temel Lighthouse erişilebilirlik ve performans
kontrolü.

### Faz 5 — Güvenlik, dağıtım ve bakım

1. Tünel açıkken auth zorunlu olsun veya Cloudflare Access kullanılsın; admin
   rotaları ayrıca korunmalı.
2. Remote provider sadece HTTPS ve yönetici allowlist'iyle çalışsın; private,
   loopback, link-local ve metadata IP'leri engellensin.
3. `monetag` kimliğini kaldır; release ağ logunu kapat; hassas uygulama verisini
   backup dışına al.
4. Android cleartext'i genel açmak yerine yalnız seçilmiş yerel sunucuya sınırla.
   Android, genel cleartext opt-in'den kaçınılmasını önerir:
   [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).
5. Python bağımlılıklarını kilitle/hash'le, container image'larını digest veya
   kontrollü sürüme sabitle, CI'da lint/typecheck/test/image scan çalıştır.
6. OTA sürümünü release imzası ve doğrulanmış checksum ile yayınla.

**Kapı:** public URL auth'suz katalog/admin döndürmez; SSRF test matrisi yeşil;
release logunda istek URL'leri yok; temiz makinede tekrarlanabilir build alınır.

## Test stratejisi

- **Contract:** local ve remote provider için aynı fixture seti.
- **Plugin adapter:** parser golden HTML/JSON fixture'ları; gerçek siteyi CI'da
  zorunlu bağımlılık yapma.
- **Web:** route/template testleri, JS unit testleri ve Playwright ile temel
  ana sayfa -> detay -> oynatma akışı.
- **Android:** repository/ViewModel unit testleri, MockWebServer contract testleri,
  Compose focus testleri ve Media3 hata/fallback testleri.
- **Ops:** Docker health, restart sonrası recovery, tunnel origin ve disk/cache
  sınırı smoke testleri.

Yeni test paketleri eklenmeden önce proje kuralı gereği ayrıca onay alınmalıdır.

## Araştırma sınırları

- Canlı servis 502/unhealthy olduğu için gerçek cihazda görüntü ve tam medya
  playback doğrulaması yapılamadı.
- Watchbuddy'nin iki discovery endpoint'i erişilebilir; belirli üçüncü taraf
  içeriği veya stream URL'si test edilmedi.
- Kaynak sitelerin dinamik alan adları ürünün çekirdek sözleşmesine taşınmamalı;
  adapter/health katmanında izole edilmelidir.

## Önerilen ilk teslimat

İlk commit yalnız P0 sözleşme ve test işidir: `load_item` düzeltmesi, ortak provider
seçimi, poster URL helper'ı için fixture testleri ve container health smoke testi.
Görsel yeniden tasarım bu kapı yeşil olduktan sonra başlamalıdır.
