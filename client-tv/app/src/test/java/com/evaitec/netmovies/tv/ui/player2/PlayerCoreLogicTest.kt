package com.evaitec.netmovies.tv.ui.player2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCoreLogicTest {

    @Test
    fun `basili tutma adimi 10-30-60 sn kademelenir`() {
        assertEquals(10_000L, tutmaAdimi(0))
        assertEquals(10_000L, tutmaAdimi(1_499))
        assertEquals(30_000L, tutmaAdimi(1_500))
        assertEquals(30_000L, tutmaAdimi(3_999))
        assertEquals(60_000L, tutmaAdimi(4_000))
    }

    @Test
    fun `sarma hedefi sure icinde kirpilir`() {
        assertEquals(0L, sarmaHedefi(5_000, -10_000, 100_000))
        assertEquals(100_000L, sarmaHedefi(95_000, 10_000, 100_000))
        // Süre bilinmiyorsa yalnız alttan kırpılır.
        assertEquals(1_000_000L, sarmaHedefi(990_000, 10_000, 0))
    }

    @Test
    fun `teklif penceresi son 60 sn ve kisa klipte kapali`() {
        // Pencere 70 → 60 sn: Dean 4 Kasım "son 1 dk'ya koy".
        val sure = 40 * 60_000L
        assertTrue(teklifPenceresinde(sure, sure - 60_000))
        assertTrue(teklifPenceresinde(sure, sure))
        assertFalse(teklifPenceresinde(sure, sure - 60_001))
        // 20 sn'lik kaldırılmış klip: her konum "sona yakın" ama teklif yok.
        assertFalse(teklifPenceresinde(20_000, 10_000))
    }

    @Test
    fun `jenerik sayimi son dakikaya kirpilir`() {
        val sure = 40 * 60_000L
        // Erken jenerik işareti (5 dk kala): sayım son dakikaya kadar başlamaz.
        assertFalse(jenerikteMi(sure, sure - 120_000, sure - 300_000))
        assertTrue(jenerikteMi(sure, sure - 60_000, sure - 300_000))
        // Geç işaret (30 sn kala) kendi yerinde başlar.
        assertFalse(jenerikteMi(sure, sure - 40_000, sure - 30_000))
        assertTrue(jenerikteMi(sure, sure - 30_000, sure - 30_000))
        // İşaret yoksa ya da kısa klipse hiç başlamaz.
        assertFalse(jenerikteMi(sure, sure, null))
        assertFalse(jenerikteMi(20_000, 19_000, 1_000))
    }

    @Test
    fun `bolum istegi listenin saglayicisi ve bolum numarasiyla gider`() {
        // Kart DiziMom'du ama liste DDizi'den geldi (Haysiyet, 27 Eylül): 3. bölümün
        // adresi DDizi'nindir ve sunucuya indeks değil NUMARA gitmeli.
        val liste = listOf(
            com.evaitec.netmovies.tv.data.EpisodeItem(season = 1, episode = 1, url = "dd1"),
            com.evaitec.netmovies.tv.data.EpisodeItem(season = 1, episode = 2, url = "dd2"),
            com.evaitec.netmovies.tv.data.EpisodeItem(season = 1, episode = 3, url = "dd3"),
        )
        assertEquals(
            CozumHedefi("DDizi", "dd3", 3, 1),
            cozumHedefi(liste, 2, listeKaynagi = "DDizi", aktifPlugin = "DiziMom", aktifUrl = "kart"),
        )
        // Liste kartın kendi sağlayıcısındaysa ad değişmez.
        assertEquals("DiziMom", cozumHedefi(liste, 0, null, "DiziMom", "kart").plugin)
        // Bölüm yoksa (film) kartın kendisi.
        assertEquals(
            CozumHedefi("HDFilm", "kart", null, null),
            cozumHedefi(emptyList(), 0, "DDizi", "HDFilm", "kart"),
        )
    }
}
