@file:OptIn(UnstableApi::class)

package com.tuner.tv.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tuner.tv.data.Channel
import com.tuner.tv.data.USER_AGENT
import com.tuner.tv.rec.NasConfig
import com.tuner.tv.rec.RecordingFile
import com.tuner.tv.rec.SmbDataSource

/**
 * One ExoPlayer shared by the preview pane and full screen, so pressing OK on a
 * previewing channel continues instantly instead of re-tuning.
 */
class PlayerController(private val context: Context) {

    val player: ExoPlayer = build()

    var channel by mutableStateOf<Channel?>(null)
        private set
    var recording by mutableStateOf<RecordingFile?>(null)
        private set
    var buffering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var muted by mutableStateOf(true)
        private set
    var playing by mutableStateOf(false)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(e: PlaybackException) {
                val config = player.currentMediaItem?.localConfiguration
                when {
                    e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                    }
                    recording == null && config != null && config.mimeType != MimeTypes.APPLICATION_M3U8 -> {
                        // Many IPTV links are HLS without ".m3u8" in the URL - try that before giving up.
                        player.setMediaItem(liveItem(config.uri.toString(), forceHls = true))
                        player.prepare()
                    }
                    else -> error = "Can't play this ${if (recording != null) "recording" else "channel"} (${e.errorCodeName})"
                }
            }
        })
    }

    fun tune(c: Channel, mute: Boolean) {
        recording = null
        channel = c
        error = null
        setMuted(mute)
        player.setMediaItem(liveItem(c.url, forceHls = false))
        player.prepare()
        player.playWhenReady = true
    }

    fun playRecording(file: RecordingFile, nas: NasConfig?) {
        channel = null
        recording = file
        error = null
        setMuted(false)
        val item = MediaItem.fromUri(file.uri)
        if (file.uri.scheme == "smb" && nas != null) {
            player.setMediaSource(ProgressiveMediaSource.Factory(SmbDataSource.Factory(nas)).createMediaSource(item))
        } else {
            player.setMediaItem(item)
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun setMuted(m: Boolean) {
        muted = m
        player.volume = if (m) 0f else 1f
    }

    fun togglePause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekBy(ms: Long) {
        val d = player.duration
        val target = (player.currentPosition + ms).coerceAtLeast(0)
        player.seekTo(if (d > 0) target.coerceAtMost(d - 1_000) else target)
    }

    /** Stops streaming but remembers what was on, so [resume] can pick it up at the live edge. */
    fun pauseForBackground() = player.stop()

    fun resume() {
        if (player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        channel = null
        recording = null
        error = null
    }

    fun release() = player.release()

    private fun liveItem(url: String, forceHls: Boolean): MediaItem {
        val b = MediaItem.Builder().setUri(Uri.parse(url))
        if (forceHls || url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }

    private fun build(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)   // start fast, then buffer well
            .build()
        return ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
    }
}
