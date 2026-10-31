# Arama Planı — gelişmiş/modern arama (31 Eki 2026)

Persona: `search-engineer` (arama), `provider-scout` (kaynak kapsaması), `yz-muhendisi` (YZ yorum).

## Neden
- "Whalefall / Balina Düşüşü" 0 sonuç döndü. Web'de sayfası olan siteler:
  hdfilmcehennemi.nl, filmmakinesi.to, cehennemfilmizle, cfilmizle, jetizle, fullfilmizlebox…
  Film 16 Eki 2026'da vizyona girdi, dijitalde yok, web kopyaları büyük olasılıkla CAM.
- Bizim eklentilerde durum (31 Eki, ZimaOS):
  - HDFilmCehennemi (`.land`): "matrix" için 4 sonuç geliyor, Whalefall sayfası 404. `.nl` 403 dönüyor (Cloudflare olabilir).
  - **FilmMakinesi eklentisi bozuk:** "matrix" için bile 0 sonuç. Site (`filmmakinesi.co/?s=matrix`) sonuç veriyor, yani sorun eklentinin ayrıştırmasında.
  - JetFilmizle: yalnız ilgisiz "Balina" başlıkları geliyor.
- Engine `global_search` her sorguda 0 dönüyor. Muhtemel neden: `elem.plugin = name` ataması pydantic'te hata veriyor, çıplak `except` bunu yutuyor. Bu uç noktayı çağıran istemci yok.

## Mevcut akış (özet)
`search_all` → `ayristir` (niyet) → `_varyantlar` → sağlayıcı başına `/search` (12 sn, ilk dolu varyantta durur)
→ `_alakali` (alt dize, kaynak başına 8) → ağırlık + `source_score` → TMDB yıl/puan (başlıkla)
→ `tekillestir` (başlık + yıl) → `sirala` → `load_item` (ilk 14).

## Hedef mimari
1. **Normalize:** tek fonksiyon, Türkçe uyumlu (İ/ı, NFKC, aksan); birleşik/ayrık varyant ("whalefall" ↔ "whale fall").
2. **Kanonik kimlik:** sorgu bir kez TMDB'de çözülür → `tmdb_id`, orijinal ad, TR ad, alternatif adlar. Sağlayıcılara TR ve orijinal ad paralel gider.
3. **Fan-out:** kaynak başına zaman aşımı + devre kesici (`source_score` ile). Zaman aşımı sıradaki varyantı denemeyi engellemez.
4. **Tekilleştirme:** anahtar `tmdb_id`; yedek olarak başlık + yıl bulanık eşleşme (RapidFuzz, yıl zorunlu).
5. **Sıralama:** kaynak sıraları RRF (k=60) ile birleşir, kullanıcı ağırlığı sonradan uygulanır.
6. **Önbellek:** sorgu→tmdb_id 7 gün; kaynak sonucu 5 dk; boş sonuç 30 sn ya da hiç.
7. **YZ:** Gemini yalnız 0 sonuç / düşük güvende sorguyu yeniden yazar, sonucu önbelleğe alır.
8. **"Henüz yok" yanıtı:** TMDB tanıyor ama kaynak 0 ise TV'de "Sinemada (16 Eki), dijitalde yok" kartı + takip listesine ekle.
9. **Yeni çıkan takibi:** takip listesi günlük taranır (TMDB `release_dates` + kaynak araması), bulununca bildirim. Otomatik indirme YOK (hafıza: kayit-yalniz-elle).

Kaçınılacaklar: ham puan toplamak, yalnız başlıkla tekilleştirmek, tüm katalog için `/discover`, `/movie/changes`'ı yeni çıkan akışı saymak, Stremio protokolünü baştan kurmak.

