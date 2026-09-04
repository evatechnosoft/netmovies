package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Yerel sunucu keşfi: LOCAL_URL artık tek adres değil, virgülle ayrık aday listesi
// (ev iki ağ kullanıyor: 192.168.1.x ve 192.168.0.x). Ağ yoklaması burada test
// edilmez — yalnız listenin doğru ayrıştığı sabitlenir.
class ServerResolverTest {

    @Test
    fun singleAddressStillParses() {
        val parsed = ServerResolver.localCandidates("http://192.168.1.185:3310")

        assertEquals(listOf("http://192.168.1.185:3310/"), parsed.map { it.toString() })
    }

    @Test
    fun multipleAddressesParseInOrder() {
        val parsed = ServerResolver.localCandidates(
            "http://192.168.1.185:3310,http://192.168.0.185:3310"
        )

        assertEquals(
            listOf("http://192.168.1.185:3310/", "http://192.168.0.185:3310/"),
            parsed.map { it.toString() },
        )
    }

    @Test
    fun blankAndBrokenEntriesAreDropped() {
        val parsed = ServerResolver.localCandidates(
            " http://192.168.1.185:3310 , , bozuk-adres ,http://192.168.0.185:3310"
        )

        assertEquals(
            listOf("http://192.168.1.185:3310/", "http://192.168.0.185:3310/"),
            parsed.map { it.toString() },
        )
    }
}
