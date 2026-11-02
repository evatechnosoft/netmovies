# NetMovies mimari araştırması — 2026-11-02

> İki `researcher` koşusu (deep + standard). Her iddia URL'li; `gap` = kaynak bulunamadı,
> `doğrulanmadı` = yalnız ikincil kaynak. Yıldız sayıları anlık (~).

## Repo gerçeği (aynı gün, `b41fae7`)
- `client-tv/app/build.gradle.kts`: Media3 **1.11.0**, Compose BOM **2024.09.02**, tv-material **1.0.0**.
- `docker-compose.yml`: stream → engine `service_healthy` var; cloudflared → stream yalnız başlama sırası.

## 1. Benzer yapılar
- Stremio addon protokolü: manifest → catalog → meta → stream → subtitles; `types`/`idPrefixes` ile süzme. https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/README.md
- KekikStream (engine kaynağı): `PluginBase` + Extractor ayrımı, yerel `Plugins/` override; ~53 yıldız, tek bakımcı riski. https://github.com/keyiflerolsun/KekikStream
- CloudStream: depo manifestiyle eklenti güncelleme, ~11.1k yıldız. https://github.com/recloudstream/cloudstream
- AIOStreams: birden çok kaynağı tek boru hattında filtreleme/sıralama (puanlama deseni). https://github.com/Viren070/AIOStreams
- Nuvio: scraper eklentileri kırılgan, kaynak haber vermeden kaybolur. https://docs.elfhosted.com/nuvio/
- Jellyfin 10.11: yerleşik yedek/geri yükleme (ikincil). https://jellywatch.app/blog/jellyfin-10-11-ef-core-migration-chaos-lessons-learned-2026
- Plex/Kodi/Aniyomi: gap.

## 2. Android TV istemci
- Media3 stabil 1.11.1 (2026-09-10). 1.11.0: `DefaultPreloadManager` sıralama karşılaştırıcısı, `enablePerStreamMediaProgression()`, HLS Content Steering, `PlayerPool`. https://developer.android.com/jetpack/androidx/releases/media3
- tv-material 1.1.0 / tv-foundation 1.0.0 stabil (2026-05-06). https://developer.android.com/jetpack/androidx/releases/tv
- Google: `tv-material` kullan, mobil material3 ile karıştırma. https://developer.android.com/training/tv/playback/compose
- Continue Watching = Engage SDK continuation cluster. https://developer.android.com/guide/playcore/engage/tv/continue-watching — Watch Next'in kalkacağı iddiası doğrulanmadı.
- Baseline profile / screenshot test için TV'ye özel 2026 kaynağı: gap.

## 3. Sunucu
- OpenTelemetry + FastAPI (`opentelemetry-instrument`, httpx instrumentation, health hariç). https://last9.io/docs/integrations/frameworks/python/fastapi.md
- QoE (açılış süresi, rebuffer) oynatıcıda ölçülür. https://oneuptime.com/blog/post/2026-02-06-monitor-video-streaming-backend-opentelemetry/view
- Cloudflare video kısıtı CDN şartlarına taşındı; tünel kapsamı doğrulanmadı. https://blog.cloudflare.com/updated-tos/
- HLS rewrite best practice, circuit breaker, eklenti contract test: gap (kendi hafıza notlarımız tek kaynak).

## 4. Ev sunucusu ops
- `depends_on: service_healthy` + `start_period`. https://oneuptime.com/blog/post/2026-01-16-docker-compose-depends-on-healthcheck/view
- Watchtower terk edildi (doğrulanmadı); Renovate PR açar, compose + gradle + pip. https://selfhosted-sh.pages.dev/renovate-automated-dependency-updates
- restic yedek: gap.

## 5. Yapay zeka
- LiteRT-LM Kotlin API stabil, function calling + constrained decoding, Gemma-4-E2B. https://developers.google.com/edge/litert-lm/overview

## 6. Ajan/persona tasarımı
- Frontmatter: name, description, tools, disallowedTools, model, effort, memory, skills, isolation, maxTurns, color… https://code.claude.com/docs/en/sub-agents
- Ajan yalnız yinelenen, ayrı araç/risk profili olan işe; denetim ajanına yazma aracı verme (aynı URL).
- Koleksiyonlar: VoltAgent ~26k, wshobson ~42k yıldız. https://github.com/VoltAgent/awesome-claude-code-subagents · https://github.com/wshobson/agents

## Koru / değiştir / ekle
- **Koru:** eklenti/extractor ayrımı · kendi HLS proxy + WARP + tünel · tv-material · kendi OTA.
- **Değiştir:** Media3 1.11.1 + PreloadManager (sonraki bölüm) · tv-material 1.1.0 + BOM güncel · kaynak puanını kanıtla otomatik (AIOStreams deseni).
- **Ekle:** compose healthcheck zinciri · kalıcı QoE olayı (client_log yerine) + OTel · eklenti contract test (chain_scan → CI) · zamanlanmış yedek + bir geri yükleme denemesi · Engage SDK Continue Watching · Renovate.
