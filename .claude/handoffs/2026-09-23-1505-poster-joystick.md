# DEVİR — 23 Eylül 2026, 15:05 · Poster joystick (0.9.19) + liste kurtarma (0.9.18)

**Dal:** netmovies `fix/general-stability` (push'lu) · **APK 0.9.19** üç yerde yayında
**Yerel:** localhost/LAN `192.168.1.185:3310` 200 · `w.evaitec.com` 303 · smoke YEŞİL · 167 gateway testi OK
**Disk:** C: 23,8 GB boş (10,1'den) · D: 22,0 GB boş (16,3'ten)

## Kapanan iş

### 1) Liste kurtarma (0.9.18, `f8b065f`) — KANITLI
İzlenecekler listesindeki diziler kalıcı "bulunamadı" veriyordu. Üç kök neden:
- Geçit `load_item`'da `fuck_dmca` sağlayıcı 5xx'te **exception fırlatıyor** →
  hata zarfına dönüşüp kurtarma kodunu hiç çalıştırmıyordu. Artık yutuluyor.
- HDFilmCehennemi `.now` adresine SABİTLENMİŞTİ, `.now` öldü → canlı TLD adayı
  (`.land`) seçiliyor.
- SezonlukDizi WARP ile bile ölü, her istekte 16 sn asılıyordu ("çok geç tıklıyor"
  şikâyetinin kaynağı) → `hidden_providers`.

Kurtarma: istemci `title` + `type` yolluyor, kayıt açılamazsa diğer sağlayıcılarda
başlıkla aranıyor, `search_all._alakali` süzgecinden geçen ilk detay dönüyor.
Kanıt: Pluribus / MobLand / R.J. Decker / Lioness / Kod Adı Apollo — beşi de
bölüm listesiyle açılıyor (önce beşi de boş).

### 2) Poster joystick (0.9.19, son commit) — EMÜLATÖRDE GÖRÜLDÜ
Dean üç tur geri bildirim verdi, üçü de uygulandı:
- "çok saçma bir kart açılıyor, küçük yonca olmalı" → 620dp panel gitti
- "isim yazmasına gerek yok, küçük sadece ikon" → yazı gitti, 58dp ikonlar kaldı
- "arka alan ve açıklamaya gerek yok, küçük bir joistik gibi yeterli" → panel,
  künye ve ipucu şeridi PAD modunda hiç çizilmiyor

**Asıl hata (Dean: "basarken komut gidiyor ve ilkini seçiyor"):** joystick'i açan
uzun basış hâlâ basılıyken framework OK'un ACTION_DOWN TEKRARLARINI yeni odaklanan
düğüme — joystick'e — yolluyor; joystick onu "OK'a basıldı" sayıp Oynat'ı anında
çalıştırıyordu. Guard AÇAN tarafta değil AÇILAN tarafta olmalı: `tusHazir`, kendi
gördüğü ilk ACTION_UP'a kadar hiçbir tuşu işlemez.
Kol zemini `ScrimSoft` → `SurfaceHigh`: panel kalkınca düğmeler afişte kayboluyordu.

Kanıt: eva_test emülatörü, 0.9.19 kurulu, uzun basışta joystick açılıyor ve
oynatıcı AÇILMIYOR (ekran görüntüsü alındı).

### 3) Disk
Silinen: docker build cache 20,5 GB, Temp 4,2 GB, npm+pip 4,2 GB, Gradle cache
10,9 GB, `D:\tmp\APK` 3,5 GB, D: geri dönüşüm kutusu 2,3 GB.

## DOĞRULANMADI
- 0.9.19 GERÇEK cihazda (Mi Box / LG) denenmedi — yalnız telefon-şekilli emülatör.
  Dean "telefonda olduğu için posterden büyük gözüküyor, TV'de rahat olur" dedi,
  boyuta dokunulmadı.
- Liste kurtarması APK üzerinden uçtan uca görülmedi (yalnız yerel API).
- /tv (web) kart verisinde `media_type` yok → orada yalnız "sağlayıcı hatası" dalı
  kurtarır, "bölüm sayfası kaydı" dalı kurtarmaz.

## Dean'e düşen — BEKLEYEN ONAY
1. `scripts/disk-temizlik-admin.ps1` (yönetici): hiberfil 25 GB + Docker vhdx
   compact ~35 GB + WinSxS.
2. `D:\tmp\Diger` 7,3 GB SİLİNMEDİ — içi çöp değil (transfer/muvafakat evrakları,
   xlsx/docx, kişisel mp4).
3. `D:\Important\Phone-Backup` 40,8 GB SİLİNMEDİ — net "sil" gelmedi.
4. `D:\projects\myCar` 47 GB — D:'deki en büyük tek kalem, incelenmedi.
5. Statik IP hâlâ yapılmadı (185 her yere gömülü).

## Tekrarlama / tuzaklar
- **Docker Desktop yeniden başlayınca stream'in port yayını bozulur** (localhost/LAN
  3310 boş yanıt, tünel çalışır): `docker compose stop cloudflared` →
  `up -d --force-recreate stream` → tüneli yeniden kur.
- `wsl --manage ... --set-sparse true` REDDEDİLİYOR (veri bozulması). vhdx küçültme
  = yönetici `diskpart compact vdisk`.
- Motor `encoded_url` base64 DEĞİL, `quote_plus`. Sunucu içinden çağırırken
  `unquote_plus` şart (yoksa `%253A` → 500).
- Gateway testleri `discover -s tests` ile koşar; tek modül döngüsel import'la patlar.
- `evaglass-releases` başka oturumlardan da yazılıyor — push reddedilirse
  `reset --hard origin/main` + apps.json alanlarını yeniden uygula (rebase çakışıyor).
- `docker exec ... /tmp/x` → `MSYS_NO_PATHCONV=1`.
- Emülatör: `adb exec-out screencap -p > dosya` (cihaz içi `screencap -p /sdcard/...`
  bu imajda hata veriyor). İlk açılışta SystemUI ANR diyaloğu çıkabilir, "Wait" ile geç.
- `data/admin.json` gitignored, `gemini_api_key` içeriyor — commit etme.

## TEK SONRAKİ EYLEM
Dean 0.9.19'u Mi Box'a kurup (a) joystick'i ve (b) izlenecekler listesinden bir
diziyi açsın. İki doğrulanmamış kanıt bunlar.
