package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * İzleme kaydındaki bölüm kimliği. Eskiden liste indeksi yazılıyordu: sağlayıcının
 * listesi değişince indeks başka bölümü gösteriyor, liste küçülünce de ekrana
 * "123. bölüm" diye düşüyordu (32 bölümlük Reacher'da).
 */
class EpisodeRefTest {
    private val reacher = (1..4).flatMap { s -> (1..8).map { e -> EpisodeItem(season = s, episode = e) } }

    @Test fun `numarali kayit yazilir`() {
        assertEquals("S4B8", episodeRef(4, 8, 31))
    }

    @Test fun `numara yoksa indekse duser`() {
        assertEquals("7", episodeRef(1, null, 7))
    }

    @Test fun `web biciminden de okunur`() {
        assertEquals(4 to 8, parseEpisodeRef("S4 E8"))
        assertEquals(4 to 8, parseEpisodeRef("S4B8"))
        assertNull(parseEpisodeRef("31"))
    }

    @Test fun `numara listede aranir siradan bagimsiz`() {
        assertEquals(31, episodeIndexOf("S4B8", reacher))
        assertEquals(31, episodeIndexOf("S4 E8", reacher))
        // Sağlayıcı 2. sezondan başlıyorsa aynı bölüm başka sırada.
        assertEquals(7, episodeIndexOf("S4B8", reacher.drop(24)))
    }

    @Test fun `liste disina tasan eski indeks bolum gostermez`() {
        assertEquals(31, episodeIndexOf("31", reacher))   // sınır içinde: eski kayıt geçerli
        assertNull(episodeIndexOf("123", reacher))        // 123. bölüm diye bir şey yok
        assertNull(episodeIndexOf("", reacher))
        assertNull(episodeIndexOf("S9B9", reacher))       // listede olmayan bölüm
    }

    @Test fun `sonraki bolum numarayla, siradan bagimsiz`() {
        assertEquals(1, nextEpisodeIndex(0, reacher))
        assertEquals(8, nextEpisodeIndex(7, reacher))         // S1B8 → S2B1
        assertNull(nextEpisodeIndex(31, reacher))             // dizinin son bölümü
        // Yeni sezon başta: S2 sonu → S1B1 açılıyordu.
        val tersSezon = reacher.drop(8).take(8) + reacher.take(8)
        assertNull(nextEpisodeIndex(7, tersSezon))
        assertEquals(0, nextEpisodeIndex(15, tersSezon))      // S1B8 → S2B1
        // Numarasız liste: sıra.
        val numarasiz = List(3) { EpisodeItem() }
        assertEquals(1, nextEpisodeIndex(0, numarasiz))
        assertNull(nextEpisodeIndex(2, numarasiz))
        assertNull(nextEpisodeIndex(0, emptyList()))
    }
}
