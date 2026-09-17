package com.evaitec.netmovies.tv.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget'ta görünen üç alanın kaynağı `/api/v1/remote/status`. Sunucu ulaşılamaz
 * olabilir, TV kapalı olabilir, açık ama boş olabilir — üçü ayrı yazı gösterir.
 */
class RemoteWidgetDurumTest {

    @Test
    fun `sunucu susarsa ulasilamadi yazar`() {
        val d = RemoteWidget.durumdan(null)
        assertEquals("sunucuya ulaşılamadı", d.alt)
        assertFalse(d.oynuyor)
    }

    @Test
    fun `bozuk govde cokmez`() {
        assertEquals("sunucuya ulaşılamadı", RemoteWidget.durumdan("{bu json degil").alt)
    }

    @Test
    fun `tv kapaliysa kapali yazar`() {
        val d = RemoteWidget.durumdan("""{"result":{"tv_online":false}}""")
        assertEquals("kapalı", d.alt)
    }

    @Test
    fun `tv acik ama bos`() {
        val d = RemoteWidget.durumdan("""{"result":{"tv_online":true}}""")
        assertEquals("açık · bir şey oynamıyor", d.alt)
    }

    @Test
    fun `oynayan icerik baslik ve sureyi verir`() {
        val d = RemoteWidget.durumdan(
            """{"result":{"tv_online":true,"now_playing":
               {"title":"Haysiyet","position":4868.4,"duration":8788.9,"playing":true}}}""",
        )
        assertEquals("Haysiyet", d.baslik)
        assertEquals("1:21:08  /  2:26:28", d.alt)
        assertTrue(d.oynuyor)
    }

    @Test
    fun `sure bilinmiyorsa oynuyor mu yazar`() {
        val d = RemoteWidget.durumdan(
            """{"result":{"tv_online":true,"now_playing":{"title":"Canlı","playing":false}}}""",
        )
        assertEquals("duraklatıldı", d.alt)
    }

    @Test
    fun `devam et serisi sunucunun sorgusunu uretir`() {
        val sorgular = RemoteWidget.devamSorgulari(
            """{"result":[
               {"title":"Haysiyet","plugin":"DiziMom","poster":"p1",
                "content_url":"https://www.dizimom.diy/diziler/haysiyet-izle/"},
               {"title":"Reacher","plugin":"DiziPal","poster":"p2",
                "content_url":"https://dizipal.com/reacher"}]}""",
        )
        assertEquals(2, sorgular.size)
        assertTrue(sorgular[0].startsWith("plugin=DiziMom&url=https%3A%2F%2Fwww.dizimom.diy"))
        // episode=0 = "kaldigi yerden"; sunucu kaydi kendi buluyor.
        assertTrue(sorgular[0].endsWith("&episode=0"))
        assertTrue(sorgular[1].contains("title=Reacher"))
    }

    @Test
    fun `eksik alanli kayit serit disi kalir`() {
        val sorgular = RemoteWidget.devamSorgulari(
            """{"result":[{"title":"Yok","plugin":"DiziPal"},
                          {"title":"Var","plugin":"DiziPal","content_url":"https://x/y"}]}""",
        )
        assertEquals(1, sorgular.size)
        assertTrue(sorgular[0].contains("title=Var"))
    }

    @Test
    fun `serit en fazla uc kart tasir`() {
        val kayit = """{"title":"A","plugin":"P","content_url":"https://x/a"}"""
        val sorgular = RemoteWidget.devamSorgulari("""{"result":[$kayit,$kayit,$kayit,$kayit,$kayit]}""")
        assertEquals(3, sorgular.size)
    }

    @Test
    fun `bozuk serit govdesi bos liste verir`() {
        assertEquals(0, RemoteWidget.devamSorgulari(null).size)
        assertEquals(0, RemoteWidget.devamSorgulari("{degil").size)
        assertEquals(0, RemoteWidget.devamSorgulari("""{"result":"dizi degil"}""").size)
    }

    @Test
    fun `sure bicimi saat esiginde degisir`() {
        assertEquals("0:09", RemoteWidget.sureMetni(9))
        assertEquals("59:59", RemoteWidget.sureMetni(3599))
        assertEquals("1:00:00", RemoteWidget.sureMetni(3600))
        assertEquals("0:00", RemoteWidget.sureMetni(-5))
    }
}
