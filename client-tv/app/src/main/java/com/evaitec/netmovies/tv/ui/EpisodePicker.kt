package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.evaitec.netmovies.tv.data.EpisodeItem
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing

// Bölüm seçici: SAĞDA dar sütun, SOLDA seçili bölümün önizlemesi. Önce sezon
// sayfası, seçilince o sezonun bölüm sayfası; GERİ bir sayfa geri alır.
//
// Liste eskiden TAM GENİŞLİKTİ: sekiz bölümlük dizide ekranı baştan başa mor
// şeritler kaplıyordu, arkadaki görüntü tamamen gidiyordu (Dean, 19 Eylül:
// "bu şekilde liste istemiyorum"). Dar sütun aynı satır düzenini korur —
// numara solda, ad ortada — ama ekranın üçte birini kullanır.
//
// ESKİ NOT: SAYFA SAYFA, tam genişlik SATIR listesi. Önce sezon sayfası,
// seçilince o sezonun bölüm sayfası; GERİ bir sayfa geri alır.
//
// Önce yatay sezon rafı + dikey bölüm listesi AYNI paneldeydi: D-pad aynı ekranda
// iki yönü idare ediyordu, nereye basınca nereye gidildiği belli olmuyordu
// (Dean: "çok karışık"). Sonra ızgara kutucuklara geçildi; o da okunmadı
// (Dean: "bölümler çok kötü gözükmekte ... yine listtile"). Izgarada göz satır mı
// sütun mu izleyeceğini bilemiyor, bölüm adı da kutuya sığmıyordu. Tek sütun satır
// listesinde sıra tek yönlü: AŞAĞI = sonraki bölüm, ad tam satır boyunca okunur.

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BolumSecici(
    title: String,
    /** Sol önizleme için dizi afişi. */
    poster: String? = null,
    /** Bölüm özeti sağlayıcıdan gelmiyor; dizi açıklaması gösterilir. */
    aciklama: String? = null,
    episodes: List<EpisodeItem>,
    currentEpIndex: Int,
    /** null = sezon sayfası açık. Durum DIŞARIDA: GERİ tuşu oynatıcıda işleniyor. */
    secilenSezon: Int?,
    onSezon: (Int?) -> Unit,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val sezonlar = remember(episodes) { episodes.map { it.season }.distinct().sorted() }

    // Tek sezonluk dizide sezon sayfası bir tık fazlalıktır: doğrudan bölümler.
    LaunchedEffect(sezonlar) {
        if (secilenSezon == null && sezonlar.size == 1) onSezon(sezonlar.first())
    }

    val ilkOdak = remember { FocusRequester() }
    LaunchedEffect(secilenSezon, episodes.size) {
        repeat(8) {
            withFrameNanos {}
            if (runCatching { ilkOdak.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // Sağdaki sütunda hangi satır odaklıysa solda o gösterilir.
    var onizlenen by remember(secilenSezon) { mutableStateOf<Int?>(null) }

    // Bölüm özeti hiçbir sağlayıcıda yok; TMDB'de var (sezon başına tek istek,
    // sunucuda 12 saat önbellekli). Gelmezse önizleme dizi açıklamasına düşer.
    var ozetler by remember(title) {
        mutableStateOf<Map<String, com.evaitec.netmovies.tv.data.EpisodeOverview>>(emptyMap())
    }
    LaunchedEffect(title, secilenSezon) {
        val sezon = secilenSezon ?: return@LaunchedEffect
        ozetler = runCatching {
            com.evaitec.netmovies.tv.data.Network.api
                .episodeOverviews(title = title, season = sezon).result.episodes
        }.getOrDefault(emptyMap())
    }

    Box(
        Modifier.fillMaxSize().background(NmColor.Scrim).focusGroup(),
        contentAlignment = Alignment.TopStart,
    ) {
        Row(
            Modifier.fillMaxSize().padding(NmDim.SafeArea),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            val onizlemeBolum = onizlenen?.let { episodes.getOrNull(it) }
            Onizleme(
                title = title,
                poster = poster,
                aciklama = aciklama,
                bolum = onizlemeBolum,
                tmdb = onizlemeBolum?.episode?.let { ozetler[it.toString()] },
                modifier = Modifier.weight(1f),
            )

        Column(
            Modifier.width(380.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                // Hangi sayfadayız ve GERİ ne yapar — tek satırda yazılı.
                Text(
                    text = when {
                        secilenSezon == null   -> "Sezon seç"
                        sezonlar.size > 1      -> "Sezon $secilenSezon · GERİ = sezonlar"
                        else                   -> "Bölüm seç"
                    },
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceMuted,
                )

            if (secilenSezon == null) {
                // Odak hedefi TEK: "şu an oynayan sezon" varsa o, yoksa ilk sezon.
                // Önceki hal `i == 0 || simdiki` idi — 1. sezonda değilken bu iki
                // koşul AYRI satıra denk geliyor, aynı FocusRequester iki düğüme
                // birden bağlanıyor ve requestFocus() hiçbirine kesin yerleşmiyordu.
                val sezonHedef = remember(sezonlar, episodes, currentEpIndex) {
                    val oynayanSezon = episodes.getOrNull(currentEpIndex)?.season
                    sezonlar.indexOf(oynayanSezon).coerceAtLeast(0)
                }
                LazyColumn(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = sezonHedef),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
                ) {
                    items(sezonlar.size, key = { sezonlar[it] }) { i ->
                        val s = sezonlar[i]
                        val sayi = episodes.count { it.season == s }
                        val simdiki = episodes.getOrNull(currentEpIndex)?.season == s
                        Satir(
                            solYazi = "Sezon $s",
                            adYazi = "",
                            sagYazi = "$sayi bölüm",
                            secili = simdiki,
                            modifier = if (i == sezonHedef) Modifier.focusRequester(ilkOdak) else Modifier,
                            onFocus = { onizlenen = episodes.indexOfFirst { e -> e.season == s } },
                        ) { onSezon(s) }
                    }
                }
            } else {
                val secili = remember(episodes, secilenSezon) {
                    episodes.withIndex().filter { it.value.season == secilenSezon }
                }
                val seciliSira = secili.indexOfFirst { it.index == currentEpIndex }.coerceAtLeast(0)
                LazyColumn(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = seciliSira),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
                ) {
                    items(secili.size, key = { "${secili[it].value.url}#${secili[it].index}" }) { i ->
                        val (idx, ep) = secili[i]
                        val oynayan = idx == currentEpIndex
                        val no = ep.episode ?: (i + 1)
                        Satir(
                            solYazi = "Bölüm $no",
                            adYazi = ep.title?.takeIf { it.isNotBlank() } ?: "",
                            sagYazi = "${secilenSezon}x$no",
                            secili = oynayan,
                            modifier = if (i == seciliSira) Modifier.focusRequester(ilkOdak) else Modifier,
                            onFocus = { onizlenen = idx },
                        ) { onSelect(idx); onClose() }
                    }
                }
            }
        }
        }
    }
}

/** Sol önizleme: afiş, dizi adı ve odaktaki bölümün künyesi. Bölüm özeti
 *  sağlayıcılardan gelmiyor — dizi açıklaması gösterilir. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Onizleme(
    title: String,
    poster: String?,
    aciklama: String?,
    bolum: EpisodeItem?,
    /** TMDB'den o bölümün adı, özeti ve kare görseli — yoksa null. */
    tmdb: com.evaitec.netmovies.tv.data.EpisodeOverview?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val kare = tmdb?.still?.takeIf { it.isNotBlank() }
        Box(
            Modifier
                .then(if (kare != null) Modifier.width(420.dp).height(236.dp)
                      else Modifier.width(200.dp).height(300.dp))
                .clip(RoundedCornerShape(NmDim.PanelRadius)),
        ) {
            PosterImage(poster = kare ?: poster, title = title)
        }
        Text(
            text = title,
            fontSize = NmType.ScreenTitle,
            fontWeight = FontWeight.Bold,
            color = NmColor.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        bolum?.let { ep ->
            // Bölüm adı sağlayıcıda boşsa TMDB'ninki yazılır.
            val ad = ep.title?.takeIf { it.isNotBlank() }
                ?: tmdb?.title?.takeIf { it.isNotBlank() }
            Text(
                text = "S${ep.season}B${ep.episode ?: "?"}" + (ad?.let { "  ·  $it" } ?: ""),
                fontSize = NmType.Body,
                fontWeight = FontWeight.SemiBold,
                color = NmColor.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Bölüm özeti varsa o; yoksa dizi açıklaması (eski davranış).
        (tmdb?.overview?.takeIf { it.isNotBlank() } ?: aciklama)?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontSize = NmType.Caption,
                color = NmColor.OnSurfaceMuted,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Liste satırı: solda numara, ortada ad, sağda rozet. Oynayan satır dolgulu. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Satir(
    solYazi: String,
    adYazi: String,
    sagYazi: String,
    secili: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    var odakli by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    odakli -> NmColor.Primary
                    secili -> NmColor.PrimarySelected
                    else   -> NmColor.Surface
                }
            )
            .nmFocusRing(odakli, shape)
            .onFocusChanged { odakli = it.isFocused; if (it.isFocused) onFocus() }
            .clickable { onClick() }
            // Satir yuksekligi: 32 bolumluk dizide ekrana 8 satir sigiyordu, aranan
            // bolume inmek sayfalar suruyordu (Dean, 17 Eylul: "kocaman").
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Numara sütunu sabit genişlikte: satırlar alt alta hizalı okunur.
        Text(
            text = (if (secili) "● " else "") + solYazi,
            fontSize = NmType.Body,
            fontWeight = FontWeight.SemiBold,
            color = if (odakli) NmColor.OnPrimary else NmColor.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = adYazi,
            fontSize = NmType.Body,
            color = if (odakli) NmColor.OnPrimary else NmColor.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = sagYazi,
            fontSize = NmType.Caption,
            color = if (odakli) NmColor.OnPrimary else NmColor.OnSurfaceMuted,
            maxLines = 1,
        )
    }
}
