package com.visioo.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class MemoryStore(var access: String? = "old-access", var refresh: String? = "old-refresh", var profile: String? = "p1") : SessionStore {
    override fun accessToken() = access
    override fun refreshToken() = refresh
    override fun profileId() = profile
    override fun saveTokens(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
    override fun setProfileId(id: String?) { profile = id }
    override fun clear() { access = null; refresh = null; profile = null }
}

class TokenAuthenticatorTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryStore
    private var expired = 0

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        store = MemoryStore()
        expired = 0
    }

    @After fun tearDown() = server.shutdown()

    private fun api() = NetworkModule.createApi(server.url("/api/v1/").toString(), store) { expired++ }

    private val sessionJson =
        """{"userId":"u","accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900,"deviceId":"d"}"""
    private val homeJson = """{"sections":[]}"""
    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun refreshesOnceAndRetriesWithTheNewToken() = runBlocking {
        server.enqueue(json(401, """{"error":{"code":"UNAUTHORIZED"}}"""))
        server.enqueue(json(200, sessionJson))
        server.enqueue(json(200, homeJson))

        api().home()

        val first = server.takeRequest()
        assertEquals("Bearer old-access", first.getHeader("Authorization"))
        assertEquals("p1", first.getHeader("X-Profile-Id"))
        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertEquals("""{"refreshToken":"old-refresh"}""", refresh.body.readUtf8())
        val retry = server.takeRequest()
        assertEquals("Bearer new-access", retry.getHeader("Authorization"))
        assertEquals("new-refresh", store.refresh)
        assertEquals(0, expired)
    }

    @Test fun clearsTheSessionWhenTheRefreshTokenIsRejected() = runBlocking {
        server.enqueue(json(401, "{}"))
        server.enqueue(json(401, """{"error":{"code":"INVALID_REFRESH_TOKEN"}}"""))

        val result = safeApiCall { api().home() }

        assertEquals(ApiError.Unauthorized, (result.exceptionOrNull() as ApiException).error)
        assertNull(store.access)
        assertNull(store.refresh)
        assertEquals(1, expired)
    }

    @Test fun keepsTheSessionWhenRefreshFailsBecauseOfServerTrouble() = runBlocking {
        server.enqueue(json(401, "{}"))
        server.enqueue(json(503, "{}"))

        val result = safeApiCall { api().home() }

        assertEquals(ApiError.Unauthorized, (result.exceptionOrNull() as ApiException).error)
        assertEquals("old-refresh", store.refresh)
        assertEquals(0, expired)
    }

    @Test fun doesNotLoopWhenTheRetryIsAlsoUnauthorized() = runBlocking {
        server.enqueue(json(401, "{}"))
        server.enqueue(json(200, sessionJson))
        server.enqueue(json(401, "{}"))

        val result = safeApiCall { api().home() }

        assertEquals(ApiError.Unauthorized, (result.exceptionOrNull() as ApiException).error)
        assertEquals(3, server.requestCount)
    }

    @Test fun neverTriesToRefreshOnLoginFailure() = runBlocking {
        server.enqueue(json(401, """{"error":{"code":"INVALID_CREDENTIALS","message":"x"}}"""))

        val result = safeApiCall { api().login(LoginRequest("a@b.c", "password1", DeviceInfoDto("install-1", "android"))) }

        val e = result.exceptionOrNull() as ApiException
        assertEquals("INVALID_CREDENTIALS", e.code)
        assertEquals(1, server.requestCount)
        assertEquals(0, expired)
    }

    @Test fun omitsProfileHeaderWhenNoProfileIsSelected() = runBlocking {
        store.profile = null
        server.enqueue(json(200, homeJson))
        api().home()
        assertNull(server.takeRequest().getHeader("X-Profile-Id"))
    }
}
