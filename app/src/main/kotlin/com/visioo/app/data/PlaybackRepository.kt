package com.visioo.app.data

import com.visioo.core.network.EpisodeDetailDto
import com.visioo.core.network.ProgressRequest
import com.visioo.core.network.ResolveRequest
import com.visioo.core.network.SourceDto
import com.visioo.core.network.VisiooApi
import com.visioo.core.network.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class SourceType { Hls, Dash, Mp4, Webm }

data class SubtitleFile(val url: String, val language: String?, val label: String?, val format: String?)

data class PlayableSource(
    val id: String,
    val provider: String,
    val type: SourceType,
    val quality: Int?,
    val url: String,
    val subtitles: List<SubtitleFile>,
)

data class SavedPosition(val positionMs: Long, val durationMs: Long)

class PlaybackRepository(
    private val api: VisiooApi,
    private val appScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Cached(val sources: List<PlayableSource>, val at: Long)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    /**
     * The server checks VIP, maturity and source authorization, and returns short-lived signed URLs, best source first.
     * A recent answer is reused so a prefetch makes "Watch" start instantly; [fresh] bypasses it (expired links).
     * The cache lives shorter than the signed links, so a cached link is never close to expiry.
     */
    suspend fun resolve(contentType: String, contentId: String, fresh: Boolean = false): Result<List<PlayableSource>> {
        val key = "$contentType:$contentId"
        if (!fresh) cache[key]?.takeIf { now() - it.at < CACHE_TTL_MS }?.let { return Result.success(it.sources) }
        return safeApiCall {
            api.resolve(ResolveRequest(contentType, contentId)).sources.mapNotNull { it.toDomain() }
        }.onSuccess { cache[key] = Cached(it, now()) }.onFailure { cache.remove(key) }
    }

    /** Warms the cache while the user is still on the details page. Failures are ignored: the player will report them. */
    fun prefetch(contentType: String, contentId: String) {
        appScope.launch { resolve(contentType, contentId) }
    }

    fun clearCache() = cache.clear()

    suspend fun position(contentType: String, contentId: String): Result<SavedPosition> =
        safeApiCall { api.position(contentType, contentId).let { SavedPosition(it.positionMs, it.durationMs) } }

    suspend fun episode(id: String): Result<EpisodeDetailDto> = safeApiCall { api.episode(id) }

    /** Fire and forget on the app scope, so leaving the screen does not cancel the last save. */
    fun saveProgress(contentType: String, contentId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        appScope.launch { safeApiCall { api.saveProgress(ProgressRequest(contentType, contentId, positionMs, durationMs)) } }
    }
}

const val CACHE_TTL_MS = 5 * 60_000L

internal fun SourceDto.toDomain(): PlayableSource? {
    val t = when (type.uppercase()) {
        "HLS" -> SourceType.Hls
        "DASH" -> SourceType.Dash
        "MP4" -> SourceType.Mp4
        "WEBM" -> SourceType.Webm
        else -> return null
    }
    return PlayableSource(id, provider, t, quality, url, subtitleTracks.map { SubtitleFile(it.url, it.language, it.label, it.format) })
}
