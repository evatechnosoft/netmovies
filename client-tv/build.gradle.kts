// D8 kayit atama hatasi (b/545349635 sinifi): AGP 8.9.1'in getirdigi R8 8.9.x,
// 256'dan fazla kayit kullanan metotlarda yanlis kayit uretiyor -> ART verifier
// PlayerScreen'i reddediyor (VerifyError). Daha yeni R8 ile derleniyor.
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("com.android.tools:r8:8.13.23") }
}

// Kök build — tüm modüllere plugin versiyonlarını tanımlar (apply false).
plugins {
    id("com.android.application")                       version "8.9.1" apply false
    id("org.jetbrains.kotlin.android")                  version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose")           version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization")     version "2.2.10" apply false
}
