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

    /** Ana iş parçacığı donarsa (ANR) yığın izini aynı dosyaya yazar.
     *
     *  Dean, 5 Ekim: film izlerken döndü, çıkıp girince uygulama donuk; sunucuya
     *  09:54'ten sonra TEK istek gelmedi, çökme izi de yok — süreç ölmeden kilitlenmiş.
     *  Kilitlenen süreç kendini anlatamaz: bekçi ayrı iş parçacığında ana döngüye
     *  bir iş koyar; 8 sn içinde koşmazsa ana iş parçacığının o anki yığınını
     *  diske yazar. Uygulama yeniden açılınca çökme izi gibi şeritte ve sunucuda görünür. */
    fun donmaBekcisi(context: Context) {
        if (!bekciKuruldu.compareAndSet(false, true)) return   // aktivite yeniden doğsa da tek bekçi
        val dizin = context.applicationContext.filesDir
        val ana = android.os.Handler(android.os.Looper.getMainLooper())
        Thread({
            var yazildi = false
            while (true) {
                val dondu = java.util.concurrent.atomic.AtomicBoolean(true)
                ana.post { dondu.set(false) }
                Thread.sleep(DONMA_ESIK_MS)
                if (!dondu.get()) { yazildi = false; continue }
                if (yazildi) continue   // aynı donmayı bir kez yaz
                yazildi = true
                runCatching {
                    val yigin = android.os.Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "  at $it" }
                    File(dizin, DOSYA).writeText(
                        "v${BuildConfig.VERSION_NAME} · ${ZAMAN.format(Date())} · DONMA: ana iş parçacığı ${DONMA_ESIK_MS / 1000} sn yanıt vermedi\n$yigin"
                    )
                }
            }
        }, "donma-bekcisi").apply { isDaemon = true }.start()
    }

    private const val DONMA_ESIK_MS = 8_000L
    private val bekciKuruldu = java.util.concurrent.atomic.AtomicBoolean(false)

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
