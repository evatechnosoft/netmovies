package com.evaitec.netmovies.tv.input

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DOWN = KeyEvent.ACTION_DOWN
private const val UP = KeyEvent.ACTION_UP

class KeyPairGateTest {

    @Test
    fun `DOWN gorulmeden gelen UP yutulur`() {
        // Basış başka bir ekranda oldu; buraya yalnız bırakma düştü.
        assertFalse(KeyPairGate().kabul(UP, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `DOWN gorulen tusun UP'i islenir`() {
        val kapi = KeyPairGate()
        assertTrue(kapi.kabul(DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(kapi.kabul(UP, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `ayni tusun ikinci UP'i yutulur`() {
        val kapi = KeyPairGate()
        kapi.kabul(DOWN, KeyEvent.KEYCODE_BACK)
        kapi.kabul(UP, KeyEvent.KEYCODE_BACK)
        assertFalse(kapi.kabul(UP, KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `tuslar birbirinden bagimsiz`() {
        val kapi = KeyPairGate()
        kapi.kabul(DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        assertFalse(kapi.kabul(UP, KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(kapi.kabul(UP, KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `uzun basis tekrarlari UP'i bozmaz`() {
        val kapi = KeyPairGate()
        repeat(5) { kapi.kabul(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertTrue(kapi.kabul(UP, KeyEvent.KEYCODE_DPAD_RIGHT))
    }
}
