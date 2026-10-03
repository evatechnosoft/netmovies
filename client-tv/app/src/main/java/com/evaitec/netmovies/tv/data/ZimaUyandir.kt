package com.evaitec.netmovies.tv.data

import com.evaitec.netmovies.tv.BuildConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

// Sunucu (ZimaOS) gece kapanıyor; TV açılıp sunucuya ulaşamayınca Wake-on-LAN
// sihirli paketi yollar (Dean, 3 Ekim: "TV açtığımda istek gelirse açsın").
// Mi Box ile ZimaOS aynı alt ağda (192.168.1.x): yayın paketi doğrudan ulaşır.
// Ev dışında paket kimseye ulaşmaz, zararsız.
object ZimaUyandir {
    private const val ARALIK_MS = 60_000L
    private val HEDEFLER = listOf("192.168.1.255", "255.255.255.255")
    private val PORTLAR = listOf(9, 7)

    @Volatile private var sonGonderim = 0L

    /** 6×FF + MAC×16. MAC "AA:BB:..." ya da "AA-BB-..." biçiminde. */
    internal fun sihirliPaket(mac: String): ByteArray {
        val baytlar = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(baytlar.size == 6) { "MAC 6 bayt olmalı: $mac" }
        return ByteArray(6) { 0xFF.toByte() } + List(16) { baytlar }.flatten().toByteArray()
    }

    /** Son [ARALIK_MS] içinde paket gitti mi — ekranda "uyandırılıyor" demek için. */
    fun yakinda(): Boolean = System.currentTimeMillis() - sonGonderim < 4 * ARALIK_MS

    /** Ağ iş parçacığından çağrılır. Dakikada en fazla bir kez gönderir. */
    fun gonder() {
        val simdi = System.currentTimeMillis()
        if (simdi - sonGonderim < ARALIK_MS) return
        sonGonderim = simdi
        val paket = sihirliPaket(BuildConfig.WOL_MAC)
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                HEDEFLER.forEach { h ->
                    val adres = InetAddress.getByName(h)
                    PORTLAR.forEach { p -> s.send(DatagramPacket(paket, paket.size, adres, p)) }
                }
            }
        }.onSuccess { PlaybackLog.info("sunucu", "ZimaOS'a uyandırma paketi gönderildi") }
            .onFailure { PlaybackLog.info("sunucu", "uyandırma paketi gönderilemedi: ${it.message}") }
    }
}
