# NetMovies — modern yapıya geçiş planı (F0–F4)
> 2026-11-02 · taban `b41fae7` (fix/general-stability) · yazan: `planner` · kaynak: `docs/research/architecture-2026.md`, eski planlar, kod (grep ile doğrulandı)
> Durum: **taslak — Dean onayı bekliyor.** Beyin fırtınası (2026-11-02) işlendi, özeti en altta.

## 1) Kapsam kilidi
**Değişenler**
- CI kapısı: engine testleri hiçbir yerde koşmuyor (`gate.yml` ve `smoke.sh` yalnız stream testleri).
- `/data` yedeği: yalnız 2026-04 tarihli elle alınmış tam yedek var.
- TV'de iki oynatıcı: `PlayerScreen.kt` 3223 satır, `player2/` 1932 satır; varsayılan eski (`OynaticiSecimi.yeniMi = false`).
- Oynatma ölçümü bellekte (`client_log` her rebuild'de sıfırlanır).
- Media3 1.11.0 → 1.11.1, Compose BOM 2024.09.02, tv-material 1.0.0 → 1.1.0.
- `:latest` imajlar (`warp`, `cloudflared`) ve tünelin sağlık sırası.

**Değişmeyenler**
- Eklenti/extractor ayrımı, kendi HLS proxy, WARP, DoH, Cloudflare tüneli, kendi OTA, motor evde.
- Zaten var: engine→stream `service_healthy` · `source_event` + `source_score` · `SunucuGecisPolitikasi` (`PlayerScreen.kt:1990`, player2 de kullanıyor) · `gate.yml` · TV yalnız `androidx.tv.material3`.
- Reddedilen: OpenTelemetry/Grafana yığını (tek kullanıcıya ağır) · Engage SDK (Mi Box başlatıcısı Projectivy) · Watchtower (izlerken rebuild) · chain_scan'i GitHub CI'a koymak (canlı site gerekir) · yeniden yazım/mikroservis/Kubernetes.

## 2) Hedef mimari
Yapı aynı: engine, stream, warp/doh + tünel. Üç ek: **kanıt** (TV'nin her oynatma denemesi SQLite'a kalıcı), **güvence** (gece yedek + geri yükleme denemesi, CI engine testleri), **tek oynatıcı** (player2; sonraki bölüm ön-yüklemesi bunun üstüne).
```
TV(player2) ──oynat──► stream(proxy,WARP) ──► engine(Plugins) ──► kaynak siteler
   │ ilk_kare_ms/rebuffer            ▲ sıra = source_score (kanıt)
   └──POST /source_event──► netmovies.db(oynatma_olay) ──► GET /qoe · /source_score
gece: yedek.sh (/data → /DATA/Backup/netmovies, 14 kopya, dogrula) · chain_scan --n 1 → /data/chain_scan.json
```

## 3) Aşamalar
D/Ç = değer/çaba (Y/O/D). Her aşama yeşil olmadan sonraki başlamaz. İzlerken rebuild yok (`remote/state` boşta kontrolü).

### F0 — Güvenlik ağı
**F0.1 Engine testleri kapıda** — `.github/workflows/gate.yml`, `scripts/smoke.sh`.
Kabul: 13 `engine/tests/test_*.py` CI'da ve smoke'ta koşar. Doğrulama: `docker run --rm -w /usr/src/KekikStreamAPI netmovies-engine:ci python -m unittest discover -s tests -v` + `bash scripts/smoke.sh`. D/Ç Y/D. Risk: kırık test çıkabilir → kodu düzelt. Persona: builder → verifier.

**F0.2 `/data` gece yedeği + geri yükleme denemesi** — yeni `scripts/backup.sh` (`take`/`verify`), `scripts/zima/netmovies-backup.{service,timer}`, `docs/ZIMAOS-DURUM.md`.
`netmovies.db` stream konteynerinde `sqlite3.backup` ile; yanına `admin.json`, `prefs.json`, `markers.json`, `lang_memo.json`, `source_score.json`, `yz_cihazlar.json`; tar → `/DATA/Backup/netmovies/`, 14 kopya, eskisi Geri Dönüşüm aracıyla. `cache/`, `apk/`, kayıtlar hariç.
`.env` tar'a girmez; Bitwarden kaydı olarak tutulur, `verify` varlığını kontrol eder. Kabul: `verify` son tar'ı açar, `PRAGMA integrity_check = ok`, izleme tablosu satır sayısı canlı DB'nin ±%5'i içinde; timer `systemctl list-timers`'ta. D/Ç Y/D. Geri alma: `systemctl disable --now`. Persona: zima-ops → verifier.

### F1 — Tek oynatıcı
**F1.1 player2 varsayılan** — `tv/data/OynaticiSecimi.kt`: anahtar `yeni_oynatici_v2` olarak yeniden adlandırılır (eski anahtar cihazda `false` yazılı kalmış olabilir, `HomeScreen.kt:1720`) + Ayarlar metni.
Kabul: `docs/PLAYER2-PLAN.md` "Korunacak davranışlar" listesi emülatörde iki oynatıcıyla karşılaştırılır (açılış, kaynak geçişi, seek, GERİ, sonraki bölüm, intro atlama, kumanda kipi); sonra Mi Box'ta Dean bir film + bir bölüm izler, `client_log`'da hata yok, açılan oynatıcının hangisi olduğu günlükte kanıtlanır. D/Ç Y/O. Geri alma: Ayarlar anahtarı / tek satır + OTA. Persona: tv-urun, playback-diag, builder.

**F1.2 Eski oynatıcı silinir (bir sürüm sonra)** — `ui/PlayerScreen.kt` (önce `videoSource` + `SunucuGecisPolitikasi` `ui/player2/`'ye taşınır; player2 bunları `PlayerCore.kt:920`, `PreviewPlayer.kt:51`'de çağırıyor), `MainActivity.kt`, `OynaticiSecimi.kt`.
Kabul: `grep -n "PlayerScreen(" .../MainActivity.kt` ve `grep "tv.ui.videoSource" ui/player2` boş; Kotlin ≥2000 satır azalır; test + derleme yeşil. D/Ç Y/O. Persona: builder → reviewer.

### F2 — Kalıcı QoE (OTel değil)
**F2.1 `source_event` genişler** — `stream/Public/API/v1/Routers/source_event.py` (isteğe bağlı `oturum_id`, `kaynak_indeksi`, `ilk_kare_ms`, `hata_kodu` (Media3 errorCode / HTTP 403-429), `warp` 0/1, `preload` 0/1, `host`, `media`; rebuffer ayrı `oturum_sonu` olayıyla, 2 sn altı tampon sayılmaz; `GET /api/v1/qoe` son 7 gün), `stream/Public/Home/Libs/watch_store.py` (`oynatma_olay`, 30 gün budama), TV `ApiModels.kt`, `NetMoviesApi.kt`, `player2/PlayerCore.kt`, yeni `stream/tests/test_qoe_event.py`.
Kabul: eski gövde aynen çalışır; bozuk/negatif alan reddedilir; rebuild sonrası `/qoe` eski olayları döner. D/Ç Y/O. Persona: builder, playback-diag.

**F2.2 Puan yavaşlığı da sayar** — `stream/Public/API/v1/Libs/source_score.py`.
Yavaşlık `plugin+host` anahtarına yazılır; `hata_kodu` codec ise (Mi Box AV1/4K) puana yazılmaz. Kabul: birim testi "8 sn açılış + 3 rebuffer < 2 sn açılış" ve "codec hatası puanı düşürmez"; `test_language_priority.py` yeşil kalır. D/Ç O/D. Persona: builder → reviewer.

### F3 — Oynatma deneyimi
**F3.1 Bağımlılık güncellemesi (ayrı commit)** — `client-tv/app/build.gradle.kts`: Media3 1.11.1, BOM güncel, tv-material 1.1.0, tv-foundation 1.0.0.
Kabul: derleme yeşil, emülatörde film/dizi/canlı açılır, APK ±%10. D/Ç O/D. Persona: builder → verifier.

**F3.0 Dizide sunucu ön-ısıtması (beyin fırtınasından; F0'dan hemen sonra)** — `stream/Public/API/v1/Routers/load_item.py:152-158` ısıtma bugün yalnız film için. Devam Et kartı odak alınca kayıtlı bölüm (S4B8), oynatmanın son ~2,5 dk'sında sonraki bölüm ısıtılır (önbellek 180 sn). İkinci oynatıcı yok, jeton harcanmaz, F1'e bağlı değil; bölüm numarayla eşlenir. D/Ç Y/D. Persona: builder → playback-diag.

**F3.2 Sonraki bölüm ön-yüklemesi** (F1 + F2.1 + F3.0 sonrası) — `player2/PlayerCore.kt`: segment değil yalnız `resolve` + master manifest önceden; oynatma adresi geçiş anında üretilir (tek kullanımlık adres); ön-yükleme 5-10 sn ile sınırlı, kalite tavanı; GERİ ile iptalde atılır, geri sayım kartı odağı çalmaz; 90 sn altı klip atlanır.
Kabul: özellik bayrağı açık/kapalı, iki kolda ≥10 olay; açık kolda medyan `ilk_kare_ms` yarısından az; ikinci ses kanalı açılmaz; `/qoe`'de 429 artmaz. D/Ç Y/O. Geri alma: özellik bayrağı. Persona: playback-diag → builder → tv-ux.

### F4 — Sakin operasyon (F0–F3'ten bağımsız)
**F4.1 Sürüm sabitleme + WARP sağlığı** — `docker-compose.yml`: `warp` ve `cloudflared` etiket sabit; warp'a healthcheck, engine `depends_on.warp: service_healthy`. cloudflared'e `service_healthy` EKLENMEZ (izlerken kesintiyi 14 sn'den 40+ sn'ye çıkarır; asıl tuzak `network_mode: service:stream`, recreate şart). Kabul: `docker compose config` hatasız; stream recreate → `BASE=https://w.evaitec.com bash scripts/smoke.sh` yeşil (tünel 200). Persona: zima-ops.

**F4.2 Renovate — çıkarılması önerildi** (reviewer: tek kullanıcıya YAGNI; F3.1 elle + F4.1 sabitleme yeter). Dean kararına bağlı. Persona: builder.

**F4.3 Gece zincir taraması** — `scripts/zima/netmovies-chainscan.{service,timer}` (04:00, `docker-bekcisi` ile çakışmayan dakika), `scripts/chain_scan.py --json`. Boşta kontrolü her sağlayıcıdan önce `remote/state` ile tekrarlanır, TV açılırsa durur; sağlayıcılar arası bekleme (NAT 180/dk). Sonuç puana yazılmaz, yalnız admin'de işaret. Kabul: `/data/chain_scan.json` her sabah tarihli. Persona: engine-plugin, zima-ops.

## 4) Riskler ve geri alma
- F1: eski oynatıcı bir sürüm yedek kalır; geri alma Ayarlar anahtarı veya önceki OTA etiketi.
- F2: yalnız yeni tablo; F0.2 yedeği F2'den önce şart.
- İzlerken dağıtım: önce `docker compose build stream`, sonra `up -d --no-build stream cloudflared`.
- Sıra: F0 → F3.0 → F1 → F2 → F3.1/F3.2; F4 paralel.

## 5) Dean'e açık sorular (beyin fırtınası sonrası)
1. Oynatıcı geçişi — (Önerilen) player2 yeni anahtarla varsayılan; Mi Box'ta bir akşam izle, eski oynatıcı bir sürüm sonra silinir.
2. Dizi ön-ısıtması (F3.0) öne alınsın mı? — (Önerilen) evet, F0'dan hemen sonra; görünür kazancın ucuz yarısı.
3. Renovate — (Önerilen) çıkar; bağımlılık güncellemesi elle, üç ayda bir.
4. Yedek laptopa da — (Önerilen) gece ZimaOS'a, haftada bir laptopa `scp`; `.env` Bitwarden'da.
5. "Ne dedi?" tuşu (10 sn geri + o aralıkta altyazı) — (Önerilen) backlog; dublaj ağırlıklı izlemede değeri düşük.

## Varsayımlar (doğrulanmadı)
- ZimaOS'ta `docker-bekcisi.timer`, `ev-dhcp.service` var (hafızadan). F4 bunlara dokunmaz.
- `engine/Public/Home` web arayüzünün kullanımı kontrol edilmedi.
- Mi Box'ın Media3 1.11.1 / tv-material 1.1.0 uyumu cihazda denenmedi.

## Beyin fırtınası (2026-11-02) — reviewer · tv-urun · playback-diag
Verdict: reviewer PASS (düzeltmelerle), tv-urun PASS, playback-diag UNKNOWN (canlı `source_score` okunamadı; kod okumasına dayalı). Maddelere işlendi:
- **Adresler:** player2 1932 satır; `PlayerCore.kt:474` `DefaultTrackSelector`, asıl politika `PlayerScreen.kt:1990` — F1.2'de silmeden önce taşınmazsa player2 derlenmez.
- **F1.1:** varsayılanı değiştirmek cihazdaki kayıtlı `false`'u ezmez → anahtar yeniden adlandırılır.
- **F2:** `hata_kodu` olmadan puan codec/jeton/429/WARP arızasını sağlayıcıya yazar; yavaşlık host düzeyinde.
- **F3:** dizide sunucu ısıtması yok (F3.0, ucuz); ön-yükleme tek kullanımlık adresi tüketmemeli, NAT 429'a yaklaşmamalı, Mi Box belleği için 5-10 sn.
- **F4:** cloudflared `service_healthy` çıkarıldı; warp healthcheck eklendi; gece tarama TV açılınca durur, puana yazmaz; Renovate YAGNI.
- **Backlog:** "Ne dedi?" tuşu.
- Doğrulanmayan: Mi Box RAM'i, warp imajının kendi healthcheck'i, Mi Box'taki kayıtlı oynatıcı tercihi.
