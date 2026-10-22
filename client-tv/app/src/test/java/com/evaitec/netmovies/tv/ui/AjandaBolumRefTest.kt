package com.evaitec.netmovies.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AjandaBolumRefTest {
  @Test
  fun bolumKimligi() {
    assertEquals("S2B3", bolumRef("2. sezon 3. bölüm"))
    assertEquals("S12B10", bolumRef("12. sezon 10. bölüm"))
    assertEquals("", bolumRef("Vizyon"))
  }
}
