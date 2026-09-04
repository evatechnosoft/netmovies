package com.evaitec.netmovies.tv.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

// Kitaplık: Favoriler + Devam Et. Kaynak SUNUCUDAKİ SQLite'tır
// (stream/Public/Home/Libs/watch_store.py) → TV, telefon ve web aynı listeyi görür.
// Cihazdaki SharedPreferences yalnızca ÖNBELLEK: sunucu erişilemezken ekran boş
// kalmasın diye. Sunucu yanıt verirse otorite odur.
class Library(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("netmovies_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val favorites = mutableStateListOf<MediaItem>()
    /** Devam Et — sunucudaki tamamlanmamış izlemeler (en son izlenen üstte). */
    val watched = mutableStateListOf<MediaItem>()
    /** url → izlenen oran (0..1). Poster üstündeki ince ilerleme çubuğu için. */
    val progress = mutableStateMapOf<String, Float>()

    init {
        favorites.addAll(read(KEY_FAV))
        watched.addAll(read(KEY_WATCHED))
        sync()
    }

    /** Sunucudan tazeler. Ağ yoksa önbellek olduğu gibi kalır. */
    fun sync() {
        scope.launch {
            pushLocalFavoritesOnce()

            runCatching { Network.api.favorites().result }
                .onSuccess { rows -> replace(favorites, rows.map(::toItem), KEY_FAV) }

            runCatching { Network.api.continueWatching(limit = 30).result }
                .onSuccess { rows ->
                    replace(watched, rows.map(::toItem), KEY_WATCHED)
                    progress.clear()
                    rows.forEach { row ->
                        val ratio = if (row.durationSeconds > 0)
                            (row.positionSeconds / row.durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
                        if (ratio > 0f) progress[encodedUrl(row.contentUrl)] = ratio
                    }
                }
        }
    }

    // Sunucu senkronu gelmeden önce cihazda birikmiş favoriler kaybolmasın: bir kez
    // yukarı taşınır. `addFavorite` idempotent (toggle olsaydı kayıtlıyı silerdi).
    private suspend fun pushLocalFavoritesOnce() {
        if (prefs.getBoolean(KEY_PUSHED, false) || favorites.isEmpty()) return
        var allOk = true
        favorites.forEach { item ->
            val ok = runCatching {
                Network.api.addFavorite(
                    title = item.title.orEmpty(),
                    plugin = item.plugin,
                    poster = item.poster.orEmpty(),
                    contentUrl = rawUrl(item.url),
                ).result.ok
            }.getOrDefault(false)
            if (!ok) allOk = false
        }
        // Yarım kalan taşıma bayrağı yakmaz; sonraki açılışta tekrar denenir.
        if (allOk) prefs.edit().putBoolean(KEY_PUSHED, true).apply()
    }

    private fun replace(target: MutableList<MediaItem>, items: List<MediaItem>, key: String) {
        target.clear()
        target.addAll(items)
        persist(key, items)
    }

    private fun toItem(row: ProgressRow) = MediaItem(
        plugin = row.plugin,
        title = row.title,
        url = encodedUrl(row.contentUrl),
        poster = row.poster.takeIf { it.isNotBlank() },
    )

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
        scope.launch {
            runCatching {
                Network.api.toggleFavorite(
                    title = item.title.orEmpty(),
                    plugin = item.plugin,
                    poster = item.poster.orEmpty(),
                    contentUrl = rawUrl(item.url),
                )
            }
        }
    }

    /**
     * İzleme konumunu sunucuya yazar (oynatıcıdan periyodik + çıkışta çağrılır).
     * Library'nin kendi scope'unda koşar: ekran kapansa da istek tamamlanır.
     * 5 sn altı kaydedilmez — yanlışlıkla açılıp kapatılan içerik listeyi kirletmesin.
     */
    fun saveProgress(item: MediaItem, positionSeconds: Double, durationSeconds: Double, episode: Int = 0) {
        if (positionSeconds < 5.0) return
        scope.launch {
            runCatching {
                Network.api.saveProgress(
                    title = item.title.orEmpty(),
                    plugin = item.plugin,
                    poster = item.poster.orEmpty(),
                    contentUrl = rawUrl(item.url),
                    episode = if (episode > 0) episode.toString() else "",
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                )
            }
        }
    }

    /** Kayıtlı konumu okur (kaldığın yerden devam). Kayıt/ağ yoksa null. */
    suspend fun loadProgress(title: String): ProgressRow? =
        runCatching { Network.api.getProgress(title).result }.getOrNull()

    /** Oynatınca çağrılır: Devam Et rafında hemen görünsün (sunucu kaydı oynatıcıda). */
    fun addWatched(item: MediaItem) {
        watched.removeAll { sameItem(it, item) }
        watched.add(0, item)
        while (watched.size > MAX_WATCHED) watched.removeAt(watched.lastIndex)
        persist(KEY_WATCHED, watched.toList())
    }

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_WATCHED = "watched"
        const val KEY_PUSHED = "favorites_pushed_v1"
        const val MAX_WATCHED = 30
    }
}

// MediaItem.url quote_plus KODLU gelir; sunucu `content_url`'ü HAM tutar (web böyle
// yazıyor). Dönüşüm tek yerde olsun diye burada — iki tarafta da kayıt aynı içeriğe
// düşer, yoksa TV'nin kaydettiğini web açamaz.
fun rawUrl(encoded: String): String =
    if (encoded.contains("://")) encoded
    else runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)

fun encodedUrl(raw: String): String =
    if (raw.isBlank() || !raw.contains("://")) raw
    else runCatching { URLEncoder.encode(raw, "UTF-8") }.getOrDefault(raw)
