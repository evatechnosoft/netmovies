package com.evaitec.netmovies.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evaitec.netmovies.tv.data.Github
import com.evaitec.netmovies.tv.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateInfo(val tag: String, val url: String)

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val tag: String) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    init { check() }

    fun check() {
        viewModelScope.launch {
            try {
                val latest = Github.api.releases().firstOrNull() ?: return@launch
                val apk = latest.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@launch
                if (latest.tagName.isNotBlank() && latest.tagName != BuildConfig.RELEASE_TAG) {
                    _ui.value = UpdateUi.Available(UpdateInfo(latest.tagName, apk.downloadUrl))
                }
            } catch (_: Exception) {
                // Güncelleme kontrolü kritik değil — sessizce geç.
            }
        }
    }

    fun download(info: UpdateInfo) {
        _ui.value = UpdateUi.Downloading(info.tag)
        viewModelScope.launch {
            try {
                Updater.downloadAndInstall(getApplication(), info.url)
            } catch (e: Exception) {
                _ui.value = UpdateUi.Failed(e.message ?: "İndirme hatası")
            }
        }
    }
}
