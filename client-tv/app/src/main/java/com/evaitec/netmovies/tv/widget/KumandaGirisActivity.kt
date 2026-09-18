package com.evaitec.netmovies.tv.widget

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Widget'taki 🎤 ve 🔍 düğmelerinin arkasındaki ince ekran.
 *
 * Widget'ın derdi uygulamayı AÇMAMAK; ama ses kaydı da klavye de bir pencere
 * ister. Bu yüzden uygulamanın kendisi değil, şeffaf ve tek işlik bu ekran
 * açılır: girdiyi alır, sunucuya yollar, kapanır — geride NetMovies arayüzü
 * kalmaz.
 *
 * İkisi de aynı uca gider: `/api/v1/voice` düz metni Gemini ile niyete çevirip
 * televizyona kendisi yollar (`sent`). Burada ikinci bir komut atılmaz.
 */
class KumandaGirisActivity : Activity() {

    override fun onCreate(kayit: Bundle?) {
        super.onCreate(kayit)
        if (intent?.getStringExtra(EK_MOD) == MOD_SES) sesAl() else metinSor()
    }

    private fun sesAl() {
        val niyet = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Televizyona ne diyelim?")
        }
        // Konuşma tanıma her cihazda yok (Play Services'sız ROM): o zaman klavyeye düş.
        runCatching { startActivityForResult(niyet, ISTEK_SES) }
            .onFailure { metinSor() }
    }

    @Deprecated("Activity Result API yeni; tek atışlık bu ekran için fazlalık.")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        if (istek != ISTEK_SES) return
        val metin = veri?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (sonuc == RESULT_OK && !metin.isNullOrBlank()) yolla(metin) else finish()
    }

    /** Klavye yolu artık gerçek arama ekranı: sonuçlar görünür, körlemesine
     *  "Gönder" yok. Ses yolu komut olarak kalır (`/voice` niyeti kendi işler). */
    private fun metinSor() {
        startActivity(Intent(this, AramaActivity::class.java))
        finish()
    }

    private fun yolla(metin: String) {
        val temiz = metin.trim()
        if (temiz.isEmpty()) {
            finish()
            return
        }
        Toast.makeText(this, "📺 $temiz", Toast.LENGTH_SHORT).show()
        // Ekran hemen kapanır; istek arka planda sürer — kullanıcı boş pencereye
        // bakmasın diye sonucu beklemiyoruz.
        val baglam = applicationContext
        thread(isDaemon = true) { RemoteWidget.sesleSoyle(baglam, temiz) }
        finish()
    }

    companion object {
        const val EK_MOD = "mod"
        const val MOD_SES = "ses"
        const val MOD_METIN = "metin"
        private const val ISTEK_SES = 1
    }
}
