package com.evaitec.netmovies.tv.ui

import androidx.compose.runtime.mutableIntStateOf
import com.evaitec.netmovies.tv.data.kullaniciMesaji
import com.evaitec.netmovies.tv.input.NmBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.FilmSerisi
import com.evaitec.netmovies.tv.data.FilmSerisiParcasi
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.okuSatirlar
import com.evaitec.netmovies.tv.data.PluginInfo
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmBottomScrim
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmFocusRingOnly
import com.evaitec.netmovies.tv.ui.theme.nmFocusScale
import com.evaitec.netmovies.tv.ui.theme.nmScale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder

// Özel Koleksiyon'a düşen eklenti adları — sunucu ulaşılamazsa kullanılan YEDEK.
// Asıl liste /api/admin/config'ten gelir (web /admin ile aynı kaynak); burada sabit
// tutulsaydı web'de yapılan değişiklik TV'ye hiç yansımazdı.
internal val VAULT_FALLBACK = listOf("porner", "porn", "spank", "hamster", "oxax", "maza")

/** Özel Koleksiyon eklentisi mi (ad parçasıyla — sunucu listesi gelmeden de çalışır). */
internal fun isVaultPlugin(name: String?): Boolean =
    name != null && VAULT_FALLBACK.any { name.contains(it, ignoreCase = true) }

private fun decode(s: String): String =
    runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

/** Ekran açılır açılmaz paralel çekilecek raf sayısı (üstteki görünür bölge). */
private const val PREFETCH_SHELVES = 6

/** Bir raf = (eklenti, kategori) çifti. Ana sayfadaki gibi yatay poster şeridi. */
private data class Shelf(
    val plugin: PluginInfo,
    val encUrl: String,
    val encCat: String,
) {
    val key: String get() = "${plugin.name}|$encCat"
    val title: String get() = "${plugin.name} · ${decode(encCat)}"
}

/** Rafın bir sayfasını çeker; hata/zaman aşımı boş liste (raf gizlenir). */
private suspend fun fetchShelf(shelf: Shelf, page: Int = 1): List<MediaItem> =
    withTimeoutOrNull(20_000) {
        runCatching {
            Network.api.getMainPage(shelf.plugin.name, page, shelf.encUrl, shelf.encCat).result
                .map { it.copy(plugin = shelf.plugin.name, category = decode(shelf.encCat)) }
        }.getOrDefault(emptyList())
    } ?: emptyList()

