package com.evaitec.netmovies.tv

import com.evaitec.netmovies.tv.data.kullaniciMesaji
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.ServerResolver
import com.evaitec.netmovies.tv.data.ZimaUyandir
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
                    val live   = async { liveRow() }
                    val diziler = others.awaitAll()
                    // "Türkçe dublaj diziler" için sunucuda tip yok: dizilerden DUB
                    // rozetli olanlar. Rozet yalnız daha önce çözülmüş içerikte var.
                    val dublaj = diziler.first()
                        .filter { "DUB" in it.lang }
                        .map { it.copy(category = DUBLAJ_RAFI) }
                    diziler.flatten() + dublaj + live.await()
                }
                val all = movie + rest
                if (all.isEmpty()) HomeState.Error("İçerik yok") else HomeState.Ready(all)
            } catch (e: Exception) {
                HomeState.Error(e.kullaniciMesaji("İçerik yüklenemedi"))
            }
            // Sunucu uyandırıldıysa açılmasını bekle: ~1-2 dk, kendiliğinden yeniden dene.
            if (_state.value is HomeState.Error && ZimaUyandir.yakinda() && uyanmaDenemesi < UYANMA_DENEME) {
                uyanmaDenemesi++
                _state.value = HomeState.Error("Sunucu uyandırılıyor… birkaç dakika sürebilir")
                kotlinx.coroutines.delay(UYANMA_BEKLEME_MS)
                load()
            } else if (_state.value is HomeState.Ready) {
                uyanmaDenemesi = 0
            }
        }
    }

    private var uyanmaDenemesi = 0

    // Tek bir tipi çeker; hata veren/boş tip sessizce boş döner (diğerleri gelsin).
    private suspend fun fetchType(type: String): List<MediaItem> =
        runCatching { Network.api.aggregateNew(type = type).result?.items.orEmpty() }
            .getOrDefault(emptyList())

    // Kanallarım: Canlı TV ekranında yıldızlanan kanallar (sunucu prefs). Favori
    // yoksa eski davranış: ilk canlı kanallar "Canlı TV" rafı.
    private suspend fun liveRow(): List<MediaItem> = coroutineScope {
        val kanallar = async { runCatching { Network.api.quickChannels().result }.getOrDefault(emptyList()) }
        val favoriler = runCatching { com.evaitec.netmovies.tv.ui.okuFavoriler(Network.api.prefsGet().result) }
            .getOrDefault(emptySet())
        val benim = kanallar.await().filter { it.url in favoriler }.map { it.copy(category = KANALLARIM_RAFI) }
        benim.ifEmpty { fetchType("live").take(LIVE_ON_HOME).map { it.copy(category = "Canlı TV") } }
    }

    companion object {
        private const val UYANMA_DENEME = 12          // 12 × 15 sn = 3 dk
        private const val UYANMA_BEKLEME_MS = 15_000L
        const val KANALLARIM_RAFI = "Kanallarım"
        const val DUBLAJ_RAFI = "Türkçe Dublaj Diziler"

        // Engine tipleri: dizi, Türk dizi, yabancı dizi. Canlı TV ayrı çekilir.
        // İlki "serie" OLMALI: dublaj rafı ondan süzülür.
        private val OTHER_TYPES = listOf("serie", "serie_local", "serie_foreign")

        // Ana sayfadaki Canlı TV rafında kaç kanal gösterilir.
        private const val LIVE_ON_HOME = 20
    }
}
