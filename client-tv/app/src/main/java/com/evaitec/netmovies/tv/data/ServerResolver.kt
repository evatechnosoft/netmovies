package com.evaitec.netmovies.tv.data

import android.content.Context
import android.content.SharedPreferences
import com.evaitec.netmovies.tv.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// "Önce local, olmazsa uzak":
// TV/Mibox sunucuyla aynı ev ağındaysa yerel IP'ye (Cloudflare'siz, hızlı) bağlan;
// ulaşılamıyorsa w.evaitec.com'a (Cloudflare tunnel) düş. Cloudflare TR'de bazı IP
// aralıklarını (188.114.x) bloklu döndürdüğü için uzak yol tek başına dengesiz.
//
// Sunucu PC'nin adresi DHCP ile kayıyor (1.185 -> 0.29): sabit aday listesi tek başına
// yetmedi, TV tünele düşüp posterleri kaybetti. Sıra artık üç katmanlı:
//   1) en son çalışan adres (SharedPreferences) + derlemedeki adaylar — paralel, hızlı
//   2) yoksa /24 taraması: cihazın kendi alt ağı + evin bilinen iki alt ağı
//   3) hiçbiri yoksa uzak tünel
object ServerResolver {
    private const val PREFS      = "server"
    private const val KEY_LAST   = "last_local"
    private const val PORT       = 3310
    private const val SCAN_MISS_TTL_MS = 5 * 60 * 1000L

    // Ev ağları. Cihazın kendi alt ağı ayrıca eklenir; burada olmayan bir ağa
    // taşınsa bile kendi /24'ü taranır.
    private val KNOWN_PREFIXES = listOf("192.168.0", "192.168.1")

    private val probe = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    // Tarama yerel ağda: bağlantı ya anında kurulur ya da yoktur, 500 ms yeter.
    private val scanProbe = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var active: HttpUrl? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var lastScanMissAt = 0L

    /** Uygulama açılışında bir kez: son çalışan adresin hatırlanabilmesi için. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Yoklanacak yerel adaylar — LOCAL_URL virgülle ayrık liste olabilir. */
    internal fun localCandidates(raw: String): List<HttpUrl> =
        raw.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toHttpUrlOrNull() }

    /** /24 ön eklerinden (ör. "192.168.0") taranacak tüm host adresleri, tekrarsız. */
    internal fun subnetHosts(prefixes: List<String>, port: Int = PORT): List<HttpUrl> =
        prefixes.distinct().flatMap { p -> (1..254).map { "http://$p.$it:$port".toHttpUrl() } }

    /** Cihazın kendi IPv4 /24 ön ekleri (Wi-Fi + Ethernet; loopback/IPv6 hariç). */
    private fun ownPrefixes(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress!!.substringBeforeLast('.') }
    }.getOrDefault(emptyList())

    /** Aktif sunucu adresi (cache'li). İlk çağrıda yerel adayları yoklar, gerekirse ağı tarar. */
    fun activeBase(): HttpUrl {
        active?.let { return it }
        return synchronized(this) {
            active ?: run {
                val remote = BuildConfig.BASE_URL.toHttpUrl()
                val chosen = discoverLocal() ?: remote
                active = chosen
                chosen
            }
        }
    }

    private fun discoverLocal(): HttpUrl? {
        val remembered = prefs?.getString(KEY_LAST, null)?.toHttpUrlOrNull()
        val quick = listOfNotNull(remembered) + localCandidates(BuildConfig.LOCAL_URL)
        firstAlive(quick, probe)?.let { return it.also(::remember) }

        // Tarama pahalı (≤508 bağlantı); ev dışında her yeniden yüklemede tekrarlanmasın.
        if (System.currentTimeMillis() - lastScanMissAt < SCAN_MISS_TTL_MS) return null
        val found = firstAlive(subnetHosts(ownPrefixes() + KNOWN_PREFIXES), scanProbe, poolSize = 64)
        if (found == null) lastScanMissAt = System.currentTimeMillis()
        return found?.also(::remember)
    }

    private fun remember(base: HttpUrl) {
        prefs?.edit()?.putString(KEY_LAST, base.toString())?.apply()
    }

    /** Adayları paralel yoklar; ilk cevap veren kazanır (sıralı olsaydı her ölü
     *  aday açılışa 1,5 sn eklerdi). */
    private fun firstAlive(candidates: List<HttpUrl>, client: OkHttpClient, poolSize: Int = candidates.size): HttpUrl? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first().takeIf { isAlive(it, client) }
        val pool = Executors.newFixedThreadPool(poolSize.coerceIn(1, 64))
        return try {
            val futures = candidates.map { base -> pool.submit(Callable { if (isAlive(base, client)) base else null }) }
            // Sırayla bekle: ilk canlı bulununca kalan yoklamalar iptal edilir.
            futures.firstNotNullOfOrNull { runCatching { it.get() }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    fun activeBaseString(): String = activeBase().toString().trimEnd('/')

    private val resolving = java.util.concurrent.atomic.AtomicBoolean(false)

    /** UI için: HİÇ bloklamaz. Seçim yoksa uzak adresi verir ve seçimi arka planda
     *  başlatır. Eskiden poster URL'i üretilirken activeBase() ana iş parçacığında
     *  yoklama (v0.1.59'dan sonra /24 taraması) yapıyordu → ekran donuyor, ANR ile
     *  uygulama kapanıp yeniden açılıyordu. */
    fun uiBase(): HttpUrl {
        active?.let { return it }
        if (resolving.compareAndSet(false, true)) {
            Thread {
                try { activeBase() } finally { resolving.set(false) }
            }.apply { isDaemon = true }.start()
        }
        return BuildConfig.BASE_URL.toHttpUrl()
    }

    /** Seçilmiş adres — HİÇ ağ yoklamaz. UI'dan (main thread) güvenle çağrılır;
     *  henüz seçim yapılmadıysa null. */
    fun cachedBase(): HttpUrl? = active

    /** Adres yerel ağdan mı? (ekranda "yerel ağ / uzak tünel" göstermek için)
     *  Tarama ile bulunan adres aday listesinde olmayabilir → özel IP aralığına bak. */
    fun isLocal(base: HttpUrl): Boolean =
        localCandidates(BuildConfig.LOCAL_URL).any { it.host == base.host } ||
            runCatching { (java.net.InetAddress.getByName(base.host) as? Inet4Address)?.isSiteLocalAddress == true }.getOrDefault(false)

    /** Ağ değişimi / "Tekrar dene" → yeniden yokla. */
    fun reset() { active = null }

    private fun isAlive(base: HttpUrl, client: OkHttpClient): Boolean = try {
        val url = base.newBuilder().addPathSegments("api/v1/health").build()
        client.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
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
