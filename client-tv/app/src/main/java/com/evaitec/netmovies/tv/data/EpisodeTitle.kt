package com.evaitec.netmovies.tv.data

// Kart başlığından sezon/bölüm çıkarır: "… 3.Sezon 8.Bölüm", "3. Sezon 8. Bölüm",
// "S3B8". Bölüm sayfası kartlarında (DiziMom/DiziPal "son bölümler" rafı)
// adres eşleşmesi tutmadığında tek ipucu budur. Bulunamazsa -1.
fun basliktanBolum(
    baslik: String?,
    bolumler: List<EpisodeItem>,
): Int {
    val metin = baslik ?: return -1
    val kalip = listOf(
        // "Bölüm"/"Bolum" ayrımı ASCII kalıpla geçilir (`[Bb].l.m`): kaynak dosya
        // UTF-8 olsa da derleyici platform kodlamasıyla okursa (Windows cp1252)
        // desenin içindeki "ö/ü" bozulur ve hiçbir başlık eşleşmez.
        Regex("""(\d+)\s*\.?\s*[Ss]ezon\s*(\d+)\s*\.?\s*[Bb].l.m""", RegexOption.IGNORE_CASE),
        Regex("""[Ss](\d+)\s*[BbEe](\d+)"""),
        Regex("""(\d+)x(\d+)"""),
    )
    for (r in kalip) {
        val m = r.find(metin) ?: continue
        val sezon = m.groupValues[1].toIntOrNull() ?: continue
        val bolum = m.groupValues[2].toIntOrNull() ?: continue
        val sira = bolumler.indexOfFirst { it.season == sezon && it.episode == bolum }
        if (sira >= 0) return sira
    }
    return -1
}
