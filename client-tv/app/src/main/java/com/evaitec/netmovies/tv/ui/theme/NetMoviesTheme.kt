package com.evaitec.netmovies.tv.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
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
    val LoaderBlue      = Color(0xFF3B82F6)   // yükleniyor halkası — dış
    val LoaderGreen     = Color(0xFF22C55E)   // yükleniyor halkası — iç
    val BannerBg        = Color(0xFF2A2140)
}

object NmDim {
    // TV overscan güvenli alanı — Mi Box 1080p/320dpi'de ekran ~960x540dp;
    // Android TV rehberinin %5 kenar payı: 48dp yatay, 27dp dikey.
    val SafeH = 48.dp
    val SafeV = 27.dp
    val SafeArea = PaddingValues(horizontal = SafeH, vertical = SafeV)

    // Dean: "çok boşluğa ve büyük başlıklara gerek yok" — ekrana daha çok raf sığsın.
    val RowGap  = 12.dp   // raflar arası
    val CardGap = 12.dp   // raf içi kartlar arası
    val RowPadV = 6.dp    // raf içi dikey nefes payı (poster odakta KÜÇÜLDÜĞÜ için az yeter)
    val ItemGap = 10.dp   // liste satırları arası
    // Oynatıcı ayar paneli: sekme, sezon çipi ve bölüm satırı aynı boyda — tek ritim.
    val PanelRowHeight = 44.dp
    val ChipGap        = 6.dp    // şerit içi çipler ve bölüm satırları arası
    val SeasonChipWidth = 56.dp  // "Sezon" etiketi bir kez yazar, çipte yalnız numara
    val EpisodeNumWidth = 40.dp  // bölüm numarası sütunu: adlar aynı hizadan başlar

    // Rafa kaç poster sığacağı SABİT: kart genişliği ekrandan hesaplanır
    // (Dean, 19 Eylül: "her sayfa eşit düzenlensin"). 10 posterde kart ~79dp,
    // başlık 3 metreden okunmuyordu → 7 (Dean, 24 Eylül).
    const val RafPosterAdedi = 7
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
    // 3 metreden okunurluk: en küçük yazı 14sp, poster başlığı (Label) 16sp.
    val RowTitle    = 17.sp
    val Body        = 18.sp
    val Label       = 16.sp
    val Caption     = 14.sp
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

/**
 * Odak kaydırması. Varsayılan davranış kartı görünür alana sokacak EN AZ mesafeyi
 * kaydırıyor: kart hep kenara dayanıyor ve her adımda "zıplıyor" (Dean: "atlar gibi
 * geziyor"). Burada kenarda bir tampon bırakılır — kart tampona girdiğinde liste
 * yumuşakça akar, sonraki kart zaten görünür olur.
 */
@OptIn(ExperimentalFoundationApi::class)
val NmBringIntoView = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 300, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val edge = containerSize * 0.22f
        val end = offset + size
        return when {
            offset < edge -> offset - edge
            end > containerSize - edge -> end - (containerSize - edge)
            else -> 0f
        }
    }
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
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
        CompositionLocalProvider(
            LocalContentColor provides NmColor.OnSurface,
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides NmBringIntoView,
            content = content,
        )
    }
}

/** Aynı APK telefona da kuruluyor. Dikey telefonda (~411dp) TV ölçüleri taşıyordu:
 *  7 poster 64dp'ye iniyor, başlık iki harfe bölünüyor, üst düğmeler ekran dışına
 *  kayıyordu (Dean, 26 Eylül ekran görüntüsü). 600dp altı = telefon düzeni. */
@androidx.compose.runtime.Composable
fun nmTelefon(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

/** Yatay kenar boşluğu: TV'de overscan payı, telefonda parmak payı kadar. */
@androidx.compose.runtime.Composable
fun nmKenar(): androidx.compose.ui.unit.Dp = if (nmTelefon()) 16.dp else NmDim.SafeH

/** Bir rafa tam [adet] poster sığacak kart genişliği. Kenar boşluğu ve kartlar
 *  arası aralık düşülür. Telefonda 3 poster + dördüncünün ucu: kaydırılabildiği belli. */
@androidx.compose.runtime.Composable
fun nmRafPosterGenisligi(adet: Int = NmDim.RafPosterAdedi): androidx.compose.ui.unit.Dp {
    val ekran = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    if (nmTelefon()) return (ekran - 16.dp - NmDim.CardGap * 3) / 3.3f
    return ((ekran - NmDim.SafeH * 2 - NmDim.CardGap * (adet - 1)) / adet)
        .coerceAtLeast(64.dp)
}
