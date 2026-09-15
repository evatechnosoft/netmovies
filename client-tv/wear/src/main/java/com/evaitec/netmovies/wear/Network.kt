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

    /**
     * Çalışan sunucu adresi. Adaylar PARALEL yoklanır, ilk cevap veren kazanır.
     *
     * İki tuzak vardı: adaylar SIRAYLA yoklanıyordu (her ölü aday 2 sn) ve hiçbiri
     * cevap vermeyince tünel adresi ayakta mı diye BAKILMADAN hatırlanıyordu. Tünel
     * kopuksa (cloudflared ağ ad alanı stream'e pinli, stream yeniden kurulunca
     * kopuyor → 530) saat ölü adrese kilitleniyor, ekran sonsuza kadar "yükleniyor"
     * kalıyor ve her düğme "gönderilemedi" diyordu. Artık yalnız GERÇEKTEN ayakta
     * olan adres hatırlanır; hiçbiri yoksa hatırlanmaz, sonraki istek yeniden arar.
     */
    fun taban(): String {
        taban?.let { return it }

        val adaylar = BuildConfig.LOCAL_URL.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val kazanan = adaylar
            .map { aday -> aday to Thread { if (ayakta(aday)) bulunan.compareAndSet(null, aday) } }
            .onEach { (_, is_) -> is_.start() }
            .also { isler -> isler.forEach { (_, is_) -> runCatching { is_.join(2_500) } } }
            .let { bulunan.getAndSet(null) }

        if (kazanan != null) return kazanan.also { taban = it }

        // Tünel de yoklanır: ölü adresi hatırlamak saati kalıcı olarak kör bırakıyordu.
        val tunel = BuildConfig.BASE_URL
        if (ayakta(tunel)) return tunel.also { taban = it }
        return tunel
    }

    private val bulunan = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /** Sunucu bulunabildi mi — ekranın "ulaşılamıyor" demesi için. */
    fun bagli(): Boolean = taban != null

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

    /** Yanıt gövdesi gereken POST'lar için (sesli niyet). Başarısızsa null. */
    fun postAl(yol: String, govde: String): String? = runCatching {
        val istek = Request.Builder()
            .url(taban() + yol)
            .post(govde.toRequestBody("application/json".toMediaType()))
            .build()
        istemci.newCall(istek).execute().use { yanit ->
            if (yanit.isSuccessful) yanit.body?.string() else null
        }
    }.getOrNull()
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

// search_all düz liste döner (katalogdaki gibi `items` sarmalayıcı yok).
@Serializable
data class AramaYaniti(val result: List<KatalogOgesi> = emptyList())

// Sesli niyet (/voice). Tuş/oynatma/ekran niyetlerini SUNUCU kuyruğa yazar —
// saatin ayrıca komut göndermesi gerekmez, `sent` onu söyler. Arama niyetinde
// sonucu kullanıcı seçer: TV'de ne açılacağına Gemini karar vermez.
@Serializable
data class SesNiyeti(
    val action: String = "",
    val query:  String? = null,
    val spoken: String? = null,
    val reply:  String? = null,
    val sent:   Boolean = false,
    val error:  String? = null,
)

@Serializable
data class SesYaniti(val result: SesNiyeti = SesNiyeti())

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
