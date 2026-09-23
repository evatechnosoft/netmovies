# DEVİR — 23 Eylül 2026, 13:09 · Liste kurtarma (0.9.18) + disk temizliği

**Dal:** netmovies `fix/general-stability` @ `b83908c` (push'lu, çalışma ağacı temiz — `atv-kopru.log` takipsiz)
**Sürümler:** APK **0.9.18** (üç yerde yayında) · saat 0.1.18 · webOS ipk 0.1.4 (değişmedi)
**Yerel:** `192.168.1.185:3310` · localhost 200 · LAN 200 · `w.evaitec.com` 303 — üçü de doğrulandı
**Kapı:** `bash scripts/smoke.sh` → YEŞİL · `unittest discover -s tests` → 167 test OK

## Bu oturumda kapanan iş — KANITLI

### Dean'in şikâyeti: "izlenecekleri eklediğim diziler hiçbir zaman açmıyor, bulunamadı deyip kapatıyor"

Üç AYRI kök neden vardı; üçü de kapandı (`f8b065f`).

1. **Geçit `load_item` kurtarmayı hiç çalıştıramıyordu.** `fuck_dmca` sağlayıcı
   5xx'te `ProviderRequestError` FIRLATIYOR → hata zarfına dönüşüp istemciye
   "bulunamadı" olarak düşüyor, handler'ın geri kalanı hiç koşmuyor. Artık
   try/except ile yutuluyor, hata "kullanılamaz detay" sayılıyor.
   **Bu tuzak kurtarma kodunu sessizce ölü bıraktı — log bile basmadı.**
2. **HDFilmCehennemi `.now` adresine SABİTLENMİŞTİ** (`if ... _MAIN_URL = ".now"`),
   `.now` öldü. Sağlayıcı komple düşmüştü (arama+katalog+film). Artık canlı TLD
   adayı seçiliyor: `.land` → 200 / 232 KB. Aramada yeniden görünüyor.
3. **SezonlukDizi ölü** — WARP ile bile ReadTimeout, numaralı adaylar (1-15,
   .com/.net) ve upstream .kt'nin gösterdiği `sezonlukdizi6.com` de ölü.
   Her istekte 16 sn asılıyordu → **Dean'in "çok geç tıklıyor" şikâyetinin
   kaynağı buydu.** `data/admin.json` → `hidden_providers`.

**Kurtarma mekanizması:** istemci `load_item`'a `title` + `type` yolluyor; kayıt
açılamazsa (hata VEYA `type=serie` olduğu halde bölüm listesi boş) diğer
sağlayıcılarda başlıkla aranır, `search_all._alakali` süzgecinden geçen ilk
açılabilir detay döner.

Kanıt (yerel 3310, önce beşi de boş/None idi):
```
Pluribus     18.1s -> 'Pluribus' eps=9        MobLand 2.1s -> 'MobLand' eps=11
R.J. Decker  16.9s -> 'R.J. Decker' eps=9     Lioness 2.9s -> 'Special Ops: Lioness' eps=24
Kod Adı: Apollo 0.9s -> 'Kod Adı: Apollo' eps=2
```

Dokunulan yerler: `stream/Public/API/v1/Routers/load_item.py` (kurtarma),
`engine/Plugins/HDFilmCehennemi.py` (TLD adayları), istemcilerde `title`/`type`
(NetMoviesApi, ApiModels `mediaType`, Library, HomeScreen, PlayerScreen,
PhoneSearchScreen, player.js, tv-home-actions.js), `stream/tests/test_load_item_rescue.py`
(4 test), sözleşme testi güncellendi, `scripts/smoke.sh` canlı TV kapalıyken
artık kırmızı değil.

### Yayın — üç yer de güncel
- Yerel OTA: `data/apk/NetMovies-TV-v0.9.18.apk` · `/api/v1/app_update?target=tv` → 0.9.18
- Release: `evatechnosoft/netmovies` `v0.9.18-poc`
- Katalog: `evaglass-releases` `netmovies-tv-v0.9.18` + `apps.json` (tv+phone, vc 918,
  sha256 6721fb2a…ea23)

### Disk — C: 10,1 → 23,8 GB · D: 16,3 → 22,0 GB
Yapılanlar: docker build cache 20,5 GB (vhdx İÇİNDE), Temp 4,2 GB, npm+pip 4,2 GB,
Gradle cache 10,9 GB, `D:\tmp\APK` 3,5 GB, D: geri dönüşüm kutusu 2,3 GB.

## DOĞRULANMADI
- 0.9.18 hiçbir cihazda görülmedi (`adb devices` boş). Derleme + 45 birim test temiz,
  davranış kanıtı yok. 0.9.17'nin dört yönlü pad'i de hâlâ denenmedi.
- Kurtarma yalnız yerel API ile doğrulandı; APK/TV üzerinden uçtan uca görülmedi.
- /tv (web) tarafında kart verisinde `media_type` YOK → orada yalnız "sağlayıcı
  hatası" dalı kurtarır, "bölüm sayfası kaydı" dalı kurtarmaz.

## Dean'e düşen — BEKLEYEN ONAY
1. `scripts/disk-temizlik-admin.ps1` — **yönetici** PowerShell: hiberfil (25 GB)
   + Docker vhdx compact (~35 GB) + WinSxS. Üçü de yönetici istiyor.
2. **`D:\tmp\Diger` (7,3 GB) SİLİNMEDİ** — adı tmp ama içi çöp değil: transfer/
   muvafakat evrakları, xlsx/docx, kişisel mp4. Dean bakıp karar verecek.
3. **`D:\Important\Phone-Backup` (40,8 GB) SİLİNMEDİ** — Dean "eskidi, yeni backup
   alınır herhalde" dedi ama net "sil" demedi. Geri dönüşü yok, onay bekliyor.
4. Statik IP hâlâ yapılmadı (PC Wi-Fi DHCP ile 185; 185 her yere gömülü).

## Tekrarlama / tuzaklar
- **Docker Desktop yeniden başlatılınca stream'in port yayını bozuluyor**
  (localhost/LAN 3310 boş yanıt, tünel çalışıyor). Çözüm: `docker compose stop
  cloudflared` → `up -d --force-recreate stream` → tünelin kendisini yeniden kur.
- `wsl --manage docker-desktop --set-sparse true` REDDEDİLİYOR (veri bozulması
  uyarısı). vhdx küçültme = yönetici `diskpart compact vdisk`.
- Motor `encoded_url` **base64 DEĞİL**, `quote_plus`. Arama sonucunun `url`'i zaten
  kodlu gelir; sunucu içinden çağırırken `unquote_plus` ŞART (httpx bir kez daha
  kodlar → motor `%253A` görüp 500).
- Tek modül testi (`python -m unittest tests.test_load_item_rescue`) döngüsel
  import'la patlar; **`discover -s tests` ile koştur.**
- `docker exec ... /tmp/x` yolunu MSYS Windows'a çeviriyor → `MSYS_NO_PATHCONV=1`.
- `data/admin.json` gitignored ve `gemini_api_key` içeriyor — commit etme.
- SezonlukDizi'yi yeniden araştırma: WARP dahil her yol denendi, site ölü.

## TEK SONRAKİ EYLEM
Dean 0.9.18'i Mi Box'a kurup **izlenecekler listesinden bir dizi açsın** — kurtarma
gerçek cihazda çalışıyor mu, tek eksik kanıt bu.
