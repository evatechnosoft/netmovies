package com.evaitec.netmovies.tv.data

import com.evaitec.netmovies.tv.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object Network {

    private val json = Json {
        ignoreUnknownKeys = true   // yanıtta with/schema/subtitles gibi ekstra alanlar var
        coerceInputValues = true   // null -> default
    }

    private val client = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)               // IPv4 + çalışan CF IP pin (TR bloklu 188.114 baypas)
        .addInterceptor(BaseUrlInterceptor())  // önce local, olmazsa uzak
        // Timeout'lar: ölü bağlantı hızlı düşsün (connect 6s), soğuk aggregate için
        // read cömert (45s), hiçbir çağrı sonsuz asılmasın (call 50s) → "Yükleniyor"da kalmaz.
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        // Ağ logu yalnız debug'da: release'te URL + Authorization header'i logcat'e
        // dökmesin (kimlik sızıntısı).
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    val api: NetMoviesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NetMoviesApi::class.java)
    }
}
