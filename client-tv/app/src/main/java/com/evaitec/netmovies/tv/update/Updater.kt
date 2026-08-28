package com.evaitec.netmovies.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri

object Updater {
    // OTA indirme: release APK URL'ini sistem tarayıcısına aç. Tarayıcı indirir,
    // kullanıcı bildirimden kurar. FileProvider + REQUEST_INSTALL_PACKAGES + in-app
    // indirme zincirinin (telefonlarda tökezleyen) tüm hata noktalarını atlar.
    fun openDownload(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
