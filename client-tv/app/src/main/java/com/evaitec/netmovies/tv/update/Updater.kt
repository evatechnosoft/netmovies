package com.evaitec.netmovies.tv.update

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.evaitec.netmovies.tv.data.PreferIpv4Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

object Updater {
    // OTA: APK'yı UYGULAMA İÇİNDE indir, sonra FileProvider ile sistem paket
    // yükleyicisini aç. Eski yöntem (ACTION_VIEW ile URL → tarayıcı) Android TV /
    // Mibox'ta tarayıcı olmadığından çalışmıyordu ("indir butonu çalışmıyor").
    // Bu yol tarayıcı gerektirmez.

    private val http = OkHttpClient.Builder().dns(PreferIpv4Dns).build()

    /**
     * Android 8+ her uygulamadan ayrı ayrı "bilinmeyen kaynak kurulumu" izni ister.
     * İzin yokken kurulum intent'i sessizce reddediliyordu: APK iniyor, hiçbir şey
     * olmuyordu — "güncelleme gelmiyor" şikâyetinin sessiz yarısı buydu.
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Kullanıcıyı bu uygulamanın kurulum izni ekranına götürür. */
    fun openInstallPermission(context: Context) {
        val direct = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Bazı TV kutularında bu ekran yok — genel güvenlik ayarlarına düşülür.
        val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(fallback) }
            .getOrThrow()
    }

    /**
     * APK'yı indirir ve dosyayı döndürür (ağ işi — IO dispatcher'da çağır).
     *
     * Bağlantı yarıda koparsa `copyTo` sessizce eksik dosya bırakıyordu; o dosya
     * kuruluma gidince sistem "paket ayrıştırılamadı" diyor ve sebep görünmüyordu.
     * Bu yüzden önce `.part` dosyasına inilir, boyut Content-Length ile doğrulanır,
     * ancak tamsa asıl ada taşınır — yarım APK hiçbir zaman kurulmaya gitmez.
     */
    fun downloadApk(context: Context, url: String): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val out = File(dir, "update.apk")
        val part = File(dir, "update.apk.part")
        if (out.exists()) out.delete()
        if (part.exists()) part.delete()

        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("İndirme hatası: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Boş yanıt")
            val beklenen = body.contentLength()
            val yazilan = part.outputStream().use { fos -> body.byteStream().copyTo(fos) }
            if (beklenen > 0 && yazilan != beklenen) {
                part.delete()
                throw IOException("İndirme yarım kaldı (${yazilan / 1024} / ${beklenen / 1024} KB)")
            }
        }
        if (!part.renameTo(out)) throw IOException("İndirilen dosya taşınamadı")
        return out
    }

    /** İndirilen APK için sistem kurulum ekranını açar (FileProvider content:// URI). */
    /**
     * Kurulumu PackageInstaller oturumu ile başlatır.
     *
     * Eski yol `ACTION_VIEW` + APK MIME idi: Android TV'de bu intent'i karşılayan
     * activity olmayabiliyor, sistem onu SESSİZCE yutuyordu — uygulama "kurulum
     * açıldı" diyor ama ekran hiç gelmiyordu (istisna da fırlamıyor). PackageInstaller
     * doğrudan sistemin kendi kurulum akışını tetikler, aracı activity gerektirmez.
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists() || file.length() == 0L) throw IOException("Kurulacak dosya yok ya da boş")

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // Boyutu bildirmek sistemin yeri ÖNCEDEN ayırmasını sağlar: yer yoksa
        // yarıda kalan bir yazma yerine anlaşılır bir hata alınır.
        params.setSize(file.length())
        params.setAppPackageName(context.packageName)

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("netmovies", 0, file.length()).use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            // Yarım oturum bırakma: birikirse sonraki denemeler de başarısız olur.
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
