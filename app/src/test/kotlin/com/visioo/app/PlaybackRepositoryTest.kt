package com.visioo.app

import com.visioo.app.data.CACHE_TTL_MS
import com.visioo.app.data.PlaybackRepository
import com.visioo.core.network.ResolveRequest
import com.visioo.core.network.ResolveResponseDto
import com.visioo.core.network.SourceDto
import com.visioo.core.network.VisiooApi
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRepositoryTest {
    private var clock = 0L
    private var calls = 0
    private var fail = false

    /** Only `resolve` is implemented; any other call is a test bug. */
    private val api: VisiooApi = Proxy.newProxyInstance(VisiooApi::class.java.classLoader, arrayOf(VisiooApi::class.java)) { _, method, args ->
        check(method.name == "resolve") { "unexpected call ${method.name}" }
        calls++
        if (fail) throw java.io.IOException("offline")
        val req = args[0] as ResolveRequest
        ResolveResponseDto(listOf(SourceDto(id = "s$calls", provider = "p", type = "MP4", quality = 720, url = "https://cdn/${req.contentId}.mp4?n=$calls")))
    } as VisiooApi

    private val repo = PlaybackRepository(api, CoroutineScope(Dispatchers.Unconfined), now = { clock })

    @Test fun reusesARecentAnswer() = runBlocking {
        val first = repo.resolve("movie", "m1").getOrThrow()
        clock += CACHE_TTL_MS - 1
        val second = repo.resolve("movie", "m1").getOrThrow()
        assertEquals(1, calls)
        assertEquals(first, second)
    }

    @Test fun refetchesAfterTheTtl() = runBlocking {
        repo.resolve("movie", "m1")
        clock += CACHE_TTL_MS
        repo.resolve("movie", "m1")
        assertEquals(2, calls)
    }

    @Test fun freshBypassesTheCacheAndUpdatesIt() = runBlocking {
        repo.resolve("movie", "m1")
        val fresh = repo.resolve("movie", "m1", fresh = true).getOrThrow()
        assertEquals(2, calls)
        assertEquals(fresh, repo.resolve("movie", "m1").getOrThrow())
        assertEquals(2, calls)
    }

    @Test fun keysAreSeparatePerContent() = runBlocking {
        repo.resolve("movie", "m1"); repo.resolve("episode", "m1"); repo.resolve("movie", "m2")
        assertEquals(3, calls)
    }

    @Test fun failuresAreNeverCached() = runBlocking {
        fail = true
        assertTrue(repo.resolve("movie", "m1").isFailure)
        fail = false
        assertTrue(repo.resolve("movie", "m1").isSuccess)
        assertEquals(2, calls)
    }

    @Test fun prefetchWarmsTheCacheSoThePlayerStartsWithoutANetworkCall() = runBlocking {
        repo.prefetch("movie", "m1")
        assertEquals(1, calls)
        repo.resolve("movie", "m1").getOrThrow()
        assertEquals(1, calls)
    }

    @Test fun aFailedPrefetchDoesNotBreakALaterResolve() = runBlocking {
        fail = true
        repo.prefetch("movie", "m1")
        fail = false
        assertTrue(repo.resolve("movie", "m1").isSuccess)
    }
}
