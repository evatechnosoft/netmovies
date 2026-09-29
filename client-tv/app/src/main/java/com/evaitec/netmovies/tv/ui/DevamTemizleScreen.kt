package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.parseEpisodeRef
import com.evaitec.netmovies.tv.input.NmBackHandler
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import kotlinx.coroutines.launch

// Devam Et toplu temizleme (Dean: "tek tek silmekle uğraşmak istemiyorum").
// OK = seç/bırak. Silme iki basış ister: ilki düğmeyi "Emin misin?"e çevirir.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DevamTemizleScreen(library: Library, onBack: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var secili by remember { mutableStateOf(emptySet<String>()) }
    var onayBekliyor by remember { mutableStateOf(false) }
    var siliniyor by remember { mutableStateOf(false) }
    var durum by remember { mutableStateOf("") }

    // Önbellekteki eski kartlarda anahtar yok; açılışta sunucudan tazelenir.
    LaunchedEffect(Unit) {
        library.sync()
        runCatching { firstFocus.requestFocus() }
    }
    NmBackHandler(enabled = true) { onBack() }

    val liste = library.watched.filter { it.contentKey.isNotBlank() }
    val n = secili.size

    fun sec(yeni: Set<String>) { secili = yeni; onayBekliyor = false; durum = "" }

    Box(Modifier.fillMaxSize().background(NmColor.Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV)) {
            Text(
                "🧹  Devam Et'i temizle",
                fontWeight = FontWeight.Bold,
                fontSize = NmType.ScreenTitle,
                color = NmColor.Primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                durum.ifEmpty { "OK: seç / bırak  ·  GERİ: çık" },
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Label,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.focusGroup().padding(bottom = 14.dp),
            ) {
                TouchButton(
                    "Tümünü seç",
                    onClick = { sec(liste.map { it.contentKey }.toSet()) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
                TouchButton("Seçimi kaldır", onClick = { sec(emptySet()) })
                TouchButton(
                    when {
                        siliniyor -> "Siliniyor…"
                        onayBekliyor -> "Emin misin? Sil ($n)"
                        else -> "Seçilenleri sil ($n)"
                    },
                    accent = onayBekliyor,
                    onClick = {
                        if (n == 0 || siliniyor) return@TouchButton
                        if (!onayBekliyor) { onayBekliyor = true; return@TouchButton }
                        siliniyor = true
                        scope.launch {
                            val ok = library.removeWatched(secili.toList())
                            durum = if (ok) "$n kayıt silindi."
                            else "Silinemedi — sunucuya ulaşılamadı. Tekrar deneyin."
                            if (ok) secili = emptySet()
                            onayBekliyor = false
                            siliniyor = false
                        }
                    },
                )
                TouchButton("Geri", onClick = onBack)
            }
            if (liste.isEmpty()) {
                Text("Devam Et listesi boş.", fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
                contentPadding = PaddingValues(bottom = NmDim.SafeV),
                verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
            ) {
                items(liste, key = { it.contentKey }) { item ->
                    val k = item.contentKey
                    SecimSatiri(item, k in secili) { sec(if (k in secili) secili - k else secili + k) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SecimSatiri(item: MediaItem, selected: Boolean, onToggle: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    val bolum = parseEpisodeRef(item.episodeRef)?.let { (s, e) -> "  ·  S$s B$e" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    isFocused -> NmColor.Primary
                    selected -> NmColor.PrimarySelected
                    else -> NmColor.Surface
                }
            )
            .nmFocusRing(isFocused, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onToggle() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(NmDim.PanelIconSize)
                .border(2.dp, if (isFocused) NmColor.OnPrimary else NmColor.Primary, RoundedCornerShape(6.dp))
                .background(if (selected) NmColor.Primary else NmColor.Surface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", fontSize = NmType.Label, color = NmColor.OnPrimary, fontWeight = FontWeight.Bold)
        }
        Text(
            item.title.orEmpty() + bolum,
            fontSize = NmType.Body,
            color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (isFocused || selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
