package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceResolverTest {

    @Test
    fun dubbedSourceOutranksSubtitled() {
        val dubbed = StreamLink(name = "DiziBox · Türkçe Dublaj")
        val subbed = StreamLink(name = "DiziBox · Türkçe Altyazılı")

        assertEquals(0, languageRank(dubbed))
        assertEquals(1, languageRank(subbed))
    }

    @Test
    fun turkishSubtitleTrackCountsAsSubtitled() {
        val link = StreamLink(name = "Dizilla · Oynatıcı", subtitles = listOf(Subtitle(name = "Türkçe", url = "x.vtt")))

        assertEquals(1, languageRank(link))
    }

    @Test
    fun unmarkedSourceRanksLast() {
        assertEquals(2, languageRank(StreamLink(name = "RecTV · Oynatıcı")))
    }

    @Test
    fun queueIsOrderedDubbedThenSubtitledThenRest() {
        val queue = listOf(
            StreamLink(name = "A · Oynatıcı"),
            StreamLink(name = "B · Türkçe Altyazılı"),
            StreamLink(name = "C · Türkçe Dublaj"),
        )

        assertEquals(listOf("C · Türkçe Dublaj", "B · Türkçe Altyazılı", "A · Oynatıcı"), orderByLanguage(queue).map { it.name })
    }

    @Test
    fun equalRankKeepsOriginalOrder() {
        val queue = listOf(
            StreamLink(name = "İlk · Türkçe Dublaj"),
            StreamLink(name = "İkinci · Türkçe Dublaj"),
        )

        assertEquals(listOf("İlk · Türkçe Dublaj", "İkinci · Türkçe Dublaj"), orderByLanguage(queue).map { it.name })
    }

    @Test
    fun searchTitleDropsSiteNoise() {
        assertEquals("Kara Şövalye", searchableTitle("Kara Şövalye izle Türkçe Dublaj 1080p"))
    }

    @Test
    fun selectedPluginIsNotSearchedTwice() {
        assertEquals(false, alternativePlugins("DiziBox").contains("DiziBox"))
    }
}
