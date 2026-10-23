# TV APK'sı DEBUG imzalı yayınlanıyor ama R8 ile küçültülüyor (build.gradle.kts).
# Saatteki iki katmanlı kural: kendi kodumuz OLDUĞU GİBİ durur, kazanç kullanılmayan
# kütüphane kodundan (Compose/Media3/OkHttp) gelir.

# 1) Uygulama kodu: hiçbir sınıf atılmaz, yeniden adlandırılmaz (Retrofit arayüzleri,
#    @Serializable veri sınıfları, Compose giriş noktaları yansımayla bulunuyor).
-keep class com.evaitec.netmovies.tv.** { *; }

# 2) Yansıma için gereken üst veri. `Signature` olmazsa Retrofit generic dönüş tipini çözemez.
-keepattributes Signature, Exceptions, InnerClasses, *Annotation*, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# 3) kotlinx.serialization: üretilen serializer'lar yansımayla bulunuyor.
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# 4) Retrofit/OkHttp: platform-özel sınıflar yok, uyarı çalışmayı etkilemez.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**
-keep,allowobfuscation interface retrofit2.Call
-keep class retrofit2.Response
