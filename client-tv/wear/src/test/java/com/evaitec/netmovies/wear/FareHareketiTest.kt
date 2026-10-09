package com.evaitec.netmovies.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class FareHareketiTest {
    @Test fun `still hand does not creep`() {
        val (dx, dy) = fareHareketi(0.02f, -0.02f, 0.01f, FareAyari())
        assertEquals(0f, dx, 0f)
        assertEquals(0f, dy, 0f)
    }

    @Test fun `quarter-pi turn is 2048 px at defaults`() {
        val (dx, dy) = fareHareketi(0f, (Math.PI / 4).toFloat(), 1f, FareAyari())
        assertEquals(2048f, dx, 1f)
        assertEquals(0f, dy, 0f)
    }

    @Test fun `negative gain inverts and speed scales`() {
        val (dx, dy) = fareHareketi(1f, 1f, 0.01f, FareAyari(x = -5, y = 10, hiz = 10))
        val (bx, by) = fareHareketi(1f, 1f, 0.01f, FareAyari())
        assertEquals(bx * -2f, dx, 0.01f)  // -5 flips, hiz 10 doubles
        assertEquals(by * 4f, dy, 0.01f)   // y 10 doubles, hiz 10 doubles
    }

    @Test fun `right wrist flips only Y`() {
        val (lx, ly) = fareHareketi(1f, 1f, 0.01f, FareAyari(el = "sol"))
        val (rx, ry) = fareHareketi(1f, 1f, 0.01f, FareAyari(el = "sag"))
        assertEquals(lx, rx, 0f)
        assertEquals(-ly, ry, 0.01f)
    }

    @Test fun `focus mode steps one arrow past the threshold`() {
        assertEquals(null, odakYonu(100f, -100f))
        assertEquals(TUS_SAG, odakYonu(500f, 100f))
        assertEquals(TUS_YUKARI, odakYonu(100f, -500f))
    }
}
