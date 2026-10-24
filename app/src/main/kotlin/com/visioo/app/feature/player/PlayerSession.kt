@file:OptIn(UnstableApi::class)

package com.visioo.app.feature.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.visioo.app.data.PlayableSource
import com.visioo.app.data.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

data class TrackOption(val id: String, val label: String, val selected: Boolean)

data class PlayerSnapshot(
    val isPlaying: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val qualities: List<TrackOption> = emptyList(),
    val audios: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = emptyList(),
)

/** Thin wrapper over ExoPlayer: loads a source, exposes tracks, reports failures. Holds no business rules. */
class PlayerSession(context: Context) {
    /** Set by the screen once it has the ViewModel. */
    var onError: (authOrExpired: Boolean) -> Unit = {}
    var onEnded: () -> Unit = {}

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(OkHttpClient())))
        .build()

    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    private var qualityHeights: List<Int> = emptyList()
    private var forcedHeight: Int? = null
    private var audioRefs: List<Pair<Tracks.Group, Int>> = emptyList()
    private var textRefs: List<Pair<Tracks.Group, Int>> = emptyList()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onEnded()
            refresh()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onTracksChanged(tracks: Tracks) = refreshTracks(tracks)
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = refresh()
        override fun onPlayerError(error: PlaybackException) {
            val http = generateSequence(error.cause) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            onError(http?.responseCode in AUTH_OR_EXPIRED)
        }
    }

    init {
        player.addListener(listener)
    }

    fun load(source: PlayableSource, startMs: Long) {
        forcedHeight = null
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(source.type.mime())
            .setSubtitleConfigurations(
                source.subtitles.map {
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                        .setMimeType(if (it.format.equals("srt", true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                        .setLanguage(it.language)
                        .setLabel(it.label)
                        .build()
                },
            )
            .build()
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
    fun seekTo(ms: Long) { player.seekTo(ms.coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)); refresh() }
    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)
    fun setSpeed(speed: Float) { player.setPlaybackSpeed(speed); refresh() }

    /** @param height null for Auto (adaptive). */
    fun selectQuality(height: Int?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (height == null) {
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            val best = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { g -> (0 until g.length).map { g to it } }
                .filter { (g, i) -> g.getTrackFormat(i).height == height }
                .maxByOrNull { (g, i) -> g.getTrackFormat(i).bitrate } ?: return
            params.setOverrideForType(TrackSelectionOverride(best.first.mediaTrackGroup, best.second))
        }
        forcedHeight = height
        player.trackSelectionParameters = params.build()
        refreshTracks(player.currentTracks)
    }

    fun selectAudio(optionId: String) {
        val (group, index) = audioRefs.getOrNull(optionId.toIntOrNull() ?: return) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
    }

    /** @param optionId [SUBTITLES_OFF] to turn subtitles off. */
    fun selectSubtitle(optionId: String) {
        val params = player.trackSelectionParameters.buildUpon()
        if (optionId == SUBTITLES_OFF) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val (group, index) = textRefs.getOrNull(optionId.toIntOrNull() ?: return) ?: return
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        player.trackSelectionParameters = params.build()
    }

    /** Position, duration and buffering change continuously, so the screen polls this a few times per second. */
    fun refresh() {
        _snapshot.value = _snapshot.value.copy(
            isPlaying = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            bufferedMs = player.bufferedPosition,
            speed = player.playbackParameters.speed,
        )
    }

    private fun refreshTracks(tracks: Tracks) {
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        qualityHeights = video.flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
            .filter { it > 0 }.distinct().sortedDescending()
        // One height means nothing to choose (e.g. a single MP4), so no Auto entry is offered either.
        val qualities = if (qualityHeights.size < 2) emptyList() else
            listOf(TrackOption(AUTO, "", forcedHeight == null)) + qualityHeights.map { TrackOption(it.toString(), TrackLabels.quality(it), forcedHeight == it) }

        audioRefs = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).map { g to it } }
        val audios = audioRefs.mapIndexed { i, (g, t) ->
            val f = g.getTrackFormat(t)
            TrackOption(i.toString(), TrackLabels.audio(f.language, f.label), g.isTrackSelected(t))
        }.let { if (it.size < 2) emptyList() else it }

        textRefs = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { g -> (0 until g.length).map { g to it } }
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val subtitles = if (textRefs.isEmpty()) emptyList() else
            listOf(TrackOption(SUBTITLES_OFF, "", textDisabled || textRefs.none { (g, t) -> g.isTrackSelected(t) })) +
                textRefs.mapIndexed { i, (g, t) ->
                    val f = g.getTrackFormat(t)
                    TrackOption(i.toString(), TrackLabels.subtitle(f.language, f.label), !textDisabled && g.isTrackSelected(t))
                }

        _snapshot.value = _snapshot.value.copy(qualities = qualities, audios = audios, subtitles = subtitles)
        refresh()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    private fun SourceType.mime() = when (this) {
        SourceType.Hls -> MimeTypes.APPLICATION_M3U8
        SourceType.Dash -> MimeTypes.APPLICATION_MPD
        SourceType.Mp4 -> MimeTypes.VIDEO_MP4
        SourceType.Webm -> MimeTypes.VIDEO_WEBM
    }

    companion object {
        const val AUTO = "auto"
        const val SUBTITLES_OFF = "off"
        private val AUTH_OR_EXPIRED = setOf(401, 403, 410)
    }
}
