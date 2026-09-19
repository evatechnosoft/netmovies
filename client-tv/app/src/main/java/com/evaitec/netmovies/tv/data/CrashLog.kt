package com.evaitec.netmovies.tv.data

import android.content.Context
import com.evaitec.netmovies.tv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Çökme raporu.
//
// Uygulama çökünce hiçbir iz kalmıyordu: televizyonda logcat okunamaz, ADB her
// zaman bağlı değil, `client_log` da yalnız oynatma sırasında dolar. Çökme anında
// AĞA YAZILMAZ — süreç ölüyor, istek yarıda kalır. Yığın izi diske düşer, bir
// sonraki açılışta sunucuya gönderilir (`/api/v1/client_log`) ve dosya silinir.
object CrashLog {

    private const val DOSYA = "son_crash.txt"
    private val ZAMAN = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** Uygulama açılışında bir kez. Sistemin kendi işleyicisi sonradan çağrılır. */
    fun kur(context: Context) {
        val dizin = context.applicationContext.filesDir
        val onceki = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, hata ->
            runCatching {
                val iz = StringWriter().also { hata.printStackTrace(PrintWriter(it)) }.toString()
                File(dizin, DOSYA).writeText(
                    "v${BuildConfig.VERSION_NAME} · ${ZAMAN.format(Date())} · thread=${thread.name}\n$iz"
                )
            }
            onceki?.uncaughtException(thread, hata)
        }
    }

    /** Bekleyen rapor varsa satır listesi olarak döner. Dosya, rapor sunucuya
     *  ULAŞINCA silinir (`temizle`): gönderim yarıda kalırsa iz kaybolmasın. */
    fun bekleyen(context: Context): List<String>? {
        val dosya = File(context.applicationContext.filesDir, DOSYA)
        if (!dosya.exists()) return null
        return runCatching { dosya.readLines() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun temizle(context: Context) {
        File(context.applicationContext.filesDir, DOSYA).delete()
    }
}
