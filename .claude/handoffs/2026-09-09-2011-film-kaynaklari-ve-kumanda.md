# DEVİR — film kaynak turu + kumanda/oynatma düzeltmeleri

**Tarih:** 9 Eylül 2026, 20:11 · **Dal:** `fix/general-stability` @ `a250a6e` (temiz, origin ile eşit)
**Yığın:** doh · engine · stream · tunnel · warp — beşi de ayakta, `smoke.sh` YEŞİL
**TV sürümü:** `v0.1.65-poc` — `data/apk/`, yerel OTA · **cihaza kurulmadı**

## Hedef
Film tarafını dizi tarafıyla eşitlemek (kaynak sayısı + yedek), sonra Dean'in cihazda
gördüğü hataları kök nedeninden çözmek.

## Durum — DOĞRULANDI (tool çıktısıyla)
- **Katalog:** movie 144 → **313** (HDFilmCehennemi 124 · FilmMakinesi 91 · KultFilmler 78 ·
  DiziPal 20); 12 film iki kaynakta. 13 eklenti yüklü.
- **Yeni eklentiler oynuyor:** KultFilmler ve FilmMakinesi'nde 2'şer film, proxy üzerinden
  manifest `#EXTM3U` 200.
- **Akış sırası:** `aggregate_new` kaynakları `zip_longest` ile dönüşümlü veriyor
  (ilk 10: DiziPal → FilmMakinesi → HDFC → KultFilmler …).
- **`/rc` JS'i ölüydü** — `node --check`: `SyntaxError: Unexpected identifier 'de'`
  (kaçırılmamış kesme işareti, `c751ce1`'den beri). Düzeltildi, `node --check` temiz.
  `remote/command` 16 komut tipinin 16'sı 200.
- **Oynatma takılması çözüldü:** aynı segment CDN'den 1,3 sn / proxy'den **19,7 sn** idi.
  Düzeltmeden sonra 4 taze segment 0,51–0,93 sn (21–49 Mbit/s).
- **PIN kapısı:** `Host=192.168.0.29` → 200, `w.evaitec.com` çerezsiz → 401.
- **smoke.sh** artık ilk kaynağın manifest'ini proxy'den indirip `#EXTM3U` görmeden yeşil demiyor.

## Durum — DOĞRULANMADI (cihazda denenmedi)
v0.1.60–v0.1.65 arası TV değişikliklerinin hiçbiri televizyonda görülmedi: yazısız ⚙/📱,
Gözat ★ puanı, Gözat'ta GERİ davranışı, bölüm seçicide D-pad, uygulama içi kumanda (WebView),
donma/ANR düzeltmesi. Hepsi `testDebugUnitTest + assembleDebug EXIT 0` ve dex kontrolüyle sınırlı.

## Kararlar ve gerekçe
- **Selcukflix (IzleAI) kapsam dışı:** katalog veriyor ama oynatıcısı `sn.dplayer82.site`
  Cloudflare 403 — httpx, curl, curl_cffi chrome impersonate, WARP hepsi. Dosya silindi.
- **DiziPal `/filmler` arşivi "Popüler Filmler" oldu**, gerçek yeni liste ana sayfadaki
  "Son Eklenen Filmler" bölümünden (6 film) ayrıştırılıyor.
- **Disk segment cache korundu ama yanıt yolundan çıkarıldı:** `/data` Windows'a 9p ile
  bağlı; dizinde 2676 dosya / 2,1 GB birikince her yazımdaki tam tarama ~17 sn sürüyordu.
  Yazım arka plana alındı, budama 200 yazımda bir (`os.scandir`).
- **PIN kapısında ev ağı muafiyeti Host'tan yapılıyor**, IP'den değil: Docker ardında
  istemci IP'si hep `172.31.0.1` görünüyor; tünel isteklerinin Host'u ise her zaman alan adı.
- **`/rc` yanıtı `Cache-Control: no-store`:** telefonda kalan eski kopya yüzünden
  düzeltmeler cihaza ulaşmıyordu (Dean "play/pause çalışıyor" derken eski sayfadaydı).

## Bir daha yapma / tekrar arama
- **Ölü film kaynakları** (tekrar denemeye değmez): FilmModu (domain kumar sitesine gitmiş) ·
  FullHDFilm (`.site` NXDOMAIN, `.pro` WARP 429) · SineWix (`ythls.kekikakademi.org` yok) ·
  UgurFilm (parklanmış) · Watch2Movies (NXDOMAIN + WebView bağımlı) · SetFilmIzle (Plesk) ·
  SuperFilmGeldi (410) · SinemaCX / WebteIzle (NXDOMAIN).
- **`activeBase()`'i composable içinden çağırma** — /24 taraması ana iş parçacığında koşup
  ANR üretiyordu; UI için `uiBase()` var.
- **FilmMakinesi'ne kısa User-Agent ile gitme** (403); tam Chrome UA şart. `.de` ölü, `.to` canlı.
- **Emoji ikon kullanma** (`/rc`): bazı telefonlarda sarı kutu/boş kare çiziliyor; SVG sprite var.
- HANDOFF.md'deki eski "yapma" listesi (Azure vault arama, model adı tahmini,
  `AUTH_USER/AUTH_PASS` doldurma, stream'e dokununca tünelin düşmesi) hâlâ geçerli.

## SIRADAKİ TEK İŞ
**TV'ye v0.1.65'i kur ve yukarıdaki "doğrulanmadı" listesini tek tek dene.** Ev ağındayken
(OTA LAN'dan iner). Kurulum "Uygulama yüklenemedi" derse eskiyi kaldırıp kur.
Sonrasında sıradaki aday iş: **JetFilmizle** portu (canlı `jetfilmizle.now`, upstream v19,
site yeniden tasarlanmış → `.kt` seçicileri bayat, orta zorluk).

## Doğrulama komutları
```bash
git fetch && git checkout fix/general-stability && git pull   # a250a6e
docker compose ps                                             # 5 kap
bash scripts/smoke.sh                                         # YEŞİL · 13 eklenti · movie 313
docker exec -w /usr/src/Stream netmovies-stream python -m unittest discover -s tests
curl -s localhost:3310/api/v1/app_update                      # v0.1.65-poc
```
Git Bash'te `docker exec`/curl için `MSYS_NO_PATHCONV=1`; Türkçe metinli isteği Python'la at.
