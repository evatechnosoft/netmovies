package com.evaitec.netmovies.tv.data.yz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Model sahte: yalnız çıktı metninin ayrıştırılması sınanır.
class NiyetJsonTest {
  @Test
  fun tamNiyet() {
    val metin = """{"tur":"dizi","baslik":"Reacher","sezon":2,"bolum":3,"gun":null,"kanal":null}"""
    assertEquals(Niyet(tur = "dizi", baslik = "Reacher", sezon = 2, bolum = 3), NiyetJson.ayristir(metin))
  }

  @Test
  fun citliVeTirnakliSayi() {
    val metin = "```json\n{\"tur\": \"Dizi\", \"baslik\": \" Ömür Usta \", \"sezon\": \"1\", \"ek\": 5}\n```"
    assertEquals(Niyet(tur = "dizi", baslik = "Ömür Usta", sezon = 1), NiyetJson.ayristir(metin))
  }

  @Test
  fun bilinmeyenTurVeSifirBolumAtilir() {
    val metin = """{"tur":"belgesel","baslik":"Kozmos","bolum":0,"gun":"Pazar"}"""
    assertEquals(Niyet(baslik = "Kozmos", gun = "pazar"), NiyetJson.ayristir(metin))
  }

  @Test
  fun canliKanal() {
    assertEquals(
      Niyet(tur = "canli", kanal = "TRT 1"),
      NiyetJson.ayristir("""{"tur":"canli","kanal":"TRT 1"}"""),
    )
  }

  @Test
  fun bozukYadaBosNull() {
    assertNull(NiyetJson.ayristir(null))
    assertNull(NiyetJson.ayristir("Anlamadım."))
    assertNull(NiyetJson.ayristir("""{"tur": "dizi", """))
    assertNull(NiyetJson.ayristir("""{"sezon": "iki"}"""))
    assertNull(NiyetJson.ayristir("""{"baslik": "", "tur": "yok"}"""))
  }
}
