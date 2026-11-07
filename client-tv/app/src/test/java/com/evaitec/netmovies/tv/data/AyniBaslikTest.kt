package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Devam Et showed Suskunlar twice (DiziAbi card + YouTube list card) until the next sync.
class AyniBaslikTest {
    @Test fun farkliSaglayiciAyniBaslikEslesir() {
        assertTrue(ayniBaslik(MediaItem(plugin = "DiziAbi", title = "Suskunlar"), MediaItem(plugin = "YouTube", title = " suskunlar ")))
    }

    @Test fun bosBaslikEslesmez() {
        assertFalse(ayniBaslik(MediaItem(plugin = "A", title = null), MediaItem(plugin = "B", title = null)))
        assertFalse(ayniBaslik(MediaItem(plugin = "A", title = "Abi"), MediaItem(plugin = "B", title = "Abiler")))
    }
}
