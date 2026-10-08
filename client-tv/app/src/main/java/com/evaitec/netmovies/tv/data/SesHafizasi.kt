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
    /** Kayıt yoksa (ilk kurulum, kayıt hiç yazılmadıysa) başlangıç düzeyi (Dean: "9'dan başlasın"). */
    internal const val VARSAYILAN = 9

    /**
     * Hedef düzey: kayıt varsa o, yoksa ya da 0 ise [VARSAYILAN]; cihazın tavanını aşmaz.
     * Sıfır kayıt film/dizinin sessiz başlamasına yol açıyordu (Dean, 8 Ekim: "0 sesle
     * başlıyor, 9 standart yapalım"): kutu uykudan 0 sesle uyanınca o değer kaydediliyordu.
     */
    internal fun hedef(kayit: Int, max: Int): Int = (if (kayit <= 0) VARSAYILAN else kayit).coerceIn(0, max)

    private fun ses(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun geriYukle(context: Context) {
        val am = ses(context)
        val kayit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, -1)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val simdi = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val hedef = hedef(kayit, max)
        // Kanıt client_log'a: "hâlâ en yüksekten başlıyor" teşhisi cihazsız yapılamıyordu.
        // sabit=true ise ses HDMI/CEC ile televizyonda, uygulama ayarlayamaz.
        PlaybackLog.info("ses", "geri yükle: sabit=${am.isVolumeFixed} şimdi=$simdi kayıt=$kayit hedef=$hedef max=$max")
        if (am.isVolumeFixed) return
        if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        if (simdi != hedef) am.setStreamVolume(AudioManager.STREAM_MUSIC, hedef, 0)
    }

    fun kaydet(context: Context) {
        val am = ses(context)
        if (am.isVolumeFixed) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, am.getStreamVolume(AudioManager.STREAM_MUSIC)).apply()
    }
}
