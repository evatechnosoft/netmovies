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

class TasimaDurumuTest {
    @Test
    fun tasiBitirKaydeder() {
        var kayit: List<String>? = null
        val d = TasimaDurumu(listOf("A", "B", "C")) { kayit = it }
        d.baslat("C")
        assertEquals(1, d.tasi(-1))
        assertEquals(0, d.tasi(-1))
        assertEquals(null, d.tasi(-1))   // kenarda durur
        d.bitir()
        assertEquals(listOf("C", "A", "B"), kayit)
        assertEquals(null, d.tasinan)
    }

    @Test
    fun iptalEskiSirayaDonerKaydetmez() {
        var kayit: List<String>? = null
        val d = TasimaDurumu(listOf("A", "B", "C")) { kayit = it }
        d.baslat("A")
        d.tasi(1)
        d.iptal()
        assertEquals(listOf("A", "B", "C"), d.sira)
        assertEquals(null, kayit)
    }

    @Test
    fun degismeyenSiraKaydedilmez() {
        var kayit: List<String>? = null
        val d = TasimaDurumu(listOf("A", "B")) { kayit = it }
        d.baslat("A")
        d.bitir()
        assertEquals(null, kayit)
    }
}

class VarsayilanKaynakSirasiTest {
    @Test
    fun yildizlilarVeKalanlarPuanaGore() {
        val sira = varsayilanKaynakSirasi(
            names = listOf("DiziPal", "YouTube", "Puansiz", "DiziMom", "DDizi"),
            favoriler = setOf("DDizi"),
            puanlar = mapOf("DiziMom" to 404.0, "DiziPal" to 320.0, "DDizi" to 239.0),
        )
        assertEquals(listOf("Tümü", "Seriler", "YouTube", "DDizi", "DiziMom", "DiziPal", "Puansiz"), sira)
    }
}
