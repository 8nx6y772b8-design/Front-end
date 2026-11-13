package com.tuner.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.tuner.tv.MainViewModel
import com.tuner.tv.data.Channel
import com.tuner.tv.player.PlayerController
import com.tuner.tv.rec.ActiveRecording
import com.tuner.tv.rec.RecordingFile
import kotlinx.coroutines.delay

private val BannerFill = Brush.linearGradient(listOf(Color(0xD90E121A), Color(0xD0141022)))

// ======================================================================= live TV

@Composable
fun LiveOverlay(
    vm: MainViewModel,
    controller: PlayerController,
    channels: List<Channel>,
    startIndex: Int,
    active: Map<String, ActiveRecording>,
    onExit: (Channel) -> Unit,
    onToast: (String) -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(channels.indices)) }
    var zapped by remember { mutableStateOf(false) }
    val channel = channels[index]
    var bannerVisible by remember { mutableStateOf(true) }
    var bannerPing by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val now by rememberNow(1_000)

    fun showBanner() {
        bannerVisible = true
        bannerPing++
    }

    // Zapping: tune after a short pause so we don't open a stream for every channel passed.
    LaunchedEffect(index) {
        showBanner()
        if (zapped) {
            delay(350)
            controller.tune(channels[index], mute = false)
            vm.settings.lastChannelId = channels[index].id
        }
    }
    LaunchedEffect(bannerPing, menu) {
        delay(4_500)
        if (menu == null && controller.error == null) bannerVisible = false
    }
    LaunchedEffect(menu) { if (menu == null) focusSoon(focus) }

    BackHandler(enabled = menu == null) { onExit(channel) }

    val nowNext = remember(channel.id, vm.epgVersion, now / 60_000) { vm.nowNext(channel) }
    val rec = active.values.firstOrNull { it.channelId == channel.id }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || menu != null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> {
                        zapped = true
                        index = if (index == 0) channels.lastIndex else index - 1
                        true
                    }
                    Key.DirectionDown, Key.ChannelDown -> {
                        zapped = true
                        index = if (index == channels.lastIndex) 0 else index + 1
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (bannerVisible) bannerVisible = false else showBanner()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        showBanner(); true
                    }
                    Key.Menu -> {
                        menu = "main"; showBanner(); true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        controller.togglePause(); showBanner(); true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        // REC chip
        if (rec != null) {
            Chip(Modifier.align(Alignment.TopStart).padding(22.dp)) {
                PulseDot(RecRed, 9.dp)
                Spacer(Modifier.width(8.dp))
                Text("REC  ${fmtDuration(now - rec.startedAt)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }
        AnimatedVisibility(bannerVisible, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Chip(Modifier.padding(22.dp)) { Text(fmtTime(now), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        }

        AnimatedVisibility(
            bannerVisible,
            Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            val (cur, next) = nowNext
            Row(
                Modifier.padding(22.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BannerFill)
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                GradientText("${channel.number}", 40.sp, FontWeight.ExtraBold, Modifier.widthIn(min = 60.dp))
                ChannelTile(channel, 72.dp, 48.dp, 16.sp)
                Column(Modifier.weight(1f)) {
                    Text(channel.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("LIVE")
                        if (rec != null) Pill("REC", Color(0xFF3A0D14), Color(0xFFFF8792))
                        val extra = buildString {
                            append(channel.group)
                            if (!controller.playing && controller.error == null && !controller.buffering) append("  ·  Paused")
                        }
                        Text(extra, fontSize = 13.sp, color = Muted, maxLines = 1)
                    }
                    if (cur != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Now  ", fontSize = 12.sp, color = Teal, fontWeight = FontWeight.SemiBold)
                            Text(cur.title, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text("   ${fmtTime(cur.start)} – ${fmtTime(cur.stop)}", fontSize = 12.sp, color = Muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(cur.progress(now), Modifier.fillMaxWidth())
                        if (next != null) {
                            Spacer(Modifier.height(6.dp))
                            Text("Next  ${fmtTime(next.start)}  ${next.title}", fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(1f, Modifier.fillMaxWidth())
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyHint("▲▼", "Channels")
                    KeyHint("≡", "Menu · record")
                }
            }
        }
    }

    // ----- menus
    when (menu) {
        "main" -> {
            val cur = nowNext.first
            val options = buildList {
                if (rec != null) {
                    add(SheetOption("Stop recording", fmtDuration(now - rec.startedAt), danger = true) {
                        vm.stopRecording(rec.id); onToast("Stopping recording…"); menu = null
                    })
                } else {
                    add(SheetOption("Record now", "until you stop", danger = true) {
                        vm.recordNow(channel); onToast("● Recording ${channel.name} to ${vm.storageLabel}"); menu = null
                    })
                    if (cur != null) {
                        add(SheetOption("Record this programme", "until ${fmtTime(cur.stop)}", danger = true) {
                            vm.recordUntil(channel, cur.stop + 3 * 60_000, cur.title)
                            onToast("● Recording “${cur.title}”"); menu = null
                        })
                    }
                }
                add(SheetOption("Schedule recording…") { menu = "schedule" })
                add(SheetOption("Close") { menu = null })
            }
            OptionsSheet(
                "Options", channel.name, options,
                note = "Recordings save to ${vm.storageLabel}. Change this in Settings.",
                onDismiss = { menu = null },
            )
        }
        "schedule" -> ScheduleSheet(
            channel = channel,
            onSave = { start, end ->
                vm.schedule(channel, start, end, null)
                onToast("⏱ Scheduled ${channel.name} at ${fmtTime(start)}")
                menu = null
            },
            onDismiss = { menu = null },
        )
    }
}

@Composable
private fun Chip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x99090C12)).border(1.dp, GlassLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private val LENGTHS = listOf(30, 60, 90, 120, 180, 240)

private fun roundUp15(t: Long): Long {
    val q = 15 * 60_000L
    return ((t + 60_000) / q + 1) * q
}

/** Pick a start time and length with ◀ ▶. */
@Composable
fun ScheduleSheet(channel: Channel, onSave: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    var start by remember { mutableLongStateOf(roundUp15(System.currentTimeMillis())) }
    var li by remember { mutableIntStateOf(1) }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.width(340.dp).fillMaxHeight().background(SheetFill).padding(horizontal = 22.dp, vertical = 26.dp)) {
                Text("SCHEDULE RECORDING", fontSize = 12.sp, letterSpacing = 2.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(channel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Adjuster("Start", "${fmtDate(start)}  ${fmtTime(start)}", Modifier.focusRequester(first)) { d ->
                    start = (start + d * 15 * 60_000L).coerceAtLeast(roundUp15(System.currentTimeMillis()) - 15 * 60_000L)
                }
                Adjuster("Length", LENGTHS[li].let { if (it >= 60) "${it / 60.0} h".replace(".0 ", " ") else "$it min" }) { d ->
                    li = (li + d).coerceIn(LENGTHS.indices)
                }
                FocusRow(onClick = { onSave(start, start + LENGTHS[li] * 60_000L) }, height = 46.dp, zoom = 1.02f,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(RecRed))
                    Spacer(Modifier.width(12.dp))
                    Text("Save schedule", fontSize = 14.sp, color = Ink)
                }
                FocusRow(onClick = onDismiss, height = 46.dp, zoom = 1.02f, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Spacer(Modifier.width(21.dp))
                    Text("Cancel", fontSize = 14.sp, color = Ink)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Use ◀ ▶ to change the time and length. Leave the Fire Stick plugged in (asleep is fine).",
                    fontSize = 11.5.sp, color = Dim, lineHeight = 16.sp,
                )
                LaunchedEffect(Unit) { focusSoon(first) }
            }
        }
    }
}

@Composable
private fun Adjuster(label: String, value: String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    FocusRow(
        onClick = { onChange(1) }, height = 46.dp, zoom = 1.02f,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onChange(-1); true }
                Key.DirectionRight -> { onChange(1); true }
                else -> false
            }
        },
    ) {
        Text(label, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
        Text("◀  $value  ▶", fontSize = 13.sp, color = Teal)
    }
}

// ======================================================================= recordings

@Composable
fun PlaybackOverlay(vm: MainViewModel, controller: PlayerController, file: RecordingFile, onExit: () -> Unit) {
    var bannerVisible by remember { mutableStateOf(true) }
    var ping by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val now by rememberNow(500)

    fun show() {
        bannerVisible = true; ping++
    }
    LaunchedEffect(ping) {
        delay(4_000)
        if (!menu) bannerVisible = false
    }
    LaunchedEffect(menu) { if (!menu) focusSoon(focus) }
    BackHandler(enabled = !menu) { onExit() }

    val pos = remember(now) { controller.player.currentPosition }
    val dur = remember(now) { controller.player.duration.takeIf { it > 0 } ?: 0L }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || menu) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft, Key.MediaRewind -> { controller.seekBy(-30_000); show(); true }
                Key.DirectionRight, Key.MediaFastForward -> { controller.seekBy(30_000); show(); true }
                Key.DirectionUp -> { controller.seekBy(5 * 60_000); show(); true }
                Key.DirectionDown -> { controller.seekBy(-5 * 60_000); show(); true }
                Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                    controller.togglePause(); show(); true
                }
                Key.Menu -> { menu = true; true }
                else -> false
            }
        }.focusRequester(focus).focusable(),
    ) {
        AnimatedVisibility(bannerVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.padding(22.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BannerFill)
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (controller.playing) "" else "Paused", fontSize = 13.sp, color = Muted)
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(if (dur > 0) pos.toFloat() / dur else 0f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmtDuration(pos)} / ${fmtDuration(dur)}", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    KeyHint("◀ ▶", "30 s")
                    Spacer(Modifier.width(14.dp))
                    KeyHint("▲ ▼", "5 min")
                    Spacer(Modifier.width(14.dp))
                    KeyHint("OK", "Pause")
                }
            }
        }
    }
    if (menu) {
        OptionsSheet(
            "Recording", file.title,
            listOf(
                SheetOption("Delete recording", fmtSize(file.size), danger = true) {
                    vm.deleteRecording(file); menu = false; onExit()
                },
                SheetOption("Close") { menu = false },
            ),
            onDismiss = { menu = false },
        )
    }
}
