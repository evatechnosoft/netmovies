# DEVİR — 22 Eylül 2026, 16:09 · /tv arama + favori + Magic Remote

**Dal:** netmovies `fix/general-stability` @ `532d090` + **commit edilmemiş** `/tv` eklemeleri
**Sürümler:** APK 0.9.15 (derlendi·YAYINLANMADI) · saat 0.1.18 · webOS ipk **0.1.4** (TV'de kurulu)
**Yerel:** `192.168.1.185:3310` · tünel `w.evaitec.com` · **LG TV `192.168.1.175` (şu an KAPALI, ping yok)**

## Hedef
Dean'in LG televizyonunda (`/tv` arayüzü) izlediğini bulup listeye alabilmesi.
Bu oturumda: arama ekranı, Magic Remote imleci, favori ekleme + Favorilerim rafı.

## Durum — KANITLI
- `532d090` push'landı: `/tv` arayüzü, client-webos 0.1.4, client-tv 0.9.15 düzeltmeleri.
- Bu commit'ten SONRA `/tv`ye eklenenler sunucuda yayında (`docker compose up -d --build stream`
  + `cloudflared --force-recreate`), curl grep kanıtı: `aramaYap|sonucOdak|mouseenter` = 12,
  `favoriDegistir|is_favorite` = 4.
- `favorites/toggle` yanıt alanı **`is_favorite`** (`added` DEĞİL) — curl ile iki kez çağrılıp
  `true` → `false` görüldü. Test kaydı "TestKayit" temizlendi (son toggle `False`).
- Dean'in favori listesinde 10 içerik var (`/api/v1/favorites`), Favorilerim rafı bunları çekecek.
- Önceki oturumda Dean doğruladı: GERİ tuşu çalışıyor, dizi oynuyor, sarma/dondurma var.

## Durum — DOĞRULANMADI (TV kapalı, hiçbiri cihazda denenmedi)
- Arama ekranı (ana ekranda en üst raftan YUKARI → kutu, OK → ara, ızgarada gezinme).
  webOS sanal klavyesinin `q.focus()` ile açılacağı VARSAYIM.
- Magic Remote: `mouseenter` odak + `click` seçme (kartlar, arama sonuçları, bölüm satırları).
- Favori: OK **basılı tutma** (`o.repeat`) → toggle; kısa basış artık **keyup**'ta açıyor.
  Bu, açma yolunu değiştirdi — TV'de OK'un hiç açmaması riski burada.
- Birikmeli sarma ve "sunucudan devam" da hâlâ Dean tarafından denenmedi.

## Kararlar
- Ayrı favori tuşu yok: kumandada boşta renkli tuş yok, Android TV'deki "basılı tut" desenine
  uyuldu. Kısa/uzun ayrımı için açma işi keydown'dan keyup'a taşındı.
- "Gözat" (kaynak/kategori gezme) ekranı YAZILMADI — arama onun işini görüyor sayıldı.
  Dean isterse eklenecek.
- İzlenecekler/Takip rafları eklenmedi; yalnız Favorilerim.

## Tekrarlama / tuzaklar (önceki devirden geçerli)
- `encoded_url`'i URLSearchParams'a verme → çift kodlama → sessizce 0 kaynak.
- Şablon değişince `restart` değil `up -d --build stream`; ardından `cloudflared --force-recreate`.
- TV sayfayı `no-store`a rağmen önbelleklerdi; ipk 0.1.4 her açılışta `?v=<zaman>` ile gidiyor.
- webOS kurulumu: `ares-install` ÇALIŞMAZ (ssh-rsa), saf SSH + `luna-send-pub`, **`ssh -tt` şart**.
  Anahtar `/c/Users/Deacjx/.ssh/evostv_plain`, TV DevTools `http://192.168.1.175:9998/json/list`,
  CDP betikleri scratchpad'de `cdp.py` / `cdp_log.py`.
- TV'nin tarayıcısı Chrome 68: `?.` / `??` / `flat` / `allSettled` kullanma.

## COMMIT EDİLMEMİŞ
`stream/Public/Home/Templates/pages/tv.html.j2` — arama ekranı, fare desteği, favori
(OK basılı tut), Favorilerim rafı, keyup ile açma. Başka dosya değişmedi.

## TEK SONRAKİ EYLEM
TV açılınca Dean denesin: OK kısa basış AÇIYOR mu (keyup değişikliği en riskli nokta),
OK basılı tutma favoriye ekliyor mu, YUKARI arama kutusunu açıp klavye çıkıyor mu.
Sonuç ne olursa olsun `tv.html.j2` commit edilecek.
