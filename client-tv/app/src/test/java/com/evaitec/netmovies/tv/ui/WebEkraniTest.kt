package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebEkraniTest {
    @Test fun `typed address opens, sentence is typed into the page`() {
        assertEquals("https://hurriyet.com.tr", webAdresiMi("hurriyet.com.tr"))
        assertEquals("https://www.ekşi.com/a?b=1", webAdresiMi("www.ekşi.com/a?b=1"))
        assertEquals("http://x.org", webAdresiMi(" http://x.org "))
        assertNull(webAdresiMi("daha 17 izle"))
        assertNull(webAdresiMi("reacher"))
        assertNull(webAdresiMi("3.14"))
    }

    @Test fun `ad hosts and subdomains are blocked, others pass`() {
        assertTrue(reklamKonagi("securepubads.g.doubleclick.net"))
        assertTrue(reklamKonagi("popads.net"))
        assertFalse(reklamKonagi("notdoubleclick.net"))
        assertFalse(reklamKonagi("www.youtube.com"))
        assertFalse(reklamKonagi(null))
    }

    @Test fun `script carries text safely quoted`() {
        val js = yaziBetigi("a\"b'</script>", true)
        assertTrue(js.contains("""("a\"b'<\/script>",true)"""))
    }
}
