package com.evaitec.netmovies.tv.input

import android.view.KeyEvent

/**
 * Uzun basışla TEK SEFER ateşlenen eylemden (bölüm listesi, ayarlar) sonra tuş
 * hâlâ basılıyken gelen tekrarları yutar.
 *
 * Basılı tutma paneli açıyordu; parmak kalkana kadar sistemin gönderdiği
 * tekrarlar yeni açılan panele gidip listeyi kaydırıyordu (Dean: "basılı
 * tutarken açıyor ve kayıyor liste"). Sürekli sarma gibi tekrarlanan eylemler
 * buraya kaydedilmez, onların tekrarı işin kendisidir.
 */
class UzunBasisKapisi {
    private val ateslenen = HashSet<Int>()

    fun ateslendi(keyCode: Int) { ateslenen.add(keyCode) }

    /** true → olay yutulmalı. Yeni basış ve bırakma kaydı temizler. */
    fun yutar(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        if (action == KeyEvent.ACTION_DOWN && repeatCount > 0) return keyCode in ateslenen
        ateslenen.remove(keyCode)
        return false
    }
}
