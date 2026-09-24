# Handoff: TV 0.9.24 — cihaz geri bildirimi bekleniyor
> 2026-09-24 18:40 · `fix/general-stability` @ `f7ab9a6` (push'lu) · kirli: yalnız `.claude/handoffs/*`, `.claude/worktrees/`, `atv-kopru.log`
> Katalog `evaglass-releases` @ `fdfd6d3` (push'lu)

## Goal
Reklamsız TV uygulamasını (client-tv, Mi Box) 3 metreden rahat kullanılır ve kesintisiz izlenir hâle getirmek. Kurallar/persona: `.claude/agents/tv-ux.md`. Oynatıcı 2 planı: `docs/PLAYER2-PLAN.md`.

## State
- **APK 0.9.24 yayında (üç yer):** `data/apk` → `app_update?target=tv` = `v0.9.24-poc`; GitHub `v0.9.24-poc`; evaglass `netmovies-tv-v0.9.24` + apps.json vc 924, sha `50fb2e72…`. İki indirme 200.
- Yığın: yerel ve `w.evaitec.com` health 200 (18:40). Birim test 55/55; gateway 168 OK (proxy fix sırasında).
- Bu gün yapılanlar (ayrıntı commit mesajlarında, `git log 2180d16..f7ab9a6`):
  - 0.9.22 okunurluk (7 poster, 14–18sp, overscan 48/27), Tekrar dene ekranları, sarma ekranında GERİ fix.
  - `eaabab7` proxy: `.js` uzantılı segmentler ön-yüklenmiyordu → DiziMom'da sürekli tamponlama. Ölçümle doğrulandı.
  - Oynatıcı 2 (`ui/player2/`) Ayarlar anahtarıyla, **varsayılan KAPALI** (eski oynatıcı yedek).
  - 0.9.24: ilk açılışta çift çözümleme (tek kullanımlık adres yanıyordu) → `detayHazir` kapısı; açılmayan kaynak + gelen yeni kaynağa geçiş; KAYNAK_YOK'ta kapanma yerine `KaynakYokEkrani`; tam ekran `PlayerLoadingScreen`. İki oynatıcıda da.
- **unverified:** hiçbiri Mi Box'ta denenmedi. `KaynakYokEkrani` emülatörde tetiklenemedi. Oynatıcı 2'de dizi bölüm geçişi / geri sayım / scrub önizleme / canlı denenmedi.

## Next
1. Dean'in Mi Box geri bildirimini bekle (0.9.24: ilk açılışta "bulunamadı" geçti mi, yükleme ekranı, DiziMom akıcı mı). Sorun bildirirse önce: `curl -s localhost:3310/api/v1/client_log` (TV günlüğü — emülatör çalıştırmadan oku, tek slot, emülatör ezer).
2. Dean yeni oynatıcıyı onaylarsa: `data/OynaticiSecimi.kt` varsayılanı `true`, bir sürüm sonra `ui/PlayerScreen.kt` eski oynatıcıyı sil (PLAYER2-PLAN § Entegrasyon).
3. Temizlik: birleşmiş ajan worktree'leri — `git worktree remove .claude/worktrees/agent-*` + `git branch -D worktree-agent-*` (commit'leri cherry-pick edildi: 15e1f5d, b0f017d, adbfdd1).

## Don't repeat
- TV davranışını `eva_test` (telefon AVD) ile test etme: telefon kipinde poster gerçek TV'ye `remote/play` yollar. `tv_test` AVD kullan (Android TV API 34): `emulator -avd tv_test -no-snapshot -no-audio -gpu swiftshader_indirect` (run_in_background). Ekran görüntüsünde video karışık kare gösterebilir — emülatör GPU kusuru.
- Dean izlerken (remote/state `playing:true`) onun içeriğini emülatörde açma — ilerlemesini ezer.
- Stream rebuild sonrası tünel: `docker compose stop cloudflared; docker compose --profile tunnel up -d` (hemen 530, ~15 sn sonra 200).
- Emülatörde açılış 7–13 sn donuyor, yayındaki eski sürümde de aynı — değişiklikten bağımsız, Mi Box'ta ölçülmedi; kök neden araştırılmadı.

## Read first
1. `.claude/agents/tv-ux.md` — ölçüler + korunan Dean kararları
2. `docs/PLAYER2-PLAN.md` — oynatıcı 2 sözleşmesi
3. `client-tv/.../ui/PlayerScreen.kt` `detayHazir` / `siradakiBekleniyor` — son düzeltmenin yeri

## Verify
git rev-parse --short HEAD                                   # expect f7ab9a6
curl -s "localhost:3310/api/v1/app_update?target=tv"         # expect tag v0.9.24-poc
cd client-tv && ./gradlew testDebugUnitTest -q               # expect exit 0 (55 test)
bash scripts/smoke.sh                                        # expect kapı YEŞİL

## <yeniden başlangıç> promptu (yapıştır)
```
NetMovies TV (D:\projects\netmovies, dal fix/general-stability @ f7ab9a6). 0.9.24 üç yerde yayında:
okunurluk turu, DiziMom tamponlama proxy düzeltmesi, ilk açılışta "kaynak bulunamadı"/kapanma
düzeltmesi, tam ekran yükleme; Oynatıcı 2 Ayarlar'da deneme anahtarıyla (varsayılan kapalı).
Hiçbiri Mi Box'ta doğrulanmadı.
Önce HANDOFF.md'yi oku ve Verify bloğunu çalıştır.
Öncelik: (1) Dean'in cihaz geri bildirimi → kök neden (client_log'u emülatör çalıştırmadan oku);
(2) onaylarsa yeni oynatıcıyı varsayılan yap; (3) ajan worktree temizliği.
TV testi yalnız tv_test AVD'de. Dean istemeden yeni iş açma.
```
