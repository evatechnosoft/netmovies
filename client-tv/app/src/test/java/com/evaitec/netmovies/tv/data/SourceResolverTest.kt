package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Zincir ve dil kuralı sunucuda (stream/tests/test_language_priority.py).
// İstemci tarafında yalnız SUNUM doğrulanır: etiketi doğru basıyor mu.
class SourceResolverTest {

    @Test
    fun labelUsesServerProvidedLanguage() {
        val link = StreamLink(name = "DiziBox · Oynatıcı", language = LanguageTag(rank = 0, label = "Türkçe dublaj"))

        assertEquals("DiziBox · Türkçe dublaj", languageLabel(link))
    }

    @Test
    fun labelFallsBackWhenServerOmitsLanguage() {
        val link = StreamLink(name = "RecTV · Oynatıcı")

        assertEquals("RecTV · dil bilinmiyor", languageLabel(link))
    }

    @Test
    fun labelSurvivesEmptyName() {
        assertEquals("Kaynak · dil bilinmiyor", languageLabel(StreamLink()))
    }

    @Test
    fun subtitleLanguageIsDetectedForPlayerTracks() {
        assertEquals("tr", guessSubtitleLang("Türkçe"))
        assertEquals("en", guessSubtitleLang("English"))
        assertEquals("und", guessSubtitleLang("?"))
    }
}
