# Samsung akıllı monitör (Tizen) + genel iyileştirme planı — 24 Eylül 2026

Persona: `.claude/agents/web-tv.md`. Android TV istemcisi kapsam dışı (o `tv-ux`'un işi).

## Cevap: evet, ucuz yoldan
Samsung akıllı monitörler (M5/M7/M8) Tizen TV profili çalıştırır; uygulama modeli web'dir
(`.wgt` = HTML/JS). LG için yazdığımız `/tv` sayfası zaten "eski TV tarayıcısı, D-pad,
bağımlılıksız" hedefiyle yazıldı. Tizen = **yeni kabuk + üç tuş kodu**, yeni arayüz değil.

| Tizen sürümü (yıl) | Chromium | `/tv` (ES2017) |
|---|---|---|
| 4.0 (2018) | 56 | sınırda |
| 5.5 (2020, ilk M5/M7) | 69 | çalışır (webOS'taki 68 ile aynı sınıf) |
| 6.5+ (2021+) | 85+ | rahat |

Tablo Samsung dokümanından — **cihazda doğrulanmadı**. Monitörün modeli/Tizen sürümü Faz 0'da öğrenilir.

## Faz 0 — Cihazı tanı
**Bulundu (24 Eylül, `http://192.168.1.184:8001/api/v2/`):** 43" Smart Monitor M7,
`LS43BM700UPXUF`, model kodu `22_NIKEL_SMT` (2022 → Tizen 6.5, Chromium 85 — sürüm doğrulanmadı),
4K, Wi-Fi, `developerMode: 0`, `DMP_DRM_WIDEVINE: false` (bizim HLS'e engel değil).

Kalan kontrol:
- Model adı (arkadaki etiket: `LS32BM70…` gibi) ve Ayarlar → Destek → Bu Cihaz Hakkında.
- Monitör tarayıcısında `http://192.168.1.185:3310/tv` aç. Raflar geliyor ve film oynuyorsa
  kabuk yazmadan bile kullanılabilir; Faz 2 yalnız "ikon + doğrudan açılış" kazancıdır.

## Faz 1 — `/tv`'ye Tizen tuşları (sunucu tarafı, tek dosya)
`stream/Public/Home/Templates/pages/tv.html.j2`:
- `TUS.GERI` yanına Tizen GERİ `10009` (webOS 461 korunur).
- Açılışta `if (window.tizen) tizen.tvinputdevice.registerKeyBatch([...])` — MediaPlay,
  MediaPause, MediaPlayPause, MediaFastForward, MediaRewind, MediaStop. Kayıt yoksa bu tuşlar sayfaya hiç gelmez.
- Ana ekranda GERİ → `tizen.application.getCurrentApplication().exit()` (webOS'ta sistem kapatıyor, Tizen'de uygulama kapatmalı).
- Kanıt: smoke yeşil + webOS'ta GERİ hâlâ çalışıyor (regresyon yok).

## Faz 2 — `client-tizen/` kabuğu
- `config.xml`: `tizen:application` id, `tizen:profile name="tv-samsung"`, `access origin="*"`,
  `tizen:privilege …/internet`, `tizen:setting screen-orientation="landscape"`.
- `index.html`: `client-webos/app/index.html` ile **aynı** adres seçici. İkisini elle
  senkron tutmamak için paketleme betiği webOS kopyasını alır; ayrı düzenleme yok.
- Paket: Tizen Studio CLI → `tizen package -t wgt -s <profil>`. İmza için Samsung
  Certificate Manager'da yazar + dağıtıcı sertifikası (Samsung hesabı, cihaz DUID'ine bağlı).

## Faz 3 — Kurulum
1. Monitör: Uygulamalar → kumandada `1 2 3 4 5` → Developer Mode ON, Host PC IP = bu PC → yeniden başlat.
2. PC: `sdb connect <monitör-ip>` → `tizen install -n NetMovies.wgt -t <cihaz>`.
3. Kanıt: `tizen run` + uzak DevTools'ta `video.readyState=4`.
- Risk: bazı akıllı monitörlerde Developer Mode menüsü kapalı olabilir (doğrulanmadı). Kapalıysa
  Faz 0'daki tarayıcı yolu + ana ekrana yer imi kalıcı çözümdür.
- webOS'tan fark: Tizen dev kurulumu 1000 saatte silinmez (doğrulanmadı), sertifika süresi geçerli.

## Uzman gözüyle — kullanılabilirlik için asıl öncelikler
Sıra "Dean'in izlerken yaşadığı acı"ya göre; Tizen bunların 3.'südür.

1. **Doğrulama borcunu kapat.** 0.9.21–0.9.24 Mi Box'ta hiç denenmedi; yeni cihaz
   eklemeden önce var olan cihazda kanıt. Yeni iş açmak borcu büyütür.
2. **Web-TV körlüğü:** `/tv` hata/tampon olayını `client_log`'a yazmıyor (Android TV yazıyor).
   LG/Samsung'da "oynamadı" dendiğinde elde kanıt yok. `video` `error`/`waiting`/`stalled`
   olaylarını mevcut `/api/v1/client_log` ucuna yolla — ~15 satır, iki platformu birden görür.
3. **Kayan sunucu adresi:** sarmalayıcıda `192.168.1.185` sabit; adres DHCP ile kayıyor
   (hafıza `dhcp-kayan-sunucu-adresi`, bir kez 0.29'a gitti). Kod yerine **modemde DHCP
   rezervasyonu** — sıfır satır, üç istemcinin (Android, LG, Samsung) hepsini düzeltir.
4. **Kaynak ölmeden haber:** `chain_scan.py`'yi gece bir kez çalıştır, kırmızı sağlayıcıyı
   `source_score`'a düşür. "Kaynak bulunamadı"yı Dean izlerken değil gece görürüz.
5. **Altyazı boyutu** (tv-ux backlog) — 3 m'den Türkçe altyazı okunmuyorsa diğer her şey boşa.
6. **Oynatıcı 2 kararı:** onaylanınca eski `PlayerScreen.kt` (~2900 satır) silinir; iki oynatıcıyı
   paralel taşımak her düzeltmeyi iki kez yazdırıyor.

Bilerek yapılmayanlar: Tizen için native (AVPlay) oynatıcı — `<video>` HLS'i açarsa gereksiz;
açamazsa Faz 1 kanıtı gösterir, o zaman eklenir. Samsung Store yayını — tek kullanıcı, gereksiz.

## Başarı kriteri
Samsung monitörde ikondan açılır, LAN'dan PIN'siz, Devam Et'ten bir filmi GERİ/OK/sarma
ile kumandayla izler; `client_log`'da oturum görünür; LG'de regresyon yok.
