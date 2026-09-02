package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.HomeState
import com.evaitec.netmovies.tv.HomeViewModel
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmBottomScrim
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmFocusRingOnly
import com.evaitec.netmovies.tv.ui.theme.nmFocusScale
import com.evaitec.netmovies.tv.ui.theme.nmScale

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvTopBarButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(isFocused, label = "topBarBtnScale")
    val shape = RoundedCornerShape(NmDim.PillRadius)

    Box(
        modifier = modifier
            .nmScale(scale)
            .clip(shape)
            .background(if (isFocused) NmColor.Primary else NmColor.SurfaceHigh)
            .nmFocusRing(isFocused, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = NmType.Label,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onSelect: (MediaItem) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    showVault: Boolean,
    onToggleVault: () -> Unit,
    onToggleMouseMode: () -> Unit = {},
    library: Library,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is HomeState.Loading -> Center("Yükleniyor…")
        is HomeState.Error   -> {
            // İçerik yüklenemese bile Favoriler/İzlenenler doluysa onları göster.
            if (library.favorites.isEmpty() && library.watched.isEmpty()) {
                ErrorWithRetry(s.message, onRetry = vm::load)
            } else {
                CategoryRows(emptyList(), library, onSelect, onOpenBrowse, onOpenKeyMap, onOpenVault, showVault, onToggleVault, onToggleMouseMode)
            }
        }
        is HomeState.Ready   -> {
            if (s.items.isEmpty() && library.favorites.isEmpty() && library.watched.isEmpty()) {
                ErrorWithRetry("İçerik yok", onRetry = vm::load)
            } else {
                CategoryRows(s.items, library, onSelect, onOpenBrowse, onOpenKeyMap, onOpenVault, showVault, onToggleVault, onToggleMouseMode)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CategoryRows(
    items: List<MediaItem>,
    library: Library,
    onSelect: (MediaItem) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    showVault: Boolean,
    onToggleVault: () -> Unit,
    onToggleMouseMode: () -> Unit,
) {
    // Kategoriye göre grupla (web ana sayfadaki yatay raylar gibi). Sıra korunur.
    val groups = remember(items) {
        items.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Yeni Çıkanlar" }
    }
    // Kitaplık satırları en üstte (İzlenenler + Favoriler), sonra agregasyon kategorileri.
    val sections = buildList {
        if (library.watched.isNotEmpty()) add("İzlenenler" to library.watched.toList())
        if (library.favorites.isNotEmpty()) add("Favoriler" to library.favorites.toList())
        groups.forEach { add(it.key to it.value) }
    }

    // Poster uzun-bas menüsü.
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }

    // Ayarlar menüsü durumu
    var showSettingsMenu by remember { mutableStateOf(false) }

    // İlk poster karta başlangıç focus'u ver — yoksa D-pad'de hiçbir şey seçilemiyor.
    val firstFocus = remember { FocusRequester() }
    val firstKey = sections.firstOrNull()?.first
    LaunchedEffect(firstKey) {
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onToggleMouseMode() }
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = NmDim.SafeV, bottom = NmDim.SafeV + 16.dp),
            verticalArrangement = Arrangement.spacedBy(NmDim.RowGap),
        ) {
            item { TopBar(onOpenBrowse, onToggleVault, onToggleMouseMode) { showSettingsMenu = true } }

            sections.forEachIndexed { sIndex, (title, list) ->
                item {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = NmType.RowTitle,
                        color = NmColor.OnSurface,
                        modifier = Modifier.padding(start = NmDim.SafeH, bottom = 2.dp),
                    )
                }
                item {
                    LazyRow(
                        modifier = Modifier.focusGroup(),
                        // Odak büyüteci kartı taşırdığı için dikey nefes payı bırakılır.
                        contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                    ) {
                        itemsIndexed(list) { index, item ->
                            val cardModifier =
                                if (sIndex == 0 && index == 0) Modifier.focusRequester(firstFocus)
                                else Modifier
                            PosterCard(
                                item = item,
                                isFavorite = library.isFavorite(item),
                                onClick = { onSelect(item) },
                                onLongPress = { menuItem = item },
                                modifier = cardModifier,
                            )
                        }
                    }
                }
            }
        }

        menuItem?.let { item ->
            PosterMenu(
                item = item,
                isFavorite = library.isFavorite(item),
                onPlay = { menuItem = null; onSelect(item) },
                onToggleFavorite = { library.toggleFavorite(item); menuItem = null },
                onClose = { menuItem = null },
            )
        }

        if (showSettingsMenu) {
            SettingsMenu(
                showVault = showVault,
                onOpenKeyMap = onOpenKeyMap,
                onOpenVault = onOpenVault,
                onToggleVault = onToggleVault,
                onToggleMouseMode = onToggleMouseMode,
                onClose = { showSettingsMenu = false }
            )
        }
    }
}

