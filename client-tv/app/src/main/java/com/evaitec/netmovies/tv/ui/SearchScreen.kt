package com.evaitec.netmovies.tv.ui

import com.evaitec.netmovies.tv.data.kullaniciMesaji
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.input.NmBackHandler
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRingOnly
import kotlinx.coroutines.launch

// Arama, Gözat'ın içinden çıkıp KENDİ ekranı oldu: büyüteç ana ekranın sol
// üstünde ve doğrudan buraya girer. Gözat'ın kendi arama kutusu duruyor —
// oradaki arama kaynak rafları içinde kalır, buradaki bağımsızdır.
//
// Durum (sorgu + sonuçlar) MainActivity'de yaşayan `SearchState`'te tutulur:
// ekran bileşimden çıkınca `remember` ölüyor ve GERİ'den dönen kullanıcı aramayı
// baştan yazmak zorunda kalıyordu (Dean: "sonrasında tekrar girersek
// bulamıyoruz").

class SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<MediaItem>?>(null)
    var loading by mutableStateOf(false)
    /** Arama kutusu açık mı — dönüşte klavyeyi yeniden açmamak için. */
    var typing by mutableStateOf(false)
}

/** Son aranan metinler; cihazda kalır, oturum aşar. */
class SearchHistory(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("netmovies_search", Context.MODE_PRIVATE)

    fun all(): List<String> =
        prefs.getString("history", "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    fun add(term: String) {
        val temiz = term.trim()
        if (temiz.isEmpty()) return
        // Aynı metin tekrar aranınca listede yukarı taşınır, ikinci kayıt açılmaz.
        val yeni = (listOf(temiz) + all().filterNot { it.equals(temiz, ignoreCase = true) }).take(MAX)
        prefs.edit().putString("history", yeni.joinToString("\n")).apply()
    }

    fun clear() = prefs.edit().remove("history").apply()

    private companion object {
        const val MAX = 40
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(state: SearchState, onSelect: (MediaItem) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val history = remember(context) { SearchHistory(context) }
    var gecmis by remember { mutableStateOf(history.all()) }
    var hepsiniGoster by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    NmBackHandler {
        // Sonuç ekranındayken GERİ arama listesine döner, oradan ana ekrana.
        if (state.results != null) state.results = null else onBack()
    }

    // Ağ hatası "sonuç yok" gibi görünmesin: ayrı mesaj + Tekrar dene.
    var aramaHatasi by remember { mutableStateOf<String?>(null) }

    fun ara(terim: String) {
        val temiz = terim.trim()
        if (temiz.isEmpty()) return
        state.query = temiz
        state.typing = false
        state.results = emptyList()
        state.loading = true
        aramaHatasi = null
        history.add(temiz)
        gecmis = history.all()
        scope.launch {
            state.results = runCatching { Network.api.searchAll(temiz).result }
                .onFailure { aramaHatasi = it.kullaniciMesaji("Arama yapılamadı") }
                .getOrDefault(emptyList())
            state.loading = false
        }
    }

    // Ekrana ilk girişte odak arama kutusunda; dönüşte sonuçlar duruyorsa
    // kutuyu açmaz, kullanıcı listeye devam eder.
    LaunchedEffect(Unit) {
        if (state.results == null) state.typing = true
    }

    Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH)) {
        NmSearchHeader(
            title = "🔎  Arama",
            open = state.typing,
            query = state.query,
            onQueryChange = { state.query = it },
            onOpen = { state.typing = true },
            onSearch = { ara(state.query) },
        )

        when {
            state.results != null -> {
                Text(
                    text = if (state.loading) "Aranıyor: ${state.query}…"
                           else "Sonuç: ${state.query} (${state.results!!.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = NmType.RowTitle,
                    color = NmColor.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val hata = aramaHatasi
                if (hata != null && !state.loading) {
                    ErrorWithRetry(hata) { ara(state.query) }
                } else if (state.results!!.isEmpty() && !state.loading) {
                    Kutu("Sonuç bulunamadı — başka bir yazım deneyin.")
                } else {
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize().focusGroup(),
                        columns = GridCells.Adaptive(minSize = NmDim.GridPosterMin),
                        contentPadding = PaddingValues(bottom = NmDim.SafeV),
                        horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                        verticalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                    ) {
                        items(state.results!!.size) { i ->
                            val oge = state.results!![i]
                            BrowsePoster(oge) { onSelect(oge) }
                        }
                    }
                }
            }

            gecmis.isEmpty() -> Kutu("Aramak için büyüteç kutusuna yazın.")

            else -> {
                // Üstte son birkaç arama hızlı erişim için; listenin tamamı
                // istenirse açılır (Dean: "üstte birkaç arama cümlesi, aşağı
                // kadar doldurma, hepsini göster koyabiliriz").
                val gosterilen = if (hepsiniGoster) gecmis else gecmis.take(KISA_GECMIS)
                Text(
                    text = "Son Aramalar (${gecmis.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = NmType.RowTitle,
                    color = NmColor.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(bottom = NmDim.SafeV),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(gosterilen.size) { i ->
                        GecmisSatiri(gosterilen[i]) { ara(gosterilen[i]) }
                    }
                    if (!hepsiniGoster && gecmis.size > KISA_GECMIS) {
                        item { GecmisSatiri("⤵  Hepsini göster (${gecmis.size})") { hepsiniGoster = true } }
                    }
                    item {
                        GecmisSatiri("🗑  Geçmişi temizle") {
                            history.clear()
                            gecmis = emptyList()
                            hepsiniGoster = false
                        }
                    }
                }
            }
        }
    }
}

private const val KISA_GECMIS = 6

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GecmisSatiri(metin: String, onSec: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) NmColor.SurfaceHigh else NmColor.Surface)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSec() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = metin,
            fontSize = NmType.Label,
            color = NmColor.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Kutu(metin: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(metin, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}
