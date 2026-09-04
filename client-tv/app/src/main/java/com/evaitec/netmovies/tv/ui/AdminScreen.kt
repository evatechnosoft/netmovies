package com.evaitec.netmovies.tv.ui

import android.content.Context
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.ServerResolver
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing

// Yönetim paneli (/admin) TV'de. Panel web için yazıldı; burada WebView içinde açılır —
// gizli kaynak/kategori, öne çıkanlar, puan eşiği gibi ayarlar PC açmadan değiştirilebilsin.
//
// Parola: sunucudaki ADMIN_PASS (.env). Kodda TUTULMAZ; kumandayla bir kez girilir ve
// cihazda saklanır. Yanlışsa sunucu 401 döner, ekran parola sorusuna geri düşer.

private const val PREFS = "netmovies_admin"
private const val KEY_PASS = "admin_pass"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var pass by remember { mutableStateOf(prefs.getString(KEY_PASS, "").orEmpty()) }
    var authFailed by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    if (pass.isBlank() || authFailed) {
        PassEntry(
            error = if (authFailed) "Parola kabul edilmedi — .env içindeki ADMIN_PASS" else null,
            onSubmit = { entered ->
                prefs.edit().putString(KEY_PASS, entered).apply()
                pass = entered
                authFailed = false
            },
            onCancel = onBack,
        )
        return
    }

    val base = ServerResolver.activeBaseString()
    AndroidView(
        modifier = Modifier.fillMaxSize().background(NmColor.Background),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Panel masaüstü genişliğine göre yazılmış; TV ekranına sığdır.
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                isFocusableInTouchMode = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedHttpAuthRequest(
                        view: WebView?,
                        handler: HttpAuthHandler?,
                        host: String?,
                        realm: String?,
                    ) {
                        // Kullanıcı adı önemsiz, sunucu yalnız parolayı doğruluyor.
                        handler?.proceed("admin", pass)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        // Ana belge 401 ise parola yanlış; alt kaynak hataları yoksayılır.
                        if (request?.isForMainFrame == true && errorResponse?.statusCode == 401) {
                            authFailed = true
                        }
                    }
                }
                loadUrl("$base/admin")
                requestFocus()
            }
        },
    )
}

// Kumandayla parola girişi: rakam ızgarası. TV'de yazılım klavyesi her cihazda
// açılmıyor, bu yüzden kendi girişimiz var (ADMIN_PASS sayısal tutulmalı).
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PassEntry(error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Box(Modifier.fillMaxSize().background(NmColor.Background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusGroup(),
        ) {
            Text(
                text = "Yönetim paneli parolası",
                fontWeight = FontWeight.Bold,
                fontSize = NmType.ScreenTitle,
                color = NmColor.Primary,
            )
            Text(
                text = if (value.isEmpty()) "— — — —" else "•".repeat(value.length),
                fontSize = NmType.ScreenTitle,
                color = NmColor.OnSurface,
            )
            error?.let { Text(it, fontSize = NmType.Caption, color = NmColor.Star) }

            listOf("123", "456", "789").forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, ch ->
                        KeyCap(
                            label = ch.toString(),
                            modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstKey)
                            else Modifier,
                        ) { value += ch }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCap("⌫") { value = value.dropLast(1) }
                KeyCap("0") { value += "0" }
                KeyCap("✓") { if (value.isNotBlank()) onSubmit(value) }
            }
            TouchButton("Vazgeç", onCancel)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyCap(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(shape)
            .background(if (focused) NmColor.Primary else NmColor.SurfaceHigh)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            fontWeight = FontWeight.Bold,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
        )
    }
}
