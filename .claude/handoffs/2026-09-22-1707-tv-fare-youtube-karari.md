# DEVİR — 22 Eylül 2026, 17:07 · /tv fare+bölüm düzeltmeleri · YouTube kalite kararı bekliyor

**Dal:** netmovies `fix/general-stability` @ `2730ae9` + **commit edilmemiş** `/tv` düzeltmeleri
**Sürümler:** APK 0.9.15 (derlendi·YAYINLANMADI) · saat 0.1.18 · webOS ipk 0.1.4 (TV'de kurulu)
**Yerel:** `192.168.1.185:3310` · LG TV `192.168.1.175` (açık, DevTools 9998)

## Dean'in doğruladıkları (bu oturum)
- `/tv` çalışıyor: dizi oynuyor, **kaldığın yerden devam ediyor**, fare (Magic Remote) seçebiliyor.
- Şikâyetler: fare imleci "yağ gibi durmuyor"; imleç açıkken OK duraklatmıyor; uzun bölüm
  listesinde gezinme zor ("13, 18 bölümler sıralama şansımız var mı").

## Durum — KANITLI
- Üç düzeltme yayında (curl grep 3/3, yığın healthy, `smoke.sh` YEŞİL):
  1. Fareden gelen odakta raf KAYDIRILMIYOR (`odakla(ri,ki,false)`) — kart imlecin altından
     kaçıyordu, kök neden buydu.
  2. `#oynatici` üzerine `click` dinleyicisi — Magic Remote açıkken kumandanın OK'u sayfaya
     keydown değil click olarak geliyor.
  3. Bölüm listesinde ←→ = ±10 bölüm; başlıkta `S4 B8 (36/43) · ←→ 10 atla`.
  Bunların hiçbiri TV'de Dean tarafından DENENMEDİ.
- **YouTube yapılabilirlik ölçüldü.** Engine'de yt-dlp 2026.08.19 + (geçici) deno 2.9.7 kurulu.
  YouTube çözülüyor (başlık + 48 format). AMA ses+görüntü BİRLEŞİK tek akış yalnız **360p**
  (`format 18`): `ios · web_safari · mweb · android_music · web_embedded · tv_embedded ·
  tv_simply · android_creator` → format yok/hata; `android · android_vr` → tek format, 360p.
  1080p akışlar ayrık (DASH), TV'nin `<video>`'su tek akış ister.
- deno GEÇİCİ: `docker exec` ile /usr/local/bin'e kuruldu, **imajda yok**, engine yeniden
  kurulursa kaybolur. Dockerfile'a henüz eklenmedi.

## Bekleyen KARAR (Dean'e soruldu, cevap gelmedi)
1. 360p ile ekle (bugün biter, müzik/klip için yeter)
2. ffmpeg ile sunucuda birleştir (1080p ama sürekli CPU + sarma sorunlu)
3. Ekleme (LG'nin kendi YouTube'u kalsın)
Öneri: **1** — arama/uç/arayüz işi 2'de de aynen kullanılır.

Dean ayrıca istedi (hangi yol seçilirse seçilsin yapılacak, YouTube'a özel değil):
**yt-dlp otomatik güncelleme** ve **çözüm sonuçlarının önbelleklenmesi**.

## Tekrarlama / tuzaklar
- YouTube için client taramasını TEKRARLAMA: sonuç yukarıda, tek birleşik = 360p.
- `ghcr.io` build sırasında zaman aşımına uğrayabiliyor (uv imajı). `up -d --build stream`
  başarısız olursa önce ağ, sonra tekrar dene — bir kez kendiliğinden düzeldi.
- Şablon değişince `restart` değil `up -d --build stream`; ardından `cloudflared --force-recreate`.
- `encoded_url`'i URLSearchParams'a verme (çift kodlama → sessizce 0 kaynak).
- TV Chrome 68: `?.` / `??` / `flat` / `allSettled` yok.
- webOS kurulumu: `ares-install` ÇALIŞMAZ; saf SSH + `luna-send-pub`, **`ssh -tt` şart**.
  Anahtar `/c/Users/Deacjx/.ssh/evostv_plain`; CDP betikleri scratchpad'de.

## COMMIT EDİLMEMİŞ
`stream/Public/Home/Templates/pages/tv.html.j2` — fare odağı, oynatıcı tıklaması, bölüm atlama.

## TEK SONRAKİ EYLEM
Dean'in YouTube kalite kararını al (1/2/3). Beklerken `tv.html.j2` commit edilecek ve
Dean TV'de üç düzeltmeyi denerse geri bildirim işlenecek.
