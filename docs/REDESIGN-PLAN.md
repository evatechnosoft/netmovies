# NetMovies — Yeniden Yapılandırma & UX Planı

> Kaynak: Dean vizyon dökümü (2026-08-24). Bu dosya PM backlog'udur; iş bittikçe
> `docs/HANDOFF.md` §6 güncellenir. Prensip: **mikro modüler, geliştirilebilir,
> düzeltilebilir, site-bağımsız.** Tek kullanıcı (Dean), auth basit kalır.

## Feature envanteri (Dean vizyonundan ayrıştırılmış)

| # | Feature | Öz | Bağımlılık |
|---|---|---|---|
| F1 | **Ana sayfa segment/tile mimarisi** | Üstte segment pill'ler: Yeni Çıkanlar · İzlemeye Devam · Favoriler · Takip Edilenler · (kategoriler). Tile tile görünürlük. | F3 |
| F2 | **Kategori-içi hızlı atlama** | DiziYou'da "Aksiyon" gibi ayrımlar aşağıda kalıyor; üstte pill/buton ile o bölüme zıpla. | — |
| F3 | **Site-agnostik izleme geçmişi** | Dakika takibi içerik kimliğine bağlı (site değil). Site değişse de "kaldığın yer" korunur. Cihazlar arası (sunucu /data). | veri katmanı |
| F4 | **İçerik kartı davranışı** | Resme basınca gereksiz "açıklama + bölümlere bak" ara ekranı ÇIKMASIN. Hızlıca son/kaldığın bölüme geç; hiç izlenmediyse 1. bölüm; istenirse bölüm seç. | F3 |
| F5 | **Ayarları çark menüsüne topla** | Üst bar aksiyonları (provider/plugin/dil) tek çark/settings drawer içine. | — |
| F6 | **i18n → TR/EN** | fr/hi/ru/uk/zh kaldır; dil seçici ayar içine. | — |
| F7 | **Favoriler & Takip** | Favori + takip listesi (veri + UI). | veri katmanı |
| F8 | **VideoPlayer.js modülleştirme** | 3294 satır god-class → ES modülleri. Davranış korunur. | — |
| F9 | **Backend mikro-modülleştirme** | Routers/Libs zaten ayrık; aggregate/servis mantığını Libs'e çek, ince tut. | — |
| F10 | **Plugin ekleme UI** | Admin'de "kaynak ekle": GitHub linki yapıştır → otomatik yükle. ⚠️ Bizim engine **Python** plugin çalıştırır (`engine/Plugins/*.py`); CloudStream `.cs3` (Kotlin) farklı runtime — direkt çalıştırılamaz. Kapsam netleşmeli: (a) GitHub'dan Python `.py` plugin çek+yükle, (b) M3U/liste URL ekle (M3UPlaylist zaten var), (c) CloudStream kaynağını yarı-otomatik Python'a adapte et. | F9 |

## Veri katmanı kararı (ADR — F3/F4/F7'nin temeli)

Dean isteği: "site değişse de kayıtlı", "cihazlar arası" (telefon/mibox/pc aynı).
→ localStorage YETMEZ (cihaz-bazlı). **Merkezi sunucu tarafı** gerekir.
`admin_config` zaten `/data/*.json` merkezi pattern kurmuş.

**Öneri: SQLite (`/data/netmovies.db`)** — izleme geçmişi (içerik_id, dakika,
bölüm, güncelleme_zamanı), favoriler, takip. İçerik kimliği **site-bağımsız**
olmalı (başlık+yıl normalizasyonu / imdb-tmdb eşleme; RİSK: kaynaklar farklı id
veriyor → normalize katmanı gerek). HANDOFF backlog item 5 (ADR-3) ile aynı yön.

## Kesişen ilke — P1: Progressive / lazy loading (Dean, 2026-08-24)

Sayfa **geç açmamalı**, içerik **birden** yüklenmemeli:
- İlk boyama HIZLI: server hafif iskelet + skeleton kartlar döner (bloke kaynağı beklemez).
- **"İzlemeye devam / kaldığın yer" başta** açılır (F3 — SQLite hazır olunca).
- Raflar **client-side async** dolar (skeleton → içerik); bir kaynak bloke/yavaşsa
  sayfayı geciktirmez, o raf boş/gecikmeli gelir.
- **Aşağı indikçe / bastıkça** daha fazla yüklenir (lazy / infinite / "daha fazla").
- ⚠️ Mevcut task 1 aggregate'i ana_sayfa.py'de **senkron** — bloke kaynak (RecTV)
  ana sayfayı bekletir. **Düzeltme: aggregate'i client-side lazy'ye taşı** (bu
  ilkenin ilk somut adımı, SQLite gerektirmez, akşam-güvenli).

