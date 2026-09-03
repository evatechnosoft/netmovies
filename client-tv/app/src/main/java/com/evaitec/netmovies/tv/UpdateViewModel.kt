package com.evaitec.netmovies.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evaitec.netmovies.tv.data.Github
import com.evaitec.netmovies.tv.data.PlaybackLog
import com.evaitec.netmovies.tv.update.ReleaseVersion
import com.evaitec.netmovies.tv.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException

data class UpdateInfo(val tag: String, val url: String)

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data class UpToDate(val tag: String) : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    /** Güncelleme var ama cihaz "bilinmeyen kaynak" kurulumuna izin vermiyor. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateUi
    data class Downloading(val tag: String) : UpdateUi
    data class Opened(val tag: String) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    /** Son BAŞARILI kontrolün zamanı — GitHub kotasını (60 istek/saat/IP) korur. */
    private var lastSuccessMillis = 0L
    private var checking = false

    init { check() }

    /**
     * Ana ekrana her dönüşte çağrılır. Önceki kontrol HATA ile bittiyse hemen,
     * başarılıysa en erken [RECHECK_INTERVAL_MS] sonra tekrar dener.
     *
     * Neden: kontrol yalnız ViewModel init'inde koşuyordu. Açılışta ağ hazır değilse
     * ya da GitHub saatlik sınırı doluysa, uygulama tamamen kapatılıp açılmadıkça bir
     * daha DENENMİYORDU — "güncelleme hiç gelmedi" şikâyetinin bir yarısı buydu.
     */
    fun recheckIfStale() {
        val stale = System.currentTimeMillis() - lastSuccessMillis > RECHECK_INTERVAL_MS
        when (_ui.value) {
            is UpdateUi.Failed, UpdateUi.Idle, is UpdateUi.UpToDate -> if (stale) check()
            else -> Unit   // indirme/kurulum sürüyor ya da güncelleme zaten gösteriliyor
        }
    }

    /**
     * Güncelleme kontrolü. Hata SESSİZ DEĞİL: ağ kesik, GitHub hız sınırı veya bozuk
     * yanıt olduğunda kullanıcı ekranda sebebi görür.
     *
     * @param verbose elle tetiklenen kontrolde "zaten güncel" de gösterilir.
     */
    fun check(verbose: Boolean = false) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                PlaybackLog.info("güncelleme", "kontrol ediliyor · yüklü: ${BuildConfig.RELEASE_TAG}")
                val releases = Github.api.releases()

                // Liste sırasına güvenme: APK'sı olan ve yüklüden YENİ olanların en
                // büyüğü seçilir. GitHub sırası yayın zamanına göredir, sürüme göre değil.
                val target = releases
                    .filter { r -> r.tagName.isNotBlank() && r.assets.any { it.name.endsWith(".apk") } }
                    .filter { ReleaseVersion.isNewerThan(it.tagName, BuildConfig.RELEASE_TAG) }
                    .reduceOrNull { best, r ->
                        if (ReleaseVersion.isNewerThan(r.tagName, best.tagName)) r else best
                    }

                lastSuccessMillis = System.currentTimeMillis()

                if (target == null) {
                    if (releases.isEmpty()) {
                        PlaybackLog.warn("güncelleme", "GitHub sürüm listesi boş döndü")
                        _ui.value = UpdateUi.Failed("Sürüm listesi boş")
                    } else {
                        PlaybackLog.info("güncelleme", "güncel (${BuildConfig.RELEASE_TAG})")
                        _ui.value =
                            if (verbose) UpdateUi.UpToDate(BuildConfig.RELEASE_TAG) else UpdateUi.Idle
                    }
                    return@launch
                }

                val apk = target.assets.first { it.name.endsWith(".apk") }
                PlaybackLog.info("güncelleme", "yeni sürüm: ${target.tagName}")
                _ui.value = UpdateUi.Available(UpdateInfo(target.tagName, apk.downloadUrl))
            } catch (e: Exception) {
                PlaybackLog.fail("güncelleme", "kontrol başarısız", e)
                _ui.value = UpdateUi.Failed(humanMessage(e))
            } finally {
                checking = false
            }
        }
    }

    fun download(info: UpdateInfo) {
        val context = getApplication<Application>()
        // İzni ÖNCE sor: yoksa APK iner ama kurulum ekranı sessizce reddedilir.
        if (!Updater.canInstall(context)) {
            PlaybackLog.warn("güncelleme", "bilinmeyen kaynak kurulum izni yok")
            _ui.value = UpdateUi.NeedsPermission(info)
            return
        }
        _ui.value = UpdateUi.Downloading(info.tag)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    Updater.downloadApk(context, info.url)
                }
                PlaybackLog.info("güncelleme", "indirildi (${file.length() / 1024} KB) · kurulum açılıyor")
                Updater.installApk(context, file)
                _ui.value = UpdateUi.Opened(info.tag)
            } catch (e: Exception) {
                PlaybackLog.fail("güncelleme", "indirme/kurulum başarısız", e)
                _ui.value = UpdateUi.Failed(humanMessage(e))
            }
        }
    }

    /** İzin ekranını açar; kullanıcı dönünce "İndir" tekrar denenebilir. */
    fun grantInstallPermission(info: UpdateInfo) {
        try {
            Updater.openInstallPermission(getApplication())
            _ui.value = UpdateUi.Available(info)
        } catch (e: Exception) {
            PlaybackLog.fail("güncelleme", "izin ekranı açılamadı", e)
            _ui.value = UpdateUi.Failed("İzin ekranı açılamadı — Ayarlar > Güvenlik > Bilinmeyen kaynaklar")
        }
    }

    private fun humanMessage(e: Throwable): String = when {
        e is HttpException && e.code() == 403 ->
            "GitHub saatlik istek sınırı dolmuş olabilir (60/saat) — biraz sonra tekrar deneyin"
        e is HttpException -> "GitHub yanıtı HTTP ${e.code()}"
        e is UnknownHostException -> "İnternete ulaşılamadı (DNS)"
        e is IOException -> "Ağ hatası: ${e.message ?: "bağlantı kurulamadı"}"
        else -> e.message ?: e::class.simpleName ?: "Bilinmeyen hata"
    }

    private companion object {
        const val RECHECK_INTERVAL_MS = 30 * 60 * 1000L   // 30 dakika
    }
}
