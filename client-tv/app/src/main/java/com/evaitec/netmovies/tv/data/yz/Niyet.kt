package com.evaitec.netmovies.tv.data.yz

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serbest cümleden çıkarılan yapılandırılmış arama niyeti. Model yalnız bunu üretir;
 * eşleştirmeyi kod yapar (bkz. docs/YZ-PLAN.md). Her alan boş olabilir.
 *
 * tur: "film" | "dizi" | "canli" — başka değer null'a düşer.
 * gun: "bugun", "yarin", "pazar" gibi küçük harf, Türkçe karaktersiz.
 */
@Serializable
data class Niyet(
  val tur: String? = null,
  val baslik: String? = null,
  val sezon: Int? = null,
  val bolum: Int? = null,
  val gun: String? = null,
  val kanal: String? = null,
)

object NiyetJson {
  private val TURLER = setOf("film", "dizi", "canli")

  // Küçük model sayıyı tırnaklı yazabiliyor ("sezon": "2"), fazladan alan ekleyebiliyor.
  private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

  /** Model çıktısından niyet. Çözülemezse ya da içi boşsa null: çağıran kurallara düşer. */
  fun ayristir(metin: String?): Niyet? {
    if (metin == null) return null
    // Model ```json çitiyle sarabiliyor — ilk { ... son } arası kesilir.
    val bas = metin.indexOf('{')
    val son = metin.lastIndexOf('}')
    if (bas < 0 || son <= bas) return null

    val ham = try {
      json.decodeFromString(Niyet.serializer(), metin.substring(bas, son + 1))
    } catch (_: SerializationException) {
      return null
    } catch (_: IllegalArgumentException) {
      return null
    }

    val niyet = Niyet(
      tur = ham.tur?.trim()?.lowercase()?.takeIf { it in TURLER },
      baslik = ham.baslik?.trim()?.takeIf { it.isNotEmpty() },
      sezon = ham.sezon?.takeIf { it > 0 },
      bolum = ham.bolum?.takeIf { it > 0 },
      gun = ham.gun?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
      kanal = ham.kanal?.trim()?.takeIf { it.isNotEmpty() },
    )
    return niyet.takeIf { it != Niyet() }
  }
}
