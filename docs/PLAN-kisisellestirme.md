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
| 4 | Çip sırası = ana sayfa raf sırası (Plex) | Bizde raflar sağlayıcı değil kategori; doğrudan raf sırası (#5) karşılıyor | M | **Birleşti → #5** |
| 5 | Raf yönetimi: ana sayfa raflarını sırala/gizle (Infuse) | Ayarlar → "Rafları düzenle" (`HomeScreen.kt` `RafDuzenleMenu`), prefs `home_row_order`/`home_row_hidden` | M | **Yapıldı** 0.9.56 |
| 6 | Kaynağı Gözat'tan gizle → Yeni Çıkanlar/aramadan da düşer | taşırken ▼ = gizle (`SourceChips` `onGizle` → admin `hidden_providers`); geri açmak Yönetim Paneli | S | **Yapıldı** 0.9.56 |
| 7 | "Devam Et"ten çıkar | poster menüsü ▼ Listeler → ⟲ (iki basış: ilerleme sunucuda silinir, geri alınamaz) | S | **Yapıldı** 0.9.56 |
| 8 | "Raflarda gösterme" | poster menüsü ▼ Listeler → 👁‍🗨 (prefs `hidden_titles`); geri: tekrar bas ya da Rafları düzenle → altta liste | M | **Yapıldı** 0.9.56 |
| 9 | Karma sıralama: elle dizilen önde, gerisi puana göre | `varsayilanKaynakSirasi` — elle dizilmemiş çipler `source_score`'a göre | M | **Yapıldı** 0.9.56 |
| 10 | Profil (çocuk/misafir) | yok | L | Reddedildi — tek kullanıcı, YAGNI |
| 11 | Canlı TV favori/koleksiyon (Channels DVR) | kanal favorisi var | M | Reddedildi — canlı TV UI'ı Dean kararıyla gizli |

## Doğrulama
- Birim: `CipSirasiTest`, `TasimaDurumuTest` (taşı/kenar/iptal/değişmeyen sıra kaydedilmez).
- Emülatör (`evabench_shot`, gerçek sunucu): ana sayfa ve Gözat'ta uzun bas → sarı çip + ipucu,
  SAĞ taşıdı, GERİ eski sıraya döndü, `prefs`'e yazılmadı; taşı-geri-OK değişmeyen sırayı yazmadı.
- 0.9.56 birim: `VarsayilanKaynakSirasiTest` (87 test yeşil). Emülatör: "Rafları düzenle" açılıp
  rafları listeledi; dikey taşıma/gizle ve poster menüsünün yeni iki düğmesi emülatörde
  denenemedi (dokunma kipi tuşları arka ekrana yolladı) — ortak `Tasima.kt` koduyla aynı yol.
- Cihaz (Mi Box): doğrulanmadı.
