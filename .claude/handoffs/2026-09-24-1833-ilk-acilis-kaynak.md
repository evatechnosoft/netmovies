# DEVİR — 24 Eylül 2026, 18:15 · 0.9.24: ilk açılış "kaynak bulunamadı" düzeltmesi

**APK 0.9.24** üç yerde (data/apk → v0.9.24-poc, GitHub v0.9.24-poc, evaglass netmovies-tv-v0.9.24 + apps.json vc 924 `fdfd6d3`), sha `50fb2e72…`, indirme 200.
- Kök neden (sunucu günlüğü): bölüm sayfası kartında zincir önce episode=0, detay gelince asıl bölümle ikinci kez koşuyordu; tek kullanımlık oynatma adresi ilkinde yanıyordu. Artık `detayHazir` kapısı. Emülatör (tv_test): MobLand tek fast+full, doğru bölüm (episode=10).
- Açılmayan kaynak + arama sürerken gelen yeni kaynağa geçiş (`siradakiBekleniyor`).
- KAYNAK_YOK'ta kapanma yok → `KaynakYokEkrani` (Tekrar dene). **Emülatörde tetiklenemedi, doğrulanmadı.**
- Yüklenirken tam ekran poster+başlık (`PlayerLoadingScreen`), panel açıkken de. Emülatör ekran görüntüsüyle doğrulandı.
- İkisi de hem eski oynatıcıda hem oynatıcı 2'de. Mi Box'ta denenmedi.
- client_log tek slot: emülatör testleri TV'nin günlüğünü ezer.

---
# DEVİR — 24 Eylül 2026, 15:25 · 0.9.23: oynatıcı 2 (deneme) + DiziMom takılma düzeltmesi

**Dal:** `fix/general-stability` (push'lu). **APK 0.9.23** üç yerde: data/apk (app_update → v0.9.23-poc),
GitHub v0.9.23-poc, evaglass-releases netmovies-tv-v0.9.23 + apps.json vc 923 (`006fbc7`), sha256 `3b1c29a5…`, iki indirme 200.

## Kanıtlı
- **Takılma kök nedeni (sunucu, `eaabab7`):** hdplayersystem segmentleri `.js`; proxy segment saymıyordu → ön-yükleme/cache yok.
  `#EXTINF` bağlamıyla tanıma. 25 ardışık segment: önce 0,6-12 sn, sonra 0,01-0,05 sn. 168 gateway testi OK. Stream yeniden kuruldu, tünel 200.
- **Oynatıcı 2** (`ui/player2/`, docs/PLAYER2-PLAN.md): 3 paralel ajan (core/keys/ui) + entegrasyon. 55 birim test OK.
  Android TV emülatöründe (yeni AVD `tv_test`, API 34 android-tv, 1920x1080/320) Super Troopers 3: başlangıç paneli → GERİ=OYNAT → devam 0:54 → sarma → GERİ×2 çıkış; ilerleme 191,9 sn kaydedildi, FATAL 0.
- Ayarlar'da "Yeni oynatıcı (deneme)" — varsayılan KAPALI (eski oynatıcı yedek, davranışı değişmedi; yalnız private→internal).

## Doğrulanmadı
- Mi Box'ta hiçbiri. Takılma düzeltmesi TV'de izlenerek değil proxy ölçümüyle doğrulandı.
- Oynatıcı 2: dizi/bölüm geçişi, geri sayım, scrub önizleme, canlı yayın emülatörde denenmedi.

## Oynatıcı 2 bilerek farklı (Dean'e söylendi)
Geri sayım 2.+ bölümde de çalışır (eski bayat-state hatası) · bölüm sayfasında GERİ önce sezonlara · kanal değişince hız 1x.

## Tekrarlama
- TV testi için `tv_test` AVD (telefon eva_test'te poster gerçek TV'ye komut yollar). Başlat: `emulator -avd tv_test -no-snapshot -no-audio -gpu swiftshader_indirect` (run_in_background). Ekran görüntüsünde video karışık kare gösterebilir — emülatör GPU kusuru.
- Yeni oynatıcıyı emülatörde açmak: shared_prefs/player.xml `yeni_oynatici=true` (run-as ile).
- Küçük kusur (eski): oynatıcıdan dönünce odak indeksle geri geliyor, liste yeniden sıralanınca başka postere düşüyor.

## TEK SONRAKİ EYLEM
Dean Mi Box'ta 0.9.23 kursun: (1) DiziMom bölümünü izlesin — takılma bitti mi; (2) Ayarlar → Yeni oynatıcı AÇIK ile bir dizi bölümü + sonraki bölüm. Onaylarsa yeni oynatıcıyı varsayılan yap.