// Gözat: her kategori kendi içeriğini poster rafı olarak gösterir (düz metin listesi yerine).
// Arama üstte sadece büyüteç; seçilince metin alanına dönüşür.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    // Ekranın yeri (kaynak, kaydırma, odak, raf önbelleği). Oynatıcıdan GERİ ile
    // dönüldüğünde aynı yere düşülsün diye DIŞARIDA tutulur.
    state: BrowseState,
    showVault: Boolean = false,
    vaultMode: Boolean = false,
    onSelect: (MediaItem) -> Unit,
    // Telefon kumandasından yazılan metin. TV'de klavye kullanmak işkence olduğu
    // için arama terimi telefondan gelir; geldiğinde doğrudan aranır.
    remoteQuery: String? = null,
    onRemoteQueryUsed: () -> Unit = {},
    // Ajandadan gelen başlıkta arama sonucu listede bırakılmaz: tek eşleşme
    // varsa doğrudan açılır (Dean: "onu da aramaya atıyor, direk diziye
    // gitmiyor"). Birden çok sonuçta liste kalır — yanlış diziyi açmaktansa
    // seçtirmek doğru.
    otomatikAc: Boolean = false,
    // "TV" hapı Canlı TV ekranını açar; GERİ Gözat'a döner.
    onOpenChannels: () -> Unit = {},
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rawPlugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deneme by remember { mutableIntStateOf(0) }

    // Ozel Koleksiyon filtresi. Yeni bir kaynak eklendiginde anahtar kelimesi
    // BURAYA eklenir; listede olmayan eklenti normal raflarda gorunur (sessiz
    // sizinti). Tek nokta olsun diye ayri sabit.
    // Sunucudaki liste tam eklenti adı verir; yedek liste parça eşleşmesiyle çalışır.
    var adultFromServer by remember { mutableStateOf<List<String>>(emptyList()) }
    // Yönetim panelinde kapatılan kaynaklar (hidden_providers: M3UPlaylist, SezonlukDizi…)
    // web'de süzülüyordu, TV'de hâlâ hap olarak duruyordu (Dean, 3 Ekim: "m3u listem
    // kullanılmıyor, şimdilik disable"). Canlı TV etkilenmez: kanallar ayrı uçtan gelir.
    var hiddenFromServer by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        runCatching { Network.api.clientConfig().result }
            .onSuccess {
                if (it.adultProviders.isNotEmpty()) adultFromServer = it.adultProviders.map(String::lowercase)
                hiddenFromServer = it.hiddenProviders.toSet()
            }
    }
    // İkisi BİRDEN: sunucu tam adla eşleşir, yedek liste parça eşleşmesiyle yakalar.
    // Yalnız sunucuya güvenilseydi listede olmayan yeni bir kaynak sessizce sızardı;
    // yalnız yedeğe güvenilseydi web'deki ayar TV'ye hiç ulaşmazdı.
    val isAdultPlugin = { name: String ->
        val n = name.lowercase()
        n in adultFromServer || VAULT_FALLBACK.any { n.contains(it) }
    }

    val plugins = remember(rawPlugins, showVault, vaultMode, adultFromServer, hiddenFromServer) {
        val gorunen = rawPlugins.filter { it.name !in hiddenFromServer }
        if (vaultMode) {
            gorunen.filter { isAdultPlugin(it.name) }
        } else if (showVault) {
            gorunen
        } else {
            gorunen.filter { !isAdultPlugin(it.name) }
        }
    }

    // Tek kaynak seçilebilir: tüm eklentilerin kategorileri alt alta dizilince
    // ekranda 40+ raf oluyordu ve aşağıdan yukarı dönmek işkenceydi.
    // null = "Tümü" (eski davranış).
    // Özel Koleksiyon'a geçilince normal listedeki seçim geçersiz kalır (o eklenti
    // burada yok) — yoksa ekran boş görünürdü.
    val selectedPlugin = state.plugin?.takeIf { name -> plugins.any { it.name == name } }

    val shelves = remember(plugins, selectedPlugin) {
        plugins.filter { selectedPlugin == null || it.name == selectedPlugin }
            .flatMap { plugin ->
                plugin.mainPage.entries.map { Shelf(plugin, it.key, it.value) }
            }
    }

    // Raf içerikleri: ekran boyunca yaşar → yukarı/aşağı gezinirken tekrar çekilmez.
    val shelfCache = state.cache
    // Aynı rafı hem önyükleme hem de satırın kendisi çekmesin.
    val started = state.started

    // İlk raflar ekrana girmeyi beklemeden PARALEL çekilir; sunucu tarafı 30 dk
    // cache'lediği için sonraki açılışlar anında gelir (Dean: "çok geç yükleniyor").
    LaunchedEffect(shelves) {
        // Özel Koleksiyon'da HEPSİ: 6'dan sonrası iskelet kalıyordu, iskelet odak
        // almadığı için aşağı inilemiyor, inilemeyince de raf yüklenmiyordu (Dean:
        // "altlardaki listeye inmiyor"). Koleksiyon küçük (~13 raf), sunucu cache'li.
        val head = shelves.take(if (vaultMode) shelves.size else PREFETCH_SHELVES).filter { started.add(it.key) }
        if (head.isEmpty()) return@LaunchedEffect
        coroutineScope {
            head.map { shelf -> async { shelfCache[shelf.key] = fetchShelf(shelf) } }.awaitAll()
        }
    }

    // Kaydırma konumu ve son odaklı raf ekran seviyesinde tutulur; arama sonucuna
    // girip çıkınca liste en üstten başlamasın (Dean: "en üstten başlıyor, olmuyor").
    val listState = state.listState
    val focusedShelf = state.shelf
    // GERİ ile "en üste dön": listeyi kaydırmak yetmiyor, odak alt rafta kalınca ilk
    // D-pad basışı listeyi geri aşağı çekiyordu → odak da ilk rafa taşınır.
    var focusResetKey by remember { mutableStateOf(0) }

    // Tüm raflar çekildi VE hepsi boş döndü mü (ölü kaynak). Kısmen yüklüyse false:
    // dolu raf varken "ulaşılamıyor" yazmak yanlış olur.
    val allShelvesEmpty by remember {
        derivedStateOf {
            shelves.isNotEmpty() && shelves.all { shelfCache[it.key]?.isEmpty() == true }
        }
    }

    // "Seriler" sekmesi: film serileri (franchise) ayrı kaynaktan gelir, raflarla
    // karışmaz. Plugin seçimi gibi persist edilmez — GERİ ya da kaynak değişince kapanır.
    var seriesMode by remember { mutableStateOf(false) }
    var filmSerileri by remember { mutableStateOf<List<FilmSerisi>>(emptyList()) }
    var filmSerileriLoading by remember { mutableStateOf(false) }
    LaunchedEffect(seriesMode) {
        if (seriesMode && filmSerileri.isEmpty() && !filmSerileriLoading) {
            filmSerileriLoading = true
            filmSerileri = runCatching { Network.api.filmSerileri().result }.getOrDefault(emptyList())
            filmSerileriLoading = false
        }
    }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by state::results
    var resultsTitle by state::resultsTitle
    var resultsLoading by remember { mutableStateOf(false) }
    var acilacakBaslik by remember { mutableStateOf<String?>(null) }
    // Yıldızlı kaynaklar SUNUCUDA (prefs): kanal favorileriyle aynı yer, aynı
    // mantık — başka TV'den girince ya da yeniden kurunca kaybolmasın.
    var favKaynaklar by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Çiplerin elle dizilmiş sırası (basılı tut → taşı); aynı prefs deposunda.
    var kaynakSirasi by remember { mutableStateOf<List<String>>(emptyList()) }
    var kaynakPuani by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        runCatching { Network.api.prefsGet().result }.onSuccess { prefs ->
            favKaynaklar = okuSatirlar(prefs, FAV_KAYNAK_ANAHTAR).toSet()
            kaynakSirasi = okuSatirlar(prefs, KAYNAK_SIRA_ANAHTAR)
        }
        runCatching { Network.api.sourceScore().result.kaynaklar }
            .onSuccess { l -> kaynakPuani = l.associate { it.plugin to it.puan } }
    }

    LaunchedEffect(deneme) {
        try {
            rawPlugins = Network.api.getAllPlugins().result
        } catch (e: Exception) {
            error = e.kullaniciMesaji("Kaynaklar yüklenemedi")
        }
        loading = false
    }

    // GERİ tek yönde ve kısa: arama → sonuç → tüm kaynaklar → ana ekran.
    //
    // Araya "önce listeyi en üste kaydır" adımı giriyordu; aşağıdayken GERİ'ye
    // basınca EKRAN DEĞİŞMİYOR, yalnız odak raflar arasında geziyordu ve çıkmak
    // üç basış sürüyordu (Dean: "orta bantlarda geziyor... bir geri daha basarsam
    // döner"). Kaydırma konumunu düzeltmek GERİ'nin işi değil — eklentiden
    // çıkarken zaten en üste dönülüyor.
    val browseScope = rememberCoroutineScope()
    NmBackHandler(enabled = true) {
        when {
            searchOpen      -> { searchOpen = false; query = "" }
            results != null -> results = null
            // Seriler'den çıkış diğer kaynak hapları gibi "Tümü"ne döner.
            seriesMode      -> seriesMode = false
            selectedPlugin != null -> {
                state.plugin = null
                state.shelf = 0
                // Kart indeksi de sıfırlanmalı: yalnız raf sıfırlanınca liste
                // başa dönüyor ama odak o raftaki ESKİ karta düşüyordu — üstte
                // sağlayıcı yazıyor, ekran ortalardaki bir posterde duruyordu.
                state.card = 0
                focusResetKey++
                browseScope.launch { listState.scrollToItem(0) }
            }
            else            -> onBack()
        }
    }

    // Tüm eklentilerde paralel ara, birleştir.
    fun doSearch(q: String) {
        val term = q.trim()
        if (term.isEmpty()) return
        searchOpen = false
        resultsTitle = "Arama: $term"
        results = emptyList()
        resultsLoading = true
        scope.launch {
            // Tek istek: birleştirme, süzme ve varyantlar sunucuda (`/search_all`).
            // Eklenti eklenti çağırmak ham liste getiriyordu — sorguyu yok sayan
            // kaynak (DDizi 45 alakasız dizi) ve Özel Koleksiyon sonuçları
            // doğrudan ekrana düşüyordu.
            results = if (vaultMode) {
                // Özel Koleksiyon sunucunun genel aramasından BİLEREK dışarıda;
                // burada kaynaklar tek tek sorulur (ekranda zaten yalnız onlar var).
                val names = plugins.map { it.name }
                coroutineScope {
                    names.map { n ->
                        async {
                            withTimeoutOrNull(12_000) {
                                runCatching { Network.api.search(n, term).result.map { it.copy(plugin = n) } }
                                    .getOrDefault(emptyList())
                            } ?: emptyList()
                        }
                    }.awaitAll().flatten()
                }
            } else if (selectedPlugin == YOUTUBE) {
                // YouTube seçiliyken YouTube'da arar; genel arama YouTube'suz (Dean, 6 Ekim).
                runCatching { Network.api.searchAll(term, yt = "video", sadece = "youtube").result }.getOrDefault(emptyList())
            } else {
                runCatching { Network.api.searchAll(term, yt = "0").result }.getOrDefault(emptyList())
            }
            resultsLoading = false
        }
    }

    // Seri parçasına basınca o başlıkla ara, tek eşleşme varsa doğrudan aç —
    // Ajanda'dan gelen başlık akışıyla (remoteQuery/otomatikAc) AYNI yol.
    fun openSeriesPart(parca: FilmSerisiParcasi) {
        acilacakBaslik = parca.baslik
        doSearch(parca.baslik)
    }

    // Kumandadan metin geldiğinde ara. Eklentiler yüklenmeden arama boş döner —
    // `plugins` listesi dolana kadar bekler.
    LaunchedEffect(remoteQuery, plugins.size) {
        val metin = remoteQuery?.trim().orEmpty()
        if (metin.isNotEmpty() && plugins.isNotEmpty()) {
            query = metin
            acilacakBaslik = if (otomatikAc) metin else null
            doSearch(metin)
            onRemoteQueryUsed()
        }
    }

    // Arama bitince: tek sonuç ya da başlığı birebir tutan tek kayıt varsa aç.
    LaunchedEffect(results, resultsLoading) {
        val hedef = acilacakBaslik
        val liste = results
        if (hedef != null && liste != null && !resultsLoading) {
            acilacakBaslik = null
            val tam = liste.filter { it.title.equals(hedef, ignoreCase = true) }
            val acilacak = tam.singleOrNull() ?: liste.singleOrNull()
            if (acilacak != null) onSelect(acilacak)
        }
    }

    Column(Modifier.fillMaxSize()) {
        NmSearchHeader(
            title = if (vaultMode) "🗂 Koleksiyon" else "Gözat",
            open = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpen = { searchOpen = true },
            onSearch = { doSearch(query) },
        )
        if (results == null && !vaultMode && plugins.size > 1) {
            SourceChips(
                names = plugins.map { it.name },
                selected = selectedPlugin,
                seriesSelected = seriesMode,
                favoriler = favKaynaklar,
                puanlar = kaynakPuani,
                kayitliSira = kaynakSirasi,
                // Taşırken AŞAĞI: kaynağı gizle (yönetim panelindeki hidden_providers;
                // sunucu onu Yeni Çıkanlar/arama/zincirden de düşürür). Geri açmak:
                // Ayarlar → Yönetim Paneli.
                onGizle = { ad ->
                    hiddenFromServer = hiddenFromServer + ad
                    if (state.plugin == ad) state.plugin = null
                    android.widget.Toast.makeText(context, "$ad gizlendi — Yönetim Paneli'nden geri açılır", android.widget.Toast.LENGTH_LONG).show()
                    browseScope.launch {
                        runCatching {
                            val cfg = Network.api.adminConfig()
                            val eski = (cfg["hidden_providers"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                                .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                            val yeni = (eski + ad).distinct().map { kotlinx.serialization.json.JsonPrimitive(it) }
                            Network.api.saveAdminConfig(
                                kotlinx.serialization.json.JsonObject(cfg + ("hidden_providers" to kotlinx.serialization.json.JsonArray(yeni)))
                            )
                        }
                    }
                },
                onSiraKaydet = { yeni ->
                    kaynakSirasi = yeni
                    browseScope.launch {
                        runCatching {
                            Network.api.prefsPost(mapOf(KAYNAK_SIRA_ANAHTAR to yeni.joinToString("\n")))
                        }
                    }
                },
                onSelect = { name ->
                    seriesMode = false
                    state.plugin = name
                    state.shelf = 0
                    state.card = 0          // yalnız raf sıfırlanırsa odak eski kartta kalır
                    browseScope.launch { listState.scrollToItem(0) }
                },
                onSelectSeries = { seriesMode = true },
                onOpenChannels = onOpenChannels,
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                results != null -> ItemGrid(
                    title = resultsTitle,
                    items = results!!,
                    loading = resultsLoading,
                    onSelect = onSelect,
                )
                loading      -> Center("Eklentiler yükleniyor…")
                error != null -> ErrorWithRetry(error!!) { error = null; loading = true; deneme++ }
                seriesMode -> when {
                    filmSerileriLoading && filmSerileri.isEmpty() -> Center("Seriler yükleniyor…")
                    filmSerileri.isEmpty() -> Center("Seri bulunamadı")
                    else -> SeriesShelfList(filmSerileri, onSelectParca = ::openSeriesPart)
                }
                shelves.isEmpty() -> Center(
                    if (vaultMode) "Bu koleksiyonda kaynak yok"
                    else "Kaynak bulunamadı",
                )
                // Boş raf hiç çizilmez (ShelfRow'daki erken return). Hepsi boşsa ekranda
                // yalnız üst bar kalıyordu: kullanıcı açılıyor sanıp boşluğa bakıyordu.
                allShelvesEmpty -> Center(
                    if (vaultMode) "Bu koleksiyonun kaynağına şu an ulaşılamıyor"
                    else "Kaynaklara şu an ulaşılamıyor",
                )
                else -> ShelfList(
                    shelves = shelves,
                    cache = shelfCache,
                    started = started,
                    listState = listState,
                    focusedShelf = focusedShelf,
                    focusedCard = state.card,
                    focusResetKey = focusResetKey,
                    onShelfFocused = { state.shelf = it },
                    onCardFocused = { state.card = it },
                    onSelect = onSelect,
                )
            }
        }
    }
}

// --------------------------------------------------------------------- Üst bar
// Kapalıyken sadece büyüteç düğmesi; OK'a basınca metin alanı açılır (Dean: "çok kaba").
// Canlı TV ekranı da aynı barı kullanıyor — arama kutusu iki yerde ayrı ayrı
// yazılmasın diye paylaşıldı.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NmSearchHeader(
    title: String,
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onSearch: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { fieldFocus.requestFocus() } }
    val context = LocalContext.current
    val klavye = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NmDim.SafeH, end = NmDim.SafeH, top = NmDim.SafeV, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.weight(1f),
        )
        if (open) {
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(NmDim.PillRadius)
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .clip(shape)
                    .background(NmColor.SurfaceHigh)
                    .nmFocusRing(focused, shape)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = NmColor.OnSurface, fontSize = NmType.Body),
                    cursorBrush = SolidColor(NmColor.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onFocusChanged { focused = it.isFocused }
                        // GERİ klavyeyi kapatınca odak kutuda kalıyor ve OK klavyeyi
                        // geri açmıyordu (Compose OK'u yutuyor). Telefon kumandası
                        // bağlanınca sistem varsayılan klavyeyi "Mobil cihazınızdaki
                        // klavyeyi kullanın" köprüsüne çeviriyor; o durumda OK ekran
                        // klavyesi seçicisini açar — telefon yolu yine durur.
                        .onPreviewKeyEvent { ke ->
                            if (ke.key != Key.DirectionCenter) return@onPreviewKeyEvent false
                            if (ke.type == KeyEventType.KeyUp) ekranKlavyesiAc(context) { klavye?.show() }
                            true
                        },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Ara…", color = NmColor.OnSurfaceFaint, fontSize = NmType.Body)
                        }
                        inner()
                    },
                )
            }
        } else {
            IconPill(glyph = "🔎", onClick = onOpen)
        }
    }
}

