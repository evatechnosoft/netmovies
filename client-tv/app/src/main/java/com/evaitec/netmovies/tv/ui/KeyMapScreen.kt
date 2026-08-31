package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
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

    Box(Modifier.fillMaxSize().background(Color(0xFF0F0F14))) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = "⚙ Buton Eşleme — Kumanda Tuş Ayarları",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF8B5CF6),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Oynatıcıda geçerli. D-pad ile satır seçip OK ile aksiyon değiştirin.",
                color = Color(0x99EDEDF2),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TouchButton("Varsayılana dön", onClick = { bindings.reset() }, modifier = Modifier.focusRequester(firstFocus))
                TouchButton("Geri", onClick = onBack)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1.0f, tween(150), label = "rowScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF2E264D) else Color(0xFF1A1726))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF8B5CF6) else Color(0x228B5CF6),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = if (isFocused) Color.White else Color(0xFFEDEDF2), fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium)
            Text(actionLabel, color = if (isFocused) Color.White else Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold)
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF20F0F14))
                .border(2.dp, Color(0xFF8B5CF6), RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Aksiyon Seç", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF8B5CF6), modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RemoteAction.entries.toList()) { action ->
                    var isFocused by remember { mutableStateOf(false) }
                    val selected = action == current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFocused) Color(0xFF8B5CF6) else if (selected) Color(0x338B5CF6) else Color(0xFF1E1A2B))
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) Color.White else Color(0x228B5CF6),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onPick(action) }
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = (if (selected) "● " else "   ") + action.label,
                            color = if (isFocused) Color.White else if (selected) Color(0xFFEDEDF2) else Color(0xCCEDEDF2),
                            fontWeight = if (isFocused || selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
