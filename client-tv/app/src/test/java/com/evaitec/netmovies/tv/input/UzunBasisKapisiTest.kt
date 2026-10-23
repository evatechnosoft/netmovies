package com.evaitec.netmovies.tv.input

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DOWN = KeyEvent.ACTION_DOWN
private const val UP = KeyEvent.ACTION_UP
private const val OK = KeyEvent.KEYCODE_DPAD_CENTER

class UzunBasisKapisiTest {

    @Test
    fun `ateslenmeden once tekrar gecer`() {
        assertFalse(UzunBasisKapisi().yutar(DOWN, OK, 3))
    }

    @Test
    fun `tek sefer eylemden sonra tekrarlar yutulur`() {
        val kapi = UzunBasisKapisi()
        kapi.ateslendi(OK)
        assertTrue(kapi.yutar(DOWN, OK, 5))
        assertTrue(kapi.yutar(DOWN, OK, 6))
    }

    @Test
    fun `birakinca kayit temizlenir`() {
        val kapi = UzunBasisKapisi()
        kapi.ateslendi(OK)
        assertFalse(kapi.yutar(UP, OK, 0))
        assertFalse(kapi.yutar(DOWN, OK, 2))
    }

    @Test
    fun `yeni basis kaydi temizler`() {
        val kapi = UzunBasisKapisi()
        kapi.ateslendi(OK)
        assertFalse(kapi.yutar(DOWN, OK, 0))
        assertFalse(kapi.yutar(DOWN, OK, 1))
    }

    @Test
    fun `baska tusun tekrari etkilenmez`() {
        val kapi = UzunBasisKapisi()
        kapi.ateslendi(OK)
        assertFalse(kapi.yutar(DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 4))
    }
}
