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

  @Test
  fun izlemediklerimBicimi() {
    // /unwatched "S7B1 · Bölüm adı" yazar; tıklayınca doğru bölüm açılmalı.
    assertEquals("S7B1", bolumRef("S7B1 · Yeni Görev"))
    assertEquals("S2B4", bolumRef("S2B4"))
  }
}
