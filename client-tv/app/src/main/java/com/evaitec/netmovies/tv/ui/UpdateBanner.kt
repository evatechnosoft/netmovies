package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.UpdateUi
import com.evaitec.netmovies.tv.UpdateViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateBanner(vm: UpdateViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (val s = ui) {
        is UpdateUi.Available -> Bar("Güncelleme mevcut: ${s.info.tag}", "İndir & Kur") { vm.download(s.info) }
        is UpdateUi.Downloading -> Bar("İndiriliyor: ${s.tag}…", null, null)
        is UpdateUi.Failed -> Bar("Güncelleme hatası: ${s.message}", "Tekrar") { vm.check() }
        UpdateUi.Idle -> {} // banner yok
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Bar(message: String, actionLabel: String?, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2140))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, modifier = Modifier.padding(end = 8.dp))
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
