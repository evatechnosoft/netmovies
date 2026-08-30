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
                val others = coroutineScope {
                    OTHER_TYPES
                        .map { t -> async { fetchType(t) } }
                        .awaitAll()
                        .flatten()
                }
                val all = movie + others
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
        // Engine tipleri: dizi, Türk dizi, yabancı dizi, canlı TV.
        val OTHER_TYPES = listOf("serie", "serie_local", "serie_foreign", "live")
    }
}
