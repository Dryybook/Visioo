package com.visioo.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

enum class ApiError {
    Network,
    Unauthorized,
    Forbidden,
    NotFound,
    RateLimited,
    Server,
    Unknown,
}

/** [code] is the server's machine-readable error code (e.g. `INVALID_KEY`), when the response had one. */
class ApiException(val error: ApiError, val code: String? = null, cause: Throwable? = null) : Exception(code ?: error.name, cause)

internal fun Throwable.toApiError(): ApiError = when (this) {
    is IOException -> ApiError.Network
    is HttpException -> when (code()) {
        401 -> ApiError.Unauthorized
        403 -> ApiError.Forbidden
        404 -> ApiError.NotFound
        429 -> ApiError.RateLimited
        in 500..599 -> ApiError.Server
        else -> ApiError.Unknown
    }
    else -> ApiError.Unknown
}

internal fun Throwable.serverErrorCode(): String? {
    if (this !is HttpException) return null
    return try {
        val body = response()?.errorBody()?.string() ?: return null
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }
}

/** Runs a network call and maps every failure to an [ApiException]. Never logs payloads or tokens. */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(ApiException(e.toApiError(), e.serverErrorCode(), e))
}
