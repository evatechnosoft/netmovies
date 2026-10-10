package com.evaitec.netmovies.tv.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.input.NmBackHandler
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Text typed on the phone while a page is open; `no` makes repeats distinct. */
data class WebMetni(val metin: String, val gonder: Boolean, val no: Long = System.nanoTime())

/**
 * A page shared from the phone ("Paylaş → TV'de aç"). Meant for the watch air mouse:
 * the cursor clicks links, BACK walks the page history, then closes. No browser app
 * on the Mi Box — current Chrome needs Android 10, the box is on 9 (Dean chose this).
 *
 * TV extras: text from the phone goes into the page's input (an address opens it),
 * HTML5 video can go fullscreen, text is larger for 3 m, known ad hosts are blocked.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebEkrani(url: String, metin: WebMetni?, onMetinYazildi: () -> Unit, onBack: () -> Unit) {
    val gorunum = remember { arrayOfNulls<WebView>(1) }
    val tamEkran = remember { arrayOfNulls<Pair<View, WebChromeClient.CustomViewCallback>>(1) }
    var ilerleme by remember { mutableIntStateOf(0) }
    var baslik by remember { mutableStateOf("") }
    var uyari by remember { mutableStateOf<String?>(null) }

    NmBackHandler {
        val w = gorunum[0]
        val tam = tamEkran[0]
        when {
            tam != null -> tam.second.onCustomViewHidden()
            w != null && w.canGoBack() -> w.goBack()
            else -> onBack()
        }
    }

    LaunchedEffect(metin) {
        val m = metin ?: return@LaunchedEffect
        val w = gorunum[0] ?: return@LaunchedEffect
        val adres = webAdresiMi(m.metin)
        if (adres != null) {
            w.loadUrl(adres)
        } else {
            w.evaluateJavascript(yaziBetigi(m.metin, m.gonder)) { sonuc ->
                if (sonuc.contains("yok")) uyari = "Sayfada yazı kutusu yok"
            }
        }
        onMetinYazildi()
    }
    LaunchedEffect(uyari) {
        if (uyari != null) { delay(3000); uyari = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val kap = FrameLayout(ctx)
                val web = WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    // 3 m away: phone-sized text is unreadable on the TV.
                    settings.textZoom = 130
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            // intent:/market:/whatsapp: links have no app to open on the box.
                            request.url.scheme !in setOf("http", "https")

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            if (reklamKonagi(request.url.host)) {
                                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            } else {
                                null
                            }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { ilerleme = newProgress }
                        override fun onReceivedTitle(view: WebView, title: String?) { baslik = title.orEmpty() }

                        // HTML5 video fullscreen: without this the page's ⛶ button does nothing.
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                            tamEkran[0]?.second?.onCustomViewHidden()
                            tamEkran[0] = view to callback
                            kap.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            this@apply.visibility = View.GONE
                        }

                        override fun onHideCustomView() {
                            tamEkran[0]?.let { kap.removeView(it.first) }
                            tamEkran[0] = null
                            this@apply.visibility = View.VISIBLE
                        }
                    }
                    loadUrl(url)
                }
                gorunum[0] = web
                kap.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                kap
            },
        )
        if (ilerleme in 1..99) {
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Box(Modifier.fillMaxWidth(ilerleme / 100f).height(4.dp).background(Color(0xFFE50914)))
                if (baslik.isNotBlank()) {
                    Text(
                        baslik,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        modifier = Modifier.background(Color(0xCC000000)).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
        uyari?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
                    .background(Color(0xCC000000)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/** "hurriyet.com.tr" / "https://x.com/a" → address to open; a sentence → null (typed into the page). */
internal fun webAdresiMi(metin: String): String? {
    val t = metin.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    return if (Regex("""^[\p{L}\d-]+(\.[\p{L}\d-]+)*\.[\p{L}]{2,}(/\S*)?$""").matches(t)) "https://$t" else null
}

// ponytail: short list of the common ad/popup networks on Turkish streaming pages;
// a full filter list (EasyList) if pages still drown in ads.
private val REKLAM_KONAKLARI = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
    "amazon-adsystem.com", "adnxs.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
    "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
    "a-ads.com", "hilltopads.net", "onclickads.net", "pubmatic.com", "rubiconproject.com", "openx.net",
)

internal fun reklamKonagi(konak: String?): Boolean {
    val k = konak?.lowercase() ?: return false
    return REKLAM_KONAKLARI.any { k == it || k.endsWith(".$it") }
}

/**
 * JS that writes [metin] into the focused input (or the first text/search box) the way
 * a user would — native setter + input/change events so React/Vue pages see it — and
 * with [gonder] submits its form (or presses Enter when there is none). Returns "ok" or "yok".
 */
internal fun yaziBetigi(metin: String, gonder: Boolean): String = """
(function(t,g){
  function yazilir(e){return e&&(e.isContentEditable||/^(INPUT|TEXTAREA)$/.test(e.tagName));}
  var e=document.activeElement;
  if(!yazilir(e)||e.type==='hidden'){e=document.querySelector('input[type=search],input[type=text],input:not([type]),textarea');}
  if(!e)return 'yok';
  e.focus();
  if(e.isContentEditable){document.execCommand('insertText',false,t);}
  else{var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');
    if(d&&d.set){d.set.call(e,t);}else{e.value=t;}
    e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}
  if(g){if(e.form){if(e.form.requestSubmit){e.form.requestSubmit();}else{e.form.submit();}}
    else{var k={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true};
      e.dispatchEvent(new KeyboardEvent('keydown',k));e.dispatchEvent(new KeyboardEvent('keyup',k));}}
  return 'ok';
})(${JSONObject.quote(metin)},$gonder)
""".trimIndent()
