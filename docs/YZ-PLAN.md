# YZ Planı — telefonda çevrimdışı arama (Gemma) + sunucu desteği (Gemini)

Sahibi: `yz-muhendisi` ajanı (`.claude/agents/yz-muhendisi.md`). Tarih: 2026-09-27.

## Hedef

Telefon (Kumanda kipi) sunucuya ulaşamasa da "Ömür Usta'nın son bölümü", "bu pazar
ne var", "Reacher 2. sezon 3" gibi cümlelerle arayabilsin. Önümüzdeki **2-3 haftada**
izlenecekler telefonda hazır dursun. Ağ gelince seçilen içerik TV'de açılsın.

Başarı ölçütü:
- Uçak kipinde 20 sabit test cümlesinin ≥ 18'i doğru içeriği ilk 3 sonuçta bulur.
- Model yokken (bulanık arama) aynı kümede ≥ 14/20 — model gerçekten fark yaratıyor mu, sayıyla.
- Yanıt < 2 sn (model ısındıktan sonra), açılışta model yükleme arka planda.

## Mimari

```
ZimaOS (stream)                                   Telefon
─────────────────                                  ─────────────────────────────
/api/v1/katalog_ozeti?gun=21  ──(Wi-Fi, günde 1)──▶ ozet.json (dosya)
  takip + favori + izlenecek                          │
  + ajanda (bugün..+21 gün)                           ▼
  + Devam Et + favori kanallar          sorgu ─▶ [Gemma: cümle → niyet JSON]
  + Gemini zenginleştirme (bir kez,                   │   (yoksa: kural ayrıştırıcı)
    cache): takma ad, TR/EN başlık,                   ▼
    tür, tek satır özet                   eşleştir (kod, bulanık) ─▶ sonuç listesi
                                                       │
/api/v1/model/gemma (dosya, LAN)  ──(bir kez)──▶ gemma.litertlm   ▼
                                               ağ var: remote/play → TV
                                               ağ yok: kuyruğa al, ağ gelince gönder
```

İş bölümü: **Gemini** ağır ve seyrek işi yapar (sunucuda, günde bir). **Gemma**
yalnız serbest cümleyi alan-değer çiftine çevirir. **Eşleştirme her zaman kod.**
Model uydurma başlık üretse bile listede olmayan içerik sonuç olamaz.

## Aşamalar

**A1 — Anlık görüntü (modelsiz, en büyük kazanç).** Sunucuda `katalog_ozeti` ucu:
mevcut `following`, `favorites`, `lists/izlenecek`, `continue_watching`,
`agenda?view=month` (bugün..+21 gün) ve `quick_channels ∩ fav_channels` birleşimi.
Telefon Wi-Fi'deyken günde bir çeker, dosyaya yazar. Arama ekranı ağ yoksa bu
dosyada bulanık arar. → Tek başına çevrimdışı aramayı açar.

**A2 — Gemini zenginleştirme.** Aynı uçta her başlık için bir kez (cache'li)
Gemini'den: `takma_adlar` ("abi" → "A.B.I.", "the odyssey" → "odyssey"), orijinal/TR
başlık, tür, tek satır özet. Mevcut `gemini.py` kapısı ve `title_rescue` deseni kullanılır.
Kota: yalnız yeni başlıklar, günde bir.

**A3 — Kural ayrıştırıcı + test kümesi.** "S2B3", "2. sezon 3. bölüm", "pazar",
"yarın", "canlı", kanal adları. 20 cümlelik sabit küme birim testi olur;
her sonraki aşama bu sayıyla ölçülür.

**A4 — Cihaz üstü model.** Önce cihaz ML Kit GenAI Prompt API (Gemini Nano)
destekliyor mu bak — destekliyorsa indirme yok. Değilse LiteRT-LM + Gemma:
- Model ZimaOS'tan LAN'dan iner (`/DATA/AppData/netmovies/models/`), APK'ya gömülmez.
  AI Edge Gallery'nin indirdiği kopya bizim uygulamadan okunamaz.
- İlk aday Gemma 3 1B IT (~0.5 GB). Test kümesinde yetmezse Gemma 4 E2B (2.6 GB).
- Model yalnız niyet JSON'u üretir; arama fonksiyonu `@Tool` olarak verilir.
- Açılışta yüklenmez; arama ekranı açılınca arka planda ısınır, 5 dk boşta kalınca bırakılır.

**A5 — Çevrimdışı komut kuyruğu.** Ağ yokken "TV'de aç" kaydedilir, sunucu
bulununca tek sefer gönderilir (TV'nin tek slotluk kuyruğuyla uyumlu: sonuncusu geçerli).

Sıra: A1 → A3 → A2 → A4 → A5. A1+A3 modelsiz çalışan bir sürüm verir; model
bunun üstüne ölçülerek eklenir.

## Açık sorular (Dean)

1. Telefonun modeli ne, "gemma 3b" hangi uygulamada duruyor? (AI Edge Gallery ise
   dosyayı paylaşamıyoruz, kendi kopyamızı indiririz. Model adı Gemma 3 1B mi, 3n E2B mi?)
2. 2-3 haftalık liste: yalnız takip ettiklerin mi, yoksa ajandadaki popüler yeni
   diziler/filmler de mi girsin? (Öneri: ikisi de, popülerler ayrı başlıkta.)
3. Saat de aynı anlık görüntüyü kullansın mı? (Saatte model yok, yalnız liste.)

## Kaynaklar

- LiteRT-LM Android: https://developers.google.com/edge/litert-lm/android
- Gemma 4 E2B .litertlm: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Gemma 3 1B: https://huggingface.co/litert-community/Gemma3-1B-IT
- ML Kit GenAI / Gemini Nano: https://developers.google.com/ml-kit/genai

## Ev YZ geçidi (2026-09-27, ZimaOS)

`infra/yz/` → ZimaOS `/DATA/AppData/yz/`. Tek OpenAI uyumlu uç:
`http://192.168.1.186:4000/v1`, anahtar yok (yalnız ev ağı, tünele açık değil).
- `model: "gemini"` → gemini-3.5-flash-lite; anahtar NetMovies panelindeki
  `admin.json`'dan açılışta okunur, diske/uygulamalara yazılmaz. Kota/hata → `yerel`.
- `model: "yerel"` → Ollama `gemma4:12b` (Quadro P620 GPU + CPU). İndirme hattı
  doldurup TV'yi dondurdu; kalanı gece 03:00'te iniyor (`gece-indirme.log`).
- Gemini Nano sunucuda KOŞMAZ: yalnız destekli telefonda AICore/ML Kit üzerinden.
- stream `gemini.sor()` `YZ_GECIT_URL` doluysa geçide gider (boşsa Google doğrudan).
  `voice.py` ses taşıdığı için kendi doğrudan çağrısında kalır.

## Gemini Nano kaydı (2026-09-27)

`com.google.mlkit:genai-prompt:1.0.0-beta4` (minSdk 26). Telefon kiplerinde
(Kumanda / Bu cihazda izle) açılışta `checkStatus`; DOWNLOADABLE + Wi-Fi ise
`download()`. Sonuç `POST /api/v1/yz/cihaz` → `/data/yz_cihazlar.json`
(`GET` aynı liste). Ayarlar'da "YZ: …" teşhis satırı. `NanoNiyet.cozumle(cumle)`
hazır, henüz aramaya bağlı değil. Emülatörde (Pixel Tablet, API 35) UNAVAILABLE;
Dean'in telefonunda henüz ölçülmedi.
