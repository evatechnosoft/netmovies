package com.evaitec.netmovies.tv.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Ekrana yazılacak hata metni. Ham `e.message` ("Unable to resolve host …",
 * "HTTP 502") kumandayla koltukta oturan için anlamsız; ne olduğunu ve neyin
 * denenebileceğini söyle. Ağ dışı hatada ekranın kendi cümlesi ([varsayilan]).
 */
fun Throwable.kullaniciMesaji(varsayilan: String): String = when (this) {
    is SocketTimeoutException -> "Sunucu yanıt vermedi"
    is UnknownHostException, is ConnectException -> "Sunucuya ulaşılamıyor — ev sunucusu açık mı?"
    is IOException -> "Bağlantı koptu"
    else -> varsayilan
}
