package com.evaitec.netmovies.tv.ui

import com.evaitec.netmovies.tv.data.ShowSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakvimSatirlariTest {
  @Test
  fun sonVeSiradakiBolum() {
    val s = takvimSatirlari(
      ShowSchedule(
        status = "Returning Series", last_season = 2, last_episode = 5, last_date = "2026-09-24",
        next_season = 2, next_episode = 6, next_name = "Evrenin Kökeni", next_date = "2026-10-01",
      ),
    )
    assertEquals(2, s.size)
    assertTrue(s[0].startsWith("Son bölüm: S2B5 · 24"))
    assertTrue(s[1].startsWith("Sıradaki: S2B6 «Evrenin Kökeni» · 1 "))
  }

  @Test
  fun bitmisDizi() {
    val s = takvimSatirlari(ShowSchedule(status = "Ended", last_season = 3, last_episode = 13, last_date = "2017-08-25"))
    assertEquals("Dizi tamamlandı", s.last())
  }

  @Test
  fun bilgiYoksaBos() {
    assertTrue(takvimSatirlari(null).isEmpty())
  }
}
