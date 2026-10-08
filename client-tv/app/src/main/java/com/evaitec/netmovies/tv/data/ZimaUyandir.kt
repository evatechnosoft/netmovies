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

    // Gece 00:00 kapanması tutmuyordu (Dean, 5 Ekim): ZimaOS 00:00'da kapanıp
    // 88 sn sonra yeniden açılıyordu, elle kapatınca da yine. Mi Box kapalıyken
    // bile — telefondaki aynı uygulamanın dakikalık widget'ı sunucuyu bulamayınca
    // paket yolluyordu. Paket artık yalnız uygulama EKRANDAYKEN (kullanıcı bakıyor)
    // ve gece yarısı ile sabah 07:00 arası DIŞINDA gider; sabah açılışı BIOS'ta.
    @Volatile var onPlanda = false
    private const val SESSIZ_BAS = 0
    private const val SESSIZ_BIT = 7

    /** Paket gönderilebilir mi: ekranda + (sessiz saatlerin dışında ya da elle istendi). */
    internal fun izinli(onPlanda: Boolean, saat: Int, elle: Boolean = false): Boolean =
        onPlanda && (elle || saat !in SESSIZ_BAS until SESSIZ_BIT)

    /** Şu an sessiz saatlerde mi — hata ekranı "gece kapalı" desin diye. */
    fun geceMi(): Boolean = java.time.LocalTime.now().hour in SESSIZ_BAS until SESSIZ_BIT

    // TV 07:00'dan önce açılınca paket sessizce engelleniyor, otomatik yeniden
    // deneme de yalnız paket gittiyse çalıştığı için ekran hatada kalıyordu
    // (Dean, 8 Ekim: "göndermedi wol"). "Tekrar dene" açık kullanıcı isteğidir:
    // kısa bir pencere için saat engelini ve dakikalık sınırı kaldırır.
    @Volatile private var elleBitis = 0L

    fun elleIste() {
        elleBitis = System.currentTimeMillis() + ARALIK_MS
        sonGonderim = 0L
    }

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
        val elle = System.currentTimeMillis() < elleBitis
        if (!izinli(onPlanda, java.time.LocalTime.now().hour, elle)) return
        val simdi = System.currentTimeMillis()
        if (simdi - sonGonderim < ARALIK_MS) return
        sonGonderim = simdi
        elleBitis = 0L
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
