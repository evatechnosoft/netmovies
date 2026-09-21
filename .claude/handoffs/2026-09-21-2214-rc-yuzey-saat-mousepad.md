# DEVİR — 21 Eylül 2026, 22:14 · /rc yüzey tuşu + saat mousepad 0.1.18 YAYINDA

**Dal:** netmovies `fix/general-stability` @ `be1ec24` (push'landı, PR yok — dal tek kaynak)
**Sürümler:** TV/telefon 0.9.14 (914) · saat **0.1.18 (118)**
**Yerel adres:** `http://192.168.1.185:3310` · tünel `https://w.evaitec.com`

## Hedef (bu oturum)
Dean: "posterde basılı tutma eskiden ekranda gezinme kipini açıyordu, şimdi açmıyor" +
"saatte posterden bağımsız düğmeyle mousepad".

## Durum — kanıtlı
- Regresyon DEĞİL: `git log -S` hiçbir sürümde basılı-tut → dokunmatik geçişi bulmadı.
  Kip `/rc` "Yön" başlığındaki 12 px "dokunmatik" düğmesiydi; sunucuda `rc_dokunmatik`
  tercihi hiç yoktu (`/api/v1/prefs`). Basılı tut = TV'de oynat (Dean'in eski kararı) KORUNDU.
- `/rc` (`aa1b85d`): D-pad sağ-alt boş köşeye **☰ yüzey** tuşu, `#mod`.click() tetikler.
  KANIT: stream `--build` + cloudflared `--force-recreate`; LAN `/rc` grep modpad=3; tünel 303.
- Saat (`aa1b85d`, 0.1.18): alt sıraya üçüncü yuvarlak **✥** → `YuzeyEkrani` (tam kadran:
  40 px kayma yön, dokun CENTER, basılı tut BACK, alt ✕ çıkış). KANIT: `:wear:assembleDebug`
  EXIT=0, `NetMovies-Wear-v0.1.18.apk` 6.753.817 B, sha256 `76d79c1b…535ad`.
- Yayın (üçü de): `data/apk` + `app_update?target=wear` → `v0.1.18-poc` (curl kanıtı);
  evaglass-releases `netmovies-wear-v0.1.18` release; apps.json 118 (`515bc38`, raw'da doğrulandı).

## Durum — doğrulanmadı (inanç)
- ☰ tuşu telefonda görsel olarak görülmedi; ✥ kipi saatte denenmedi. TV'de 0.9.14 de hâlâ denenmedi.

## Kararlar
- Seçenek 1 (Dean onayı "Onaylısın"): kip tuşu D-pad içine; basılı tut davranışı değişmedi.
- Saat mousepad'i ayrı ekran (poster yayı yok → yanlış oynatma yok), ring kiplerine eklenmedi.
- Wear GitHub release'i yalnız evaglass-releases'ta (önceki desen), netmovies repo'da tag yok.

## Tekrarlama / tuzaklar
- Kotlin dizesine `\n` yazarken bash/python/sed/perl hepsi gerçek satır sonu bıraktı (Türkçe
  karakter + locale); Edit tool'u çözdü. Aynı dosyada kaçış gerekiyorsa doğrudan Edit kullan.
- compose'da servis adı `cloudflared` (`tunnel` değil, o profil adı). Stream rebuild sonrası
  cloudflared recreate şart (netns pinli).
- apps.json: `git reset --hard origin/main` üstüne yaz, rebase etme.

## TEK SONRAKİ EYLEM
Dean'in geri bildirimini bekle: telefonda `/rc` ☰ yüzey, saatte ✥ (Mini'de "⬆ 0.1.18 güncelle"
şeridine dokun), TV'de 0.9.14 oklar/şerit. Sonra: AWOX webOS ipk kurulumu (`client-webos/README.md`).
