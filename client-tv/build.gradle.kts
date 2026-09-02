// Kök build — tüm modüllere plugin versiyonlarını tanımlar (apply false).
plugins {
    id("com.android.application")                       version "8.9.1" apply false
    id("org.jetbrains.kotlin.android")                  version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose")           version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization")     version "2.2.10" apply false
}
