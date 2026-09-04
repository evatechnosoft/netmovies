package com.evaitec.netmovies.tv.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Tek merkezi tasarım kaynağı: renk / boşluk / tipografi / odak davranışı.
// Ekranlar hardcoded dp-Color yerine buradaki token'ları kullanır.

object NmColor {
    val Background      = Color(0xFF15131E)
    val Surface         = Color(0xFF231F31)   // satır/kart zemini
    val SurfaceHigh     = Color(0xFF332C48)   // poster placeholder, pasif buton
    val SurfaceDialog   = Color(0xF21C1929)   // modal panel
    val Primary         = Color(0xFF8B5CF6)
    val PrimarySelected = Color(0x338B5CF6)   // seçili ama odaklı değil
    val PrimaryHairline = Color(0x2E8B5CF6)   // pasif kenarlık
    val OnPrimary       = Color(0xFFFFFFFF)
    val OnSurface       = Color(0xFFEDEDF2)
    val OnSurfaceMuted  = Color(0xB3EDEDF2)
    val OnSurfaceFaint  = Color(0x80EDEDF2)
    val FocusRing       = Color(0xFFFFFFFF)   // odak halkası — en yüksek kontrast
    val Scrim           = Color(0xCC000000)   // tam ekran modal arkası
    val ScrimSoft       = Color(0x99000000)   // overlay pill zemini
    val TrackIdle       = Color(0x40FFFFFF)   // ilerleme çubuğu boş kısmı
    val Star            = Color(0xFFFFC107)
    val BannerBg        = Color(0xFF2A2140)
}

object NmDim {
    // TV overscan güvenli alanı — 1080p/320dpi'de ekran ~640x360dp, %5-6 kenar payı.
    val SafeH = 36.dp
    val SafeV = 20.dp
    val SafeArea = PaddingValues(horizontal = SafeH, vertical = SafeV)

    val RowGap  = 22.dp   // raflar arası
    val CardGap = 18.dp   // raf içi kartlar arası
    val ItemGap = 10.dp   // liste satırları arası

    val PosterWidth   = 130.dp
    val GridPosterMin = 150.dp

    val FocusRingWidth = 3.dp
    val IdleRingWidth  = 1.dp

    val CardRadius  = 12.dp
    val RowRadius   = 10.dp
    val PillRadius  = 24.dp
    val PanelRadius = 16.dp

    // Poster odakta büyümez, hafifçe küçülür (~4dp): büyüteç komşu kartları eziyordu.
    val FocusScaleCard = 0.97f
    val FocusScalePill = 1.06f
    val FocusScaleRow  = 1.02f

    val PanelWidth  = 360.dp
    val DialogWidth = 380.dp
}

object NmType {
    val Wordmark    = 26.sp
    val ScreenTitle = 22.sp
    val RowTitle    = 19.sp
    val Body        = 16.sp
    val Label       = 15.sp
    val Caption     = 13.sp
}

private const val FOCUS_ANIM_MS = 140

/** Odak halkası: odaklıyken kalın beyaz, boştayken ince mor iz. */
fun Modifier.nmFocusRing(focused: Boolean, shape: Shape): Modifier = border(
    width = if (focused) NmDim.FocusRingWidth else NmDim.IdleRingWidth,
    color = if (focused) NmColor.FocusRing else NmColor.PrimaryHairline,
    shape = shape,
)

/** Odak halkası — boştayken kenarlık hiç çizilmez (poster gibi kenarlıksız öğeler). */
fun Modifier.nmFocusRingOnly(focused: Boolean, shape: Shape): Modifier = border(
    width = if (focused) NmDim.FocusRingWidth else 0.dp,
    color = if (focused) NmColor.FocusRing else Color.Transparent,
    shape = shape,
)

fun Modifier.nmScale(scale: Float): Modifier =
    graphicsLayer { scaleX = scale; scaleY = scale }

/** Odak büyüteci — tüm ekranlarda aynı süre/eğri. */
@Composable
fun nmFocusScale(focused: Boolean, focusedScale: Float = NmDim.FocusScalePill, label: String = "nmFocusScale"): Float {
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(FOCUS_ANIM_MS),
        label = label,
    )
    return scale
}

/** Poster altındaki başlık için okunabilirlik degradesi. */
val nmBottomScrim: Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to Color(0x99000000),
    1f to Color(0xE6000000),
)

/** Oynatıcı alt kontrol çubuğu degradesi (video üstünde okunurluk). */
val nmPlayerScrim: Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color(0xE6000000),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetMoviesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary        = NmColor.Primary,
            onPrimary      = NmColor.OnPrimary,
            background     = NmColor.Background,
            onBackground   = NmColor.OnSurface,
            surface        = NmColor.Surface,
            onSurface      = NmColor.OnSurface,
            surfaceVariant = NmColor.SurfaceHigh,
        ),
    ) {
        // tv-material3 Text rengini LocalContentColor'dan okur; içerik bir Surface
        // içinde olmadığından varsayılan Color.Black kalıyordu → koyu zeminde yazı
        // görünmüyordu. Tema onSurface rengini tüm içeriğe zorla.
        CompositionLocalProvider(LocalContentColor provides NmColor.OnSurface, content = content)
    }
}
