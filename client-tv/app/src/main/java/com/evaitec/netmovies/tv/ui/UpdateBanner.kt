package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.UpdateUi
import com.evaitec.netmovies.tv.UpdateViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateBanner(vm: UpdateViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Ana ekrana her dönüşte bayat kontrolü tazele — ilk denemede ağ kapalıysa veya
    // GitHub kotası dolmuşsa kullanıcı uygulamayı kapatmadan da güncellemeyi görsün.
    LaunchedEffect(Unit) { vm.recheckIfStale() }
    when (val s = ui) {
        is UpdateUi.Available   -> Bar("Güncelleme mevcut: ${s.info.tag}", "İndir") { vm.download(s.info) }
        is UpdateUi.NeedsPermission ->
            Bar("Kurulum için izin gerekiyor (bilinmeyen kaynaklar)", "İzin ver") { vm.grantInstallPermission(s.info) }
        is UpdateUi.Downloading -> Bar("İndiriliyor (${s.tag})… kurulum ekranı birazdan açılır.", null, null)
        is UpdateUi.Opened      -> Bar("Kurulum başlatıldı (${s.tag}). İzin isterse onayla.", null, null)
        is UpdateUi.Failed      -> Bar("Güncelleme kontrol edilemedi: ${s.message}", "Tekrar") { vm.check(verbose = true) }
        is UpdateUi.UpToDate    -> Bar("Uygulama güncel (${s.tag})", null, null)
        UpdateUi.Idle -> {} // banner yok
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Bar(message: String, actionLabel: String?, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NmColor.BannerBg)
            .padding(horizontal = NmDim.SafeH, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = NmColor.OnSurface,
            fontSize = NmType.Label,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (actionLabel != null && onAction != null) {
            TouchButton(actionLabel, onAction)
        }
    }
}
