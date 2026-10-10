package com.evaitec.netmovies.tv.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaylasTest {
    @Test fun `link is pulled out of shared text`() {
        assertEquals("https://a.b/c?d=1", paylasilanAdres("Bak şuna https://a.b/c?d=1 güzel"))
        assertEquals(null, paylasilanAdres("bağlantısız metin"))
        assertEquals(null, paylasilanAdres(null))
    }

    @Test fun `link becomes web, bare text becomes search, empty is nothing`() {
        val web = paylasimKomutu("Daha 17 https://youtu.be/x")!!
        assertEquals("web", web.getString("type"))
        assertEquals("https://youtu.be/x", web.getString("url"))
        val ara = paylasimKomutu("  Reacher 3. sezon ")!!
        assertEquals("text", ara.getString("type"))
        assertEquals("Reacher 3. sezon", ara.getString("text"))
        assertEquals(true, ara.getBoolean("submit"))
        assertNull(paylasimKomutu("   "))
    }

    @Test fun `toast follows what the server decided`() {
        assertEquals("TV'de oynatıcıda açılıyor", paylasimSonucu("""{"result":{"ok":true,"as":"play"}}""", "web"))
        assertEquals("TV'de sayfa açılıyor", paylasimSonucu("""{"result":{"ok":true,"as":"web"}}""", "web"))
        assertEquals("TV'de aranıyor", paylasimSonucu("""{"result":{"ok":true}}""", "text"))
        assertEquals("TV'ye yazma kapalı", paylasimSonucu("""{"result":{"ok":false,"error":"TV'ye yazma kapalı"}}""", "text"))
        assertEquals("TV'ye ulaşılamadı", paylasimSonucu(null, "web"))
    }
}
