package com.evaitec.netmovies.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

// Air mouse: the watch registers as a Bluetooth HID mouse (BluetoothHidDevice, the same
// approach as ginkage/wearmouse, Apache-2.0) and turns wrist rotation into cursor motion.
// The Mi Box sees a plain Bluetooth mouse — nothing to install on the TV side.

/** Pointer tuning, persisted. X/Y are signed gains (negative = inverted axis), hiz scales both. */
data class FareAyari(val x: Int = 5, val y: Int = 5, val hiz: Int = 5, val el: String? = null) {
    companion object {
        const val EN_AZ = -10
        const val EN_COK = 10

        fun oku(context: Context): FareAyari {
            val p = context.getSharedPreferences("fare", Context.MODE_PRIVATE)
            return FareAyari(p.getInt("x", 5), p.getInt("y", 5), p.getInt("hiz", 5), p.getString("el", null))
        }
    }

    fun yaz(context: Context) {
        context.getSharedPreferences("fare", Context.MODE_PRIVATE).edit()
            .putInt("x", x).putInt("y", y).putInt("hiz", hiz).putString("el", el).apply()
    }
}

/**
 * Pure motion math: gyroscope rates (rad/s) and dt (s) → cursor delta in pixels.
 * Screen up, forearm pointing at the TV: turning the hand left/right spins around the
 * screen normal (Z), tilting up/down around Y. Signs come from Dean's first try on the
 * Watch6 (left wrist): the theory-derived ones moved the cursor the wrong way. The right
 * wrist is the same watch turned 180° around Z, which flips Y only.
 * 45° of wrist turn ≈ 2048 px at gain 5 / speed 5 (1024 felt slow on the Mi Box).
 */
internal fun fareHareketi(gy: Float, gz: Float, dt: Float, ayar: FareAyari): Pair<Float, Float> {
    // Gyro noise floor: below this the hand is "still", without it the cursor creeps.
    fun temiz(v: Float) = if (abs(v) < OLU_BOLGE) 0f else v
    val olcek = PIKSEL_PER_RADYAN * dt * (ayar.hiz / 5f)
    val el = if (ayar.el == "sag") -1f else 1f
    return Pair(temiz(gz) * olcek * (ayar.x / 5f), -temiz(gy) * olcek * (ayar.y / 5f) * el)
}

/** Focus mode: accumulated motion → one arrow (HID usage), or null below the step. */
internal fun odakYonu(x: Float, y: Float, esik: Float = ODAK_ADIMI): Int? = when {
    abs(x) >= esik && abs(x) >= abs(y) -> if (x > 0) TUS_SAG else TUS_SOL
    abs(y) >= esik -> if (y > 0) TUS_ASAGI else TUS_YUKARI
    else -> null
}

internal const val TUS_ENTER = 0x28
internal const val TUS_SAG = 0x4F
internal const val TUS_SOL = 0x50
internal const val TUS_ASAGI = 0x51
internal const val TUS_YUKARI = 0x52
/** Cursor-pixels per focus step: one poster per ~10° wrist turn at defaults. */
private const val ODAK_ADIMI = 450f

private const val PIKSEL_PER_RADYAN = (2048.0 / (Math.PI / 4)).toFloat()
private const val OLU_BOLGE = 0.03f

/** HID: keyboard (ID 1, for focus mode: arrows/Enter) + mouse (ID 2: 3 buttons, X/Y/wheel int8). */
private const val ID_KLAVYE: Byte = 1
private const val ID_FARE: Byte = 2
private val TANIMLAYICI = byteArrayOf(
    0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), ID_KLAVYE,
    0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08,
    0x81.toByte(), 0x02, 0x75, 0x08, 0x95.toByte(), 0x01, 0x81.toByte(), 0x01,
    0x75, 0x08, 0x95.toByte(), 0x06, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65,
    0x81.toByte(), 0x00, 0xC0.toByte(),
    0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), ID_FARE,
    0x09, 0x01, 0xA1.toByte(), 0x00,
    0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x03,
    0x81.toByte(), 0x02, 0x75, 0x05, 0x95.toByte(), 0x01, 0x81.toByte(), 0x01,
    0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81.toByte(), 0x25, 0x7F,
    0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
    0xC0.toByte(), 0xC0.toByte(),
)

/**
 * One air-mouse session. [baslat] registers the HID app and connects to [hedef];
 * [durum] reports progress in Turkish for the watch screen. Call [kapat] when the
 * screen leaves — a registered HID app blocks other HID apps (e.g. WowMouse).
 */
@SuppressLint("MissingPermission") // caller requests BLUETOOTH_CONNECT before baslat()
class HavaFaresi(private val context: Context, private val durum: (String) -> Unit) {
    private val bt = context.getSystemService(BluetoothManager::class.java).adapter
    private val sensorler = context.getSystemService(SensorManager::class.java)
    private val yurutucu = Executors.newSingleThreadExecutor()
    private var hid: BluetoothHidDevice? = null
    private var bagli: BluetoothDevice? = null
    private var hedef: BluetoothDevice? = null

