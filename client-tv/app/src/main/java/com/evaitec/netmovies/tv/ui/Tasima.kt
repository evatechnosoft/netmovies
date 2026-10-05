package com.evaitec.netmovies.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.input.NmBackHandler
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Elle sıralanan çip şeritleri (Gözat kaynakları, ana sayfa kişisel blok) için
// tek jest: OK BASILI TUT → öğe sarı, SOL/SAĞ taşır, OK bırakır ve kaydeder,
// GERİ eski sıraya döner. Google TV / tvOS / webOS'taki "taşıma modu"nun aynısı.

/** Kayıtlı sıradaki adlar önce (artık olmayanlar düşer), yeni gelenler varsayılan yerinden sona. */
internal fun siralaCipler(varsayilan: List<String>, kayitli: List<String>): List<String> {
    val kayitliVar = kayitli.filter { it in varsayilan }.distinct()
    return kayitliVar + varsayilan.filterNot { it in kayitliVar }
}

@Stable
internal class TasimaDurumu(ilk: List<String>, private val kaydet: (List<String>) -> Unit) {
    var sira by mutableStateOf(ilk)
        private set
    var tasinan by mutableStateOf<String?>(null)
        private set
    private var onceki = ilk

    fun baslat(ad: String) {
        onceki = sira
        tasinan = ad
    }

    /** Taşınan öğeyi bir adım kaydırır; yeni indeksi döner (kenardaysa null). */
    fun tasi(yon: Int): Int? {
        val i = sira.indexOf(tasinan ?: return null)
        val j = i + yon
        if (i < 0 || j !in sira.indices) return null
        sira = sira.toMutableList().apply { add(j, removeAt(i)) }
        return j
    }

    fun bitir() {
        if (tasinan == null) return
        tasinan = null
        if (sira != onceki) kaydet(sira)
    }

    fun iptal() {
        if (tasinan == null) return
        sira = onceki
        tasinan = null
    }
}

/** Taşıma durumu + GERİ = iptal. [kayitli] değişince (sunucudan geldi) yeniden kurulur. */
@Composable
internal fun rememberTasima(
    varsayilan: List<String>,
    kayitli: List<String>,
    kaydet: (List<String>) -> Unit,
): TasimaDurumu {
    val guncelKaydet by rememberUpdatedState(kaydet)
    val durum = remember(varsayilan, kayitli) {
        TasimaDurumu(siralaCipler(varsayilan, kayitli)) { guncelKaydet(it) }
    }
    NmBackHandler(enabled = durum.tasinan != null) { durum.iptal() }
    return durum
}

/**
 * OK kısa bas = [onClick], basılı tut = [onLongPress]; [tasiniyor] iken SOL/SAĞ = [onMove].
 * Kumandanın OK'u `combinedClickable(onLongClick)`'e düşmez (yalnız dokunma); uzun basış
 * key event'ten okunur, bırakıştaki ACTION_UP tıklama sayılmaz.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tasimaTuslari(
    tasiniyor: Boolean,
    onLongPress: () -> Unit,
    onMove: (Int) -> Unit,
    onClick: () -> Unit,
): Modifier = composed {
    var uzunBasildi by remember { mutableStateOf(false) }
    var zamanlayici by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val uzunBas = {
        if (!uzunBasildi) {
            uzunBasildi = true
            onLongPress()
        }
    }
    onPreviewKeyEvent { ke ->
        val ne = ke.nativeKeyEvent
        when (ne.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!tasiniyor) return@onPreviewKeyEvent false
                if (ne.action == KeyEvent.ACTION_DOWN) onMove(if (ne.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (ne.action) {
                    KeyEvent.ACTION_DOWN ->
                        if (ne.repeatCount == 0) {
                            uzunBasildi = false
                            zamanlayici?.cancel()
                            zamanlayici = scope.launch {
                                delay(UZUN_BASIS_MS)
                                uzunBas()
                            }
                        } else {
                            uzunBas()
                        }
                    KeyEvent.ACTION_UP -> {
                        zamanlayici?.cancel()
                        if (!uzunBasildi) onClick()
                        uzunBasildi = false
                    }
                }
                true
            }
            else -> false
        }
    }
        // Dokunma yolu (telefon): uzun basış pointer'la burada.
        .combinedClickable(onClick = onClick, onLongClick = onLongPress)
}

/** Taşıma modundaki öğenin etiketi. */
internal fun tasimaEtiketi(label: String) = "◀ $label ▶"

/** Şeridin altındaki tek satırlık ipucu; taşıma yokken hiçbir şey çizmez. */
@Composable
internal fun TasimaIpucu(gorunur: Boolean, modifier: Modifier = Modifier) {
    if (gorunur) {
        Text(
            "◀ ▶ taşı · OK bırak · GERİ iptal",
            fontSize = NmType.Label,
            color = NmColor.Star,
            modifier = modifier,
        )
    }
}

private const val UZUN_BASIS_MS = 500L
