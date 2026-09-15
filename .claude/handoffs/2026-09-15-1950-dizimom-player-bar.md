# DEVİR — DiziMom oynatma zinciri + oynatıcı alt barı

**Zaman:** 15 Eylül 2026, 19:50 · **Dal:** `fix/general-stability` @ `ab0cbf7` · **Oturum:** 3178259a
**Kirli:** 1 dosya — `.claude/handoffs/2026-09-04-1610-lan-warp-ota.md` silinmiş (eski devir, geri koymaya gerek yok)
**Push:** EDİLMEDİ (`3d880b1`, `3f753ed`, `6b3a622`, `7a98329`, `fcac4db`, `ab0cbf7` yerel)

## Hedef

Dean'in iki sorusu:
1. "Oynanabilir kaynak vermedi demekte favoriye aldığım dizilerim nedendir" →
   favorilerdeki iki yerli dizi (DiziMom · Daha 17, Altı Üstü İstanbul) hiç oynamıyordu.
2. Oynatıcı arayüzü: dakika yazma tuşu telefon düzeninde 4x4 olsun, altta açılsın;
   alt barda oynat + ileri/geri + bölüm geçme gezinilebilir olsun, bölüm listesine
   oradan girilsin, küçük ikonlarla; sarma sayfası koca ekranı kaplamasın, sol altta
   küçük çıksın.
3. Ek: "orası her ortamı taramalı, DiziMom DDizi vb denerken de bilelim — tek yerden
   eklemedim, o dizi olarak favorim."

## Durum — üçü de kodda ve doğrulandı

`fcac4db` — DiziMom zinciri · `ab0cbf7` — oynatıcı arayüzü + tarama kapsamı.

### Doğrulanmış (tool çıktısı var)

| İddia | Kanıt |
|---|---|
| DiziMom favorileri kaynak veriyor | "Daha 17" ve "Altı Üstü İstanbul" 1. ve son bölüm: 0 → **1 kaynak** |
| Gerileme yok | hdplayersystem kullanan diziler 1 → 1 (değişmedi) |
| Uçtan uca oynanıyor | proxy master **200** → varyant **200** → segment **200** |
| Tarama kapsamı | `kapsam` teşhisi: **12 sağlayıcı** (DDizi, FullHDFilmizlesene, JetFilmizle, M3UPlaylist artık içeride) |
| Yanlış eşleşme düzeldi | "Daha 17" → "Hızlı ve Öfkeli 2 Daha Hızlı Daha Öfkeli" eşleşmesi gitti |
| Testler | motor `Ran 40 ... OK` · gateway `Ran 132 ... OK` · `:app:testDebugUnitTest` OK |
| Kapı | `bash scripts/smoke.sh` → "SONUÇ: kapı YEŞİL" |
| TV APK | `:app:assembleDebug` EXIT=0 · 20.232.314 bayt · yeni dizeler dex'te ("Alt kumanda bar", "Dakika", "Ana sayfa") |

### DOĞRULANMADI — sıradaki iş bu

**Yeni oynatıcı arayüzü televizyonda hiç görülmedi.** Alt bar ve 4x4 tuş takımı yalnız
derlendi. Cihazda bakılacaklar aşağıda (SIRADAKİ İŞ).

## Kök nedenler ve gerekçe

**1. FirePlayer embed'inin iki adres biçimi var.** `fireplayer_sources`
(`engine/Plugins/__dizi_common.py`) yalnız `<origin>/player/index.php?...&do=getVideo`
deniyordu. `/tv/` altındaki kurulumlar (peacemakerst.com, hdstreamable.com) orada **404**
verip yalnız sayfanın KENDİSİNE (`<iframe>?do=getVideo`) cevap veriyor; üstelik linki
`securedLink`/`videoSource` yerine `videoSources[].file` içinde döndürüyorlar. İkisi de
denenip üç yanıt biçimi de okunuyor (`_fireplayer_stream`). hdplayersystem kök kurulum
olduğu için çalışıyordu — bu yüzden anime çalışıp yerli dizi çalışmıyordu.

**2. Proxy, referer reddeden CDN'e referer gönderiyordu.** Akış `video.twimg.com`
üzerinden geliyor; referer'sız 200, embed referer'ı ile **403**. Kaynak bulunuyor ama
oynatıcıda "Upstream Error: 403" çıkıyordu. `_REFERER_REDDEDEN_HOSTLAR`
(`stream/Public/Proxy/Libs/helpers.py`) — host listesi, imzalı adres referer istemiyor.

**3. Alternatif tarama KAPSAMI elle yazılmış listeden geliyordu.**
`ALTERNATIVE_ORDER` 9 ad; motorda 16 eklenti yüklü. Sıra listesi aynı zamanda kapsamı
belirlediği için DDizi/FullHDFilmizlesene/JetFilmizle/M3UPlaylist zincire hiç
girmiyordu — arama onları buluyor, çözümleme denemiyordu. `tum_saglayicilar()`
(`engine/Public/API/v1/Libs/tarama_sirasi.py`) kapsamı yüklü eklentilerden alıyor,
sırayı yine puan/öncelik listesinden. Yetişkin eklentileri `TARAMA_DISI`.

