package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.encodedUrl
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import kotlinx.coroutines.launch

// Canlı TV: 170+ kanal tek uçtan (`/api/v1/quick_channels`). Ana ekrandaki "live"
// rafı posterleri yan yana diziyordu — kanal aramak için kötü. Burada kanal LİSTESİ
// ve üstte tür süzgeci var; kanal adı okunur, logo yalnız tanıma yardımcısı.

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsScreen(onSelect: (MediaItem) -> Unit, onBack: () -> Unit) {
    var all by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }   // null = Tümü

    LaunchedEffect(Unit) {
        runCatching { Network.api.quickChannels().result }
            .onSuccess { all = it }
            .onFailure { error = it.message ?: "Kanallar alınamadı" }
        loading = false
    }

    // Kaynak "Animation;Kids" gibi çoklu tür veriyor; her parça ayrı süzgeç olur.
    val categories = remember(all) {
        all.flatMap { it.category.orEmpty().split(";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val shown = remember(all, category) {
        val c = category
        if (c == null) all
        else all.filter { ch -> ch.category.orEmpty().split(";").any { it.trim() == c } }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstChannel = remember { FocusRequester() }
    LaunchedEffect(shown.firstOrNull()?.url) {
        if (shown.isNotEmpty()) runCatching { firstChannel.requestFocus() }
    }

    // GERİ: tür seçiliyken önce Tümü'ye döner, sonra ekrandan çıkar.
    BackHandler {
        if (category != null) category = null else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "📡 Canlı TV — ${shown.size} kanal",
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(start = NmDim.SafeH, top = NmDim.SafeV, bottom = 6.dp),
        )

        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { CatChip("Tümü", category == null) { category = null } }
                items(categories.size) { i ->
                    CatChip(categories[i], category == categories[i]) {
                        category = categories[i]
                        scope.launch { listState.scrollToItem(0) }
                    }
                }
            }
        }

        when {
            loading -> Center("Kanallar yükleniyor…")
            error != null -> Center(error!!)
            shown.isEmpty() -> Center("Bu türde kanal yok")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().focusGroup().padding(horizontal = NmDim.SafeH),
                contentPadding = PaddingValues(bottom = NmDim.SafeV),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shown.size) { i ->
                    val ch = shown[i]
                    ChannelRow(
                        channel = ch,
                        modifier = if (i == 0) Modifier.focusRequester(firstChannel) else Modifier,
                    ) {
                        // quick_channels HAM url veriyor; oynatma zinciri kodlu bekliyor.
                        onSelect(ch.copy(url = encodedUrl(ch.url)))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatChip(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.PillRadius)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused -> NmColor.Primary
                    active -> NmColor.PrimarySelected
                    else -> NmColor.Surface
                }
            )
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = NmType.Caption,
            maxLines = 1,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(channel: MediaItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) NmColor.SurfaceHigh else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NmColor.SurfaceHigh),
        ) {
            PosterImage(poster = channel.poster, title = channel.title)
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = channel.title.orEmpty(),
                fontSize = NmType.Body,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = NmColor.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!channel.category.isNullOrBlank()) {
                Text(
                    text = channel.category.orEmpty().replace(";", " · "),
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}
