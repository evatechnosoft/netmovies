package com.evaitec.netmovies.tv.data

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

// Cloudflare-Türkiye sorunu:
//  1) IPv6 bozuk → IPv4 önceliği.
//  2) Cloudflare cihazın ağına TR'de BLOKLU IP aralığı (188.114.x) döndürüyor;
//     w.evaitec.com'un GERÇEK kayıtları 172.67.143.235 / 104.21.55.13 çalışıyor.
//     Bu host için çalışan IP'leri PIN'le (önce onlar denensin), sonra sistemin
//     döndürdüklerini yedek olarak ekle.
val PreferIpv4Dns = object : Dns {
    private val pinned = mapOf(
        "w.evaitec.com" to listOf("172.67.143.235", "104.21.55.13"),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        pinned[hostname]?.let { ips ->
            val pins = ips.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
            val sys  = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            // Pinlenen çalışan IP'ler önce; sonra sistem sonucu (tekrarsız), IPv4 öncelikli.
            return (pins + sys).distinct().sortedByDescending { it is Inet4Address }
        }
        val all = Dns.SYSTEM.lookup(hostname)
        return all.filterIsInstance<Inet4Address>().ifEmpty { all }
    }
}
