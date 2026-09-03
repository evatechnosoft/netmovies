package com.evaitec.netmovies.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evaitec.netmovies.tv.data.Github
import com.evaitec.netmovies.tv.data.PlaybackLog
import com.evaitec.netmovies.tv.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateInfo(val tag: String, val url: String)

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data class UpToDate(val tag: String) : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val tag: String) : UpdateUi
    data class Opened(val tag: String) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    init { check() }

    /**
     * Güncelleme kontrolü. Hata ARTIK SESSİZ DEĞİL: ağ kesik, GitHub hız sınırı
     * (kimliksiz istek saatte 60) veya bozuk yanıt olduğunda kullanıcı "güncelleme
     * gelmiyor" diye bekliyordu; hiçbir yerde iz kalmıyordu.
     *
     * @param verbose elle tetiklenen kontrolde "zaten güncel" de gösterilir.
     */
    fun check(verbose: Boolean = false) {
        viewModelScope.launch {
            try {
                PlaybackLog.info("güncelleme", "kontrol ediliyor · yüklü: ${BuildConfig.RELEASE_TAG}")
                val releases = Github.api.releases()
                val latest = releases.firstOrNull()
                if (latest == null) {
                    PlaybackLog.warn("güncelleme", "GitHub sürüm listesi boş döndü")
                    _ui.value = UpdateUi.Failed("Sürüm listesi boş")
                    return@launch
                }

                val apk = latest.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apk == null) {
                    PlaybackLog.warn("güncelleme", "${latest.tagName} sürümünde APK yok")
                    _ui.value = UpdateUi.Failed("${latest.tagName}: APK bulunamadı")
                    return@launch
                }

                if (latest.tagName.isNotBlank() && latest.tagName != BuildConfig.RELEASE_TAG) {
                    PlaybackLog.info("güncelleme", "yeni sürüm: ${latest.tagName}")
                    _ui.value = UpdateUi.Available(UpdateInfo(latest.tagName, apk.downloadUrl))
                } else {
                    PlaybackLog.info("güncelleme", "güncel (${BuildConfig.RELEASE_TAG})")
                    _ui.value = if (verbose) UpdateUi.UpToDate(BuildConfig.RELEASE_TAG) else UpdateUi.Idle
                }
            } catch (e: Exception) {
                PlaybackLog.fail("güncelleme", "kontrol başarısız", e)
                _ui.value = UpdateUi.Failed(e.message ?: e::class.simpleName ?: "Bilinmeyen hata")
            }
        }
    }

    fun download(info: UpdateInfo) {
        _ui.value = UpdateUi.Downloading(info.tag)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    Updater.downloadApk(getApplication(), info.url)
                }
                Updater.installApk(getApplication(), file)
                _ui.value = UpdateUi.Opened(info.tag)
            } catch (e: Exception) {
                _ui.value = UpdateUi.Failed(e.message ?: "İndirme/kurulum başarısız")
            }
        }
    }
}
