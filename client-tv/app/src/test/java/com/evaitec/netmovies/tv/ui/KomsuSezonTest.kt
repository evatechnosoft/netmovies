package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KomsuSezonTest {
    @Test fun sagIleriSolGeri() {
        assertEquals(2, komsuSezon(listOf(1, 2, 3), 1, +1))
        assertEquals(1, komsuSezon(listOf(1, 2, 3), 2, -1))
    }

    @Test fun uctaVeTekSezondaNull() {
        assertNull(komsuSezon(listOf(1, 2, 3), 3, +1))
        assertNull(komsuSezon(listOf(1, 2, 3), 1, -1))
        assertNull(komsuSezon(listOf(1), 1, +1))
    }

    @Test fun bilinmeyenSezonIlkineDoner() {
        assertEquals(1, komsuSezon(listOf(1, 2), null, +1))
    }
}
