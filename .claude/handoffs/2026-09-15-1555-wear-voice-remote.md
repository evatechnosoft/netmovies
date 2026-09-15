# DEVİR — saat uygulaması: sesli kumanda + halka anahtarı (v0.1.3)

**Zaman:** 15 Eylül 2026, 15:55 · **Dal:** `fix/general-stability` · **Oturum:** 3178259a

## Hedef

Dean'in saat (Galaxy Watch 6 Classic 47 mm) isteği: geç açılışı düzelt, sunucuda
tut, arama ekranı + sesle arama, TV'de oynat, döner çerçeve ile sarma/ses ve tek
düğmeyle aralarında geçiş, yükleme göstergesi olarak "iç içe halkamız".
Ardından: APK adını düzgün koy, evaitecOTA'da yayınla (katalog 0.1.2'de kalmıştı).

## Durum — HEPSİ BİTTİ ve yayında

Commit'ler (netmovies, `fix/general-stability`, push EDİLMEDİ):
- `3d880b1` — iki aşamalı yükleme, halka kipi anahtarı, kenar yükleme halkası, cache ısıtıcı
- `3f753ed` — sesli komut: tanınan metin `/api/v1/voice` ucundan niyete çevrilir
- `6b3a622` — Gradle çıktı adı `NetMovies-Wear-vX.Y.Z.apk`

evaglass-releases (`main`, push EDİLDİ): `66acab8` — apps.json vc 103.

### Doğrulanmış (tool çıktısı var)

| İddia | Kanıt |
|---|---|
| Wear derleniyor | `./gradlew :wear:assembleDebug` EXIT=0 → `NetMovies-Wear-v0.1.3.apk`, 23.178.305 bayt |
| Sunucu testleri yeşil | `Ran 132 tests ... OK` (docker exec, `-w /usr/src/Stream`) |
| Kapı yeşil | `bash scripts/smoke.sh` → "SONUÇ: kapı YEŞİL" |
| Cache ısıtıcı çalışıyor | `aggregate_new?type=movie` 1. çağrı 4.83 sn, 2. çağrı **0.062 sn** |
| Devam Et hızlı | `continue_watching` 0.157 sn |
| Sesli komut uçtan uca | `POST /voice {"text":"10 saniye geri al"}` → `sent:true`; `remote/poll` → `{"type":"transport","action":"seek","value":-10.0}` |
| Yerel OTA | `/api/v1/app_update?target=wear` → `tag v0.1.3-poc`, 23.178.305 bayt |
| GitHub release | indirme http 200, 23.178.305 bayt (`netmovies-wear-v0.1.3`) |
| Katalog canlı | raw.githubusercontent apps.json → `0.1.3 vc 103` |

### DOĞRULANMADI — sıradaki iş bu

**Saatte hiç denenmedi.** Cihazda görülmesi gereken üç şey:
1. 🎙 düğmesi → mikrofon açılıyor mu (Android 11+ paket görünürlüğü için manifeste
   `<queries><action android:name="android.speech.RecognitionService"/>` eklendi;
   eksikse ekran sessizce açılmaz).
2. "inception aç" → liste geliyor mu; "sesi kıs" → TV'de ses düşüyor mu.
3. Halka: ⏩/🔊 düğmesiyle kip değişiyor mu, her tam adımda tek komut mu gidiyor.

Aynı şekilde **TV v0.2.9 hâlâ televizyonda denenmedi** — önceki devirdeki SIRADAKİ
İŞ #1 duruyor (`docs/HANDOFF.md`), bu oturumda ona dokunulmadı.

## Kararlar ve gerekçe

- **Sesli tanıma saatin kendi motoruyla** (`RecognizerIntent`, tr-TR), Gemini'ye ses
  yüklenmiyor. `/voice` ucu düz metni de kabul ediyor; anahtar sunucuda kalır.
- **`/voice` komut niyetini KENDİ kuyruğa yazıyor** (`sent:true`). İstemci bir de
  `/remote/command` atarsa TV'ye çift gider. Hafızaya yazıldı:
  `memory/voice-endpoint-self-enqueues.md`.
- **Gemini yoksa düz aramaya düşülür** — 503'te sesli komut sussa bile arama çalışır.
- **Yükleme iki aşamalı**: Devam Et sunucunun yerel kaydından anında gelir ve hemen
  çizilir; Yeni Çıkanlar arkadan eklenir. Tek beklemede ikisini istemek, soğuk
  agregasyonda (~40 sn) saati yarım dakika boş tutuyordu.
- **Cache ısıtıcı `lifespan`'de** (`stream/Core/Modules/__init__.py`): 480 sn'de bir
  `aggregate_new` movie+serie tazelenir. Aralık TTL'in (600 sn) ALTINDA olmalı,
  yoksa arada soğuk pencere kalır. `params` istemcininkiyle birebir aynı olmalı —
  cache anahtarı params'tan üretiliyor.
- **APK adı build'de veriliyor** — kozmetik değil: `app_update.py` hedefi
  `netmovies-wear-` önekinden, sürümü addaki `vX.Y.Z`den okur; `wear-debug.apk`
  /data/apk'ya kopyalansa bile sunulmuyordu.

## Tekrarlama

- **`gh release create` `--target main` olmadan** — release `/releases` listesine
  düşmez, OTA görmez (hafıza: `ota-release-target-flag`).
- **`docker exec -w /usr/src/Stream`** Git Bash'te yol çeviriyor → "Cwd must be an
  absolute path". Başına `MSYS_NO_PATHCONV=1` koy.
- **Bash heredoc ile Kotlin dosyası yazmaya çalışma** — bu oturumda tırnak yüzünden
  "unexpected EOF" verdi; Write tool'u kullan.
- **Wear'a Gemini sesi yüklemek** — gereksiz: saatte tanıyıcı var, `/voice` metin kabul ediyor.

## TEK SIRADAKİ ADIM

Saate `evaitecOTA` üzerinden NetMovies Mini 0.1.3'ü kur, 🎙'a bas ve
"inception aç" de. Çalışmazsa önce `adb logcat | grep -i recognizer` ile
mikrofonun açılıp açılmadığına bak — manifest `<queries>` bloğu oradan doğrulanır.
