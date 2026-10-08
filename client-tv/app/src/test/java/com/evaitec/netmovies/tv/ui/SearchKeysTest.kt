package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKeysTest {
    @Test fun `grid is full`() {
        assertEquals(0, KLAVYE_TUSLARI.size % KLAVYE_SUTUN)
    }

    @Test fun `edges wrap around`() {
        val son = KLAVYE_TUSLARI.lastIndex
        assertEquals(6, sarmaliKomsu(0, -1, 0))        // left of A -> row end
        assertEquals(0, sarmaliKomsu(6, 1, 0))         // right of row end -> A
        assertEquals(son - 6, sarmaliKomsu(0, 0, -1))  // up from A -> bottom row
        assertEquals(0, sarmaliKomsu(son - 6, 0, 1))   // down from bottom -> top
        assertEquals(8, sarmaliKomsu(1, 0, 1))         // inner move unchanged
    }

    @Test fun `turkish letters lowercase correctly`() {
        assertEquals("ıi", tusUygula(tusUygula("", "I"), "İ"))
        assertEquals("çğ", tusUygula(tusUygula("", "Ç"), "Ğ"))
    }

    @Test fun `space delete clear`() {
        assertEquals("", tusUygula("", TUS_BOSLUK))
        assertEquals("ab ", tusUygula("ab", TUS_BOSLUK))
        assertEquals("ab ", tusUygula("ab ", TUS_BOSLUK))
        assertEquals("a", tusUygula("ab", TUS_SIL))
        assertEquals("", tusUygula("", TUS_SIL))
        assertEquals("", tusUygula("abc", TUS_TEMIZLE))
    }
}
