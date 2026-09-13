package com.evaitec.netmovies.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evaitec.netmovies.tv.ui.theme.NmColor

// Yükleniyor göstergesi: uçları açık İKİ halka, ters yönlerde döner, maviden yeşile.
// Metin tek başına ("Yükleniyor…") bir şeyin ilerlediğini göstermiyordu; donmuş
// ekranla bekleyen ekran aynı görünüyordu.

@Composable
fun NmLoader(size: Dp = 38.dp) {
    val donus = rememberInfiniteTransition(label = "nmLoader")
    val dis by donus.animateFloatSpin(durationMillis = 1500, ters = false, label = "dis")
    val ic  by donus.animateFloatSpin(durationMillis = 1000, ters = true,  label = "ic")

    Canvas(Modifier.size(size)) {
        val kalinlik = this.size.minDimension * 0.085f
        val stroke   = Stroke(width = kalinlik, cap = StrokeCap.Round)
        val bosluk   = this.size.minDimension * 0.24f   // iç halkanın kenardan payı

        drawArc(
            brush      = Brush.sweepGradient(listOf(NmColor.LoaderBlue, NmColor.LoaderGreen, NmColor.LoaderBlue)),
            startAngle = dis,
            sweepAngle = 250f,            // ucu açık: tam çember değil
            useCenter  = false,
            style      = stroke,
            topLeft    = Offset(kalinlik / 2, kalinlik / 2),
            size       = Size(this.size.width - kalinlik, this.size.height - kalinlik),
        )
        drawArc(
            brush      = Brush.sweepGradient(listOf(NmColor.LoaderGreen, NmColor.LoaderBlue, NmColor.LoaderGreen)),
            startAngle = ic,
            sweepAngle = 200f,
            useCenter  = false,
            style      = stroke,
            topLeft    = Offset(bosluk, bosluk),
            size       = Size(this.size.width - bosluk * 2, this.size.height - bosluk * 2),
        )
    }
}

/** 0→360 (ya da tersi) sonsuz dönüş. */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatSpin(
    durationMillis: Int,
    ters: Boolean,
    label: String,
) = animateFloat(
    initialValue  = if (ters) 360f else 0f,
    targetValue   = if (ters) 0f else 360f,
    animationSpec = infiniteRepeatable(
        animation  = tween(durationMillis, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = label,
)
