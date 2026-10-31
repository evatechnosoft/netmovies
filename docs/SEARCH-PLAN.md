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

## Değer/çaba sırası
| # | İş | Değer | Çaba |
|---|----|------|------|
| 1 | FilmMakinesi eklenti aramasını düzelt (+ chain_scan) | Yüksek | S |
| 2 | Boş `/search` sonucunu önbelleğe yazma | Orta | XS |
| 3 | Altın sorgu seti + ölçüm betiği (`scripts/search_eval.py`) | Yüksek (kapı) | S |
| 4 | Tek normalize + birleşik/ayrık varyant, `_varyantlar` ↔ `query_variants` birleşimi | Yüksek | S |
| 5 | TMDB kanonik kimlik: TR + orijinal ad fan-out, `tmdb_id` ile tekilleştirme | Çok yüksek | M |
| 6 | "Henüz yok / sinemada" kartı + takip listesi | Yüksek (Dean'in sorusu) | M |
| 7 | RRF sıralama | Orta | S |
| 8 | Gemini yedek yorum (0 sonuçta) | Orta | S |
| 9 | Web başlık aramasını `search_all`'a taşı | Düşük | S |
| 10 | `global_search` onar ya da sil (kimse kullanmıyor → sil) | Düşük | XS |
| 11 | provider-scout: hdfilmcehennemi.nl / filmmakinesi.to canlılık ve içerik farkı | Orta | S |

## Başarı kriteri
- Altın sette isabet oranı önce/sonra raporlanır; hedef ≥ %90 ilk 3'te doğru başlık.
- p95 `search_all` ≤ 6 sn (sıcak), tek kaynak askıda kalınca da.
- Whalefall gibi vizyondaki film: 0 sonuç yerine "sinemada" kartı.

## Açık sorular
- Bu planın brainstorm çıktısı aşağıdaki bölüme eklenir.
