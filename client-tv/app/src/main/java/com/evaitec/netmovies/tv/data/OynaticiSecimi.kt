package com.evaitec.netmovies.tv.data

import android.content.Context

/**
 * Hangi oynatıcı açılsın: `PlayerScreen2` (0.9.84'ten itibaren varsayılan) mi, eski `PlayerScreen` (yedek) mi.
 * Cihazda kalır; Ayarlar'daki "Oynatıcı" satırı değiştirir. Anahtar yeni: eski "kapalı" seçimi
 * bir haftalık denemeyi engellemesin.
 */
object OynaticiSecimi {
    private const val PREFS = "player"
    private const val KEY = "yeni_oynatici_v2"

    fun yeniMi(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun ayarla(context: Context, yeni: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, yeni).apply()
    }
}
