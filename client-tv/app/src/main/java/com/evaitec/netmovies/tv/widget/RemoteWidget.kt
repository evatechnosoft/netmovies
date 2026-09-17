package com.evaitec.netmovies.tv.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.evaitec.netmovies.tv.R
import com.evaitec.netmovies.tv.data.ServerResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
            EYLEM_OYNAT -> intent.getStringExtra(EK_GOVDE)?.let { sorgu ->
                thread(isDaemon = true) {
                    oynat(context, sorgu)
                    Thread.sleep(600)
                    hepsiniTazele(context)
                }
            }
            EYLEM_KAY -> intent.getStringExtra(EK_GOVDE)?.toIntOrNull()?.let { yeni ->
                secimYaz(context, yeni.coerceAtLeast(0))
                thread(isDaemon = true) { hepsiniTazele(context) }
            }
            EYLEM_TAZELE -> thread(isDaemon = true) {
                // Dakikalık alarm yayı bir adım ilerletir: posterler kendiliğinden
                // geziyor, widget'a bakan her seferinde başka bir içerik görüyor.
                // Elle kaydırma bunu ezmez — sıradaki adım oradan devam eder.
                if (intent.getBooleanExtra(EK_OTOMATIK, false)) ilerlet(context)
                hepsiniTazele(context)
            }
        }
    }

    /** Yayı bir kart ilerletir; sona gelince başa döner. */
    private fun ilerlet(context: Context) {
        val adet = kartAdedi(context)
        if (adet <= 1) return
        secimYaz(context, (secim(context) + 1) % adet)
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
            // Yön pad'i: saatteki gezinmenin aynısı, aynı tuş gövdeleri.
            dugme(context, R.id.widget_sol, tus("LEFT"))
            dugme(context, R.id.widget_sag, tus("RIGHT"))
            dugme(context, R.id.widget_yukari, tus("UP"))
            dugme(context, R.id.widget_asagi, tus("DOWN"))
            dugme(context, R.id.widget_ok, tus("CENTER"))
            setOnClickPendingIntent(R.id.widget_mikrofon, giris(context, KumandaGirisActivity.MOD_SES))
            setOnClickPendingIntent(R.id.widget_ara, giris(context, KumandaGirisActivity.MOD_METIN))
            dugme(context, R.id.widget_geri_tus, tus("BACK"))

            // Devam Et yayı. Ortadaki göz seçili karttır: dokunmak televizyonda açar.
            // Yandaki gözlere dokunmak seçimi oraya kaydırır — yay böyle geziliyor.
            // Şerit tamamen boşsa gizlenir; tek kart inemezse o göz boş kalır.
            val posterler = durum.devam
            setViewVisibility(R.id.widget_posterler, if (posterler.isEmpty()) View.GONE else View.VISIBLE)
            val orta = secim(context)
            POSTER_ID.forEachIndexed { goz, id ->
                val kayma = goz - MERKEZ
                val sira = orta + kayma
                val oge = posterler.getOrNull(sira)
                setViewVisibility(id, if (oge == null) View.INVISIBLE else View.VISIBLE)
                if (oge == null) return@forEachIndexed
                setImageViewBitmap(id, oge.gorsel)
                setOnClickPendingIntent(
                    id,
                    if (kayma == 0) yayin(context, EYLEM_OYNAT, oge.sorgu)
                    else yayin(context, EYLEM_KAY, sira.toString()),
                )
            }
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

    /** 🎤 / 🔍: girdi bir pencere ister, ama uygulamanın kendisi açılmaz. */
    private fun giris(context: Context, mod: String): PendingIntent {
        val intent = Intent(context, KumandaGirisActivity::class.java)
            .putExtra(KumandaGirisActivity.EK_MOD, mod)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            mod.hashCode(),
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
            Intent(context, RemoteWidget::class.java)
                .setAction(EYLEM_TAZELE)
                .putExtra(EK_OTOMATIK, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return am to pi
    }

    companion object {
        private const val EYLEM_KOMUT  = "com.evaitec.netmovies.tv.WIDGET_KOMUT"
        private const val EYLEM_OYNAT  = "com.evaitec.netmovies.tv.WIDGET_OYNAT"
        private const val EYLEM_KAY    = "com.evaitec.netmovies.tv.WIDGET_KAY"
        private const val EYLEM_TAZELE = "com.evaitec.netmovies.tv.WIDGET_TAZELE"
        private const val EK_GOVDE     = "govde"
        private const val EK_OTOMATIK  = "otomatik"
        private const val ANAHTAR_ADET = "yay_adet"
        private const val ALARM_KODU   = 4310
        private const val TAZELEME_MS  = 60_000L
        private const val POSTER_GENISLIK = 220

        // Saatin yolladığı gövdelerin aynısı; sunucuda yeni bir dal açılmadı.
        private const val KOMUT_GERI  = """{"type":"transport","action":"seek","value":-30}"""
        private const val KOMUT_ILERI = """{"type":"transport","action":"seek","value":30}"""
        private const val KOMUT_OYNAT = """{"type":"transport","action":"play_pause","value":0}"""
        private const val KOMUT_EV    = """{"type":"nav","screen":"home"}"""

        // Yayın beş gözü; ortadaki (indeks 2) seçili olan.
        private val POSTER_ID = listOf(
            R.id.widget_poster1, R.id.widget_poster2, R.id.widget_poster3,
            R.id.widget_poster4, R.id.widget_poster5,
        )
        private const val MERKEZ = 2
        private const val SERIT_KAC = 12          // yayın dolaştığı Devam Et derinliği
        private const val PREF = "widget"
        private const val ANAHTAR_SECIM = "yay_secim"

        /** Yayın ortasındaki kart — widget yeniden çizilse de yerinde kalsın diye diskte. */
        private fun secim(context: Context): Int =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(ANAHTAR_SECIM, 0)

        /** Son çizimdeki kart sayısı — ilerletme ağa çıkmadan sınırını bilsin. */
        private fun kartAdedi(context: Context): Int =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(ANAHTAR_ADET, 0)

        private fun kartAdediYaz(context: Context, adet: Int) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putInt(ANAHTAR_ADET, adet).apply()
        }

        private fun secimYaz(context: Context, deger: Int) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putInt(ANAHTAR_SECIM, deger).apply()
        }

        /** Şeritteki tek kart: küçültülmüş poster + televizyona yollanacak sorgu. */
        data class Kart(val gorsel: Bitmap?, val sorgu: String)

        data class Durum(
            val baslik: String,
            val alt: String,
            val oynuyor: Boolean,
            val devam: List<Kart> = emptyList(),
        )

        private fun tus(k: String) = """{"type":"key","key":"$k"}"""

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

        private fun durumOku(context: Context): Durum {
            ServerResolver.init(context)
            val taban = runCatching { ServerResolver.activeBaseString() }.getOrNull()
                ?: return durumdan(null)
            val durum = durumdan(metinAl(taban + "/api/v1/remote/status"))
            // Şerit ikinci bir istektir: durum gelmediyse sunucu zaten yok, deneme.
            if (durum.alt == "sunucuya ulaşılamadı") return durum
            return durum.copy(devam = devamKartlari(context, metinAl(taban + "/api/v1/continue_watching")))
        }

        /** `/api/v1/continue_watching` -> ilk üç kart (poster indirilmiş). */
        internal fun devamSorgulari(govde: String?): List<String> {
            val dizi = govde?.takeIf { it.isNotBlank() }?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject["result"]?.jsonArray }.getOrNull()
            } ?: return emptyList()
            return dizi.take(SERIT_KAC).mapNotNull { oge ->
                val o = runCatching { oge.jsonObject }.getOrNull() ?: return@mapNotNull null
                val url = o.metin("content_url").orEmpty()
                val plugin = o.metin("plugin").orEmpty()
                if (url.isBlank() || plugin.isBlank()) return@mapNotNull null
                // Saatin `gonder`i ile birebir aynı sorgu; episode=0 "kaldığı yerden".
                "plugin=" + kacis(plugin) +
                    "&url=" + kacis(url) +
                    "&title=" + kacis(o.metin("title").orEmpty()) +
                    "&poster=" + kacis(o.metin("poster").orEmpty()) +
                    "&episode=0"
            }
        }

        private fun devamKartlari(context: Context, govde: String?): List<Kart> {
            val posterler = posterAdresleri(govde)
            val sorgular = devamSorgulari(govde)
            // Seçim listenin dışına taşmışsa (kayıt kısaldı) içeri çekilir.
            val orta = secim(context).coerceIn(0, (sorgular.size - 1).coerceAtLeast(0))
            if (orta != secim(context)) secimYaz(context, orta)
            // Yalnız yayda görünen gözlerin görseli indirilir: on iki posteri çözmek
            // her tazelemeyi gereksiz yere uzatıyordu.
            kartAdediYaz(context, sorgular.size)
            val gorunur = (orta - MERKEZ)..(orta + MERKEZ)
            return sorgular.mapIndexed { i, sorgu ->
                val gorsel = if (i in gorunur) posterler.getOrNull(i)?.let { gorselAl(context, it) } else null
                Kart(gorsel, sorgu)
            }
        }

        private fun posterAdresleri(govde: String?): List<String> {
            val dizi = govde?.takeIf { it.isNotBlank() }?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject["result"]?.jsonArray }.getOrNull()
            } ?: return emptyList()
            return dizi.take(SERIT_KAC).mapNotNull {
                runCatching { it.jsonObject.metin("poster") }.getOrNull()
            }
        }

        private fun kacis(d: String): String = URLEncoder.encode(d, "UTF-8")

        private fun metinAl(adres: String): String? = runCatching {
            val conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 5_000
            }
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()

        /**
         * Poster'i widget'a sığacak kadar küçük indirir. RemoteViews bir işlemde
         * ~1 MB taşıyabiliyor: tam boy üç poster bu sınırı aşıp widget'ı hiç
         * çizdirmezdi, o yüzden inSampleSize ile örnekleniyor.
         */
        private fun gorselAl(context: Context, adres: String): Bitmap? {
            // Önbellek: widget her tazelemede posteri yeniden indiriyordu, ilk çizim
            // boş kalıp poster saniyeler sonra düşüyordu (Dean, 17 Eylül: "poster geç
            // geldi"). Küçültülmüş hâli diske yazılır, sonraki çizim anında dolu gelir.
            val kap = File(context.cacheDir, "widget-poster").apply { mkdirs() }
            val dosya = File(kap, adres.hashCode().toUInt().toString(16) + ".png")
            if (dosya.exists()) {
                BitmapFactory.decodeFile(dosya.path)?.let { return it }
                dosya.delete()
            }
            val bitmap = indir(adres) ?: return null
            runCatching {
                dosya.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            // Şerit üç posterlik: eski içerikler birikmesin.
            runCatching {
                kap.listFiles()?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
            }
            return bitmap
        }

        private fun indir(adres: String): Bitmap? = runCatching {
            val conn = (URL(adres).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 8_000
            }
            val ham = try {
                if (conn.responseCode !in 200..299) return@runCatching null
                conn.inputStream.readBytes()
            } finally {
                conn.disconnect()
            }
            val olcu = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(ham, 0, ham.size, olcu)
            var ornek = 1
            while (olcu.outWidth / ornek > POSTER_GENISLIK) ornek *= 2
            BitmapFactory.decodeByteArray(
                ham, 0, ham.size,
                BitmapFactory.Options().apply {
                    inSampleSize = ornek
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            )
        }.getOrNull()

        /**
         * Metni `/api/v1/voice`'a yollar. O uç niyeti kendisi çözüp televizyona
         * gönderiyor (`sent`), bu yüzden ayrıca bir komut atılmaz.
         */
        fun sesleSoyle(context: Context, metin: String): Boolean = runCatching {
            ServerResolver.init(context)
            val conn = (URL(ServerResolver.activeBaseString() + "/api/v1/voice")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4_000
                readTimeout = 20_000          // Gemini yanıtı birkaç saniye sürebiliyor
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                // Kaçışı elle yazmak yerine serileştiriciye bırak: tırnak, ters bölü
                // ve satır sonu taşıyan bir arama metni gövdeyi bozardı.
                val govde = buildJsonObject { put("text", metin) }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(govde) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

        private fun oynat(context: Context, sorgu: String): Boolean = runCatching {
            ServerResolver.init(context)
            val conn = (URL(ServerResolver.activeBaseString() + "/api/v1/remote/play?" + sorgu)
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4_000
                readTimeout = 8_000
            }
            try {
                conn.outputStream.close()
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

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
