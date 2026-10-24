package com.visioo.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures in resources/contract are real responses captured from the backend
 * (tokens redacted). If the backend changes a response shape, regenerate them and this test shows what broke.
 */
class ContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private inline fun <reified T> load(name: String): T =
        json.decodeFromString(javaClass.getResource("/contract/$name.json")!!.readText())

    @Test fun session() {
        val s = load<SessionDto>("register")
        assertEquals("redacted", s.accessToken)
        assertTrue(s.userId.isNotEmpty() && s.deviceId.isNotEmpty())
        assertEquals(900, s.expiresIn)
    }

    @Test fun home() {
        val h = load<HomeResponseDto>("home")
        assertEquals("Big Buck Bunny", h.hero.single().title)
        assertNotNull(h.hero.single().overview)
        val section = h.sections.single()
        assertEquals("poster", section.layout)
        assertEquals(listOf(false, true), section.items.map { it.isVip })
        assertEquals("movie", section.items.first().type)
        assertNull(section.items.first().posterUrl)
    }

    @Test fun listsAndSearch() {
        assertEquals(1, load<GenresResponseDto>("genres").genres.size)
        assertEquals(2, load<PagedContentDto>("movies").items.size)
        assertEquals("series", load<PagedContentDto>("series").items.single().type)
        assertEquals("Big Buck Bunny", load<SearchResponseDto>("search").items.single().title)
    }

    @Test fun details() {
        val m = load<MovieDetailDto>("movie")
        assertEquals("Big Buck Bunny", m.title)
        assertTrue(m.canPlay)
        assertEquals("animation", m.genres.single().slug)
        val s = load<SeriesDetailDto>("series_detail")
        assertEquals(1, s.seasons.single().seasonNumber)
        assertEquals(1, s.seasons.single().episodeCount)
        assertEquals(12, s.ageRating)
        assertEquals(7.5, s.rating!!, 0.0)
    }

    @Test fun seasonsWithEpisodes() {
        val e = load<SeasonsResponseDto>("seasons").seasons.single().episodes.single()
        assertEquals("Pilot", e.title)
        assertEquals(45, e.runtimeMin)
    }

    @Test fun accountAndProfiles() {
        val me = load<MeDto>("me")
        assertEquals("contract@example.com", me.email)
        assertFalse(me.vip.active)
        assertFalse(me.hasRecovery)
        assertFalse(load<VipDto>("vip").active)
        val p = load<ProfilesResponse>("profiles").profiles.single()
        assertEquals("Djidjoho", p.name)
        assertEquals(18, p.maturityLevel)
        assertEquals(12, load<RecoveryWordsDto>("recovery").words.size)
    }

    @Test fun errorEnvelopeCodeIsExtracted() = kotlinx.coroutines.runBlocking {
        val body = javaClass.getResource("/contract/error.json")!!.readText()
        val result = safeApiCall<Unit> {
            throw retrofit2.HttpException(retrofit2.Response.error<Any>(400, okhttp3.ResponseBody.create(null, body)))
        }
        assertEquals("INVALID_KEY", (result.exceptionOrNull() as ApiException).code)
    }
}
