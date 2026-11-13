@file:OptIn(UnstableApi::class)

package com.tuner.tv.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.tuner.tv.ALL_CHANNELS
import com.tuner.tv.MainViewModel
import com.tuner.tv.UiState
import com.tuner.tv.data.Channel
import com.tuner.tv.player.PlayerController
import com.tuner.tv.rec.RecordingFile
import com.tuner.tv.rec.Recordings
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Browse : Screen
    data object Guide : Screen
    data object Settings : Screen
    data class Live(val channels: List<Channel>, val index: Int) : Screen
    data class Playback(val file: RecordingFile) : Screen
}

@Composable
fun TunerApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    // Stop streaming when the app is in the background; resume at the live edge.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) controller.pauseForBackground()
            if (e == Lifecycle.Event.ON_START) controller.resume()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Browse) }
    var railKey by rememberSaveable { mutableStateOf("g:$ALL_CHANNELS") }
    var focusChannelId by remember { mutableStateOf(vm.settings.lastChannelId.takeIf { it >= 0 }) }
    var previewRect by remember { mutableStateOf<IntRect?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val active by Recordings.active.collectAsState()
    val recMsg by Recordings.messages.collectAsState()

    LaunchedEffect(recMsg) {
        val m = recMsg ?: return@LaunchedEffect
        toast = m
        Recordings.consumeMessage()
        vm.refreshRecordings()
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3_500)
            toast = null
        }
    }

    fun watch(list: List<Channel>, index: Int) {
        val c = list[index]
        if (controller.channel?.id != c.id) controller.tune(c, mute = false) else controller.mute(false)
        vm.settings.lastChannelId = c.id
        screen = Screen.Live(list, index)
    }

    val state = vm.state
    val full = screen is Screen.Live || screen is Screen.Playback

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val density = LocalDensity.current
        val fullRect = with(density) { IntRect(0, 0, maxWidth.roundToPx(), maxHeight.roundToPx()) }

        if (!full) AuroraBackground()

        CompositionLocalProvider(LocalContentColor provides Ink) {
            when {
                state is UiState.Loading -> CenterMessage("Loading your channels…", spinner = true)

                state is UiState.NeedsSetup || screen == Screen.Settings -> SettingsScreen(
                    vm = vm,
                    canGoBack = state is UiState.Ready,
                    onBack = { screen = Screen.Browse },
                    onSaved = { screen = Screen.Browse },
                    onToast = { toast = it },
                )

                state is UiState.Error -> ErrorScreen(
                    message = state.message,
                    onRetry = { vm.load(forceRefresh = true) },
                    onSettings = { screen = Screen.Settings },
                )

                state is UiState.Ready -> when (screen) {
                    Screen.Guide -> GuideScreen(
                        vm = vm,
                        channels = state.playlist.channels,
                        startChannelId = focusChannelId,
                        active = active,
                        onWatch = { c ->
                            val list = state.playlist.channels
                            watch(list, list.indexOf(c).coerceAtLeast(0))
                        },
                        onBack = { screen = Screen.Browse },
                        onToast = { toast = it },
                    )
                    is Screen.Live, is Screen.Playback -> Unit
                    else -> BrowseScreen(
                        vm = vm,
                        controller = controller,
                        state = state,
                        railKey = railKey,
                        onRailKey = { railKey = it },
                        focusChannelId = focusChannelId,
                        active = active,
                        onWatch = ::watch,
                        onOpenGuide = {
                            controller.stop()
                            screen = Screen.Guide
                        },
                        onOpenSettings = {
                            controller.stop()
                            screen = Screen.Settings
                        },
                        onPlayRecording = { f ->
                            controller.playRecording(f, vm.nas)
                            screen = Screen.Playback(f)
                        },
                        onPreviewRect = { previewRect = it },
                        onToast = { toast = it },
                    )
                }
            }
        }

        val showVideo = state is UiState.Ready &&
            (full || (screen == Screen.Browse && previewRect != null && controller.channel != null))
        VideoSurface(controller, showVideo, if (full) fullRect else previewRect ?: IntRect.Zero, full)

        CompositionLocalProvider(LocalContentColor provides Ink) {
            when (val s = screen) {
                is Screen.Live -> LiveOverlay(
                    vm = vm,
                    controller = controller,
                    channels = s.channels,
                    startIndex = s.index,
                    active = active,
                    onExit = { c ->
                        focusChannelId = c.id
                        controller.mute(true)
                        screen = Screen.Browse
                    },
                    onToast = { toast = it },
                )
                is Screen.Playback -> PlaybackOverlay(
                    vm = vm,
                    controller = controller,
                    file = s.file,
                    onExit = {
                        controller.stop()
                        screen = Screen.Browse
                    },
                )
                else -> Unit
            }
        }

        Box(Modifier.align(Alignment.TopCenter)) { Toast(toast) }
    }
}

/** The single video view. It glides between the preview pane and full screen. */
@Composable
private fun VideoSurface(controller: PlayerController, visible: Boolean, target: IntRect, full: Boolean) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) settled = false
        else {
            withFrameNanos { }
            withFrameNanos { }
            settled = true
        }
    }
    val offsetSpec: AnimationSpec<IntOffset> = if (settled) tween(450, easing = FastOutSlowInEasing) else snap()
    val sizeSpec: AnimationSpec<IntSize> = if (settled) tween(450, easing = FastOutSlowInEasing) else snap()
    val offset by animateIntOffsetAsState(target.topLeft, offsetSpec, label = "videoOffset")
    val size by animateIntSizeAsState(IntSize(target.width, target.height), sizeSpec, label = "videoSize")

    if (!visible || size.width <= 0 || size.height <= 0) return

    Box(
        Modifier
            .offset { offset }
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints.fixed(size.width, size.height))
                layout(size.width, size.height) { p.place(0, 0) }
            }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = controller.player
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        val err = controller.error
        if (err != null) {
            Text(
                err,
                color = ErrorRed,
                textAlign = TextAlign.Center,
                fontSize = if (full) 20.sp else 12.sp,
                modifier = Modifier.widthIn(max = if (full) 760.dp else 360.dp).padding(16.dp),
            )
        } else if (controller.buffering) {
            Spinner(if (full) 56.dp else 36.dp)
        }
    }
}

@Composable
fun CenterMessage(text: String, spinner: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (spinner) Spinner(48.dp)
            Text(text, color = Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon(focus) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Glass(Modifier.widthIn(max = 720.dp)) {
            Column(
                Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Wordmark()
                Text("Something went wrong", fontSize = 22.sp, color = Ink)
                Text(message, color = Muted, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrimaryButton("Try again", onRetry, Modifier.focusRequester(focus))
                    PrimaryButton("Settings", onSettings, primary = false)
                }
            }
        }
    }
}
