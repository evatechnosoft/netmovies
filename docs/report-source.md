# NetMovies research source ledger

Audience: NetMovies maintainer. Access date: 2026-09-02. Scope and assumptions are
recorded in `NETMOVIES-IMPROVEMENT-PLAN-2026-09-02.md`.

| Claim | Evidence | Confidence / limitation |
|---|---|---|
| TV navigation should minimize clicks, preserve predictable D-pad movement and show clear focus. | Android Developers, “TV navigation”, updated 2026-08-19: https://developer.android.com/training/tv/get-started/navigation | High; first-party platform guidance. |
| TV screens need dedicated landscape layouts, overscan-safe spacing and efficient image decoding. | Android Developers, “Build TV layouts”, updated 2026-08-19: https://developer.android.com/training/tv/playback/compose/layouts | High; first-party platform guidance. |
| TV details should lead directly to playback; all controls must work with D-pad. | Android Developers, “TV apps checklists”, updated 2025-04-17: https://developer.android.com/training/tv/publishing/checklist | High; first-party quality checklist. |
| Media3 can share an injected network stack; a single network instance is recommended. | Android Developers, “Network stacks”, accessed 2026-09-02: https://developer.android.com/media/media3/exoplayer/network-stacks | High; first-party technical documentation. |
| Current stable Media3 is 1.11.0; project uses 1.4.1. | Android Developers, “Media3 releases”, latest update 2026-08-05: https://developer.android.com/jetpack/androidx/releases/media3 | High; version is time-sensitive and was checked on access date. |
| Playback failures can be classified using `PlaybackException` and underlying HTTP error types. | Android Developers, “Player events”, accessed 2026-09-02: https://developer.android.com/media/media3/exoplayer/listening-to-player-events | High; first-party API guidance. |
| Responsive poster sizes and selective preload reduce wasted bytes and improve the first visible image. | web.dev, “Preload responsive images”, updated 2026-07-10: https://web.dev/articles/preload-responsive-images | High; first-party browser performance guidance. Exact gain is workload-specific. |
| A custom web media player needs keyboard access, visible focus, labels and contrast. | W3C WAI, “Media Players”, updated 2024-09-17: https://www.w3.org/WAI/media/av/player/ | High; standards-oriented accessibility guidance. |
| Capability manifests can prevent irrelevant provider calls by declaring resources and media types. | Stremio Addon SDK, “Manifest format”: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md and “Resources”: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/README.md | Medium-high; first-party open-source protocol, used as an architectural analogue rather than a required dependency. |
| Customizable home sections and unified search/watchlist are established media-hub patterns. | Jellyfin official site: https://jellyfin.org/ and Plex Discover: https://www.plex.tv/discover/ | Medium; first-party product descriptions, not independent usability studies. |
| Broad cleartext opt-in should be avoided and narrowed with network security configuration. | Android Developers, “Network security configuration”, accessed 2026-09-02: https://developer.android.com/privacy-and-security/security-config | High; first-party security guidance. |
| Android backup includes shared preferences and most app data unless excluded or disabled. | Android Developers, “Auto Backup”, accessed 2026-09-02: https://developer.android.com/identity/data/autobackup | High; first-party platform guidance. |

Local evidence: repository files, Docker state/health, container logs, HTTP status
checks, Python compileall, and Gradle lint/test output. Watchbuddy schema and plugin
discovery endpoints returned HTTP 200 on 2026-09-02; no content stream was tested.

Stop condition: every consequential recommendation has first-party support or local
runtime evidence; additional product screenshots or third-party trend articles were
unlikely to change the stabilization priorities.
