package com.evaitec.netmovies.tv.ui

import java.util.Locale

// On-screen search keyboard: key layout and pure text/navigation rules.
// Kept free of Compose so the wrap math and text edits are unit-testable.

internal const val TUS_BOSLUK = "␣"
internal const val TUS_SIL = "⌫"
internal const val TUS_TEMIZLE = "✕"

/** Search input mode; [anahtar] is what is persisted. NORMAL hides the grid (system keyboard only). */
internal enum class KlavyeModu(val anahtar: String, val etiket: String) {
    ABC("abc", "▦  ABC"),
    QWERTY("qwerty", "⌨  QWERTY"),
    NORMAL("normal", "✎  Sistem");

    companion object {
        fun bul(anahtar: String?): KlavyeModu = entries.firstOrNull { it.anahtar == anahtar } ?: ABC
    }
}

/** One grid layout: keys row by row, [sutun] per row. Grids must be full for edge wrap. */
internal class KlavyeDuzeni(val tuslar: List<String>, val sutun: Int)

private fun tuslar(vararg satirlar: String): List<String> =
    satirlar.flatMap { satir -> satir.map { it.toString() } }

/** Turkish alphabet, digits and the three edit keys: 42 keys = full 6x7 grid. */
internal val KLAVYE_ABC = KlavyeDuzeni(
    tuslar("ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ1234567890") + listOf(TUS_BOSLUK, TUS_SIL, TUS_TEMIZLE),
    sutun = 7,
)

/** Turkish Q layout, 4x12; digits on top like a real keyboard, punctuation common in titles. */
internal val KLAVYE_QWERTY = KlavyeDuzeni(
    tuslar("1234567890") + listOf(TUS_SIL, TUS_TEMIZLE) +
        tuslar("QWERTYUIOPĞÜ", "ASDFGHJKLŞİ'", "ZXCVBNMÖÇ") + listOf(TUS_BOSLUK, "-", "."),
    sutun = 12,
)

internal fun KlavyeModu.duzen(): KlavyeDuzeni? = when (this) {
    KlavyeModu.ABC -> KLAVYE_ABC
    KlavyeModu.QWERTY -> KLAVYE_QWERTY
    KlavyeModu.NORMAL -> null
}

private val TR = Locale.forLanguageTag("tr-TR")

/** Neighbour of [index] moving by ([dx], [dy]); wraps around every edge of a full grid. */
internal fun sarmaliKomsu(index: Int, dx: Int, dy: Int, sutun: Int, adet: Int): Int {
    require(adet % sutun == 0) { "grid must be full" }
    val satir = adet / sutun
    val x = Math.floorMod(index % sutun + dx, sutun)
    val y = Math.floorMod(index / sutun + dy, satir)
    return y * sutun + x
}

/** Applies one key press to the query text. Letters go in as Turkish lowercase (İ→i, I→ı). */
internal fun tusUygula(metin: String, tus: String): String = when (tus) {
    TUS_SIL -> metin.dropLast(1)
    TUS_TEMIZLE -> ""
    // No leading or double spaces: they only make a different (empty-result) query.
    TUS_BOSLUK -> if (metin.isEmpty() || metin.endsWith(' ')) metin else "$metin "
    else -> metin + tus.lowercase(TR)
}
