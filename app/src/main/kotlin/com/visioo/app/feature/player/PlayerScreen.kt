@file:OptIn(UnstableApi::class)

package com.visioo.app.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.visioo.app.R
import com.visioo.app.ui.PipState
import com.visioo.app.ui.containerViewModel
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooShapes
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.visiooFocus
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L
private const val HIDE_AFTER_MS = 4_000L
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private enum class Panel { Audio, Subtitles, Quality, Speed }

@Composable
fun PlayerScreen(
    kind: PlayKind,
    id: String,
    isTv: Boolean,
    onBack: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onActivateVip: () -> Unit,
) {
    val vm = containerViewModel(key = "player-${kind.path}-$id") {
        PlayerViewModel(it.playbackRepository, it.catalogRepository, it.accountRepository, it.sessionRepository::selectedProfileId, kind, id)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { if (it is PlayerEvent.PlayEpisode) onPlayEpisode(it.id) }
    }

    when (val s = state) {
        PlayerUiState.Loading -> Box(Modifier.fillMaxSize().background(VisiooColors.Black)) { LoadingState() }
        is PlayerUiState.Error -> Column(
            Modifier.fillMaxSize().background(VisiooColors.Black).padding(VisiooSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(s.message), color = VisiooColors.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            if (s.needsVip) VisiooButton(stringResource(R.string.details_vip_required), onActivateVip)
            else VisiooButton(stringResource(R.string.action_retry), vm::load)
            VisiooButton(stringResource(R.string.action_back), onBack, primary = false)
        }
        is PlayerUiState.Ready -> PlayerContent(s, vm, isTv, onBack)
    }
}

@Composable
private fun PlayerContent(state: PlayerUiState.Ready, vm: PlayerViewModel, isTv: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { PlayerSession(context) }
    DisposableEffect(session) { onDispose { session.release() } }
    PlayerSurface(state, vm, session, isTv, onBack)
}

@Composable
private fun PlayerSurface(state: PlayerUiState.Ready, vm: PlayerViewModel, session: PlayerSession, isTv: Boolean, onBack: () -> Unit) {
    val snapshot by session.snapshot.collectAsStateWithLifecycle()
    val inPip by PipState.inPip.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    val rootFocus = remember { FocusRequester() }

    fun touch() { interaction = System.nanoTime() }
    fun showControls() { controlsVisible = true; touch() }

    // (Re)prepare whenever the ViewModel picks another source or retries.
    LaunchedEffect(state.index, state.attempt, state.sources) {
        state.sources.getOrNull(state.index)?.let { session.load(it, state.startMs) }
    }

    // Errors need the position they happened at, so it is read from the player when they fire.
    SideEffect {
        session.onError = { auth -> vm.onPlayerError(auth, session.player.currentPosition) }
        session.onEnded = vm::onEnded
    }

    // Poll position a few times a second, and save it every 15 seconds.
    LaunchedEffect(session) {
        var sinceSave = 0L
        while (true) {
            session.refresh()
            delay(500)
            sinceSave += 500
            if (sinceSave >= 15_000 && session.player.isPlaying) {
                sinceSave = 0
                vm.reportProgress(session.player.currentPosition, session.snapshot.value.durationMs)
            }
        }
    }

    LaunchedEffect(controlsVisible, interaction, snapshot.isPlaying, panel) {
        if (controlsVisible && snapshot.isPlaying && panel == null) {
            delay(HIDE_AFTER_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) { if (!controlsVisible) rootFocus.requestFocus() }

    // Save on the way out and when the app goes to the background.
    val exit = {
        vm.reportProgress(session.player.currentPosition, session.snapshot.value.durationMs)
        onBack()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.reportProgress(session.player.currentPosition, session.snapshot.value.durationMs)
        if (!inPip) session.player.pause()
    }
    DisposableEffect(Unit) {
        onDispose { vm.reportProgress(session.player.currentPosition, session.snapshot.value.durationMs) }
    }
    ImmersiveMode(isTv)
    DisposableEffect(isTv) {
        PipState.active = !isTv
        onDispose { PipState.active = false }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(VisiooColors.Black)
            .focusRequester(rootFocus)
            .focusTarget()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch()
                val code = e.key.nativeKeyCode
                val playPause = code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                when {
                    code == AndroidKeyEvent.KEYCODE_BACK || code == AndroidKeyEvent.KEYCODE_ESCAPE -> {
                        when {
                            panel != null -> panel = null
                            controlsVisible -> controlsVisible = false
                            else -> exit()
                        }
                        true
                    }
                    playPause -> { session.togglePlay(); showControls(); true }
                    code == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { session.seekBy(SEEK_MS); showControls(); true }
                    code == AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { session.seekBy(-SEEK_MS); showControls(); true }
                    // With the controls hidden the remote drives playback directly.
                    !controlsVisible && (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER) -> { session.togglePlay(); showControls(); true }
                    !controlsVisible && code == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { session.seekBy(-SEEK_MS); showControls(); true }
                    !controlsVisible && code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { session.seekBy(SEEK_MS); showControls(); true }
                    !controlsVisible && (code == AndroidKeyEvent.KEYCODE_DPAD_UP || code == AndroidKeyEvent.KEYCODE_DPAD_DOWN) -> { showControls(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    player = session.player
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (controlsVisible) controlsVisible = false else showControls()
                },
        )

        if (snapshot.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = VisiooColors.VioletBright)

        if (controlsVisible && !inPip) {
            Controls(state, snapshot, session, isTv, panelOpen = panel != null, onOpenPanel = { panel = it; touch() }, onBack = exit, onNext = vm::playNextNow, onTouch = ::touch)
        }

        panel?.let { p ->
            SettingsPanel(p, snapshot, session, onClose = { panel = null; showControls() }, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun Controls(
    state: PlayerUiState.Ready,
    snapshot: PlayerSnapshot,
    session: PlayerSession,
    isTv: Boolean,
    panelOpen: Boolean,
    onOpenPanel: (Panel) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onTouch: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTv) runCatching { firstFocus.requestFocus() } }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = snapshot.durationMs.coerceAtLeast(1)
    val shown = dragging ?: (snapshot.positionMs.toFloat() / duration)

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(VisiooColors.Black.copy(alpha = 0.7f), androidx.compose.ui.graphics.Color.Transparent, VisiooColors.Black.copy(alpha = 0.85f))))) {
        Row(Modifier.align(Alignment.TopStart).padding(VisiooSpacing.Md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
            VisiooButton(stringResource(R.string.action_back), onBack, primary = false)
            state.title?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = VisiooColors.TextPrimary) }
        }

        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Xl), verticalAlignment = Alignment.CenterVertically) {
            RoundControl("−10", stringResource(R.string.player_back_10), 56.dp) { session.seekBy(-SEEK_MS); onTouch() }
            RoundControl(if (snapshot.isPlaying) "❚❚" else "▶", stringResource(if (snapshot.isPlaying) R.string.player_pause else R.string.player_play), 80.dp, Modifier.focusRequester(firstFocus)) { session.togglePlay(); onTouch() }
            RoundControl("+10", stringResource(R.string.player_forward_10), 56.dp) { session.seekBy(SEEK_MS); onTouch() }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = VisiooSpacing.Lg, vertical = VisiooSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime((shown * duration).toLong()), color = VisiooColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = shown.coerceIn(0f, 1f),
                    onValueChange = { dragging = it; onTouch() },
                    onValueChangeFinished = { dragging?.let { session.seekTo((it * duration).toLong()) }; dragging = null },
                    modifier = Modifier.weight(1f).padding(horizontal = VisiooSpacing.Sm),
                    colors = SliderDefaults.colors(thumbColor = VisiooColors.VioletBright, activeTrackColor = VisiooColors.Violet, inactiveTrackColor = VisiooColors.TextMuted),
                )
                Text(formatTime(snapshot.durationMs), color = VisiooColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.padding(top = VisiooSpacing.Sm), horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
                if (snapshot.audios.isNotEmpty()) VisiooButton(stringResource(R.string.player_audio), { onOpenPanel(Panel.Audio) }, primary = false)
                if (snapshot.subtitles.isNotEmpty()) VisiooButton(stringResource(R.string.player_subtitles), { onOpenPanel(Panel.Subtitles) }, primary = false)
                if (snapshot.qualities.isNotEmpty()) VisiooButton(stringResource(R.string.player_quality), { onOpenPanel(Panel.Quality) }, primary = false)
                VisiooButton(stringResource(R.string.player_speed) + " ${speedLabel(snapshot.speed)}", { onOpenPanel(Panel.Speed) }, primary = false)
                if (state.hasNextEpisode) VisiooButton(stringResource(R.string.player_next_episode), onNext, primary = false)
            }
        }
    }
}

