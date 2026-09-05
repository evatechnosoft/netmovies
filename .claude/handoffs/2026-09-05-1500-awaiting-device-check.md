# DEVİR — iş bitti, cihaz doğrulaması bekleniyor (v0.1.50-poc yayında)

**Tarih:** 2026-09-05 15:00 · **Oturum:** 149619ac (devam)
**Dal:** `fix/general-stability` @ `7d85ac8` · çalışma ağacı TEMİZ, push'lı
**Yığın:** doh/engine/stream/warp `running` (`docker compose ps`)

## Durum
Kod işi kapandı. 12:40 devrinden sonra tek değişiklik `7d85ac8` — `docs/HANDOFF.md`
DEVİR bloğunun 5 Eylül durumuna güncellenmesi. Yeni kod yazılmadı, yeni doğrulama
koşulmadı.

**Kanonik devir dosyası artık `docs/HANDOFF.md`** (proje CLAUDE.md onu okutuyor).
Ayrıntılı teknik gerekçe orada ve `2026-09-05-1240-stability-hardening.md`'de;
burada tekrarlanmıyor.

## Bu oturumda kanıtlanan (doğrulandı)
- smoke.sh kapı YEŞİL · client-tv 18/18 · CI run `33955264088` üç iş `success`
- resolve full zincir 11sn, `atlanan sağlıksız kaynak: HQPorner, RecTV`
- izleme DB 8 → 7 kayıt (mükerrer birleşti), "The Gorge" konum 3763 korundu
- `v0.1.50-poc` GitHub Release'te en üstte, APK 19.986.558 bayt
- yerel 200 · LAN `192.168.1.185:3310` 200

## Doğrulanmadı (iddia edilmedi)
- Film ortasında kaynak düşünce **aynı dakikadan** devam ediyor mu — sadece derleme
  ve birim testler yeşil, gerçek TV'de bakılmadı.
- Devam Et rafında artık **tek poster** görünüyor mu — sunucu verisi tekilleşti,
  cihaz ekranı görülmedi.

## Tekrar deneme
- Tünel mimarisi (cloudflared `network_mode: service:stream`) Dean'in kararıyla
  kapsam dışı — dokunma.
- Yeni sürüm: SADECE `client-tv/app/build.gradle.kts` içindeki `val appVersion`.

## SIRADAKİ TEK İŞ
Dean TV'de v0.1.50'yi kurup iki şeye baksın: kaynak düştüğünde konum korunuyor mu,
Devam Et'te tek poster mı. Şikâyet gelirse önce Ayarlar → 🩺 Kaynak raporu.
Kod tarafında sıradaki: içerik detay ekranı (TMDB özet/oyuncu) — `docs/HANDOFF.md` §3.2.
