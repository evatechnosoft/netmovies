package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Query

class NetMoviesApiContractTest {
    @Test
    fun loadItemUsesEncodedUrlContract() {
        val method = NetMoviesApi::class.java.methods.single { it.name == "loadItem" }
        val queryAnnotations = method.parameterAnnotations
            .flatMap { annotations -> annotations.filterIsInstance<Query>() }

        // `title`/`type` kurtarma parametreleri: kayıtlı adres çürüdüğünde sunucu
        // içeriği başlıktan başka sağlayıcıda bulur.
        assertEquals(listOf("plugin", "encoded_url", "title", "type"), queryAnnotations.map(Query::value))
        assertTrue(queryAnnotations.single { it.value == "encoded_url" }.encoded)
    }
}
