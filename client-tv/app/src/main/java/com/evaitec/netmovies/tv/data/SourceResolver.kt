package com.evaitec.netmovies.tv.data

// Oynatma kaynağı zinciri.
//
// Aynı film/dizi birden çok sitede var. Eskiden oynatıcı AÇILIRKEN altı
// sağlayıcı sırayla taranıyor, kullanıcı hepsi bitene kadar bekliyordu; hiçbiri
// çalışmazsa ekrandan atan bir hata kutusu çıkıyordu. Artık:
//   1) seçili sağlayıcı hemen denenir (ilk link gelir gelmez oynatma başlar),
//   2) diğer sağlayıcılar ARKA PLANDA taranır, bulunan linkler kuyruğa eklenir,
//   3) çalmayan link olursa sessizce sıradakine geçilir; ekranda yalnız durum yazar.
//
// Sıralama dil tercihine göre: önce Türkçe dublaj, sonra Türkçe altyazılı,
// sonra kalanlar. Aynı gruptaki linklerin kendi sırası korunur (stable sort).

/** Bir linkin dil önceliği: 0 = dublaj, 1 = Türkçe altyazı, 2 = diğer. */
fun languageRank(link: StreamLink): Int {
    val name = link.name.lowercase()
    val subs = link.subtitles.joinToString(" ") { it.name }.lowercase()

    val dubbed = DUB_MARKERS.any { it in name }
    if (dubbed) return 0

    val turkishSub = TR_SUB_MARKERS.any { it in name } || TR_SUB_MARKERS.any { it in subs } ||
        link.subtitles.any { guessSubtitleLang(it.name) == "tr" }
    if (turkishSub) return 1

    return 2
}

/** Kuyruğu dil tercihine göre sıralar; grup içi sıra bozulmaz. */
fun orderByLanguage(links: List<StreamLink>): List<StreamLink> = links.sortedBy(::languageRank)

/** Altyazı adından dil kodu tahmini (oynatıcı track etiketleri de bunu kullanır). */
fun guessSubtitleLang(name: String): String {
    val n = name.lowercase()
    return when {
        TR_SUB_MARKERS.any { it in n } || n.startsWith("tr") -> "tr"
        "ing" in n || "eng" in n || n.startsWith("en") -> "en"
        else -> "und"
    }
}

/** Arama sorgusu için başlığı gürültüden arındırır. */
fun searchableTitle(title: String?): String =
    (title ?: "")
        .replace(TITLE_NOISE, " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '·', ':')

/**
 * Alternatif sağlayıcı sırası — seçili sağlayıcı listeden çıkarılır, o hep ilk denenir.
 * Sıra kasıtlı: dublaj ağırlıklı kaynaklar önde.
 */
fun alternativePlugins(selected: String): List<String> =
    ALTERNATIVE_PLUGINS.filter { it != selected }

private val DUB_MARKERS = listOf("dublaj", "dublajlı", "türkçe dublaj", "tr dublaj", "dubbed")
private val TR_SUB_MARKERS = listOf("türkçe altyazı", "türkçe alt yazı", "altyazı", "alt yazı", "turkce altyazi", "türkçe", "turkish")
private val ALTERNATIVE_PLUGINS = listOf("HDFilmCehennemi", "DiziBox", "DiziYou", "DiziMom", "Dizilla", "RecTV")

private val TITLE_NOISE = Regex(
    """(?i)\bizle\b|\bfull\s*hd\b|\bhd\b|\b4k\b|\b1080p?\b|\b720p?\b|""" +
        """\bt[üu]rk[çc]e\b|\bdublaj\b|\balt\s*yaz[ıi]l[ıi]?\b|\baltyaz[ıi]\b|\bdizisi\b|\bfilmi\b"""
)
