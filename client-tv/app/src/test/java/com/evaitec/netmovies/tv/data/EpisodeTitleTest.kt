package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Bölüm sayfası kartında adres eşleşmesi tutmayabiliyor; başlık tek ipucu olur.
// Yanlış eşleşme kullanıcıyı 1. bölüme düşürüyordu (Dean, 18 Eylül).
class EpisodeTitleTest {

    private val bolumler = listOf(
        EpisodeItem(season = 1, episode = 1, url = "a"),
        EpisodeItem(season = 3, episode = 7, url = "b"),
        EpisodeItem(season = 3, episode = 8, url = "c"),
    )

    @Test
    fun turkceBaslikBolumuBulur() {
        assertEquals(2, basliktanBolum("The Walking Dead: Dead City 3.Sezon 8.Bölüm", bolumler))
        assertEquals(2, basliktanBolum("Dead City 3. Sezon 8. Bolum", bolumler))
    }

    @Test
    fun kisaBicimCalisir() {
        assertEquals(1, basliktanBolum("Dead City S3B7", bolumler))
    }

    @Test
    fun listedeOlmayanBolumEslesmez() {
        assertEquals(-1, basliktanBolum("Dead City 9.Sezon 9.Bölüm", bolumler))
        assertEquals(-1, basliktanBolum("Dead City", bolumler))
        assertEquals(-1, basliktanBolum(null, bolumler))
    }
}
