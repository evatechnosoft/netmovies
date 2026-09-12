package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// Ana ekranın yeri. Gözat'taki ile aynı sebep: oynatıcı açılınca HomeScreen
// bileşimden çıkıyor ve `remember` durumu ölüyor — bir içerikten GERİ ile çıkınca
// ekran en üste, ilk rafın ilk posterine dönüyordu. Durum MainActivity'de yaşar.
// Ayrıntı: ui/BrowseState.kt.
class HomePosition {
    /** Son odaklanılan raf ve o raftaki kart. */
    var row by mutableIntStateOf(0)
    var card by mutableIntStateOf(0)

    /** Dikey kaydırma konumu. */
    val listState = LazyListState()

    /** GERİ ile en üste dönüşte çağrılır: odak yeniden ilk posterde aranır. */
    fun toTop() { row = 0; card = 0 }
}
