package com.evaitec.netmovies.tv.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/** Uzaktan kapatılabilen özellikler. Parametre adı `feature_<anahtar>`. */
enum class Feature(val key: String) {
    OTA_UPDATE("feature_ota_update"),
    WAKE_ON_LAN("feature_wake_on_lan"),
    GEMINI_NANO("feature_gemini_nano"),
}

/** Uzak değer kaynağı; null = bilinmiyor. Test için enjekte edilebilir. */
fun interface FlagSource {
    fun get(key: String): Boolean?
}

/**
 * Kill-switch: varsayılan hep AÇIK. Uzak değer yalnız KAPATABİLİR; yerelde kapalı olan
 * bir şeyi açmaz çünkü çağıran yerel koşulunu `&&` ile ayrıca korur.
 */
class FeatureFlags(private val source: FlagSource = FlagSource { null }) {
    fun enabled(f: Feature): Boolean =
        runCatching { source.get(f.key) }.getOrNull() ?: true

    companion object {
        @Volatile private var instance = FeatureFlags()

        fun enabled(f: Feature): Boolean = instance.enabled(f)

        /** Önbellekteki değerler hemen devreye girer; fetch arka planda (başlangıcı bloklamaz). */
        fun init(context: Context) {
            runCatching {
                if (FirebaseApp.initializeApp(context) == null) return
                val rc = FirebaseRemoteConfig.getInstance()
                rc.setDefaultsAsync(Feature.values().associate { it.key to true })
                rc.activate()
                rc.fetch()
                instance = FeatureFlags { k -> rc.getBoolean(k) }
            }
        }
    }
}
