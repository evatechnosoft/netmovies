package com.evaitec.netmovies.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager

/**
 * Mi Box'u derin uykudan uzak tutar: ekran kapanır ama işlemci ve Wi-Fi açık kalır.
 * Derin uykudaki kutu ağda yok (WoWLAN yok, sihirli paket tutmadı) ve Bluetooth
 * kumanda onu bazen uyandıramıyordu (Dean, 10 Eki: "kumandadan bazen açmıyor").
 * Kutu ağda kalınca atv köprüsünün POWER'ı açma işini de görür. Bedeli birkaç watt.
 * Yalnız TV kipinde başlar; aynı APK telefonda bunu hiç çalıştırmaz.
 * ponytail: açılışta değil uygulama ilk açılınca başlar; kutu yeniden başlayıp
 * NetMovies açılmadan uyursa eski davranış — gerekirse BOOT_COMPLETED alıcısı.
 */
class UyanikTut : Service() {
    private var kilit: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Uyanık tut", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("NetMovies kutuyu ağda tutuyor")
            .build())
        kilit = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "netmovies:uyanik")
            .apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        kilit?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val KANAL = "uyanik"

        fun baslat(context: Context) {
            context.startForegroundService(Intent(context, UyanikTut::class.java))
        }
    }
}
