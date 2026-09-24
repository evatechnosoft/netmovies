package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType

/**
 * Kaynak aranırken/yüklenirken tam ekran geçiş: siyah ekranda köşede küçük
 * "Yükleniyor…" yazısıyla beklemek uygulamanın donduğu hissini veriyordu
 * (Dean, 24 Eylül: "yüklerken bekliyoruz, burada geçiş tam ekran olabilir").
 * İçeriğin posteri arka planda, başlık ve ne yapıldığı önde.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerLoadingScreen(poster: String?, title: String?, status: String?) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PosterImage(poster, title, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF2000000), 0.6f to Color(0xB3000000), 1f to Color(0x66000000)),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(NmDim.SafeArea).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                title.orEmpty(),
                fontSize = NmType.Wordmark,
                fontWeight = FontWeight.Bold,
                color = NmColor.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmLoader()
                Text(status ?: "Yükleniyor…", fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
            }
        }
    }
}

/**
 * Hiç oynamadan kaynak bulunamadı. Eskiden 2,5 sn sonra kendiliğinden kapanıyordu;
 * ilk açılıştaki geçici hatada kullanıcı ana ekrana atılıyor, aynı içeriği yeniden
 * açınca oynuyordu (Dean, 24 Eylül). Şimdi ekranda kalır: OK = tekrar dene, GERİ = çık.
 */
@Composable
fun KaynakYokEkrani(poster: String?, title: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PosterImage(poster, title, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xD9000000)))
        ErrorWithRetry("Çalışan kaynak bulunamadı\n${title.orEmpty()}", onRetry)
    }
}
