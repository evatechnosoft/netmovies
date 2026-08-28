# NetMovies TV — OTA & Release Rehberi

Bu doküman: TV/telefon uygulamasını **GitHub Release** ile yayınlama ve **OTA** (Over-The-Air,
kendini güncelleme) mekanizması. Kaynak: `client-tv/` (Kotlin + Compose for TV).

---

## 1. OTA nasıl çalışır?
1. Her APK, derlendiği release tag'ini gömülü taşır: `BuildConfig.RELEASE_TAG`
   (bkz. `app/build.gradle.kts` → `buildConfigField("String","RELEASE_TAG", ...)`).
2. Uygulama **açılışta** GitHub API'yi çağırır:
   `GET https://api.github.com/repos/evatechnosoft/netmovies/releases`
   (bkz. `data/GithubApi.kt`). En yeni release'in `tag_name`'i alınır.
   > `/releases/latest` **prerelease'i atlar** → o yüzden `/releases` listesi kullanılır.
3. `tag_name != RELEASE_TAG` ise ana ekranın üstünde şerit çıkar:
   **"Güncelleme mevcut: <tag> — İndir & Kur"** (bkz. `ui/UpdateBanner.kt`, `UpdateViewModel.kt`).
4. Tıklayınca APK indirilir (OkHttp → `getExternalFilesDir/update.apk`) ve kurulum
   intent'i açılır (bkz. `update/Updater.kt`): sistem "bu uygulamayı kur?" diye sorar.

**Gerektirenler (manifest'te hazır):**
- `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`
- `FileProvider` (authorities `${applicationId}.fileprovider`) + `res/xml/file_paths.xml`
- Repo **public** olmalı → release APK'sı girişsiz indirilir (private'ta link auth ister).

İlk kurulumda cihazda **"bilinmeyen kaynaklara izin"** (bu uygulama için) bir kez açılır.

---

## 2. Release nasıl çıkarılır? (yeni sürüm reçetesi)
```bash
# 1) Yeni tag'i koda göm — app/build.gradle.kts:
#    buildConfigField("String", "RELEASE_TAG", "\"v0.1.1-poc\"")

# 2) Derle
cd client-tv
./gradlew.bat assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /tmp/netmovies-tv-v0.1.1-poc.apk

# 3) GitHub release oluştur (eski sürümdeki app bunu "güncelleme" olarak görür)
gh release create v0.1.1-poc /tmp/netmovies-tv-v0.1.1-poc.apk \
  -R evatechnosoft/netmovies \
  --target claude/stream-app-architecture-86q0sg \
  --prerelease --title "NetMovies TV v0.1.1" --notes "değişiklikler…"
```

**Mevcut release'in APK'sını değiştirmek** (tag aynı kalsın):
```bash
gh release upload v0.1.0-poc /tmp/yeni.apk -R evatechnosoft/netmovies --clobber
```

### ⚠️ Release tuzakları (yaşandı)
- `gh release create --target` **KISA SHA kabul etmez** (`target_commitish is invalid`) → **dal adı** ver.
- Commit **push'lanmış** olmalı; remote'ta olmayan commit'e release target olamaz.
- Release'i **prerelease** yaparsan `/releases/latest` onu atlar; OTA `/releases` listesini kullandığı için sorun değil.

---

## 3. Sürüm/BASE_URL notları
- **BASE_URL derleme zamanı gömülür.** Boşsa varsayılan `https://w.evaitec.com` kullanılır.
  Yalnız LAN'a sabitlemek için `gradle.properties → NETMOVIES_BASE_URL=http://<ev-ip>:3310` girilir.
- Bu yüzden "uzak sürüm" ayrı bir build/release olur (farklı BASE_URL). OTA yine çalışır.

---

## 4. Faydalı linkler
- **Release'ler:** https://github.com/evatechnosoft/netmovies/releases
- **Son APK (doğrudan):** https://github.com/evatechnosoft/netmovies/releases/download/v0.1.0-poc/netmovies-tv-v0.1.0-poc.apk
- **Client README (build/kurulum):** `client-tv/README.md`
- **Kod:** `data/GithubApi.kt` · `update/Updater.kt` · `UpdateViewModel.kt` · `ui/UpdateBanner.kt`
