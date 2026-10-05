# Plan — TV kişiselleştirme (taşı / sabitle / gizle)

> Sahibi: `tv-urun` ajanı (`.claude/agents/tv-urun.md`). Başlangıç: 5 Ekim 2026, Dean:
> "bu ve buna benzer yapıları, web'de trend olanları plana yaz, kontrol edip ekleyelim".
> Ortak jest: OK basılı tut → sarı, SOL/SAĞ taşı, OK bırak (kaydeder), GERİ iptal.
> Kod: `client-tv/.../ui/Tasima.kt` (`TasimaDurumu`, `tasimaTuslari`, `TasimaIpucu`).

## Web'de ne var (Ekim 2026)
Basılı tut → taşıma modu sektör standardı: LG webOS (OK basılı → SOL/SAĞ → OK),
Apple tvOS (jiggle), Samsung, Google TV (uzun bas → "Move" menüsü), Channels DVR
("Rearrange Channels"). Fire TV elle taşımayı kaldırıp "öne sabitle"ye geçti.
- https://www.howtogeek.com/694783/how-to-customize-the-google-tv-home-screen/
- https://support.apple.com/en-ca/guide/tv/atvbad14dc6a/tvos
- https://www.samsung.com/in/support/tv-audio-video/how-to-move-and-rearrange-apps-in-samsung-tv/
- https://www.aftvnews.com/how-to-move-and-organize-fire-tv-apps-with-the-new-pinning-feature/
- https://getchannels.com/docs/apps/usage/manage-channels/
- Satır yönetimi: Plex (sabitleme sırası = ana ekran sırası), Infuse 8 (satır ekle/kaldır/sırala).
  https://www.howtogeek.com/how-to-customize-your-plex-interface-for-easier-navigation/ ·
  https://community.firecore.com/t/infuse-home-screen-layout/19547
- "Devam Et"ten kaldır + Geri al: Disney+ (2025), Netflix.
  https://9to5mac.com/2025/03/24/disney-plus-remove-continue-watching/

## Maddeler (değer/çaba sırası)

| # | Desen | Bizde | Çaba | Durum |
|---|---|---|---|---|
| 1 | Gözat kaynak çiplerini taşı | `BrowseScreen.kt` `SourceChips` | S | **Yapıldı** 0.9.54 |
| 2 | Taşıma ipucu + GERİ = iptal (eski sıra) | `Tasima.kt` `TasimaIpucu`, `iptal()` | S | **Yapıldı** 0.9.55 |
| 3 | Ana sayfa kişisel çiplerini taşı (Devam edenler, Kayıtlar, Favoriler…) | `HomeScreen.kt` `SegmentChip`, prefs `home_segment_order` | S | **Yapıldı** 0.9.55 |
| 4 | Çip sırası = ana sayfa raf sırası (Plex) | raf sırası sabit kodda (`HomeScreen.kt` sections) | M | Sırada |
| 5 | Raf yönetimi ekranı: ana sayfa raflarını sırala/gizle (Infuse) | yok | M | Sırada — `TasimaDurumu` dikey listede yeniden kullanılır |
| 6 | Kaynağı TV'den gizle → Yeni Çıkanlar'dan da düşsün | admin'de var (`AdminScreen.kt` `hidden_providers`) | S | Kısmen var; Gözat'tan kısayol sırada |
| 7 | "Devam Et"ten kaldır + Geri al | toplu ekran var (`DevamTemizleScreen.kt`); poster menüsünde tek tık yok | S | Sırada |
| 8 | "Bunu gösterme" (Yeni Çıkanlar/öneri dışı) | yok | M | Sırada |
| 9 | Karma sıralama: elle sabitlenen önde, gerisi `source_score` | puan var (`/api/v1/source_score`) | M | Değerlendir — elle sıra yeni geldi, önce kullanılsın |
| 10 | Profil (çocuk/misafir) | yok | L | Reddedildi — tek kullanıcı, YAGNI |
| 11 | Canlı TV favori/koleksiyon (Channels DVR) | kanal favorisi var | M | Reddedildi — canlı TV UI'ı Dean kararıyla gizli |

## Doğrulama
- Birim: `CipSirasiTest`, `TasimaDurumuTest` (taşı/kenar/iptal/değişmeyen sıra kaydedilmez).
- Emülatör (`evabench_shot`, gerçek sunucu): ana sayfa ve Gözat'ta uzun bas → sarı çip + ipucu,
  SAĞ taşıdı, GERİ eski sıraya döndü, `prefs`'e yazılmadı; taşı-geri-OK değişmeyen sırayı yazmadı.
- Cihaz (Mi Box): doğrulanmadı.
