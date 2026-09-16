# Handoff: TV/saat sürümleri cihazda denenmedi

> 2026-09-16 · `fix/general-stability` @ `7d69f58` · 0 kirli dosya
> Yan repolar: `evaitec-appkit` @ `bd74499` · `evaglass-releases` @ `edd638c` (ikisi de temiz)

## Goal

NetMovies'in televizyon ve saat istemcilerini Dean'in bildirdiği hatalardan
temizlemek. Bu oturumda on bir kök neden bulundu ve düzeltildi, dört uygulamanın
yeni sürümü yayınlandı. **Hiçbiri cihazda denenmedi** — sunucu tarafı kanıtlı,
ekran değil.

Gerekçe zinciri ve oturum günlükleri: `docs/HANDOFF.md` (uzun tarihçe),
`.claude/handoffs/2026-09-16-1747-tv-wear-ota-round.md` (bu oturumun tam dökümü).

## State

Yayındaki sürümler, hepsi doğrulandı:

| Uygulama | Sürüm | vc |
|---|---|---|
| NetMovies TV | 0.3.8 | 308 |
| NetMovies Mini (saat) | 0.1.8 | 108 |
| evaitecOTA TV | 0.1.12 | 13 |
| evaitecOTA saat | 0.1.10 | 6 |

- Kapı testi YEŞİL (`bash scripts/smoke.sh`), manifest proxy'den indi
- Yerel OTA uçları: tv `v0.3.8-poc`, wear `v0.1.8-poc`
- Üç repo da temiz ve push edilmiş

Sunucu tarafında canlı olan iki düzeltme (TV güncellemesi gerektirmez):
- Proxy, tek kullanımlık oynatma adresi 403 dönünce son iyi manifeste düşüyor
- DiziMom/Dizilla poster ve katalog düzeltmeleri (Dizilla 5 → 99 öğe)

## Next

1. **Saate NetMovies Mini 0.1.8'i koy — ADB'den ÖNCE telefondan gönder.**
   Telefona evaitecOTA kur → aç → "Bağlı saate" → evaitecOTA 0.1.10 gönder →
   sonra NetMovies Mini 0.1.8. Kod: `appkit/transfer/ApkSender.kt`, telefon
   arayüzü `ota-mobile` (`action_send_to_watch`). Saatin internete çıkmasına da
   kablosuz hata ayıklamaya da gerek yok.
   Son çare ADB: `bash scripts/saat-kur.sh` — 16 Eylül denemesinde saat
   bulunamadı, bilekte kablosuz hata ayıklama kapalı. Makine ağı `192.168.1.x`.

2. **Televizyonda evaitecOTA'yı 0.1.12'ye güncelle, sonra NetMovies 0.3.8'i kur**
   ve şu beşini dene:
   - Film aç, SAĞ'ı 3 sn tut → bırakınca tek seferde gitsin, fazladan atlama
     olmasın. SAĞ'a 3 kez hızlı bas → +30 sn. Sarma sürerken OK → orada dursun.
   - Reacher aç → `Devam et — S4B8 · …` yazmalı, "123. bölüm" değil.
     Bölüm seçicide SEZON seçilebilmeli.
   - Kaldırılmış bölümü olan bir dizi aç → zincirleme atlama OLMAMALI,
     "Bu bölüm sağlayıcıda yok" yazmalı.
   - Ayarlar → kaynak tek satır, basınca alt sayfa. Ajanda → üç adım.
   - Kesilme tekrarlarsa: `curl -s localhost:3310/api/v1/client_log`

3. Kalan kozmetik iş: poster kartında D-pad ile gezilebilir ikon şeridi,
   favori/takip düğmelerini dışarı alma, uzun basmada dörtlü menü
   (`docs/HANDOFF.md` içindeki "kart aksiyonları" listesi).

## Don't repeat

- **Tuş tekrarı başına `exo.seekTo` çağırma** — hedefte biriktir, tek seek yap
- **D-pad SOL/SAĞ'a çift basış atama** — tek basışı 300 ms bekletir
- **İzleme kaydına liste indeksi yazma** — `episodeRef` ile `S4B8` yaz
- **Poster için `first_attr(..., "src")` yazma** — `poster_attr` kullan,
  tembel yüklemede `src` yer tutucudur
