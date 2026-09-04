package com.evaitec.netmovies.tv.data

import com.evaitec.netmovies.tv.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// "Önce local, olmazsa uzak":
// TV/Mibox sunucuyla aynı ev ağındaysa yerel IP'ye (Cloudflare'siz, hızlı) bağlan;
// ulaşılamıyorsa w.evaitec.com'a (Cloudflare tunnel) düş. Cloudflare TR'de bazı IP
// aralıklarını (188.114.x) bloklu döndürdüğü için uzak yol tek başına dengesiz.
// Ev birden çok ağ kullanabildiği için yerel adres TEK değil, aday listesidir.
object ServerResolver {
    private val probe = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var active: HttpUrl? = null

    /** Yoklanacak yerel adaylar — LOCAL_URL virgülle ayrık liste olabilir. */
    internal fun localCandidates(raw: String): List<HttpUrl> =
        raw.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toHttpUrlOrNull() }

    /** Aktif sunucu adresi (cache'li). İlk çağrıda yerel adayları yoklar. */
    fun activeBase(): HttpUrl {
        active?.let { return it }
        return synchronized(this) {
            active ?: run {
                val remote = BuildConfig.BASE_URL.toHttpUrl()
                val chosen = firstAlive(localCandidates(BuildConfig.LOCAL_URL)) ?: remote
                active = chosen
                chosen
            }
        }
    }

    /** Adayları paralel yoklar; ilk cevap veren kazanır (sıralı olsaydı her ölü
     *  aday açılışa 1,5 sn eklerdi). */
    private fun firstAlive(candidates: List<HttpUrl>): HttpUrl? {
        if (candidates.size <= 1) return candidates.firstOrNull()?.takeIf(::isAlive)
        val pool    = Executors.newFixedThreadPool(candidates.size)
        return try {
            pool.invokeAll(candidates.map { base -> Callable { if (isAlive(base)) base else null } })
                .firstNotNullOfOrNull { runCatching { it.get() }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    fun activeBaseString(): String = activeBase().toString().trimEnd('/')

    /** Ağ değişimi / "Tekrar dene" → yeniden yokla. */
    fun reset() { active = null }

    private fun isAlive(base: HttpUrl): Boolean = try {
        val url = base.newBuilder().addPathSegments("api/v1/health").build()
        probe.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}

// İstekleri aktif sunucuya yönlendirir (scheme + host + port). Retrofit baseUrl'i
// placeholder kalır; gerçek hedef burada belirlenir.
class BaseUrlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = ServerResolver.activeBase()
        val req  = chain.request()
        val newUrl = req.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(req.newBuilder().url(newUrl).build())
    }
}
