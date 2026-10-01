package com.evaitec.netmovies.tv.data

import android.content.Context
import android.media.AudioManager

/**
 * Medya sesini oynatıcılar arasında hatırlar: oynatıcı kapanırken cihazın gerçek
 * STREAM_MUSIC düzeyi yazılır, açılırken geri verilir. Mi Box her açılışta sesi
 * yüksek başlatıyordu (Dean, 1 Ekim: "ses hep yüksek başlıyor, hafızada tutsun").
 * Sabit sesli çıkışta (HDMI ses düzeyini TV yönetiyorsa) dokunulmaz.
 */
object SesHafizasi {
    private const val PREFS = "player"
    private const val KEY = "medya_sesi"

    private fun ses(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun geriYukle(context: Context) {
        val am = ses(context)
        if (am.isVolumeFixed) return
        val kayit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, -1)
        if (kayit < 0) return
        val hedef = kayit.coerceAtMost(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != hedef) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, hedef, 0)
        }
    }

    fun kaydet(context: Context) {
        val am = ses(context)
        if (am.isVolumeFixed) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, am.getStreamVolume(AudioManager.STREAM_MUSIC)).apply()
    }
}
