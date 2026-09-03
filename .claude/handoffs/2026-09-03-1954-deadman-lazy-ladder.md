# DEVİR — deadman kurulumu + lazy-ladder skill'i

**Tarih:** 2026-09-03 19:54 · **Oturum:** 3e529d30 · **Dizin:** `D:\projects\netmovies`
**Asıl değişen yer:** `~/.claude` (global agent config) — netmovies koduna DOKUNULMADI.

## Hedef
Dean dört repo adı verdi (ponytail / yahni / caveman / deadman), "uzman modunda çözümle"
dedi. Çözümleme sonrası kararı: **deadman'i kur**, ponytail+YAGNI içeriğini kendi
on-demand yapımıza al, caveman'in **skill iskeletini** al (içeriğini değil), caveman
proxy'sini kurma — kendi ortamımıza (ZimaOS/Nexus) kurulursa aktif ederiz.

## Durum — KANITLI vs İNANILAN

### ✅ Kanıtlı (tool çıktısı görüldü)
| İş | Kanıt |
|---|---|
| deadman kuruldu, `~/.claude/deadman/bin/deadman` | `install` çıktısı: 2 Stop + 1 SessionStart wired, backup alındı |
| `doctor` 9/9 yeşil | jq 1.8.1 · claude 2.1.258 (asyncRewake ≥2.1.220) · timeout 3600s covers AFTER=3300s |
| `drill` yeşil | sleeper exit 2 · warn T-3s · fired event · wake payload → `.claude/handoffs/` |
| `.bat` wrapper stdin+exit kodu geçiriyor | cmd üzerinden `announce` → `{"systemMessage":"deadman armed · …"}` EXIT=0 |
| **deadman canlı ateşledi** | Bu handoff'un kendisi asyncRewake uyandırmasıyla yazılıyor |
| lazy-ladder skill'i geçerli | `validate-skill.py` → PASS |
| Commit atıldı | `42b855f` (~/.claude, main), 7 dosya, +1201 satır |

### ❌ Yanlış çıkan öngörü (düzeltme)
Rapor ederken **"hooks oturum başında yüklenir, bu oturumda aktif değil"** dedim.
**Yanlış** — deadman aynı oturumda ateşledi. CC hook config'ini canlı okuyor.
Bir dahaki sefere hook kurulumundan sonra "restart gerekir" diye varsayma; test et.

### ⚠️ Doğrulanmadı
- Gerçek 50dk uyarı adımı (drill 6sn'lik sıkıştırılmış pencereyle koştu; canlı `warn`
  adımı `notifier: none` olduğu için sessiz — telefon bildirimi kurulmadı).
- `sessionstart` resume-offer'ın gerçek bir yeni oturumda handoff'u geri sunması.

## Kararlar ve gerekçeleri
1. **Plugin değil, plain-script kurulum.** `/plugin` yolu marketplace fetch + restart
   gerektiriyordu; script yolu `install` ile deterministik ve `doctor`/`drill` ile
   doğrulanabilir. Kurulum yalnız YENİ hook grubu ekliyor, mevcut `stop-hook.bat` ve
   nexus SessionStart zincirine dokunmuyor (diff ile doğrulandı).
2. **`.bat` wrapper şart.** Hook komutu `$HOME/.claude/deadman/bin/deadman` idi —
   Windows'ta ne `$HOME` genişler ne bash betiği doğrudan çalışır. Dean'in bilinen
   pattern'i (`stop-hook.bat`) izlendi. `exit /b %errorlevel%` kritik: exit 2 =
   asyncRewake sinyali, yutulursa mekanizma sessizce ölür.
3. **`.gitignore`'a `!deadman/`.** `settings.json` takipte, betik takipsiz olsaydı
   temiz klonda hook kırılırdı. `config.env` + `state/` hariç tutuldu.
4. **caveman içeriği ALINMADI, iskeleti alındı.** Çıktı sıkıştırma "Türkçe zorunlu" +
   "iddia=kanıt tek nefeste" kurallarıyla çelişiyor. Alınan: Persistence / Rules /
   Intensity(lite-full-ultra) / Auto-Clarity / Boundaries düzeni.
5. **lazy-ladder `_lazy/` altında, plugin değil.** Start-token'a binmiyor;
   `rules/_ondemand-index.md`'den tetikle açılıyor. ponytail plugin'i kurulmadı —
   Dean zaten her turn ~6k token kural taşıyor, marjinal fayda negatif.

## Bir daha yapma
- **"yıldız = kalite" sanma.** ponytail 122k★ / 305 watcher (oran 402), caveman 102k★ /
  241 (426) — meşru emsal (superpowers 263, claude-code 164) katı. Şişkinlik var ama
  sahtelik doğrulanmadı; koda bakıldı, karar oradan verildi.
- **"yahni" diye repo yok** — Dean muhtemelen **YAGNI**'yi kastetti (ponytail'in kendi
  benchmark'ındaki `yagni-oneliner` kolu). Aramada boşuna zaman harcama.
- `rm -rf`'li tek satır komut `dangerous-command-blocker.py` tarafından bloklanıyor;
  scratchpad'de yeni dizin adı kullan.

## SIRADAKİ TEK İŞ
Dean'e sorulmuş ama **cevaplanmamış** iki soru var — önce onları al:
1. **[Kayıt Önerisi]** "caveman proxy → sadece kendi ortamımızda/Nexus" kararı +
   deadman Windows `.bat` wrapper gerekçesi → memory'ye yazılsın mı?
2. Telefon bildirimi istiyor mu (`BARK_KEY`/`NTFY_TOPIC` → `~/.claude/deadman/config.env`)?
   Aynı ekipten `barkme-mcp-server` zaten var.

Sonrası (Dean onaylarsa): caveman proxy'nin ZimaOS'ta self-host edilebilirliğini
araştır — BSL-1.1 runtime lisansı kendi ortamda çalıştırmaya izin veriyor mu, ilk ona bak.

## Değişen dosyalar (~/.claude, commit 42b855f)
```
.gitignore                        +3    (!deadman/ whitelist)
deadman/bin/deadman               +861  (upstream, MIT değil→LICENSE kontrol et)
scripts/deadman-hook.bat          +5    (Windows wrapper)
settings.json                     +26   (2 Stop + 1 SessionStart hook grubu)
skills/_lazy/lazy-ladder/SKILL.md +117  (YAGNI merdiveni)
skills/handoff/SKILL.md           +188  (deadman'in kurduğu skill)
rules/_ondemand-index.md          +2    (lazy-ladder tetikleyicisi)
```
Rollback: `git revert 42b855f` + `bash ~/.claude/deadman/bin/deadman uninstall`.
Yedek: `~/.claude/settings.json.bak-predeadman-20260903185356`.
