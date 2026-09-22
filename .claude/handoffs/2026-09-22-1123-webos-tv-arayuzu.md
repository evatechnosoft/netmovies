# DEVİR — 22 Eylül 2026, 11:23 · LG webOS TV arayüzü (/tv) + client-tv 0.9.15

**Dal:** netmovies `fix/general-stability` @ `66a1571` + **commit edilmemiş iş** (aşağıda)
**Sürümler:** TV/telefon APK **0.9.15** (915, derlendi·yayınlanmadı) · saat 0.1.18 · webOS ipk **0.1.4**
**Yerel:** `http://192.168.1.185:3310` · tünel `https://w.evaitec.com` · LG TV `192.168.1.175`

## Hedef
Dean'in LG 65A6500 televizyonunda (Android yok, webOS) NetMovies'i kumandayla izlemek.
Yan iş: Android TV'de izlerken Ayarlar'a çıkınca uygulamanın sıfırlanması.

## Durum — KANITLI
- **webOS kurulum yolu çalışıyor.** `ares-install` bu TV'de ÇALIŞMIYOR (ares-cli 3.2.6'nın
  ssh2'si RSA-SHA2 imzalıyor, TV yalnız `ssh-rsa` kabul ediyor → "All configured
  authentication methods failed"). Kurulum saf SSH ile yapılıyor:
  ```bash
  KEY=/c/Users/Deacjx/.ssh/evostv_plain     # şifresiz kopya; şifreli olan .enc uzantılı
  OPTS="-o StrictHostKeyChecking=no -o BatchMode=yes -o HostKeyAlgorithms=+ssh-rsa -o PubkeyAcceptedAlgorithms=+ssh-rsa"
  scp -i $KEY -P 9922 $OPTS <ipk> prisoner@192.168.1.175:/media/developer/temp/
  ssh -tt -i $KEY -p 9922 $OPTS prisoner@192.168.1.175 'luna-send-pub -i -f luna://com.webos.appInstallService/dev/install "{\"id\":\"com.evaitec.netmovies\",\"ipkUrl\":\"/media/developer/temp/<ipk>\",\"subscribe\":true}" & sleep 14; kill %1; exit'
  ```
  KANIT: `"state": "installed"`, `launch` → `"returnValue": true`.
- **`ssh -tt` ŞART.** TTY'siz `luna-send-pub` hiçbir çıktı vermiyor, RC=0 döndürüyor ve
  kurulum olmuyor. `/usr/bin/luna-send` root-only, prisoner'a kapalı; `luna-send-pub` kullanılır.
- **TV tarayıcısı Chrome 68** (UA kanıtı, 1920x1080, dpr=2). Ana web arayüzü orada ÖLÜ:
  `?.` sözdizimi `Uncaught SyntaxError: Unexpected token .` veriyor, 259 kullanım / 23 dosya,
  `/izle/...` oynatma sayfası dahil. Bu yüzden ana UI TV'ye uyarlanmadı.
- **`/tv` yazıldı ve TV'de OYNATIYOR.** Yeni: `stream/Public/Home/Routers/tv.py` +
  `Templates/pages/tv.html.j2` (tek dosya, ES2017, bağımlılıksız, D-pad).
  KANIT: TV DevTools'ta `ready=4 dur=5946.375 err=-`, `src=…/proxy/video?…` — Şeytan Çocuk oynadı.
  5 raf / 1042 kart: Devam Et · Yeni Filmler · Yeni Diziler · Türk Dizileri · Canlı TV.
- **GERİ tuşu düzeldi** (Dean doğruladı: "evet çalıştı"). `appinfo.json` →
  `disableBackHistoryAPI: true`; yedek olarak `popstate` de `geriGit()`e bağlı.
- **client-tv 0.9.15 derlendi.** `testDebugUnitTest assembleDebug` EXIT=0,
  `versionCode='915' versionName='0.9.15'` (aapt2 kanıtı).

