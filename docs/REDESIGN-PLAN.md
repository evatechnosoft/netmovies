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

## Veri katmanı kararı (ADR — F3/F4/F7'nin temeli)

Dean isteği: "site değişse de kayıtlı", "cihazlar arası" (telefon/mibox/pc aynı).
→ localStorage YETMEZ (cihaz-bazlı). **Merkezi sunucu tarafı** gerekir.
`admin_config` zaten `/data/*.json` merkezi pattern kurmuş.

**Öneri: SQLite (`/data/netmovies.db`)** — izleme geçmişi (içerik_id, dakika,
bölüm, güncelleme_zamanı), favoriler, takip. İçerik kimliği **site-bağımsız**
olmalı (başlık+yıl normalizasyonu / imdb-tmdb eşleme; RİSK: kaynaklar farklı id
veriyor → normalize katmanı gerek). HANDOFF backlog item 5 (ADR-3) ile aynı yön.

## Faz planı

### Faz A — İzole refactor (Dean onayı gerekmez, PARALEL — ⏳ BAŞLADI)
- **F8** VideoPlayer bölme → subagent `vp-split`
- **F6** i18n TR/EN → subagent `i18n-trim`
- **F9** backend ince temizlik (aggregate mantığını Libs'e) → sıradaki

### Faz B — Veri katmanı + ana sayfa (SQLite onayı sonrası)
- Veri servisi: `/data/netmovies.db` + izleme/favori/takip API (engine veya stream)
- İçerik kimliği normalize katmanı (site-agnostik eşleme)
- **F1** ana sayfa segment pill + tile rafları (Yeni Çıkanlar zaten var → segment'e taşı)
- **F3** izlemeye devam rafı + dakika takibi (player → API)

### Faz C — Etkileşim cila
- **F4** kart davranışı (ara ekran kaldır, son bölüme atla) — F8 + F3 sonrası
- **F2** kategori-içi hızlı atlama pill
- **F5** ayar çark menüsü
- **F7** favori/takip UI (veri Faz B'de hazır)

## Paralel çalışma disiplini
- Subagent'ler **commit etmez**; dosya yazar, PM (ben) doğrular + commit'ler (kanıt kapısı).
- İzole dosya setleri paralel; ortak dosyaya (ana sayfa, içerik, veri) dokunan işler sıralı.
- Her feature: davranış kanıtı (syntax/compile) + Dean'in evinde runtime doğrulama.
