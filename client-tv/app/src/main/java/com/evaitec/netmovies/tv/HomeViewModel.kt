package com.evaitec.netmovies.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
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
        viewModelScope.launch {
            _state.value = try {
                val res = Network.api.aggregateNew(type = "movie")
                HomeState.Ready(res.result?.items.orEmpty())
            } catch (e: Exception) {
                HomeState.Error(e.message ?: "Bilinmeyen hata")
            }
        }
    }
}
