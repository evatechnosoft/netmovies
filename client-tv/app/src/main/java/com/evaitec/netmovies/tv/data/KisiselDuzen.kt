package com.evaitec.netmovies.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Dean'in elle kurduğu ana sayfa düzeni: kişisel çip sırası, raf sırası, gizli raflar,
 * "bunu gösterme" başlıkları. Hepsi sunucuda `prefs` (başka TV'den girince ya da
 * yeniden kurunca kaybolmasın); satır başına bir ad. Ekranlar buradan okur.
 */
object KisiselDuzen {
    const val SEGMENT_SIRA = "home_segment_order"
    const val RAF_SIRA = "home_row_order"
    const val GIZLI_RAF = "home_row_hidden"
    const val GIZLI_BASLIK = "hidden_titles"
    const val GIZLI_SEGMENT = "home_segment_hidden"

    var segmentSirasi by mutableStateOf<List<String>>(emptyList())
        private set
    var gizliSegmentler by mutableStateOf<Set<String>>(emptySet())
        private set
    var rafSirasi by mutableStateOf<List<String>>(emptyList())
        private set
    var gizliRaflar by mutableStateOf<Set<String>>(emptySet())
        private set
    var gizliBasliklar by mutableStateOf<Set<String>>(emptySet())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var yuklendi = false

    suspend fun yukle() {
        if (yuklendi) return
        runCatching { Network.api.prefsGet().result }.onSuccess { p ->
            segmentSirasi = eskiDevamiBol(okuSatirlar(p, SEGMENT_SIRA))
            gizliSegmentler = okuSatirlar(p, GIZLI_SEGMENT).toSet()
            rafSirasi = okuSatirlar(p, RAF_SIRA)
            gizliRaflar = okuSatirlar(p, GIZLI_RAF).toSet()
            gizliBasliklar = okuSatirlar(p, GIZLI_BASLIK).toSet()
            yuklendi = true
        }
    }

    fun segmentSirasiYaz(yeni: List<String>) { segmentSirasi = yeni; yaz(SEGMENT_SIRA, yeni) }
    fun rafSirasiYaz(yeni: List<String>) { rafSirasi = yeni; yaz(RAF_SIRA, yeni) }

    fun rafGizleGoster(raf: String) {
        gizliRaflar = if (raf in gizliRaflar) gizliRaflar - raf else gizliRaflar + raf
        yaz(GIZLI_RAF, gizliRaflar.toList())
    }

    fun segmentGizleGoster(ad: String) {
        gizliSegmentler = if (ad in gizliSegmentler) gizliSegmentler - ad else gizliSegmentler + ad
        yaz(GIZLI_SEGMENT, gizliSegmentler.toList())
    }

    fun gizliMi(item: MediaItem) = baslikAnahtari(item.title) in gizliBasliklar

    fun baslikGizleGoster(baslik: String?) {
        val k = baslikAnahtari(baslik)
        if (k.isEmpty()) return
        gizliBasliklar = if (k in gizliBasliklar) gizliBasliklar - k else gizliBasliklar + k
        yaz(GIZLI_BASLIK, gizliBasliklar.toList())
    }

    private fun yaz(anahtar: String, satirlar: List<String>) {
        scope.launch { runCatching { Network.api.prefsPost(mapOf(anahtar to satirlar.joinToString("\n"))) } }
    }
}

/** Başlık eşleştirme anahtarı: sağlayıcılar aynı diziyi farklı büyük/küçük harfle yazıyor. */
internal fun baslikAnahtari(baslik: String?) = baslik.orEmpty().trim().lowercase()

// Kişisel blok segmentleri. Devam Et film/dizi diye ikiye bölündü (Dean, 6 Ekim).
const val DEVAM_DIZI = "Devam · Dizi"
const val DEVAM_FILM = "Devam · Film"
private const val ESKI_DEVAM = "Devam edenler"

/** 0.9.56 öncesi kayıtlı sırada tek "Devam edenler" vardı; yerini ikisi alır. */
internal fun eskiDevamiBol(sira: List<String>): List<String> =
    sira.flatMap { if (it == ESKI_DEVAM) listOf(DEVAM_DIZI, DEVAM_FILM) else listOf(it) }

/** prefs'te satır başına bir ad tutan kayıt. */
internal fun okuSatirlar(prefs: Map<String, JsonElement>, anahtar: String): List<String> {
    val metin = (prefs[anahtar] as? JsonPrimitive)?.content ?: return emptyList()
    return metin.split("\n").map { it.trim() }.filter { it.isNotBlank() }
}
