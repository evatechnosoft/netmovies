package com.evaitec.netmovies.tv.data

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Oynatma teşhis günlüğü.
//
// Kaynak zincirindeki her adım (arama, link çözme, oynatma denemesi) buraya
// yazılır — hem logcat'e hem uygulama içinde okunabilen halka tampona. Eskiden
// bu adımlar `runCatching{}.getOrNull()` ile sessizce yutuluyordu: bir içerik
// açılmadığında hangi sağlayıcının neden düştüğü hiçbir yerde görünmüyordu.
//
// Ayarlar → "Kaynak raporu" bu tamponu gösterir; cihazda, PC'siz okunur.

object PlaybackLog {

    enum class Level { INFO, WARN, FAIL }

    data class Entry(
        val timeMillis: Long,
        val level: Level,
        val stage: String,
        val message: String,
    ) {
        fun format(): String = "${TIME_FORMAT.format(Date(timeMillis))} ${level.mark()} $stage — $message"
    }

    private const val TAG = "NetMoviesPlayback"
    private const val CAPACITY = 200

    private val entries = ArrayDeque<Entry>(CAPACITY)
    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Yeni bir oynatma denemesi başlarken çağrılır; tampon sıfırlanır. */
    @Synchronized
    fun startSession(title: String?, plugin: String) {
        entries.clear()
        info("oturum", "${title ?: "?"} · seçili sağlayıcı: $plugin")
    }

    @Synchronized
    fun info(stage: String, message: String) = record(Level.INFO, stage, message)

    @Synchronized
    fun warn(stage: String, message: String) = record(Level.WARN, stage, message)

    @Synchronized
    fun fail(stage: String, message: String, error: Throwable? = null) {
        val detail = error?.let { "$message · ${it::class.simpleName}: ${it.message ?: "-"}" } ?: message
        record(Level.FAIL, stage, detail)
    }

    /** En yeni kayıt en üstte — rapor ekranı bu sırayı gösterir. */
    @Synchronized
    fun snapshot(): List<Entry> = entries.toList().asReversed()

    @Synchronized
    fun clear() = entries.clear()

    private fun record(level: Level, stage: String, message: String) {
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast(Entry(System.currentTimeMillis(), level, stage, message))
        when (level) {
            Level.INFO -> Log.i(TAG, "$stage — $message")
            Level.WARN -> Log.w(TAG, "$stage — $message")
            Level.FAIL -> Log.e(TAG, "$stage — $message")
        }
    }

    private fun Level.mark(): String = when (this) {
        Level.INFO -> "•"
        Level.WARN -> "!"
        Level.FAIL -> "x"
    }
}

/**
 * Yutan `runCatching` yerine: hata günlüğe düşer, çağıran null alır.
 * Zincirin devam etmesi gerektiği için hata fırlatılmaz — ama artık görünür.
 */
inline fun <T> loggedOrNull(stage: String, detail: String, block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        PlaybackLog.fail(stage, detail, e)
        null
    }
