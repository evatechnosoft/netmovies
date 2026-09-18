# DEVİR — 18 Eylül 2026, 12:10 · oynatıcı düzeltmeleri

**Dal:** `fix/general-stability` @ `b79020e` · push EDİLDİ
**Katalog:** `evaglass-releases/apps.json` @ `8aae758` · netmovies-tv/phone **0.6.2 (vc 602)**
**Saat:** **0.1.14** — APK 23,2 MB → **850 KB** (debug buildType'inda R8) ve indirme
artik once Wi-Fi tasiyicisini isteyip yerel adresi yeniden cozuyor (tunelden inmiyor).
**Dean cihazda dogruladi: "oldu hizlandi".** R8 bu modulde ilk kez calisti, wear'da
birim test yok — kucultme degisikliginden sonra saatte bir kez ac.

## Bugün çıkan sürümler

- **0.5.1** favoriden açılan dizi kaldığı bölümden devam + gateway bayat bağlantı tekrarı
- **0.5.2** dil etiketi ("orijinal dil" rank'i), poster DUB/ALT/ORJ rozeti, menüde dil özeti
- **0.6.0** telefonda gerçek arama ekranı (tek satır + bilgi kartı), sıralama sunucuda
- **0.6.1** Bölümler paneli, alt bar kaydırma, odak dönüşü (aşağıda)
- **0.6.2** sonraki bölüm kartı 90 → 70 sn

Üç dağıtım yeri her sürümde güncellendi; her APK GitHub'dan indirilip sha256 + boyutla doğrulandı.

## Doğrulanmış

- `smoke.sh` YEŞİL (son koşu 0.6.0 sonrası) · `stream/tests` **149 OK** · Android derlemeleri BUILD SUCCESSFUL
- `chain_scan --n 2` → ÖLÜ KAYNAK 2, ikisi de geçici
- Tünel 200 (oturum başında cloudflared hiç ayakta değildi)
- Zengin arama: `search_all?query=reacher&group=1` → 6 satır, 3.1 sn
- Dil: DiziPal "Orijinal" → `rank 2 / orijinal dil`; katalogda `Dark -> ['ALT','DUB']`

## Doğrulanmamış

0.5.1'den 0.6.2'ye kadar **hiçbir sürüm cihazda görülmedi** — Dean'in fotoğrafları
hangi sürümden olduğu belirsiz (aşağıdaki açık buga doğrudan etki eder).

## AÇIK BUG #1 — bölüm etiketi S1B1 diyor, oynayan S2B1

Dean: "sezon 1x1 gördüğü için 2. bölüm gösteriyor… ileri dediğimde son bölüme
tekrar geçiyor ve oynatıyor."

**Toplanan kanıt (bugün):** HDFilmCehennemi listesi doğru ve sıralı —
`0:(1,1) 1:(1,2) … 5:(1,6) 6:(2,1)`, URL'ler de bölüm numarasıyla uyumlu
(`…2-sezon-1-bolum…` indeks 6'da). Tam ekran seçici `onSelect(idx)` ile GERÇEK
indeksi gönderiyor (`withIndex().filter`), panel içi liste de öyle. Etiket
`episodes[currentEpIndex]`ten okunuyor. Yani kodda kayma **bulunamadı**;
etiketin S1B1 demesi için `currentEpIndex == 0` olması gerekir.

**Sıradaki adım:** Dean'in TV'sinde kurulu sürümü öğren (Ayarlar → sürüm).
Fotoğraflar 0.5.0 veya öncesinden olabilir. Sürüm 0.6.1+ ise `client_log`
(`curl -s localhost:3310/api/v1/client_log`) ile seçim anını izle; `currentEpIndex`
nerede sıfırlanıyor — `remember(item.url)` anahtarı tetikleniyor olabilir
(`item.url` bölüm seçiminde değişmemeli).

## AÇIK BUG #2 — sezon 2'de 1 bölüm görünüyor

Kod değil **veri**: aynı dizi için HDFilmCehennemi **7** bölüm veriyor
(S1×6 + S2B1), DiziMom **20**, Dizilla **16**. Kart HDFilmCehennemi'den geldiği
için liste gerçekten eksik.

**Çözüm taslağı:** bölüm listesini en zengin sağlayıcıdan al ve seçilen bölümü
O sağlayıcının adresiyle oynat. Yeni uç (`/api/v1/episodes_best`) + oynatıcıda
`aktifPlugin/aktifUrl` state'i gerekir; `resolve_sources` zaten plugin+url+episode
alıyor, sözleşme değişmiyor.

## Bu oturumda düzeltilen kök nedenler (tekrarlanmasın)

1. **`load_item`'a kodlu adres gönderme** — httpx bir kez daha kodluyor, motor 500.
   Sunucu içinden çağrıda `unquote_plus` ile HAM gönder.
2. **Test canlı `/data`'ya bağlanmasın** — `LANG_MEMO_PATH` tempdir'e izole edildi.
3. **`docker exec -w`** Git Bash'te `MSYS_NO_PATHCONV=1` ister.
4. **Stream rebuild tüneli düşürür** — `up -d --build stream cloudflared` birlikte.
5. **Panel içi liste yazmak yetmez, GİRİŞLERİ de çevir** — 0.5.0'da liste panele
   alınmıştı ama üç giriş de hâlâ tam ekran seçiciyi açıyordu.
6. **Odak sahipliği efektinin anahtarları eksikse D-pad ölür** — `showStartPanel`
   yoktu, tam ekran liste kapanınca sarma tuşları hiçbir yere gitmiyordu.
7. **Sabit genişlikli şerit TV'de taşar** — 11×64dp alt bar 640dp ekrana sığmıyor,
   taşan düğme çizilmiyor ama odak alıyordu.
