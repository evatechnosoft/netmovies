package com.evaitec.netmovies.tv.ui

// Telefondaki arama ekranı.
//
// Önceki hâli bir AlertDialog'du: tek satır metin, "Gönder" ve hiçbir sonuç
// (Dean, 18 Eylül: "arama kutusu çok kötü"). Yazdığın şeyin televizyonda ne
// açacağını görmeden göndermek zorundaydın.
//
// Şimdi: sunucu arar (`/search_all?group=1` — sıralamayı, gruplamayı ve zengin
// alanları sunucu üretir), her içerik TEK satır, satıra basınca bilgi kartı
// açılır ve kaynaklar orada yoklanır. Sonuç yoksa metin komut olarak `/voice`'a
// gider — televizyonda ne yapılacağını orası yorumlar.
//
// TV bileşeni (tv-material) KULLANILMAZ: bu ekran parmakla sürülür, odak
// halkası ve D-pad ölçüleri burada yanlış durur.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evaitec.netmovies.tv.data.EpisodeItem
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.StreamLink
import com.evaitec.netmovies.tv.data.rawUrl
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

/** Satır altındaki meta: "DiziPal +3 · 2022 · 4 sezon 32 bölüm · DUB ALT". */
private fun MediaItem.metaSatiri(): String {
    val parcalar = mutableListOf<String>()
    val saglayicilar = providers.map { it.plugin }.ifEmpty { listOf(plugin) }.filter { it.isNotBlank() }
    if (saglayicilar.isNotEmpty()) {
        parcalar += saglayicilar.first() + if (saglayicilar.size > 1) " +${saglayicilar.size - 1}" else ""
    }
    year?.let { parcalar += it.toString() }
    val bolum = buildString {
        seasonCount?.let { append("$it sezon ") }
        episodeCount?.let { append("$it bölüm") }
    }.trim()
    if (bolum.isNotEmpty()) parcalar += bolum
    if (lang.isNotEmpty()) parcalar += lang.joinToString(" ")
    return parcalar.joinToString("  ·  ")
}

