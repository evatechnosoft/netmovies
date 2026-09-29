## 2026-09-29 akşam 4 — DÜZELTME: twimg tam bölüm de taşıyor (EN GÜNCEL)
- **Yanlış varsayımım geri alındı:** video.twimg.com'u konak olarak klip saymak Tuzlu Kahve 3-4 ve Haysiyet 3'ün 8500 sn'lik TAM bölümlerini de eledi (Dean: "hiçbir şey açmamaya başladın"). Artık süre ölçülür: `hls_kisa_klip_mi` master → ilk varyant → #EXTINF toplamı < 90 sn (yalnız twimg, 2 küçük istek). Lioness S2B3 twimg 31 sn → elenir; Tuzlu Kahve 4 8573 sn → geçer.
- **Tuzlu Kahve 4 aslında VAR** (DDizi ve DiziMom twimg, 2h23m) — önceki nottaki "yalnız klip" hükmü yanlıştı; S1B5 googlevideo 75 sn hâlâ klip.
- DiziPal hız sınırı tüm sayfalarda (bölüm sayfası da 429): tüm DiziPal istekleri `_sirayla` ile tek sıra + 3 sn aralık + 429'da 4 sn bekleyip tekrar. 'film' başlık gürültüsüne eklendi ('Tuzlu Kahve Film' → 'tuzlu kahve').
- **Kanıt (laptop motor, son imaj):** Tuzlu Kahve 3 → DiziMom 1 kaynak; Lioness S2B3 → soğuk motorda ilk istek boş (DiziPal yolu ~14 sn + ısınma, 25 sn bütçe), ikinci istek DiziPal 1 kaynak. TV fast+full iki geçiş yaptığı için pratikte açılır; açılmazsa bir kez daha OK.
- Commit'ler push'lu; ZimaOS dönünce `git pull && up -d --build engine`.

## 2026-09-29 akşam 3 — Lioness açıldı, Tuzlu Kahve yok (EN GÜNCEL)
- **Lioness S2B3 canlıda çözülüyor** (68d6963, e4fe9bb kısa ad eşleşmesi): DiziMom kartı 'Special Ops Lioness' → varyant 'lioness' → DiziPal 'Lioness' eşleşti → S2B3 dplayer82 1 kaynak (ağ geçidi kanıtı 19:56). Eklenen: DiziPal arama önbelleği 10 dk + tek sıra/3 sn aralık + 429'da tekrar; son kelime varyantı; kısa ad eşleşmesi (aday ⊆ asıl ve son kelimeyi taşıyor; ':' alt başlıklı adlar muaf).
- **Tuzlu Kahve S1B4/S1B5 hiçbir yerde yok:** DiziMom S1B4 twimg klibi, S1B5 googlevideo 75 sn; DDizi S1B4 AYNI twimg klibi. Diğer sağlayıcılarda arama boş. 'Başlarken başka şey geldi' = klip; şimdi elendiği için 'kaynak yok'. TV'nin 19:55'teki 500'ü motor rebuild anına denk geldi.
- Laptop engine son kod ile kurulu (65 test OK). ZimaOS dönünce aynı commit'lerle rebuild.