## Durum — DOĞRULANMADI (inanç)
- Birikmeli sarma (10+10+10 → tek seek) ve "sunucudan devam" (`devamdanBasla`) TV'ye kuruldu
  ama Dean HENÜZ denemedi. Kod TV'de: `sarHedef` grep'i YENİ KOD dedi.
- client-tv 0.9.15'teki iki düzeltme cihazda denenmedi: manifest `configChanges` genişletmesi
  (BT klavye/kumanda bağlanınca Activity recreate olmasın) ve açılıştaki "kaldığın yer" kartı.
- webOS'ta dizi bölüm listesi ekranı hiç görülmedi (film yolu denendi).

## Kararlar
- Ana web arayüzünü Chrome 68'e derlemek (esbuild `--target=chrome68`) DEĞERLENDİRİLDİ, seçilmedi:
  Dean "sen buna özel uygulama yapmalısın" dedi. Ayrı `/tv` sayfası yazıldı, ana UI'ye dokunulmadı.
- Mi Box seçeneği YOK: LG'de kumanda eşleştirmesi gerekmediği için zaten webOS'a gelindi (Dean).
- Yeni oynatma ucu yazılmadı: `/api/v1/resolve_sources` `route_through_proxy` ile ZATEN
  oynatılabilir proxy adresi döndürüyor.
- webOS'ta hls.js yüklenmiyor; TV'nin kendi medya motoru m3u8'i açıyor.

## Tekrarlama / tuzaklar
- **`encoded_url`'i URLSearchParams'a verme.** Katalog değeri zaten quote_plus kodlu; ikinci
  kodlama (%3A→%253A) zinciri sessizce 0 kaynak yapıyor → ekranda "kaynak bulunamadı".
  KANIT: çift kodlu curl 0, tek kodlu curl 1 kaynak. Adres elle birleştirilir (`zincirYolu`).
- **Şablon değişince `docker compose restart` YETMEZ** — şablon imaja gömülü, `up -d --build stream`
  şart. Ardından `cloudflared --force-recreate` (netns pinli).
- **TV sayfayı `Cache-Control: no-store`a RAĞMEN önbellekliyor.** Uygulama kapat-aç bile eski kodu
  açıyordu; çözüm ipk 0.1.4'te: `location.replace(adres + "/tv?v=" + Date.now())`.
  Teşhiste `document.documentElement.outerHTML.indexOf('<yeni simge>')` ile sürüm doğrula.
- LG Developer Mode'daki kimlik alanı **LG Account e-postası** ister, Google user ID değil.
- TV DevTools: `appinfo.json` → `"inspectable": true`, port 9998,
  `http://192.168.1.175:9998/json/list`. CDP betikleri scratchpad'de `cdp.py` / `cdp_log.py`.

## COMMIT EDİLMEMİŞ — ilk iş bu
```
 M client-tv/app/build.gradle.kts                     0.9.14 → 0.9.15
 M client-tv/.../AndroidManifest.xml                  configChanges genişletildi
 M client-tv/.../MainActivity.kt                      açılışta "kaldığın yer" kartı
 M client-tv/.../data/ApiModels.kt                    ProgressRow.updated_at
 M client-tv/.../data/Library.kt                      sonKalinanYer()
 M client-webos/app/{appinfo.json,index.html}         0.1.4 · /tv · back · cache-buster
 M stream/Public/Home/Routers/__init__.py             tv router kaydı
?? stream/Public/Home/Routers/tv.py                   YENİ
?? stream/Public/Home/Templates/pages/tv.html.j2      YENİ
?? atv-kopru.log                                      çöp, silinecek
```

## TEK SONRAKİ EYLEM
Yukarıdakileri commit et (`atv-kopru.log` hariç). Sonra Dean'in geri bildirimi:
webOS'ta sarma birikmesi + kaldığın yerden devam; TV'de 0.9.15 (henüz yayınlanmadı —
`data/apk` + `app_update` + evaglass-releases + apps.json üçlüsü bekliyor).
