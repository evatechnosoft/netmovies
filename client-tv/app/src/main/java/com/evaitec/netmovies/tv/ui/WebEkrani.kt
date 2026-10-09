package com.evaitec.netmovies.tv.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.evaitec.netmovies.tv.input.NmBackHandler

/**
 * A page shared from the phone ("Paylaş → TV'de aç"). Meant for the watch air mouse:
 * the cursor clicks links, BACK walks the page history, then closes. No browser app
 * on the Mi Box — current Chrome needs Android 10, the box is on 9 (Dean chose this).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebEkrani(url: String, onBack: () -> Unit) {
    val gorunum = remember { arrayOfNulls<WebView>(1) }
    NmBackHandler {
        val w = gorunum[0]
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()   // links stay in this view
                loadUrl(url)
                gorunum[0] = this
            }
        },
    )
}
