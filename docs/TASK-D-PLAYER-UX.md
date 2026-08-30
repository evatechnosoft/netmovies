# Task D — Oynatıcı Girdi Şeması + Listeler + Mouse Mode (best-practice plan)

Dean'in ham istekleri organize edildi. YouTube/Netflix TV davranışı referans.
Sıra: küçük+bağımsız → büyük+veri-bağımlı. Her faz **Mi Box'ta test → sonra OTA**.

## Oynatıcı iki durumu (best practice)
- **Kontroller gizli** (immersive izleme)
- **Kontroller görünür** (Media3 native seekbar + butonlar; D-pad gezinir)

## D1 — Oynatıcı D-pad şeması (bağımsız, yeni veri gerekmez)
| Girdi | Davranış |
|---|---|
| OK (tek) | Kontrolleri aç + play/pause toggle |
| OK (basılı tut) | ⚙ Ayarlar (çark) menüsünü aç |
| DPAD ◀ / ▶ (tek, hızlı ardışık) | Bastığın yöne biriktirmeli sarma: 10→20→30sn (ekranda "+30sn" göstergesi) |
| DPAD ◀ / ▶ (basılı tut) | Sürekli hızlı sarma (bırakınca uygular) |
| DPAD ▲ / ▼ veya ayrı buton | Büyük atlama: **film → 1 dk**, dizi → sonraki/önceki bölüm (D3'e bağlı) |

- Native controller'a dokunmadan, player kök view'ına `onKeyEvent` handler eklenir; kontroller
  gizliyken bizim şema, görünürken native davranış. Çift-bas biriktirme YouTube mantığı.
- Sarma adımı ayarlanabilir (varsayılan film 60sn / hızlı-bas 10sn).

## D2 — Poster context menu + favoriler (yerel depolama)
- Ana sayfada poster **basılı tut → 4'lü menü**: Oynat · Favori · Listeye ekle · Detay.
- Favori/liste kalıcılığı: önce **DataStore/Room** (yerel). İleride backend'e senkron.
- "Favori gibi izlemiyor / diğer listelere ekleme" bu fazda çözülür.

## D3 — Dizi bölümleri + "izlerken gez" (veri-bağımlı, büyük)
- Oynatıcıya **bölüm listesi** gelmeli (API: `load_item`/series yapısı) → player'da ileri/geri bölüm.
- "Alta pop liste, sonrakine bakarken oynamaya devam": video oynarken alttan bottom-sheet liste;
  seçim yapılana kadar mevcut oynatma sürer (mini-player mantığı).

## D4 — Mouse mode (deneysel)
- Ana sayfada boş alana **basılı tut → imleç modu** toggle; D-pad ile imleç sürülür, OK tıklar.
- Sentetik dokunuş dağıtımı Compose'da zahmetli → en son, izole faz.

## Bağımlılıklar / riskler
- D1: native controller ile çakışma riski → gerçek Mi Box testi şart.
- D2: kalıcılık katmanı yeni (DataStore) — mimari ekleme.
- D3: API'de series/episode akışı yok — engine/stream tarafı iş.
- D4: en deneysel; D1-D3 oturmadan başlanmaz.
