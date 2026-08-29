package com.evaitec.netmovies.tv.data

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

// Bazı Android TV / Mibox ağlarında IPv6 çalışmıyor. w.evaitec.com (Cloudflare)
// IPv6'ya çözülünce "Failed to connect to w.evaitec.com/2a06:98c1:...:443" hatası
// veriyordu. IPv4 adreslerini öne al; hiç IPv4 yoksa sistemin tüm sonucunu kullan.
// Not: okhttp3.Dns Kotlin interface'i olduğundan lambda değil anonim object gerekir.
val PreferIpv4Dns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        return all.filterIsInstance<Inet4Address>().ifEmpty { all }
    }
}
