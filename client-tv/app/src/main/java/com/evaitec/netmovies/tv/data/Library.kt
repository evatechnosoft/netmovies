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
    /** İzlenecekler — elle işaretlenen, henüz başlanmamış içerikler. */
    val izlenecek = mutableStateListOf<MediaItem>()
    /** Takip ettiklerim — yeni bölümü çıkınca ajandada görünen diziler. */
    val takip = mutableStateListOf<MediaItem>()
    /** url → izlenen oran (0..1). Poster üstündeki ince ilerleme çubuğu için. */
    val progress = mutableStateMapOf<String, Float>()

    init {
        favorites.addAll(read(KEY_FAV))
        watched.addAll(read(KEY_WATCHED))
        izlenecek.addAll(read(KEY_IZLENECEK))
        takip.addAll(read(KEY_TAKIP))
        sync()
    }

    /** Sunucudan tazeler. Ağ yoksa önbellek olduğu gibi kalır. */
    fun sync() {
        scope.launch {
            pushLocalFavoritesOnce()

            runCatching { Network.api.favorites().result }
                .onSuccess { rows -> replace(favorites, rows.map(::toItem), KEY_FAV) }

            runCatching { Network.api.userList(LISTE_IZLENECEK).result }
                .onSuccess { rows -> replace(izlenecek, rows.map(::toItem), KEY_IZLENECEK) }
            runCatching { Network.api.userList(LISTE_TAKIP).result }
                .onSuccess { rows -> replace(takip, rows.map(::toItem), KEY_TAKIP) }

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

    fun inIzlenecek(item: MediaItem): Boolean = izlenecek.any { sameItem(it, item) }
    fun inTakip(item: MediaItem): Boolean = takip.any { sameItem(it, item) }

    /** Sunucudaki kullanıcı listesine ekler/çıkarır. Yerel liste hemen güncellenir:
     *  raf ve menü satırı beklemeden doğru durumu gösterir. */
    fun toggleListe(item: MediaItem, liste: String) {
        val (hedef, anahtar) = when (liste) {
            LISTE_IZLENECEK -> izlenecek to KEY_IZLENECEK
            LISTE_TAKIP     -> takip to KEY_TAKIP
            else            -> return
        }
        val idx = hedef.indexOfFirst { sameItem(it, item) }
        if (idx >= 0) hedef.removeAt(idx) else hedef.add(0, item)
        persist(anahtar, hedef.toList())
        scope.launch {
            runCatching {
                Network.api.toggleList(
                    listName = liste,
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
    fun saveProgress(
        item: MediaItem,
        positionSeconds: Double,
        durationSeconds: Double,
        episodeRef: String = "",
        isSerie: Boolean = false,
    ) {
        if (positionSeconds < 5.0) return
        val type = mediaType(isSerie)
        scope.launch {
            runCatching {
                Network.api.saveProgress(
                    title = item.title.orEmpty(),
                    plugin = item.plugin,
                    poster = item.poster.orEmpty(),
                    mediaType = type,
                    contentUrl = rawUrl(item.url),
                    episode = episodeRef,
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                )
            }
        }
    }

    /** Kayıtlı konumu okur (kaldığın yerden devam). Kayıt/ağ yoksa null. */
    suspend fun loadProgress(title: String, isSerie: Boolean = false): ProgressRow? =
        runCatching { Network.api.getProgress(title, mediaType(isSerie)).result }.getOrNull()

    /** Oynatınca çağrılır: Devam Et rafında hemen görünsün (sunucu kaydı oynatıcıda). */
    fun addWatched(item: MediaItem) {
        watched.removeAll { sameItem(it, item) }
        watched.add(0, item)
        while (watched.size > MAX_WATCHED) watched.removeAt(watched.lastIndex)
        persist(KEY_WATCHED, watched.toList())
    }

    companion object {
        const val KEY_FAV = "favorites"
        const val KEY_WATCHED = "watched"
        const val KEY_IZLENECEK = "izlenecek"
        const val KEY_TAKIP = "takip"
        // Sunucudaki liste adları (watch_store.ALLOWED_LISTS).
        const val LISTE_IZLENECEK = "izlenecek"
        const val LISTE_TAKIP = "takip"
        const val KEY_PUSHED = "favorites_pushed_v1"
        const val MAX_WATCHED = 30
    }
}

// ---- Bölüm kimliği ----
// `episode` sütununa uzun süre LİSTE İNDEKSİ yazıldı. İndeks sağlayıcıya ve o günkü
// listeye bağlı: Dizilla'nın sızıntılı listesinde 123'üncü sıra kaydedildi, liste
// 32 bölüme düzelince kayıt "123. bölüm" diye ekrana bastı (Dean, 16 Eylül). Web
// tarafı (`central-progress.js`) aynı sütuna "S4 E8" yazıyordu — iki taraf aynı
// alanı iki ayrı anlamda kullanıyordu, web'de izlenen bölüm TV'de eşleşmiyordu.
// Artık kayıt SEZON+BÖLÜM numarası taşır; indeks yalnız numara bilinmiyorsa kalır.

/** Kayda yazılacak bölüm kimliği: numara varsa "S4B8", yoksa ham indeks. */
fun episodeRef(season: Int?, episode: Int?, index: Int): String =
    if (episode != null) "S${season ?: 1}B$episode" else index.toString()

/** "S4B8" / "S4 E8" / "s4e8" → (sezon, bölüm). Düz sayı ya da tanınmayan metin → null. */
fun parseEpisodeRef(ref: String): Pair<Int, Int>? {
    val m = Regex("""[Ss](\d+)\s*[BbEe](\d+)""").find(ref.trim()) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2].toInt()
}

/**
 * Kaydın listedeki karşılığı. Numaralı kayıt sezon+bölüm ile aranır (sağlayıcı
 * değişse de doğru bölüm bulunur). Eski indeks kayıtları sınır içindeyse indeks
 * sayılır; liste küçüldüyse `null` döner — "123. bölüm" diye bir şey gösterilmez.
 */
fun episodeIndexOf(ref: String, episodes: List<EpisodeItem>): Int? {
    if (ref.isBlank() || episodes.isEmpty()) return null
    parseEpisodeRef(ref)?.let { (s, e) ->
        return episodes.indexOfFirst { it.season == s && it.episode == e }.takeIf { it >= 0 }
    }
    return ref.toIntOrNull()?.takeIf { it in episodes.indices }
}

// `content_key` sunucuda başlık + media_type'tan türer; web `serie`/`movie` yazıyor
// (central-progress.js). TV boş gönderirse AYNI film iki ayrı kayda düşer
// ("gorge" vs "gorge|movie") ve cihazlar arası devam etme kopar.
private fun mediaType(isSerie: Boolean) = if (isSerie) "serie" else "movie"

// MediaItem.url quote_plus KODLU gelir; sunucu `content_url`'ü HAM tutar (web böyle
// yazıyor). Dönüşüm tek yerde olsun diye burada — iki tarafta da kayıt aynı içeriğe
// düşer, yoksa TV'nin kaydettiğini web açamaz.
fun rawUrl(encoded: String): String =
    if (encoded.contains("://")) encoded
    else runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)

fun encodedUrl(raw: String): String =
    if (raw.isBlank() || !raw.contains("://")) raw
    else runCatching { URLEncoder.encode(raw, "UTF-8") }.getOrDefault(raw)
