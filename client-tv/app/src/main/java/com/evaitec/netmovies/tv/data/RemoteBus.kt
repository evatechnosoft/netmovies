package com.evaitec.netmovies.tv.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// Telefon kumandasından gelen komutların TV içindeki dağıtım noktası.
//
// Yoklama tek yerde (MainActivity) yapılır ve sonucu buraya düşer. Her ekranın
// kendi yoklama döngüsünü kurması denendi ve yanlış çıktı: v0.1.56'da döngü
// yalnız HomeScreen'de olduğu için oynatıcı açıkken kumanda tamamen ölüydü —
// "durdur" komutu hiçbir zaman TV'ye ulaşmıyordu.
//
// replay YOK: kumanda komutu ANLIK bir olaydır. Tekrar oynatılırsa ekran her
// açılışında eski bir "duraklat" yeniden uygulanır.
object RemoteBus {
    private val _komutlar = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
    val komutlar: SharedFlow<RemoteCommand> = _komutlar

    /** Dolu tampon komutu düşürür — kumandada eski tuş, yeni tuştan değersizdir. */
    fun yayinla(komut: RemoteCommand): Boolean = _komutlar.tryEmit(komut)
}
