package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.evaitec.netmovies.tv.data.MediaItem

// Gözat ekranının yeri. Oynatıcı açılınca BrowseScreen bileşimden tamamen çıkıyor
// ve içindeki `remember` durumu ölüyordu: bir diziden GERİ ile çıkınca kaynak
// seçimi (DiziMom), kaydırma ve odak sıfırlanıp kullanıcı "Tümü"nün en tepesine
// düşüyordu. Durum MainActivity seviyesinde tutulur — oynatıcı gidip gelse de yaşar.
class BrowseState {
    /** Seçili kaynak (eklenti adı); null = Tümü. */
    var plugin: String? by mutableStateOf(null)

    /** Son odaklanılan raf ve o raftaki kart — dönüşte ikisi birden geri verilir. */
    var shelf by mutableIntStateOf(0)
    var card by mutableIntStateOf(0)

    /** Dikey kaydırma konumu. */
    val listState = LazyListState()

    /** Çekilmiş raf içerikleri — dönüşte yeniden indirilmesin. */
    val cache = mutableStateMapOf<String, List<MediaItem>>()
    val started = mutableSetOf<String>()
}
