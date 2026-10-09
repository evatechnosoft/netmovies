package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKeysTest {
    private fun komsu(i: Int, dx: Int, dy: Int) = sarmaliKomsu(i, dx, dy, KLAVYE_ABC.sutun, KLAVYE_ABC.tuslar.size)

    @Test fun `grids are full`() {
        listOf(KLAVYE_ABC, KLAVYE_QWERTY).forEach { assertEquals(0, it.tuslar.size % it.sutun) }
    }

    @Test fun `qwerty has every turkish letter and edit key`() {
        val eksik = KLAVYE_ABC.tuslar - KLAVYE_QWERTY.tuslar.toSet()
        assertEquals(emptyList<String>(), eksik)
        assertEquals("Q", KLAVYE_QWERTY.tuslar[KLAVYE_QWERTY.sutun])  // letters start on row 2
    }

    @Test fun `unknown saved mode falls back to abc`() {
        assertEquals(KlavyeModu.ABC, KlavyeModu.bul(null))
        assertEquals(KlavyeModu.ABC, KlavyeModu.bul("bozuk"))
        assertEquals(KlavyeModu.NORMAL, KlavyeModu.bul("normal"))
    }

    @Test fun `edges wrap around`() {
        val son = KLAVYE_ABC.tuslar.lastIndex
        assertEquals(6, komsu(0, -1, 0))        // left of A -> row end
        assertEquals(0, komsu(6, 1, 0))         // right of row end -> A
        assertEquals(son - 6, komsu(0, 0, -1))  // up from A -> bottom row
        assertEquals(0, komsu(son - 6, 0, 1))   // down from bottom -> top
        assertEquals(8, komsu(1, 0, 1))         // inner move unchanged
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
