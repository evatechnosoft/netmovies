package com.evaitec.netmovies.tv.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * İzleme senkronu uçlarının TEL ÜZERİNDEKİ hâli.
 *
 * Kritik nokta: `saveProgress`/`toggleFavorite` GÖVDESİZ POST + query gönderir
 * (stream middleware önce query params'a bakar). OkHttp POST'ta null gövdeyi
 * reddeder — bu yüzden isteğin gerçekten üretildiği burada kanıtlanır, imza
 * kontrolüyle değil.
 */
class WatchSyncApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: NetMoviesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NetMoviesApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun saveProgressPostsQueryWithoutBody() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":{"ok":true}}"""))

        val res = api.saveProgress(
            title = "Inception (2010) izle",
            plugin = "HDFilmCehennemi",
            contentUrl = "https://x.tld/film/inception/",
            positionSeconds = 120.5,
            durationSeconds = 7200.0,
        )

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals(0L, req.bodySize)
        val path = req.path.orEmpty()
        assertTrue(path, path.startsWith("/api/v1/progress?"))
        assertTrue(path, path.contains("position_seconds=120.5"))
        assertTrue(path, path.contains("duration_seconds=7200.0"))
        assertTrue(path, path.contains("plugin=HDFilmCehennemi"))
        assertTrue(res.result.ok)
    }

    @Test
    fun toggleFavoritePostsContentUrl() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":{"ok":true,"is_favorite":true}}"""))

        val res = api.toggleFavorite(
            title = "Mayday",
            plugin = "HDFilmCehennemi",
            contentUrl = "https://x.tld/film/mayday/",
        )

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path.orEmpty().contains("content_url=https"))
        assertTrue(res.result.isFavorite)
    }

    @Test
    fun continueWatchingParsesServerShape() = runBlocking {
        // Canlı sunucudan alınmış gerçek gövde (alan adları sözleşme).
        server.enqueue(
            MockResponse().setBody(
                """{"with":"x","schema":"/api/v1/schema","result":[
                   {"content_key":"mayday|movie","plugin":"HDFilmCehennemi","title":"Mayday",
                    "poster":"","media_type":"movie","episode":"","position_seconds":120.5,
                    "duration_seconds":7200.0,"updated_at":1788540813,
                    "content_url":"https://x.tld/film/mayday/"}]}""",
            ),
        )

        val rows = api.continueWatching().result
        assertEquals(1, rows.size)
        assertEquals("https://x.tld/film/mayday/", rows[0].contentUrl)
        assertEquals(120.5, rows[0].positionSeconds, 0.001)
    }

    /** URL biçimi köprüsü: sunucu HAM tutar, MediaItem quote_plus KODLU taşır. */
    @Test
    fun urlFormsRoundTrip() {
        val raw = "https://www.hdfilmcehennemi.now/film/mayday-2026-izle/"
        val encoded = encodedUrl(raw)
        assertTrue(encoded, encoded.startsWith("https%3A%2F%2F"))
        assertEquals(raw, rawUrl(encoded))
        // Zaten ham/kodlu olanı ikinci kez dönüştürmez (çift kodlama tuzağı).
        assertEquals(raw, rawUrl(raw))
        assertEquals(encoded, encodedUrl(encoded))
    }
}
