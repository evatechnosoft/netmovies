# DEVİR — 10 Eylül 2026, 23:12 · tek kalan iş: TV'de dene

**Dal:** `fix/general-stability` @ `3529746` · origin ile eşit
**TV sürümü:** `v0.1.74-poc` — `data/apk/` içinde, OTA'da görünüyor. **Cihaza kurulmadı.**
**Yığın:** doh · engine · stream · tunnel · warp — beşi ayakta

21:30'dan bu yana **kod değişmedi**. Gerekçe zinciri ve tam kanıt tablosu:
`.claude/handoffs/2026-09-10-2130-ayarlar-sunucuda.md`

## Hedef
Dean'in oturum boyunca bildirdiği kullanım şikâyetlerini kök nedeninden kapatmak.
Kod bitti; kalan iş cihazda doğrulama.

## Durum

**Doğrulanmış (tool çıktısı var):** yedek sağlayıcı zinciri onarıldı (aynı içerik
**0 → 2 kaynak · 19 bölüm**) · ayar deposu `/api/v1/prefs` yazıp birleştiriyor,
`/data/prefs.json` diskte · TV buton eşlemesi sunucuda (yazıldı, geri okundu,
dex'te izleri var) · FilmMakinesi dizi desteği (22 bölüm) · Yeni Çıkanlar yıl
sıralaması (312 içerik, 280'inde yıl) · `assembleDebug` exit 0 · OTA `v0.1.74-poc` ·
stream tests OK · `smoke.sh` YEŞİL · tünel 200.

**Doğrulanmamış:** bu oturumun **TV tarafındaki hiçbir düzeltmesi** televizyonda
görülmedi — GERİ tuşu, bölüm listesi, odak nöbeti, eşlemenin kurulumdan sonra
geri gelmesi. Hepsi yalnız derlendi.

## Tek sıradaki eylem
**TV'ye v0.1.74'ü kur, dördünü sırayla dene** (ev ağındayken OTA'dan iner):
1. Postere gir → GERİ **çıkıyor mu** (eskiden OYNAT'a atlayıp kalıyordu).
2. Gözat / Takip / Kanallar → GERİ başa çıkarıp kapatıyor mu.
3. Dizi aç → bölüm sayısı kaydırmadan görünüyor mu · bölüme basınca doğrudan
   oynuyor mu · sezon değişince odak kayboluyor mu.
4. Buton eşlemesini değiştir → uygulamayı kaldır/kur → eşleme geri geliyor mu.

## Tekrarlama / tuzaklar
- **`.claude/handoffs/` Dean'in temiz tuttuğu yer.** Silinmiş bir baton dosyası
  kaza değil TERCİHTİR — geri getirme, `git restore` etme. (Bu oturumda bir kez
  yanlış yapıldı ve geri alındı.) 23:12 itibarıyla `2026-09-03-2320-*.md` de
  silinmiş durumda, commit edilmedi; Dean'in temizliği sürüyor olabilir.
- **Eklenti kodu değişince `docker compose up -d --build engine`** — `restart`
  imajdaki eski kodu çalıştırır (Plugins imaja gömülü, volume yok).
- **Stream'e her dokunuş tüneli düşürür.** Kurtarma:
  `docker compose --profile tunnel up -d --force-recreate --no-deps cloudflared`
- **`python -c "from Public.API...import"`** engine kabında dairesel import verir,
  sunucu sağlıklıyken bile. Sabit doğrulamak için `docker exec ... sed -n`.
- **`docker exec ... python - <<EOF`** Git Bash'te çıktı vermiyor; betiği dosyaya
  yaz, `docker cp` ile kopyala, sonra çalıştır.
- Kaplarda `unzip`/`strings` yok; APK içi doğrulama Python `zipfile` ile.
- **Ayar sıfırlanması artık "köken" değil.** localStorage köken başına ayrıydı
  (LAN ≠ tünel); kapandı. Şikâyet sürerse `/api/v1/prefs` içeriğine bak.
- FilmMakinesi kendi sayfaları engine'in paylaşılan httpx'inde `Non-2xx` veriyor
  (taze istemcide 200). UA/WARP farkı, ayrı iş; alternatif sağlayıcı devrede.
- DiziBox'ta Türk dizisi için tarih sıralı sayfa YOK.

## Sonraki iki iş (başlanmadı)
1. **Ana ekran widget'ı + Samsung saat uygulaması.** Tasarım hazır:
   `docs/mini-widget-taslak.html`. Ayrı Gradle modülü, kendi manifesti.
2. **Ajanda sayfası.** Plan onaylı, kod yok. TMDB uçları sınandı: `discover/tv`
   (TR bu hafta 21 dizi), `movie/upcoming` (TR 15 film).
   `GET /api/v1/agenda?view=week|month` · günde 1 tazeleme · `/ajanda` · TV'de WebView.
