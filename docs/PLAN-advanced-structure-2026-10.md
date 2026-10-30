# NetMovies — Gelişmiş Yapı Planı (Ekim 2026)

> Durum: **TASLAK — Dean onayı bekliyor.** Takım Pi kuralı: "başla" gelmeden kod/koşu yok.
> Kaynak: 30 Eki 2026 araştırması (mevcut durum haritası + web trend taraması + persona araştırması).
> Trend iddialarının bir kısmı yalnız arama özetine dayanıyor; o maddeler **[doğrulanmadı]** etiketli.

## Neden
Uygulama özellik olarak olgun; kırılganlık üç yerde:
1. **Kaynaklar sessizce çürüyor.** Domain/HTML değişince Dean fark edene kadar raf boş. Engine'in 80 testi CI'da koşmuyor.
2. **Görünürlük yok.** Compose healthcheck yok, metrik yok, `client_log` bellekte (her stream rebuild'de sıfır). "Takılıyor" ancak Dean söyleyince görülüyor.
3. **İstemci kodu büyüdü.** `PlayerScreen.kt` 3223 satır, PLAYER2 deneme anahtarının arkasında → iki oynatıcı yolu.

## Mevcut durum (ölçülmüş)
| Alan | Durum |
|---|---|
| Testler | engine 80 (CI'da yok) · stream 278 (CI'da) · client-tv 119 · wear 1 dosya · tizen/webos 0 |
| CI `gate.yml` | client-tv test+assemble · stream unittest · engine yalnız imaj build |
| Gözlem | healthcheck yok, Prometheus/Sentry yok, `client_log` bellekte |
| Büyük dosyalar | PlayerScreen 3223 · HomeScreen 1848 · wear MainActivity 1328 · BrowseScreen 1108 · PlayerCore 1041 · VideoPlayer.js 1432 |
| Güvenlik | app+wear `usesCleartextTraffic=true`, `allowBackup=true`; `Network.kt:33` BASIC log açık |
| Doküman | `docs/HANDOFF.md` 1440 satır + kökte ikinci HANDOFF; plan durum işaretleri tutarsız |

## Personalar (bu dalda eklendi)
| Ajan | Kapsam | Model |
|---|---|---|
| `source-engineer` | engine/Plugins, domain, ayrıştırma, chain_scan | sonnet |
| `stream-proxy-engineer` | stream/ proxy, manifest, jeton, WARP, 403/429 | sonnet |
| `zima-ops` | ZimaOS, compose, tünel, kesintisiz rebuild (Edit/Write yok) | sonnet |
| `android-release` | Gradle/R8, sürüm, üç dağıtım yeri, ADB | sonnet |
Mevcut `tv-ux`, `tv-urun`, `web-tv`, `yz-muhendisi` ile çakışma yok. Plan/review Opus'ta kalır (Takım Pi).

## Fazlar (değer/çaba sırası)

### F0 — Kapılar (1 gün, en yüksek değer/çaba)
- engine `unittest`'i `gate.yml`'e ekle (imaj içinde koş).
- `docker-compose.yml`: engine/stream/doh/warp `healthcheck` + `depends_on: condition: service_healthy`.
- Doküman: kök `HANDOFF.md` tek devir; `docs/HANDOFF.md` → `docs/archive/`. Plan dosyalarına tek durum satırı (AÇIK/KAPANDI/ERTELENDİ).
- **Kabul:** CI'da engine job yeşil; `docker ps` sağlık sütunu `healthy`; smoke YEŞİL.

### F1 — Kaynak dayanıklılığı (2-3 gün)
- Eklenti başına **sözleşme testi**: kayıtlı HTML fixture (çevrimdışı, CI'da) + canlı duman testi (kaynak düşükse sebebiyle `skip`). Örnek: yt-dlp extractor test deseni [doğrulanmadı].
- **Gece kanaryası:** ZimaOS timer ile `chain_scan.py --n 2`; sağlayıcı N gün üst üste 0 → uyarı.
- Seçici değişikliği yalnız ikinci sayfada doğrulanınca (self-healing seçiciye şema doğrulaması olmadan güvenme).
- **Kabul:** her aktif eklentide ≥1 fixture testi; kanarya bir haftalık kayıt üretiyor.

### F2 — Hafif gözlem (1-2 gün)
- Seçenek A (önerilen): healthcheck + mevcut `docker-bekcisi.timer`'a blackbox yoklama (tünel, katalog, örnek manifest) → kalıcı JSON satır günlüğü + uyarı.
- Seçenek B: Grafana Alloy + blackbox exporter + SLO paneli [doğrulanmadı; ZimaOS ~97 konteyner, kaynak maliyeti ölçülmeli].
- `client_log`'u kalıcı yap (SQLite/JSONL volume), rebuild'de silinmesin.
- **Kabul:** stream rebuild sonrası günlük duruyor; tünel düşünce 5 dk içinde uyarı.

### F3 — Oynatma kalitesi (QoE) (2-3 gün)
- TV'de Media3 `PlaybackStatsListener`: başlama süresi, tampon sayısı/süresi, hata → sunucuya, F2 günlüğüne.
- Kaynak puanı (`source_score`) bu ölçümle beslensin (bkz. hafıza `source-order-needs-evidence`).
- Sonraki bölümü önceden ısıtma: Media3 `DefaultPreloadManager` [doğrulanmadı; proxy tek kullanımlık jetonla çakışabilir → önce pilot].
- **Kabul:** Mi Box'ta 1 hafta veri; bölüm geçişi ölçülü olarak kısalıyorsa preload kalır.

### F4 — Kod sağlığı (3-5 gün)
- PLAYER2 kararı: varsayılan yap ya da sil — iki yol kalmasın.
- `PlayerScreen.kt` bölünmesi (panel, sarma, bölüm listesi, ayar paneli ayrı dosyalar); davranış değişmez, testler önce.
- **Kabul:** tek oynatıcı yolu; en büyük Kotlin dosyası <1200 satır; 119+ test yeşil; Mi Box'ta oynatma kanıtı.

### F5 — Güvenlik sıkılaştırma (1 gün)
- `network_security_config`: cleartext yalnız LAN aralıkları (192.168.0.0/16) + localhost; tünel HTTPS. Düz `usesCleartextTraffic=false` LAN-önce yolu kırar.
- `allowBackup`: imza anahtarı olayında ADB prefs yedeği bununla yapıldı → `dataExtractionRules` ile yalnız prefs; tamamen kapatma.
- Release'te HTTP log `NONE`.
- DENETIM açıkları: upstream TLS doğrulaması, cloudflared `network_mode: service:stream` gerekçesi.
- **Kabul:** emülatör + Mi Box'ta LAN ve tünel yolu açılıyor; prefs yedek/geri yükleme bir kez denendi.

### F6 — Performans + cihazlı CI (2 gün, en son)
- Baseline Profile (soğuk açılış 7-13 sn emülatörde ölçülmüştü).
- `reactivecircus/android-emulator-runner` ile CI'da açılış duman testi [sürüm doğrulanmadı].
- **Kabul:** Mi Box soğuk açılış önce/sonra ölçümü.

## Açık sorular / kapsam dışı
- Engage SDK "İzlemeye Devam" (Watch Next 2027 H2'de destek kaybı) yan yüklü APK'da çalışır mı? [doğrulanmadı] → araştırma bileti, şimdilik kapsam dışı.
- tv-material 1.0 kararlı, tv-foundation kullanımdan kalkıyor → F4 sırasında bağımlılık kontrolü.
- Motoru buluta taşımak: kapsam dışı (datacenter IP engeli).

## Riskler
| Risk | Önlem |
|---|---|
| Healthcheck yanlış pozitif → yeniden başlatma döngüsü | `start_period` + gevşek eşik; ilk hafta yalnız raporla |
| Cleartext kısıtı TV'yi ağsız bırakır | Önce emülatör, sonra Mi Box; OTA öncesi geri alma APK hazır |
| Bölme refaktörü oynatıcıyı bozar | Önce davranış testleri; refaktör ayrı dal, cihaz kanıtı şart |
| Kanarya kaynaklara yük | Günde 1, `--n 2`, WARP üstünden |

## Takım Pi akışı
Her faz: planner (Opus) → builder (Sonnet, ilgili persona) → verifier → reviewer (Opus) → scribe.
Fazlar ayrı dallarda (`feature/f0-gates` vb.), her faz kendi PR'ı.