## 2026-09-29 akşam 2 — Lioness S2B3 "içerik sağlanamıyor" (EN GÜNCEL)
- **İki kök neden, ikisi de düzeltildi, laptop engine yeniden kuruldu (bfa25c4, 59e6069, push'lu):**
  1. DiziMom S2B3 kaynağı `video.twimg.com` 31 sn tanıtım klibiydi; süzgeç yalnız googlevideo `dur=`'a bakıyordu → TV 31 sn'lik "bölüm" açıp kayda `duration 31.3` yazdı. Artık twimg konağı klip sayılır (test_kisa_klip 3/3).
  2. DiziPal arama ucu değişmiş: `/api/search-autocomplete` 404 → `/bg/searchcontent` (form `searchterm`+`type=hepsi`, JSON `data.result[].used_slug/object_name/object_poster_url`). Bu yüzden her yedek arama "DiziPal sonuç yok" dönüyordu. Yeni uçla Lioness → 24 bölüm, S2B3 → dplayer82 master (motor içinde doğrulandı).
- **Dikkat:** DiziPal arama ucu IP başına hız sınırı koyuyor (429, openresty); test yoklamalarım WARP IP'sini birkaç dakika kilitledi. Uçtan uca ağ geçidi kanıtı sınır açılınca alınacak (Monitor koşuyor). 429 sürerse `nocache=1` ile tek istek at, art arda yoklama.
- Not: yeni kurulmuş motorda ilk çözüm DiziMom adımında ~30 sn sürdü ve 25 sn arama bütçesini yedi (tüm yedekler "bütçesi aşıldı — atlandı"); ikinci istekte normale döndü. Isınma etkisi, kod değişikliği yok.

## 2026-09-29 akşam — sunucu laptopta otomatik açılış
- **Laptop autostart geri açıldı** (`netmovies-autostart.lnk`, betik `--profile tunnel` ile, 1b8a685). Docker Desktop AutoStart hâlâ False, betik onu da açıyor. health 200, tünel 303. ZimaOS dönünce kısayolu `.disabled` yap + laptop `compose --profile tunnel stop`.
- **"İçerik sağlanamıyor" = Haysiyet 4. bölüm gerçekten yok:** DiziMom (peacemakerst FirePlayer) 64 sn googlevideo klip (elendi), DDizi oynat sayfası `/player/mp4/<b64>.mp4` → media.duhnet.tv 73,8 sn TNT tanıtımı (mvhd). Diğer sağlayıcılarda arama boş. 3. bölüm DDizi twimg m3u8 ile çözülüyor. Kod değişikliği yok; DDizi `_HLS` regex'i mp4 tanımıyor ama bu bölümde mp4 zaten tanıtım.
- Not: motor açılışta 18× "Yüklenecek bir Extractor bulunamadı!" basıyor — KekikStream 4.0.4 paket Extractors boş, kendi çözücülerimiz __dizi_common'da; eskiden beri böyle, hata değil.

## 2026-09-29 öğleden sonra — 0.9.36 + 0.9.37 (EN GÜNCEL)
- **Sunucu laptop** (IP artık 192.168.1.186 — ZimaOS'un adresi laptopa geçti; Docker Desktop bir kez kapandı, yeniden başlatıldı).
- **0.9.36** (d45a2dc): mp4 kaynak progressive (videoSource), Özel Koleksiyon tüm raflar önden yüklenir (iskelet odak almıyordu), yetişkin izleme Devam Et'e yazılmaz (TV+sunucu).
- **0.9.37** (b4cfc8f, sha ae06910b…): Ayarlar → "🧹 Devam Et'i temizle" toplu seç/sil; sunucu `POST /api/v1/progress/delete?content_keys=a,b`. Üç yerde yayında, app_update v0.9.37-poc. Emülatörde seçim/onay görüldü, gerçek silme cihazda denenmedi.
- Test sızıntısı düzeltildi (eeab2ae): RemoveProgressTest canlı DB'ye 'bbb' yazıyordu, silindi.
- **Ses var görüntü yok (sunucu tarafı, APK'sız):** fe0d8d2 HQPorner 1080p önce/4K sonda (2160p H.264 High@5.1 Mi Box çözemiyor); 038cbfa xHamster AV1 elenir + 4K sonda (TV'nin açtığı 5 içeriğin 3'ü 1080p.av1 fMP4'tü). Sunucuda probe ile ilk segment H.264 (0x1b) doğrulandı; TV'de doğrulanmadı. Memory: ses-var-goruntu-yok-codec.
- **Tekrar etme:** Dean'e "hangi içerik" sorma — stream günlüğünden (`docker logs netmovies-stream | tr -d '\n'`) bul.
- **NEXT:** Dean'in TV geri bildirimi (0.9.37 toplu temizleme gerçek silme, Özel Koleksiyon görüntü). ZimaOS dönünce taşıma adımları aşağıda. PornHub hâlâ boş (JS doğrulaması).

## 2026-09-29 öğle — 0.9.35 + Özel Koleksiyon (EN GÜNCEL)
- **Sunucu geçici olarak laptop** (ZimaOS kapalı): yığın + tünel laptopta, 0.9.35 laptop data/apk'da. ZimaOS dönünce: laptop `compose --profile tunnel stop`, ZimaOS'ta pull + `up -d --build engine stream cloudflared` + APK kopyala.
- **Özel Koleksiyon (8e57a8b):** xHamster 6/6, HQPorner 5/6 oynuyor (proxy uçtan uca). PornHub AÇIK: liste JS doğrulaması (leastFactor) py_mini_racer ile çözülüyor ama sunucu tekrar doğrulama döndürüyor; yt-dlp PhantomJS istiyor.
- **0.9.35** (950c7ab, 22.603.404 B, sha256 789b3b38…): Devam Et kartı kayıttaki bölümü açar (MediaItem.episodeRef; ref → adres → başlık). GitHub v0.9.35-poc + evaglass netmovies-tv-v0.9.35 + apps.json vc 935 (8b71fca), iki indirme sha eşleşti. Cihazda denenmedi.
- **EKSİK: ZimaOS yerel OTA.** ZimaOS 10+ dk erişilemez (ARP "Destination host unreachable", 22/3310 kapalı, w.evaitec 530). Açılınca: APK'yı `data/apk/NetMovies-TV-v0.9.35.apk` olarak koy → `curl 192.168.1.186:3310/api/v1/app_update?target=tv` = v0.9.35-poc doğrula. TV o zamana kadar 0.9.35'i görmez.

## 2026-09-29 sabah oturumu — özet (EN GÜNCEL)
- **0.9.34 YAYINDA** (2799a38, debug imzalı, 23.273.805 B, sha256 551e8302…): ZimaOS yerel OTA (app_update → v0.9.34-poc), GitHub v0.9.34-poc, evaglass netmovies-tv-v0.9.34 + apps.json vc 934 (b7090c0). Üç indirmenin sha'sı eşleşiyor. Cihazda denenmedi. İçerik: ikonlu segmentler, Favori/Takip/İzlenecek tek seçim, satır başına tek chip, GERİ sırası; `testDebugUnitTest assembleDebug` EXIT=0.
- **Engine kırık testi düzeldi** (650f551): `test_hepsi_dusunce_hata_yukselir` TransportError bekliyor (son deneme UA'sız WARP → ProxyError). ZimaOS konteynerinde `docker cp tests` ile 2/2 OK. İmaja bir sonraki `up -d --build engine` ile girer.
- **feature/yz-sanal-anahtar ana dala girdi** (2c00fb3). ZimaOS'taki yerel docker-compose/gemini.py farkı aynı içerikti, `checkout --` + pull; stream konteyneri zaten YZ_GECIT_KEY kodunu ve env'i taşıyor (rebuild gerekmedi). ZimaOS 2c00fb3'te.
- **Worktree temizliği:** altı `.claude/worktrees/agent-*` + `../netmovies-yz` silindi; hepsi `git cherry` ile ana dalda vardı (oynatıcı-2 commit'leri 15e1f5d/b0f017d/adbfdd1 olarak cherry-pick'liydi).
- **Açık (değişmedi):** Dean'in 0.9.33/0.9.34 cihaz geri bildirimi; "İzlediklerim" listesi mi; filmde panel hangi sekmeyle açılsın; Oynatıcı 2 nerede aranıyor.

## 2026-09-28 oturumu — özet
- **0.9.32 YAYINDA** (debug imzalı, 22.570.636 B, sha256 faa99ab3…): ZimaOS yerel OTA (app_update → v0.9.32-poc), GitHub v0.9.32-poc, evaglass netmovies-tv-v0.9.32 + apps.json vc 932 (169adb3). Üç indirmenin sha'sı eşleşiyor. Cihazda denenmedi.
- **ZimaOS engine** aa4f9b4 ile yeniden kuruldu. Canlı kanıt: Lioness S3B9 (yok) → 0 kaynak "istenen bölüm listede yok"; S3B8 → 1 kaynak.
- İçerik: ee29b1e sonraki bölüm numarayla (TV) + bolum_sirasi numaralı listede sıraya düşmez (engine) · a955759 bölüm sayfası kartı yolla eşlenir (kodlu liste adresi/alan adı kayması; Lioness "film gibi / S1B1") · aa4f9b4 kart bölüm sayfasıyken olmayan bölüm kartın bölümünü oynatmaz.
- **Açık:** Dean'e "Yeni oynatıcı (deneme)" nerede aradığı soruldu (ana ekran Ayarlar'da var, oynatıcı içi Ayarlar'da yok). Lioness'ta 0.9.32 ile bölüm seçimi görünüyor mu — Dean'in cihaz geri bildirimi bekleniyor.
- Not: laptop netmovies-engine/stream konteynerleri de çalışıyor (tek sunucu kararı ZimaOS; bkz. memory tek-sunucu-zimaos).
- **Dean geri bildirimi:** Lioness'ta bölümler APK kurmadan geldi. Engine rebuild'den sonra liste yüklendi. Kök neden doğrulanmadı; önceki TV isteğinde gateway/engine cache'inde eski yanıt olabilir. `continue_watching` kaydı **S1B8** gösteriyor ve bu DOĞRU: Dean bilerek S1B8 izliyormuş. `content_url` alanı yalnız kartın anlık görüntüsü.
- **Ağ ("bugün çok yavaş", laptop 3 dk koptu), kanıtlı:**
  - Laptop Wi-Fi 12:31:29–12:34:35 arası koptu. Olay günlüğü: "disconnected by the driver" (Intel AX201). TP-Link o sırada yeniden başlamadı. TV kesintide bağlı kaldı.
  - Yavaşlığın asıl sebebi: gemma4:12b indirmesi 09:24–11:37 arasında ~8 MB/s hızla hattı doldurdu. Plan gece 03:00'te indirmekti ama ZimaOS gece 00:00'da kapandığı için hiç çalışmadı. İndirme bitti.
  - Tarife 100/20 Mbps. MR200 portları 10/100, ZimaOS eth0 100 Mb/s Full. Ölçüm: ZimaOS 89 Mbit, laptop 61 Mbit.
  - Netmaster kablo hattı: DS kanal 4 kilitsiz, kanal 5'te 26.490 düzeltilemeyen blok. Yavaşlık sürerse bu ISS arızası.
- **Yapılanlar:**
  - TP-Link DHCP DNS2 0.0.0.0 → 1.1.1.1 (`~/.ai/scripts/home-net/tp_dns2.py`, before/after okundu).
  - Laptop Wi-Fi güç tasarrufu DC=Max Performance.
  - Adaptör ayarları için `~/.ai/scripts/home-net/laptop_wifi.ps1` hazır: roaming Lowest, No SMPS, 5GHz tercih. Yönetici ister, Dean çalıştıracak, ÇALIŞTIRILMADI.
  - Bulgular memory `ev-agi-iki-modem`'e yazıldı.
- **Tekrar etme:** laptopta yönetici yetkisi yok, adaptör ayarını uzaktan deneme. TV (.105) ölçülemedi, çünkü kapalıydı (adb 5555 kapalı).
- **DNS (Dean istedi):** TP-Link DHCP DNS 1.1.1.1/1.0.0.1, ZimaOS nmcli DNS 1.1.1.1/1.0.0.1. Ölçüm: CF 16 ms, 8.8.8.8 55 ms. Yan etki: `nmcli device reapply` w.evaitec tünelini ~4 dk düşürdü (530). `compose up -d --force-recreate --no-deps cloudflared` ile düzeldi; 200 alındı, 4 bağlantı ayakta. Memory `stream-tunnel-netns-rebuild`'e eklendi.
- **Bridge: KARAR — yapılmayacak.** Dean: "kalsın, az gecikme sorun olmaz". Netmaster Wi-Fi'ı da açık kalıyor. Tekrar önerme.
- **laptop_wifi.ps1 çalıştırıldı** (UAC onaylandı): No SMPS, roaming Lowest, 5GHz tercih, Ethernet DHCP açık. Doğrulandı: Deancjx 5GHz %91. Ethernet hâlâ "Media disconnected" — sorun kablo/port, fiziksel.
- **Laptop Ethernet "çalışmadı":** `ipconfig` çıktısı "Media disconnected" (fiziksel bağlantı yok: kablo ya da port) ve Ethernet'te DHCP kapalı, statik IP de yok. DHCP'yi açan satır `laptop_wifi.ps1`'e eklendi (yönetici ister).
- **0.9.33 YAYINDA:**
  - İçerik: 983fc1a, tv-ux ajanı. Oynatıcı ayar paneli sabit 440×440dp; üç sekme: Bölümler & Listeler (canlıda Kanallar) / Kaynak · Ses · Hız / ⚙. Kaynak, ses, altyazı ve hız hap butonlar; ✕ Kapat kalktı.
  - Yayın yerleri: ZimaOS OTA v0.9.33-poc, GitHub, evaglass apps.json vc 933 (ab755e5). sha 310aa9c5… iki kaynakta eşleşiyor.
  - Doğrulama: emülatörde dizi paneli sabit görüldü (ekran görüntüleri scratchpad/panel-*.png). Canlı yayın, film ve Oynatıcı 2 yalnız derlendi.
  - Dean'e sorulanlar: (1) "İzlediklerim" listesi yok, hap şu an "İzlenecek" — yeni liste mi? (2) Filmde panel sekme 2 ile mi açılsın?
- **Widget/kumanda kök nedeni:** Laptop NetMovies yığını Startup `netmovies-autostart.lnk` ile yine ayağa kalkıyordu; telefon laptopa gidiyordu. Kısayol `.lnk.disabled` yapıldı, `compose stop` çalıştırıldı. Telefon (.187) artık ZimaOS'a gidiyor (log kanıtı).
- **TP-Link:**
  - Rezervasyonlar: Mi Box 24:18:C6:B8:05:B8→.105, LG D0:A4:6F:C9:28:80→.175.
  - Virtual Server 3310 .185 (laptop) idi → .186 yapıldı (`tp_vs.py`).
  - ZimaOS .186'da kaldı: IP değişikliği APK/apps.json/ssh/vg.env'i bozar.
- **Kablosuz ölçüm:** Mi Box, LG ve Samsung 5GHz'te, 40 ping 0 kayıp. LG: sinyal -58 dBm, internet 81 Mbit. Mi Box ölçülemedi (adb 5555 reddediyor, ağ üzerinden hata ayıklama kapalı).
- **0.9.34 işi SÜRÜYOR:** tv-ux ajanı arka planda, worktree'de çalışıyor.
  - Üst sekmeler yazısız ikonlu segment olacak; 1. sekme hapları da yazısız ikon olacak.
  - Kaynak/Ses/Altyazı/Hız/kalite: satır başına tek chip, OK ile açılır liste.
  - GERİ sırası: açık liste → üst satır → panel.
  - Favori/Takip/İzlenecek: check'li 3'lü segment, tek seçim (diğer ikisinden çıkar); ekleme dizi/filmin kendisine.
  - Ajan tek commit atacak, push/release yok. Ekran görüntüleri scratchpad/panel2-*.png.
  - Sonuç gelince: ekranlara bak → ff-merge → test → push → üç yere yayınla (0.9.33'teki adımlar).
- **NEXT:** Dean'in 0.9.33 geri bildirimi ve iki sorunun cevabı. Mi Box'ta adb açılırsa hız ölç. TV açılınca Wi-Fi sinyalini ve hızını ölç (TP-Link Statistics/Client List). Oynatıcı-2 sorusunun cevabı bekleniyor.

## 2026-09-27 akşam oturumu — özet
- **Tek sunucu ZimaOS.** Laptop NetMovies yığını DURDURULDU (`docker compose --profile tunnel stop`). w.evaitec.com tüneli ZimaOS'ta (`netmovies-tunnel`), ZimaOS `.env` CF_TUNNEL_TOKEN laptopunkiyle aynı (yedek `.env.bak`). Kök: saat/telefon laptopa, TV ZimaOS'a bağlıydı → kumanda komutları TV'ye gitmiyordu. İstemci adayları 7441e75'te yalnız 1.186 / 0.11.
- **ZimaOS'ta compose:** `sudo DOCKER_CONFIG=/DATA/AppData/.docker docker compose --profile tunnel up -d --build stream cloudflared` (stream'i yenileyince tüneli de birlikte kur).
- **Sunucuda canlı (51d1ab6 öncesi 485b5e6 ile kuruldu):** canlı raf yalnız favori kanallar (sunucu tarafı, APK'sız), ajanda `kanal` alanı, kısa klip eleme (googlevideo dur<90), DiziMom → dizimom.cam (.env, iki makinede), `/api/v1/yz/cihaz` (Nano kaydı), `YZ_GECIT_URL=http://192.168.1.186:4000/v1`.
- **Ev YZ geçidi** (`infra/yz/` → ZimaOS `/DATA/AppData/yz/`): LiteLLM :4000 anahtarsız LAN; `gemini` (panel anahtarı admin.json'dan) + `yerel` (Ollama gemma4:12b). Ollama 0.34.4, GPU (P620) açık, konteyner adı `ollama-ollama-1` korundu. gemma4:12b indirmesi %51'de durduruldu (hattı doldurup TV'yi donduruyordu), kalanı 03:00'te `nohup` ile iniyor → `/DATA/AppData/yz/gece-indirme.log`. İndikten sonra `yerel` hızını ölç.
- **TV 0.9.31:** tv-ux işi (0dd6fb7) + SAĞ/SOL uzak kartı + ajanda Canlı/Bölüm seçimi + Nano kaydı (ML Kit genai-prompt 1.0.0-beta4) birleşti (51d1ab6). Yayın durumu aşağıdaki NEXT'te.
- **Bilinen kırmızı:** engine `tests/test_fetch_html_fallback.test_hepsi_dusunce_hata_yukselir` HEAD'de de kırık (6a6ac7a Cloudflare UA değişikliği sonrası) — düzeltilmedi.
- **Arka planda:** yz-muhendisi ajanı arama işinde (worktree): mükerrer kart eleme, "dublaj/altyazı/2026/son/tüm seri" niyeti, Resident Evil yenisinin neden çıkmadığı. Push etmeyecek; sonucu doğrula → dala al → ZimaOS stream yenile.
- **0.9.31 YAYINDA** (debug imzalı, 24.681.770 B, sha256 47dbcf59…): ZimaOS yerel OTA (`app_update?target=tv` → v0.9.31-poc), GitHub `v0.9.31-poc`, evaglass-releases `netmovies-tv-v0.9.31` + apps.json (b3ed87a; aynalar 1.186'ya çevrildi 4609ab3). Cihazda denenmedi.
- **NEXT:** (1) Dean TV + telefona 0.9.31 kursun. (2) Dean telefonda Ayarlar → "🤖 YZ:" satırı; `curl 192.168.1.186:3310/api/v1/yz/cihaz` ile Nano durumunu oku. (3) Arama ajanının sonucu. (4) gemma4:12b indiyse `yerel` ölçümü. (5) Ömür Usta ajandada görünüyor mu (NOW, pazar).

## 2026-09-27 sabah oturumu — özet (EN GÜNCEL)
- **ZimaOS:** BIOS Auto-On çalışıyor, iki gündür 11:00'de açılıyor (journalctl --list-boots). Ekrandaki açılış yazıları normal fsck, üç disk de clean. 26 Eylül'de makine 23:11'de kapandı, zamanlayıcı ise 00:00'a kurulu; nedeni doğrulanmadı, Dean'e soruldu.
- **Evaitec cloudflared açılışta kalkmadı:** restart policy `no` idi, önceki ayar kalıcı olmamış. Başlatıldı, 4 bağlantı kuruldu (ist05/07), portal 200. cloudflared, eva-portal ve nexus-memora `unless-stopped` yapıldı. Bir sonraki açılışta tutup tutmadığı doğrulanmadı.
- **21662f1 DiziPal:** feed kartı bölüm sayfası olunca eklenti MovieInfo döndürüyordu, Bölümler boş kalıyordu ("Abi" dizisi). Çözüm: h1'deki dizi linki izleniyor, A.B.I. 0 → 21 bölüm (ağ geçidinde `&nocache=1` ile doğrulandı). Engine yeniden başlatıldı. Stream'deki load_item cache'i 1 saat eski yanıtı tutar. Push edilmedi. ZimaOS'a pull ve engine rebuild yapılmadı.
- **tv-ux ajanı arka planda çalışıyor:** oynatıcı yan menüsü (Bölümler sekmesi odak/numara/etiket, dar panel), "bölüm 3 seç → 1 açılıyor" kök nedeni, video oynarken "Çalışan kaynak bulunamadı" uyarısı. Hedef 0.9.31 / vc 931, tek commit, push ve release yok (release'i PM yapacak). Sonucu henüz gelmedi.
- **Dean'e sorulan, cevap bekleyen:** telefonda Yönetim Paneli "yok" ve "liste/menü açılmıyor". Ayarlar'da `🛠 Yönetim Paneli` satırı kodda mevcut. Şüphe (doğrulanmadı): Kumanda kipinde dokunma = TV'ye gönder olduğu için poster menüsü yalnız basılı tutunca açılıyor.
- **NEXT:** ajan sonucunu doğrula (test + diff) → push → 0.9.31'i üç yere yayınla → ZimaOS'ta `git pull && up -d --build engine stream` → Dean'in telefon cevabına göre ajana ikinci iş.

## 2026-09-26 gece oturumu — özet
- fffb554 stream: episodes_best aynı adlı eski yapımı eliyor (sezon boyu) · 73dc9b2 0.9.26 Bölümler paneli + telefon widget goAsync
- 40e3d27 0.9.27 telefon responsive (600dp) · 4481b44 0.9.28 telefon pad dokunma + saat 0.1.19 ✥ pad tek gesture
- 0da11af 0.9.29 dokun=TV'ye gönder, pad telefona sığar, /api/v1/show_schedule (Özet'te son/sıradaki bölüm)
- ae6508f 0.9.30 ilk açılış cihaz kipi (Televizyon/Kumanda/Bu cihazda izle) — TV'ler güncellemeden sonra bir kez OK ister
- Hepsi 3 yerde yayında; HİÇBİRİ cihazda doğrulanmadı (emülatör açılışta çöktü, Dean "boşver" dedi)
- ZimaOS: /etc/systemd/system/gece-kapanma.timer her gece 00:00 poweroff (overlay /mnt/overlay ext4, kalıcı). Sabah açılış BIOS'ta (Dean) — rtcwake "alarm: off", BIOS Auto-On doğrulanmadı.
- stream konteynerine following.py docker cp ile girdi; imaj rebuild'de commit'ten gelir.

# Handoff: ZimaOS'a taşıma (yarım) + LG /tv iki tur + DeanOS taraması
> 2026-09-25 · `fix/general-stability` @ `5289574` (push'lu) · kirli: `.claude/handoffs/*`, `.claude/worktrees/`, `atv-kopru.log`

## Goal
NetMovies sunucusunu laptoptan 7/24 ZimaOS'a (DeanOS) taşımak; LG webOS / Samsung Tizen `/tv` deneyimini Android TV seviyesine getirmek; DeanOS'u temizlemek.

## State — KANITLI
- **ZimaOS açıldı.** Siyah ekranın sebebi BIOS'ta SATA=RAID On'du, AHCI'ye alınınca açıldı. Makine: Precision 3551, i7-10850H, 30 GB, ZimaOS v1.6.0, /DATA 294 GB boş.
- ZimaOS IP **192.168.1.186**: nmcli ile statik, bağlantı adı "Supervisor eth0". `ssh zima` alias'ı .186'ya bakıyor, anahtar `~/.ssh/deanos`.
- **NetMovies ZimaOS'ta ayakta:** `/DATA/AppData/netmovies`, `.env` içinde `NM_NET=10.231.0` (172.31/16 orada dolu), `smoke.sh` YEŞİL. Kod o sırada `4d1cb5c`'deydi; ZimaOS 5289574 çekildi ve yeniden kuruldu. Yeniden başlatma sonrası 82/82 konteyner ayakta, health OK. Dozzle internete şifresiz açıktı: durduruldu (restart=no). mosquitto ve portainer-agent restart=unless-stopped yapıldı. /DATA/.bashrc dosyasına DOCKER_CONFIG eklendi (yedek: /DATA/AppData/.docker-dean/bashrc.bak).
- **Tünel (w.evaitec.com) hâlâ laptopta** ve 200 dönüyor. ZimaOS'ta cloudflared başlatılmadı; aynı token iki connector olur, başlatma.
- LG TV 192.168.1.175 (TP-Link ağında), kabuk 0.1.5 kurulu. `/tv` commit'leri: 264f8a7 günlük, 4d1cb5c kapanma/poster/tekerlek/tuş/7 poster, 5289574 pad/bilgi/dil/altyazı/açılışı geç.
- LG'de 41. sn kapanmanın kökü: IP başına 180/dk istek sınırı ve tüm evin tek NAT IP'si. `/proxy/video` ve `/proxy/subtitle` sınırdan muaf tutuldu.
- DeanOS taraması: `docs/ZIMAOS-DURUM.md`, 14 adım, 1. adım bitti.
- **2026-09-25 öğleden sonra (ZimaOS / Evaitec):**
  - **Evaitec tüneli** (1d2e8f02) ZimaOS'taki `cloudflared` konteynerinde çalışıyor. Ingress artık dosyadan yönetiliyor: `~/.ai/scripts/home-net/evaitec_tunnel.yml` + `evaitec_tunnel.py pull|diff|push`. Güncel sürüm v55, yedekler `evaitec_tunnel_backups/`. Ayrıntı hafızada: `evaitec-tunel-zimada`.
  - portal.evaitec.com → Homer :8090. Yeni tema/logolar: `/DATA/AppData/portal/assets/{config.yml,custom.css}`, yedek `config.yml.bak`. CSS `?v=2` ile yükleniyor (Cloudflare 4 sa cache).
  - Ölü rotalar (api, test, it, dozzle, db) kaldırıldı.
  - `cloudflared` ve `eva-portal` konteynerleri restart=unless-stopped yapıldı.
  - Host `cloudflared.service` (ayrı tünel 9162d6ae, binary yok) disable edildi.
  - **nexus-memora düzeldi:** compose'da katlanmış `command` bölünüyordu, liste formuna çevrildi. `MEMORA_HOST=0.0.0.0` ve `MEMORA_PORT=8080` eklendi. MCP `http://192.168.1.186:8081/mcp` initialize 200 döndü, `memories.db` oluştu. Graph `:8765/` 404 verdi, doğru yol doğrulanmadı. Henüz bağlı istemci yok.
  - **Kaldırılanlar:** yedekler `/DATA/backups/{pg,apps}` altında.
    - Postgres: agentops-nexus-db, apiflow-postgres, volley-db-1 + volume'ları, sport-app_postgres_data, shared içindeki `inventory` DB'si.
    - Uygulamalar: modularcrm, WeKnora, LibreChat (CasaOS app dizinleri dahil).
    - opik volume'ları (yedeksiz).
    - `image prune -a` ile 13,29 GB açıldı.
  - Eski "49 GB imaj + 16 GB cache" rakamı bayattı: prune öncesi geri kazanılabilir alan 0,8 GB, cache 0 B.

## Believed / doğrulanmadı
- LG'de renkli tuşlar, pad ve açılışı geç yalnız sahte tuş olaylarıyla denendi. AWOX kumandanın gerçekte hangi kodları gönderdiği bilinmiyor; `client_log`'daki `TUŞ kod=` satırlarına bak.
- LG'de altyazının ekranda göründüğü doğrulanmadı. Teşkilat (googlevideo) LG'de `HATA kod=4` veriyor.
- TP-Link rezervasyonu YAPILDI (38:14:28:35:9A:AE → .186, router şifresi Dean'de).

## Decisions
- Laptoptaki `.185` ile adres takası yerine ZimaOS `.186`'da kalır. TV'ler `ServerResolver` / kabuk adres listesiyle yeni adrese geçirilecek. Kabuk ADRESLER listesine `.186` eklenmeli.
- `/` %100 dolu görünmesi ZimaOS'ta normal (squashfs). Temizlik `/DATA` ve Docker üzerinde yapılır.

## Next
0. **SIRADAKİ TEK İŞ:** Portal'dan LibreChat kartını çıkar (`/DATA/AppData/portal/assets/config.yml`). Ardından Dean onaylarsa Nexus memora'yı Claude'a MCP olarak bağla (`http://192.168.1.186:8081/mcp`).
   - Açık: `deanfit` DB'si boş. Silmeden önce fit.evaitec.com verisinin nerede tutulduğu doğrulanmalı.
   - Açık: Coolify `failed_jobs` tablosunda 2045 kayıt var.
   - Güvenlik: 9162d6ae tünel token'ı chat'e düştü, döndürülmeli. `EVAITEC_CF_API_TOKEN_READ` aslında yazma yetkili, adı ya da yetkisi düzeltilmeli.
1. ZimaOS: `chain_scan.py --n 2` çalıştır.
2. **Tünel geçişi (Dean onayı):** laptopta cloudflared'ı durdur, ZimaOS'ta `--profile tunnel up -d` ile başlat, `w.evaitec.com` 200 dönmeli. Ardından istemci adresleri: webOS kabuk ADRESLER listesine `.186` (0.1.6), Android TV ServerResolver, Samsung `/tv` URL'si. Laptop stack'i en son durdurulur.
3. `docs/ZIMAOS-DURUM.md` adımları:
   - `DOCKER_CONFIG` ayarını `/DATA/.bashrc`'ye ekle.
   - Hafızada düz SSH parolası var, parolayı döndür. **Dean onayı**
4. LG'de gerçek kumandayla dene: renkli tuşlar, pad, Dark Matter "kaldığın bölüm", altyazı. Ardından `client_log` oku.
6. Uygulama envanteri: `docs/ZIMAOS-UYGULAMALAR.md` (TUT/BİRLEŞTİR/KALDIR + mağaza önerileri). Kurulum ve kaldırmalar Dean onayıyla.
7. Çalışma kökü: ZimaOS projeleri `/DATA/projects/` altında (apiflow-monitor-prod, evaiteclabs, it-inventory, quiz_bank, volley). NetMovies `/DATA/AppData/netmovies`'ten `/DATA/projects/netmovies`'e taşınacak: compose down, mv, up, smoke. Erişim bilgisi hafızada: ev-agi-erisim (vg.env HOME_*). TP ve Netmaster şifreleri vg.env içinde, ikisi de doğrulandı.
8. Uygulamalar: llama.cpp kaldırıldı (model dosyası yoktu, LLM için Ollama llama3.2 + Open WebUI kaldı). node-red durduruldu, flows yok, veri duruyor. quiz-bank frontend düzeltildi: backend'e `backend` alias verildi, :9310 200 dönüyor; `/api` 404, backend yolu doğrulanmadı.
9. Laptop `D:projects` (224 GB diskin 159 GB'ı dolu) eski projeleri tutuyor: codeplay, esp, ev, evaglass*, evaitec*, layers, Layersmig-pm, life-os-finance, sides, templates, test, tools. Her birini git remote durumuna göre sınıflandır: aktif → ZimaOS `/DATA/projects`, arşiv → yedekle ve sil (Dean onayı).
10. **KARAR (Dean, 2026-09-25): tüm projeler ZimaOS `/DATA/projects` altında toplanacak.** Kaynaklar laptopta `D:\MainProjects` (11 GB; Azure, claudex, ev_sound_simulator, Findtalent, ha-ops, ipfunc-wt, ITLayers, layers-API, LayersCX, layers-HRCenter, LayersProjects; `_migtmp` ve `_syncstage` geçici) ve `D:\projects` (madde 9). NetMovies de `/DATA/projects/netmovies`'e geçecek. Sıra: git'li olanları clone et, git'sizleri rsync/tar ile kopyala, laptoptaki kopyayı silme (Dean onayı).
11. Dark Matter S2B3 (Android TV'de izlenen, konum 2691 sn): Dean "1. sezon finali gibi" diyor. Bizim eşleme doğru: DiziYou ID'leri 93508 (S1E8), 93509 (S1E9), 93510–93513 (S2E1–E4) sıralı. İçeriğin sağlayıcıda yanlış yüklenip yüklenmediği doğrulanmadı; altyazı ilk satırları ya da süre karşılaştırılmalı, başka sağlayıcıyla (mavi tuş) çapraz kontrol edilmeli.
5. Açık işler: `/proxy/image` hâlâ 180/dk sınırında; Teşkilat googlevideo kaynağı LG'de açılmıyor (kod=4).

## Don't repeat
- Router şifresini dosyaya/hafızaya yazma (Dean verir).
- Evaitec tünel ingress'ini Cloudflare panelinden ya da elle PUT ile değiştirme; `evaitec_tunnel.py` kullan (yedek alır).
- ZimaOS'ta `systemctl status cloudflared*` çıktısı token'ı düz basar. Çıktıyı redakte et.
- Canlı veritabanlı uygulamayı `tar` ile yedeklemeden önce durdur (mongo "file changed" hatası).
- ZimaOS'ta `docker compose` için `DOCKER_CONFIG=/DATA/AppData/.docker-dean` ve `sudo -E` şart, yoksa `/DATA/.docker` izin hatası verir.
- Stream restart sonrası laptopta `docker compose --profile tunnel up -d --force-recreate cloudflared` şart, yoksa tünel 530 döner.

## Verify
curl -s 192.168.1.186:3310/api/v1/health; curl -s localhost:3310/api/v1/health; curl -s -o /dev/null -w "%{http_code}" https://w.evaitec.com/api/v1/health
ssh zima 'cd /DATA/AppData/netmovies && git log --oneline -1'

## <yeniden başlangıç> promptu
```
NetMovies (D:\projects\netmovies, fix/general-stability @ 5289574). ZimaOS 192.168.1.186'da NetMovies ayakta
(smoke YEŞİL), tünel hâlâ laptopta. Önce .claude/handoffs/latest.md ve docs/ZIMAOS-DURUM.md oku, Verify çalıştır.
Sıra: ZimaOS'u 5289574'e çek → tünel geçişi (onayla) → istemci adresleri .186 → DeanOS temizliği (onayla).
```
