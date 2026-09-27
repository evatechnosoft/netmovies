package com.evaitec.netmovies.tv.data.yz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.evaitec.netmovies.tv.data.Network
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** AICore'un bildirdiği Gemini Nano durumu. HATA: sorgunun kendisi patladı. */
enum class NanoDurum(val etiket: String) {
  UNAVAILABLE("cihaz desteklemiyor"),
  DOWNLOADABLE("indirilebilir (Wi-Fi bekleniyor)"),
  DOWNLOADING("iniyor"),
  AVAILABLE("Gemini Nano hazır"),
  HATA("kontrol edilemedi");

  companion object {
    fun kod(durum: Int): NanoDurum = when (durum) {
      FeatureStatus.AVAILABLE -> AVAILABLE
      FeatureStatus.DOWNLOADABLE -> DOWNLOADABLE
      FeatureStatus.DOWNLOADING -> DOWNLOADING
      else -> UNAVAILABLE
    }
  }
}

/**
 * Telefonda Gemini Nano (ML Kit GenAI Prompt API, AICore). Yalnız telefon kiplerinde
 * başlatılır; TV'de hiç çağrılmaz. Nano yoksa her şey sessizce null/UNAVAILABLE
 * döner — çağıran kurallara/bulanık aramaya düşer, uygulama bozulmaz.
 */
object NanoNiyet {
  private const val TAG = "NanoNiyet"
  private const val ZAMAN_ASIMI_MS = 8_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _durum = MutableStateFlow<NanoDurum?>(null)
  val durum: StateFlow<NanoDurum?> = _durum

  private val model: GenerativeModel by lazy { Generation.getClient() }

  /** Durumu sorar, sunucuya bildirir; indirilebilirse ve Wi-Fi'deyse indirmeyi başlatır. */
  fun baslat(context: Context) {
    val uygulama = context.applicationContext
    scope.launch {
      val durum = sorgula()
      bildir(durum)
      if (durum == NanoDurum.DOWNLOADABLE && wifiMi(uygulama)) indir()
    }
  }

  /**
   * Serbest cümle → niyet. Nano hazır değilse, zaman aşımında ya da model
   * çözülemeyen bir şey yazarsa null.
   */
  suspend fun cozumle(cumle: String): Niyet? {
    if (_durum.value != NanoDurum.AVAILABLE || cumle.isBlank()) return null
    return guvenli("cozumle") {
      withTimeoutOrNull(ZAMAN_ASIMI_MS) {
        // Sistem talimatı her cihazda desteklenmiyor (isSystemPromptAvailable):
        // talimat metnin başına eklenir.
        val istek = generateContentRequest(TextPart("$TALIMAT\nCümle: ${cumle.trim()}")) {
          temperature = 0f
          maxOutputTokens = 128
        }
        NiyetJson.ayristir(model.generateContent(istek).candidates.firstOrNull()?.text)
      }
    }
  }

  private suspend fun sorgula(): NanoDurum {
    val durum = guvenli("checkStatus") { NanoDurum.kod(model.checkStatus()) } ?: NanoDurum.HATA
    _durum.value = durum
    return durum
  }

  private suspend fun indir() {
    _durum.value = NanoDurum.DOWNLOADING
    bildir(NanoDurum.DOWNLOADING)
    guvenli("download") {
      model.download().collect { adim ->
        when (adim) {
          is DownloadStatus.DownloadFailed -> Log.w(TAG, "Nano indirme başarısız: ${adim.e.message}")
          DownloadStatus.DownloadCompleted -> Log.i(TAG, "Nano indirildi")
          else -> Unit
        }
      }
    }
    bildir(sorgula())
  }

  private suspend fun bildir(durum: NanoDurum) {
    val govde = mapOf(
      "cihaz" to "${Build.MANUFACTURER} ${Build.MODEL}",
      "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      "nano_durum" to durum.name,
      "zaman" to Instant.now().toString(),
    )
    guvenli("bildir") { Network.api.yzCihaz(govde) }
  }

  private fun wifiMi(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val yetenek = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return yetenek.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
  }

  /**
   * Nano isteğe bağlı bir yetenek: AICore'u olmayan cihazda ML Kit GenAiException,
   * eksik servis ya da güvenlik hatası fırlatabiliyor. Hangisi olursa olsun
   * uygulama akışı sürmeli — hata günlüğe yazılır, null döner. İptal yutulmaz.
   */
  private suspend fun <T> guvenli(adim: String, blok: suspend () -> T): T? = try {
    blok()
  } catch (iptal: CancellationException) {
    throw iptal
  } catch (hata: Exception) {
    Log.w(TAG, "$adim: ${hata.javaClass.simpleName}: ${hata.message}")
    null
  }

  private val TALIMAT = """
    Bir film/dizi/canlı TV uygulamasında arama cümlesini JSON'a çevir. Yalnız JSON yaz.
    Alanlar: {"tur": "film"|"dizi"|"canli"|null, "baslik": metin|null, "sezon": sayı|null,
    "bolum": sayı|null, "gun": "bugun"|"yarin"|"pazartesi".."pazar"|null, "kanal": metin|null}.
    Bilmediğin alanı null bırak, başlık uydurma.
  """.trimIndent()
}