## Değer/çaba sırası (brainstorm sonrası, v2)
| # | İş | Değer | Çaba |
|---|----|------|------|
| 1 | FilmMakinesi teşhisi, sonra düzeltme (+ chain_scan) | Yüksek | S |
| 2 | Altın sorgu seti + ölçüm betiği (`scripts/search_eval.py`) | Yüksek (kapı) | S |
| 3 | Zaman bütçesi: kaynak başına devre kesici, TMDB çağrıları paralel, `_bolum_sayilari` beklemesi kısalır | Yüksek | S |
| 4 | Tek normalize + birleşik/ayrık varyant, `_varyantlar` ↔ `query_variants` birleşimi | Yüksek | S |
| 5 | TMDB kanonik kimlik: TR + orijinal ad fan-out, `tmdb_id` ile tekilleştirme | Çok yüksek | M |
| 6 | "Henüz yok / sinemada" kartı → mevcut takibe bağlı (`following.py`'ye `movie` tipi) | Yüksek (Dean'in sorusu) | M |
| 7 | 0 sonuçta "web'de şu sitelerde var" satırı (web başlık araması `search_all`'a) | Yüksek | S |
| 8 | provider-scout: hdfilmcehennemi.nl / filmmakinesi.to (403) canlılık ve içerik farkı | Orta | S |
| 9 | `global_search` sil (önce grep ile çağıran yok kanıtı) | Düşük | XS |
| 10 | Takip edilen yabancı diziler aylık ajandada (Dean, 31 Eki) | Yüksek | S |

Ertelendi (altın set ihtiyacı kanıtlarsa geri gelir): RRF sıralama, Gemini yeniden yazımı,
RapidFuzz, üç katmanlı önbellek.
Çıkarıldı: "boş sonucu önbelleğe yazma" — zaten yazılmıyor (`stream/Public/API/v1/Libs/__init__.py:162`).

### Madde ayrıntıları
- **#1 Teşhis:** `FilmMakinesi.py:69` `poster-lang` rozeti olmayan kartları atıyor; `search()`
  HTTP durumunu kontrol etmiyor (:83-84). Sitenin `?s=matrix` sayfasında `poster` sınıfı 1,
  `/film/` bağlantısı 0 (31 Eki) — arama sonucu farklı biçimde/JS ile geliyor olabilir, önce HTML'e bak.
- **#2 Altın set:** ≥30 sorgu, iki kova: (a) bulunabilir (beklenen `tmdb_id` + ilk 3) ve
  (b) henüz yok (beklenen: "sinemada" kartı). Türkçe İ/ı, birleşik/ayrık ad, TR/orijinal ad çiftleri içerir.
- **#3 Zaman:** bugünkü yapıda p95 ≤ 6 sn imkânsız (12 sn kaynak zaman aşımı, sıralı TMDB,
  `_bolum_sayilari` 12 sn'ye kadar). Önce ölç (#2), sonra hedef koy.
- **#6 Takip:** `watch_store.list_user_list("takip")`, `/following`, TV `showFollowing`
  (`MainActivity.kt:445`) zaten var; yalnız dizi (`search/tv`). Film eklenir, ikinci liste açılmaz.
  Bulununca ana ekranın ilk rafının başında "Artık izlenebilir" posteri (tek OK); günlük tarama
  mevcut `following`/`agenda` TMDB çağrısına eklenir, yeni zamanlayıcı/push yok. Otomatik indirme YOK.
- **#10 Aylık ajanda + takip:** `agenda.py?view=month` bugün yalnız TMDB `discover`
  popüler dizilerini gösteriyor; takip listesi ayrı uçta (`/following`, `next_episode_to_air`).
  Takipteki diziler aylık görünüme birleşir, en üstte ve işaretli; popüler adaylarla aynı dizi
  tekilleşir (`tmdb_id`). Mevcut `_bolum_satiri`/`gunlere_bol` kullanılır, yeni uç yok.
- **Sesli arama/trend zaten var:** `/voice` 0 sonuçta da aynı kartı göstermeli; boş arama
  ekranında mevcut popüler/ajanda rafı. Ayrı "deep search" modu gerekmez.

## Başarı kriteri
- Altın sette isabet oranı önce/sonra raporlanır; hedef ≥ %90 ilk 3'te doğru başlık.
- p95 hedefi #2 ölçümünden sonra konur.
- Whalefall gibi vizyondaki film: 0 sonuç yerine "sinemada" kartı + web'de bulunduğu siteler.

## Brainstorm (Takım Pi, 31 Eki)
- **reviewer (REWORK):** #2 zaten yapılmış; altın set tanımsız; p95 hedefi mevcut zaman
  aşımlarıyla tutmaz; FilmMakinesi için teşhis önce; .to → .co; takip listesi zaten var.
- **tv-urun (REWORK, küçük):** #6 mevcut takibe bağlanmalı; "web'de var" satırı Dean'in
  sorusunun birebir cevabı, değeri yükseldi; RRF/Gemini/RapidFuzz YAGNI. Örnek UX:
  Plex ortak izleme listesi (techcrunch.com/2022/04/05/huge-plex-update-…), Google TV
  "izlenebilir olunca bildir" (support.google.com/googletv/answer/10059391).
- v2 sıralaması iki görüşün birleşimidir.
