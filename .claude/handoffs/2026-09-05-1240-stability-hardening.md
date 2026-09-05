# DEVİR — kalıcılaştırma turu: konum taşıma, tek poster, kapı, autostart (v0.1.50-poc yayında)

**Tarih:** 2026-09-05 12:40 · **Oturum:** 149619ac
**Dal:** `fix/general-stability` @ `8d1f512` (push'lı, çalışma ağacı temiz)
**Sürüm:** `v0.1.50-poc` — GitHub Release'te en üstte (APK 19.986.558 bayt, prerelease)

## Hedef
Dean: *"pm olarak kıdemli konuştur ve artık bozulmayacak şekilde yapalım"* → tekrar eden
kırılmaların KÖKÜNÜ kapat. Dean dört ekseni de onayladı ("hepsini değerlendir"), sonra
tüneli kapsam dışı bıraktı ("tünel boşver, local çalışsın"), sonra çift poster bug'ını
bildirip release istedi.

## Yapıldı — commit ve kanıt

| Commit | Ne | Kanıt |
|---|---|---|
| `f52be9c` | Oynatıcı konum taşıma · resolve sağlık süzmesi · sürüm tek kaynak · CI kapısı | aşağıda |
| `f6265a1` | PC açılışında yığın otomatik kalksın | script exit=0, log "engine Healthy" |
| `8d1f512` | Devam Et çift poster — anahtar tür-agnostik + migration | DB 8→7 kayıt |

### 1. Film başa dönmesi (KÖK NEDEN)
`PlayerScreen.kt`: kaynak düşünce `currentLinkIndex++` → yeni `prepare()` konumu 0'lar,
devam-etme ise `resumeApplied` ile TEK seferlik → ikinci kaynakta seek HİÇ uygulanmıyordu.
Fix: geçişte `carryOverMs = exo.currentPosition`, prepare sonrası geri verilir.
Kanıt: `compileDebugKotlin BUILD SUCCESSFUL`, 18/18 test. **Cihazda doğrulanmadı.**

### 2. Ölü kaynak (resolve_sources.py)
Sağlık süzmesi yalnız `aggregate_new`'deydi. Artık `run_plugin_health` sonucu burada da
uygulanıyor + sağlayıcı başına `ALTERNATIVE_TIMEOUT = 25` bütçe (`_with_budget`).
Sağlık bilinmiyorsa hepsi denenir (kart kaybetme).
Kanıt: teşhis satırı `atlanan sağlıksız kaynak: HQPorner, RecTV`, full zincir **11sn**.
Sağlık durumu: 6/8 sağlıklı — HQPorner + RecTV `unreachable`.

### 3. Sürüm tek kaynak (build.gradle.kts)
`val appVersion` eklendi; versionCode ondan türer (`major*10000+minor*100+patch`).
Tespit: v0.1.49 çıkarken versionCode 48'de kalmıştı. Kanıt: aapt → `versionCode='150'
versionName='0.1.50'`. **Yeni sürüm = SADECE `appVersion`'ı değiştir.**

### 4. CI kapısı (.github/workflows/gate.yml) — YENİ
Repoda hiç workflow yoktu. Üç iş: client-tv test+derleme · stream sözleşme testleri
(imaj içinde) · engine imaj derlemesi. Kanıt: run `33955264088` üçü de `success`.

### 5. PC açılışı (scripts/netmovies-autostart.cmd)
Kök neden: Docker Desktop `AutoStart: False` (settings-store.json) → motor hiç
başlamıyor, `restart: unless-stopped` de işlemiyordu. Script Docker'ı başlatır, motoru
bekler, `docker compose up -d` çağırır (idempotent). Startup klasörüne `.lnk` kondu.

### 6. Devam Et'te çift poster (watch_store.py + watch.py)
Kök neden Dean'in tahmininden farklı: iki sağlayıcı DEĞİL — `content_key` `media_type`
içeriyordu; bir istemci türü gönderip diğeri göndermeyince aynı film iki kayıt oluyordu
(`gorge|movie` 3763sn ve `gorge` 2884sn). Fix: tür anahtardan çıktı, `canonical_key()`
hazır anahtarın son ekini kırpar, `_migrate_type_suffix()` açılışta eski kayıtları
birleştirir (EN SON güncellenen konum kazanır).
Kanıt: canlı DB **8 → 7** kayıt, "The Gorge" tek satır, konum 3763 korundu.
Test: `stream/tests/test_watch_key.py` 4 test.

## Doğrulama komutları (bu oturumda yeşil)
```bash
bash scripts/smoke.sh          # kapı YEŞİL: movie 38 · serie 78 · live 173 · zincir 1 kaynak
cd client-tv && ./gradlew testDebugUnitTest assembleDebug   # 18/18, BUILD SUCCESSFUL
gh run list --limit 1          # gate: success
```
Yerel 200 · LAN `192.168.1.185:3310` 200. Tünel bu oturumda ÇALIŞTIRILMADI (profile kapalı).

## Yapma / tekrar deneme
- **Tünel mimarisine dokunma.** `cloudflared` hâlâ `network_mode: service:stream` (stream
  recreate = tünel ölür). Dean bilerek kapsam dışı bıraktı: "tünel boşver, local çalışsın".
- **Sürüm alanlarını elle üç yerde güncelleme** — artık `appVersion` tek kaynak.
- **`docker compose up -d --build` arka planda bırakma:** yarım kalan build "container name
  already in use" çakışması bıraktı (`docker rm -f <id>` ile temizlendi).
- **`/api/v1/plugin_health` stream üzerinden 302 döner** (gateway proxy'lemiyor) — engine
  içinden sor: `docker exec netmovies-engine python -c "...localhost:3310/api/v1/plugin_health"`.
- Engine kaynak yolu container'da `/usr/src/KekikStreamAPI/`, `/usr/src/KekikStream/` DEĞİL.

## SIRADAKİ TEK İŞ
**Cihaz doğrulaması (Dean'e bağlı).** v0.1.50 TV'ye düşünce: film ortasında kaynak
düşerse AYNI DAKİKADAN devam ediyor mu · Devam Et rafında tek poster mı. Şikâyet gelirse
önce Ayarlar → 🩺 Kaynak raporu.

Sonraki kod işleri (docs/HANDOFF.md §3'ten devam): içerik detay ekranı (TMDB özet/oyuncu),
resmi kaynaklar bölümü, Faz 3/5 oynatıcı dayanıklılığı.
