package com.evaitec.netmovies.wear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException

// Saatin KENDİ güncellemesi.
//
// Dağıtım üç yerde: ev sunucusu (`/data/apk`), GitHub release, evaitecOTA kataloğu.
// evaitecOTA saate APK indirip kuramıyordu (Dean, 16 Eylül: "kuramıyor") — aracı
// uygulamanın kurulum akışı bileklikte açılmıyor. Zaten kurulu olan uygulamanın
// kendini güncellemesi bu aracıyı hiç gerektirmez: APK ev sunucusundan inip
// PackageInstaller oturumuyla doğrudan sisteme verilir.
//
// TV tarafındaki `Updater` ile aynı desen; orada kanıtlandı (`ACTION_VIEW` +
// APK MIME sessizce yutuluyordu, PackageInstaller açıyor).

object Guncelleme {

    private const val ETIKET = "NetMoviesWearOTA"

    /**
     * Kurulumun son durumu — EKRANA yazılır. Sonuç yalnız logcat'e gidiyordu:
     * kurulum reddedilince ("abort") ekranda "kuruluyor…" asılı kalıyor,
     * bilekten sebebi görmenin yolu olmuyordu. Saatte logcat okunamaz.
     */
    var sonDurum by mutableStateOf("")
        internal set

    data class Bilgi(val tag: String, val surum: String, val boyut: Long)

    /** "v0.1.5-poc" → (0,1,5). Tanınmayan metin → null. */
    private fun surumParcala(metin: String): Triple<Int, Int, Int>? {
        val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(metin) ?: return null
        val (a, b, c) = m.destructured
        return Triple(a.toInt(), b.toInt(), c.toInt())
    }

    private fun yeniMi(uzak: String, yerel: String): Boolean {
        val u = surumParcala(uzak) ?: return false
        val y = surumParcala(yerel) ?: return true
        return listOf(u.first - y.first, u.second - y.second, u.third - y.third)
            .firstOrNull { it != 0 }?.let { it > 0 } ?: false
    }

    /**
     * Sunucuda daha yeni sürüm var mı (ağ işi — IO'da çağır).
     *
     * İndirme adresi yanıttaki `url` alanından DEĞİL, bulunan sunucu tabanından
     * kurulur: uç adresi `request.base_url`'den üretiyor ve ters vekil arkasında
     * bu adres saatin ulaşamayacağı bir host olabiliyor.
     */
    fun kontrol(): Bilgi? {
        val govde = Sunucu.get("/api/v1/app_update?target=wear") ?: return null
        val sonuc = runCatching {
            Sunucu.json.decodeFromString<GuncellemeYaniti>(govde).result
        }.getOrNull() ?: return null
        if (!yeniMi(sonuc.tag, BuildConfig.VERSION_NAME)) return null
        val surum = surumParcala(sonuc.tag)?.let { "${it.first}.${it.second}.${it.third}" } ?: sonuc.tag
        return Bilgi(tag = sonuc.tag, surum = surum, boyut = sonuc.size)
    }

    /** Android 8+ her uygulamadan ayrı "bilinmeyen kaynak" izni ister. */
    fun kurabilirMi(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Kullanıcıyı bu uygulamanın kurulum izni ekranına götürür. */
    fun izinEkrani(context: Context) {
        val dogrudan = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Bazı saatlerde bu ekran yok — genel güvenlik ayarlarına düşülür.
        val yedek = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(dogrudan) }
            .recoverCatching { context.startActivity(yedek) }
            .getOrThrow()
    }

