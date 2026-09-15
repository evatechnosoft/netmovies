# Oynatma Kalitesi Planı — performans, ses kesintisi, kalite, kaynak seçimi

> 15 Eylül 2026. Araştırma + kod keşfi sonucu. Kanıtlar dosya:satır; dış kaynaklar sonda.
> **DURUM: dört fazın tamamı uygulandı** (15 Eylül, TV v0.2.9). Kanıt: stream
> 130/130 test, TV `testDebugUnitTest assembleDebug` Exit Code 0. Cihazda
> DENENMEDİ — ses kesintisinin gerçekten bittiği `client_log` ile doğrulanmalı.

## Ne bulduk (kanıtlı)

**TV oynatıcısı tamamen varsayılan ayarlarla çalışıyor** — Media3 1.11.0
(`client-tv/app/build.gradle.kts:88-90`), ama:
- `LoadControl` yok (`PlayerScreen.kt:143-148`): tampon 50 sn hedef / 2.5 sn başlangıç
  varsayılanı. Ev upload'ından geçen segmentlerde küçük tampon = takılma.
- Tunneling / audio offload kapalı (repoda `setTunnelingEnabled` yok). Android TV'de
  ses-video senkronu ve ses kesintisi için birincil öneri bu (Media3 dokümanı).
- `DefaultHttpDataSource` (`PlayerScreen.kt:740-743`): katalogdaki IPv4 DNS pini
  (`Network.kt:19`) video akışına uygulanmıyor.
- **İkinci tam ExoPlayer** (`previewExo`, `PlayerScreen.kt:151-157`, `781-784`) aynı
  HLS akışını paralel hazırlıyor: ikinci kod çözücü + ikinci indirme zinciri, scrub
  yapılmasa da. Mi Box sınıfı cihazda ses tamponu için en güçlü şüpheli.
- Ses teşhisi var (`onAudioUnderrun/onAudioSinkError`, `PlayerScreen.kt:514-546`)
  ama günlük şu an boş ("Kayıt yok" — `client_log`). Kesinti gerçekleşince kanıt burada.

**Kalite varsayılanı bağlı değil.** `window.DEFAULT_QUALITY` yalnız okunuyor
(`hls-setup.js:109`), hiçbir yer yazmıyor; admin config'te kalite anahtarı yok
(`admin_config.py:28-77`). TV'de `qualityAuto` sabit `true` (`PlayerScreen.kt:190`) ve
her kaynak geçişinde sıfırlanıyor (`:735`). Proxy varyant filtrelemiyor
(`helpers.py:299-351`).

**Kaynak seçimi elle yazılı sıra + dil kuralı, puan yok.**
`_ONCELIKLI = [DiziPal, DiziMom, HDFilmCehennemi]` + alfabetik
(`resolve_sources.py:31-33`); sağlık dışında dinamik sinyal yok. `fav_providers`
seçime girmiyor (yalnız Gözat çip sırası, `BrowseScreen.kt:481`). `source_stats`
yalnız izlenme sayısı türetimi (`watch_store.py:417`). Engine günlüğünde DiziMom
14 bölüm bulup "oynatılabilir kaynak vermedi" ile bitiyor; sıradaki denemeler de
boş → puanlama olsa DiziMom o dizi için geriye düşerdi.

**Proxy darboğazları** (`stream/Public/Proxy/`):
- WARP başarısız olduğunda **negatif önbellek yok** (`helpers.py:57-69`): `four.pichive.online`
  her segmentte 403 → WARP → 403 döngüsü, stream günlüğü bununla dolu. Her segment iki
  upstream isteği + WARP gecikmesi.
- `force_proxy=1` olan 10 sağlayıcıda tüm segmentler ev upload'ından (`source_proxy.py:20,48`).
- ~~Segment cache tavanı 5 MB~~ — YANLIŞ bulgu: tavan zaten 20 MB
  (`segment_cache.py:190`), yorum satırı eski durumu anlatıyordu.
- Gövde manifest tespiti 1 MB tam `aread()` (`video.py:124-127`): akış başına gecikme.
- Ön-yükleme 3 segment, yalnız manifest anında (`video.py:16`); sürekli değil.

## Plan — sıra, bedel, kanıt kapısı

### Faz A — istemci (en yüksek getiri, en ucuz) · TV v0.2.9
1. `previewExo`'yu **tembel** yap: yalnız scrub başlayınca oluştur, scrub bitince
   `release`. Tek satırlık davranış değişikliği, iki kod çözücü sorunu biter.
2. `DefaultLoadControl`: `setBufferDurationsMs(min=30s, max=90s, playback=3s,
   rebuffer=6s)` + `setBackBuffer(20s)`. TV'de bellek bol, upload dar.
