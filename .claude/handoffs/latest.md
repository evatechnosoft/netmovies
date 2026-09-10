# DEVİR — 10 Eylül 2026, 22:11 · kod bitti, cihaz denemesi bekliyor

> 21:30 devrinden bu yana **kod değişmedi**. Bu dosya baton'u tazeler ve tek
> kalan adımı öne çıkarır. Ayrıntılı gerekçe zinciri:
> `.claude/handoffs/2026-09-10-2130-ayarlar-sunucuda.md`

**Dal:** `fix/general-stability` @ `365ec8e` · çalışma ağacı temiz, origin ile eşit
**TV sürümü:** `v0.1.74-poc` — `data/apk/` içinde, OTA'da görünüyor. **Cihaza kurulmadı.**
**Yığın:** doh · engine · stream · tunnel · warp — beşi ayakta

## Hedef
Dean'in oturum boyunca bildirdiği kullanım şikâyetlerini kök nedeninden kapatmak.
Kod tarafı bitti; kalan iş doğrulama.

## Durum

### Doğrulanmış (tool çıktısı görüldü)
- **Yedek sağlayıcı zinciri onarıldı.** `resolve_sources` alternatifleri
  `quote_plus(match)` ile çağırıyordu, `_links_for` düz URL bekliyor → eklentiye
  `https%3A%2F%2F…` gidiyordu. İlk kaynak patlayınca hiçbir alternatif devreye
  giremiyormuş. Kanıt: The Walking Dead: Dead City **0 → 2 kaynak · 19 bölüm**.
- **Ayar deposu çalışıyor.** `POST /api/v1/prefs` iki anahtar yazdı, ikinci POST
  üçüncüyü ekledi ve öncekiler durdu (birleşme doğru), `/data/prefs.json` diskte.
- **TV buton eşlemesi sunucuda.** `tv_keymap_23_SINGLE` yazıldı ve geri okundu;
  APK dex'inde `tv_keymap_`, `api/v1/prefs`, `sunucudanYukle` var.
- FilmMakinesi dizi desteği (`load_item` → 22 bölüm) · Yeni Çıkanlar yıl sıralaması
  (312 içerik, 280'inde yıl, ilk 12 kart 2026) · DiziBox "yeni" rafı taze ·
  sağlayıcı sırası kapta doğrulandı.
- `testDebugUnitTest + assembleDebug` exit 0 · OTA `v0.1.74-poc` · stream tests OK ·
  `smoke.sh` YEŞİL · tünel `/giris` 200.

### Doğrulanmamış (sadece derlendi, cihazda görülmedi)
Bu oturumun **TV tarafındaki hiçbir düzeltmesi** televizyonda denenmedi:
GERİ tuşunun çıkması, listelerde başa dönmesi, bölüm listesinin kaydırmadan
görünmesi, bölüme basınca doğrudan oynaması, odak nöbeti, buton eşlemesinin
yeniden kurulumdan sonra geri gelmesi.

## Tek sıradaki eylem
**TV'ye v0.1.74'ü kur ve şu dördünü sırayla dene** (ev ağındayken OTA'dan iner):
1. Bir postere gir → GERİ **çıkıyor mu** (eskiden OYNAT'a atlayıp kalıyordu).
2. Gözat / Takip / Kanallar → GERİ en üste çıkarıp kapatıyor mu.
3. Dizi aç → bölüm sayısı kaydırmadan görünüyor mu · bölüme basınca doğrudan
   oynuyor mu · sezon değişince odak kayboluyor mu.
4. Buton eşlemesini değiştir → uygulamayı kaldır/kur → eşleme geri geliyor mu.

## Tekrarlama / tuzaklar
- **Eklenti kodu değişince `docker compose up -d --build engine`** — `restart`
  imajdaki eski kodu çalıştırır (Plugins imaja gömülü, volume yok).
- **Stream'e her dokunuş tüneli düşürür.** Kurtarma:
  `docker compose --profile tunnel up -d --force-recreate --no-deps cloudflared`
- **`python -c "from Public.API...import"`** engine kabında dairesel import verir,
  sunucu sağlıklıyken bile. Sabit doğrulamak için `docker exec ... sed -n`.
- **`docker exec ... python - <<EOF`** Git Bash'te çıktı vermiyor; betiği dosyaya
  yaz, `docker cp` ile kopyala, sonra çalıştır.
- Kaplarda `unzip`/`strings` yok; APK içi doğrulama Python `zipfile` ile.
- **Ayar sıfırlanması bir daha "köken" değildir.** localStorage köken başına ayrıydı
  (LAN ≠ tünel), o kapandı. Şikâyet sürerse `/api/v1/prefs` içeriğine bak.
- FilmMakinesi kendi sayfaları engine'in paylaşılan httpx'inde `Non-2xx` veriyor
  (taze istemcide 200). UA/WARP farkı, ayrı iş; alternatif sağlayıcı devrede.
- DiziBox'ta Türk dizisi için tarih sıralı sayfa YOK.
- **Eski devir dosyalarını geri getirme.** Dean `.claude/handoffs/` altını
  bilerek temiz tutuyor; silinmiş bir baton dosyası kaza değil, tercihtir.

## Sonraki iki iş (ikisi de başlanmadı)
1. **Ana ekran widget'ı + Samsung saat uygulaması.** Tasarım hazır:
   `docs/mini-widget-taslak.html` (yay üzerinde yuvarlak posterler, yön pad,
   OK = başlat, GERİ = duraklat → ikinci basışta çık). Ayrı Gradle modülü.
2. **Ajanda sayfası.** Plan onaylı, kod yok. TMDB uçları sınandı: `discover/tv`
   (TR bu hafta 21 dizi), `movie/upcoming` (TR 15 film).
   `GET /api/v1/agenda?view=week|month` · günde 1 tazeleme · `/ajanda` · TV'de WebView.
