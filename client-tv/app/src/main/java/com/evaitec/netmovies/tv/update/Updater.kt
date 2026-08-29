package com.evaitec.netmovies.tv.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

object Updater {
    // OTA: APK'yı UYGULAMA İÇİNDE indir, sonra FileProvider ile sistem paket
    // yükleyicisini aç. Eski yöntem (ACTION_VIEW ile URL → tarayıcı) Android TV /
    // Mibox'ta tarayıcı olmadığından çalışmıyordu ("indir butonu çalışmıyor").
    // Bu yol tarayıcı gerektirmez.

    private val http = OkHttpClient()

    /** APK'yı indirir ve dosyayı döndürür (ağ işi — IO dispatcher'da çağır). */
    fun downloadApk(context: Context, url: String): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val out = File(dir, "update.apk")
        if (out.exists()) out.delete()

        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("İndirme hatası: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Boş yanıt")
            out.outputStream().use { fos -> body.byteStream().copyTo(fos) }
        }
        return out
    }

    /** İndirilen APK için sistem kurulum ekranını açar (FileProvider content:// URI). */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
