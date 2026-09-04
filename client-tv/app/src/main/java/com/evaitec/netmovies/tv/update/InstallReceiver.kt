package com.evaitec.netmovies.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.evaitec.netmovies.tv.data.PlaybackLog

/**
 * PackageInstaller oturumunun sonucunu alır. STATUS_PENDING_USER_ACTION geldiğinde
 * sistemin kurulum onay ekranını AÇMAK bizim işimiz — açılmazsa kurulum sessizce
 * bekler ve kullanıcı "hiçbir şey olmadı" görür.
 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { PlaybackLog.fail("güncelleme", "kurulum ekranı açılamadı", it) }
            }
            PackageInstaller.STATUS_SUCCESS ->
                PlaybackLog.info("güncelleme", "kurulum tamam")
            else ->
                PlaybackLog.fail(
                    "güncelleme",
                    "kurulum reddedildi (durum $status): " +
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty(),
                )
        }
    }
}
