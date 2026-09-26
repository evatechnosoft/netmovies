package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.CihazKipi
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing

/**
 * İlk açılış: bu cihaz ne olacak. Hem kumandayla (odak önerilen kipte başlar) hem
 * dokunuşla seçilir. Sonradan Ayarlar → "Cihaz kipi" satırından değişir.
 */
@Composable
fun KipSecimEkrani(oneri: CihazKipi, onSec: (CihazKipi) -> Unit) {
  val odak = remember { FocusRequester() }
  Column(
    modifier = Modifier.fillMaxSize().background(NmColor.Background).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
  ) {
    Text("NetMovies", fontSize = NmType.Wordmark, fontWeight = FontWeight.ExtraBold, color = NmColor.Primary)
    Text("Bu cihazı nasıl kullanacaksın?", fontSize = NmType.Body, color = NmColor.OnSurface)
    CihazKipi.entries.forEach { kip ->
      var odakli by remember { mutableStateOf(false) }
      val shape = RoundedCornerShape(NmDim.PanelRadius)
      Column(
        modifier = Modifier
          .widthIn(max = 520.dp)
          .fillMaxWidth()
          .clip(shape)
          .background(if (odakli) NmColor.Primary else NmColor.Surface)
          .nmFocusRing(odakli, shape)
          .then(if (kip == oneri) Modifier.focusRequester(odak) else Modifier)
          .onFocusChanged { odakli = it.isFocused }
          .clickable { onSec(kip) }
          .padding(horizontal = 20.dp, vertical = 14.dp),
      ) {
        Text(
          kip.etiket + if (kip == oneri) "  (önerilen)" else "",
          fontSize = NmType.Body,
          fontWeight = FontWeight.SemiBold,
          color = if (odakli) NmColor.OnPrimary else NmColor.OnSurface,
        )
        Text(kip.aciklama, fontSize = NmType.Caption, color = if (odakli) NmColor.OnPrimary else NmColor.OnSurfaceMuted)
      }
    }
    Text("Sonradan Ayarlar → Cihaz kipi", fontSize = NmType.Caption, color = NmColor.OnSurfaceFaint)
  }
  // Tek requestFocus ilk karede sessizce başarısız olabiliyor (hafıza: tv-focus-and-install-traps).
  LaunchedEffect(Unit) {
    repeat(8) {
      if (runCatching { odak.requestFocus() }.isSuccess) return@LaunchedEffect
      androidx.compose.runtime.withFrameNanos {}
    }
  }
}
