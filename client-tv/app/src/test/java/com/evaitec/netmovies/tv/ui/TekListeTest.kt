package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TekListeTest {
    @Test fun bosken_basilan_secilir() =
        assertEquals(listOf(false, true, false), tekListe(listOf(false, false, false), 1))

    @Test fun baska_listeye_basinca_eskisinden_cikar() =
        assertEquals(listOf(false, false, true), tekListe(listOf(true, false, false), 2))

    @Test fun seciliye_tekrar_basinca_hicbiri_kalmaz() =
        assertEquals(listOf(false, false, false), tekListe(listOf(false, true, false), 1))

    @Test fun eski_coklu_kayitta_basilan_kalir() =
        assertEquals(listOf(true, false, false), tekListe(listOf(true, true, false), 0))
}
