package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// Hem DOKUNMATİK (telefon) hem D-pad (TV) ile çalışan buton.
// androidx.tv.material3.Button TV/D-pad odaklıydı → telefonda tıklanmıyordu.
// foundation clickable her ikisini de destekler; focusable ile TV'de de seçilir.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TouchButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary)
    }
}
