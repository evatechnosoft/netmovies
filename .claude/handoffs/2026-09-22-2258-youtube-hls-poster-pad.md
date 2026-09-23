# DEVİR — 22 Eylül 2026, 22:58 · YouTube HLS + poster pad · TV'de test bekliyor

**Dal:** netmovies `fix/general-stability` @ `dcd321f` (push'lu, çalışma ağacı temiz)
**Sürümler:** APK **0.9.17** (yayında · üç yer) · saat 0.1.18 · webOS ipk 0.1.4 (değişmedi, gerekmiyor)
**Yerel:** `192.168.1.185:3310` · LG TV `192.168.1.175` · tünel AYAKTA (`w.evaitec.com` 200)

## Bu oturumda kapanan işler — KANITLI

1. **YouTube 1080p/4K** (`b8aa969`). Önceki oturumun "en fazla 360p, ffmpeg gerekir"
   sonucu YANLIŞTI: progressive akışlara bakılmıştı. **HLS master playlist ses+video
   BİRLEŞİK** rendition taşıyor (1080p avc1 → 2160p vp9), kaliteyi istemci seçer.
   Kanıt: master'da `RESOLUTION=3840x2160`, 1080p variant ilk segmenti `http=200
   bytes=1384056`. Engine `manifest_url` tercih ediyor, 30 dk çözüm önbelleği,
   hata anında günde bir kez `pip install -U yt-dlp` + tek yeniden deneme.
   `yt-dlp` requirements'a, **deno** Dockerfile'a girdi (ikisi de elle kurulmuştu).
   `/youtube-search` (engine) + `/youtube_search` (gateway) · /tv aramasında
   sonuçların sonuna YouTube kartları, oynatmada zincir yerine doğrudan çözüm.
   Kırık `_ytdlp_extractor` singleton'ı kaldırıldı — uç 500 veriyordu, hiç çalışmamış.

2. **Poster uzun basışı** (`8bfcca9`, 0.9.16). Kök neden iki istemcide de aynı:
   uzun basış dokunma olayına bağlıydı, kumandanın OK'u oraya düşmüyor.
   APK: Compose `onLongClick` yalnız pointer'da tetiklenir → `onKeyEvent` +
   `isLongPress || repeatCount>0`. /tv: Magic Remote imleci açıkken OK keydown
   olarak GELMİYOR → kartlarda `mousedown`/`mouseup` 600 ms ölçümü.

3. **Donma ölçümü** (`73e3928`). `continue_watching` **500** veriyordu: canlı yayında
   `video.duration = Infinity`, SQLite kabul ediyor, JSON'a çevirirken patlıyor
   ("Out of range float values are not JSON compliant"). Devam Et rafı komple ölüydü.
   3 bozuk kayıt silindi, yazma tarafına sonluluk kapısı kondu → 200 (0,78 s).
   **Canlı TV kapatıldı** (Dean istedi): `M3UPlaylist` → `hidden_providers`,
   /tv'den Canlı TV rafı kaldırıldı, `aggregate_new?type=live` → `count: 0`.
   Cloudflare tüneli 18:53'ten beri kopuktu (stream rebuild, netns pinli) →
   `--force-recreate` ile ayağa kalktı. Docker build cache 20,5 GB temizlendi.

4. **Dört yönlü pad** (`dcd321f`, 0.9.17). Dean: "koca bir liste açıyor... 4 yön
   tuşu gibi pad, hiçbir özellik tam sayfa olmasın." `PosterMenu` tam ekran 3
   sütundan 620dp panele indi: ▲ bölümler · ▼ listeler (3 düğme yan yana) ·
   ◀ özet · ▶ benzerleri · orta oynat. GERİ bir katman geri alır.
   Sunucuda yeni `/similar` ucu (TMDB recommendations + cache) — Dune için 20 öneri.
   Benzer seçilince `search_all` ile katalogda aranır, bulunan kart pad'de açılır.

## DOĞRULANMADI — Dean'in TV'de bakması gereken
- 0.9.17 pad'i hiçbir cihazda görülmedi (bağlı cihaz yok, `adb devices` boş).
  Derleme + `testDebugUnitTest` temiz, davranış kanıtı YOK.
- /tv'de fare ile poster basılı tutma TV'de denenmedi.
- LG'de VP9 2160p rendition'ı webOS medya motoru seçiyor mu bilinmiyor; avc1 1080p kesin.

## Bekleyen / Dean'e düşen
- **Statik IP**: PC (Wi-Fi) hâlâ DHCP ile 185. Yönetici PowerShell'de:
  `Set-NetIPInterface -InterfaceAlias "Wi-Fi" -Dhcp Disabled; Remove-NetIPAddress -InterfaceAlias "Wi-Fi" -AddressFamily IPv4 -Confirm:$false; New-NetIPAddress -InterfaceAlias "Wi-Fi" -IPAddress 192.168.1.185 -PrefixLength 24 -DefaultGateway 192.168.1.1; Set-DnsClientServerAddress -InterfaceAlias "Wi-Fi" -ServerAddresses 1.1.1.1,1.0.0.1`
- Dean 170 istemişti; **185 gömülü** (webOS ipk içi, apps.json mirrors ×3, TV/saat
  LAN adayları) ve OTA'nın kendisi 185'ten iniyor → taşınırsa kurulu istemciler
  hem sunucuyu hem güncelleme yolunu kaybeder. Taşıma kararı Dean'de.
- Disk D: **%93 / 17 GB boş**. Build cache temizlendi ama WSL vhdx kendiliğinden
  küçülmüyor — gerçek kazanç için vhdx shrink gerek.

## Tekrarlama / tuzaklar
- YouTube client taramasını TEKRARLAMA: HLS master çözüm, sonuç yukarıda.
- `-f best/all` yt-dlp'de her format için AYRI JSON satırı basar → `json.loads` patlar.
- `-f best` YouTube'da "Requested format is not available" veriyor (birleşik progressive yok);
  format seçimi Python'da yapılıyor, `-f` verilmiyor.
- Şablon değişince `restart` değil `up -d --build stream`; ardından tünel `--force-recreate`.
- Git Bash `docker exec ... /tmp/x` yolunu Windows'a çeviriyor (MSYS) — stdout'a al.
- Uzun Kotlin bloğu `<<'PY'` heredoc'unda kırılıyor; parçayı scratchpad'e Write edip
  python ile yerleştir.
- `data/admin.json` gitignored ve **gemini_api_key içeriyor** — commit etme.

## TEK SONRAKİ EYLEM
Dean 0.9.17'yi Mi Box'a kurup pad'i denesin (yerel OTA hazır: `NetMovies-TV-v0.9.17.apk`).
Geri bildirime göre pad düzeltilecek, sonra aynı düzen /tv'ye taşınacak (Dean'in seçimi:
"önce Mi Box, sonra LG").
