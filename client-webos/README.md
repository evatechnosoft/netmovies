# NetMovies — webOS (LG / webOS Hub) uygulaması

Web arayüzünün (`stream/`) ince sarmalayıcısı: `app/index.html` önce ev ağını
(`192.168.1.185:3310`, LAN'dan PIN sorulmaz), 3 sn cevap yoksa tüneli
(`w.evaitec.com`, PIN bir kez, çerezle kalır) açar. Kod TV'de değil sunucuda —
yeni özellik için ipk yeniden kurmak gerekmez.

## Paketle
```bash
npm i -g @webos-tools/cli          # bir kez
ares-package app -o dist           # dist/com.evaitec.netmovies_0.1.0_all.ipk
```
Sürüm: `app/appinfo.json` → `version`. Sunucu adresi değişirse `app/index.html`
→ `ADRESLER`.

## TV'ye kur (Developer Mode, root gerekmez)
1. TV: LG Content Store → **Developer Mode** uygulamasını kur, LG hesabıyla gir,
   **Dev Mode Status: ON**, **Key Server: ON**. Ekranda TV'nin IP'si ve passphrase
   (6 harf) görünür. Oturum 1000 saat; süre dolunca uygulama silinir — uygulamanın
   içindeki **Extend** ile yenilenir.
2. PC:
```bash
ares-setup-device --add evostv -i "host=192.168.1.188" -i "port=9922" -i "username=prisoner"
ares-novacom --device evostv --getkey        # passphrase sorar
ares-install --device evostv dist/com.evaitec.netmovies_0.1.0_all.ipk
ares-launch  --device evostv com.evaitec.netmovies
```
Kaldırma: `ares-install --device evostv --remove com.evaitec.netmovies`.

## Bilinen sınırlar
- Developer Mode ile kurulan uygulama kalıcı değildir (1000 sa). Kalıcılık için
  root + Homebrew Channel gerekir; AWOX (3. parti webOS Hub) cihazlarda doğrulanmadı.
- Web arayüzü kumanda D-pad'iyle geziliyor; TV'ye özel tuş eşlemesi `client-tv`'deki
  gibi değil, tarayıcı odak sistemi.
