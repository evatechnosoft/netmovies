# DEVİR — 16 Eylül 2026, 14:42 · sarma motoru + bölüm kimliği

**Dal:** `fix/general-stability` @ `e9ded2c` · 0 kirli dosya · push EDİLDİ
**Sürüm:** TV `v0.3.3-poc` (saat `v0.1.4-poc` değişmedi)
**Katalog:** `evaglass-releases` @ `9c00313` (push EDİLDİ) — netmovies-tv/phone 0.3.3 (vc 303)

## Hedef

Dean'in iki şikâyeti: (1) oynatıcıda sarma basılı tutunca durmuyor, basmadan da
ilerlemiyor, bıraktığı yerde devam etmiyor; (2) Reacher 4x8=32 bölüm olmasına rağmen
panelde "Devam et — 123. bölüm" yazıyor + "kaynak denemesi 4/9'dayken kendiliğinden
başka içerik açıldı".

## Durum — kanıtlanmış

- **0.3.2 sarma motoru** (`8f355e7`). Basışlar `seekTarget`'ta birikir, ekranda
  `+2dk 30sn → 1:12:40`, son basıştan 350 ms sonra TEK `seekTo`. Basılı tutma 120 ms'de
  bire indirildi, adım tutma süresiyle büyür (10 sn / 30 sn / 1 dk). OK sarma
  beklerken hedefi hemen uygular, duraklatmaz. Kumandanın ⏪⏩ tuşları aynı motoru
  kullanır. SOL/SAĞ çift-basış varsayılanı KALDIRILDI.
  Kod: `PlayerScreen.kt` `seekBy` / `seekHold` / `commitSeek` · `RemoteInput.kt` `onHold`
  + repeatable uzun basışta `longFired = true`.
- **0.3.3 bölüm kimliği** (`2250b46`). Kayıt artık `S4B8` taşır, listede sezon+bölüm
  ile aranır. Web'in `S4 E8` biçimi de okunur. Sınır dışı eski indeks kaydı bölüm
  etiketi göstermez. Kod: `Library.kt` `episodeRef` / `parseEpisodeRef` / `episodeIndexOf`.
- **Testler:** `assembleDebug testDebugUnitTest` exit 0 (her iki sürümde). Yeni
  `EpisodeRefTest` 5 vaka.
- **Dağıtım üç yerde:** yerel OTA `/api/v1/app_update?target=tv` → `v0.3.3-poc` ·
  GitHub release `netmovies-tv-v0.3.3` (target main, indirme HTTP 200) ·
  `apps.json` vc 303 canlı (raw.githubusercontent'te doğrulandı).

## Durum — DOĞRULANMADI

**Hiçbir sürüm cihazda denenmedi.** Sunucu tarafı kanıtlı, ekran değil. Sarma
adım süreleri (1.5 sn / 4 sn eşikleri, 350 ms bekleme) masabaşı değerler —
televizyonda hissine göre ayarlanacak.

**"Kendiliğinden Reacher açıldı"** kök nedeni bulunamadı. Kod değiştirilmedi.

## Kararlar ve gerekçe

- **Sarmada iki ayrı kök neden vardı**, ikisi de düzeltildi: (a) her tuş tekrarında
  anında `exo.seekTo` → saniyede ~20 seek HLS'de üst üste biniyor, tuş bırakılınca
  kuyruk işlenmeye devam ediyordu; (b) repeatable uzun basışta `longFired` hiç true
  olmuyordu → parmak kalkınca gelen UP "tek basış" sayılıp bir 10 sn daha ekliyordu.
  Yalnız hızı düşürmek ikincisini gizlerdi.
- **Çift basış kaldırıldı** çünkü tanımlıyken tek basış 300 ms bekletiliyor ve art
  arda basışlar birikmiyordu — Dean'in "basmadan ilerlemiyor" şikâyeti buydu.
- **Bölüm kimliği indeks değil numara**: indeks o anki listenin özelliği, kimlik
  değil. Dizilla'nın sızıntılı listesinde 123. sıra kaydedilmiş, liste 32'ye
  düzelince ham sayı "123. bölüm" diye ekrana basılmıştı. Web zaten `S4 E8`
  yazıyordu; aynı sütun iki anlamda kullanılıyordu.
- **İkinci şikâyette kod değiştirilmedi**: `/api/v1/remote/poll` boş, kuyruk TTL
  120 sn, oynatıcı açıkken uzak komut onay kartı gösteriyor (`MainActivity.kt:246`).
  Sessiz geçişin bilinen yolu yok — kanıtsız kod değiştirmek yanlış yeri düzeltmek olur.
  Bunun yerine teşhis kancası kondu.
- **Teşhis kancası** (`2250b46`): oynatma günlüğü ilk gönderimi 30 sn bekliyordu,
  içerik o süre dolmadan değişince günlük HİÇ gitmiyordu — `client_log` bu yüzden
  boştu. İlk gönderim 6 sn'ye indi, ekran kapanırken `NonCancellable` ile son bir
  gönderim yapılıyor, açılış sebebi yazılıyor (`uzak komut / onay` ya da
  `kullanıcı seçimi`).

## Tekrarlama

- **Tuş tekrarı başına `seekTo` çağırma.** Hedefte biriktir, tek seek yap.
- **D-pad SOL/SAĞ'a çift basış atama.** Tek basışı bekletir, ardışık basış birikmez.
- **İzleme kaydına liste indeksi yazma.** Sağlayıcı/liste değişince çöp olur.
- **`client_log` boş diye "TV hiç oynatmadı" sanma.** 0.3.3'ten önce günlük yalnız
  30 sn'yi geçen oturumlarda gidiyordu.
- **"Kendiliğinden içerik açıldı" için `remote.py`'a dokunma** — kuyruk zaten temiz
  ve TTL'li; kanıt `client_log`'daki `açılış — ...` satırından gelecek.
- Git Bash'te `docker exec -w /usr/src/Stream` → başına `MSYS_NO_PATHCONV=1`.
- `gh release create` `--target main` olmadan release `/releases` listesine düşmez.

## Sıradaki TEK iş

**TV'de evaitecOTA → NetMovies 0.3.3 (vc 303) kur ve şunu dene:**
1. Bir FİLM aç, SAĞ'ı 3 sn basılı tut → gösterge büyümeli, bırakınca tek seferde
   oraya gidip oynamalı, fazladan atlama olmamalı.
2. SAĞ'a hızlı 3 kez bas → +30 sn tek atlama.
3. Sarma sürerken OK → hedefte durmalı, duraklatmamalı.
4. Reacher aç → panelde `Devam et — S4B8 · ...` yazmalı, "123. bölüm" DEĞİL.
5. `curl -s localhost:3310/api/v1/client_log` → `açılış — ...` satırı görünmeli.

Sarma hissi ağır/hafif gelirse eşikler `PlayerScreen.kt` `seekHold` içinde
(`heldMs < 1_500` / `< 4_000`) ve bekleme `seekBy` sonundaki `delay(350)`.

## Sonraya

- Telefon kumandasının `play` komutu hâlâ 0 tabanlı sıra gönderiyor (`remote.py`
  `build_command`, `episode` alanı). Aynı indeks varsayımı — telefondan bölüm
  seçiminde yanlış bölüm açılabilir.
- HANDOFF'taki "kart aksiyonları" listesi (madde 2-5: poster kartında D-pad ile
  gezilebilir ikon şeridi, favori/takip dışarı, uzun basmada 4'lü menü) duruyor.