/** Varsayılan klavye telefon köprüsüyse seçiciyi aç, değilse ekran klavyesini göster. */
private fun ekranKlavyesiAc(context: android.content.Context, goster: () -> Unit) {
    val ime = android.provider.Settings.Secure.getString(
        context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
    ).orEmpty()
    if (ime.contains(TELEFON_KOPRU_IME)) {
        (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showInputMethodPicker()
    } else {
        goster()
    }
}

/** Android TV Remote Service'in klavyesi: ekranda tuş yok, yalnız telefondan yazdırır. */
private const val TELEFON_KOPRU_IME = "com.google.android.tv.remote.service"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconPill(glyph: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScalePill, label = "browseIcon")
    Box(
        modifier = Modifier
            .size(46.dp)
            .nmScale(scale)
            .clip(CircleShape)
            .background(if (focused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(focused, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = NmType.Body, color = NmColor.OnSurface)
    }
}

// ------------------------------------------------------------------ Kaynaklar
// Eklenti (kanal) seçici. Seçilen kaynağın kategorileri gösterilir; bir kaynağın
// rafları başka kaynağınkilerle iç içe geçmez.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceChips(
    names: List<String>,
    selected: String?,
    seriesSelected: Boolean,
    favoriler: Set<String>,
    puanlar: Map<String, Double>,
    kayitliSira: List<String>,
    onGizle: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onSiraKaydet: (List<String>) -> Unit,
    onSelectSeries: () -> Unit,
    onOpenChannels: () -> Unit,
) {
    // Varsayılan sıra: Tümü, Seriler, YouTube, yıldızlılar, kalanlar; yıldızlılar ve
    // kalanlar kendi içinde oynatma puanına göre (en çok çalışan önde).
    // Dean bunu elle değiştirir: çipe BASILI TUT → çip sarıya döner, SOL/SAĞ
    // taşır, OK ya da GERİ bırakır; sıra sunucuya yazılır (Dean, 5 Ekim:
    // "serileri en sona, DiziMom en öne").
    val varsayilan = remember(names, favoriler, puanlar) { varsayilanKaynakSirasi(names, favoriler, puanlar) }
    val tasima = rememberTasima(varsayilan, kayitliSira, onSiraKaydet)
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth().focusGroup(),
            contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Anahtar = ad: yer değiştiren çip aynı düğüm kalır, odak onunla gider.
            itemsIndexed(tasima.sira, key = { _, ad -> ad }) { _, ad ->
                val active = when (ad) {
                    TUMU -> selected == null && !seriesSelected
                    SERILER -> seriesSelected
                    else -> selected == ad
                }
                SourceChip(
                    label = ad,
                    active = active,
                    favori = ad in favoriler,
                    tasiniyor = tasima.tasinan == ad,
                    onLongPress = { tasima.baslat(ad) },
                    onMove = { yon ->
                        val j = tasima.tasi(yon) ?: return@SourceChip
                        // Kenara dayandıysa şeridi kaydır, çip ekranda kalsın.
                        val gorunen = rowState.layoutInfo.visibleItemsInfo
                        if (gorunen.isNotEmpty() && (j <= gorunen.first().index || j >= gorunen.last().index)) {
                            scope.launch { rowState.animateScrollBy(yon * 220f) }
                        }
                    },
                    // Odak YUKARI/AŞAĞI ile şeritten çıkarsa taşıma biter, sıra kaybolmaz.
                    onFocusLost = { if (tasima.tasinan == ad) tasima.bitir() },
                    onAsagi = if (ad in SABIT_CIPLER) null else ({ tasima.bitir(); onGizle(ad) }),
                ) {
                    when {
                        tasima.tasinan != null -> tasima.bitir()
                        ad == TUMU -> onSelect(null)
                        ad == SERILER -> onSelectSeries()
                        ad == TV -> onOpenChannels()
                        else -> onSelect(ad)
                    }
                }
            }
        }
        TasimaIpucu(
            tasima.tasinan != null,
            Modifier.padding(start = NmDim.SafeH),
            metin = if (tasima.tasinan in SABIT_CIPLER) "◀ ▶ taşı · OK bırak · GERİ iptal"
                else "◀ ▶ taşı · ▼ gizle · OK bırak · GERİ iptal",
        )
    }
}

