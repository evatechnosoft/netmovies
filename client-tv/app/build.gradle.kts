plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace  = "com.evaitec.netmovies.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.evaitec.netmovies.tv"
        minSdk        = 26        // Android TV / Mi Box
        targetSdk     = 34
        versionCode   = 2
        versionName   = "0.1.3"

        // Stream taban URL — gradle.properties'ten okunur, yoksa public tunnel kullanılır.
        val baseUrl = (project.findProperty("NETMOVIES_BASE_URL") as String?)
            ?.takeIf { it.isNotBlank() } ?: "https://w.evaitec.com"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        // OTA: bu APK'nın yayınlandığı release tag'i. GitHub'daki en yeni release tag'i
        // bundan farklıysa "güncelleme mevcut" gösterilir. Yeni release'te BUNU güncelle.
        buildConfigField("String", "RELEASE_TAG", "\"v0.1.3-poc\"")
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Android TV (Compose for TV)
    implementation("androidx.tv:tv-material:1.0.0")

    // Media3 / ExoPlayer (HLS oynatma)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Ağ (Retrofit + kotlinx.serialization)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Poster görselleri
    implementation("io.coil-kt:coil-compose:2.7.0")
}
