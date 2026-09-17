package com.evaitec.netmovies.tv.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.evaitec.netmovies.tv.R
import com.evaitec.netmovies.tv.data.ServerResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

// Telefon ana ekranı kumandası: uygulamayı AÇMADAN televizyonu sür (Dean, 17 Eylül).
// Saatteki kumandayla AYNI sunucu sözleşmesi — yeni uç yazılmadı:
//   GET  /api/v1/remote/status   -> now_playing {title, position, duration, playing}
//   POST /api/v1/remote/command  -> {"type":"transport","action":...} | {"type":"nav",...}
//
// Compose değil RemoteViews: ana ekran widget'ı başka bir süreçte çizilir, Compose
// oraya giremez (Glance yeni bir bağımlılık demekti, dört düğme için gereksiz).
class RemoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        tazele(context, manager, ids)
        alarmKur(context)
    }

    override fun onEnabled(context: Context) {
        alarmKur(context)
    }

    override fun onDisabled(context: Context) {
        // Son widget da kaldırıldı: dakikalık uyandırma boşuna pil yakmasın.
        alarm(context)?.let { (am, pi) -> runCatching { am.cancel(pi) } }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            EYLEM_KOMUT -> intent.getStringExtra(EK_GOVDE)?.let { govde ->
                thread(isDaemon = true) {
                    komutYolla(context, govde)
                    // Komuttan sonra durum değişir; kısa bir soluk sonra tazele ki
                    // düğmeye basıldığı widget'ta görünsün.
                    Thread.sleep(400)
                    hepsiniTazele(context)
                }
            }
            EYLEM_TAZELE -> thread(isDaemon = true) { hepsiniTazele(context) }
        }
    }

    private fun hepsiniTazele(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RemoteWidget::class.java))
        if (ids.isEmpty()) return
        val gorunum = ciz(context, durumOku(context))
        ids.forEach { manager.updateAppWidget(it, gorunum) }
    }

    private fun tazele(context: Context, manager: AppWidgetManager, ids: IntArray) {
        thread(isDaemon = true) {
            val gorunum = ciz(context, durumOku(context))
            ids.forEach { manager.updateAppWidget(it, gorunum) }
        }
    }

    private fun ciz(context: Context, durum: Durum): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_remote).apply {
            setTextViewText(R.id.widget_baslik, durum.baslik)
            setTextViewText(R.id.widget_alt, durum.alt)
            setTextViewText(R.id.widget_oynat, if (durum.oynuyor) "❚❚" else "▶")
            dugme(context, R.id.widget_geri, KOMUT_GERI)
            dugme(context, R.id.widget_oynat, KOMUT_OYNAT)
            dugme(context, R.id.widget_ileri, KOMUT_ILERI)
            dugme(context, R.id.widget_ev, KOMUT_EV)
            // Başlığa dokunmak yalnız tazeler: widget'tan uygulamayı açmak, widget'ın
            // var oluş sebebini (uygulamayı açmamak) ortadan kaldırırdı.
            setOnClickPendingIntent(R.id.widget_baslik, yayin(context, EYLEM_TAZELE, null))
        }

    private fun RemoteViews.dugme(context: Context, id: Int, govde: String) =
        setOnClickPendingIntent(id, yayin(context, EYLEM_KOMUT, govde))

    private fun yayin(context: Context, eylem: String, govde: String?): PendingIntent {
        val intent = Intent(context, RemoteWidget::class.java).setAction(eylem)
        govde?.let { intent.putExtra(EK_GOVDE, it) }
        // requestCode gövdeye göre AYRI olmalı: aynı kodla üretilen PendingIntent'ler
        // eşdeğer sayılır, bütün düğmeler son gövdeyi yollardı.
        return PendingIntent.getBroadcast(
            context,
            (eylem + (govde ?: "")).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmKur(context: Context) {
        val (am, pi) = alarm(context) ?: return
        // Inexact: sistem dakikayı kaydırabilir, Doze'da seyrekleşir. Telefon elde /
        // ekran açıkken pratikte dakikada bir gelir — istenen de bu.
        runCatching {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TAZELEME_MS,
                TAZELEME_MS,
                pi,
            )
        }
    }

    private fun alarm(context: Context): Pair<AlarmManager, PendingIntent>? {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_KODU,
            Intent(context, RemoteWidget::class.java).setAction(EYLEM_TAZELE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return am to pi
    }

    companion object {
        private const val EYLEM_KOMUT  = "com.evaitec.netmovies.tv.WIDGET_KOMUT"
        private const val EYLEM_TAZELE = "com.evaitec.netmovies.tv.WIDGET_TAZELE"
        private const val EK_GOVDE     = "govde"
        private const val ALARM_KODU   = 4310
        private const val TAZELEME_MS  = 60_000L

        // Saatin yolladığı gövdelerin aynısı; sunucuda yeni bir dal açılmadı.
        private const val KOMUT_GERI  = """{"type":"transport","action":"seek","value":-30}"""
        private const val KOMUT_ILERI = """{"type":"transport","action":"seek","value":30}"""
        private const val KOMUT_OYNAT = """{"type":"transport","action":"play_pause","value":0}"""
        private const val KOMUT_EV    = """{"type":"nav","screen":"home"}"""

        data class Durum(val baslik: String, val alt: String, val oynuyor: Boolean)

        /**
         * Sunucu yanıtı -> ekrandaki üç alan. Ağdan ayrı ki test edilebilsin.
         * `org.json` DEĞİL: o sınıf JVM birim testinde stub'dur, her çağrı patlar
         * ve hata sessizce "ulaşılamadı"ya düşerdi. kotlinx.serialization projede
         * zaten var ve testte de çalışıyor.
         */
        fun durumdan(govde: String?): Durum {
            val sonuc = govde?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Json.parseToJsonElement(it).jsonObject["result"]?.jsonObject }.getOrNull() }
                ?: return Durum("Televizyon", "sunucuya ulaşılamadı", false)
            if (sonuc.bool("tv_online") != true) return Durum("Televizyon", "kapalı", false)
            val simdi = runCatching { sonuc["now_playing"]?.jsonObject }.getOrNull()
                ?: return Durum("Televizyon", "açık · bir şey oynamıyor", false)
            val konum = (simdi.sayi("position") ?: 0.0).toLong()
            val sure = (simdi.sayi("duration") ?: 0.0).toLong()
            val oynuyor = simdi.bool("playing") == true
            val baslik = (simdi.metin("title") ?: "").ifBlank { "Televizyon" }
            val alt = when {
                sure > 0 -> sureMetni(konum) + "  /  " + sureMetni(sure)
                oynuyor  -> "oynuyor"
                else     -> "duraklatıldı"
            }
            return Durum(baslik, alt, oynuyor)
        }

        private fun JsonObject.bool(ad: String): Boolean? =
            runCatching { this[ad]?.jsonPrimitive?.booleanOrNull }.getOrNull()

        private fun JsonObject.sayi(ad: String): Double? =
            runCatching { this[ad]?.jsonPrimitive?.doubleOrNull }.getOrNull()

        private fun JsonObject.metin(ad: String): String? =
            runCatching { this[ad]?.jsonPrimitive?.content }.getOrNull()

        /** Saniye -> "1:12:40" / "12:40". */
        fun sureMetni(saniye: Long): String {
            val s = saniye.coerceAtLeast(0)
            val sa = s / 3600
            val dk = (s % 3600) / 60
            val sn = s % 60
            return if (sa > 0) "%d:%02d:%02d".format(sa, dk, sn) else "%d:%02d".format(dk, sn)
        }

        private fun durumOku(context: Context): Durum = runCatching {
            ServerResolver.init(context)
            val conn = (URL(ServerResolver.activeBaseString() + "/api/v1/remote/status")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
            }
            try {
                if (conn.responseCode !in 200..299) durumdan(null)
                else durumdan(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }.getOrElse { durumdan(null) }

        private fun komutYolla(context: Context, govde: String): Boolean = runCatching {
            ServerResolver.init(context)
            val conn = (URL(ServerResolver.activeBaseString() + "/api/v1/remote/command")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4_000
                readTimeout = 6_000
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                OutputStreamWriter(conn.outputStream).use { it.write(govde) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
}
