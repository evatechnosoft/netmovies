# DEVİR — 18 Eylül 2026, 11:00 · telefon araması + dil rozeti

**Dal:** `fix/general-stability` @ `3a6bd2d` · 0 kirli dosya · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `14fae2c` · netmovies-tv/phone **0.6.0 (vc 600)**
**Saat:** değişmedi — `v0.1.13-poc`, evaitecOTA saat 0.1.11

## Bu oturumda ne yapıldı

Dean: "uygulamayı çalıştır testlerini yap… bütün eklentiler ve saat", ardından
"[dil] eklenelim belirtilsin içine girince ve posterde", ardından "arama kutusu
çok kötü… tek satır basınca bilgi kartı".

Üç sürüm çıktı (0.5.1 → 0.5.2 → 0.6.0), üç dağıtım yeri de her seferinde güncellendi.

## Doğrulanmış (tool çıktısı var)

- `bash scripts/smoke.sh` → kapı YEŞİL (son koşu 0.6.0 sonrası).
- `stream/tests` → **149 test OK** (container içinde, gerçek import grafiğiyle).
- `client-tv` `:app` + `:wear` `testDebugUnitTest` + `assembleDebug` → BUILD SUCCESSFUL.
- `chain_scan.py --n 2` → **ÖLÜ KAYNAK: 2**, ikisi de geçici (JetFilmizle ReadTimeout;
  KultFilmler 500 → aynı istek hemen ardından 200).
- Tünel `https://w.evaitec.com/api/v1/health` → 200 (oturum başında 530'du:
  cloudflared hiç ayakta değildi, `--profile tunnel` ile kaldırıldı).
- Dil etiketi canlı: DiziPal "Orijinal" → `{'rank': 2, 'label': 'orijinal dil'}`.
- Poster rozeti uçtan uca: `data/lang_memo.json` yazılıyor, `aggregate_new`
  yanıtında `Seni Tanıyorum -> ['ALT','ORJ']`, `Dark -> ['ALT','DUB']`.
- Zengin arama: `search_all?query=reacher&group=1` → 6 satır, 3.1 sn, ilk satır
  `Reacher | 2022 | 8.1 | sez 4 bol 28 | [HDFilmCehennemi, DiziMom, DiziYou, Dizilla]`.
- Üç APK da GitHub'dan indirilip sha256 + boyut ile karşılaştırıldı, eşleşti.

## Doğrulanmamış (cihazda görülmedi)

0.5.1/0.5.2/0.6.0'ın **hiçbiri** TV'de ya da telefonda açılmadı. Özellikle:
favoriden bölüm devamı, poster rozetinin yerleşimi, telefon arama ekranının
dokunma davranışı ve bilgi kartının yüksekliği.

## Kararlar ve gerekçe

- **Poster rozeti için zincir koşturulmaz.** Dil ancak `resolve_sources`'tan sonra
  bilinir; kart çizerken o dakikalar sürer. Çözüm: çözümlemenin yan ürünü
  `lang_memo.json`'a yazılır, `aggregate_new` bilinen başlıklara `lang` ekler.
  Hiç açılmamış içerik rozetsiz kalır — bu kabul edilen sınırdır, hata değil.
- **Yeni uç açılmadı.** Bilgi kartı mevcut `load_item` + `resolve_sources`'u
  çağırıyor. `search_all` zenginleşti ama `group=1` OPSİYONEL: TV Gözat ve web
  kumanda düz listeyi kullanmaya devam ediyor, sözleşme kırılmadı.
- **Dil rank'i 4'e çıktı** (0 dublaj · 1 TR altyazı · 2 orijinal dil · 3 bilinmiyor).
  TV'de `LanguageTag.rank` varsayılanı 2→3 yapıldı; web JS kendi ad-tabanlı
  tespitini kullanıyor, rank'e bakmıyor → etkilenmedi.
- **Telefon ekranında tv-material bileşeni kullanılmadı** (Text hariç): odak
  halkası ve D-pad ölçüleri parmakla sürülen ekranda yanlış durur.

## Tekrarlanmayacak hatalar

1. **`load_item`'a kodlu adres gönderme.** `oge["url"]` quote_plus kodlu; httpx
   parametreyi bir kez daha kodluyor, motor `%253A` görüp **500** dönüyor.
   Sunucu içinden çağrıda `unquote_plus` ile HAM gönder. (İstemci kodlu gönderir,
   çünkü kodlama onun tarafında bir kez olur.)
2. **Testi canlı `/data` deposuna bağlı bırakma.** `test_api_contract` gerçek
   `lang_memo.json`'u okuyup sözleşme testini kırdı; `LANG_MEMO_PATH` tempdir'e
   izole edildi. Yeni kalıcı depo eklerken ortam değişkeni + test izolasyonu şart.
3. **`docker exec -w /usr/src/Stream`** Git Bash'te `Cwd must be an absolute path`
   verir → `MSYS_NO_PATHCONV=1` önekle.
4. **Stream rebuild tüneli düşürür** (cloudflared netns'i stream'e pinli):
   her `up -d --build stream` çağrısına `cloudflared`'i de ekle.

## Sıradaki tek iş

**Saati 0.1.4'ten kurtar — sıra önemli:** önce saatte **evaitecOTA 0.1.11**
(345 KB), sonra **NetMovies Mini 0.1.13** (22,2 MB). Ters sırada 22 MB'ı hâlâ eski
yükleyici indirir ve aynı duvara çarpar. Bu iş yalnız Dean'in bileğinde yapılabilir.

Sonrasında: 0.6.0'ı telefona kurup arama ekranını gör; bölüm **süresi** satırda
yok (her sonuç için ayrı TMDB detay isteği gerekirdi) — istenirse bilgi kartına
tek istekle eklenir.
