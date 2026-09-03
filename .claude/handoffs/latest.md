# DEVİR — agent katmanı araçları + TV sanal fare

**Tarih:** 2026-09-03 23:20 · **Oturum:** 3e529d30
**Dizin:** `D:\projects\netmovies` (dal `fix/general-stability`) + `~/.claude` (dal `main`)

## Bu oturumda ne oldu

İki ayrı iş yürüdü. Çoğu değişiklik **netmovies'te değil, `~/.claude` agent
katmanında**.

### A) Agent katmanı (`~/.claude`, hepsi commit'li)
| Commit | Ne |
|---|---|
| `42b855f` | deadman kurulumu (Windows `.bat` wrapper) + `lazy-ladder` skill'i |
| `e613edc` | kanıt kapısına **TEKRAR YOK** maddesi |
| `122b404` | memory-decisions arşivine bu oturumun dersleri |
| `368d57e` | 3 ölü-yük skill `_lazy/`'ye (ölçüldü: 2316→2101 tok/turn, skor 70→74) |
| `2abe9e9` | statusline'a deadman çipi + GNU/BSD `stat` sırası düzeltmesi |
| `3f1fb49` | stop-hook: ⚠ ikonu, zayıf desen gürültüsü kesildi |
| `ffa6087` | kapıya **HEPSİNİ CEVAPLA** maddesi |
| `7086ca6` | lazy-ladder'a `ponytail` tetiği + "Tembellik kapsamı daraltmaz" bölümü |
| `86ee982` | Stop uyarısı ekrandan footer'a taşındı (`state/pending.json` → `⚠ memory`) |

### B) NetMovies TV (dal `fix/general-stability`, push'landı)
| Commit | Ne |
|---|---|
| `0da5188` | sanal fare: slider ayarlar, ivmeli gezinme, çift-basış kaydırma |
| `9f45c91` | Ayarlar açıkken imleç katmanı bastırılıyor |
| `85042e1` | v0.1.37-poc sürüm yükseltmesi |
| `d811c2e` | Özel Koleksiyon: yanıltıcı kilit ikonu, filtre tek noktaya |

**Release:** https://github.com/evatechnosoft/netmovies/releases/tag/v0.1.37-poc
(APK 19.888.222 bayt, prerelease, listede en üstte → OTA şeridi düşer.)

## Kanıtlı vs doğrulanmadı

