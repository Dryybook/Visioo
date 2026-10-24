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

class PlaybackRepository(private val api: VisiooApi, private val appScope: CoroutineScope) {
    /** The server checks VIP, maturity and source authorization, and returns short-lived signed URLs, best source first. */
    suspend fun resolve(contentType: String, contentId: String): Result<List<PlayableSource>> = safeApiCall {
        api.resolve(ResolveRequest(contentType, contentId)).sources.mapNotNull { it.toDomain() }
    }

    suspend fun position(contentType: String, contentId: String): Result<SavedPosition> =
        safeApiCall { api.position(contentType, contentId).let { SavedPosition(it.positionMs, it.durationMs) } }

    suspend fun episode(id: String): Result<EpisodeDetailDto> = safeApiCall { api.episode(id) }

    /** Fire and forget on the app scope, so leaving the screen does not cancel the last save. */
    fun saveProgress(contentType: String, contentId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        appScope.launch { safeApiCall { api.saveProgress(ProgressRequest(contentType, contentId, positionMs, durationMs)) } }
    }
}

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
