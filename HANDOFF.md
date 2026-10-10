# Handoff: Paylaş → TV 0.9.67 + ses kayması
> 2026-10-10 14:20 · `fix/general-stability` @ `b0c2e5d`-sonrası (bu handoff commit'i) · origin ile eşit

## Goal
Reklamsız TV (client-tv, Mi Box) + ZimaOS sunucu. Kurallar `CLAUDE.md`. Bu oturum: Dean "Daha 17'de ses geriden geliyor, bir daha karşılaşmayalım". Gerekçe ve ölçüm yöntemi: hafıza `ayri-ses-kaymasi.md`.

## State
- 0.9.67 üç yerde (sha `7d210e58…`, yerel OTA + GitHub `v0.9.67-poc` (target fix/general-stability) + evaglass `netmovies-tv-v0.9.67` & `apps.json` vc 967 `3bf61de`; üç indirme sha eşleşti, Pages 0.9.67 gösteriyor). Mi Box'a KURULMADI (kendisi OTA'dan görür).
- Paylaş → TV (hafıza `paylas-tv-akisi.md`): sunucu web→play eşler (`stream/Public/API/v1/Libs/paylas_hedefi.py`, başlık arka planda `remote.py::_baslikla_kuyruga`), bağlantısız yazı TV araması, sayfa açıkken yazı sayfadaki kutuya / adres ise açılır, sayfada tam ekran video + %130 yazı + reklam konağı engeli + yükleniyor şeridi. Emülatör (evabench_shot) kanıtı: youtu.be paylaşımı → oynatıcı "Daha 17 (19. Bölüm)" posterli; duckduckgo sayfası açıldı; Vikipedi'de "Daha 17" kutuya yazılıp arandı; "tr.wikipedia.org" yazısı sayfayı açtı; "Reacher" paylaşımı → Gözat araması. Testler: stream 241, engine 77, TV unit (Paylas 3, WebEkrani 3) yeşil.
- unverified: DuckDuckGo'da yazma "ok" dönüyor ama kutuda görünmüyor (ağır JS); HTML5 tam ekran ve reklam engeli cihazda denenmedi; Fold'da paylaş menüsü Dean'de denenmedi.
- WARP 10 Eki sağlıksızdı (DNS %91 zaman aşımı) → `docker restart netmovies-warp` ile healthy; YouTube yt-dlp artık önce ev bağlantısı (`a76286c`).
- Kök neden kaynakta: DiziPal (sn.dplayer82) ayrı ses playlist'i 8640,07 sn, görüntü 8628,17 sn (oran 1,00138) → ses doğrusal geride (~9 sn 1:49'da). Cihaz/tunneling suçsuz.
- `c099fa7` + `7279fc1` push'lu, ZimaOS'ta canlı (engine/stream `.Created` 2026-10-10T07:03Z). Akış: engine `hls_av_orani` (`engine/Public/API/v1/Libs/kisa_klip.py`) ayrı sesli master'da EXTINF toplamlarını kıyaslar → kaynağa `av_oran` → gateway `source_proxy.py` zorla proxy + `&av_oran=` → proxy (`stream/Public/Proxy/Libs/av_esitle.py`) görüntü varyantı EXTINF + TS görüntü PES PTS/DTS × oran; ses dokunulmaz. `language.py` kayık kaynağı sona alır.
- Canlı kanıt: Daha 17 kaynak sırası YouTube, DiziMom, DDizi, DiziPal; proxy'den ses/görüntü toplamı 8640,07 = 8640,07; YouTube'a göre desync 3000/6500/7800 sn'de −0,53/−0,63/−0,56 sn (önce 3,6→8,8 büyüyordu). Engine test 6/6, stream 236/236, smoke YEŞİL, w.evaitec.com 303.
- unverified: kalan ~0,5 sn sabit fark kaynakta mı, ölçüm artefaktı mı. Dean'in TV'de gözle teyidi yok (bölümü düzeltmeden önce bitirdi).
- Önceki oturumdan açık, Dean'den geri bildirim bekleyen: saat 0.1.24 fare yönü/hızı, TV 0.9.66 Paylaş → TV'de aç, 0.9.64 klavye. Ayrıntı git `1801110:HANDOFF.md`.

## Next
0. Dean'in Fold'da Paylaş → TV denemesi (YouTube/dizi linki, düz yazı, sayfada yazı) geri bildirimi; DuckDuckGo tipi sayfada yazı tutmazsa InputConnection/klavye olayıyla yaz.
1. Dean bir sonraki ayrı-sesli kaynakta ağız-ses uyumunu söylesin; uyumsuzsa `ssh zima "docker logs netmovies-engine 2>&1 | grep 'oran '"` ile oranı gör, sabit ofset gerekiyorsa `av_esitle.ts_olcekle`'ye ekle.
2. Muxed (tek dosya) veya fMP4 kaynakta kayma şikâyeti gelirse kapsamı genişlet (şu an `ts_olcekle` fMP4'te no-op, muxed ölçülmüyor).

## Don't repeat
- A/V'yi ffmpeg `-ss` + `-c copy` ile ölçme: akışları bağımsız kaydırır, 10–18 sn sahte sonuç verdi. Ham segmentleri PTS'iyle indir; YouTube sesiyle xcorr, sahne kesmesiyle görüntü (scratchpad `raw.py` mantığı).
- YouTube ses segmentlerinin PTS'i 0'dan başlar: zamanı playlist EXTINF toplamından al.
- DDizi'nin bu bölümdeki mp4'ü 60 sn klip — referans olamaz.
- TV oynarken rebuild yok: `ssh zima "docker logs --since 2m netmovies-stream 2>&1 | grep -c remote/state"` 0 değilse bekle; `/tmp/nm_deploy.sh` (ZimaOS) boşta iki kez görünce pull + build + tünel recreate yapar.

## Verify
```
git rev-parse --short HEAD     # 7279fc1 veya handoff commit'i
bash scripts/smoke.sh | tail -1   # kapı YEŞİL
ssh zima "docker inspect -f '{{.Created}}' netmovies-stream"   # 2026-10-10T07:03Z veya sonrası
```

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies, dal fix/general-stability. 10 Ekim: TV 0.9.67 üç yerde — Paylaş → TV (video oynatıcıda, yazı aramada/sayfa kutusunda, hafıza paylas-tv-akisi.md). Ayrıca: ayrı ses playlist'li HLS kaynaklarda (DiziPal Daha 17) görüntü sesten %0,14 kısaydı, ses giderek geride kalıyordu; engine av_oran ölçüyor, proxy görüntü PTS/EXTINF'i esnetiyor (stream/Public/Proxy/Libs/av_esitle.py), kayık kaynak sona alınıyor — canlı, ölçümle doğrulandı; kalan ~0,5 sn sabit fark doğrulanmadı.
Önce HANDOFF.md oku, Verify bloğunu koş, hafıza ayri-ses-kaymasi.md'ye bak.
Sıra: 1) Dean ağız-ses geri bildirimi → gerekirse sabit ofset 2) önceki açıklar: saat fare yönü, Paylaş → TV'de aç, klavye geri bildirimi.
Yeni iş açma; TV oynarken rebuild yapma.
```
