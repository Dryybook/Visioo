package com.visioo.app.feature.player

import com.visioo.app.data.Season
import java.text.Normalizer
import java.util.Locale

/** What the player is asked to play. An episode is always played by its own id. */
enum class PlayKind(val path: String) {
    Movie("movie"), Episode("episode");

    companion object { fun fromPath(value: String) = entries.firstOrNull { it.path == value } }
}

/**
 * Decides what to do when playback of a source fails. Bounded on purpose: each source gets one retry,
 * a fresh resolve is allowed once per session (for expired signed URLs), and after the last source we give up.
 */
class FallbackPolicy(private val sourceCount: Int) {
    sealed interface Action {
        data object RetrySame : Action
        data object Refresh : Action
        data class Next(val index: Int) : Action
        data object GiveUp : Action
    }

    var index = 0
        private set
    private var failuresOnCurrent = 0
    private var refreshed = false

    fun onError(authOrExpired: Boolean): Action {
        failuresOnCurrent++
        if (authOrExpired && !refreshed) {
            refreshed = true
            failuresOnCurrent = 0
            return Action.Refresh
        }
        if (failuresOnCurrent == 1) return Action.RetrySame
        return if (index + 1 < sourceCount) {
            index++
            failuresOnCurrent = 0
            Action.Next(index)
        } else {
            Action.GiveUp
        }
    }

    /** New sources after a refresh: start again from the best one, but never refresh twice. */
    fun onRefreshed(newCount: Int): FallbackPolicy = FallbackPolicy(newCount).also { it.refreshed = true }
}

object ResumePolicy {
    const val MIN_RESUME_MS = 2 * 60_000L
    const val COMPLETED_RATIO = 0.95

    /** Spec: under 2 minutes restart; above 95% it is finished, so restart; otherwise resume. */
    fun startPosition(positionMs: Long, durationMs: Long): Long = when {
        positionMs < MIN_RESUME_MS -> 0L
        durationMs > 0 && positionMs.toDouble() / durationMs > COMPLETED_RATIO -> 0L
        else -> positionMs
    }

    fun isCompleted(positionMs: Long, durationMs: Long) = durationMs > 0 && positionMs.toDouble() / durationMs > COMPLETED_RATIO
}

/** Next episode in season order, rolling over to the next season. Null at the end of the series. */
fun nextEpisodeId(seasons: List<Season>, currentEpisodeId: String): String? {
    val ordered = seasons.sortedBy { it.number }.flatMap { s -> s.episodes.sortedBy { it.number } }
    val i = ordered.indexOfFirst { it.id == currentEpisodeId }
    return if (i >= 0) ordered.getOrNull(i + 1)?.id else null
}

object TrackLabels {
    /** Human-friendly audio name: French is shown as VF, English as VO, anything else by its language name. */
    fun audio(language: String?, label: String?): String {
        val code = language?.lowercase()?.substringBefore('-')
        return when {
            code == "fr" -> "Français (VF)"
            code == "en" -> "English (VO)"
            !label.isNullOrBlank() -> label
            !code.isNullOrBlank() && code != "und" -> languageName(code)
            else -> "Audio"
        }
    }

    fun subtitle(language: String?, label: String?): String {
        val code = language?.lowercase()?.substringBefore('-')
        return when {
            !label.isNullOrBlank() -> label
            !code.isNullOrBlank() && code != "und" -> languageName(code)
            else -> "Sous-titres"
        }
    }

    fun quality(height: Int) = "${height}p"

    private fun languageName(code: String): String =
        Locale(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase() }.ifBlank { code }

    fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
