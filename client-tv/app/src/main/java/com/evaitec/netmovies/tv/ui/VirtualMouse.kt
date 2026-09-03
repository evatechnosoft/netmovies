package com.evaitec.netmovies.tv.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType

// Sanal fare ayarlarinin TEK noktasi: hiz, imlec boyutu ve modun acik kalmasi.
// Kalici (SharedPreferences), Compose-observable -> Ayarlar'da degisince imlec aninda uyar.
// Degerler yuzde olarak tutulur; Ayarlar'daki D-pad slider'i dogrudan bunlari surer.
// Gezinme ayarlari — kullanici gormez, his ayari.
private const val ACCEL_MAX_REPEAT = 18      // bu tekrardan sonra hiz artmaz
private const val ACCEL_PER_REPEAT = 0.14f   // her tekrar +%14
private const val DOUBLE_MS = 320L           // cift basis penceresi
private const val SCROLL_IDLE_MS = 2500L     // kaydirma modu kendiliginden duser

object MouseSettings {
    private const val PREFS = "netmovies_mouse"
    private const val KEY_SPEED = "speed_pct"
    private const val KEY_SIZE = "size_pct"
    private const val KEY_ENABLED = "enabled"

    // Yuzde -> gercek deger araliklari. Alt sinir "kontrol edilebilir en yavas",
    // ust sinir "ekrani bir basista gecmeyen en hizli".
    private const val STEP_MIN = 0.008f
    private const val STEP_MAX = 0.070f
    private const val SIZE_MIN = 18
    private const val SIZE_MAX = 52

    private var prefs: SharedPreferences? = null

    var speedPct by mutableIntStateOf(40)
        private set
    var sizePct by mutableIntStateOf(30)
        private set
    // Mod acikken uygulamayi kapatip acinca fare acik gelsin diye kalici.
    // Ayri backing property: `var enabled` + `fun setEnabled` ayni JVM imzasini
    // (setEnabled(Z)) uretip derlemeyi kiriyordu.
    private var enabledState by mutableStateOf(false)
    val enabled: Boolean get() = enabledState

    /** Basis basina temel adim (ekran genisliginin orani). */
    val step: Float get() = STEP_MIN + (STEP_MAX - STEP_MIN) * (speedPct / 100f)
    val cursorDp: Int get() = SIZE_MIN + ((SIZE_MAX - SIZE_MIN) * (sizePct / 100f)).toInt()