    @Volatile var ayar: FareAyari = FareAyari.oku(context)
    /** Finger on screen: freeze motion so a tap does not drag the cursor. */
    @Volatile var dondur = false
    /** Focus mode: motion moves D-pad focus (arrow keys) instead of the cursor. */
    @Volatile var odak = false
    @Volatile private var dugmeler = 0
    private var birikX = 0f
    private var birikY = 0f
    private var sonZaman = 0L

    companion object {
        /** The single paired TV box ("MyBoX"), or null when there is none or several. */
        fun kutuAdresi(context: Context): String? =
            context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
                .filter { it.name.orEmpty().contains("box", ignoreCase = true) }
                .singleOrNull()?.address
    }

    /** Paired devices for the picker; the Mi Box shows up as "MyBoX". */
    fun eslesmisler(): List<BluetoothDevice> = bt?.bondedDevices.orEmpty().sortedBy { it.name.orEmpty() }

    private val geriCagri = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered) { durum("fare kaydı düştü"); return }
            val d = hedef ?: return
            durum("bağlanıyor: ${d.name}")
            hid?.connect(d)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            // The phone auto-connects to any newly registered HID app; only the TV may drive us.
            if (device.address != hedef?.address) {
                if (state == BluetoothProfile.STATE_CONNECTED) hid?.disconnect(device)
                return
            }
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> { bagli = device; durum("bağlı: ${device.name}") }
                BluetoothProfile.STATE_DISCONNECTED -> { if (bagli == device) bagli = null; durum("bağlantı yok") }
            }
        }
    }

    private val sensorDinleyici = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val simdi = e.timestamp
            val dt = if (sonZaman == 0L) 0f else ((simdi - sonZaman) / 1e9f).coerceAtMost(0.05f)
            sonZaman = simdi
            if (dondur || dt == 0f) return
            val (dx, dy) = fareHareketi(e.values[1], e.values[2], dt, ayar)
            birikX += dx; birikY += dy
            if (odak) odakla() else gonder(0)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun baslat(cihaz: BluetoothDevice) {
        hedef = cihaz
        val adaptor = bt ?: run { durum("Bluetooth yok"); return }
        durum("fare hazırlanıyor…")
        adaptor.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "NetMovies Fare", "Saat hava faresi", "evaitec",
                    BluetoothHidDevice.SUBCLASS1_COMBO, TANIMLAYICI,
                )
                val qos = BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, 800, 9, 0, 11250,
                    BluetoothHidDeviceAppQosSettings.MAX,
                )
                if (!h.registerApp(sdp, null, qos, yurutucu, geriCagri)) {
                    durum("fare kaydı reddedildi — WowMouse açıksa kapat")
                }
            }

            override fun onServiceDisconnected(profile: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
        sensorler.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorler.registerListener(sensorDinleyici, it, 10_000)
        } ?: durum("jiroskop yok")
    }

    /** Button bits: 1 = left (OK), 2 = right (Android: BACK). */
    fun tikla(dugme: Int) {
        if (yurutucu.isShutdown) return
        yurutucu.execute {
            dugmeler = dugme; gonder(0)
            Thread.sleep(40)
            dugmeler = 0; gonder(0)
        }
    }

    /** Touch-pad drag on the watch glass (px of finger travel); scaled by the speed setting. */
    fun surukle(dx: Float, dy: Float) {
        if (yurutucu.isShutdown) return
        val k = 2.5f * (ayar.hiz / 5f)
        yurutucu.execute { birikX += dx * k; birikY += dy * k; if (odak) odakla() else gonder(0) }
    }

    /** Focus mode key: HID usage (Enter 0x28, arrows 0x4F-0x52) down or up. */
    fun tus(kullanim: Int, basili: Boolean) {
        if (yurutucu.isShutdown) return
        yurutucu.execute {
            val h = hid ?: return@execute
            val d = bagli ?: return@execute
            h.sendReport(d, ID_KLAVYE.toInt(), byteArrayOf(0, 0, (if (basili) kullanim else 0).toByte(), 0, 0, 0, 0, 0))
        }
    }

    @Synchronized
    private fun odakla() {
        val yon = odakYonu(birikX, birikY) ?: return
        birikX = 0f; birikY = 0f
        tusBas(yon)
    }

    fun tusBas(kullanim: Int) { tus(kullanim, true); tus(kullanim, false) }

    fun tekerlek(adim: Int) { if (!yurutucu.isShutdown) yurutucu.execute { gonder(adim) } }

    @Synchronized
    private fun gonder(tekerlek: Int) {
        val h = hid ?: return
        val d = bagli ?: return
        val x = birikX.roundToInt().coerceIn(-127, 127)
        val y = birikY.roundToInt().coerceIn(-127, 127)
        if (x == 0 && y == 0 && tekerlek == 0 && dugmeler == 0 && sonDugme == 0) return
        birikX -= x; birikY -= y
        sonDugme = dugmeler
        h.sendReport(d, ID_FARE.toInt(), byteArrayOf(dugmeler.toByte(), x.toByte(), y.toByte(), tekerlek.coerceIn(-127, 127).toByte()))
    }
    private var sonDugme = 0

    fun kapat() {
        sensorler.unregisterListener(sensorDinleyici)
        hid?.let { h ->
            bagli?.let { h.disconnect(it) }
            h.unregisterApp()
            bt?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        }
        hid = null; bagli = null
        yurutucu.shutdown()
    }
}
