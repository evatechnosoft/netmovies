package com.evaitec.netmovies.tv.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// Sunucunun IP'si DHCP ile kayinca secili adres oluyor ve ekranda "ag hatasi"
// kaliyordu. Interceptor bagalanti hatasinda adresi bir kez yeniden kesfetmeli.
class BaseUrlInterceptorTest {

    private fun call(interceptor: BaseUrlInterceptor, target: String) =
        OkHttpClient.Builder().addInterceptor(interceptor).build()
            .newCall(Request.Builder().url(target).build()).execute()

    @Test fun `olu adres yerine yeniden kesfedileni kullanir`() {
        val dead = MockWebServer().apply { start() }
        val deadBase = dead.url("/")
        dead.shutdown()                                    // artik baglanti reddedilir

        val alive = MockWebServer().apply {
            enqueue(MockResponse().setBody("tamam"))
            start()
        }

        val interceptor = BaseUrlInterceptor(current = { deadBase }, rediscover = { alive.url("/") })
        call(interceptor, "http://placeholder/api/v1/health").use {
            assertEquals("tamam", it.body!!.string())
        }
        assertEquals("/api/v1/health", alive.takeRequest().path)
        alive.shutdown()
    }

    @Test fun `yeniden kesif ayni adresi verirse hata yukselir`() {
        val dead = MockWebServer().apply { start() }
        val deadBase = dead.url("/")
        dead.shutdown()

        val interceptor = BaseUrlInterceptor(current = { deadBase }, rediscover = { deadBase })
        val hata = runCatching { call(interceptor, "http://placeholder/api/v1/health") }.exceptionOrNull()
        assertTrue("IOException bekleniyordu, gelen: $hata", hata is IOException)
    }
}
