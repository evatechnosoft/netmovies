package com.evaitec.netmovies.tv.data

import com.evaitec.netmovies.tv.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object Network {

    private val json = Json {
        ignoreUnknownKeys = true   // yanıtta with/schema/subtitles gibi ekstra alanlar var
        coerceInputValues = true   // null -> default
    }

    private val client = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)               // bozuk IPv6 → IPv4 önceliği
        .addInterceptor(BaseUrlInterceptor())  // önce local, olmazsa uzak
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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
