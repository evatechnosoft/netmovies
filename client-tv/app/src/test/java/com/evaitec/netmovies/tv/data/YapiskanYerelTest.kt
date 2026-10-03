package com.evaitec.netmovies.tv.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YapiskanYerelTest {
    private val zima = "http://192.168.1.186:3310".toHttpUrl()
    private val simdi = 1_000_000_000L

    @Test
    fun evdeVeYakindaCalistiysaYereldeKalir() =
        assertEquals(zima, ServerResolver.yapiskanSecim(zima, simdi - 60_000, simdi, listOf("192.168.1")))

    @Test
    fun evdenCikincaTuneleGecer() =
        assertNull(ServerResolver.yapiskanSecim(zima, simdi - 60_000, simdi, listOf("10.120.4")))

    @Test
    fun uzunSuredirCalismiyorsaTuneleGecer() =
        assertNull(ServerResolver.yapiskanSecim(zima, simdi - 11 * 60_000, simdi, listOf("192.168.1")))

    @Test
    fun hicCalismadiysaTuneleGecer() =
        assertNull(ServerResolver.yapiskanSecim(zima, 0, simdi, listOf("192.168.1")))
}
