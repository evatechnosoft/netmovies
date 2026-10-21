# Handoff: ZimaOS sadeleştirme + TV 0.9.74
> 2026-10-21 · `fix/tv-heap-buffer` @ 1e81c42 (origin'de; `feature/profiles` da aynı commit'i içeriyor) · 2 untracked (`.claude/` yerel ayar, işle ilgisiz)

## Goal
Profiller (feature/profiles) + TV heap düzeltmesi canlıda; ZimaOS'u gereksiz yükten arındırmak.
Profil ayrıntısı: hafıza + `git log feature/profiles`. Heap düzeltmesi: commit 1e81c42 mesajı.

## State
- **ZimaOS kodu** `feature/profiles` @ 1e81c42; TV OTA `v0.9.74-poc` (curl ile doğrulandı).
  0.9.74 kutuda açılışta ANR verdi → `cmd package compile -m speed -f` ile çözüldü (hafıza `adb-kurulum-dexopt-anr`).
- **20 konteyner durduruldu**, restart=no: coolify-v4(+db,redis,sentinel), twenty-(server,worker,db,redis),
  quiz-bank-frontend, quiz-bank-backend_old, callcenter-(frontend,backend,redis), sportapp-backend,
  randevu-container, big-bear-flowise, flowise, obsidian, webtop, big-bear-dockhand.
  Eski politikalar `/DATA/AppData/durdurulan-20261021.txt` (`ad politika`). Geri: `docker update --restart=<p> <ad> && docker start <ad>`.
  RAM 11,4/31,7 GB. 97 konteyner hâlâ çalışıyor.
- **Gece temizliği**: `/DATA/AppData/gece-temizlik/temizlik.sh` + `gece-temizlik.{service,timer}` (23:40, 00:00 kapanıştan önce).
  21 Eki 23:40'ta kendiliğinden koştu; /DATA 90G boş. Volume/etiketli imaja dokunmaz.
- **LOQ** `~/.gradle/gradle.properties`: daemon idletimeout 10 dk, Kotlin `in-process` (ayrı daemon yok).
- nexus-db CPU = geçici `ingest.py`; dokunulmadı.

## Next
1. Yarın ZimaOS açılışında durdurulanlar geri kalktı mı: `ssh zima "docker ps --format '{{.Names}}' | grep -cE 'coolify|twenty|callcenter|flowise|webtop|obsidian'"` → 0 beklenir. >0 ise CasaOS açılışta başlatıyor; CasaOS app ayarından kapat.
2. Dean'e açık soru: Kasm agent da durdurulsun mu?
3. `fix/tv-heap-buffer` → `fix/general-stability` PR'ı (feature/profiles ile birlikte) — henüz açılmadı.

## Don't repeat
- Durdurulan konteynerleri silme/prune etme — volume'ları ve verisi duruyor, yalnız durduruldu.
- `docker volume prune` / `system prune -a` gece betiğine ekleme — durdurulan uygulamaların imajını/verisini götürür.
- nexus-db psql rolü `nexus`, `postgres` değil.
- Zima'da compose/docker için `export DOCKER_CONFIG=/tmp/dc` şart.

## Verify
git rev-parse --short HEAD                                         # 1e81c42
curl -s 192.168.0.186:3310/api/v1/app_update?target=tv | grep -o '"tag":"[^"]*"'   # v0.9.74-poc
ssh zima "systemctl list-timers gece-temizlik.timer --no-pager | sed -n 2p"        # sonraki 23:40
ssh zima "cat /DATA/AppData/gece-temizlik/son.log | tail -1"                       # df satırı

## Yeniden başlangıç promptu (yapıştır)
```
NetMovies / ZimaOS bakım devamı. Durum: ZimaOS feature/profiles @ 1e81c42, TV 0.9.74 yayında.
21 Eki'de 20 kullanılmayan konteyner (coolify, twenty, callcenter, flowise, webtop…) durduruldu,
restart=no; eski politikalar /DATA/AppData/durdurulan-20261021.txt. Gece 23:40 temizlik zamanlayıcısı kurulu.
LOQ'ta Gradle/Kotlin boşta kapanıyor.
Ortam: C:\projects\netmovies (worktree feature+profiles), ssh zima, DOCKER_CONFIG=/tmp/dc.
Önce HANDOFF.md oku, Verify bloğunu koş. Sıra: 1) durdurulanlar açılışta geri kalktı mı
2) Kasm sorusu Dean'e 3) fix/tv-heap-buffer PR. Yeni iş açma.
```
