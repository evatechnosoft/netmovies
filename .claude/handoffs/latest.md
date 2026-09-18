# DEVİR — sıradaki iş: bölüm listesi en zengin sağlayıcıdan

**Dal:** `fix/general-stability` @ `82cbffc` · 0 kirli dosya · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `d08e167`
**Sürümler:** TV/telefon **0.6.4 (vc 604)** · saat **0.1.15 (vc 115)** · evaitecOTA saat 0.1.11
**Adresler:** yerel `http://192.168.0.29:3310` · tünel `https://w.evaitec.com`

## Önce doğrula (koş, sonra başla)

```bash
git rev-parse --short HEAD                 # beklenen: 82cbffc
bash scripts/smoke.sh                      # beklenen: kapı YEŞİL
curl -s localhost:3310/api/v1/unwatched | head -c 200      # 8 civarı bölüm
curl -s "localhost:3310/api/v1/app_update?target=tv"       # v0.6.4-poc
```

---

# YAPILACAK İŞ

## Sorun (iki kez ısırdı, kanıtlı)

Bölüm listesi, kartın geldiği sağlayıcınınkidir. O sağlayıcı eksik liste
veriyorsa kullanıcı yeni bölümü göremiyor:

| Dizi | HDFilmCehennemi | DiziMom | Dizilla |
|---|---|---|---|
| The Walking Dead: Dead City | **7** (S1×6 + S2B1) | 20 | 16 |
| Reacher | **28** (son S4B4) | 32 (S4B8) | 32 |

Dean (18 Eylül): "sezon 2'de 1 bölüm gösteriyor", "son bölümünde o değil eski
bölüm, neredeyse 8. bölüm bu hafta".

Ölçüm komutu (tekrar üretmek için):
```bash
python -c "
import json,urllib.request,urllib.parse
q='http://localhost:3310/api/v1/search_all?'+urllib.parse.urlencode({'query':'dead city','group':'1'})
r=json.load(urllib.request.urlopen(q,timeout=240))['result'][0]
for p in r['providers']:
    raw=urllib.parse.unquote_plus(p['url'])
    d=json.load(urllib.request.urlopen('http://localhost:3310/api/v1/load_item?'+urllib.parse.urlencode({'plugin':p['plugin'],'encoded_url':raw}),timeout=120))['result']
    eps=d.get('episodes') or []
    print(p['plugin'],len(eps),[(e.get('season'),e.get('episode')) for e in eps][-3:])
"
```

## Çözüm taslağı

**Sunucu — yeni uç `/api/v1/episodes_best`**
(`stream/Public/API/v1/Routers/episodes_best.py`, `Routers/__init__.py`'ye ekle)

- Girdi: `title`, `plugin`, `encoded_url` (mevcut kart).
- `search_all`'daki desenle aynı başlığı tüm sağlayıcılarda ara
  (`_varyantlar`, `_alakali`, `admin_config.filter_aggregate_items` yeniden kullan).
- Her adayda `load_item` → **en çok (sezon,bölüm) çifti taşıyan** listeyi seç.
  Eşitlikte `source_score.puanlar()` yüksek olan kazansın.
- Dönüş: `{"plugin": …, "encoded_url": …, "episodes": [...], "kaynak_sayisi": n}`.
- **`load_item`'a adres HAM gider** (`unquote_plus`) — kodlu gönderirsen httpx bir
  daha kodlar, motor 500 döner. Bu tuzağa bugün bir kez düşüldü.
- Kapı: `stream/tests/test_episodes_best.py` — en zengin listenin seçilmesi,
  eşitlikte puan, aday bulunamayınca mevcut listeyi aynen döndürme.

**İstemci — `client-tv/app/.../ui/PlayerScreen.kt`**

- `aktifPlugin` / `aktifUrl` state'i ekle (`remember(item.url)`), varsayılan
  `item.plugin` / `item.url`. `resolve_sources` ve `load_item` çağrıları bunları
  kullansın (şu an doğrudan `item.*` okuyor: satır ~325 ve ~890).
- **Yalnız Bölümler sekmesi açılınca** `episodesBest` çağrılsın (oynatıcı
  açılışında DEĞİL — tüm sağlayıcıları taramak ilk oynatmayı saniyelerce geciktirir).
  Sekme girişi: `SettingsPanel` içinde `acilisBolumler` / `"Bölümler"` dalı.
- Gelen liste daha uzunsa: `episodes = yeni`, `aktifPlugin/aktifUrl = yeni`,
  `currentEpIndex`'i **bölüm numarasıyla** yeniden eşle (`episodeIndexOf`,
  indeksle DEĞİL — sağlayıcılar farklı bölümden başlıyor).
- Panelde satır: "📑 Bölümler (32) · Dizilla" — listenin hangi sağlayıcıdan
  geldiği görünsün.

## Dikkat

- Sözleşme değişmiyor: `resolve_sources` zaten `plugin`+`encoded_url`+`episode` alıyor.
- TMDB sezon numaralandırması sağlayıcılardan farklı olabilir (Dead City: TMDB S3,
  sağlayıcı S2) — eşleme sağlayıcı listesi üzerinden yapılmalı, TMDB üzerinden değil.
- Yayın kuralı: kanıt yeşilse sürüm artır ve üç dağıtım yerini birden güncelle
  (yerel `data/apk`, `gh release … --target main`, `apps.json`), sonra
  GitHub'dan indirip sha256+boyut karşılaştır.

---

## Cihazda henüz görülmeyenler (Dean denerse bildirsin)

- TV 0.6.1–0.6.4: Bölümler paneli, alt bar kaydırma, sağ/sol sarma, 70 sn kartı,
  "Bölümler yükleniyor…", Ajanda'da **İzlemediklerim**.
- Saat **0.1.15** açılıyor mu: 0.1.14 (R8, 850 KB) açılmamıştı; kurallar
  düzeltildi, 5,3 MB. Açılmazsa `client-tv/wear/build.gradle.kts` içinde
  `debug { isMinifyEnabled = false }` ile 23 MB'a dön — Wi-Fi düzeltmesi
  sayesinde o boyut bile artık LAN'dan iniyor.

## Tekrarlanmayacak hatalar

1. Sunucu içinden `load_item`'a **kodlu** adres gönderme → motor 500.
2. Testi canlı `/data` deposuna bağlama → `LANG_MEMO_PATH` gibi ortam değişkeni + tempdir.
3. Git Bash'te `docker exec -w` → `MSYS_NO_PATHCONV=1`.
4. Stream rebuild tüneli düşürür → `up -d --build stream cloudflared` birlikte.
5. Panel içi ekran yazmak yetmez, **girişleri de** oraya çevir.
6. Odak efektinin anahtar listesi eksikse D-pad ölür (`showStartPanel` unutulmuştu).
7. Sabit genişlikli şerit TV'de taşar (11×64dp alt bar 640dp ekrana sığmıyor).
8. R8 açarken uygulama kodunu koru + `android.enableR8.fullMode=false`.
9. Modül monkeypatch'i yerine saf fonksiyon test et (`ref_coz` deseni).
