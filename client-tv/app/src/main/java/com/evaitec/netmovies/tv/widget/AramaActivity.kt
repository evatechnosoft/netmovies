package com.evaitec.netmovies.tv.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.evaitec.netmovies.tv.ui.PhoneSearchScreen

/**
 * Telefondaki arama ekranı (widget'taki 🔍).
 *
 * Eskiden burada tek satırlık bir AlertDialog vardı: ne aradığını göremeden
 * "Gönder"e basıyordun. Artık sonuçlar listelenir, satıra basınca bilgi kartı
 * açılır, oynatma oradan televizyona gider.
 *
 * Kendi Activity'si: widget'tan açılır ve kapanınca geriye NetMovies arayüzü
 * bırakmaz — TV uygulamasının MainActivity'si telefonda açılacak bir şey değil.
 */
class AramaActivity : ComponentActivity() {

    override fun onCreate(kayit: Bundle?) {
        super.onCreate(kayit)
        val baslangic = intent?.getStringExtra(EK_SORGU).orEmpty()
        setContent {
            PhoneSearchScreen(baslangic = baslangic, onKapat = { finish() })
        }
    }

    companion object {
        const val EK_SORGU = "sorgu"
    }
}
