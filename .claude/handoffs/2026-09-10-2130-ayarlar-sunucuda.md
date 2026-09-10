# DEVİR — 10 Eylül 2026, 21:30 · GERİ tuşu, ayar kalıcılığı, kaynak zinciri

**Dal:** `fix/general-stability` @ `b369b29` · temiz, origin ile eşit
**TV sürümü:** `v0.1.74-poc` — `data/apk/` içinde, OTA'da görünüyor. **Cihaza kurulmadı.**
**Yığın:** beş kap ayakta · `smoke.sh` YEŞİL · tünel `/giris` 200

## Hedef
Dean'in oturum boyunca bildirdiği kullanım şikâyetlerini kök nedeninden kapatmak:
GERİ tuşu, bölüm seçimi, ayarların sıfırlanması, ana ekranda eski filmler,
"içerik bulunamadı", telefon kumandası düzeni.

## Bu oturumun iki büyük kök nedeni
1. **Yedek sağlayıcı zinciri tamamen ölüymüş.** `resolve_sources` alternatif
   sağlayıcıları `quote_plus(match)` ile çağırıyordu; `_links_for` düz URL bekliyor.
   Eklentiye `https%3A%2F%2F…` gidiyor, httpx "missing protocol" diyordu. Yani ilk
   kaynak patlayınca hiçbir alternatif devreye giremiyordu. Kanıt: The Walking Dead:
   Dead City → **0 kaynak** iken **2 kaynak · 19 bölüm**.
2. **GERİ tuşu Compose'a hiç ulaşmıyormuş.** TV Material odak grupları GERİ'yi
   "gruptaki ilk öğeye dön" diye yutuyor, `BackHandler` çalışmıyordu. Belirti:
   oynatıcıda odak OYNAT'a atlayıp çıkmama, listelerde raflar arasında gezinme.
   Çözüm tek yerde: `client-tv/.../input/BackBus.kt` — ekranlar `NmBackHandler` ile
   kaydolur, `MainActivity.dispatchKeyEvent` tuşu Compose'a İNMEDEN en üstteki
   işleyiciye verir. Sekiz ekran geçirildi, her ekranın kendi mantığı korundu.

## Doğrulanmış (tool çıktısı görüldü)
| Ne | Kanıt |
|---|---|
| Alternatif zincir onarıldı | 0 → 2 kaynak · 19 bölüm |
| FilmMakinesi dizi desteği | `load_item` → 22 bölüm (SeriesInfo, `/sezon-N/bolum-M/`) |
| Yeni Çıkanlar yıl sıralaması | movie 312, 280'inin yılı var, ilk 12 kart 2026 |
| DiziBox "yeni" rafı | The Ark / The Game / Star Trek SNW |
| FilmMakinesi dublaj etiketi | `rank 0 / "Türkçe dublaj"` |
| Kumanda "şu an oynayan" | `POST /remote/state` → `status.now_playing` dolu |
| **Ayar deposu** | `POST {"rc_sira":[...],"rc_esik":52}` → ok · ikinci POST birleşti, üç anahtar birlikte döndü · `/data/prefs.json` diskte |
| TV buton eşlemesi | `POST tv_keymap_23_SINGLE` → GET aynı anahtarı döndürdü · dex'te `tv_keymap_`, `api/v1/prefs`, `sunucudanYukle` VAR |
| Sağlayıcı sırası | kapta `DiziPal, DiziMom, HDFilmCehennemi + harf sırası` |
| Derleme / kapı | `testDebugUnitTest + assembleDebug` exit 0 · OTA `v0.1.74-poc` · stream tests OK · smoke YEŞİL · tünel 200 |

