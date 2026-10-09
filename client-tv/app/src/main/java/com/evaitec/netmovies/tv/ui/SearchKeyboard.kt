package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing

/**
 * Google TV style grid keyboard that stays on screen (no IME round trip).
 * Edges wrap; RIGHT on the last column leaves to the results when [sagdaIcerikVar],
 * UP on the top row leaves to the header (system keyboard field). Re-entry lands
 * on the last pressed/focused key ([sonTus]).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun SearchKeyboard(
    duzen: KlavyeDuzeni,
    sonTus: Int,
    onSonTus: (Int) -> Unit,
    ilkOdak: Boolean,
    sagdaIcerikVar: Boolean,
    onTus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adet = duzen.tuslar.size
    val sutunSayisi = duzen.sutun
    val istekler = remember(duzen) { List(adet) { FocusRequester() } }
    val son = sonTus.coerceIn(0, adet - 1)
    fun komsu(i: Int, dx: Int, dy: Int) = istekler[sarmaliKomsu(i, dx, dy, sutunSayisi, adet)]
    // Wide layouts get smaller keys so the results keep room for posters.
    val tusBoyu = if (sutunSayisi > 7) NmDim.KeySizeWide else NmDim.KeySize

    // A single requestFocus on the first frame is silently dropped (tv-focus trap #2).
    LaunchedEffect(Unit) {
        if (!ilkOdak) return@LaunchedEffect
        repeat(6) {
            withFrameNanos {}
            if (runCatching { istekler[son].requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Column(
        modifier = modifier
            .focusProperties { enter = { istekler[son] } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(NmDim.ChipGap),
    ) {
        duzen.tuslar.chunked(sutunSayisi).forEachIndexed { satir, tuslar ->
            Row(horizontalArrangement = Arrangement.spacedBy(NmDim.ChipGap)) {
                tuslar.forEachIndexed { sutun, tus ->
                    val i = satir * sutunSayisi + sutun
                    Tus(
                        tus = tus,
                        boyut = tusBoyu,
                        modifier = Modifier
                            .focusRequester(istekler[i])
                            .focusProperties {
                                left = komsu(i, -1, 0)
                                down = komsu(i, 0, 1)
                                right = if (sutun == sutunSayisi - 1 && sagdaIcerikVar) FocusRequester.Default
                                        else komsu(i, 1, 0)
                                // Top row: UP exits to the header field (system keyboard stays reachable).
                                up = if (satir == 0) FocusRequester.Default else komsu(i, 0, -1)
                            },
                        onFocus = { onSonTus(i) },
                        onClick = { onTus(tus) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Tus(tus: String, boyut: Dp, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        // onFocusChanged before clickable: one focus target (tv-focus trap #1).
        modifier = modifier
            .size(boyut)
            .clip(shape)
            .background(if (focused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tus,
            fontSize = NmType.Body,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
        )
    }
}
