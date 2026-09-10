package com.evaitec.netmovies.tv.ui

import com.evaitec.netmovies.tv.input.NmBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// Yönetim paneli TV'de — kaynak gizleme ve puan eşiği kumandayla.
//
// Eskiden web panelini (/admin) WebView içinde açıyordu: panel fare için yazılmış,
// D-pad ile gezilemiyordu ve WebView bileşeni her Android TV'de aynı davranmıyor.
// Ayarlar sunucuda `/api/admin/config` ile duruyor; ekran onu doğrudan okur/yazar.
// Web panelindeki geri kalan işler (öne çıkanlar, harici depolar) tarayıcıda kalır.

private val RATING_STEPS = listOf(0.0, 5.0, 6.0, 7.0, 8.0)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<JsonObject?>(null) }
    var plugins by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    NmBackHandler { onBack() }

    LaunchedEffect(Unit) {
        runCatching {
            plugins = Network.api.getAllPlugins().result.map { it.name }
            config  = Network.api.adminConfig()
        }.onFailure { error = it.message ?: "Ayarlar okunamadı" }
    }

    // Tam config geri yazılır; yalnız tek alan değiştirilir.
    fun kaydet(alan: String, deger: kotlinx.serialization.json.JsonElement) {
        val mevcut = config ?: return
        saving = true
        scope.launch {
            runCatching { Network.api.saveAdminConfig(JsonObject(mevcut + (alan to deger))) }
                .onSuccess { config = it; error = null }
                .onFailure { error = it.message ?: "Kaydedilemedi" }
            saving = false
        }
    }

    val cfg = config
    if (cfg == null) {
        Box(
            Modifier.fillMaxSize().background(NmColor.Background),
            contentAlignment = Alignment.Center,
        ) {
            Text(error ?: "Ayarlar yükleniyor…", fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
        }
        return
    }

    val gizli = remember(cfg) {
        (cfg["hidden_providers"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
    }
    val esik = remember(cfg) { cfg["min_rating"]?.jsonPrimitive?.doubleOrNull ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NmColor.Background).focusGroup(),
        contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
        verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
    ) {
        item {
            Text(
                text = if (saving) "⚙ Yönetim · kaydediliyor…" else "⚙ Yönetim",
                fontSize = NmType.ScreenTitle,
                fontWeight = FontWeight.Bold,
                color = NmColor.Primary,
            )
        }
        error?.let { mesaj -> item { Text(mesaj, fontSize = NmType.Caption, color = NmColor.Star) } }

        item { AdminSectionTitle("Kaynaklar — kapalı olan hiçbir listede görünmez") }
        items(plugins, key = { it }) { ad ->
            val kapali = ad in gizli
            AdminRow(if (kapali) "✕  $ad" else "✓  $ad", selected = !kapali) {
                val yeni = if (kapali) gizli - ad else gizli + ad
                kaydet("hidden_providers", JsonArray(yeni.map { JsonPrimitive(it) }))
            }
        }

        item { AdminSectionTitle("Puan eşiği — altında kalan içerik gizlenir (puansız içerik kalır)") }
        items(RATING_STEPS, key = { it }) { adim ->
            AdminRow(
                label = if (adim == 0.0) "Eşik yok" else "${adim.toInt()} ve üzeri",
                selected = adim == esik,
            ) { kaydet("min_rating", JsonPrimitive(adim)) }
        }

        item { AdminSectionTitle("Diğer ayarlar (öne çıkanlar, harici depolar) web panelinde: /admin") }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdminSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = NmType.Caption,
        color = NmColor.OnSurfaceMuted,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdminRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused  -> NmColor.Primary
                    selected -> NmColor.PrimarySelected
                    else     -> NmColor.Surface
                }
            )
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
