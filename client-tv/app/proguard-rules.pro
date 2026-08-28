# kotlinx.serialization — @Serializable modelleri koru (POC'ta minify kapalı ama ileriye dönük).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.evaitec.netmovies.tv.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.evaitec.netmovies.tv.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