**Kanıtlı:** deadman `doctor` 9/9 + `drill` yeşil, canlı ateşleme gördü ·
caveman binary 6/6 checksum · telemetri kapalı (`source: runtime`) · sıkıştırma
ölçümü: `docker logs` −63..69%, katalog JSON −45%, kaynak/markdown %0 ·
`caveman learn` 1290 oturum · `./gradlew assembleDebug testDebugUnitTest`
BUILD SUCCESSFUL (her TV commit'inde) · statusline çipi canlı oturumla test edildi.

**Doğrulanmadı:** ⚠ APK **cihaza kurulmadı** — fare hızı/ivme katsayıları
(`ACCEL_PER_REPEAT=0.14`, `DOUBLE_MS=320`, `SCROLL_IDLE_MS=2500`) tahmin, his
doğrulaması yok. Kaydırmanın (`MotionEvent.ACTION_SCROLL`) Compose listelerinde
gerçekten çalıştığı da denenmedi. caveman **proxy'si uçtan uca koşulmadı**
(`caveman claude` bu oturumun içinden çalıştırılamaz).

## Açık işler
1. **Cihaz doğrulaması (Dean'e bağlı).** v0.1.37 kurulunca: slider SOL/SAĞ,
   basılı-tut ivmesi, çift-basış kaydırma, Ayarlar açıkken imlecin kaybolması.
   Katsayılar hisse göre ayarlanacak.
2. **caveman proxy ölçümü.** Ayrı terminalde `caveman claude` → iş yap →
   `caveman tools stats`. Gerçek tasarruf sayısı orada. Sonrası: ZimaOS/Nexus'a
   self-host (BSL Additional Use Grant buna açıkça izin veriyor).
3. **dumbzone (−25 puan).** Medyan context 147.760 token, turn'lerin %66'sı
   pencerenin %50 üstünde. `cache_churn` ile aynı kök: oturum uzuyor →
   compaction → ~91k prefix yeniden yazılıyor. Kaldıraç: oturumu deadman
   devriyle kesmek + tool çıktısını daraltmak (exec %23.5, `Read(*.md)` %18.8,
   `Bash(cd)` %7.3 / toplam 28,8M token).
4. **Cevapsız soru:** "switch koysun benim ve senin modu" — üç okuması var
   (TV'de kumanda↔fare geçişi / footer'da mod göstergesi / Claude'da
   sor-önce↔kendin-karar-ver anahtarı). Dean'e sorulmayı bekliyor.
5. **`D:\projects\layers` altında 4 açık oturum** boşta bekliyor (deadman
   status'te görüldü). Unutulmuş olabilir, sorulmadı.

## SIRADAKİ İŞ — bu oturum bunun için devrediliyor
**AI tooling best-practice derin araştırması.** Dean istedi: modern trendlere
bak, bizim kurulumumuza uyanları çıkar. `deep-research` skill'i var, onu kullan.

Araştırma kapsamı (bu oturumun bulguları soruları şekillendirdi):
- **Context mühendisliği:** 148k medyan context ile çalışan kurulumlar ne
  yapıyor? Compaction stratejileri, alt-ajana devretme eşiği, "dumbzone"
  kaçınma pratikleri.
- **Skill/rule yükleme mimarisi:** her-turn enjeksiyon vs on-demand vs
  progressive disclosure. Bizim `_ondemand-index.md` deseni sektörde nerede
  duruyor? 2101 tok/turn config tax normal mi, yüksek mi?
- **Tool çıktısı yönetimi:** 28,8M token'ın %55'i 632 imzada toplanıyor.
  Sıkıştırma (caveman), CCR/recovery, dar okuma, `Bash(cd)` gibi gürültü
  imzalarının kesilmesi — sektörde hangisi işe yarıyor?
- **Oturum devri:** deadman gibi otomatik handoff yaygın mı, alternatifler?
- **Çoklu-ajan:** subagent kullanımı 1290 oturumda sadece 6 spawn — az mı,
  doğru mu? Hangi işler delege edilmeli?
- **Kanıt/doğrulama disiplini:** "iddia=kanıt" gibi kuralları hook'la zorlamak
  yaygın bir pratik mi, yoksa bize özgü mü?

Çıktı: bulguları `~/.ai/guides/` altına guide olarak yaz, bizim kuruluma
uygulanabilir olanları `~/.claude/rules/_ondemand-index.md`'ye tetikleyiciyle bağla.

## Bir daha düşme
- **Hook değişikliği ANINDA geçerli.** "Restart gerekir" diye varsayma, test et.
- **Kural yazmak yetmez, enjekte edilen kapıya koy.** `global-config.md`'de
  "süreç anlatma / tekrar etme" vardı, zorlayıcı yoktu, drift oldu.
- **Yıldız ≠ kalite.** ponytail 122k★/305 watcher. Koda bak.
- Git Bash'te `stat -f` **başarılı olur** (dosya sistemi bilgisi döner) →
  `-c %Y` fallback'i hiç çalışmaz. GNU önce.
- `git commit -m` içinde parantez → shell parse hatası. Heredoc + `-F -` kullan.
- Python heredoc içinde backslash → mangle. Ayrı `.py` dosyasına yaz.
- `~/.claude/.gitignore` whitelist tipinde (`/*` + `!dizin/`); yeni üçüncü-taraf
  betik eklerken whitelist'e almayı unutma.
- **PowerShell `Set-Content -Encoding utf8` PS 5.1'de BOM yazar ve jq BOM'lu
  dosyayı OKUYAMAZ.** Hook'ların ürettiği JSON'da `[System.IO.File]::WriteAllText`
  + `New-Object System.Text.UTF8Encoding($false)` kullan.
- **`Get-Date -UFormat %s` kültür-bağımlı float döndürür** (tr-TR'de virgüllü).
  Unix zaman damgası için `[int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()`.