    /**
     * APK'yı indirir. Yarım inen dosya kuruluma gitmesin diye önce `.part`'a
     * yazılır, boyut doğrulanır, ancak tamsa asıl ada taşınır — yarım APK
     * sistemde "paket ayrıştırılamadı" diyor ve sebebi görünmüyor.
     */
    fun indir(context: Context, bilgi: Bilgi): File {
        val dizin = context.getExternalFilesDir(null) ?: context.filesDir
        val hedef = File(dizin, "wear-${bilgi.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
        if (hedef.exists() && bilgi.boyut > 0 && hedef.length() == bilgi.boyut) return hedef

        val parca = File(dizin, "${hedef.name}.part")
        listOf(hedef, parca).forEach { if (it.exists()) it.delete() }
        // Eski sürümlerin artıkları birikmesin: saatte yer dar.
        dizin.listFiles()?.forEach { f ->
            if (f.name.startsWith("wear-") && f.name != hedef.name) f.delete()
        }

        val yazilan = Sunucu.indir("/api/v1/app_update/download?target=wear", parca)
        if (yazilan <= 0L) {
            parca.delete()
            throw IOException("indirilemedi")
        }
        if (bilgi.boyut > 0 && yazilan != bilgi.boyut) {
            parca.delete()
            throw IOException("yarım indi (${yazilan / 1024} / ${bilgi.boyut / 1024} KB)")
        }
        if (!parca.renameTo(hedef)) throw IOException("dosya taşınamadı")
        return hedef
    }

    /**
     * Kurulumu PackageInstaller oturumuyla başlatır: aracı bir activity
     * gerektirmez, sistemin kendi kurulum akışını tetikler.
     */
    fun kur(context: Context, dosya: File) {
        if (!dosya.exists() || dosya.length() == 0L) throw IOException("kurulacak dosya yok")

        val kurucu = context.packageManager.packageInstaller
        val ayar = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        ayar.setSize(dosya.length())
        ayar.setAppPackageName(context.packageName)
        // Kullanıcının istediği kurulum: sistem bunu "otomatik/politika" kurulumdan
        // ayırıyor ve onay akışını buna göre seçiyor.
        ayar.setInstallReason(PackageManager.INSTALL_REASON_USER)

        val oturum = kurucu.createSession(ayar)
        try {
            kurucu.openSession(oturum).use { s ->
                s.openWrite("netmovies-wear", 0, dosya.length()).use { cikis ->
                    dosya.inputStream().use { giris -> giris.copyTo(cikis) }
                    s.fsync(cikis)
                }
                val niyet = Intent(context, KurulumAlicisi::class.java)
                val bayrak = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val bekleyen = PendingIntent.getBroadcast(context, oturum, niyet, bayrak)
                s.commit(bekleyen.intentSender)
            }
        } catch (e: Exception) {
            // Yarım oturum bırakma: birikirse sonraki denemeler de düşer.
            runCatching { kurucu.abandonSession(oturum) }
            throw e
        }
    }

    internal fun logla(mesaj: String) = Log.i(ETIKET, mesaj)
}

/**
 * PackageInstaller oturumunun sonucu. STATUS_PENDING_USER_ACTION geldiğinde
 * sistemin onay ekranını AÇMAK bizim işimiz — açılmazsa kurulum sessizce bekler
 * ve kullanıcı "hiçbir şey olmadı" görür.
 */
class KurulumAlicisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val durum = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val onay = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                onay?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(onay) }
                    .onSuccess { Guncelleme.sonDurum = "onayla ↑" }
                    .onFailure {
                        // Onay ekranı hiç açılamazsa kurulum sessizce bekler ve
                        // sonunda "abort" olur: sebebi burada yakalanmazsa görünmez.
                        Guncelleme.sonDurum = "onay ekranı açılmadı"
                        Guncelleme.logla("onay ekranı açılamadı: ${it.message}")
                    }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Guncelleme.sonDurum = "kuruldu ✓"
                Guncelleme.logla("kurulum tamam")
            }
            else -> {
                val mesaj = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                // Ekranda yer 1 satır: kısa ad + kod. Ayrıntı logcat'te kalır.
                Guncelleme.sonDurum = when (durum) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "iptal edildi — onayı kaçırdın, tekrar dokun"
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "imza farklı — eskisini kaldır"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "yer yok"
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "uyumsuz APK"
                    else -> "olmadı ($durum)"
                }
                Guncelleme.logla("kurulum reddedildi ($durum): $mesaj")
            }
        }
    }
}
