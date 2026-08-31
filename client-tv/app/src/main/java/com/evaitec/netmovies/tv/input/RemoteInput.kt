package com.evaitec.netmovies.tv.input

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Oynatıcıda bir tuşa atanabilir aksiyonlar. repeatable=true olanlar basılı tutmada
// her tekrar olayında yeniden tetiklenir (sürekli hızlı sarma hissi).
enum class RemoteAction(val id: String, val label: String, val repeatable: Boolean = false) {
    NONE("none", "(Yok)"),
    PLAY_PAUSE("play_pause", "Oynat / Duraklat"),
    SEEK_FWD_10("fwd10", "10sn İleri", repeatable = true),
    SEEK_BACK_10("back10", "10sn Geri", repeatable = true),
    SEEK_FWD_60("fwd60", "1dk İleri", repeatable = true),
    SEEK_BACK_60("back60", "1dk Geri", repeatable = true),
    SEEK_HOLD_FWD("hold_fwd", "Basılı: Hızlı İleri", repeatable = true),
    SEEK_HOLD_BACK("hold_back", "Basılı: Hızlı Geri", repeatable = true),
    OPEN_SETTINGS("settings", "Ayarlar (çark)"),
    SHOW_CONTROLS("controls", "Kontrolleri Göster"),
    TOGGLE_SCRUB("scrub", "Önizleme / Scrub"),
    TOGGLE_MOUSE_MODE("mouse_mode", "🖱 Sanal Fare (Mouse)"),
    BACK("back", "Geri / Çık");

    companion object {
        fun fromId(id: String): RemoteAction = entries.firstOrNull { it.id == id } ?: NONE
    }
}

// Yeniden atanabilir tuşlar (TV kumandası D-pad).
enum class RemoteKey(val keyCode: Int, val altKeyCode: Int, val label: String) {
    OK(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, "OK / Orta"),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, -1, "Sol ◀"),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, -1, "Sağ ▶"),
    UP(KeyEvent.KEYCODE_DPAD_UP, -1, "Yukarı ▲"),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN, -1, "Aşağı ▼");

    companion object {
        fun from(keyCode: Int): RemoteKey? =
            entries.firstOrNull { it.keyCode == keyCode || it.altKeyCode == keyCode }
    }
}

enum class PressType(val label: String) {
    SINGLE("Tek basış"),
    DOUBLE("Çift basış"),
    LONG("Basılı tutma");
}

// Best-practice varsayılan eşleme (YouTube/Netflix TV mantığı).
private val DEFAULTS: Map<String, RemoteAction> = buildMap {
    fun k(key: RemoteKey, p: PressType) = "${key.keyCode}_${p.name}"
    put(k(RemoteKey.OK, PressType.SINGLE), RemoteAction.PLAY_PAUSE)
    put(k(RemoteKey.OK, PressType.LONG), RemoteAction.OPEN_SETTINGS)
    put(k(RemoteKey.LEFT, PressType.SINGLE), RemoteAction.SEEK_BACK_10)
    put(k(RemoteKey.RIGHT, PressType.SINGLE), RemoteAction.SEEK_FWD_10)
    put(k(RemoteKey.LEFT, PressType.DOUBLE), RemoteAction.SEEK_BACK_60)
    put(k(RemoteKey.RIGHT, PressType.DOUBLE), RemoteAction.SEEK_FWD_60)
    put(k(RemoteKey.LEFT, PressType.LONG), RemoteAction.SEEK_HOLD_BACK)
    put(k(RemoteKey.RIGHT, PressType.LONG), RemoteAction.SEEK_HOLD_FWD)
    put(k(RemoteKey.UP, PressType.SINGLE), RemoteAction.TOGGLE_SCRUB)
    put(k(RemoteKey.DOWN, PressType.SINGLE), RemoteAction.OPEN_SETTINGS)
}

// Kalıcı tuş eşlemesi. Compose observable (mutableStateMap) → Buton Eşleme ekranı
// değişince anında güncellenir.
class KeyBindings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("netmovies_keymap", Context.MODE_PRIVATE)

    val map = mutableStateMapOf<String, RemoteAction>()

    init { load() }

    private fun storageKey(key: RemoteKey, p: PressType) = "${key.keyCode}_${p.name}"

    private fun load() {
        map.clear()
        // Önce varsayılanlar, sonra kayıtlı override'lar.
        DEFAULTS.forEach { (k, v) -> map[k] = v }
        for (key in RemoteKey.entries) {
            for (p in PressType.entries) {
                val sk = storageKey(key, p)
                prefs.getString(sk, null)?.let { map[sk] = RemoteAction.fromId(it) }
            }
        }
    }

    fun get(key: RemoteKey, p: PressType): RemoteAction =
        map[storageKey(key, p)] ?: RemoteAction.NONE

    fun get(keyCode: Int, p: PressType): RemoteAction {
        val rk = RemoteKey.from(keyCode) ?: return RemoteAction.NONE
        return get(rk, p)
    }

    fun set(key: RemoteKey, p: PressType, action: RemoteAction) {
        val sk = storageKey(key, p)
        map[sk] = action
        prefs.edit().putString(sk, action.id).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
        load()
    }
}

// Ham KeyEvent akışını basış tipine (tek/çift/uzun) çözer ve eşlenen aksiyonu tetikler.
// Oynatıcı immersive modda tüm D-pad tuşlarını buraya yönlendirir.
class RemoteInputController(
    private val bindings: KeyBindings,
    private val scope: CoroutineScope,
    private val onAction: (RemoteAction) -> Unit,
) {
    private val longFired = HashMap<Int, Boolean>()
    private val lastUp = HashMap<Int, Long>()
    private val pendingSingle = HashMap<Int, Job>()
    private val doubleWindowMs = 300L

    // true → olay tüketildi (native focus gezinmesi engellenir).
    fun process(e: KeyEvent): Boolean {
        val code = e.keyCode
        val rk = RemoteKey.from(code) ?: return false

        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) {
                    longFired[code] = false
                } else {
                    // Sistem uzun-basış tekrarları gönderiyor.
                    val longAction = bindings.get(rk, PressType.LONG)
                    if (longAction != RemoteAction.NONE) {
                        if (longAction.repeatable) {
                            onAction(longAction)          // sürekli sarma: her tekrarda
                        } else if (longFired[code] != true) {
                            onAction(longAction); longFired[code] = true   // tek sefer
                        }
                    }
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (longFired[code] == true) { longFired[code] = false; return true }
                val now = e.eventTime
                val dbl = bindings.get(rk, PressType.DOUBLE)
                if (dbl != RemoteAction.NONE) {
                    val prev = lastUp[code] ?: 0L
                    if (now - prev <= doubleWindowMs) {
                        pendingSingle.remove(code)?.cancel()
                        lastUp[code] = 0L
                        onAction(dbl)
                    } else {
                        lastUp[code] = now
                        pendingSingle.remove(code)?.cancel()
                        pendingSingle[code] = scope.launch {
                            delay(doubleWindowMs)
                            onAction(bindings.get(rk, PressType.SINGLE))
                        }
                    }
                } else {
                    onAction(bindings.get(rk, PressType.SINGLE))
                }
                return true
            }
        }
        return false
    }
}
