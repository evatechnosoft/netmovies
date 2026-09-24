# Oynatıcı 2 — plan ve sözleşme

**Amaç:** `ui/PlayerScreen.kt` (2800+ satır, ana composable 1640 satır / 40+ durum) bölünmüş,
okunur bir oynatıcıya taşınır. **Eski oynatıcı YEDEK kalır, davranışı değişmez** (yalnız
yeniden kullanılan alt composable/sabitler `private` → `internal` yapıldı). MainActivity
Ayarlar'daki "Yeni oynatıcı (deneme)" anahtarıyla seçer; varsayılan ESKİ.

**Taban:** yeni kod eski kodun mantığını birebir taşır (yeniden icat yok). Alt UI
parçaları (ControlsOverlay, SettingsPanel, StartPanel, ScrubOverlay, NextEpisodeCard,
SkipIntroCard, CornerStatus, KeyHintChip, SeekScreen, BolumSecici) YENİDEN YAZILMAZ, çağrılır.

## Dosyalar ve sahipleri (paralel ajanlar yalnız kendi dosyasına yazar)
| Dosya | Sahip | Eski kod (PlayerScreen.kt satır) |
|---|---|---|
| `ui/player2/PlayerCore.kt` | core | A 165-202, C 358-409, E 421-560 (eylemler), J/K/L 668-705, M 707-882, N-P 885-933, Q-T 938-1144, V-Y 1184-1273, AB-AD 1309-1381 |
| `ui/player2/PlayerUiState.kt` + `PlayerKeys.kt` | keys | F 562-605, G 610-619, H 622-638, I 643-666, Z 1276-1288, AE 1383-1532 (tuş kısmı) |
| `ui/player2/PlayerScreen2.kt` (+ gerekirse `PreviewPlayer.kt`) | ui | U 1147-1181, AA 1297-1304, AE kök Box, AF 1534-1789 |

Kural: mevcut public imzalar değişmez; eksik bir şey lazımsa KENDİ dosyana ekle ya da
raporda "core'a gerekli: …" diye yaz. Başka ajanın dosyasına dokunma.

## Paylaşılan durum (kesilmez — core'da birlikte durur)
- Kaynak fallback makinesi: links, currentLinkIndex, carryOverMs, retryKey, status, searching, ready, autoRefresh (M↔S↔T).
- Bölüm geçiş kilidi: gecisBekleyen, akisBitti, akisGecersiz, geriSayim (M↔AD↔goToEpisode↔S).
- Devam/ilerleme: C/Q/W/X/M-dispose ↔ Library; C ve Q `showStartPanel`'e yazar (core'da).
- episodesBest (Y) aktifPlugin/aktifUrl değiştirir → çözümleme (S) yeniden tetiklenir.

## Korunacak davranışlar (eski kodda "Dean:" kararları)
Başlangıç panelinde GERİ = OYNAT (kaynak yoksa çıkış) · birikimli seek 350 ms tek seekTo,
OK hedefi hemen uygular · geçiş kilidi (tekrar basış çok bölüm atlamaz) · canlıda
YUKARI/AŞAĞI kanal · <90 sn akış = kaldırılmış klip, otomatik geçiş yok · yarım kayıt
varsa autoplay'de de panel sorar · bölüm indeksle değil kendi URL'iyle çözülür ·
oynamış içerik KAYNAK_YOK ile kapanmaz · PlayerView odak almaz, zemin siyah · GERİ
onPreviewKeyEvent'te (odak grubu yutar) · yetim ACTION_UP yutulur (KeyPairGate) · modal
açıkken tuş controller'a gitmez · kaynak geçişinde konum korunur (kısa klipte değil) ·
hazırlama anahtarı currentLinkUrl · kalite tavanı her geçişte · dublajda altyazı kapalı
başlar · canlıda ilerleme kaydı yok · dispose'da konum release'den ÖNCE okunur · resume
başka bölüme ait kayıtta uygulanmaz · jenerik işareti varsa 70 sn teklif kartı yok ·
geri sayımı GERİ ile iptal o bölüm için kalıcı · tunneling hatasında pref'e kalıcı kapalı.

## Entegrasyon (PM)
Üç dal birleşir → `./gradlew testDebugUnitTest assembleDebug` → emülatörde eski/yeni
aynı içerikle karşılaştırma (film + dizi: açılış, kaynak, seek, GERİ, sonraki bölüm) →
Ayarlar anahtarı → yayın. Yeni oynatıcı Mi Box'ta onaylanınca varsayılan olur; eskisi
bir sürüm daha yedek kalır, sonra silinir.