@Composable
private fun RoundControl(glyph: String, description: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(size)
            .visiooFocus(CircleShape)
            .clip(CircleShape)
            .background(VisiooColors.Surface.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = VisiooColors.TextPrimary, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun SettingsPanel(panel: Panel, snapshot: PlayerSnapshot, session: PlayerSession, onClose: () -> Unit, modifier: Modifier) {
    val auto = stringResource(R.string.player_auto)
    val off = stringResource(R.string.player_off)
    val (title, options) = when (panel) {
        Panel.Audio -> stringResource(R.string.player_audio) to snapshot.audios
        Panel.Subtitles -> stringResource(R.string.player_subtitles) to snapshot.subtitles.map { if (it.id == PlayerSession.SUBTITLES_OFF) it.copy(label = off) else it }
        Panel.Quality -> stringResource(R.string.player_quality) to snapshot.qualities.map { if (it.id == PlayerSession.AUTO) it.copy(label = auto) else it }
        Panel.Speed -> stringResource(R.string.player_speed) to SPEEDS.map { TrackOption(it.toString(), speedLabel(it), it == snapshot.speed) }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier.widthIn(max = 320.dp).fillMaxSize().padding(VisiooSpacing.Md).clip(VisiooShapes.Card).background(VisiooColors.Surface).padding(VisiooSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = VisiooColors.TextPrimary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
            items(options, key = { it.id }) { option ->
                val selectedFirst = option.selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (selectedFirst) Modifier.focusRequester(firstFocus) else Modifier)
                        .visiooFocus(VisiooShapes.Button)
                        .clip(VisiooShapes.Button)
                        .background(if (option.selected) VisiooColors.Violet else VisiooColors.SurfaceElevated)
                        .clickable(role = Role.RadioButton) {
                            when (panel) {
                                Panel.Audio -> session.selectAudio(option.id)
                                Panel.Subtitles -> session.selectSubtitle(option.id)
                                Panel.Quality -> session.selectQuality(option.id.toIntOrNull())
                                Panel.Speed -> session.setSpeed(option.id.toFloat())
                            }
                            onClose()
                        }
                        .padding(VisiooSpacing.Md),
                ) { Text(option.label, color = VisiooColors.TextPrimary, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

private fun speedLabel(speed: Float) = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/** Full-screen playback: landscape on phones, system bars hidden. Restores everything on exit. */
@Composable
private fun ImmersiveMode(isTv: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(isTv) {
        val previous = activity.requestedOrientation
        if (!isTv) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previous
        }
    }
}
