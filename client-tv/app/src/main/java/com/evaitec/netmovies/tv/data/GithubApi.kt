package com.evaitec.netmovies.tv.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

// OTA: GitHub Releases API. /releases (liste) prerelease'leri de içerir; /releases/latest
// prerelease'i ATLAR — o yüzden liste kullanıyoruz.
@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

interface GithubApi {
    @GET("repos/evatechnosoft/netmovies/releases")
    suspend fun releases(@Query("per_page") perPage: Int = 5): List<GhRelease>
}

object Github {
    private val json = Json { ignoreUnknownKeys = true }

    val api: GithubApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubApi::class.java)
    }
}
