# DEVİR — 18 Eylül 2026, 12:36 · izlenmemiş listesi + saat küçültme

**Dal:** `fix/general-stability` @ `4733e73` · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `d08e167`
**Sürümler:** TV/telefon **0.6.4 (vc 604)** · saat **0.1.15 (vc 115)** · evaitecOTA saat 0.1.11

## Amaç

Dean'in bugün bildirdiği her şeyi cihazda çalışır hâle getirmek: oynatıcı
düzeltmeleri, telefon araması, dil rozeti, saatin yavaş indirmesi, ajandanın
işe yaramaması.

## Bugün çıkan sürümler (hepsi üç dağıtım yerinde, her APK sha256+boyut ile doğrulandı)

- **0.5.1** favoriden açılan dizi kaldığı bölümden devam · gateway bayat bağlantı tekrarı
- **0.5.2** "orijinal dil" rank'i · poster DUB/ALT/ORJ rozeti · menüde dil özeti
- **0.6.0** telefonda gerçek arama ekranı (tek satır + bilgi kartı), sıralama sunucuda
- **0.6.1** Bölümler paneli · alt bar kaydırma · odak dönüşü
- **0.6.2** sonraki bölüm kartı 90 → 70 sn
- **0.6.3** panelde "Bölümler yükleniyor…" satırı
- **0.6.4** Ajanda'da **İzlemediklerim** adımı
- saat **0.1.14** (R8, 850 KB — AÇILMADI) → **0.1.15** (5,3 MB, kurallar düzeltildi)

## Doğrulanmış (tool çıktısı ya da Dean'in cihaz onayı)

- `smoke.sh` YEŞİL (son koşu 0.6.4 öncesi rebuild sonrası) · `stream/tests` **153 OK**
- `/api/v1/unwatched` canlı **8 bölüm** döndü (A.B.İ. S2B2, Dead City S3B8 …)
- `search_all?group=1` 6 satır / 3.1 sn · dil rozeti katalogda görünüyor
- **Dean: "oldu hızlandı"** — saat indirmesi (0.1.14, R8 + Wi-Fi yolu)
- saat APK imzası değişmedi: `20319a76…` (apksigner)

## Doğrulanmamış

- TV 0.6.1–0.6.4 cihazda **görülmedi** (Dean'in fotoğrafları hangi sürüm belirsiz).
- saat **0.1.15 açılıyor mu** bilinmiyor: 0.1.14 açılmamıştı, kurallar düzeltildi
  ama wear'da birim test yok (`testDebugUnitTest` NO-SOURCE). Açılmazsa
  `isMinifyEnabled=false` ile 23 MB'a dön — Wi-Fi düzeltmesi sayesinde o boyut
  bile artık LAN'dan iniyor.

## SIRADAKİ TEK İŞ — bölüm listesi en zengin sağlayıcıdan

Bugün **iki kez** ısırdı: "sezon 2'de 1 bölüm", "son bölüm S4B4 ama bu hafta S4B8".
Kod değil veri: aynı dizi için HDFilmCehennemi 7/28 bölüm veriyor, DiziMom ve
Dizilla 20/32. Kart hangi sağlayıcıdansa liste onunki.

Taslak: `/api/v1/episodes_best?title=&plugin=&encoded_url=` → `search_all`
mantığıyla aynı başlığı tüm sağlayıcılarda bul, her birinde `load_item`, EN ÇOK
(sezon,bölüm) çiftini taşıyanı döndür (hangi sağlayıcı olduğunu da söyle).
İstemci Bölümler sekmesi açılınca çağırır; liste daha zenginse `episodes`'u
değiştirir ve `aktifPlugin/aktifUrl` state'ine geçer — `resolve_sources` zaten
plugin+url+episode alıyor, sözleşme değişmiyor. Oynatıcı açılışını yavaşlatmamak
için load_item yolunda DEĞİL, yalnız panel açılışında çağrılmalı.

## Tekrarlanmayacak hatalar

1. **`load_item`'a kodlu adres gönderme** (sunucu içinden): httpx bir kez daha
   kodluyor, motor 500. `unquote_plus` ile ham gönder.
2. **Testi canlı `/data` deposuna bağlama**: `LANG_MEMO_PATH` tempdir'e izole edildi.
3. **`docker exec -w`** Git Bash'te `MSYS_NO_PATHCONV=1` ister.
4. **Stream rebuild tüneli düşürür**: `up -d --build stream cloudflared` birlikte.
5. **Panel içi liste yazmak yetmez, GİRİŞLERİ de çevir** (0.5.0'da liste panele
   alınmıştı, üç giriş hâlâ tam ekranı açıyordu).
6. **Odak efektinin anahtarları eksikse D-pad ölür**: `showStartPanel` yoktu,
   tam ekran liste kapanınca sarma tuşları hiçbir yere gitmiyordu.
7. **Sabit genişlikli şerit TV'de taşar**: 11×64dp alt bar 640dp ekrana sığmıyor.
8. **R8 açarken uygulama kodunu koru**: `**$$serializer` için yalnız
   keepclassmembers yetmez, sınıfın kendisi ve `Signature` üst verisi de gerekir.
   `android.enableR8.fullMode=false`.
9. **Modül monkeypatch'i yerine saf fonksiyon test et**: `watch_store.get_progress`
   yaması testte tutmadı; ayrıştırıcı `ref_coz` olarak ayrılınca test netleşti.

## Bilinen sınır (düzeltme değil, bilgi)

TMDB sezon numaralandırması sağlayıcılarınkinden farklı olabiliyor: Dead City'de
TMDB "S3", sağlayıcılar "S2" diyor. İzlemediklerim listesi doğru bölümleri
gösteriyor ama etiket sağlayıcıdakiyle birebir tutmayabilir.
