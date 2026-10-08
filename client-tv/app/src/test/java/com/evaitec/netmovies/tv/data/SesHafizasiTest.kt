package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SesHafizasiTest {
    @Test
    fun kayitYoksaDokuzdanBaslar() = assertEquals(9, SesHafizasi.hedef(-1, 15))

    @Test
    fun sifirKayitDokuzdanBaslar() = assertEquals(9, SesHafizasi.hedef(0, 15))

    @Test
    fun kayitVarsaKaldigiYerden() = assertEquals(4, SesHafizasi.hedef(4, 15))

    @Test
    fun tavaniAsmaz() = assertEquals(7, SesHafizasi.hedef(-1, 7))
}
