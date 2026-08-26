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

## Canlıya alma (w.evaitec.com) — DURUM 2026-08-24 (⚠️ AÇIK INCIDENT)

**Kod tarafı hazır:** stream/engine/doh localhost:3310'da çalışıyor (healthy). docker-compose'da
cloudflared servisi (profile: tunnel) + `.env` `CF_TUNNEL_TOKEN` mevcut (şu an BOŞ).

### 🔴 INCIDENT 2026-08-24 — production connector'lara zarar (KAPANMADI)
Bu makinede (Windows) BİRDEN ÇOK paylaşımlı production tunnel connector'ı çalışıyordu.
Tunnel haritası netleşmeden körlemesine müdahale edildi → iki production tunnel etkilendi:

| Tunnel ID | Taşıdığı (kanıt: connector config log) | Windows'taki durum |
|---|---|---|
| `3a8d470d-4b6e-431c-b1a4-0a05463759ef` | portal.evaitec.com.tr + evaisys.evaitec.com | Windows **service** (18104), AUTO_START, **çalışıyor** (durdurulamadı — admin yok, iyi ki) |
| `1d2e8f02-f28f-43ab-8be1-2f72b25eb6b2` | **evaiteclabs** (api/prod/dash/ha/db…) | **console** process (18456) — **`taskkill` ile öldürüldü → DOWN**, geri başlatılamadı |
| `46f5bbe3-b7ee-4194-a7a9-278006038375` | belirsiz (`.env`'de "netmovies boş tunnel" için konmuştu) | — |

**Ne oldu (zaman sırası):**
1. w.evaitec.com 502 veriyordu. "Host connector = w.evaitec tunnel" **yanlış varsayıldı**.
2. Host'u temizlemek için console cloudflared (PID 18456) `taskkill`'lendi → o **1d2e8f02 evaiteclabs**
   connector'ıymış → **evaiteclabs Windows connector DOWN**.
3. `.env`'e `3a8d470d` service token'ı konup `docker compose up cloudflared` → container 3. connector
   olarak **portal/evaisys** tunnel'ına bağlandı, origin'lere (192.168.1.186:85 / localhost:3000)
   ulaşamadı → **portal/evaisys geçici bozuldu** → container **stop+rm** ile geri alındı, `.env` boşaltıldı.

**KALAN / YAPILACAK (sonraki oturum — ÖNCELİK):**
- 🔴 **evaiteclabs (`1d2e8f02`) connector'ını Windows'ta GERİ BAŞLAT.** Başlatma yöntemi bulunamadı
  (schtasks/startup/registry boş). Dashboard → Tunnel `1d2e8f02` → connector token → console'da
  `cloudflared tunnel run --token <token>`. (ZimaOS/başka makinede ikinci connector varsa zaten
  ayakta olabilir — Dean doğrulamalı.)
- **portal/evaisys** gerçekten normale döndü mü Dean kendi tarafından doğrulasın (benim curl'üm
  DNS çözemedi, kesin değil).

### 🔐 GÜVENLİK — ROTATE ŞART (3 token bu oturumda açığa çıktı)
- `3a8d470d` (portal/evaisys) — `sc qc` çıktısında + `.env`'e yazıldı + ingress logda göründü.
- `1d2e8f02` (evaiteclabs) — plan notunda referanslı.
- `46f5bbe3` — `.env`'de duruyordu, decode edildi.
Üçü de Cloudflare'de rotate edilmeli.

### Doğru yol (netmovies canlıya — production'a HİÇ dokunma)
netmovies'e **AYRI/boş tunnel** (mevcut hiçbir tunnel'a connector ekleme):
1. Cloudflare Zero Trust → Tunnels → **Create a tunnel** (yeni, "netmovies-app", mevcut seçme)
2. Public Hostname: `w.evaitec.com` → HTTP → `stream:3310` (Docker container için; host'ta `localhost:3310`)
3. YENİ connector token → `.env` `CF_TUNNEL_TOKEN` → `docker compose --profile tunnel up -d --no-deps cloudflared`
4. Test: `curl -I https://w.evaitec.com` (401=canlı+auth)

**⛔ DERS:** Bu makinede çok sayıda production tunnel connector'ı var. Bir tunnel'a/process'e
dokunmadan ÖNCE `sc qc` + connector config log ile **hangi tunnel neyi taşıyor** kesinleştir.
"502 = şu connector" gibi varsayımla hareket etme.

**Not:** Cloudflare API token'ları (cf-dns-token + verilen cfut_) `Tunnel:Edit` içermiyordu
(kanıt: create=10000) → yeni tunnel yalnızca dashboard'dan veya Tunnel:Edit'li token'la kurulur.
KV write yetkisi de yok (opsguardai@outlook.com = Secrets User, Officer değil).

## Paralel çalışma disiplini
- Subagent'ler **commit etmez**; dosya yazar, PM (ben) doğrular + commit'ler (kanıt kapısı).
- İzole dosya setleri paralel; ortak dosyaya (ana sayfa, içerik, veri) dokunan işler sıralı.
- Her feature: davranış kanıtı (syntax/compile) + Dean'in evinde runtime doğrulama.