/** Tümü, Seriler, YouTube, TV, yıldızlılar, kalanlar; son ikisi kendi içinde puana göre (puansız sonda, özgün sırada). */
internal fun varsayilanKaynakSirasi(
    names: List<String>,
    favoriler: Set<String>,
    puanlar: Map<String, Double>,
): List<String> {
    val puanSirali = { l: List<String> -> l.sortedByDescending { puanlar[it] ?: Double.NEGATIVE_INFINITY } }
    return listOf(TUMU, SERILER) + names.filter { it == YOUTUBE } + TV +
        puanSirali(names.filter { it in favoriler && it != YOUTUBE }) +
        puanSirali(names.filterNot { it in favoriler || it == YOUTUBE })
}

private const val TUMU = "Tümü"
private const val SERILER = "Seriler"
private const val YOUTUBE = "YouTube"
// Canlı kanallar (Show, Kanal D, ATV…) eklenti değil: hap Canlı TV ekranını açar (Dean, 6 Ekim).
private const val TV = "TV"
// Eklenti olmayan haplar ▼ ile gizlenemez.
private val SABIT_CIPLER = setOf(TUMU, SERILER, TV)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SourceChip(
    label: String,
    active: Boolean,
    favori: Boolean,
    tasiniyor: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
    onFocusLost: () -> Unit = {},
    onAsagi: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.PillRadius)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    tasiniyor -> NmColor.Star
                    focused -> NmColor.Primary
                    active  -> NmColor.PrimarySelected
                    else    -> NmColor.Surface
                }
            )
            .nmFocusRing(focused, shape)
            .onFocusChanged {
                if (focused && !it.isFocused) onFocusLost()
                focused = it.isFocused
            }
            .then(
                if (onLongPress != null) {
                    Modifier.tasimaTuslari(tasiniyor, onLongPress, { onMove?.invoke(it) }, onClick, onAsagi = onAsagi)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = when {
                tasiniyor -> tasimaEtiketi(label)
                favori -> "★ $label"
                else -> label
            },
            fontSize = NmType.Label,
            maxLines = 1,
            color = when {
                tasiniyor -> androidx.compose.ui.graphics.Color.Black
                focused -> NmColor.OnPrimary
                else -> NmColor.OnSurface
            },
            fontWeight = if (active || focused || tasiniyor) FontWeight.Bold else FontWeight.Normal,
        )
    }
}


