package com.evaitec.netmovies.tv.ui.player2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Oynatıcı 2 — UI bayrakları ve TEK GERİ mantığı. Eski kodda GERİ iki yerde
 * (NmBackHandler + onPreviewKeyEvent) tekrar ediyordu; burada tek fonksiyon.
 *
 * SAHİBİ: keys ajanı. Alan EKLEYEBİLİR, mevcut imzaları değiştiremez.
 */
class PlayerUiState {
    var showSettings by mutableStateOf(false)
    var showSeek by mutableStateOf(false)
    var showReport by mutableStateOf(false)
    var showKeys by mutableStateOf(false)
    var panelAsList by mutableStateOf(false)
    var secilenSezon by mutableStateOf<Int?>(null)
    var panelGeriGelsin by mutableStateOf(false)
    /** Ayarlar doğrudan Bölümler sekmesinde açılsın. */
    var ayarBolumler by mutableStateOf(false)
    var showControls by mutableStateOf(false)
    var controlsTick by mutableIntStateOf(0)
    var scrubMode by mutableStateOf(false)
    var scrubPos by mutableLongStateOf(0L)
    var scrubTick by mutableIntStateOf(0)
    var keyHint by mutableStateOf<String?>(null)
    var keyTick by mutableIntStateOf(0)

    fun flashControls() { showControls = true; controlsTick++ }

    /** Eski `panelAcik`: açılış atla / sonraki bölüm teklifi bu panellerde devre dışı. */
    fun panelAcik(core: PlayerCore): Boolean =
        core.showStartPanel || showSettings || showSeek || scrubMode

    /**
     * GERİ önceliği (eski PlayerScreen:643-666 ile birebir): geri sayım → iptal;
     * scrub → kapat; başlangıç paneli → liste/sezon kapat, kaynak varsa OYNAT, yoksa
     * çık; ayarlar → kapat (+paneli geri getir); kontroller → gizle; yoksa [onExit].
     */
    fun onBack(core: PlayerCore, onExit: () -> Unit) {
        val cokSezon = core.episodes.map { it.season }.distinct().size > 1
        when (
            geriEylemi(
                geriSayimVar = core.geriSayim != null,
                scrub = scrubMode,
                baslangicPaneli = core.showStartPanel,
                liste = panelAsList,
                bolumSayfasi = secilenSezon != null && cokSezon,
                kaynakVar = core.links.isNotEmpty(),
                ayarlar = showSettings,
                kontroller = showControls,
            )
        ) {
            GeriEylem.SAYIMI_IPTAL -> core.otoGecisIptal = true
            GeriEylem.SCRUB_KAPAT -> scrubMode = false
            GeriEylem.SEZONLARA_DON -> secilenSezon = null
            GeriEylem.LISTEYI_KAPAT -> { core.showStartPanel = false; panelAsList = false }
            GeriEylem.OYNAT -> core.panelOynat()
            GeriEylem.AYARLARI_KAPAT -> {
                showSettings = false
                if (panelGeriGelsin) { panelGeriGelsin = false; core.showStartPanel = true }
            }
            GeriEylem.KONTROLLERI_GIZLE -> showControls = false
            GeriEylem.CIK -> onExit()
        }
    }
}

internal enum class GeriEylem {
    SAYIMI_IPTAL, SCRUB_KAPAT, SEZONLARA_DON, LISTEYI_KAPAT, OYNAT, AYARLARI_KAPAT, KONTROLLERI_GIZLE, CIK,
}

/**
 * GERİ kararı — saf fonksiyon (test: GeriEylemTest). Eski iki yolun birleşimi:
 * NmBackHandler sırası + onPreviewKeyEvent'teki "bölüm sayfası → sezon sayfası" adımı.
 */
internal fun geriEylemi(
    geriSayimVar: Boolean,
    scrub: Boolean,
    baslangicPaneli: Boolean,
    liste: Boolean,
    bolumSayfasi: Boolean,
    kaynakVar: Boolean,
    ayarlar: Boolean,
    kontroller: Boolean,
): GeriEylem = when {
    // Geri sayım sürerken GERİ = "geçme, jeneriği izliyorum"; bölümden çıkarmaz.
    geriSayimVar -> GeriEylem.SAYIMI_IPTAL
    scrub -> GeriEylem.SCRUB_KAPAT
    // Başlangıç panelinde GERİ = OYNAT (Dean). Kaynak yoksa oynatacak bir şey yok → çıkış.
    // Oynarken açılan bölüm listesinde arkada film var: GERİ önce sezon sayfasına,
    // sonra listeyi kapatır.
    baslangicPaneli -> when {
        liste && bolumSayfasi -> GeriEylem.SEZONLARA_DON
        liste -> GeriEylem.LISTEYI_KAPAT
        kaynakVar -> GeriEylem.OYNAT
        else -> GeriEylem.CIK
    }
    ayarlar -> GeriEylem.AYARLARI_KAPAT
    kontroller -> GeriEylem.KONTROLLERI_GIZLE
    else -> GeriEylem.CIK
}
