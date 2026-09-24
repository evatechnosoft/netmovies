package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType

/**
 * Hata/boş ekranı + "Tekrar dene". Buton açılışta odağı alır: yalnız metin olan
 * hata ekranında D-pad'in basacak bir şeyi yoktu, kumanda ölü gibi duruyordu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    val odak = remember { FocusRequester() }
    LaunchedEffect(message) {
        // İlk karede düğüm henüz bağlı olmayabilir — birkaç kare dene.
        repeat(10) {
            if (runCatching { odak.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            TouchButton("Tekrar dene", onRetry, modifier = Modifier.focusRequester(odak), accent = true)
        }
    }
}
