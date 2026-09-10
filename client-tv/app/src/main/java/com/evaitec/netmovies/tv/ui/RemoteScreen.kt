package com.evaitec.netmovies.tv.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import com.evaitec.netmovies.tv.input.NmBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Telefon kumandası (/rc) uygulamanın İÇİNDE: 📱 tarayıcıya atıyordu, kullanıcı
// uygulamadan kopuyordu. Sayfa telefon için yazıldı (dokunmatik); TV'de D-pad ile
// gezilmez ama TV'nin kumandaya ihtiyacı yok — bu ekran telefonda anlamlı.
// Mikrofon yalnız https (tünel) altında çalışır; ev ağında düz http.
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RemoteScreen(url: String, onBack: () -> Unit) {
    NmBackHandler { onBack() }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()   // linkler dış tarayıcıya kaçmasın
                loadUrl(url)
            }
        },
    )
}
