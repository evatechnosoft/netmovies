package com.evaitec.netmovies.tv.data

import android.content.Context
import android.provider.Settings
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cihaz kimliği: sunucu izleme geçmişini bu kimliğin atandığı profile yazar
 * (projektör → Eva). Atama sunucuda/admin'de yapılır, uygulamada değil.
 */
object Cihaz {
    const val HEADER = "X-NM-Cihaz"
    const val MODEL_HEADER = "X-NM-Model"

    @Volatile var id: String = ""
        private set

    fun init(context: Context) {
        if (id.isEmpty()) {
            id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }
    }
}

// Model: sunucu yeni cihazı admin'deki `model:<MODEL>` kaydıyla eşler (Mi Box → Dean).
class CihazInterceptor(
    private val id: () -> String = { Cihaz.id },
    private val model: String = android.os.Build.MODEL.orEmpty(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val deviceId = id()
        if (deviceId.isEmpty()) return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder().header(Cihaz.HEADER, deviceId).header(Cihaz.MODEL_HEADER, model).build()
        )
    }
}
