package com.visioo.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
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

class ApiException(val error: ApiError, cause: Throwable? = null) : Exception(error.name, cause)

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

/** Runs a network call and maps every failure to an [ApiException]. Never logs payloads or tokens. */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(ApiException(e.toApiError(), e))
}
