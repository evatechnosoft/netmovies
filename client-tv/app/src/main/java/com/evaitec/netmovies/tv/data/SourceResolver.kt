package com.evaitec.netmovies.tv.data

// Oynatma kaynağı ZİNCİRİ artık burada değil: sunucuda, /api/v1/resolve_sources.
// Sağlayıcı arama, bölüm çözme, alternatif kaynak toplama ve dil sıralaması tek
// yerde yapılır; TV, telefon ve web aynı listeyi aynı sırada alır. Bu dosyada
// yalnızca SUNUM yardımcıları kalır — kural kopyası taşımaz.

/** Kaynağın ekranda görünen adı: "DiziBox · Türkçe dublaj" (dil sunucudan gelir). */
fun languageLabel(link: StreamLink): String {
    val provider = link.name.substringBefore(" · ").ifBlank { "Kaynak" }
    val language = link.language?.label?.takeIf { it.isNotBlank() } ?: "dil bilinmiyor"
    return "$provider · $language"
}

/**
 * Kaynak Türkçe dublaj mı? (rank sunucudan: 0 dublaj, 1 Türkçe altyazı, 2 bilinmiyor —
 * stream/Public/API/v1/Libs/language.py). Dublajlı kaynakta altyazı açılmaz.
 */
fun isDubbed(link: StreamLink): Boolean = link.language?.rank == 0

/**
 * Altyazı dosyasının dil kodu — ExoPlayer'ın altyazı parçasını etiketlemesi için.
 * Oynatıcıya ait bir ayrıntı olduğu için istemcide kalır.
 */
fun guessSubtitleLang(name: String): String {
    val n = name.lowercase()
    return when {
        "türk" in n || "turk" in n || n.startsWith("tr") -> "tr"
        "ing" in n || "eng" in n || n.startsWith("en") -> "en"
        else -> "und"
    }
}
