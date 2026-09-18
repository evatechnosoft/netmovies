# DEVİR — 19 Eylül 2026 · sıradaki iş: bölüm listesi en zengin sağlayıcıdan

**Dal:** `fix/general-stability` @ `2499352` · 0 kirli dosya · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `2b09a5d`
**Sürümler:** TV/telefon **0.6.7 (vc 607)** · saat **0.1.15 (vc 115)** · evaitecOTA saat 0.1.11
**Adresler:** yerel `http://192.168.0.29:3310` · tünel `https://w.evaitec.com`
**Yayın kuralı:** kanıt yeşilse sürüm artır, üç yeri birden güncelle
(`data/apk`, `gh release … --target main`, `apps.json`), sonra GitHub'dan indirip
sha256+boyut karşılaştır. Onay sorulmaz.

## Önce doğrula

```bash
git rev-parse --short HEAD                              # 2499352
bash scripts/smoke.sh                                   # kapı YEŞİL
curl -s "localhost:3310/api/v1/app_update?target=tv"    # v0.6.7-poc
curl -s localhost:3310/api/v1/unwatched | head -c 160   # izlenmemiş bölümler
```

---

# YAPILACAK İŞ (tek)

## Sorun — üç kez ısırdı, ölçüldü

Bölüm listesi kartın geldiği sağlayıcınındır; o eksikse yeni bölüm görünmez.

| Dizi | HDFilmCehennemi | DiziMom | Dizilla |
|---|---|---|---|
| Dead City | **7** (S1×6 + S2B1) | 20 | 16 |
| Reacher | **28** (son S4B4) | 32 (S4B8) | 32 |

Dean: "sezon 2'de 1 bölüm", "son bölüm S4B4 ama bu hafta S4B8", "Bölümler (21)".

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

**Sunucu** — `stream/Public/API/v1/Routers/episodes_best.py` (+ `Routers/__init__.py`).
- Girdi `title`, `plugin`, `encoded_url`; `search_all` desenini yeniden kullan
  (`_varyantlar`, `_alakali`, `admin_config.filter_aggregate_items`).
- Her adayda `load_item` → **en çok (sezon,bölüm) çifti** taşıyan liste kazanır;
  eşitlikte `source_score.puanlar()`.
- Dönüş: `{"plugin", "encoded_url", "episodes", "kaynak_sayisi"}`.
- `load_item`'a adres **HAM** gider (`unquote_plus`) — kodlu gönderirsen motor 500.
- Kapı: `stream/tests/test_episodes_best.py`.

**İstemci** — `client-tv/app/.../ui/PlayerScreen.kt`
- `aktifPlugin`/`aktifUrl` state'i (`remember(item.url)`, varsayılan `item.*`);
  `load_item` ve `resolve_sources` bunları kullansın.
- Çağrı **yalnız Bölümler sekmesi açılınca** — oynatıcı açılışında DEĞİL.
- Liste daha uzunsa `episodes` değişsin, `currentEpIndex` **bölüm numarasıyla**
  yeniden eşlensin (`episodeIndexOf`), satırda kaynak görünsün:
  "Bölümler (32) · Dizilla".

---

## Bugün biten (cihazda görülmedi — Dean denerse bildirsin)

0.6.1 Bölümler paneli + alt bar kaydırma + odak dönüşü · 0.6.2 sonraki bölüm
kartı 70 sn · 0.6.3 "Bölümler yükleniyor…" · 0.6.4 Ajanda **İzlemediklerim**
(`/api/v1/unwatched`) · 0.6.5 alt bar tuşları indeks tabanlı gezinme ·
0.6.6 bölüm sayfası kartı doğrudan o bölümü açar · **0.6.7 oynayan bölüm
kendiliğinden kapanmıyor + bağlantı hatasında WARP**.

Saat **0.1.15** (5,3 MB): indirme hızı Dean tarafından onaylandı, **açılışı
doğrulanmadı**. Açılmazsa `client-tv/wear/build.gradle.kts` →
`debug { isMinifyEnabled = false }` (23 MB, Wi-Fi düzeltmesiyle LAN'dan iner).

## Açık kalan gözlem

Dean "bazen duruyor" da dedi. 0.6.7 kapanmayı kesti ama **durma** (tampon
boşalması) ayrı olabilir — tekrarlarsa `docker logs netmovies-stream | grep
"Proxy hatası"` ve `client_log` ile segment/tampon tarafına bak.

## Tekrarlanmayacak hatalar

1. Sunucu içinden `load_item`'a **kodlu** adres → motor 500.
2. Testi canlı `/data` deposuna bağlama (ortam değişkeni + tempdir).
3. Git Bash'te `docker exec -w` → `MSYS_NO_PATHCONV=1`.
4. Stream rebuild tüneli düşürür → `up -d --build stream cloudflared`.
5. Panel içi ekran yazmak yetmez, **girişleri de** çevir.
6. Odak efektinin anahtar listesi eksikse D-pad ölür (`showStartPanel`).
7. Sabit genişlikli şerit TV ekranına sığmaz (11×64dp alt bar).
8. **TV'de Compose odağına güvenme** — pad seçimi indeksle tutulmalı.
9. **Kotlin regex'ine Türkçe harf koyma** — derleyici platform kodlamasıyla
   okuyunca "ö/ü" bozulur, desen hiç eşleşmez; ASCII kalıp (`[Bb].l.m`).
10. R8 açarken uygulama kodunu koru + `android.enableR8.fullMode=false`.
11. Modül monkeypatch'i yerine saf fonksiyon test et (`ref_coz`, `basliktanBolum`).
12. **"Hiç açılamadı" dalını oynayan içeriğe uygulama** — otomatik kapanma
    yalnız `position == 0` iken.