- **CSS seçicide duyarlı sınıf adına bel bağlama** (`grid-cols-3` → `sm:grid-cols-3`)
- **`docker compose restart stream` Python değişikliğini ALMAZ** — kod imajda,
  `up -d --build stream` şart. Restart yalnız ağ geçidi önbelleğini temizler
- **Eklenti düzeltmesi hemen görünmez** — `/get_main_page` 30 dk, `/aggregate_new`
  10 dk önbellekli; doğrulamadan önce stream'i yeniden başlat
- **Bash heredoc ile Kotlin/XML yazma** — tırnak ve `\n` kaçışları bozuluyor;
  Write ile `.py` yaz, onu çalıştır (bu oturumda iki kez ısırdı)
- **Saate kurulum için ADB'den başlama** — telefondan gönderme hazır
- **evaitecOTA "abort" için `remote.py`'a bakma** — kurulum oturumu sorunuydu
- **Çift odak hedefi** — aynı `FocusRequester`'ı iki öğeye bağlamak D-pad'i öldürür
- `gh release create` `--target main` olmadan release listeye düşmez
- Git Bash'te `docker exec -w /usr/src/Stream` → başına `MSYS_NO_PATHCONV=1`

## Read first

1. `.claude/handoffs/latest.md` — bu oturumun kök nedenleriyle tam dökümü
2. `docs/HANDOFF.md` — proje tarihçesi, ölen yollar, sürüm günlüğü
3. `CLAUDE.md` — çalıştırma, doğrulama komutları, kullanıcı tercihleri

## Verify

```bash
git rev-parse --short HEAD      # beklenen: 7d69f58 — değilse: git log 7d69f58..HEAD --oneline
git status --porcelain | wc -l  # beklenen: 0
bash scripts/smoke.sh           # beklenen: kapı YEŞİL
curl -s "localhost:3310/api/v1/app_update?target=tv"    # beklenen: v0.3.8-poc
curl -s "localhost:3310/api/v1/app_update?target=wear"  # beklenen: v0.1.8-poc
```

Tünel 530 dönüyorsa: `docker compose --profile tunnel up -d` — cloudflared ağ ad
alanı stream'e pinli, stream her yeniden kurulduğunda tünel kopuyor.

## Açık, doğrulanmamış

- Saatteki yay listesi ve düğme yerleşimi cihazda görülmedi; sabitler masabaşı,
  kodda `ponytail:` ile işaretli
- Zincirleme atlama eşiği 90 sn masabaşı seçildi — gerçekten kısa bir bölüm
  yanlış elenebilir
- Dizilla `/tum-bolumler` (5) ve `/dublaj-bolumler` (0) zayıf — eklenti hatası
  DEĞİL, sunucudan gelen HTML'de o kadar dizi bağlantısı var
- "Kaynak denemesi 4/9'dayken kendiliğinden Reacher açıldı" — kuyruk uçları temiz,
  sessiz geçişin bilinen yolu yok; `client_log`'da artık açılış sebebi yazıyor
- Telefon kumandasının `play` komutu hâlâ 0 tabanlı sıra gönderiyor (`remote.py`)

## <yeniden başlangıç> promptu (yapıştır)

```
NetMovies projesinde çalışıyoruz (D:\projects\netmovies, dal fix/general-stability).
Geçen oturumda on bir kök neden düzeltildi ve dört uygulamanın yeni sürümü
yayınlandı: NetMovies TV 0.3.8, NetMovies Mini saat 0.1.8, evaitecOTA TV 0.1.12,
evaitecOTA saat 0.1.10. Hepsi üç dağıtım yerinde canlı ve sunucu tarafı kanıtlı,
ama HİÇBİRİ cihazda denenmedi. Üç repo da temiz ve push edilmiş.

Önce HANDOFF.md'yi oku ve Verify bloğunu çalıştır.

Öncelik sırası:
1. Saate NetMovies Mini 0.1.8'i koy — ADB'den ÖNCE telefondaki evaitecOTA'nın
   "Bağlı saate → Saate gönder" yolunu kullan.
2. Televizyonda evaitecOTA 0.1.12 → NetMovies 0.3.8 kur, HANDOFF.md'deki beş
   maddelik denemeyi yap.
3. Dean'den yeni hata gelirse onu önceliklendir.

Yeni iş açma, HANDOFF.md'deki Next listesinin dışına çıkma. Bir şey bozuksa
kök nedeni bul, semptomu yamalama. Her iddianın arkasında komut çıktısı olsun.
```
