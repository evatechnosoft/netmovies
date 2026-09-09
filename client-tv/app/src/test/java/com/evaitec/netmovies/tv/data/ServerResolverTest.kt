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

    // Sunucu IP'si DHCP ile kaydığında /24 taraması devreye girer: ön ekler tekrarsız,
    // her ön ek için 1..254 host, verilen port.
    @Test
    fun subnetScanCoversEveryHostOnce() {
        val hosts = ServerResolver.subnetHosts(listOf("192.168.0", "192.168.1", "192.168.0"))

        assertEquals(508, hosts.size)
        assertEquals("http://192.168.0.1:3310/", hosts.first().toString())
        assertEquals("http://192.168.0.254:3310/", hosts[253].toString())
        assertEquals("http://192.168.1.1:3310/", hosts[254].toString())
        assertEquals(hosts.size, hosts.toSet().size)
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
