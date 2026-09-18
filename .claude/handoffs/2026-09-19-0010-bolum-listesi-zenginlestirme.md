# DEVİR — 19 Eylül 2026 · bölüm listesi işi bitti (0.7.0)

**Dal:** `fix/general-stability` @ `f944907` · 0 kirli dosya · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `2b09a5d`
**Sürümler:** TV/telefon **0.7.0 (vc 700)** · saat **0.1.15 (vc 115)** · evaitecOTA saat 0.1.11
**Adresler:** yerel `http://192.168.0.29:3310` · tünel `https://w.evaitec.com`
**Yayın kuralı:** kanıt yeşilse sürüm artır, üç yeri birden güncelle
(`data/apk`, `gh release … --target main`, `apps.json`), sonra GitHub'dan indirip
sha256+boyut karşılaştır. Onay sorulmaz.

## Önce doğrula

```bash
git rev-parse --short HEAD                              # f944907
bash scripts/smoke.sh                                   # kapı YEŞİL
curl -s "localhost:3310/api/v1/app_update?target=tv"    # v0.7.0-poc
curl -s localhost:3310/api/v1/unwatched | head -c 160   # izlenmemiş bölümler
```

---

# BİTTİ — bölüm listesi en zengin sağlayıcıdan (0.7.0)

`/api/v1/episodes_best`: aynı başlık tüm sağlayıcılarda aranır, **benzersiz
(sezon,bölüm) çifti** en çok olan liste kazanır (ham uzunluk yanıltıcı — bazı
sağlayıcılar aynı bölümü iki kez veriyor); eşitlikte `source_score` puanı.
İstemcide `aktifPlugin`/`aktifUrl` state'i: `load_item` ve `resolve_sources`
bunları kullanıyor, liste değişince oynatma o sağlayıcıya geçiyor ve oynayan
bölüm **sezon+bölüm numarasıyla** yeniden eşleniyor. Çağrı yalnız Bölümler
sekmesi açılınca (tarama ~7 sn). Panel başlığında listenin kaynağı yazıyor.

Canlı ölçüm: **Reacher 28 → 43** bölüm (S4B10), 7.6 sn · **Dead City 7 → 21**
(S3B8), 6.9 sn. Kapı: `stream/tests` 159 OK, smoke YEŞİL.

# SIRADAKİ İŞ — açık

Belirlenmiş bir sonraki iş yok. Cihaz denemesinden gelen geri bildirim belirler.
Açık gözlem: Dean "bazen duruyor" dedi (0.6.7 kapanmayı kesti, **durma** ayrı
olabilir) — tekrarlarsa `docker logs netmovies-stream | grep "Proxy hatası"` ve
`client_log` ile segment/tampon tarafına bak.

## Bugün biten (cihazda görülmedi — Dean denerse bildirsin)

0.6.1 Bölümler paneli + alt bar kaydırma + odak dönüşü · 0.6.2 sonraki bölüm
kartı 70 sn · 0.6.3 "Bölümler yükleniyor…" · 0.6.4 Ajanda **İzlemediklerim**
(`/api/v1/unwatched`) · 0.6.5 alt bar tuşları indeks tabanlı gezinme ·
0.6.6 bölüm sayfası kartı doğrudan o bölümü açar · 0.6.7 oynayan bölüm
kendiliğinden kapanmıyor + bağlantı hatasında WARP · **0.7.0 en zengin bölüm listesi**.

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
