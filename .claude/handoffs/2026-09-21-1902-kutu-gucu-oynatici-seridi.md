# DEVİR — 21 Eylül 2026, 19:02 · kutu gücü + Reacher film + oynatıcı şeridi

**Dal:** netmovies `fix/general-stability` (push'landı, PR yok — dal tek kaynak)
**Sürümler:** TV/telefon **0.9.14 (914)** · saat **0.1.17 (117)**
**Yerel adres:** `http://192.168.1.185:3310` · Mi Box `.189` (MAC 24:18:C6:B8:05:B8) ·
TV AWOX webOS 5.6.0-20 `.188` (Wi-Fi MAC D0:A4:6F:C9:28:80), aygıt adı `evostv`

## Bu oturumda kapananlar (hepsi commit + push)

### 1. Kutu gücü — `589d84b` (kapatma çalışıyor, açma AĞDAN MÜMKÜN DEĞİL)
- Eski "Docker NAT engeli" teşhisi YANLIŞTI: konteyner host'a ulaşıyor, yalnız
  `host.docker.internal` adı çözülmüyordu (özel `dns:`). compose'a
  `extra_hosts: host.docker.internal:host-gateway` eklendi. Ters-köprü planı iptal.
- `POST /api/v1/remote/power` → host'taki `scripts/atv_power.py` (3311) → kutuya POWER.
  `/rc` kumandasında güç düğmesi. Köprü kutu uykudayken de başlatılabiliyor (lazy
  bağlantı); `netmovies-autostart.cmd` köprüyü de kaldırıyor.
- KANIT: kutu açıkken POWER → 6466 kapandı. Uykudayken → `{"ok":false,"error":"kutu uykuda"}`.
- Uykudaki kutu ağda HİÇ yok (ping bile geçmez, 6466/8008/8009 kapalı). WoL denendi
  (Wi-Fi, WoWLAN yok) 30 sn uyanmadı → kod bırakılmadı. Cast/DIAL da uykuda kapalı.
- TV (webOS): 3000/3001 kapalı, tek açık port 6668. Dean'in TV'de açması gerekenler:
  SIMPLINK (HDMI-CEC) — kutu kapanınca TV de kapansın; "Mobil TV Açma" — WoL ile TV açılır,
  CEC ile kutu uyanır. İkisi de TV menüsünde, uzaktan yapılamaz. İP sabitleme: TV `.180`e
  geçmemiş (o adreste başka cihaz var: CC-8C-BF-24-86-7B), hâlâ `.188`.

### 2. Reacher S4E8 başka film açıyordu — `535b1b2`
`baslik_uyusuyor` tek yönlü kapsama bakıyordu; "Reacher" → "Jack Reacher: Asla Geri
Dönme" filmi zincire giriyordu. Artık adaydaki FAZLA kelimeler yapımı değiştiriyorsa red
(sezon/bölüm/sayı muaf), iki dilli başlık " - " ile parçalanıp ayrı denenir.
KANIT: engine tests 12/12; resolve_sources Reacher → yalnız DiziYou/Dizilla/DiziMom.
Bölüm seçimi doğruydu (32 bölüm, S4E8 = index 31).

### 3. Oynatıcı: alt kumanda şeridi (QuickPad) KALDIRILDI — `a2580ba` (0.9.14)
Dean: "her basım 2. bara düşüyor 30sn/5dk olan yere". Kök: varsayılan eşlemede
SOL/SAĞ/AŞAĞI = OPEN_BAR + boşta duran her tuş şeridi açıyordu. Yeni: SOL/SAĞ 10 sn,
basılı hızlı sarma, AŞAĞI süre bilgisi / basılı ayarlar, YUKARI önizleme / basılı bölümler.
`RemoteAction.fromId("bar")` → SHOW_CONTROLS (kayıtlı eşleme ölmesin).
KANIT: assembleDebug + testDebugUnitTest exit 0. **CİHAZDA DOĞRULANMADI.**

## Yayın (üçü de yapıldı, kanıt)
- Yerel OTA `app_update?target=tv` → `v0.9.14-poc` (data/apk/NetMovies-TV-v0.9.14.apk)
- GitHub `v0.9.14-poc` (netmovies, `--target fix/general-stability`)
- evaglass-releases `netmovies-tv-v0.9.14` + apps.json tv/phone 914 (`546ab2e`, main)
  NOT: apps.json rebase'de çakıştı → `reset --hard origin/main` üstüne yeniden uygulandı.
sha256 `52d0c288a0dea71bb3ef7242c6af80daa8d8898345bc36eacda11df03acf93ac`

## Açık konular / sıradaki
1. Dean 0.9.14'ü TV'ye kursun: oklar sarıyor mu, şerit gerçekten yok mu, AŞAĞI bilgi.
2. Dean'in araştırma isteği: AWOX webOS temizlik/uygulama ekleme. Cevap verildi:
   Developer Mode (1000 sa) vs root (dejavuln-autoroot, 3. parti webOS Hub'da
   doğrulanmamış). ÖNERİ: NetMovies web arayüzünü webOS .ipk olarak paketle → Mi Box
   gereksiz olur. Dean cani.rootmy.tv'ye bakıyor; TV modeli `HD203024212K0447`,
   firmware `5.6.0-20`.
3. Kutu gücü "açma" tarafı: TV'de SIMPLINK + Mobil TV Açma açılınca WoL'u TV MAC'ine dene.
