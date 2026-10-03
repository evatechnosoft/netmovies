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

  @Test(expected = IllegalArgumentException::class)
  fun eksikMacReddedilir() {
    ZimaUyandir.sihirliPaket("38:14:28")
  }
}
