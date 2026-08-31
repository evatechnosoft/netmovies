package com.evaitec.netmovies.tv.ui

import android.app.Activity
import android.os.SystemClock
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

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

    val displayMetrics = context.resources.displayMetrics
    val screenWPx = displayMetrics.widthPixels.toFloat()
    val screenHPx = displayMetrics.heightPixels.toFloat()
    val screenWDp = (screenWPx / displayMetrics.density).dp
    val screenHDp = (screenHPx / displayMetrics.density).dp

    var cursorX by remember { mutableFloatStateOf(0.5f) }
    var cursorY by remember { mutableFloatStateOf(0.5f) }
    var isClicking by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent true
                val step = 0.035f
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT  -> { cursorX = (cursorX - step).coerceIn(0.01f, 0.99f); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cursorX = (cursorX + step).coerceIn(0.01f, 0.99f); true }
                    KeyEvent.KEYCODE_DPAD_UP    -> { cursorY = (cursorY - step).coerceIn(0.01f, 0.99f); true }
                    KeyEvent.KEYCODE_DPAD_DOWN  -> { cursorY = (cursorY + step).coerceIn(0.01f, 0.99f); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        isClicking = true
                        sendClick(cursorX * screenWPx, cursorY * screenHPx)
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        onToggle()
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
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE68B5CF6))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "🖱 Sanal Fare Modu — Yön tuşları: Hareket · OK: Tıkla · Geri: Çık",
                color = Color.White,
                fontSize = 12.sp,
            )
        }

        // Fare İmleci
        val actualX = screenWDp * cursorX
        val actualY = screenHDp * cursorY

        Box(
            modifier = Modifier
                .offset(x = actualX, y = actualY)
                .size(28.dp),
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
                drawPath(path, color = Color.White, style = Stroke(width = 4f))
                // İç mor dolgu
                drawPath(path, color = if (isClicking) Color(0xFFEF4444) else Color(0xFF8B5CF6), style = Fill)
            }
        }
    }
}