**4. Kapsam genişleyince yanlış eşleşme çıktı.** `_anlamli_kelimeler` 2 harften kısa
parçaları atıyordu: "Daha 17" → `{daha}` kalınca alakasız film eşleşme sayılıyordu.
Sayılar uzunluk sınırından muaf edildi + regresyon testi (`engine/tests/test_query_variants.py`).

**5. Arayüz.** `QuickPad` sağ alt dikey kutudan ALT BAR'a döndü (küçük vektör ikon +
altında ad, D-pad gezinir); süre/ilerleme barın üstünde, çünkü bar açıkken
`ControlsOverlay` çizilmiyor (ikisi de ekranın altına oturuyor, üst üste geliyordu).
AŞAĞI tuşu yeni `RemoteAction.OPEN_BAR`'a bağlandı (eskiden OPEN_SETTINGS'ti; Ayarlar
artık barın içinde bir düğme, tek giriş noktası). `SeekScreen` tam ekran + tek sıra
0-9 iken sol altta 300dp kutu ve telefon düzeni 4x4 oldu.

## Tekrarlama — ölen yollar

- **`localhost:3310/api/v1/resolve_sources?...&url=`** diye çağırma: uç `encoded_url`
  ister, `url` verilince **410** döner. `encoded_url` base64 DEĞİL, **tek kez**
  yüzde-kodlanmış düz URL (gateway `dict(request.query_params)` ile bir kez çözüyor).
  Base64 verilirse "UnsupportedProtocol: Request URL is missing an 'http://'" görürsün —
  kodda değil, çağrıda hata vardır.
- **`fuck_dmca` yanıtı 180 sn cache'li**: düzeltmeden hemen sonra aynı parametreyle
  çağırırsan ESKİ sonucu görürsün. Parametreyi değiştir (`&episode=0&mode=full`) ya da bekle.
- **Bash tool'unda ters bölü yiyor**: heredoc'a yazılan Python regex'lerinde `\\x` → `\x`
  oluyor ve "incomplete escape" veriyor. Python dosyasını `python - <<PY` ile ÜRETİP
  öyle çalıştır, ya da `chr(92)` kullan.
- **DDizi'yi "bozuk" sanma**: o eklenti yalnız resmi YouTube yayınlarını çözüyor
  (`_YOUTUBE` regex + `ytdlp_info`). Dizi sayfasında YouTube yerleşimi yoksa 0 kaynak
  döner — tasarım, hata değil. Teşhis satırı bunu zaten söylüyor.
- **Motor içinde script çalıştırırken** `cd /usr/src/KekikStreamAPI && PYTHONPATH=. python ...`
  şart; `docker exec -w` tek başına `ModuleNotFoundError: Plugins` veriyor.
- Önceki devirlerden geçerliliğini koruyanlar: `gh release create --target main`;
  Git Bash'te `MSYS_NO_PATHCONV=1 docker exec -w`; Kotlin dosyasını bash heredoc ile yazma.

## SIRADAKİ İŞ #1 — yeni oynatıcıyı televizyonda dene

APK hazır: `client-tv/app/build/outputs/apk/debug/app-debug.apk` (15 Eylül 19:24,
20.232.314 bayt). Telefondan aktarım: evaitecOTA → "Televizyona gönder" (kare kod).

1. Favorilerden **Altı Üstü İstanbul** veya **Daha 17** aç → oynamalı (asıl düzeltme bu).
2. **AŞAĞI** tuşuna bas → alt bar açılmalı; D-pad ile ⏮ · −5dk · −30sn · ▶ · +30sn ·
   +5dk · ⏭ · Bölümler · Dakika · Ayarlar · Ana sayfa · Kapat arasında gezilmeli.
   Süre ve ilerleme çubuğu barın üstünde görünmeli.
3. Bardan **Dakika** → sol altta 300dp tuş takımı; `1 2 3 ⌫ / 4 5 6 0 / 7 8 9 ▶ /
   ⏮ ⏭ 📑 ✕`. Görüntü kararmamalı. 45 yaz → ▶ → 45. dakikaya gitmeli.
4. `curl -s localhost:3310/api/v1/client_log` → `sunucu·kapsam` satırı denenen
   sağlayıcıların tamamını yazmalı; `ses · tampon boşaldı` hiç olmamalı.

Alt bar açılmıyorsa: AŞAĞI tuşu eşlemesi kullanıcı tarafından değiştirilmiş olabilir —
Ayarlar → Buton Eşleme → Aşağı ▼ → "Alt kumanda barı".

## SIRADAKİ İŞ #2 — saat 0.1.3'ü bilekte dene

`docs/HANDOFF.md` SIRADAKİ İŞ #1'den devralındı, bu oturumda dokunulmadı. Saat
uygulaması üç dağıtım yerinde yayında, bilekte hiç denenmedi.

## Not — v0.2.9 / OTA

TV APK bu oturumda yeniden derlendi ama **sürüm yükseltilmedi ve OTA'ya konmadı**:
cihazda denenmemiş sürüm televizyona "güncelleme var" diye düşmemeli (önceki devirdeki
karar). Cihaz denemesi yeşilse sürüm artırılıp release + `apps.json` vc 209 yapılır.
