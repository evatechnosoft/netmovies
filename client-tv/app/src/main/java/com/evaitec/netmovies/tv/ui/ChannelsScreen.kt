package com.evaitec.netmovies.tv.ui

import com.evaitec.netmovies.tv.input.NmBackHandler
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
import androidx.compose.ui.input.key.onKeyEvent
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
    var category by remember { mutableStateOf<String?>(null) }   // null = Tümü, FAV = favoriler
    // Favori kanallar SUNUCUDA (prefs): 170+ kanal içinde hep aynı 7-8 tanesi
    // izleniyor, her seferinde listeyi taramak saçma. Cihazda tutulmaz — başka
    // TV'den girince ya da uygulama yeniden kurulunca kaybolmasın.
    var favUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { Network.api.quickChannels().result }
            .onSuccess { all = it }
            .onFailure { error = it.message ?: "Kanallar alınamadı" }
        favUrls = runCatching { okuFavoriler(Network.api.prefsGet().result) }.getOrDefault(emptySet())
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
    val shown = remember(all, category, query, favUrls) {
        val c = category
        val sonuc = when (c) {
            null -> all
            FAV_KATEGORI -> all.filter { it.url in favUrls }
            else -> all.filter { ch -> ch.category.orEmpty().split(";").any { it.trim() == c } }
        }
        val q = trNormal(query.trim())
        val suzulmus = if (q.isEmpty()) sonuc else sonuc.filter { trNormal(it.title.orEmpty()).contains(q) }
        // Favoriler listenin başında: sabit izlenen kanallar en üstte olsun.
        if (c == FAV_KATEGORI) suzulmus else suzulmus.sortedByDescending { it.url in favUrls }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun favoriDegistir(ch: MediaItem) {
        val yeni = if (ch.url in favUrls) favUrls - ch.url else favUrls + ch.url
        favUrls = yeni
        scope.launch {
            runCatching { Network.api.prefsPost(mapOf(FAV_ANAHTAR to yeni.joinToString("\n"))) }
        }
    }
    val firstChannel = remember { FocusRequester() }
    LaunchedEffect(shown.firstOrNull()?.url) {
        if (shown.isNotEmpty()) runCatching { firstChannel.requestFocus() }
    }

    // GERİ: önce aramayı, sonra tür seçimini bırakır, en son ekrandan çıkar.
    NmBackHandler {
        when {
            searchOpen -> { searchOpen = false; query = "" }
            category != null -> category = null
            else -> onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        NmSearchHeader(
            title = "📡 Canlı TV — ${shown.size} kanal",
            open = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpen = { searchOpen = true },
            // Kanal araması SUNUCUYA gitmez: liste zaten elde, her tuşta yerinde süzülür.
            onSearch = {},
        )

        if (categories.isNotEmpty() || favUrls.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { CatChip("Tümü", category == null) { category = null } }
                if (favUrls.isNotEmpty()) {
                    item {
                        CatChip("★ Favoriler (${favUrls.size})", category == FAV_KATEGORI) {
                            category = FAV_KATEGORI
                            scope.launch { listState.scrollToItem(0) }
                        }
                    }
                }
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
            shown.isEmpty() -> Center(
                when {
                    query.isNotBlank() -> "\"$query\" ile eşleşen kanal yok"
                    category == FAV_KATEGORI -> "Henüz favori kanal yok — listede SAĞ ok ile ekle"
                    else -> "Bu türde kanal yok"
                }
            )
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
                        favori = ch.url in favUrls,
                        modifier = if (i == 0) Modifier.focusRequester(firstChannel) else Modifier,
                        onToggleFavori = { favoriDegistir(ch) },
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
private fun ChannelRow(
    channel: MediaItem,
    favori: Boolean,
    modifier: Modifier = Modifier,
    onToggleFavori: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) NmColor.SurfaceHigh else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            // Liste dikey; SAĞ ok boşta duruyor → favori aç/kapat. Oynatıcıdaki
            // "boşta duran tuşu kullan" deseninin aynısı, yeni tuş öğrenilmiyor.
            .onKeyEvent { ke ->
                if (ke.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (ke.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) onToggleFavori()
                    true
                } else {
                    false
                }
            }
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
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.title.orEmpty(),
                fontSize = NmType.Body,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = NmColor.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Rehber varsa kategori yerine "şu an ne oynuyor" yazılır: kanal
            // listesinde asıl merak edilen bu, kategori zaten üstteki sekmede.
            val simdi = channel.simdi
            if (simdi != null && simdi.program.isNotBlank()) {
                Text(
                    text = "▶ " + simdi.program +
                        (if (simdi.sonraki.isNotBlank()) "   ›  " + simdi.sonraki else ""),
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!channel.category.isNullOrBlank()) {
                Text(
                    text = channel.category.orEmpty().replace(";", " · "),
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Yıldız hem durumu gösterir hem tuşu öğretir: odaktaki satırda favori
        // değilse soluk "☆ SAĞ" ipucu çıkar.
        if (favori) {
            Text("★", fontSize = NmType.Body, color = NmColor.Primary)
        } else if (focused) {
            Text("☆ SAĞ", fontSize = NmType.Caption, color = NmColor.OnSurfaceFaint)
        }
    }
}

// prefs'teki favori kanal kaydı: satır başına bir kanal adresi.
private const val FAV_ANAHTAR = "fav_channels"
// Kategori çipi için ayrılmış değer; gerçek bir tür adı olamaz.
private const val FAV_KATEGORI = "@@favoriler"

private fun okuFavoriler(prefs: Map<String, kotlinx.serialization.json.JsonElement>): Set<String> {
    val ham = prefs[FAV_ANAHTAR] ?: return emptySet()
    val metin = (ham as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return emptySet()
    return metin.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toSet()
}

// Aramada Türkçe büyük/küçük harf: varsayılan lowercase() "İ" harfini birleşik
// noktalı i'ye çeviriyor ve "İZLE" araması "izle" ile eşleşmiyor.
private fun trNormal(s: String): String = s.lowercase(java.util.Locale("tr", "TR"))

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}
