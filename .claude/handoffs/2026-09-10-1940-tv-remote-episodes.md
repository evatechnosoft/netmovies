# DEVİR — 10 Eylül 2026, 19:40 · kumanda, bölüm seçimi, kaynak zinciri

**Dal:** `fix/general-stability` @ `bd69bd8` · temiz, origin ile eşit
**TV sürümü:** `v0.1.71-poc` — `data/apk/` içinde, yerel OTA'da görünüyor. **Cihaza kurulmadı.**
**Yığın:** doh · engine · stream · tunnel · warp — beşi ayakta, `smoke.sh` YEŞİL, tünel `/giris` 200.

## Hedef
Dean'in bu oturumdaki isteklerini kapatmak: TV oynatıcı/başlangıç paneli, telefon
kumandası (`/rc`), ana ekran sıralaması ve kaynak zinciri.

## Doğrulanmış (tool çıktısı görüldü)
| Ne | Kanıt |
|---|---|
| Oynatma bandı kalkıyor | v0.1.67, STATE_READY'de `status = null` |
| FilmMakinesi dublaj etiketi | `resolve_sources` → `rank 0 / "Türkçe dublaj"` |
| Kumanda "şu an oynayan" şeridi | `POST /remote/state` → `status.now_playing` dolu |
| DiziBox "yeni" rafı | ilk kartlar The Ark / The Game / Star Trek SNW |
| Yeni Çıkanlar yıl sıralaması | movie 312 içerik, 280'inin yılı var, ilk 12 kart 2026 |
| **Alternatif sağlayıcı zinciri onarıldı** | The Walking Dead: Dead City → önce **0 kaynak**, sonra **2 kaynak · 19 bölüm** |
| FilmMakinesi dizi desteği | `load_item` → 22 bölüm (SeriesInfo) |
| Sağlayıcı sırası | kapta `ALTERNATIVE_ORDER = DiziPal, DiziMom, HDFilmCehennemi + harf sırası` |
| TV derleme | `testDebugUnitTest + assembleDebug` exit 0 · OTA `v0.1.71-poc` |
| Kumanda sayfası | `/rc` JS `node --check` OK · 7 blok · Mini sekme kalıntısı 0 |

## Doğrulanmamış (cihazda denenmedi)
TV'ye v0.1.71 kurulmadı. Şunların hiçbiri cihazda görülmedi: bölüm seçince
oynatma, odak nöbeti, ana ekranda GERİ'nin başa dönmesi, kumandadaki Mini panel,
dokunmatik yüzey hassasiyeti (44 px) ve kenar tekrarı (170 ms).

## Bu oturumun kararları ve nedeni
- **Yıl sıralaması TMDB cache'inden.** `search/multi` yanıtı yılı zaten taşıyordu;
  `_year_cache` + `year_for()` ile saklandı, **ek TMDB isteği yok**. Soğuk cache'te
  sıra değişmez (güvenli geri düşüş), yılı bilinmeyen içerik listeden düşmez.
- **`quote_plus(match)` kaldırıldı** (`resolve_sources.py`). `_links_for` düz URL
  bekliyor; alternatifler kodlanmış URL alınca httpx "missing protocol" diyordu.
  Yani ilk kaynak patlayınca **yedek zincirin tamamı sessizce ölüydü**. Bu oturumun
  en büyük kazancı bu.
- **Mini sekme değil panel.** Saat uygulaması için ayrılan tasarım kumandaya sekme
  olarak konmuştu; Dean "orası değil" dedi → tek tuşla inen panel oldu, "Düzenle"
  başlıktan çıkıp panelin içine girdi.
- **Bölüme basmak doğrudan oynatır.** Panelin tepesine dönmek fazladan yolculuktu.
- **Ana ekran GERİ:** odak ÖNCE en üste alınır, sonra liste kaydırılır. Ters sırada
  liste hemen geri kayıyordu.

## Bir daha deneme / tuzaklar
- **Eklenti kodu değişince `docker compose up -d --build engine`** — `restart`
  imajdaki eski kodu çalıştırır (Plugins imaja gömülü, volume yok). CLAUDE.md'ye yazıldı.
- **Stream'e her dokunuş tüneli düşürür.** Kurtarma:
  `docker compose --profile tunnel up -d --force-recreate --no-deps cloudflared`
- **`python -c "from Public.API...import"`** engine kabında dairesel import verir —
  sunucu sağlıklıyken bile. Sıra/sabit doğrulamak için `docker exec ... sed -n` kullan.
- **`docker exec ... python - <<EOF`** Git Bash'te çıktı vermiyor; betiği dosyaya
  yazıp `docker cp` ile kopyala, sonra çalıştır.
- **FilmMakinesi kendi sayfaları engine'in paylaşılan httpx'inde `Non-2xx` veriyor**
  (taze httpx istemcisinde aynı sayfa 200). UA/WARP farkı. Alternatif sağlayıcı
  devreye girdiği için oynatma çalışıyor — ayrı iş.
- DiziBox'ta Türk dizisi için tarih sıralı sayfa YOK; "Yerli Diziler" rafı hâlâ
  tarihsiz. Arşiv yalnız IMDB/yorum sıralıyor.

## Sıradaki tek iş
**TV'ye v0.1.71'i kur ve dene.** Ev ağındayken OTA'dan iner. Bakılacaklar sırayla:
bir dizi aç → bölüm listesi kaydırmadan görünüyor mu · bölüme basınca doğrudan
oynuyor mu · sezon değişince odak kayboluyor mu · ana ekranda GERİ başa dönüyor mu.

Sonra bekleyen iki iş (ikisi de başlanmadı):
1. **Samsung saat uygulaması / widget** — Mini tasarımı (yay üzerinde yuvarlak
   posterler, yön pad, OK=başlat, GERİ=duraklat→çıkış) oraya taşınacak. Ayrı Gradle
   modülü + eşleştirme akışı.
2. **Ajanda sayfası** — plan çıkarıldı, onay alındı, kod yazılmadı. TMDB liste uçları
   sınandı ve çalışıyor: `discover/tv` (TR bu hafta 21 dizi), `movie/upcoming`
   (TR 15 film). `GET /api/v1/agenda?view=week|month`, günde 1 tazeleme, `/ajanda`
   sayfası, TV'de WebView.
