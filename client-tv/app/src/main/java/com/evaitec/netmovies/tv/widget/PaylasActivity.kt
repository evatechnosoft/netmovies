package com.evaitec.netmovies.tv.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject

/**
 * Phone share target: "Paylaş → TV'de aç". A link goes to the TV — the server opens a
 * provider/YouTube page in the player, anything else in the TV's page view (WebEkrani).
 * Plain text without a link becomes a TV search. No UI of its own: toast, then finish.
 */
class PaylasActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val govde = paylasimKomutu(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (govde == null) {
            Toast.makeText(this, "Paylaşılan şey boş", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Thread {
            val yanit = RemoteWidget.komutYaniti(applicationContext, govde.toString())
            runOnUiThread {
                Toast.makeText(this, paylasimSonucu(yanit, govde.optString("type")), Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }
}

/** First http(s) link in shared text ("Başlık https://…" is how most apps share). */
internal fun paylasilanAdres(metin: String?): String? =
    metin?.let { Regex("""https?://\S+""").find(it)?.value }

/** Link → `web` (server may turn it into `play`); bare text → TV search; empty → null. */
internal fun paylasimKomutu(metin: String?): JSONObject? {
    paylasilanAdres(metin)?.let { return JSONObject().put("type", "web").put("url", it) }
    val yazi = metin?.trim()?.take(200).orEmpty()
    if (yazi.isEmpty()) return null
    return JSONObject().put("type", "text").put("text", yazi).put("submit", true)
}

/** Toast text from the server reply: what the TV is about to do. */
internal fun paylasimSonucu(yanit: String?, gonderilen: String): String {
    if (yanit == null) return "TV'ye ulaşılamadı"
    val sonuc = runCatching { JSONObject(yanit).optJSONObject("result") }.getOrNull()
    if (sonuc?.optBoolean("ok", true) == false) return sonuc.optString("error").ifBlank { "TV kabul etmedi" }
    return when (sonuc?.optString("as")?.ifBlank { null } ?: gonderilen) {
        "play" -> "TV'de oynatıcıda açılıyor"
        "text" -> "TV'de aranıyor"
        else -> "TV'de sayfa açılıyor"
    }
}
