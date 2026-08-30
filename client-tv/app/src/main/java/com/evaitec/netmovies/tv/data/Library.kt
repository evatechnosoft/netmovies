package com.evaitec.netmovies.tv.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Yerel kitaplık: Favoriler + İzlenenler (izleme geçmişi). Sunucusuz, cihazda kalıcı
// (SharedPreferences + JSON). Compose-observable (mutableStateList) → satırlar anında güncellenir.
class Library(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("netmovies_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val favorites = mutableStateListOf<MediaItem>()
    val watched = mutableStateListOf<MediaItem>()

    init {
        favorites.addAll(read(KEY_FAV))
        watched.addAll(read(KEY_WATCHED))
    }

    private fun read(key: String): List<MediaItem> =
        prefs.getString(key, null)?.let {
            runCatching { json.decodeFromString<List<MediaItem>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    private fun persist(key: String, list: List<MediaItem>) {
        runCatching { prefs.edit().putString(key, json.encodeToString(list)).apply() }
    }

    // Aynı içerik: plugin + url eşleşmesi.
    private fun sameItem(a: MediaItem, b: MediaItem) = a.plugin == b.plugin && a.url == b.url

    fun isFavorite(item: MediaItem): Boolean = favorites.any { sameItem(it, item) }

    fun toggleFavorite(item: MediaItem) {
        val idx = favorites.indexOfFirst { sameItem(it, item) }
        if (idx >= 0) favorites.removeAt(idx) else favorites.add(0, item)
        persist(KEY_FAV, favorites.toList())
    }

    // Oynatınca çağrılır: en öne al, tekrarı sil, ~30 ile sınırla.
    fun addWatched(item: MediaItem) {
        watched.removeAll { sameItem(it, item) }
        watched.add(0, item)
        while (watched.size > MAX_WATCHED) watched.removeAt(watched.lastIndex)
        persist(KEY_WATCHED, watched.toList())
    }

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_WATCHED = "watched"
        const val MAX_WATCHED = 30
    }
}
