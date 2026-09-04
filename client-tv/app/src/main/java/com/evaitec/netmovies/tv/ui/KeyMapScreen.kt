package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
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
import com.evaitec.netmovies.tv.data.ServerResolver
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.input.PressType
import com.evaitec.netmovies.tv.input.RemoteAction
import com.evaitec.netmovies.tv.input.RemoteKey

// Buton Eşleme: her tuş × basış tipi için atanmış aksiyonu göster + değiştir.
private data class BindingSlot(val key: RemoteKey, val press: PressType)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KeyMapScreen(bindings: KeyBindings, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<BindingSlot?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    BackHandler(enabled = true) {
        if (editing != null) editing = null else onBack()
    }

    val slots = remember {
        buildList {
            for (k in RemoteKey.entries) for (p in PressType.entries) add(BindingSlot(k, p))
        }
    }

    Box(Modifier.fillMaxSize().background(NmColor.Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV)) {
            Text(
                text = "⚙  Buton Eşleme — Kumanda Tuş Ayarları",
                fontWeight = FontWeight.Bold,
                fontSize = NmType.ScreenTitle,
                color = NmColor.Primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = "Oynatıcıda geçerli. D-pad ile satır seçip OK ile aksiyon değiştirin.",
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Caption,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Hangi sunucuya bağlı olduğumuz görünmüyordu; "yerel ağ çalışıyor mu"
            // sorusu ancak log'a bakarak cevaplanabiliyordu. Artık ekranda yazıyor.
            Text(
                text = serverStatusLine(),
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Caption,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.focusGroup().padding(bottom = 16.dp),
            ) {
                TouchButton("Varsayılana dön", onClick = { bindings.reset() }, modifier = Modifier.focusRequester(firstFocus), accent = true)
                TouchButton("Geri", onClick = onBack)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
                contentPadding = PaddingValues(bottom = NmDim.SafeV),
                verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
            ) {
                items(slots) { slot ->
                    val action = bindings.get(slot.key, slot.press)
                    BindingRow(
                        title = "${slot.key.label}  ·  ${slot.press.label}",
                        actionLabel = action.label,
                        onClick = { editing = slot },
                    )
                }
            }
        }

        editing?.let { slot ->
            ActionPicker(
                current = bindings.get(slot.key, slot.press),
                onPick = { action ->
                    bindings.set(slot.key, slot.press, action)
                    editing = null
                },
                onClose = { editing = null },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BindingRow(title: String, actionLabel: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(isFocused, NmDim.FocusScaleRow, label = "rowScale")
    val shape = RoundedCornerShape(NmDim.RowRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .nmScale(scale)
            .clip(shape)
            .background(if (isFocused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(isFocused, shape)
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = NmType.Body,
                color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                text = actionLabel,
                fontSize = NmType.Body,
                color = if (isFocused) NmColor.OnPrimary else NmColor.Primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionPicker(
    current: RemoteAction,
    onPick: (RemoteAction) -> Unit,
    onClose: () -> Unit,
) {
    val pickerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { pickerFocus.requestFocus() } }

    val panelShape = RoundedCornerShape(NmDim.PanelRadius)
    val rowShape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        Modifier
            .fillMaxSize()
            .background(NmColor.Scrim)
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(NmDim.DialogWidth)
                .clip(panelShape)
                .background(NmColor.SurfaceDialog)
                .nmFocusRing(false, panelShape)
                .focusGroup()
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
        ) {
            Text(
                text = "Aksiyon Seç",
                fontWeight = FontWeight.Bold,
                fontSize = NmType.ScreenTitle,
                color = NmColor.Primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LazyColumn(
                modifier = Modifier.focusRequester(pickerFocus).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
            ) {
                items(RemoteAction.entries.toList()) { action ->
                    var isFocused by remember { mutableStateOf(false) }
                    val selected = action == current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(
                                when {
                                    isFocused -> NmColor.Primary
                                    selected  -> NmColor.PrimarySelected
                                    else      -> NmColor.Surface
                                }
                            )
                            .nmFocusRing(isFocused, rowShape)
                            .clickable { onPick(action) }
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = (if (selected) "●  " else "     ") + action.label,
                            fontSize = NmType.Body,
                            color = if (isFocused) NmColor.OnPrimary else if (selected) NmColor.OnSurface else NmColor.OnSurfaceMuted,
                            fontWeight = if (isFocused || selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/** "Sunucu: 192.168.1.185:3310 · yerel ağ" — hangi yolun seçildiği ayarlarda görünsün.
 *  cachedBase() kullanılır: UI thread'inde ağ yoklaması yapılmaz. */
internal fun serverStatusLine(): String {
    val base = ServerResolver.cachedBase() ?: return "Sunucu: seçiliyor…"
    val yol  = if (ServerResolver.isLocal(base)) "yerel ağ" else "uzak tünel"
    return "Sunucu: ${base.host}:${base.port}  ·  $yol"
}