// --------------------------------------------------------------------- Raflar
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShelfList(
    shelves: List<Shelf>,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<MediaItem>>,
    started: MutableSet<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusedShelf: Int,
    focusedCard: Int,
    focusResetKey: Int,
    onShelfFocused: (Int) -> Unit,
    onCardFocused: (Int) -> Unit,
    onSelect: (MediaItem) -> Unit,
) {
    // Bu liste her ekrana dönüşte yeniden oluşur; odağı SON kalınan rafa geri ver
    // (yeniden en üste atlamasın). Kullanıcı gezinmeye başlayınca bir daha çalmaz;
    // GERİ ile en üste dönüşte (focusResetKey) yeniden çalır.
    var pendingFocus by remember(shelves.firstOrNull()?.key, focusResetKey) { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = NmDim.SafeV + 16.dp),
        verticalArrangement = Arrangement.spacedBy(NmDim.RowGap),
    ) {
        items(shelves.size) { index ->
            ShelfRow(
                shelf = shelves[index],
                cache = cache,
                started = started,
                // Hedef raf boş çıkarsa (kaynak ölü) odak sonraki dolu rafa düşsün.
                autoFocus = pendingFocus && index >= focusedShelf,
                // Hedef raf mı, yoksa onun altındaki bir yedek mi? Kaynak seçilince
                // ilk raf ("Son Bölümler") hep atlanıp ikinciye düşülüyordu: raflar
                // paralel çekildiği için hangisi ÖNCE dolarsa odağı kapıyordu. Yedek
                // raflar kısa süre bekler, hedef dolarsa o kazanır (Dean).
                // Kart da geri verilir: dönüşte raf doğru ama poster ilk sıradaysa
                // kullanıcı hangi diziden çıktığını yine bulamıyor. Yalnız ASIL rafa
                // dönüldüğünde; alt rafa kayıldıysa baştan başlanır.
                oncelikli = index == focusedShelf,
                restoreCard = if (index == focusedShelf) focusedCard else 0,
                onFocusConsumed = { pendingFocus = false },
                onFocused = { onShelfFocused(index) },
                onCardFocused = onCardFocused,
                onSelect = onSelect,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShelfRow(
    shelf: Shelf,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<MediaItem>>,
    started: MutableSet<String>,
    autoFocus: Boolean,
    oncelikli: Boolean,
    restoreCard: Int,
    onFocusConsumed: () -> Unit,
    onFocused: () -> Unit,
    onCardFocused: (Int) -> Unit,
    onSelect: (MediaItem) -> Unit,
) {
    val items = cache[shelf.key]

    // Raf ekrana girdiğinde içeriğini çeker; sonuç önbellekte kalır.
    LaunchedEffect(shelf.key) {
        if (!started.add(shelf.key)) return@LaunchedEffect   // önyükleme zaten aldı
        cache[shelf.key] = fetchShelf(shelf)
    }

    // Boş dönen kategori (ölü/değişmiş kaynak) hiç yer kaplamasın.
    if (items != null && items.isEmpty()) return

    // Sayfalama: raf sonuna gelince sonraki sayfa eklenir. Kaynak boş sayfa
    // döndürdüğünde durur (sonsuz istek yok).
    var page by remember(shelf.key) { androidx.compose.runtime.mutableIntStateOf(1) }
    var exhausted by remember(shelf.key) { mutableStateOf(false) }
    var loadingMore by remember(shelf.key) { mutableStateOf(false) }

    // Odak geri verilecek kart. Liste kısaldıysa (kaynak farklı sayfa döndü) sona
    // kırpılır; yatay kaydırma da oraya taşınır, yoksa odak ekran dışında kalır.
    val targetCard = if (items.isNullOrEmpty()) 0 else restoreCard.coerceIn(0, items.lastIndex)
    val rowState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus, items) {
        if (autoFocus && !items.isNullOrEmpty()) {
            // Yedek raf: hedefe zaman tanı. Hedef bu arada dolarsa odağı o alır ve
            // `autoFocus` düşer, bu efekt de sessizce iptal olur.
            if (!oncelikli) kotlinx.coroutines.delay(900)
            if (targetCard > 0) runCatching { rowState.scrollToItem(targetCard) }
            runCatching { firstFocus.requestFocus() }.onSuccess { onFocusConsumed() }
        }
    }

    Column(Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() }) {
        Text(
            text = shelf.title,
            fontWeight = FontWeight.Medium,
            fontSize = NmType.RowTitle,
            color = NmColor.OnSurfaceMuted,
            modifier = Modifier.padding(start = NmDim.SafeH),
        )
        if (items == null) {
            ShelfSkeleton()
        } else {
            LazyRow(
                modifier = Modifier.focusGroup(),
                state = rowState,
                contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = NmDim.RowPadV),
                horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
            ) {
                itemsIndexed(items) { i, item ->
                    // Yalnız SON kartta tetiklenir: birkaç karta koyulsaydı aynı sayfa
                    // paralel çekilirdi.
                    if (i == items.lastIndex && !exhausted) {
                        LaunchedEffect(shelf.key, items.size) {
                            if (loadingMore) return@LaunchedEffect
                            loadingMore = true
                            val next = fetchShelf(shelf, page + 1)
                            val known = items.map { it.url }.toSet()
                            val fresh = next.filter { it.url !in known }
                            if (fresh.isEmpty()) exhausted = true
                            else {
                                page += 1
                                cache[shelf.key] = items + fresh
                            }
                            loadingMore = false
                        }
                    }
                    val mod = Modifier
                        .width(com.evaitec.netmovies.tv.ui.theme.nmRafPosterGenisligi())
                        .then(if (i == targetCard) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onCardFocused(i) }
                    BrowsePoster(item, modifier = mod) { onSelect(item) }
                }
            }
        }
    }
}

