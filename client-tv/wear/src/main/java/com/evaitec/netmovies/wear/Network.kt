package com.evaitec.netmovies.wear

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// Saatin sunucuya bağlanma yolu. TV istemcisinin `ServerResolver`'ı ile aynı fikir:
// önce ev ağındaki adaylar PARALEL yoklanır, ilk cevap veren kazanır; hiçbiri
// yoksa tünele düşülür. Saat evde Wi-Fi'ye bağlıysa tünel üzerinden dolaşmak hem
// yavaş hem gereksiz — üstelik tünel PIN kapısının arkasında ve saat çerez taşımaz.

object Sunucu {
    private val istemci = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    @Volatile private var taban: String? = null

    /** Çalışan sunucu adresi. İlk çağrıda yoklar, sonra hatırlar. */
    fun taban(): String {
        taban?.let { return it }

        val adaylar = BuildConfig.LOCAL_URL.split(",").map { it.trim() }.filter { it.isNotBlank() }
        for (aday in adaylar) {
            if (ayakta(aday)) {
                taban = aday
                return aday
            }
        }
        return BuildConfig.BASE_URL.also { taban = it }
    }

    private fun ayakta(adres: String): Boolean = runCatching {
        istemci.newCall(Request.Builder().url("$adres/api/v1/health").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Sunucu değiştiyse (başka ağa geçildi) yeniden aranması için. */
    fun unut() { taban = null }

    fun get(yol: String): String? = runCatching {
        istemci.newCall(Request.Builder().url(taban() + yol).build()).execute().use { yanit ->
            if (yanit.isSuccessful) yanit.body?.string() else null
        }
    }.getOrNull()

    fun post(yol: String, govde: String? = null): Boolean = runCatching {
        val istek = Request.Builder()
            .url(taban() + yol)
            .post((govde ?: "").toRequestBody("application/json".toMediaType()))
            .build()
        istemci.newCall(istek).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}

// ── Uçların gövdeleri ────────────────────────────────────────────────────────
// Alanlar sunucudakiyle birebir; tanınmayanlar yok sayılır ki eski saat APK'sı
// yeni bir alan yüzünden çökmesin.

@Serializable
data class IzlemeKaydi(
    val plugin: String = "",
    @SerialName("content_url") val contentUrl: String = "",
    val title: String = "",
    val poster: String = "",
)

@Serializable
data class IzlemeYaniti(val result: List<IzlemeKaydi> = emptyList())

@Serializable
data class KatalogOgesi(
    val plugin: String = "",
    val url: String = "",
    val title: String = "",
    val poster: String = "",
)

@Serializable
data class KatalogGovde(val items: List<KatalogOgesi> = emptyList())

@Serializable
data class KatalogYaniti(val result: KatalogGovde = KatalogGovde())

// load_item → dizi bilgisi. Saat yalnız bölüm listesini kullanıyor: poster'a
// dokunulunca dizi mi film mi olduğu buradan anlaşılır.
@Serializable
data class BolumOgesi(
    val season: Int = 1,
    val episode: Int? = null,
    val title: String? = null,
)

@Serializable
data class BilgiGovde(val episodes: List<BolumOgesi> = emptyList())

@Serializable
data class BilgiYaniti(val result: BilgiGovde? = null)
