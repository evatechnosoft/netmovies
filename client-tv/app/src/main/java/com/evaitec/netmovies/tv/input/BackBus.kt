package com.evaitec.netmovies.tv.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * GERİ tuşu için tek kapı.
 *
 * `BackHandler` tek başına yetmiyordu: TV Material'ın odak grupları GERİ'yi
 * "gruptaki ilk öğeye dön" diye yutuyor, tuş hiç geri-dispatch'e ulaşmıyordu.
 * Kullanıcıda görünen hâli: GERİ raflar arasında geziyor, en üste çıkmıyor,
 * ekranı kapatmıyor (Dean: "bantlarda geziyor, kapatmıyor").
 *
 * Çözüm: ekranlar işleyicisini buraya yazar, `MainActivity.dispatchKeyEvent`
 * tuşu Compose'a İNMEDEN önce en üstteki işleyiciye verir. Yığın: en son
 * kaydolan (en üstteki ekran/modal) önce görür.
 */
object BackBus {
    private val yigin = mutableListOf<() -> Unit>()

    fun kaydol(isleyici: () -> Unit): () -> Unit {
        yigin.add(isleyici)
        return { yigin.remove(isleyici) }
    }

    fun varMi(): Boolean = yigin.isNotEmpty()

    /** GERİ tüketildiyse true. Kimse kayıtlı değilse sistem kendi işini yapar. */
    fun geri(): Boolean {
        val isleyici = yigin.lastOrNull() ?: return false
        isleyici()
        return true
    }
}

/** `BackHandler` yerine bunu kullan: aynı imza, ama tuş gerçekten geliyor. */
@Composable
fun NmBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val guncel = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val birak = BackBus.kaydol { guncel.value() }
        onDispose { birak() }
    }
}
