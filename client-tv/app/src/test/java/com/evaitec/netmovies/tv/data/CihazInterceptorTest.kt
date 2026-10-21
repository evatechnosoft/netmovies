package com.evaitec.netmovies.tv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Sunucu izleme geçmişini cihaz kimliğinin atandığı profile yazar; başlık
// gitmezse her cihaz Dean'in Devam Et'ini görür (projektör sorunu).
class CihazInterceptorTest {

    private fun headerWith(id: String): String? {
        val server = MockWebServer().apply { enqueue(MockResponse()); start() }
        OkHttpClient.Builder().addInterceptor(CihazInterceptor({ id }, "MIBOX4")).build()
            .newCall(Request.Builder().url(server.url("/api/v1/profil")).build()).execute().close()
        val req = server.takeRequest()
        val header = req.getHeader(Cihaz.HEADER)
        if (header != null) assertEquals("MIBOX4", req.getHeader(Cihaz.MODEL_HEADER))
        server.shutdown()
        return header
    }

    @Test fun `kimlik basliga yazilir`() = assertEquals("abc123", headerWith("abc123"))

    @Test fun `bos kimlik baslik eklemez`() = assertNull(headerWith(""))
}
