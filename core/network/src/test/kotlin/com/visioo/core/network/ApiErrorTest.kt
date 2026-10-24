package com.visioo.core.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun errorOf(t: Throwable): ApiError = runBlocking {
        val result = safeApiCall<Unit> { throw t }
        (result.exceptionOrNull() as ApiException).error
    }

    @Test fun ioExceptionIsNetwork() = assertEquals(ApiError.Network, errorOf(IOException()))
    @Test fun http401IsUnauthorized() = assertEquals(ApiError.Unauthorized, errorOf(http(401)))
    @Test fun http403IsForbidden() = assertEquals(ApiError.Forbidden, errorOf(http(403)))
    @Test fun http404IsNotFound() = assertEquals(ApiError.NotFound, errorOf(http(404)))
    @Test fun http429IsRateLimited() = assertEquals(ApiError.RateLimited, errorOf(http(429)))
    @Test fun http503IsServer() = assertEquals(ApiError.Server, errorOf(http(503)))
    @Test fun otherIsUnknown() = assertEquals(ApiError.Unknown, errorOf(IllegalStateException()))

    @Test fun successIsWrapped() = runBlocking {
        assertTrue(safeApiCall { 1 }.isSuccess)
    }
}

class ServerErrorCodeTest {
    @Test fun readsTheCodeFromTheErrorBody() = runBlocking {
        val body = """{"error":{"code":"KEY_EXPIRED","message":"x"}}""".toResponseBody()
        val result = safeApiCall<Unit> { throw HttpException(Response.error<Any>(403, body)) }
        val e = result.exceptionOrNull() as ApiException
        assertEquals(ApiError.Forbidden, e.error)
        assertEquals("KEY_EXPIRED", e.code)
    }

    @Test fun toleratesBodiesWithoutACode() = runBlocking {
        val result = safeApiCall<Unit> { throw HttpException(Response.error<Any>(500, "oops".toResponseBody())) }
        assertEquals(null, (result.exceptionOrNull() as ApiException).code)
    }
}
