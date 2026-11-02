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
        ayarlar: Boolean = false,
        kontroller: Boolean = false,
    ) = geriEylemi(sayim, scrub, panel, liste, bolumSayfasi, ayarlar, kontroller)

    // Dean 02.11: bölüm başlamadan GERİ çıkmalı (eskiden kaynak varsa OYNAT'tı).
    @Test
    fun `baslangic panelinde GERI cikis`() {
        assertEquals(GeriEylem.CIK, geri(panel = true))
        assertEquals(GeriEylem.CIK, geri(panel = true, ayarlar = true, kontroller = true))
    }

    @Test
    fun `liste sirasi bolum sayfasi sonra liste`() {
        assertEquals(GeriEylem.SEZONLARA_DON, geri(panel = true, liste = true, bolumSayfasi = true))
        assertEquals(GeriEylem.LISTEYI_KAPAT, geri(panel = true, liste = true))
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
