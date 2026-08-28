package com.evaitec.netmovies.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object Updater {
    private val client = OkHttpClient()

    // APK'yı indir → FileProvider ile paket kurulum intent'ini aç (sistem "kur?" sorar).
    suspend fun downloadAndInstall(context: Context, url: String) {
        val file = withContext(Dispatchers.IO) {
            val out = File(context.getExternalFilesDir(null), "update.apk")
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("Boş yanıt")
                out.outputStream().use { os -> body.byteStream().copyTo(os) }
            }
            out
        }

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
