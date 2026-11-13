@file:OptIn(UnstableApi::class)

package com.tuner.tv.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tuner.tv.data.Channel
import com.tuner.tv.data.USER_AGENT
import kotlinx.coroutines.delay

/**
 * Full-screen live player.
 *  Up / Down (or CH+ / CH-)  change channel within the current group
 *  Centre / Left / Right      show the channel banner
 *  Play/Pause                 pause / resume
 *  Back                       return to the channel list
 */
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    startIndex: Int,
    onChannelChanged: (Channel) -> Unit,
    onExit: (Channel) -> Unit,
) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(startIndex.coerceIn(channels.indices)) }
    val channel = channels[index]

    var overlayVisible by remember { mutableStateOf(true) }
    var overlayPing by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var firstLoad by remember { mutableStateOf(true) }

    val player = remember { createPlayer(context) }
    val focus = remember { FocusRequester() }

    fun showOverlay() {
        overlayVisible = true
        overlayPing++
    }

    // Player events: buffering state, errors, and a fallback to HLS for extension-less URLs.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                val config = player.currentMediaItem?.localConfiguration
                when {
                    e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                    }
                    config != null && config.mimeType != MimeTypes.APPLICATION_M3U8 -> {
                        // Many IPTV links are HLS without ".m3u8" in the URL - try that before giving up.
                        player.setMediaItem(mediaItemFor(config.uri, forceHls = true))
                        player.prepare()
                    }
                    else -> {
                        error = "Can't play this channel (${e.errorCodeName})"
                        showOverlay()
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Stop streaming when the app goes to the background; resume at the live edge when back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> if (player.mediaItemCount > 0) {
                    player.prepare()
                    player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tune. When zapping quickly, wait briefly so we don't open a stream for every channel passed.
    LaunchedEffect(index) {
        showOverlay()
        error = null
        if (!firstLoad) delay(350)
        firstLoad = false
        player.setMediaItem(mediaItemFor(Uri.parse(channel.url), forceHls = false))
        player.prepare()
        player.playWhenReady = true
        onChannelChanged(channel)
    }

    // Auto-hide the banner.
    LaunchedEffect(overlayPing) {
        delay(4_000)
        if (error == null) overlayVisible = false
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler { onExit(channel) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> {
                        index = if (index == 0) channels.lastIndex else index - 1; true
                    }
                    Key.DirectionDown, Key.ChannelDown -> {
                        index = if (index == channels.lastIndex) 0 else index + 1; true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft, Key.DirectionRight -> {
                        if (overlayVisible && e.key == Key.DirectionCenter) overlayVisible = false else showOverlay()
                        true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        player.playWhenReady = !player.playWhenReady
                        showOverlay(); true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
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

        if (buffering && error == null) {
            Text(
                "Tuning…",
                color = Muted,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        error?.let {
            Text(
                it,
                color = ErrorRed,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 800.dp),
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ChannelBanner(channel, paused = !player.playWhenReady)
        }
    }
}

@Composable
private fun ChannelBanner(channel: Channel, paused: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("${channel.number}", style = MaterialTheme.typography.displaySmall, color = Teal)
            ChannelLogo(channel)
            Column {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (paused) "${channel.group}  ·  Paused" else channel.group,
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun mediaItemFor(uri: Uri, forceHls: Boolean): MediaItem {
    val builder = MediaItem.Builder().setUri(uri)
    if (forceHls || uri.toString().contains(".m3u8", ignoreCase = true)) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return builder.build()
}

private fun createPlayer(context: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
    val dataSource = DefaultDataSource.Factory(context, http)

    // Start quickly (1.5 s buffered), but keep a healthy buffer once playing.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
        .build()

    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
        .setLoadControl(loadControl)
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
        .build()
}
