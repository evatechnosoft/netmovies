package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmFocusScale
import com.evaitec.netmovies.tv.ui.theme.nmScale

// Hem DOKUNMATİK hem TV D-pad ile odaklanıp parlayan ve çalışan buton.
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TouchButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(isFocused, label = "touchBtnScale")
    val shape = RoundedCornerShape(NmDim.PillRadius)

    Box(
        modifier = modifier
            .nmScale(scale)
            .clip(shape)
            .background(
                when {
                    isFocused -> NmColor.Primary
                    accent    -> NmColor.PrimarySelected
                    else      -> NmColor.SurfaceHigh
                }
            )
            .nmFocusRing(isFocused, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = NmType.Label,
        )
    }
}
