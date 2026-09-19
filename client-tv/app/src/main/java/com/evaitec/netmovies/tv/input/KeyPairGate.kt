package com.evaitec.netmovies.tv.input

import android.view.KeyEvent

/**
 * Yarım tuş olaylarını eler: yalnız ACTION_DOWN'ı BU ekranda görülen tuşun
 * ACTION_UP'ı işlenir.
 *
 * Neden: bir ekranda basılan tuşun bırakılması, o basış yeni bir ekran açtıysa
 * YENİ ekrana düşer. Poster kartındaki OYNAT'a basınca OK'un DOWN'ı ana ekranda
 * işleniyor, UP'ı oynatıcıda — oynatıcının başlangıç panelindeki OYNAT'a
 * kendiliğinden basılmış oluyordu (Dean: "film başlarken kendi kendine basar
 * gibi oluyor"). Aynı tuzak uzun basışla açılan her panel için geçerli.
 *
 * `KeyEvent` nesnesi değil (action, keyCode) alır: birim testte android.jar
 * stub'ı `KeyEvent(...)` kurucusunu "not mocked" diye reddediyor, sabitler ise
 * derleme zamanı int.
 */
class KeyPairGate {
    private val gorulen = mutableSetOf<Int>()

    /** true → olay işlenebilir. false → sahipsiz UP, yut. */
    fun kabul(action: Int, keyCode: Int): Boolean = when (action) {
        KeyEvent.ACTION_DOWN -> { gorulen.add(keyCode); true }
        KeyEvent.ACTION_UP   -> gorulen.remove(keyCode)
        else                 -> true
    }
}
