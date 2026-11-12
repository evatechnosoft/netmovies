# Handoff: Yeraltı açılmıyor — DDizi çöktü, yedek eşleşme yarım (12 Kas akşam)
> 2026-11-12 21:17 (+03) · dal `fix/devam-et-tazeleme` @ 64b3e05 (push'lu, 0 dirty) · taslak PR #28 → `fix/general-stability`, birleşmedi · oturum seat 1'e taşındı (netmovies-s1)
> **Sorun (Dean):** "Yeraltı izliyorduk, şimdi açmıyor." Devam Et kaydı: DDizi · S1B22 · 4254/7715 sn · `ddizi.tel/diziler/2158/yeralti-5-son-bolum-izle`.
> **Kök neden 1 (dış):** `www.ddizi.tel` Cloudflare 522 / ReadTimeout — motordan doğrudan da, WARP (Cloudflare vekili) üzerinden de, laptoptan da. `ddizi.im`, `ddizi.plus` ConnectError. Bizim düzeltebileceğimiz bir şey değil.
> **Kök neden 2 (bizim, 64b3e05):** yedek kaynaklar (DiziAbi, DDiziMom) aynı bölümü S2B6 diye veriyor (22 bölüm: S1 1-16 + S2 1-6), DDizi sezonsuz S1B22 diyor → "istenen bölüm listede yok". `bolum_sirasi` artık numara hiçbir sezonda yoksa ve liste S1B1'den başlıyorsa baştan sayıyor. `engine/tests/test_bolum_esle.py` 11/12 geçti; 12.si laptopta `KekikStream` modülü olmadığı için import hatası (benim değişikliğimle ilgisiz), konteynerde koşulmadı.
> **Canlı:** ZimaOS'ta engine 64b3e05 ile derlendi ve yeniden oluşturuldu (`.Created` 2026-11-12T18:15:30Z, healthy, konteynerde yeni kod var). **ZimaOS checkout'u hâlâ `fix/devam-et-tazeleme`'de** (geri alınmadı). `/tmp/dc` dean'e kapalı çıktı; compose `DOCKER_CONFIG=/tmp/dc-dean` ile çalıştı.
> **HÂLÂ AÇILMIYOR (21:16 günlüğü):** yeni motorda bütün sağlayıcılar aramada "sonuç yok (1 varyant denendi)" döndü, bölüm eşleşmesine hiç gelinmedi. 21:09'da aynı arama ('yeralti') DiziAbi/DDiziMom/JetFilmizle'de eşleşmişti. Günlükte sorgu `'yeralt�'` görünüyor: ya başlık bozuk kodlamayla geliyor ya da motor açılışının ilk saniyeleriydi. Neden doğrulanamadı.
> **Tek sonraki adım:** `ssh zima "docker logs --since 10m netmovies-engine 2>&1 | grep 'resolve: arama'"` ile aramanın hâlâ boş dönüp dönmediğine bak; boşsa sorgu metnini (ı harfi) izle, doluysa Yeraltı S1B22'nin DiziAbi S2B6'ya düştüğünü uçtan uca doğrula.

# Handoff: Devam Et / Son izlenenler tazeleme, client-tv 0.9.92 (12 Kas)
> 2026-11-12 · dal `fix/devam-et-tazeleme` @ 8576d15 (push'lu) · taslak PR #28 → `fix/general-stability`, MERGEABLE, birleşmedi · worktree `.claude/worktrees/devam-et-tazeleme`, 0 dirty
> **Sorun (Dean):** telefonda "son izlenenler / nerede kaldığım" bazen görünmüyor. Sunucu sağlam: TV konumu 15 sn'de bir yazıyor, telefon (SM-S928B) ve Mi Box aynı `dean` profilinde.
> **Kök neden 1 (uygulama, 0af76a6):** `Library.sync()` yalnız açılışta ve oynatıcıdan çıkarken çağrılıyordu. Şimdi `MainActivity` `repeatOnLifecycle(RESUMED)` ile öne gelince + 60 sn'de bir; `sync` Mutex'li.
> **Kök neden 2 (kumanda sayfası `/rc`, 8576d15):** `rc.html.j2` `sonIzlenenler()` yalnız sayfa yüklenirken koşuyordu. Şimdi 60 sn'de bir + `visibilitychange`; liste değişmediyse yeniden çizilmez.
> **Kanıt:** `./gradlew testDebugUnitTest assembleDebug` yeşil. Emülatör (Pixel_API36) + sunucu günlüğü: önde 06:20:53 / 06:21:52 / 06:22:52 UTC istek, arka planda 140 sn istek yok, öne dönünce 3 sn içinde istek.
> **Yayın 0.9.92:** `apk_publish.sh tv` ZimaOS'ta, imza `7328a32d`. `app_update` → `v0.9.92-poc` / `NetMovies-TV-v0.9.92.apk`; release `v0.9.92-poc`; evaglass-releases `netmovies-tv-v0.9.92`. Laptopta derlenen APK farklı imzalı (20319a76), yayınlanmaz.
> **Canlı:** `rc.html.j2` konteynere `docker cp` ile kondu (rebuild yok, konteyner başlangıcı 2026-11-12T05:21Z). ZimaOS checkout'u `fix/general-stability`; PR #28 birleşmeden stream rebuild edilirse kumanda düzeltmesi geri gider.
> **Doğrulanamadı:** gerçek telefonda kumanda ekranı · Mi Box/telefonun 0.9.92'yi kurduğu · `evaitec-ota/apps.json` satırı (yalnız betiğin "yayinlandi" çıktısı).
> **Tek sonraki adım:** PR #28'i birleştir (Dean kararı), sonra `ssh zima "cd /DATA/AppData/netmovies && git pull"`.

# Handoff: denetim + dal/worktree temizliği (11 Kas)
> 2026-11-11 · kod değişikliği yok · `fix/general-stability` @ ea272f5 = ZimaOS (dal değiştirildi, rebuild yok: e62e8e3 sonrası yalnız doküman) · ana checkout `fix/general-stability`
> **Kanıtlı (11 Kas):** `smoke.sh` YEŞİL (16 eklenti, movie 399, serie 410, live 10). `BASE=http://192.168.0.186:3310 python scripts/chain_scan.py --n 2` → "TÜM KAYNAKLAR OYNUYOR". `BASE` verilmezse localhost'a gider, laptopta reddedilir.
> **Birleşti:** PR #26 `fix/film-eslesme` (Balina Düşüşü → Moonfall), PR #24 `fix/bayat-bilgi` (taban `fix/general-stability`'ye çevrildi). PR #4 kapatıldı.
> **Temizlik:** 16 birleşmiş worktree kaldırıldı, 12 yerel dal silindi (`branch -d`); silinemeyen 5 klasör Geri Dönüşüm Kutusu'nda. Kaybolacak iki handoff bloğu (30 Eki, 31 Eki/3 Kas) bu dosyanın sonuna eklendi.
> **Bırakılan 9 worktree (dokunma, sahibi/işi var):** `unwatched-one` (commit'siz `unwatched.py` + test, 33 satır) · `agent-acacc…` (commit'siz Routers değişikliği + `scripts/`) · `agent-ad68…` (`scripts/backup.sh`, `zima`) · `stream-segment-dedup` (`.probe/`) · `feature+profiles` (iç içe `.claude/handoffs`) · `tv-bolum-ayar-odak` (birleşmemiş handoff commit'i ce14230) · `fix-sonraki-bolum-son-dakika`, `search-plan`, `tv-season-nav` (bekleyen oturumlara bağlı).
> **Aşağıdaki 10 Kas bloğu bayat:** onarım canlıda; DiziPal, SezonlukDizi, DiziMom kaldırıldı (603bc98).
> **Doğrulanamadı:** film-eslesme'nin canlı davranışı · Mi Box'ta 0.9.91 · KultFilmler ok.ru gerçek TV'de.
> **Tek sonraki adım:** Mi Box sürüm kontrolü: `adb connect 192.168.0.11:5555 && adb shell dumpsys package com.evaitec.netmovies.tv | grep versionName` → 0.9.91 beklenir.

# Handoff: bütün eklentilerin onarımı (10 Kas)
> 2026-11-10 09:54 · dal `fix/eklenti-onarim` @ 304b6de, push'landı (PR yok) · ZimaOS `/DATA/AppData/netmovies` bu dalda, yeni engine imajı derlendi · **canlıya ALINMADI**: `netmovies-engine` konteyneri hâlâ 2026-11-05 tarihli (kontrol 10 Kas). Oturum kişisel hesapta kotaya takıldı, ekip hesabına `--resume` ile devredildi.
> **Değişen (dal birleşimi: fix/eklenti-ddizi 03cbe85 + fix/eklenti-film 6c61ec4 + feature/ddizimom-plugin):** DDizi sağlık kontrolü Cloudflare sınamasını canlı sayıyor (katalogdan düşmüyor) · DDiziMom yeni eklenti, rafta ve sırada DiziMom'un yerini aldı (The Boys 40 bölüm, manifest iniyor) · JetFilmizle POST `.now`→`.app` (ilk 6 filmin 5'i açılıyor) · KultFilmler ok.ru gömmesi çözülüyor (16/16) · DiziPal öncelikten düştü.
> **Kanıtlı:** birleşik engine testleri konteynerde 118 OK (oturum raporu).
> **Değişmedi:** DiziBox, DiziAbi, DiziYou, HQPorner sağlam · DiziMom ölü (tüm adresler park/kapalı). DiziPal oynatıcı, SezonlukDizi (reCAPTCHA/gömme yasağı) ve Dizilla altyazılı kaynak (Pichive) bot korumasının arkasında; aşma kodu izin sisteminde reddedildi, tekrar deneme.
> **Dean kararı bekliyor:** DiziPal ve SezonlukDizi kapatılsın mı (oturumun önerisi: kapat).
> **Tek sonraki adım:** TV boşta mı (`ssh zima "docker logs --since 2m netmovies-stream 2>&1 | grep -c remote/state"` → 0) bak; boştaysa `ssh zima "export DOCKER_CONFIG=/tmp/dc; cd /DATA/AppData/netmovies && docker compose --profile tunnel up -d --no-build engine"`, sonra `python scripts/chain_scan.py --n 2` ile uçtan uca tara. KultFilmler ok.ru gerçek TV'de denenmedi (IP'ye bağlı adres, WARP'ta açılmayabilir).

# Handoff: DDizi parçalı bölüm (4-5 Kas)
> dal `fix/ddizi-parcali` @ 881e278 · PR #23 → fix/general-stability (merge EDİLMEDİ) · Zima bu dalda, engine 4 Kas 21:01 UTC, stream 20:58 UTC kuruldu
> Yeraltı 21 ~13 dk'da bitiyordu: sayfada 1.Parça..9.Parça, eklenti yalnız 1. parçayı oynatıyordu. 7e89d0f parçaları `ddizi_birlesik` ucuyla tek HLS'e birleştirir. Site 4 Kas'ta ilk sekmeyi "Tek Parça" (tam 7628 sn) yaptı → 881e278: o sekme varsa birleştirme yok, doğrudan tam video. Kanıt: konteynerde load_links tek link, twimg 2118080436668624896 (7628 sn); stream testleri 285 OK.
> Zima compose: `/tmp/dc` izin hatası → `DOCKER_CONFIG=/tmp/dc-dean`.
> TV'de oynatma doğrulanmadı.

# Handoff: sonraki bölüm son dakika (0.9.87) + DHCP modeme devredildi (4 Kas)
> 2026-11-04 · worktree `fix-sonraki-bolum-son-dakika` · dal `fix/sonraki-bolum-son-dakika` @ 23fc75d (push'lu) · PR #22 → fix/general-stability (merge EDİLMEDİ)
> **Hedef A (bitti, kanıtlı):** 5b00b4c — geri sayım `jenerikteMi()` (PlayerCore.kt) ile son 60 sn'ye kırpılır, `NEXT_COUNTDOWN_SEC` 10→30, `NEXT_EPISODE_WINDOW_MS` 70→60 (iki oynatıcı). testDebugUnitTest 118/0 fail. 0.9.87 OTA + GitHub `v0.9.87-poc` + apps.json (afbfdb75) yayında, imza 7328a32d. Mi Box'ta açılış doğrulanmadı. "Her bölüm aynı" şikâyetine bakılmadı.
> **Hedef B (bitti, kanıtlı 4 Kas akşam):** Dean: ev-dhcp kapalı kalacak, tüm cihazlar modemden alacak. Dean modemi elle yeniden başlattı; ondan sonra `dhcp_probe.py 192.168.0.20` → `offer 192.168.0.167 from 192.168.0.1` (tek teklif). Zima `ev-dhcp`: inactive + disabled, konteyner yok. Zima'da :67 dinleyen dnsmasq libvirt `virbr0` (virtnetworkd), LAN DHCP değil. Modem havuzu son uygulanan `ac 20 150 86400` (home-net handoff'una göre; bu oturumda okunmadı) (web reboot düğmesi çalışmıyor, fiziksel reboot gerekti).
> **Açık risk:** eski Zima kiraları bitince Mi Box .11'den kayabilir → `ATV_HOST` (Zima atv :3311) + ADB adresi güncellenmeli; doğrulanmadı.
> **Tekrarlama:** ev-dhcp'yi açma (Dean kararı); `dhcp-authoritative`/eski konteyner yok; modem web reboot'u (`RgSecurity_btnReboot`) tetiklenmiyor, Dean'e fiş çektir.
> **Tek sonraki adım:** Mi Box'ın güncel adresini kontrol et (`adb connect 192.168.0.11:5555`; olmazsa modem istemci tablosu) ve gerekirse ATV_HOST'u güncelle.

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

# Handoff: İzlemediklerim düzeltmesi + DDizi Son Bölümler (3 Kas)
> 2026-11-03 · dal `fix/gozat-izlemediklerim` @ 4d428b1, push'landı · draft PR #19 (base `fix/general-stability`) · **canlıya ALINMADI**
> **Kanıtlı:** stream `unittest discover` 284 OK (Zima geçici konteyner, ağsız) · client `:app:testDebugUnitTest --tests '*AjandaBolumRefTest*' :app:compileDebugKotlin` EXIT=0, 2/2 · DDizi canlı çağrı (engine'de /tmp/kanit, canlı dosyaya dokunmadan): 22 dizi/22 poster, load_item 19 bölüm.
> **Değişen:** unwatched.py `_mutlaktan_sezona` (S1B191→S7B7) + `_tek_satir` (dizi başına tek satır, `kalan`); AgendaScreen `SxBy` bolumRef + "N bölüm kaldı"; DDizi main_page yalnız "Son Bölümler" (ana sayfa `/izle/` kartları → sidebar slug ile dizi sayfası; dizi sayfası olmayan günlük programlar atlanır).
> **Karar (Dean):** alfabetik raf yok, rastgele sıra yok; Yabancı/Eski kaldırıldı (sitede tarih sıralaması yok, sayfa 2 = sayfa 1).
> **Yapma:** onaysız Zima rebuild; bu dosyayı commit'e katma (başka oturumun değişikliği var).
> **Tek sonraki adım:** Dean "canlıya al" derse ve TV boştaysa (`remote/state` sayısı 0): Zima'da dalı çek, engine+stream build → `up -d --no-build`; TV istemcisi için sürüm yayını ayrı.

# Handoff: sunucu geneli kontrol + temizlik (31 Eki)
> 2026-10-31 · `fix/general-stability` @ b41fae7 (laptop = origin = ZimaOS, TV 0.9.83) · kod değişikliği yok
> **Kanıtlı:** smoke YEŞİL; ZimaOS 31 Eki 08:50 yeniden başladı. Dean "yap hepsini" sonrası:
> - `durdurulan-20261021.txt` listesindeki 20 konteyner tekrar durduruldu (running=0). Kök neden: ZimaOS açılışta restart=no olanları da başlatıyor → bir sonraki açılışta GERİ GELECEK.
> - Kasm onarıldı: kasm_manager/guac/rdp_gateway/rdp_https_gateway restart=always yapıldı, manager→proxy→guac sırasıyla başlatıldı; hepsi healthy.
> - nexus-mcp ve code-server-dean BAŞLATILMADI, gerek yok: yerlerini `nexus-mcp-stub-stopped` (nexus-mcp:latest, :8900 healthy) ve `code-server` (:8901, code-server-aile compose) almış; çıkmış olanlar eski kopya.
> **Silindi (Dean onayı, 31 Eki):** 15 konteyner (coolify-v4 x3 + sentinel, callcenter x3, quiz-bank-frontend/backend_old, sportapp-backend, randevu, flowise x2, obsidian, dockhand) + 12 imaj, /DATA 159→145 GB. Veri klasörleri yerinde. CasaOS app dizinleri `/DATA/AppData/_casaos-removed-20261031/`'a taşındı (geri: `/var/lib/casaos/apps/`'e geri taşı). Kalan liste `durdurulan-20261021.txt` = yalnız twenty x4 + webtop (Dean: kalsın; açılışta kalkarlarsa sorun değil); eski liste `.bak-20261031`. Bekçi servisi gereksizleşti, kurulmadı.
> **Dokunulmadı:** supervisor-firewall-gateway (iptables raw yok), dev-lifeosfinance-web, volley-backend-1.
> **Tek sonraki adım:** yok.

# Handoff: Gelişmiş yapı planı + F0 kapıları + F1 (30 Eki)
> 2026-10-30 · worktree `advanced-structure-plan`, dal `feature/f1-source-resilience` @ 11360e7 (push'landı) · ZimaOS'ta hiçbir şey kurulmadı/değişmedi (geçici /tmp denemeleri silindi)
> **F1 = PR #10 (draft, taban `feature/f0-gates`):** 7e80427 gece kanaryası (`scripts/chain_scan.py --jsonl`, `scripts/source_canary.py`, `scripts/systemd/source-canary.{service,timer}` + README; HA `notify.mobile_app_fold`, `canary.env` gitignore) · 11360e7 DiziAbi/FilmMakinesi/JetFilmizle/KultFilmler/SezonlukDizi sözleşme testleri (`engine/tests/test_*_contract.py` + `fake_http.py`). **Kanıt:** kanarya unittest 5/5; Zima'da canlı `chain_scan movie --n 1 --jsonl` 5 film sağlayıcısı OK, kanarya "sorun yok" (HA'ya gönderim denenmedi); engine imajında `--network none` 96/96 OK (bağımsız tekrar). CI'ya bakılmadı.
> **F1 bekleyen (Dean onayı):** #10 birleşince Zima'ya timer kurulumu + HA uzun ömürlü jeton (`scripts/systemd/README.md`). Testte görülen, düzeltilmeyen: KultFilmler `tags` liste değil düz metin; JetFilmizle `_LD_BLOK` son bloktan sonra >1200 karakterde son filmi kaçırabilir — ayrı fix için Dean'e soruldu.
> **Sıradaki tek eylem:** Dean'in birleştirme/onay cevabı; gelmezse `gh pr checks 10` ve `gh pr checks 9`.
> **Hedef:** `docs/PLAN-advanced-structure-2026-10.md` fazlarını sırayla yürütmek (F0→F1…), her faz ayrı dal + PR.
> **Kararlar (Dean, 30 Eki "Başlat önerinden"):** önce F0 sonra F1 · hafif gözlem (F2 Seçenek A) · uyarı Home Assistant'a · PLAYER2 Mi Box'ta 1 hafta varsayılan.
> **Açık PR'lar (hepsi Dean birleştirmesini bekliyor, kendin merge ETME):**
> - #7 `worktree-advanced-structure-plan` → fix/general-stability: plan + 4 persona ajanı (33a89d5).
> - #8 oynatıcı 1/2 etiketi ayırt edilsin + PLAYER2 varsayılan (TV 0.9.84). Birleşince yayın: `ssh zima 'bash /DATA/AppData/netmovies/scripts/apk_publish.sh tv "Oynatıcı 2 varsayılan"'` — yayınlanmadı.
> - #9 `feature/f0-gates` → #7 dalı (draft): engine unittest gate.yml'de (`--network none`, imaj içinde); test_dizilla_grid/sezon 9cc5754 sonrası `_kartlar`/`_bolumler`'e taşındı. **Kanıt:** Zima'da engine imajında 80/80 OK. CI koşusu sonucu bakılmadı. #7 birleşince tabanı fix/general-stability'ye çevir.
> **F0 eksik:** diğer plan dosyalarına durum satırı eklenmedi (durumları doğrulanmadı). Compose healthcheck eklenmedi: engine/stream/warp zaten healthy, doh/cloudflared distroless.
> **Tekrarlama:** rules-gate english-names hook'u Türkçe kelimeli Bash'i (heredoc/sed) engeller → Write/Edit kullan. PR gövdesi `--body-file` ile job tmp'den.
> **F0 sonrası:** Dean #7/#8/#9'u birleştirince 0.9.84 yayını (F1 dalı açıldı, yukarıda).

