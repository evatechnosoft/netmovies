// NetMovies — saat uygulaması (Wear OS). Mini kumanda: sunucuya doğrudan bağlanır,
// televizyona komut yollar. Telefon aracı DEĞİLDİR — saat Wi-Fi'ye bağlıyken ev
// sunucusunu kendi bulur; telefon kapalıyken de çalışsın.
//
// Sürüm TV'den bağımsız: saat arayüzü ayrı gelişiyor, her TV sürümünde saat APK'sı
// yeniden yayınlanmasın. OTA `?target=wear` ile bu APK'yı ayırır.
val wearVersion = "0.1.0"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace  = "com.evaitec.netmovies.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.evaitec.netmovies.wear"
        minSdk        = 30        // Wear OS 3+ (Galaxy Watch 4 ve sonrası)
        targetSdk     = 34
        versionCode   = wearVersion.split(".").map(String::toInt)
            .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
        versionName   = wearVersion

        // TV istemcisiyle aynı adresler: ev sunucusu önce LAN'dan, bulunamazsa tünelden.
        val baseUrl = (project.findProperty("NETMOVIES_BASE_URL") as String?)
            ?.takeIf { it.isNotBlank() } ?: "https://w.evaitec.com"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        val localUrl = (project.findProperty("NETMOVIES_LOCAL_URL") as String?)
            ?.takeIf { it.isNotBlank() } ?: "http://192.168.1.185:3310,http://192.168.0.185:3310,http://192.168.0.29:3310"
        buildConfigField("String", "LOCAL_URL", "\"$localUrl\"")
        buildConfigField("String", "RELEASE_TAG", "\"v$wearVersion-poc\"")
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")

    // Wear'a özgü: yuvarlak ekran düzeni, döner çerçeve, kadran kenarı.
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
