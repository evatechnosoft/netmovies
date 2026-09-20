# Devir — Oynatıcı D-pad gezinmesi ve giriş düzeltmeleri (0.9.7 → 0.9.9)

**Tarih:** 19 Eylül 2026, 22:41 · **Oturum:** 152b91f2
**Repo:** `D:\projects\netmovies` · dal `fix/general-stability` @ `cd64ac8` · 0 kirli, push edildi
**Sürüm:** TV/telefon **0.9.9 (vc 909)** — üç dağıtım yerinde yayında

## Hedef

Dean'in TV'de bildirdiği giriş/odak kusurlarının kök nedenini kapatmak. Dört
bildirim geldi, dördü de kod tarafında kapandı. **Hiçbiri televizyonda
doğrulanmadı** — emülatör oynatıcıyı ayakta tutamıyor.

## Durum

### Doğrulandı (tool çıktısı var)

- `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL; `KeyPairGateTest` 5/5.
- 0.9.9 emülatöre kuruldu: `cmd package compile -m verify` Success,
  `verify_err=0`, `fatal=0`, ana ekran temiz açıldı.
- Poster uzun-bas kartı emülatörde (YATAY) uçtan uca denendi: ◀ bölümler → ▼▼ →
  OK seçti → odak Oynat'a döndü → kaynak yoklandı, düğmede
  "Oynat — S1B3 · 1 kaynak ✓ · Türkçe altyazı" → ▶ listeler sütunu.
- `scripts/smoke.sh` YEŞİL (movie 428 · serie 450 · live 236 · zincir + manifest
  proxy + gateway testleri).
- Yayın: `app_update?target=tv` → `v0.9.9-poc`; release `netmovies-tv-v0.9.9`
  indirme HTTP 200; `apps.json` tv+phone vc 909. sha256
  `bbd60667051a9990b8d11061f5797d58cbf14a5626a027f65f11dc4145897559`.
- Sunucuda kayıtlı tuş eşlemesi **0 adet**
  (`curl -s localhost:3310/api/v1/prefs | grep -c tv_keymap_`) → yeni varsayılan
  eşleme Dean'in cihazında geçerli olacak.

### Doğrulanmadı (cihaz bekliyor)

**Oynatıcı İÇİ her şey.** Emülatörde oynatıcı açılıyor, `resolve_sources` 200
dönüyor, ~15-20 sn sonra bir toast ile ana ekrana düşüyor. Görülemeyenler:

1. Ok tuşlarıyla kumanda barında gezinme.
2. Ok tuşlarının artık sarmaması.
3. Yarım tuş düzeltmesi (film kendi kendine başlamamalı).
4. Başlangıç panelinde GERİ = OYNAT.
5. Poster kartının TV ekranındaki gerçek ölçüleri (emülatör 698dp, TV ~960dp).

## Kararlar ve gerekçeleri

**0.9.7 — düğmelerde odak yoktu: iki paralel şerit.** `ControlsOverlay`in
`IconBtn`leri Compose `.focusable()` ile odak almaya çalışıyordu; kök kutu da
`.focusable()` olduğu için odak araması oraya inmiyor, AŞAĞI ok `false` dönüp
ölüyordu. O dal silindi (AŞAĞI → `OPEN_BAR` → QuickPad), `IconBtn`den
`.focusable()`/`.clickable()` kaldırıldı. Gezilebilir tek desen QuickPad'in
**index**'i.

**0.9.7 — poster uzun-bas kartı** Dean'in tarifiyle yeniden yazıldı
(`HomeScreen.kt`): SOL bölümler · ORTA poster + yıl/puan/tür + özet + dil
rozetleri · SAĞ listeler · ALT Oynat. Seçim index'le. Bölüm seçmek oynatmaz;
kaynak yoklanır, sonuç Oynat düğmesinde yazar. Veri tek `load_item`'dan.

**0.9.8 — film kendi kendine başlıyordu: yarım tuş olayı.** Bir ekranda basılan
tuşun BIRAKILMASI, o basış yeni ekran açtıysa YENİ ekrana düşüyor. Kartta OYNAT'a
basınca OK'un `ACTION_DOWN`'ı ana ekranda, `ACTION_UP`'ı oynatıcıda işleniyor,
başlangıç panelindeki OYNAT'a kendiliğinden basılmış oluyordu. `KeyPairGate`:
DOWN'ı bu ekranda görülmeyen tuşun UP'ı yutulur — `onPreviewKeyEvent`in ilk
satırında, ne panel ne controller görür. 5 birim testi.

**0.9.8 — başlangıç panelinde GERİ = OYNAT.** Eskiden içerikten çıkarıyordu;
izleyen paneli engel sanıp GERİ'ye basıyor ve ana ekranda buluyordu kendini.
`links` boşsa oynatacak şey yok → yine çıkış. `panelAsList` davranışı değişmedi.

**0.9.9 — ok tuşları sarmaya bağlıydı, gezinme imkânsızdı.**
`RemoteInput.DEFAULTS`: SOL/SAĞ tek basış `SEEK_*_10` → **`OPEN_BAR`**, uzun
basış `SEEK_HOLD_*` → `NONE`. Sarma üç yoldan duruyor: bardaki
−5dk/−30sn/+30sn/+5dk düğmeleri, kumandanın `MEDIA_FAST_FORWARD`/`MEDIA_REWIND`
tuşları (eşlemeden bağımsız, doğrudan bağlı), YUKARI ile açılan önizleme.
Eylemler silinmedi — Buton Eşleme'den geri atanabilir.

## Tekrarlanmayacak

- **Oynatıcıda yön tuşlarını sarmaya bağlamak** — ekranda hiçbir yere gidilemiyor.
  Yön tuşları GEZİNME; sarma kendi tuşunda/düğmesinde.
- **Compose odak ağacına katman üstü kartta/panelde güvenmek** — üçüncü kez
  yaşandı. Katman üstünde index-tabanlı seçim + kendi `onKeyEvent`i.
- **Ekran değiştiren bir tuşun `ACTION_UP`'ını işlemek** — `KeyPairGate` var;
  yeni tam ekran panelde aynı tuzak geçerli.
- **Birim testte `KeyEvent(...)` nesnesi kurmak** — android.jar stub'ı
  "not mocked" atar. Saf mantığı `(action, keyCode)` ilkel imzayla ayır.
- **Kartta sabit `width(240.dp)` sütun** — dar ekranda ortaya yer kalmıyor,
  başlık tek harflik sütuna sıkışıyordu. `weight` kullan.
- **Yarı saydam scrim ile tam ekran kart** — arkadaki raflar okunuyor, dağınık.
- **`PlayerScreen`i bölerek VerifyError çözmeye çalışmak** — hata dexleyicide
  (R8 8.9.x, 256+ register). R8 8.13.23 override'ı kök build'de, düşürme.
- **Emülatörü YATAY çevirmeden TV yerleşimine bakmak** — dikeyde üç sütun üst
  üste biniyor. `settings put system accelerometer_rotation 0` + `user_rotation 1`.
- **Git Bash heredoc'una uzun Kotlin/Markdown gömmek** → parse hatası; Write ile
  dosyaya yaz, `python <dosya>` ile çalıştır.
- `smoke.sh` ilk koşuda `serie` boş diyebilir (soğuk cache 90 sn'yi aşıyor) —
  ikinci koşu 450 verir, kusur değil.

## Sıradaki tek iş

**Dean 0.9.9'u TV'ye kurup bir film/dizi açsın.** "Doğrulanmadı" listesindeki 5
madde bakılacak. Kusur çıkarsa ilk iş emülatör reçetesi (`docs/HANDOFF.md` →
EMÜLATÖR bloğu), cihaz/fotoğraf beklemek değil; oynatıcı içi kalırsa
`docker logs netmovies-engine | grep resolve:` ve
`curl -s localhost:3310/api/v1/client_log`.

Cihaz geri bildirimi gelmeden oynatıcı arayüzünde yeni özellik açılmayacak.

## Açık kalanlar (Dean isterse)

- Kartta ▲ (ORTA/özet) yalnız özeti uzatıyor — Dean başka bir şey kastetmiş olabilir.
- "değişebilir şekilde ekle" — kart tuşları Buton Eşleme'ye bağlanmadı, sabit.
- Oynatıcı emülatörde görüntü gelmeden kapanıyor (toast). TV'de oynuyorsa
  emülatör kusuru.
- "Sağlayıcı & Kaynak" listesinde beş kayıt da "dil bilinmiyor", ikisi yineleniyor
  (DiziPal ×2, Dizilla ×2). 17 Eylül'den beri açık, koda bakılmadı.
