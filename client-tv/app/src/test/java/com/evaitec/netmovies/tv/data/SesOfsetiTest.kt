package com.evaitec.netmovies.tv.data

import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SesOfsetiTest {
    @Test
    fun `bluetoothta arti ofset ses saatini ileri alir`() {
        assertEquals(1_100_000L, SesOfseti.uygula(1_000_000L, 100, bt = true))
        assertEquals(900_000L, SesOfseti.uygula(1_000_000L, -100, bt = true))
    }

    @Test
    fun `bluetooth degilse saat degismez`() {
        assertEquals(1_000_000L, SesOfseti.uygula(1_000_000L, 100, bt = false))
    }

    @Test
    fun `bilinmeyen konum oldugu gibi gecer`() {
        val yok = AudioSink.CURRENT_POSITION_NOT_SET
        assertEquals(yok, SesOfseti.uygula(yok, 100, bt = true))
    }

    @Test
    fun `secenekler sifiri icerir ve adimlidir`() {
        assertTrue(0 in SesOfseti.secenekler)
        assertEquals(SesOfseti.ADIM_MS, SesOfseti.secenekler[1] - SesOfseti.secenekler[0])
    }
}