// Sade üst bar: marka + tam genişlik arama + ayarlar. Tek odak grubu.
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TopBar(
    onOpenBrowse: () -> Unit,
    onToggleVault: () -> Unit,
    onToggleMouseMode: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(horizontal = NmDim.SafeH, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NetMovies",
            fontWeight = FontWeight.ExtraBold,
            fontSize = NmType.Wordmark,
            color = NmColor.Primary,
            modifier = Modifier
                .padding(end = 4.dp)
                .combinedClickable(
                    onClick = onToggleMouseMode,
                    onLongClick = onToggleMouseMode,
                ),
        )
        HomeSearchBarButton(
            modifier = Modifier.weight(1f),
            onClick = onOpenBrowse,
            onLongClick = onToggleVault,
        )
        TvTopBarButton("⚙  Ayarlar", onClick = onOpenSettings, onLongClick = onToggleMouseMode)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    item: MediaItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-pad focus → "büyüteç": kart büyür, beyaz odak halkası, üstte kalır.
    // combinedClickable → OK tek bas = oynat, OK basılı tut = menü (favori vb.).
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScaleCard, label = "posterScale")
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = modifier
            .width(NmDim.PosterWidth)
            .aspectRatio(2f / 3f)
            .nmScale(scale)
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(NmColor.SurfaceHigh)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        PosterImage(poster = item.poster, title = item.title)
        // Başlık degradesi — poster ne olursa olsun yazı okunur kalsın.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(54.dp)
                .background(nmBottomScrim),
        )
        if (isFavorite) {
            Text(
                text = "★",
                color = NmColor.Star,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
        Text(
            text = item.title.orEmpty(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = NmType.Caption,
            color = NmColor.OnSurface,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

// Poster uzun-bas menüsü — sayfa açmadan hızlı aksiyonlar.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterMenu(
    item: MediaItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    ModalCard(title = item.title ?: "Seçenekler") {
        MenuRow("▶  Oynat", onPlay)
        MenuRow(if (isFavorite) "★  Favorilerden çıkar" else "☆  Favorilere ekle", onToggleFavorite)
        MenuRow("Kapat", onClose)
    }
}

// Ortak modal kabuğu: scrim + panel + başlık. Menülerin görünümü tek yerden gelir.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModalCard(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(NmDim.PanelRadius)
    Box(
        Modifier.fillMaxSize().background(NmColor.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(NmDim.DialogWidth)
                .clip(shape)
                .background(NmColor.SurfaceDialog)
                .nmFocusRing(false, shape)
                .focusGroup()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = NmType.ScreenTitle,
                color = NmColor.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isFocused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(isFocused, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
            Spacer(Modifier.height(16.dp))
            TouchButton("Tekrar dene", onRetry, accent = true)
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeSearchBarButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(isFocused, NmDim.FocusScaleRow, label = "searchScale")
    val shape = RoundedCornerShape(NmDim.PillRadius)

    Box(
        modifier = modifier
            .nmScale(scale)
            .clip(shape)
            .background(if (isFocused) NmColor.SurfaceHigh else NmColor.Surface)
            .nmFocusRing(isFocused, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "🔎  Film, dizi veya tür ara…",
            color = if (isFocused) NmColor.OnSurface else NmColor.OnSurfaceFaint,
            fontWeight = FontWeight.Medium,
            fontSize = NmType.Label,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsMenu(
    showVault: Boolean,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    onToggleVault: () -> Unit,
    onToggleMouseMode: () -> Unit,
    onClose: () -> Unit,
) {
    ModalCard(title = "Ayarlar") {
        MenuRow("⚙  Buton Eşleme", onClick = { onClose(); onOpenKeyMap() })
        MenuRow("🖱  Sanal Fare Modu", onClick = { onClose(); onToggleMouseMode() })
        MenuRow(if (showVault) "👁  Özel Koleksiyonu Gizle" else "👁  Özel Koleksiyonu Göster", onClick = { onClose(); onToggleVault() })
        if (showVault) {
            MenuRow("🔒  Özel Koleksiyon'a Gir", onClick = { onClose(); onOpenVault() })
        }
        MenuRow("✕  Kapat", onClose)
    }
}
