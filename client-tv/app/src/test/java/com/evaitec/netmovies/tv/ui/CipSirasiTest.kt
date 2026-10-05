package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CipSirasiTest {
    private val varsayilan = listOf("Tümü", "Seriler", "YouTube", "DiziMom", "DiziPal")

    @Test
    fun kayitYoksaVarsayilan() {
        assertEquals(varsayilan, siralaCipler(varsayilan, emptyList()))
    }

    @Test
    fun kayitliSiraUygulanirOlmayanDuserYeniSonaEklenir() {
        val kayitli = listOf("DiziMom", "Tümü", "Silinen", "YouTube", "Seriler")
        assertEquals(
            listOf("DiziMom", "Tümü", "YouTube", "Seriler", "DiziPal"),
            siralaCipler(varsayilan, kayitli),
        )
    }
}
