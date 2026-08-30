package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                text = "Buton Eşleme — her tuşa aksiyon ata",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Oynatıcıda geçerli. Bir satır seç → aksiyon değiştir.",
                color = Color(0x99EDEDF2),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TouchButton("Varsayılana dön", onClick = { bindings.reset() })
                TouchButton("Geri", onClick = onBack)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1726))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title)
            Text(actionLabel, color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold)
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Perde dokunuşu yutsun (arkadaki liste kaymasın); boşa dokun → kapat.
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF20F0F14))
                // Panel içi dokunuş perdeye geçmesin.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Aksiyon seç", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(RemoteAction.entries.toList()) { action ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (action == current) Color(0x338B5CF6) else Color(0x00000000))
                            .clickable { onPick(action) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = (if (action == current) "● " else "   ") + action.label,
                            fontWeight = if (action == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
