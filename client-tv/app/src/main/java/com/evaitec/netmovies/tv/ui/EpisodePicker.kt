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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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

// Bölüm seçici: SAYFA SAYFA, kutucuklu. Önce sezon sayfası, seçilince o sezonun
// bölüm sayfası; GERİ bir sayfa geri alır.
//
// Öncesinde sezon rafı (yatay) ile bölüm listesi (dikey) AYNI panelin içindeydi,
// panelin kendisi de oynatıcının üstünde bir katmandı: D-pad aynı ekranda iki ayrı
// yönü idare etmek zorundaydı ve nereye basınca nereye gidildiği belli olmuyordu
// (Dean: "çok karışık, player içinde liste seçimi iç içe hep geçiyor, tile olsun
// ya da sayfa geçiş"). Kutucuk ızgarasında her yön aynı şeyi yapar: komşu kutuya gider.

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BolumSecici(
    title: String,
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

    Box(
        Modifier.fillMaxSize().background(NmColor.Scrim).focusGroup(),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier.fillMaxSize().padding(NmDim.SafeArea),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = NmType.ScreenTitle,
                    fontWeight = FontWeight.Bold,
                    color = NmColor.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            }

            if (secilenSezon == null) {
                // Odak hedefi TEK: "şu an oynayan sezon" varsa o, yoksa ilk sezon.
                // Önceki hal `i == 0 || simdiki` idi — 1. sezonda değilken bu iki
                // koşul AYRI kutucuklara denk geliyor, aynı FocusRequester iki
                // düğüme birden bağlanıyor ve requestFocus() hiçbirine kesin
                // yerleşmiyordu (D-pad/OK panele hiç ulaşmıyordu, "sezon seçilemiyor").
                val sezonHedef = remember(sezonlar, episodes, currentEpIndex) {
                    val oynayanSezon = episodes.getOrNull(currentEpIndex)?.season
                    sezonlar.indexOf(oynayanSezon).coerceAtLeast(0)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(NmDim.GridPosterMin),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                    verticalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                ) {
                    items(sezonlar.size, key = { sezonlar[it] }) { i ->
                        val s = sezonlar[i]
                        val sayi = episodes.count { it.season == s }
                        val simdiki = episodes.getOrNull(currentEpIndex)?.season == s
                        Kutucuk(
                            ustYazi = "Sezon $s",
                            altYazi = "$sayi bölüm",
                            secili = simdiki,
                            modifier = if (i == sezonHedef) Modifier.focusRequester(ilkOdak) else Modifier,
                        ) { onSezon(s) }
                    }
                }
            } else {
                val secili = remember(episodes, secilenSezon) {
                    episodes.withIndex().filter { it.value.season == secilenSezon }
                }
                val seciliSira = secili.indexOfFirst { it.index == currentEpIndex }.coerceAtLeast(0)
                val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = seciliSira)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(NmDim.GridPosterMin),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                    verticalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                ) {
                    items(secili.size, key = { "${secili[it].value.url}#${secili[it].index}" }) { i ->
                        val (idx, ep) = secili[i]
                        val oynayan = idx == currentEpIndex
                        // seciliSira zaten oynayan bölümün sırası (bulunamazsa 0) —
                        // ayrıca `oynayan` kontrolü aynı öğeye iki kez odak hedefi
                        // bağlamaktan başka bir şey katmıyordu, tek koşula indirildi.
                        Kutucuk(
                            ustYazi = ep.episode?.let { "Bölüm $it" } ?: "Bölüm ${i + 1}",
                            altYazi = ep.title?.takeIf { it.isNotBlank() } ?: "",
                            secili = oynayan,
                            modifier = if (i == seciliSira) Modifier.focusRequester(ilkOdak) else Modifier,
                        ) { onSelect(idx); onClose() }
                    }
                }
            }
        }
    }
}

/** Izgara kutucuğu: üstte numara, altta ad. Oynayan bölüm dolgulu. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Kutucuk(
    ustYazi: String,
    altYazi: String,
    secili: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var odakli by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Column(
        modifier = modifier
            // Dean: "gridler çok büyük kaldı" — 92dp + 180dp min genişlik ekrana az
            // kutucuk sığdırıyordu. 68dp + NmDim.GridPosterMin (150dp) ile hem daha
            // çok sütun hem daha çok satır görünür; bölüm numarası (Body, tek satır)
            // yine de rahat okunur.
            .height(68.dp)
            .clip(shape)
            .background(
                when {
                    odakli -> NmColor.Primary
                    secili -> NmColor.PrimarySelected
                    else   -> NmColor.Surface
                }
            )
            .nmFocusRing(odakli, shape)
            .onFocusChanged { odakli = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = (if (secili) "● " else "") + ustYazi,
            fontSize = NmType.Body,
            fontWeight = FontWeight.Bold,
            color = if (odakli) NmColor.OnPrimary else NmColor.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (altYazi.isNotBlank()) {
            // Kutucuk küçülünce iki satıra yer kalmadı — öncelik bölüm numarasında,
            // ad tek satıra sığmazsa kesilir.
            Text(
                text = altYazi,
                fontSize = NmType.Caption,
                color = if (odakli) NmColor.OnPrimary else NmColor.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