    // Hem imlec katmani hem Ayarlar menusu cagirir; ikinci cagri no-op.
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        speedPct = p.getInt(KEY_SPEED, 40).coerceIn(0, 100)
        sizePct = p.getInt(KEY_SIZE, 30).coerceIn(0, 100)
        enabledState = p.getBoolean(KEY_ENABLED, false)
    }

    fun setSpeed(pct: Int) {
        speedPct = pct.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_SPEED, speedPct)?.apply()
    }

    fun setSize(pct: Int) {
        sizePct = pct.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_SIZE, sizePct)?.apply()
    }

    fun setEnabled(on: Boolean) {
        enabledState = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VirtualMouseOverlay(
    active: Boolean,
    onToggle: () -> Unit,
) {
    if (!active) return

    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    MouseSettings.attach(context)

    val displayMetrics = context.resources.displayMetrics
    val screenWPx = displayMetrics.widthPixels.toFloat()
    val screenHPx = displayMetrics.heightPixels.toFloat()
    val screenWDp = (screenWPx / displayMetrics.density).dp
    val screenHDp = (screenHPx / displayMetrics.density).dp

    var cursorX by remember { mutableFloatStateOf(0.5f) }
    var cursorY by remember { mutableFloatStateOf(0.5f) }
    var isClicking by remember { mutableStateOf(false) }
    // isClicking hic sifirlanmiyordu: ilk tiklamadan sonra imlec kalici kirmizi kaliyordu.
    // Her tiklamada artan sayac kisa bir flash suresi sonunda rengi geri alir.
    var clickFlash by remember { mutableIntStateOf(0) }
    LaunchedEffect(clickFlash) {
        if (clickFlash > 0) {
            kotlinx.coroutines.delay(150)
            isClicking = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun sendClick(pxX: Float, pxY: Float) {
        activity?.let { act ->
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, pxX, pxY, 0)
            val upEvent = MotionEvent.obtain(downTime, eventTime + 50, MotionEvent.ACTION_UP, pxX, pxY, 0)
            act.window.decorView.dispatchTouchEvent(downEvent)
            act.window.decorView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
        }
    }

    // Kaydirma: altta ne varsa (Compose listeleri dahil) fare tekerlegi olayini anlar.
    // Kendi kaydirma mantigimizi yazmak yerine platformun ACTION_SCROLL'u kullanilir.
    fun sendScroll(pxX: Float, pxY: Float, amount: Float) {
        val act = activity ?: return
        val t = SystemClock.uptimeMillis()
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = pxX
            y = pxY
            setAxisValue(MotionEvent.AXIS_VSCROLL, amount)
        }
        val ev = MotionEvent.obtain(
            t, t, MotionEvent.ACTION_SCROLL, 1,
            arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        act.window.decorView.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    // Cift basis algisi: ayni yon tusuna DOUBLE_MS icinde ikinci basis kaydirmaya gecirir.
    // Kaydirma modunda YUKARI/ASAGI imleci degil, imlecin altindaki listeyi surer.
    var lastKey by remember { mutableIntStateOf(0) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    var scrollMode by remember { mutableStateOf(false) }
    LaunchedEffect(scrollMode) {
        if (scrollMode) {
            kotlinx.coroutines.delay(SCROLL_IDLE_MS)
            scrollMode = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onKeyEvent true
                val code = native.keyCode

                // Tusu basili tutunca imlec hizlanir: tek basista hassas, basili
                // tutunca ekrani gecebilecek kadar hizli. Tek adim boyutunu
                // buyutmeden gezinmeyi akici yapan sey bu.
                val boost = 1f + (native.repeatCount.coerceAtMost(ACCEL_MAX_REPEAT) * ACCEL_PER_REPEAT)
                val step = MouseSettings.step * boost

                // Ayni yon tusuna hizli ikinci basis → kaydirma modu.
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    val now = SystemClock.uptimeMillis()
                    if (native.repeatCount == 0) {
                        if (code == lastKey && now - lastKeyAt < DOUBLE_MS) scrollMode = true
                        lastKey = code
                        lastKeyAt = now
                    }
                }

                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT  -> { cursorX = (cursorX - step).coerceIn(0.01f, 0.99f); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cursorX = (cursorX + step).coerceIn(0.01f, 0.99f); true }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (scrollMode) sendScroll(cursorX * screenWPx, cursorY * screenHPx, boost)
                        else cursorY = (cursorY - step).coerceIn(0.01f, 0.99f)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (scrollMode) sendScroll(cursorX * screenWPx, cursorY * screenHPx, -boost)
                        else cursorY = (cursorY + step).coerceIn(0.01f, 0.99f)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        isClicking = true
                        sendClick(cursorX * screenWPx, cursorY * screenHPx)
                        clickFlash = clickFlash + 1
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (scrollMode) scrollMode = false else onToggle()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Üst bilgi rozeti
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = NmDim.SafeV)
                .clip(RoundedCornerShape(NmDim.PillRadius))
                .background(if (scrollMode) Color(0xFF0EA5E9) else NmColor.Primary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (scrollMode)
                    "🖱 Kaydırma — Yukarı/Aşağı: Kaydır · Geri: Kaydırmadan çık"
                else
                    "🖱 Sanal Fare — Yön: Hareket (basılı tut: hızlan) · Çift Yukarı/Aşağı: Kaydır · OK: Tıkla · Geri: Çık",
                color = NmColor.OnPrimary,
                fontSize = NmType.Caption,
            )
        }

        // Fare İmleci
        val actualX = screenWDp * cursorX
        val actualY = screenHDp * cursorY

        Box(
            modifier = Modifier
                .offset(x = actualX, y = actualY)
                .size(MouseSettings.cursorDp.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.9f, size.height * 0.45f)
                    lineTo(size.width * 0.45f, size.height * 0.55f)
                    lineTo(size.width * 0.65f, size.height * 0.95f)
                    lineTo(size.width * 0.45f, size.height * 1.0f)
                    lineTo(size.width * 0.28f, size.height * 0.6f)
                    lineTo(0f, size.height * 0.85f)
                    close()
                }
                // Dış beyaz gölge / çizgi
                drawPath(path, color = NmColor.FocusRing, style = Stroke(width = 4f))
                // İç mor dolgu
                drawPath(path, color = if (isClicking) Color(0xFFEF4444) else NmColor.Primary, style = Fill)
            }
        }
    }
}