@Composable
fun PhoneSearchScreen(baslangic: String = "", onKapat: () -> Unit) {
    var sorgu by remember { mutableStateOf(baslangic) }
    var arananSorgu by remember { mutableStateOf("") }
    var araniyor by remember { mutableStateOf(false) }
    var sonuclar by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var secili by remember { mutableStateOf<MediaItem?>(null) }
    var durum by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun ara(metin: String) {
        if (metin.isBlank()) return
        araniyor = true
        durum = ""
        arananSorgu = metin
        sonuclar = runCatching { Network.api.searchAll(metin, group = 1).result }
            .getOrElse {
                durum = "Arama başarısız — sunucuya ulaşılamadı."
                emptyList()
            }
        araniyor = false
        if (sonuclar.isEmpty() && durum.isEmpty()) durum = "Sonuç yok."
    }

    // Sesli komuttan gelen metin doğrudan aransın; kullanıcı iki kez yazmasın.
    LaunchedEffect(baslangic) { if (baslangic.isNotBlank()) ara(baslangic) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NmColor.Background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ara", color = NmColor.OnSurface, fontSize = NmType.ScreenTitle, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "Kapat",
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Body,
                modifier = Modifier.clickable(onClick = onKapat).padding(6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NmDim.PillRadius))
                .background(NmColor.Surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (sorgu.isEmpty()) {
                Text("ne izlemek istersin?", color = NmColor.OnSurfaceFaint, fontSize = NmType.Body)
            }
            BasicTextField(
                value = sorgu,
                onValueChange = { sorgu = it },
                singleLine = true,
                textStyle = TextStyle(color = NmColor.OnSurface, fontSize = NmType.Body),
                cursorBrush = SolidColor(NmColor.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { scope.launch { ara(sorgu) } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))
        when {
            araniyor -> Text("aranıyor…  (tüm kaynaklar taranıyor)", color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
            durum.isNotEmpty() -> Column {
                Text(durum, color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
                if (arananSorgu.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    // Arama bulamadıysa metin bir komut olabilir ("devam et", "sonraki
                    // bölüm"): /voice niyeti çözüp televizyona kendisi gönderir.
                    Text(
                        "⌁  Komut olarak televizyona gönder",
                        color = NmColor.Primary,
                        fontSize = NmType.Body,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    durum = runCatching { Network.api.voice(mapOf("text" to arananSorgu)) }
                                        .fold({ "Televizyona gönderildi." }, { "Gönderilemedi." })
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap)) {
            items(sonuclar.size) { i -> SonucSatiri(sonuclar[i]) { secili = sonuclar[i] } }
        }
    }

    secili?.let { oge -> BilgiKarti(oge, onKapat = { secili = null }) }
}

@Composable
private fun SonucSatiri(oge: MediaItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NmDim.RowRadius))
            .background(NmColor.Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(46.dp)
                .height(69.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NmColor.SurfaceHigh),
        ) { PosterImage(poster = oge.poster, title = oge.title) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                oge.title.orEmpty(),
                color = NmColor.OnSurface,
                fontSize = NmType.RowTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                oge.metaSatiri(),
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        oge.rating?.let {
            Spacer(Modifier.width(8.dp))
            Text("★ %.1f".format(it), color = NmColor.Star, fontSize = NmType.Caption)
        }
    }
}

/** Kaynak adındaki kalite ipucu — "VidMoly 1080p" → "1080p". */
private fun kalite(link: StreamLink): String? =
    Regex("""\b(2160p|1440p|1080p|720p|480p|4K)\b""", RegexOption.IGNORE_CASE)
        .find(link.name)?.value?.uppercase()

@Composable
private fun BilgiKarti(oge: MediaItem, onKapat: () -> Unit) {
    var saglayici by remember(oge.url) { mutableStateOf(oge.providers.firstOrNull()?.plugin ?: oge.plugin) }
    var adres by remember(oge.url) { mutableStateOf(oge.providers.firstOrNull()?.url ?: oge.url) }
    var bolumler by remember(oge.url) { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var kaynaklar by remember(oge.url) { mutableStateOf<List<StreamLink>?>(null) }
    var secilenBolum by remember(oge.url) { mutableStateOf<Int?>(null) }
    var aciklama by remember(oge.url) { mutableStateOf("") }
    var mesaj by remember(oge.url) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Kart açılınca: bölüm listesi + açıklama (tek istek), sonra kaynak yoklaması.
    LaunchedEffect(saglayici, adres, secilenBolum) {
        kaynaklar = null
        val detay = runCatching { Network.api.loadItem(saglayici, adres).result }.getOrNull()
        bolumler = detay?.episodes.orEmpty()
        aciklama = detay?.description.orEmpty()
        // Dizide bölüm seçilmeden zincir taranmaz: hangi bölüm olduğu belli değil.
        if (bolumler.isNotEmpty() && secilenBolum == null) return@LaunchedEffect
        kaynaklar = runCatching {
            Network.api.resolveSources(
                plugin = saglayici,
                encodedUrl = adres,
                title = oge.title,
                episode = secilenBolum?.let { it + 1 } ?: 0,
                mode = "fast",
            ).result?.sources.orEmpty()
        }.getOrDefault(emptyList())
    }

    // HomeScreen'in ModalCard'ı TV içindir (odak halkası, D-pad geri) ve dosyaya
    // özeldir; telefonda dokunmayla kapanan sade bir kabuk yeter.
    Box(
        Modifier
            .fillMaxSize()
            .background(NmColor.Scrim)
            .clickable(onClick = onKapat),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = NmDim.PanelRadius, topEnd = NmDim.PanelRadius))
                .background(NmColor.SurfaceDialog)
                // Kartın içine yapılan dokunuş kartı kapatmamalı.
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    oge.title ?: "İçerik",
                    color = NmColor.OnSurface,
                    fontSize = NmType.RowTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "✕",
                    color = NmColor.OnSurfaceMuted,
                    fontSize = NmType.Body,
                    modifier = Modifier.clickable(onClick = onKapat).padding(start = 10.dp, end = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Text(oge.metaSatiri(), color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
            if (aciklama.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(aciklama, color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }

            if (oge.providers.size > 1) {
                Spacer(Modifier.height(10.dp))
                Text("Sağlayıcı", color = NmColor.OnSurfaceFaint, fontSize = NmType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    oge.providers.forEach { p ->
                        val secili = p.plugin == saglayici
                        Text(
                            p.plugin,
                            color = if (secili) NmColor.OnPrimary else NmColor.OnSurfaceMuted,
                            fontSize = NmType.Caption,
                            modifier = Modifier
                                .clip(RoundedCornerShape(NmDim.PillRadius))
                                .background(if (secili) NmColor.Primary else NmColor.SurfaceHigh)
                                .clickable {
                                    saglayici = p.plugin
                                    adres = p.url
                                    secilenBolum = null
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (bolumler.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    secilenBolum?.let { i ->
                        bolumler.getOrNull(i)?.let { "Bölüm: S${it.season}B${it.episode ?: (i + 1)}" } ?: "Bölüm seçildi"
                    } ?: "Bölüm seç (${bolumler.size})",
                    color = NmColor.OnSurface,
                    fontSize = NmType.Caption,
                )
                Spacer(Modifier.height(4.dp))
                Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                    bolumler.forEachIndexed { i, b ->
                        Text(
                            "S${b.season}B${b.episode ?: (i + 1)}  ${b.title.orEmpty()}",
                            color = if (i == secilenBolum) NmColor.Primary else NmColor.OnSurfaceMuted,
                            fontSize = NmType.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { secilenBolum = i }
                                .padding(vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Kaynaklar", color = NmColor.OnSurfaceFaint, fontSize = NmType.Caption)
            when {
                bolumler.isNotEmpty() && secilenBolum == null ->
                    Text("bölüm seçilince yoklanır", color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
                kaynaklar == null ->
                    Text("yoklanıyor…", color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
                kaynaklar!!.isEmpty() ->
                    Text("kaynak bulunamadı", color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
                else -> kaynaklar!!.take(8).forEach { link ->
                    val etiket = listOfNotNull(
                        link.name.substringBefore(" · ").ifBlank { "Kaynak" },
                        link.language?.label?.takeIf { it.isNotBlank() },
                        kalite(link),
                    ).joinToString("  ·  ")
                    Text(etiket, color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "▶  Televizyonda oynat",
                color = NmColor.OnPrimary,
                fontSize = NmType.Body,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NmDim.PillRadius))
                    .background(NmColor.Primary)
                    .clickable {
                        scope.launch {
                            mesaj = runCatching {
                                Network.api.remotePlay(
                                    plugin = saglayici,
                                    url = rawUrl(adres),
                                    title = oge.title.orEmpty(),
                                    poster = oge.poster.orEmpty(),
                                    episode = secilenBolum ?: -1,
                                )
                            }.fold({ "Televizyona gönderildi." }, { "Gönderilemedi." })
                        }
                    }
                    .padding(vertical = 11.dp),
            )
            if (mesaj.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(mesaj, color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
            }
        }
        }
    }
}
