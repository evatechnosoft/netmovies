package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HataMesajiTest {
    @Test
    fun agHatalariInsanDilinde() {
        assertEquals("Sunucu yanıt vermedi", SocketTimeoutException("x").kullaniciMesaji("v"))
        assertEquals("Sunucuya ulaşılamıyor — ev sunucusu açık mı?", UnknownHostException("host").kullaniciMesaji("v"))
        assertEquals("Sunucuya ulaşılamıyor — ev sunucusu açık mı?", ConnectException("refused").kullaniciMesaji("v"))
        assertEquals("Bağlantı koptu", IOException("reset").kullaniciMesaji("v"))
    }

    @Test
    fun digerHatalardaEkranCumlesi() {
        assertEquals("Liste alınamadı", IllegalStateException("HTTP 502").kullaniciMesaji("Liste alınamadı"))
    }
}