## Doğrulanmamış — sıradaki iş bu
**TV'ye v0.1.74 kurulmadı.** Bu oturumun TV tarafındaki hiçbir düzeltmesi cihazda
görülmedi. Ev ağındayken OTA'dan iner. Sırayla bakılacak:
1. Bir postere gir → GERİ **çıkıyor mu** (v0.1.72 öncesi OYNAT'a atlıyordu).
2. Listelerde (Gözat / Takip / Kanallar) GERİ en üste çıkarıp kapatıyor mu.
3. Dizi aç → bölüm listesi kaydırmadan görünüyor mu, bölüme basınca doğrudan
   oynuyor mu, sezon değişince odak kayboluyor mu.
4. Buton eşlemesini değiştir → uygulamayı kaldır/kur → eşleme geri geliyor mu.

## Kararlar ve nedeni
- **Ayarlar sunucuda.** `localStorage` KÖKEN BAŞINA ayrı: LAN `192.168.x.x:3310`
  ile tünel `w.evaitec.com` iki ayrı depo, PWA'yı yeniden kurmak da siliyor.
  Dean'in "her açılışta sıfırlanıyor" şikâyetinin sebebi buydu. Yeni uç:
  `GET/POST /api/v1/prefs` → `/data/prefs.json` (atomik yazma, 64 KB tavan, POST
  **birleştirir/silmez**). Kumandada `localStorage` yalnız anlık önbellek; yazma
  600 ms toplanır. Uç tarayıcıdan çağrılıp diske yazdığı için `_KORUMALI_API`'de.
- **Yıl bilgisi bedava geldi.** TMDB `search/multi` yanıtı yılı zaten taşıyordu;
  `_year_cache` + `year_for()` ile saklandı, **ek istek yok**. Soğuk cache'te sıra
  değişmez, yılı bilinmeyen içerik listeden düşmez.
- **Mini kumandadan çıktı.** Tasarım `docs/mini-widget-taslak.html`'de bekliyor;
  ana ekran widget'ı ve saat uygulaması oradan yazılacak.
- **Bölüme basmak doğrudan oynatır**, panelin tepesine dönmek yok.

## Bir daha deneme / tuzaklar
- **Eklenti kodu değişince `docker compose up -d --build engine`** — `restart`
  imajdaki eski kodu çalıştırır (Plugins imaja gömülü, volume yok).
- **Stream'e her dokunuş tüneli düşürür.** Kurtarma:
  `docker compose --profile tunnel up -d --force-recreate --no-deps cloudflared`
- **`python -c "from Public.API...import"`** engine kabında dairesel import verir,
  sunucu sağlıklıyken bile. Sabit doğrulamak için `docker exec ... sed -n` kullan.
- **`docker exec ... python - <<EOF`** Git Bash'te çıktı vermiyor; betiği dosyaya
  yaz, `docker cp` ile kopyala, sonra çalıştır.
- Kapta `unzip`/`strings` yok; APK içi doğrulama için Python `zipfile` kullan.
- **FilmMakinesi kendi sayfaları engine'in paylaşılan httpx'inde `Non-2xx`**
  veriyor (taze istemcide aynı sayfa 200). UA/WARP farkı, ayrı iş. Alternatif
  sağlayıcı devreye girdiği için oynatma çalışıyor.
- DiziBox'ta Türk dizisi için tarih sıralı sayfa YOK; arşiv yalnız IMDB/yorum sıralıyor.
- **Telefonda kumanda düzeni:** Dean bir kez daha "kaydetmedi" derse artık sebep
  köken değil — `/api/v1/prefs` içeriğine bak.

## Sonraki iki iş (ikisi de başlanmadı)
1. **Ana ekran widget'ı + Samsung saat uygulaması.** Tasarım hazır
   (`docs/mini-widget-taslak.html`): yay üzerinde yuvarlak posterler, yön pad,
   OK = başlat, GERİ = duraklat → ikinci basışta çık. Ayrı Gradle modülü,
   kendi manifesti, eşleştirme akışı.
2. **Ajanda sayfası.** Plan onaylandı, kod yazılmadı. TMDB uçları sınandı:
   `discover/tv` (TR bu hafta 21 dizi), `movie/upcoming` (TR 15 film).
   `GET /api/v1/agenda?view=week|month`, günde 1 tazeleme, `/ajanda` sayfası,
   TV'de WebView.
