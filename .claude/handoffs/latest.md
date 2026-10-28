# Handoff: ZimaOS disk doldu → Nextcloud temizliği + SSD'ye taşıma (28 Eki)
> 2026-10-28 · kod değişikliği yok (sunucu bakımı)
> **Hedef (Dean):** "Yüklemiyor film dizi" + "temizle hepsini, diğer SSD'ye topla, aralıklı süzülsün, hiç dolmasın".
> **Kök neden:** /DATA %100 — Nextcloud çöp kutusu 283G (PC İndirilenler → /Indirilenler eşitlemesi, qBittorrent `.!qB` dosyaları çöpe düşüyordu).
> **Kanıtlı:** buildx prune 20G + trashbin/versions temizliği → /DATA 162G/457G (%36). Retention `auto, 3` (trashbin+versions), cron aktif. PC `%APPDATA%\Nextcloud\nextcloud.cfg` Folders\2 (Downloads) silindi, yedek `nextcloud.cfg.yedek-20261028` (client kapalıydı). Nextcloud data → `/DATA/sata/nextcloud-data` (sda1, 5.2G/220G): compose bind eklendi (`/var/lib/casaos/apps/nextcloud/docker-compose.yml`, yedek `/DATA/AppData/nextcloud-compose.backup-20261028.yml`), `docker compose up -d`, `occ status` maintenance:false, status.php OK. NetMovies health 200, stream'de "no space" 0.
> **Doğrulanmadı:** TV'de film/dizi yüklenmesi (Dean'e soruldu).
> **Bekleyen:** obsidian + netmovies-warp json logları 3.2G — truncate sınıflandırıcıya takıldı. files:scan hatası `/mnt/shared/apps/.recycle` izin — önceden var, ilgisiz.
> **Açık soru:** "Collar ve lock" / "proje bine oturum" anlaşılmadı, soruldu.
> **Akşam (Dean onaylı):** TV'de açıldı (Dean). gece-temizlik.timer 19:00 + Persistent=true; servis artık `nightly_cleanup.sh` çağırıyor (buildx prune + 200MB üstü json log truncate; eski `temizlik.sh` duruyor, kullanılmıyor). Silindi: OTA 1.7.0/1.7.0-beta1/1.8.0-beta2 (1.8.0 kaldı, kurulu 1.6.0), 8 eski imaj (dotnet sdk, temurin, nexus-mcp tarihli, sentinel), obsidian+warp logları. /DATA 155G (%34). Gece: llama3.2 silindi (kullanılmıyordu); gemma4:12b tutuldu (agent-eva night_model + yz). Eski Nextcloud host kopyası (5.2G) silindi, Nextcloud SSD'den çalışıyor (status OK). /DATA 148G (%33). android-build/ffmpeg/curl imajları bilerek tutuldu.
> **Tek sonraki adım:** birkaç gün sonra eski Nextcloud data kopyasını sil (5.2G); Dean ollama model kararını versin.

# Handoff: TV 0.9.83 — ayar panelinde bölüm listesine iniş (28 Eki)
> 2026-10-28 · worktree `worktree-tv-bolum-ayar-odak` @ b41fae7 (fix/general-stability'ye push'landı) · TV 0.9.83 yayında (OTA v0.9.83-poc, evaglass-releases, apps.json 5217263)
> **Hedef (Dean):** 180+ bölümlü dizide ayar paneli: GERİ önce üst sekmelere; üstten bölüme az basışla inmek.
> **Kanıtlı:** GERİ→sekme davranışı 0.9.34'ten (2799a38) beri kodda var; Dean TV'de "geri üste çıktı" dedi. b41fae7: liste açıkken "Kaldığım bölüm"/"Son bölüm" satırları gizli, `KayanListe` giriş odağı oynayan bölüm (`girisOdagi`). Derleme + birim testleri yeşil.
> **Doğrulanmadı:** iniş davranışı cihazda; Mi Box'ta kurulu sürüm (ADB .11/.105 yanıt vermedi). Dean'e "dene" deyip GERİ'yi kastetmem karışıklık yarattı — açıklandı.
> **Tekrarlama:** emülatörü canlı sunucuya bağlı telefon kipinde tıklatma (remote/play TV'ye komut gönderir). Dean izlerken TV'ye/sunucuya müdahale yok.
> **Tek sonraki adım:** Dean 0.9.83'te sekmeden aşağı inişi denesin; geri bildirime göre PlayerScreen.kt `SettingsPanel` (~2440).

# Handoff: logo + DiziYou/DiziAbi proxy + Dizilla yeniden yazımı (27 Eki)
> 2026-10-27 · worktree `worktree-logo-v1` @ 9cc5754 (= origin/fix/general-stability, push'landı) · ZimaOS aynı commit'te, engine 10:28 UTC yeniden kuruldu
> **Hedef:** Dean Türkçe dublaj izlemek istiyor ("Dizimom dizilla yok mu").
> **Kanıtlı:** a9cb077 yeni logo (TV 0.9.82 / saat 0.1.26 yayında) · d1cb25d DiziYou/DiziAbi segmentleri proxy'den (canlı) · 9cc5754 Dizilla Ekim 2026 Next.js tasarımına göre yeniden yazıldı: kart `a.pcard`/`span.mvk-t`, arama `/ara?q=`, bölümler flight JSON-LD adreslerinden, kaynaklar bölüm sayfasındaki `"sources":[...]` (lang dub önce), pichive zinciri aynı. Yeni raf "Türkçe Dublaj". Canlı `api/v1/search?plugin=Dizilla&query=wednesday` → 1 sonuç; konteyner testinde 16 bölüm + dublaj master.m3u8 200. Gemini anahtarı Bitwarden 82397c85.
> **TV'de doğrulandı (Dean, 27 Eki):** Dizilla dublaj oynuyor.
> **DiziMom:** .wiki/.food/.plus hepsi ölü, WARP ile de — yeni domain çıkana kadar dokunma.
> **Ağ:** Dean kararı — DHCP'yi modem veriyor, ağ bekçisi kapalı; ağ/modem ayarına onaysız dokunma (aşağıdaki 25 Eki DHCP bloğu bu açıdan bayat).
> **Tek sonraki adım:** yok — Dean'den yeni istek bekleniyor. Dizilla sorunu gelirse önce `docker logs netmovies-engine | grep -i dizilla`.

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