## Faz planı

### Faz A — İzole refactor (Dean onayı gerekmez, PARALEL — ⏳ BAŞLADI)
- **F8** VideoPlayer bölme → subagent `vp-split`
- **F6** i18n TR/EN → subagent `i18n-trim`
- **F9** backend ince temizlik (aggregate mantığını Libs'e) → sıradaki

### Faz B — Veri katmanı + ana sayfa (GENİŞLEDİ, Dean 2026-08-24)
- **B1 Provider sadeleştirme** — WatchBuddy uzak-provider artığını sök: "Örnek
  Sağlayıcıyı Dene" (uzak `stream.watchbuddy.tv` → "App Store/yoğunluk" hatası
  BURADAN geliyor, bizden değil), ExampleProvider paneli, watch-party
  (watchbuddy.tv/room), app-store linkleri (fetch.js). Kendi engine sabit varsayılan;
  remote-provider mimarisi tamamen SÖKÜLMEZ (gizle/ileride kalsın — "geliştirilebilir").
- **B2 Büyük banner kaldır** — featured-hero (yükleme uzun) kaldırılır.
- **B3 Ana sayfa rafları** — direkt: **İzlediklerim (devam) · Favoriler · Yeniler**
  (client-side lazy, P1). Yeniler SQLite'sız (aggregate); İzlediklerim/Favoriler B4'e bağlı.
- **B4 Veri katmanı (SQLite `/data/netmovies.db`)** — izleme geçmişi (content_key,
  dakika, bölüm), favoriler, istatistik. stream tarafı (auth arkası, /data mount).
  İçerik kimliği **site-agnostik** normalize (başlık+yıl) — RİSK.
- **B5 Kaynak istatistikleri** — site başına içerik sayısı + izlenme (B4 üstünde).

### Faz C — Etkileşim cila
- **F4** kart davranışı (ara ekran kaldır, son bölüme atla) — F8 + F3 sonrası
- **F2** kategori-içi hızlı atlama pill
- **F5** ayar çark menüsü
- **F7** favori/takip UI (veri Faz B'de hazır)

## Oturum durumu — 2026-08-24 (devir)

**Push'lanan (dalda canlı):**
- ✅ Task 1 birleşik Yeni Çıkanlar (`0440ff9`) — NOT: senkron aggregate; B3'te client-side lazy'ye taşınacak
- ✅ Kök ölü iskelet sil + README (`92232fc`)
- ✅ F6 i18n → TR/EN (`9301a93`)
- ✅ F8 VideoPlayer god-class böl, 8 mixin modül (`5b5b99d`)
- ✅ B2 büyük banner (featured-hero) kaldır
- ✅ B4 SQLite veri katmanı (watch_store.py + watch.py, site-agnostik content_key, doğrulandı)
- ✅ Otonom dev reload (docker-compose.override.yml, watchmedo) — `docker compose config` OK

**KALAN (sonraki oturum devralsın):**
- ⏳ **B1** WatchBuddy provider artığı sök + ayar kalıcılığı (localStorage→admin_config). YAPILMADI.
- ⏳ **B3** Ana sayfa rafları (İzlediklerim/Favoriler/Yeniler, client-side lazy) + `aggregate_new` client endpoint. YAPILMADI.
  - Not: `home-redesign` subagent'i başlatıldı ama **working tree'ye çıktı üretmedi** (neden belirsiz);
    sonraki oturum B1+B3'ü sıfırdan ele almalı. API'ler HAZIR: watch.py (/continue_watching, /favorites, /favorites/toggle).
- ⏳ **B5** Kaynak istatistikleri (get_source_stats HAZIR, UI kaldı)
- ⏳ **Faz C** F2 kategori-içi zıplama · F4 kart davranışı (ara ekran kaldır, son bölüme atla) · F5 ayar çark (dil dahil) · F7 favori/takip UI · F10 plugin ekleme UI

**Doğrulanmamış (Dean'in evinde test şart):** F8 player gerçek oynatma · otonom reload runtime (watchmedo).

## Paralel çalışma disiplini
- Subagent'ler **commit etmez**; dosya yazar, PM (ben) doğrular + commit'ler (kanıt kapısı).
- İzole dosya setleri paralel; ortak dosyaya (ana sayfa, içerik, veri) dokunan işler sıralı.
- Her feature: davranış kanıtı (syntax/compile) + Dean'in evinde runtime doğrulama.
