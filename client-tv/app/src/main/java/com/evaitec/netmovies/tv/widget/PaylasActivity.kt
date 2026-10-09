package com.evaitec.netmovies.tv.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject

/**
 * Phone share target: "Paylaş → TV'de aç" sends the shared link to the TV, which opens
 * it in its own page view (WebEkrani). No UI of its own: toast, then finish.
 */
class PaylasActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adres = paylasilanAdres(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (adres == null) {
            Toast.makeText(this, "Paylaşılan şeyde bağlantı yok", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val govde = JSONObject().put("type", "web").put("url", adres).toString()
        Thread {
            val tamam = RemoteWidget.komutYolla(applicationContext, govde)
            runOnUiThread {
                Toast.makeText(this, if (tamam) "TV'de açılıyor" else "TV'ye ulaşılamadı", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }
}

/** First http(s) link in shared text ("Başlık https://…" is how most apps share). */
internal fun paylasilanAdres(metin: String?): String? =
    metin?.let { Regex("""https?://\S+""").find(it)?.value }
