package com.evaitec.netmovies.tv.data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Bu cihaz ne iş yapıyor. Aynı APK televizyona da telefona da kuruluyor. Eskiden
 * karar yalnız UiMode'a bakıyordu: telefonda izlemek mümkün değildi, TV kutusu
 * UiMode'u yanlış bildirirse kumandaya dönüşüyordu. Kip ilk açılışta seçilir,
 * Ayarlar'dan değiştirilir (Dean, 26 Eylül).
 */
enum class CihazKipi(val kisa: String, val etiket: String, val aciklama: String) {
  TV("Televizyon", "📺  Televizyon", "Burada izle, telefondan ve saatten gelen komutları dinle"),
  KUMANDA("Kumanda", "📱  Kumanda", "Seçtiğin içerik televizyonda açılır"),
  TELEFON("Bu cihazda izle", "▶  Bu cihazda izle", "Telefonda ya da tablette oynat");

  /** İçerik bu cihazda mı oynar (yoksa TV'ye mi gönderilir). */
  val oynatBurada: Boolean get() = this != KUMANDA

  /** Uzak komut kuyruğunu (telefon/saat kumandası) bu cihaz mı dinler. */
  val komutDinler: Boolean get() = this == TV

  companion object {
    private const val PREFS = "cihaz"
    private const val KEY = "kip"

    /** Seçilmemişse null: ilk açılış ekranı gösterilir. */
    fun oku(context: Context): CihazKipi? =
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        ?.let { ad -> entries.firstOrNull { it.name == ad } }

    /** Ayarlar'daki "değiştir": seçim silinir, sonraki kurulumda ekran yeniden gelir. */
    fun sifirla(context: Context) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun ayarla(context: Context, kip: CihazKipi) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, kip.name).apply()
    }

    /** Seçim ekranında öne çıkan öneri: sistem TV diyorsa TV, değilse kumanda. */
    fun oneri(context: Context): CihazKipi {
      val mode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
      return if (mode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) TV else KUMANDA
    }
  }
}
