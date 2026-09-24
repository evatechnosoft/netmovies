package com.evaitec.netmovies.tv.data

import android.content.Context

/**
 * Hangi oynatıcı açılsın: eski `PlayerScreen` (varsayılan, yedek) mi, `PlayerScreen2` mi.
 * Cihazda kalır; Ayarlar'daki "Yeni oynatıcı (deneme)" satırı değiştirir.
 */
object OynaticiSecimi {
    private const val PREFS = "player"
    private const val KEY = "yeni_oynatici"

    fun yeniMi(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun ayarla(context: Context, yeni: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, yeni).apply()
    }
}
