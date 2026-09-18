# Saat APK'si DEBUG imzali yayinlaniyor ama R8 ile kucultuluyor (build.gradle.kts).
# Kucultme kod boyutunu ~3 kat dusuruyor; saatin Wi-Fi radyosu uyuya kalka
# indirdigi icin her megabayt bekleme suresi demek.

# kotlinx.serialization: @Serializable siniflarin uretilen serializer'lari
# yansimayla bulunuyor, R8 kullanilmiyor sanip atiyor.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.evaitec.netmovies.wear.**$$serializer { *; }
-keepclassmembers class com.evaitec.netmovies.wear.** {
    *** Companion;
}

# OkHttp/Okio: platform-ozel siniflar yoksa uyari verir, calismayi etkilemez.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
