# NetMovies TV — Compose for TV client (POC)

Kotlin + Jetpack Compose for TV + Media3/ExoPlayer. Engine/stream'i **client-agnostik**
API olarak tüketir (scrape/proxy DEĞİŞMEZ). POC hedefi: **Yeni Filmler listesi → seç → HLS oynat.**

> ✅ **Build DOĞRULANDI** (2026-08-28, bu makine): `./gradlew.bat assembleDebug` → BUILD SUCCESSFUL,
> `app-debug.apk` (~12.7MB) üretildi. Versiyon stack'i (AGP8.5.2/Kotlin2.0.20/Gradle8.9/Java21) tutuyor.
> ⚠️ **Oynatma (HLS)** uçtan uca Mi Box'ta doğrulanacak — derleme ≠ oynatma. `local.properties`'te SDK
> yolunda **forward-slash** kullan (backslash Java properties'te kaçış → "path syntax incorrect").

## Gereksinimler (bu makinede tespit edildi)
- Java 21 ✓ (`java -version`)
- Android SDK ✓ `C:\Users\Deacjx\AppData\Local\Android\Sdk` (adb mevcut)
- Gradle: **wrapper** kullanılır (kurulu gradle gerekmez).

## Kurulum
```bash
cd client-tv
cp local.properties.example local.properties     # SDK yolu (zaten doğru)
# Ev sunucunun (stream) LAN IP'sini gir:
#   gradle.properties → NETMOVIES_BASE_URL=http://192.168.0.28:3310
```

## Derle (komut satırı)
```bash
# Git Bash / PowerShell (Windows):
./gradlew.bat assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
İlk çalıştırma gradle 8.9 dağıtımını ve eksik SDK paketlerini (platform 34, build-tools) indirir.
Eksik SDK lisansı hatası olursa: `sdkmanager --licenses` kabul et.

## Mi Box'a kur
```bash
adb connect <mibox-ip>:5555        # veya USB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Uygulama TV launcher'da (Leanback) ve telefonda görünür.

## Akış (POC)
1. `HomeScreen` → `GET /api/v1/aggregate_new?type=movie` → 6'lı poster grid (D-pad ile gezilir).
2. Poster seç → `PlayerScreen` → `GET /api/v1/load_links?plugin=…&encoded_url=…`
   → dönen `url` + `referer`/`user_agent` ile Media3 HLS oynatır (proxy'siz, header ExoPlayer'da).

## Bilinen/doğrulanacaklar
- `encoded_url` çift-kodlama: `NetMoviesApi` `encoded=true` kullanıyor (web akışıyla aynı varsayım).
  Oynatma "kaynak yok"/404 verirse `encoded=false` dene veya bir tur `URLDecoder` uygula.
- Bazı kaynaklar segment'lerde de header ister; sorun olursa stream'in `/proxy/video`'suna yönlendir
  (referer/UA query ile) — web player böyle yapıyor.
- Dizi (serie) akışı POC dışı: `type=movie`. Dizi için `load_item` → bölüm seçimi eklenecek.

## Mimari notu
Engine residential (ev) egress'te kalır — kaynaklar datacenter IP'sini engeller. TV client
LAN'da stream:3310'a bağlanır; uzaktan Cloudflare Tunnel (`w.evaitec.com`) ile de çalışır
(BASE_URL'i tünel adresine çevir).
