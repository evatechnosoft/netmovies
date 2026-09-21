# DEVİR — 21 Eylül 2026, 09:49 · bölüm geçişi kilidi + saat poster yayı

**Dal:** netmovies `fix/general-stability` (push'landı, PR yok — dal tek kaynak)
**Sürümler:** TV/telefon **0.9.13 (913)** · saat **0.1.17 (117)**
**Yerel adres:** `http://192.168.1.185:3310` · kutu (Mi Box) 192.168.1.189

## Bu oturumda kapananlar

### 1. TV 0.9.13 — bölüm sonunda tek basışta 2-3 bölüm atlanması (Dean'in şikâyeti)
KÖK NEDEN: geçiş istendiğinde yeni kaynak çözülene kadar **eski akış ekranda kalıyor**.
`duration/position` hâlâ eski bölümün olduğu için "sonraki bölüm" teklif kartı görünür
kalıyor; kumandanın tuş tekrarı veya ikinci basış her seferinde bir bölüm daha
ilerletiyordu. Ayrıca eski akış bu arada bitince `STATE_ENDED` işareti artık YENİ
bölümün durumuna yazılıyor (`akisBitti` `currentEpIndex`'e göre remember'lı), yeni
bölüm açılır açılmaz geri sayım başlıyordu.

`PlayerScreen.kt`:
- `gecisBekleyen: Int?` — `goToEpisode` yeniden girişe kapalı. Kilit `STATE_READY`'de
  (yeni kaynak açıldı) VE çözümleme zinciri bitince (`searching = false`) kalkar —
  kaynak bulunamayan bölüm sonraki geçişleri kilitlemesin diye iki yerde de.
- Teklif kartı (`sonrakiTeklif`) + geri sayım (`sayimBaslasin`) geçiş boyunca gizli.
- Geçiş beklerken gelen `STATE_ENDED` "bitti" işareti koymaz.

KANIT: `:app:assembleDebug` + `:app:testDebugUnitTest` BUILD SUCCESSFUL.
**Cihazda DOĞRULANMADI** — TV'de bölüm sonu davranışı görülmedi.

### 2. Saat 0.1.17 — poster yayı iç içe (Dean fotoğrafı: "çok iç içe, yukarı al,
listeyi ona göre yay çiz, aşağı al kontrolleri")
`wear/MainActivity.kt`:
- Poster altındaki başlık KALKTI — asıl iç içelik oydu (44dp etiket, 5°'lik adımda
  komşusunun üstüne biniyordu). Seçili başlık zaten yayın altındaki durum satırında.
- `aciAdimi` 5°→9°, `yaricapKat` 2.2→1.6 → komşu arası ~47dp, kavis görünür.
- Yayın TEPESİ şeridin üst üçte birine: `merkezY = yaricap + yukseklikPx*0.34f`
  (eskiden `yukseklikPx + yaricap - yukseklikPx*0.15f` → daireler kutunun DİBİNDE,
  alttaki metin/düğmelerle çakışmanın kaynağı). `SeritYuksekligi` 50→66dp.
- Offset px/dp karışıklığı: `(x - 18f)` px'ten çıkarılıyordu ama 18dp sanılmıştı →
  `PosterCap(38.dp).toPx()/2` ile daire merkezi yayın üstüne oturdu.
- Düğmeler + `KipYayi` `Spacer(Modifier.weight(1f))` ile alt yarıya indi; Column
  padding `start/end 8dp, top 8dp, bottom 12dp` (horizontal+top/bottom KARIŞIK
  kullanılamaz — derleme hatası verdi, start/end yazıldı).

KANIT: `:wear:assembleDebug` BUILD SUCCESSFUL, APK 6.737.433 B.
**Cihazda/emülatörde DOĞRULANMADI** — Wear AVD yok (`emulator -list-avds` → yalnız
`eva_test`). Kavis kalibrasyonu gözle: `aciAdimi` + `yaricapKat` tek yerde.

## Yayın — üçü de yapıldı (kanıt)
- Yerel OTA: `curl localhost:3310/api/v1/app_update?target=tv` → `v0.9.13-poc`,
  `?target=wear` → `v0.1.17-poc`. APK'lar `data/apk/`de.
- GitHub (uygulama içi OTA): `v0.9.13-poc` (netmovies repo, `--target fix/general-stability`).
- Mağaza: `netmovies-tv-v0.9.13` + `netmovies-wear-v0.1.17` (evaglass-releases) ve
  `apps.json` → tv/phone 913, watch 117 (push'landı).

## TUZAK — saat APK'sı DEBUG ile üretilir
`wear/build.gradle.kts`: küçültme (`isMinifyEnabled`) **debug**'da açık, release'te
KAPALI (release imzasız çıkıyor, cihaz kuramıyor). Bu oturumda önce `assembleRelease`
denendi → 18,8 MB. Doğrusu `:wear:assembleDebug` → 6,7 MB. TEKRARLAMA.

## Sıradaki iş (önceki devirden devam, dokunulmadı)
KUTU GÜCÜ: `scripts/atv_power.py` köprüsü PC'de çalışıyor ve eşleşme tamam, ama
stream konteyneri LAN'a/host'a ulaşamıyor (Docker Desktop NAT). Planlanan çözüm:
yönü ters çevir — köprü sunucuyu yoklasın (`/api/v1/remote/command` type=power
kuyruğu) + widget düğmesi. POWER tuşu cihazda HİÇ denenmedi.

## Tek sonraki eylem
Dean TV'yi 0.9.13'e, saati 0.1.17'ye güncelleyip **bölüm sonu geçişini** ve **saat
kadranındaki yay yerleşimini** görsün; geri bildirime göre ya kalibrasyon (aciAdimi/
yaricapKat) ya da kutu gücü işine geç.
