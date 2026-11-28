package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SesBicimiTest {
    @Test
    fun `stereo aac`() = assertEquals("AAC 2.0", sesBicimi("audio/mp4a-latm", 2))

    @Test
    fun `cok kanalli dolby`() {
        assertEquals("Dolby Digital 5.1", sesBicimi("audio/ac3", 6))
        assertEquals("Dolby Digital+ 5.1", sesBicimi("audio/eac3", 6))
        assertEquals("Dolby Atmos 5.1", sesBicimi("audio/eac3-joc", 6))
    }

    @Test
    fun `bilinmeyen kanal ya da codec yazilmaz`() {
        assertEquals("AAC", sesBicimi("audio/mp4a-latm", -1))
        assertEquals("2.0", sesBicimi("audio/x-bilinmeyen", 2))
        assertEquals("", sesBicimi(null, -1))
    }
}
