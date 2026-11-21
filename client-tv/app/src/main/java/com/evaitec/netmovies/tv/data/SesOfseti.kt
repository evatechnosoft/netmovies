package com.evaitec.netmovies.tv.data

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Bluetooth hoparlörde ses-görüntü ayarı (Dean, 21 Kas: "ses ilerde oluyor").
 * Kaynak ve proxy temiz ölçüldü; Android A2DP gecikmesini yanlış bildirince ExoPlayer
 * görüntüyü yanlış hizalıyor. Çare: ses saatine elle ofset. Artı = sesi geciktir.
 * Yalnız Bluetooth çıkışında uygulanır; HDMI'da saat olduğu gibi kalır.
 */
@UnstableApi
object SesOfseti {
    private const val PREFS = "player"
    private const val KEY = "bt_ses_ofseti_ms"
    const val ADIM_MS = 50
    const val SINIR_MS = 400

    /** Panelde gösterilen seçenekler: -400 … +400 ms. */
    val secenekler: List<Int> = (-SINIR_MS..SINIR_MS step ADIM_MS).toList()

    @Volatile var ms: Int = 0
        private set
    @Volatile var bluetooth: Boolean = false
        private set
    private var hazir = false

    /** Ses saatine eklenecek süre; konum bilinmiyorsa (MIN_VALUE) ya da çıkış Bluetooth değilse dokunma. */
    internal fun uygula(konumUs: Long, ofsetMs: Int, bt: Boolean): Long =
        if (!bt || ofsetMs == 0 || konumUs == AudioSink.CURRENT_POSITION_NOT_SET) konumUs
        else konumUs + ofsetMs * 1000L

    fun hazirla(context: Context) {
        if (hazir) return
        hazir = true
        val app = context.applicationContext
        ms = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0).coerceIn(-SINIR_MS, SINIR_MS)
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun tazele() {
            bluetooth = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        }
        // İlk kayıtta mevcut cihazlarla onAudioDevicesAdded hemen çağrılır.
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(eklenen: Array<out AudioDeviceInfo>) = tazele()
            override fun onAudioDevicesRemoved(cikan: Array<out AudioDeviceInfo>) = tazele()
        }, null)
        tazele()
    }

    fun ayarla(context: Context, yeniMs: Int) {
        ms = yeniMs.coerceIn(-SINIR_MS, SINIR_MS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, ms).apply()
        PlaybackLog.info("ses", "bluetooth ofseti $ms ms")
    }

    fun renderersFactory(context: Context): DefaultRenderersFactory {
        hazirla(context)
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                val asil = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                    ?: return null
                return object : ForwardingAudioSink(asil) {
                    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
                        uygula(super.getCurrentPositionUs(sourceEnded), ms, bluetooth)
                }
            }
        }
    }
}
