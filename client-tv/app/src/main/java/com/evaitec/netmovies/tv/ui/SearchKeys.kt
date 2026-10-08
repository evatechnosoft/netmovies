package com.evaitec.netmovies.tv.ui

import java.util.Locale

// On-screen search keyboard: key layout and pure text/navigation rules.
// Kept free of Compose so the wrap math and text edits are unit-testable.

internal const val TUS_BOSLUK = "␣"
internal const val TUS_SIL = "⌫"
internal const val TUS_TEMIZLE = "✕"

internal const val KLAVYE_SUTUN = 7

/** Turkish alphabet, digits and the three edit keys: 42 keys = full 6x7 grid. */
internal val KLAVYE_TUSLARI: List<String> =
    "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ1234567890".map { it.toString() } +
        listOf(TUS_BOSLUK, TUS_SIL, TUS_TEMIZLE)

private val TR = Locale.forLanguageTag("tr-TR")

/** Neighbour of [index] moving by ([dx], [dy]); wraps around every edge of a full grid. */
internal fun sarmaliKomsu(index: Int, dx: Int, dy: Int, sutun: Int = KLAVYE_SUTUN, adet: Int = KLAVYE_TUSLARI.size): Int {
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