3. `DefaultTrackSelector` parametreleri: `setTunnelingEnabled(true)` (yalnız cihaz
   destekliyorsa; `onAudioSinkError` düşerse otomatik kapat ve prefs'e yaz),
   `setAllowVideoMixedMimeTypeAdaptiveness(true)`, `setExceedVideoConstraintsIfNecessary(true)`.
   Audio offload **denenmez** (HLS/TS'de gapless şartı, kazancı pil — TV'de gereksiz).
4. `LoadErrorHandlingPolicy`: segment hatasında 3 deneme / üstel bekleme, sonra kaynak
   geçişi — bugün tek 4xx doğrudan `onPlayerError` → kaynak atlıyor.
5. Kalite varsayılanı: `client_config`'e `default_quality` (auto/1080/720) ekle,
   admin'de alan aç, TV `setMaxVideoSize` ile uygula, kaynak geçişinde **koru**.
Kanıt: `client_log`'da `tampon boşaldı` sayısı bir bölüm boyunca 0; `gradlew testDebugUnitTest assembleDebug` yeşil.

### Faz B — proxy (ses kesintisinin ağ tarafı) · stream
1. WARP **negatif önbellek**: host WARP'ta da 403 aldıysa 10 dk `_warp_dead` kümesine,
   tekrar denenmez; kaynak zincirine "bu host ölü" sinyali gider.
2. Ön-yükleme sürekli: servis edilen segmentin sıradaki 2'si kuyruğa.
   (Cache tavanı maddesi düştü — zaten 20 MB.)
3. Manifest gövde tespiti: ilk 8 KB'ı `aiter_bytes` ile oku, `#EXTM3U` yoksa kalanı
   doğrudan akıt (1 MB tam okuma biter).
Kanıt: `smoke.sh` + `chain_scan.py --n 2` yeşil; stream günlüğünde WARP döngüsü 0.

### Faz C — kaynak puanlama (otonom seçim) · engine + stream
Sinyaller tek SQLite tablosu `source_score(plugin, host, ok_count, fail_count,
avg_first_byte_ms, last_ok_at)`:
- İstemci zaten bildiriyor: kaynak geçişi, `KAYNAK_YOK`, oynatma başladı → bunlar
  `client_log` yerine yapısal uç `POST /api/v1/source_event` olur.
- Puan = `+50 başarı / −50 başarısızlık` (AutoStream deseni), 7 gün yarı ömür,
  `fav_providers` +100 (Dean'in yıldızı en ağır oy). Hiçbir sağlayıcı yasaklanmaz.
- `ALTERNATIVE_ORDER` sabit liste → puana göre sıralı; sabit liste yalnız soğuk başlangıç.
- Dil kuralı korunur (dublaj önce), puan yalnız aynı dil grubu içinde sıralar.
Kanıt: `resolve: sıra — DiziPal(120) DiziMom(−30)…` günlük satırı; engine testleri.

### Faz D — Gemini (opsiyonel, ucuz)
Mevcut key ve model (`gemini-3.5-flash-lite`, `voice.py:107`) zaten panelde. İki
gerçek iş:
1. **Arama eşleştirme hakemi:** `resolve: arama — sonuç yok (2 varyant denendi)`
   çıktığında Gemini'ye başlık + sağlayıcının ilk 10 arama sonucu verilip
   `responseSchema` ile `{match_index, confidence}` istenir. "The Odyssey"/"Odyssey"
   türü varyant kaybı biter. Yalnız zincir boşa düştüğünde çağrılır (maliyet ~0).
2. **Bölüm/sezon doğrulama:** Dizilla'daki 124→31 türü sahte listeleri, sayfa
   metninden `{season, episode}` çıkararak ikinci görüş. İsteğe bağlı.
Ses/performans için Gemini'nin katkısı YOK — orası ExoPlayer ve proxy işi.

## Yapılmayacaklar
- Global WARP proxy (çıkış IP'si bazı kaynaklarda bloklu, `helpers.py:37-38`).
- Sunucu tarafı transcode/ffmpeg (imajlarda kapalı, ev CPU'su).
- Bulut motor (datacenter IP engeli, CLAUDE.md).
- Audio offload (bkz. A3).

## Dış kaynaklar
- Media3 HLS: https://developer.android.com/media/media3/exoplayer/hls
- Media3 track selection / tunneling / offload: https://developer.android.com/media/media3/exoplayer/track-selection
- Tunneled playback (Woodman): https://medium.com/google-exoplayer/tunneled-video-playback-in-exoplayer-84f084a8094d
- LoadControl tampon tıkanması: https://github.com/google/ExoPlayer/issues/9553
- AudioSink hatasında yeniden yapılandırma: https://github.com/google/ExoPlayer/issues/9588
- AutoStream puanlama (+50/−50, yasak yok): https://github.com/keypop3750/AutoStream
- HLS proxy prefetch/keep-alive deseni (EasyProxy): https://github.com/djsaikrishna/EasyProxy/pull/91
- Gemini yapısal çıktı: https://firebase.google.com/docs/ai-logic/generate-structured-output
- Gemini 3.1 Flash-Lite: https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite
