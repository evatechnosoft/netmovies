# Saat APK'si DEBUG imzali yayinlaniyor ama R8 ile kucultuluyor (build.gradle.kts).
# Ilk denemede (0.1.14) uygulama ACILMADI: kucultme uygulamanin kendi siniflarina
# da dokunuyordu. Kural artik iki katmanli — kendi kodumuz OLDUGU GIBI durur,
# kazanc zaten kullanilmayan kutuphane kodundan (Compose/Wear/OkHttp) geliyor.

# 1) Uygulama kodu: hicbir sinif atilmaz, yeniden adlandirilmaz.
#    Retrofit arayuzleri, @Serializable veri siniflari ve Compose giris
#    noktalari yansimayla bulunuyor; tek tek kural yazmak kirilgan.
-keep class com.evaitec.netmovies.wear.** { *; }

# 2) Yansima icin gereken ust veri. `Signature` OLMAZSA Retrofit generic donus
#    tipini cozemez ("Unable to create converter"); `Exceptions` ve `InnerClasses`
#    de ayni zincirde.
-keepattributes Signature, Exceptions, InnerClasses, *Annotation*, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# 3) kotlinx.serialization: uretilen serializer'lar yansimayla bulunuyor.
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# 4) Retrofit/OkHttp: platform-ozel siniflar yok, uyari calismayi etkilemez.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**
-keep,allowobfuscation interface retrofit2.Call
-keep class retrofit2.Response
