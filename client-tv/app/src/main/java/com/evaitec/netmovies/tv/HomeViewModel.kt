package com.evaitec.netmovies.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.ServerResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val items: List<MediaItem>) : HomeState
    data class Error(val message: String) : HomeState
}

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = HomeState.Loading
        ServerResolver.reset()   // her (yeniden) yüklemede local/uzak'ı taze seç
        viewModelScope.launch {
            _state.value = try {
                // Önce film (sunucuyu resolve eder + hızlı içerik), sonra diğer tipleri
                // PARALEL çek → diziler + canlı TV de gelsin (tek "yeni filmler" satırı değil).
                val movie = Network.api.aggregateNew(type = "movie").result?.items.orEmpty()
                // Canlı TV ana sayfada TEK raf: M3U grup adları 20 ayrı kategori
                // üretiyor ve ekranı tek posterlik raflarla dolduruyordu. Kanalların
                // tamamı Ayarlar → Canlı TV ekranında kategorileriyle duruyor.
                // Canlı da diğerleriyle PARALEL çekilir; ardışık olsaydı ana sayfa
                // bir istek boyu daha geç açılırdı.
                val rest = coroutineScope {
                    val others = OTHER_TYPES.map { t -> async { fetchType(t) } }
                    val live   = async { fetchType("live").take(LIVE_ON_HOME).map { it.copy(category = "Canlı TV") } }
                    others.awaitAll().flatten() + live.await()
                }
                val all = movie + rest
                if (all.isEmpty()) HomeState.Error("İçerik yok") else HomeState.Ready(all)
            } catch (e: Exception) {
                HomeState.Error(e.message ?: "Bilinmeyen hata")
            }
        }
    }

    // Tek bir tipi çeker; hata veren/boş tip sessizce boş döner (diğerleri gelsin).
    private suspend fun fetchType(type: String): List<MediaItem> =
        runCatching { Network.api.aggregateNew(type = type).result?.items.orEmpty() }
            .getOrDefault(emptyList())

    private companion object {
        // Engine tipleri: dizi, Türk dizi, yabancı dizi. Canlı TV ayrı çekilir.
        val OTHER_TYPES = listOf("serie", "serie_local", "serie_foreign")

        // Ana sayfadaki Canlı TV rafında kaç kanal gösterilir.
        const val LIVE_ON_HOME = 20
    }
}
