package com.evaitec.netmovies.tv.ui.player2

import org.junit.Assert.assertEquals
import org.junit.Test

class GeriEylemTest {

    private fun geri(
        sayim: Boolean = false,
        scrub: Boolean = false,
        panel: Boolean = false,
        liste: Boolean = false,
        bolumSayfasi: Boolean = false,
        kaynak: Boolean = false,
        ayarlar: Boolean = false,
        kontroller: Boolean = false,
    ) = geriEylemi(sayim, scrub, panel, liste, bolumSayfasi, kaynak, ayarlar, kontroller)

    @Test
    fun `baslangic panelinde GERI kaynak varsa OYNAT`() {
        assertEquals(GeriEylem.OYNAT, geri(panel = true, kaynak = true))
    }

    @Test
    fun `baslangic panelinde kaynak yoksa cikis`() {
        assertEquals(GeriEylem.CIK, geri(panel = true))
    }

    @Test
    fun `liste sirasi bolum sayfasi sonra liste`() {
        assertEquals(GeriEylem.SEZONLARA_DON, geri(panel = true, liste = true, bolumSayfasi = true, kaynak = true))
        assertEquals(GeriEylem.LISTEYI_KAPAT, geri(panel = true, liste = true, kaynak = true))
    }

    @Test
    fun `geri sayim ve scrub her seyden once`() {
        assertEquals(GeriEylem.SAYIMI_IPTAL, geri(sayim = true, scrub = true, panel = true, ayarlar = true))
        assertEquals(GeriEylem.SCRUB_KAPAT, geri(scrub = true, panel = true, ayarlar = true))
    }

    @Test
    fun `ayarlar sonra kontroller sonra cikis`() {
        assertEquals(GeriEylem.AYARLARI_KAPAT, geri(ayarlar = true, kontroller = true))
        assertEquals(GeriEylem.KONTROLLERI_GIZLE, geri(kontroller = true))
        assertEquals(GeriEylem.CIK, geri())
    }
}
