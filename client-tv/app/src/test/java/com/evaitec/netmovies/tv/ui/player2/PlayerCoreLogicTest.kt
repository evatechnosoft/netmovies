package com.evaitec.netmovies.tv.ui.player2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCoreLogicTest {

    @Test
    fun `basili tutma adimi 10-30-60 sn kademelenir`() {
        assertEquals(10_000L, tutmaAdimi(0))
        assertEquals(10_000L, tutmaAdimi(1_499))
        assertEquals(30_000L, tutmaAdimi(1_500))
        assertEquals(30_000L, tutmaAdimi(3_999))
        assertEquals(60_000L, tutmaAdimi(4_000))
    }

    @Test
    fun `sarma hedefi sure icinde kirpilir`() {
        assertEquals(0L, sarmaHedefi(5_000, -10_000, 100_000))
        assertEquals(100_000L, sarmaHedefi(95_000, 10_000, 100_000))
        // Süre bilinmiyorsa yalnız alttan kırpılır.
        assertEquals(1_000_000L, sarmaHedefi(990_000, 10_000, 0))
    }

    @Test
    fun `teklif penceresi son 70 sn ve kisa klipte kapali`() {
        val sure = 40 * 60_000L
        assertTrue(teklifPenceresinde(sure, sure - 70_000))
        assertTrue(teklifPenceresinde(sure, sure))
        assertFalse(teklifPenceresinde(sure, sure - 70_001))
        // 20 sn'lik kaldırılmış klip: her konum "sona yakın" ama teklif yok.
        assertFalse(teklifPenceresinde(20_000, 10_000))
    }
}
