# Handoff: ev DHCP tek sunucu + modem yedek + ev hız ölçümü (25 Eki)
> 2026-10-25 · worktree `fix/dhcp-kok-duzeltme` @ cebc0d3 (= origin/fix/general-stability) · TV 0.9.81 yayında
> **DEVİR (25 Eki, kanıtlı):** Güncel durum kök `HANDOFF.md`'de (State/Don't repeat/Verify). Özet: ZimaOS host `ev-dhcp.service` tek DHCP; `dhcp-authoritative` kaldırıldı; eski `ev-dhcp` konteyneri silindi; Netmaster DHCP yedek havuz .220–.254/1 gün açık (`nm_dhcp.py ac 220 35 86400`). Dean kararı: modem NAK riski kabul, cihazlar MAC ile sabitlenir — bu riski yeniden raporlama.
> **Hız ölçümü (25 Eki, iperf3 + Cloudflare, Dean'e raporlandı):** LOQ kablolu↔Zima eth0 949/941 Mbit/s; LOQ Wi-Fi↔Zima eth0 90/78; kablolu→Zima wlan0 95 (ters yön 851); Wi-Fi↔wlan0 132/77. İnternet: LOQ kablolu 121↓/18,6↑, LOQ Wi-Fi 91↓/18,3↑ (ilk koşu 2,8↑ dalgalanma), Zima host 115↓/19,2↑, netmovies-stream konteyner 59↓ (python urllib, UA şart: Cloudflare python UA'ya 403). Betikler `~/.claude/jobs/e12b219d/tmp/{lan_speed,inet_speed}.sh` (iş silinince gider). Sonuç: kablolu gigabit dolu, Wi-Fi ~80–130; TV Zima'ya hep eth0 (.186) ile gitmeli.
> **TV 0.9.79 (25 Eki akşam, kanıtlı):** "Takıldı" teşhisi: Suskunlar (DiziPal→YouTube, segmentler TV→googlevideo doğrudan) 634 sn'de ~30 sn BUFFERING, kendiliğinden devam; HTTP hatası yok. Yan bulgu kök düzeltildi: TV API'yi .174 (Zima wlan0, ~77 Mbit) üzerinden çağırıyordu — `ServerResolver.quickLocal` hatırlanan adresi derlenmiş .186'nın önüne koyuyordu. ff7e03f: derlenmiş adaylar önce (`hizliAdaylar` + test, 5/5 yeşil). `fix/general-stability` ff7e03f'e fast-forward edildi (merge yok), OTA `v0.9.79-poc` + GitHub + apps.json yayında. TV izlerken yayınlandı; kutu OTA ile kendisi alır, prefs'e dokunulmadı.
> **TV 0.9.80 (25 Eki gece, kanıtlı):** Dean ekran görüntüsü: telefon KUMANDA kipinde arama TV ızgara klavyesini açıyor, sonuç sütunu sağda daralıp yazı dikey akıyordu. f87db8f: `MainActivity` telefon (`nmTelefon`) + kumanda kipinde `PhoneSearchScreen` (telefon klavyesi, kart → "Televizyonda oynat", bölüm seçimi) gösterir; `PhoneSearchScreen`'e `NmBackHandler` eklendi. TELEFON kipi (cihazda izle) hâlâ ızgara klavye — PhoneSearchScreen yalnız TV'ye gönderir, yerel oynatma yolu yok. OTA `v0.9.80-poc` + GitHub + apps.json yayında (apk_publish EXIT=0).
> **TV 0.9.81 (25 Eki gece, kanıtlı):** Dean: "Geri tuşu çalışmıyor". Kök neden: PhoneSearchScreen'de arama sonrası/kart açılınca telefon klavyesi açık kalıyor, ilk GERİ'yi IME yutuyordu (ekran 2.–3. basışta kapanıyordu). cebc0d3: ara() ve satır tıklamasında odak bırakılır + klavye gizlenir. Emülatör kanıtı IME arama tuşuyla: 1 GERİ → ana ekran; kart açıkken GERİ kart, GERİ ana. Dikkat: `input keyevent 66` (donanım Enter) onSearch'ü tetiklemez, testte IME tuşuna dokun (`993 1710`). OTA `v0.9.81-poc` + GitHub + apps.json yayında. Dean'in S24'ünde doğrulanmadı.
> **Tek sonraki adım:** TV 0.9.79'u aldıktan sonra `adb -s 192.168.0.55:5555 shell dumpsys package com.evaitec.netmovies.tv | grep versionName` ve stream loglarında isteklerin .186'dan geldiğini doğrula; sonra kök HANDOFF.md Next 1-2 (Mi Box ADB doğrulama).
> **Tekrarlama:** `dhcp-authoritative` geri koyma; `ev-dhcp` konteynerini geri getirme; `interface=wlan0` çıkarma; Netmaster havuzunu .100–.179 ile çakıştırma; Zima docker için `export DOCKER_CONFIG=/tmp/dc` şart.

# (eski) Handoff: dal birleştirme — tek dal fix/general-stability, TV 0.9.76 (23 Eki)
> 2026-10-23 · `fix/general-stability` @ f45cb59 (= eski `fix/stream-segment-dedup` ucu; `feature/profiles`, `fix/tv-heap-buffer` de bu zincirde, silinmedi) · smoke YEŞİL
> **DEVİR:** Laptop 12 commit geride, yanlış daldaydı; `fix/general-stability` dedup ucuna fast-forward edilip push'landı, ZimaOS da aynı dala alındı (aynı commit, rebuild yok). Canlı: engine 5363f7c, stream 04c8f03 imajı; OTA `v0.9.76-poc`, GitHub 0.9.76 prerelease. apps.json (evaitecOTA) sürümü doğrulanmadı. prod tüneli 502 (19 Eki'den beri). Durdurulan konteynerler açılışta geri kalkmadı (0). Aşağısı 21 Eki devri (0.9.74) — sonrası commit mesajlarında: 04c8f03 yavaş kaynak→WARP, fdba5d8 arama GERİ çökmesi, f45cb59 ajanda kartı.

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
