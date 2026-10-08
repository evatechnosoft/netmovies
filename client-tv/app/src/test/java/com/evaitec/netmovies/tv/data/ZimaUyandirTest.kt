package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ZimaUyandirTest {
  @Test
  fun sihirliPaketBicimi() {
    val p = ZimaUyandir.sihirliPaket("38:14:28:35:9A:AE")
    assertEquals(102, p.size)
    assertEquals((0 until 6).map { 0xFF.toByte() }, p.take(6))
    val mac = listOf(0x38, 0x14, 0x28, 0x35, 0x9A, 0xAE).map { it.toByte() }
    (0 until 16).forEach { i -> assertEquals(mac, p.drop(6 + i * 6).take(6)) }
  }

  @Test
  fun yalnizEkrandaVeGeceDisinda() {
    // Widget / arka plan: hiç gönderilmez (gece kapanmasını bozan buydu).
    assertEquals(false, ZimaUyandir.izinli(onPlanda = false, saat = 20))
    // Ekranda ama gece yarısı–07:00: sunucu bilerek kapalı.
    assertEquals(false, ZimaUyandir.izinli(onPlanda = true, saat = 0))
    assertEquals(false, ZimaUyandir.izinli(onPlanda = true, saat = 6))
    assertEquals(true, ZimaUyandir.izinli(onPlanda = true, saat = 7))
    assertEquals(true, ZimaUyandir.izinli(onPlanda = true, saat = 23))
    // "Tekrar dene" gece engelini aşar, ekranda olma şartını aşmaz.
    assertEquals(true, ZimaUyandir.izinli(onPlanda = true, saat = 6, elle = true))
    assertEquals(false, ZimaUyandir.izinli(onPlanda = false, saat = 6, elle = true))
  }

  @Test(expected = IllegalArgumentException::class)
  fun eksikMacReddedilir() {
    ZimaUyandir.sihirliPaket("38:14:28")
  }
}
