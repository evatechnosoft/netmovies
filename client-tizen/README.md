# NetMovies — Tizen (Samsung Smart Monitor M7) uygulaması

`app/` ince kabuktur: medya tuşlarını kaydeder, `http://192.168.0.186:3310/tv?profil=cocuk`'ye (çocuk modu) geçer.
Arayüz sunucuda; yeni özellik için yeniden kurmak gerekmez.

- Satır içi `<script>`/`<style>` Tizen'in varsayılan CSP'sine takılır → `app.js`, `app.css`.
- iframe olmaz: sunucu `X-Frame-Options: sameorigin` döner.
- Uzak sayfada `tizen` nesnesi yoktur; tuş kaydı kabukta yapılır.

## Kur (laptop, Tizen Studio CLI `C:\tizen-studio`)
Monitörde: Apps → App Settings → `12345` → Developer Mode On, Host IP = laptop → yeniden başlat.
```powershell
$t="C:\tizen-studio\tools\ide\bin\tizen.bat"
C:\tizen-studio\tools\sdb.exe connect 192.168.1.184
& $t package -t wgt -s netmovies -- <app kopyası>     # imza profili: netmovies (yazar sertifikası)
& $t install -n NetMovies.wgt -t LS43BM700UPXUF
& $t run -p EvNetMovie.NetMovies -t LS43BM700UPXUF
```
Hata ayıklama: `sdb shell 0 debug EvNetMovie.NetMovies` → verilen portu `sdb forward tcp:9222 tcp:<port>` → DevTools.
