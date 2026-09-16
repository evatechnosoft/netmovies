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
}
