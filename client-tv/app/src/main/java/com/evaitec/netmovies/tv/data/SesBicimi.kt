package com.evaitec.netmovies.tv.data

/**
 * Ses izinin okunur biçimi: "AAC 2.0", "Dolby Digital+ 5.1", "Dolby Atmos".
 * Ses sekmesinde iz adının yanında görünür (Dean, 28 Kas: soundbar için hangi
 * kaynağın çok kanallı olduğu seçilirken görülsün).
 */
fun sesBicimi(mime: String?, kanal: Int): String {
    val ad = when (mime) {
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "Dolby Digital"
        "audio/eac3" -> "Dolby Digital+"
        "audio/eac3-joc" -> "Dolby Atmos"
        "audio/true-hd" -> "Dolby TrueHD"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/opus" -> "Opus"
        "audio/mpeg" -> "MP3"
        else -> ""
    }
    val duzen = when (kanal) {
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> ""   // bilinmiyor (-1) ya da alışılmadık düzen: yazma
    }
    return listOf(ad, duzen).filter { it.isNotEmpty() }.joinToString(" ")
}
