package com.evaitec.netmovies.tv.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PaylasTest {
    @Test fun `link is pulled out of shared text`() {
        assertEquals("https://a.b/c?d=1", paylasilanAdres("Bak şuna https://a.b/c?d=1 güzel"))
        assertEquals(null, paylasilanAdres("bağlantısız metin"))
        assertEquals(null, paylasilanAdres(null))
    }
}