/** İçerik gelene kadar rafın yerini tutan gri poster iskeleti (liste zıplamasın). */
@Composable
private fun ShelfSkeleton() {
    Row(
        modifier = Modifier.padding(horizontal = NmDim.SafeH, vertical = NmDim.RowPadV),
        horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
    ) {
        repeat(4) {
            Box(
                Modifier
                    .width(com.evaitec.netmovies.tv.ui.theme.nmRafPosterGenisligi())
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(NmDim.CardRadius))
                    .background(NmColor.Surface),
            )
        }
    }
}

// ------------------------------------------------------------------- Seriler
// Her koleksiyon bir raf: raf adı = koleksiyon adı, kartlar = parçalar (çıkış
// tarihine göre sıralı — sunucu sıralar). Tıklanınca MediaItem'a dönüştürülmez,
// başlıkla arama yapılır (bkz. openSeriesPart / otomatikAc yolu).
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesShelfList(
    seriler: List<FilmSerisi>,
    onSelectParca: (FilmSerisiParcasi) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(seriler.firstOrNull()?.id) { runCatching { firstFocus.requestFocus() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NmDim.SafeV + 16.dp),
        verticalArrangement = Arrangement.spacedBy(NmDim.RowGap),
    ) {
        items(seriler.size) { i ->
            val seri = seriler[i]
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = seri.ad,
                    fontWeight = FontWeight.Medium,
                    fontSize = NmType.RowTitle,
                    color = NmColor.OnSurfaceMuted,
                    modifier = Modifier.padding(start = NmDim.SafeH),
                )
                LazyRow(
                    modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = NmDim.RowPadV),
                    horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                ) {
                    itemsIndexed(seri.parcalar) { j, parca ->
                        val mod = Modifier
                            .width(com.evaitec.netmovies.tv.ui.theme.nmRafPosterGenisligi())
                            .then(if (i == 0 && j == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        SeriesPoster(parca, modifier = mod) { onSelectParca(parca) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesPoster(parca: FilmSerisiParcasi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScaleCard, label = "seriesScale")
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .nmScale(scale)
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(NmColor.SurfaceHigh)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
    ) {
        PosterImage(poster = parca.poster, title = parca.baslik)
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(54.dp)
                .background(nmBottomScrim),
        )
        Text(
            text = if (parca.yil > 0) "${parca.baslik} (${parca.yil})" else parca.baslik,
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

// --------------------------------------------------------------- Arama sonucu
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ItemGrid(
    title: String,
    items: List<MediaItem>,
    loading: Boolean,
    onSelect: (MediaItem) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items) { if (items.isNotEmpty()) runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = NmType.RowTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            loading -> Center("Yükleniyor…")
            items.isEmpty() -> Center("İçerik bulunamadı")
            else -> LazyVerticalGrid(
                modifier = Modifier.focusGroup(),
                columns = GridCells.Adaptive(minSize = NmDim.GridPosterMin),
                contentPadding = PaddingValues(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                verticalArrangement = Arrangement.spacedBy(NmDim.CardGap),
            ) {
                items(items.size) { i ->
                    val mod = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                    BrowsePoster(items[i], modifier = mod) { onSelect(items[i]) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun BrowsePoster(item: MediaItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScaleCard, label = "browseScale")
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .nmScale(scale)
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(NmColor.SurfaceHigh)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
    ) {
        PosterImage(poster = item.poster, title = item.title)
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(54.dp)
                .background(nmBottomScrim),
        )
        // Puan rozeti — ana ekrandaki kartla aynı; Gözat'ta eksikti.
        item.rating?.let { puan ->
            Text(
                text = "★ %.1f".format(puan),
                color = NmColor.Star,
                fontSize = NmType.Caption,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(NmDim.PillRadius))
                    .background(NmColor.ScrimSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}


// prefs'teki yıldızlı kaynak kaydı: satır başına bir eklenti adı.
internal const val FAV_KAYNAK_ANAHTAR = "fav_providers"

// Kaynak çiplerinin elle dizilmiş sırası: satır başına bir çip adı.
internal const val KAYNAK_SIRA_ANAHTAR = "provider_order"



