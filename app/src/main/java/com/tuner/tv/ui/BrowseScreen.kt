package com.tuner.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tuner.tv.MainViewModel
import com.tuner.tv.UiState
import com.tuner.tv.data.Channel
import com.tuner.tv.player.PlayerController
import com.tuner.tv.rec.ActiveRecording
import com.tuner.tv.rec.RecordingFile
import com.tuner.tv.rec.Schedule
import kotlinx.coroutines.delay

private sealed interface RailRow {
    val key: String
    data class G(val name: String, val count: Int) : RailRow { override val key = "g:$name" }
    data object Guide : RailRow { override val key = "guide" }
    data object Recs : RailRow { override val key = "recordings" }
    data object Settings : RailRow { override val key = "settings" }
}

private sealed interface RecItem {
    data class Active(val r: ActiveRecording) : RecItem
    data class Sched(val s: Schedule) : RecItem
    data class File(val f: RecordingFile) : RecItem
}

@Composable
fun BrowseScreen(
    vm: MainViewModel,
    controller: PlayerController,
    state: UiState.Ready,
    railKey: String,
    onRailKey: (String) -> Unit,
    focusChannelId: Int?,
    active: Map<String, ActiveRecording>,
    onWatch: (List<Channel>, Int) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayRecording: (RecordingFile) -> Unit,
    onPreviewRect: (IntRect) -> Unit,
    onToast: (String) -> Unit,
) {
    val now by rememberNow()
    val rows: List<RailRow> = remember(state) {
        state.groups.map { RailRow.G(it.name, it.channels.size) } + listOf(RailRow.Guide, RailRow.Recs, RailRow.Settings)
    }
    val showingRecordings = railKey == RailRow.Recs.key
    val group = state.groups.firstOrNull { "g:" + it.name == railKey } ?: state.groups.first()
    val channels = group.channels

    val recItems: List<RecItem> = remember(active, vm.schedules, vm.recordingFiles) {
        active.values.map { RecItem.Active(it) } + vm.schedules.map { RecItem.Sched(it) } + vm.recordingFiles.map { RecItem.File(it) }
    }

    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var focusedRec by remember { mutableStateOf<RecItem?>(null) }
    var sheetFor by remember { mutableStateOf<RecItem?>(null) }

    // Live preview: tune the focused channel after a short pause.
    LaunchedEffect(focusedChannel) {
        val c = focusedChannel ?: return@LaunchedEffect
        delay(650)
        if (controller.channel?.id != c.id) controller.tune(c, mute = true)
    }
    LaunchedEffect(showingRecordings) { if (showingRecordings) vm.refreshRecordings() }

    // Focus on arrival: the last-watched channel, else the selected rail row.
    val restoreIndex = remember(channels, focusChannelId) { channels.indexOfFirst { it.id == focusChannelId } }
    val channelFocus = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    val railIndex = rows.indexOfFirst { it.key == railKey }.coerceAtLeast(0)
    val railState = remember { LazyListState((railIndex - 4).coerceAtLeast(0)) }
    val listState = remember(railKey) { LazyListState((if (showingRecordings) 0 else restoreIndex - 3).coerceAtLeast(0)) }
    LaunchedEffect(Unit) { focusSoon(if (!showingRecordings && restoreIndex >= 0) channelFocus else railFocus) }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        // ---------------- top bar
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.width(18.dp))
            vm.epgStatus?.let { Text(it, fontSize = 11.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
                ?: Spacer(Modifier.weight(1f))
            Text(fmtDate(now), fontSize = 12.sp, color = Muted)
            Spacer(Modifier.width(10.dp))
            Text(fmtTime(now), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // ---------------- rail
            Glass(Modifier.width(196.dp).fillMaxHeight()) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("BROWSE", fontSize = 10.sp, letterSpacing = 2.sp, color = Dim, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp))
                    LazyColumn(state = railState, contentPadding = PaddingValues(vertical = 4.dp)) {
                        itemsIndexed(rows, key = { _, r -> r.key }) { i, r ->
                            if (r == RailRow.Guide) {
                                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(GlassLine))
                            }
                            val selected = r.key == railKey && r !is RailRow.Settings && r !is RailRow.Guide
                            FocusRow(
                                onClick = {
                                    when (r) {
                                        is RailRow.G, RailRow.Recs -> onRailKey(r.key)
                                        RailRow.Guide -> onOpenGuide()
                                        RailRow.Settings -> onOpenSettings()
                                    }
                                },
                                selected = selected,
                                height = 38.dp,
                                radius = 11.dp,
                                zoom = 1.03f,
                                onFocus = { if (r is RailRow.G || r == RailRow.Recs) onRailKey(r.key) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .then(if (i == railIndex) Modifier.focusRequester(railFocus) else Modifier),
                            ) { focused ->
                                if (selected) Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(BrandGradient))
                                Spacer(Modifier.width(if (selected) 7.dp else 10.dp))
                                RailIcon(r, focused || selected)
                                Spacer(Modifier.width(10.dp))
                                val label = when (r) {
                                    is RailRow.G -> r.name
                                    RailRow.Guide -> "TV Guide"
                                    RailRow.Recs -> "Recordings"
                                    RailRow.Settings -> "Settings"
                                }
                                Text(label, fontSize = 13.5.sp, color = if (focused || selected) Ink else Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                val badge = when (r) {
                                    is RailRow.G -> "${r.count}"
                                    RailRow.Recs -> (active.size + vm.schedules.size + vm.recordingFiles.size).takeIf { it > 0 }?.toString()
                                    else -> null
                                }
                                if (badge != null) {
                                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(if (focused) 0x29FFFFFF else 0x12FFFFFF)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                                        Text(badge, fontSize = 10.sp, color = if (focused) Ink else Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------- list
            Glass(Modifier.width(318.dp).fillMaxHeight()) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)) {
                    Column(Modifier.padding(horizontal = 10.dp)) {
                        Text(if (showingRecordings) "Recordings" else group.name, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (showingRecordings) "${vm.recordingFiles.size} recorded · ${vm.schedules.size} scheduled · ${active.size} recording now"
                            else "${channels.size} channels",
                            fontSize = 11.5.sp, color = Muted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(42.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(BrandGradient))
                    }
                    Spacer(Modifier.height(6.dp))
                    if (showingRecordings) {
                        if (recItems.isEmpty()) {
                            Text(
                                vm.recordingsError ?: "No recordings yet.\nWhile watching, press ≡ (Menu) and choose Record now or Schedule.\nOr pick a programme in the TV Guide.",
                                fontSize = 13.sp, color = if (vm.recordingsError != null) ErrorRed else Muted, lineHeight = 19.sp,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        LazyColumn(state = listState, contentPadding = PaddingValues(4.dp)) {
                            itemsIndexed(recItems) { _, item ->
                                RecRow(item, now, state.playlist.channels, onFocus = { focusedRec = item; focusedChannel = null },
                                    onClick = {
                                        when (item) {
                                            is RecItem.File -> onPlayRecording(item.f)
                                            is RecItem.Active -> {
                                                val list = state.playlist.channels
                                                val i = list.indexOfFirst { it.id == item.r.channelId }
                                                if (i >= 0) onWatch(list, i) else onToast("That channel isn't in the playlist any more")
                                            }
                                            is RecItem.Sched -> sheetFor = item
                                        }
                                    },
                                    onMenu = { sheetFor = item })
                            }
                        }
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(4.dp)) {
                            itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                                val recording = active.values.any { it.channelId == c.id }
                                val nowTitle = remember(c.id, vm.epgVersion, now / 60_000) { vm.nowNext(c).first?.title }
                                FocusRow(
                                    onClick = { onWatch(channels, i) },
                                    onFocus = { focusedChannel = c; focusedRec = null },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        .then(if (i == restoreIndex) Modifier.focusRequester(channelFocus) else Modifier),
                                    height = 50.dp,
                                ) { focused ->
                                    Text("${c.number}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (focused) Teal else Dim, modifier = Modifier.width(30.dp))
                                    ChannelTile(c)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (nowTitle != null) Text(nowTitle, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (recording) Box(Modifier.size(8.dp).clip(CircleShape).background(RecRed))
                                    else if (focused) Pill("LIVE", Color(0x29FF4D5E), Color(0xFFFF8792), pulse = false)
                                }
                            }
                        }
                    }
                }
            }

            // ---------------- preview + info
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                        .background(Brush.radialGradient(listOf(Teal.copy(alpha = 0.18f), Color(0xFF0B0F16))))
                        .onGloballyPositioned { onPreviewRect(it.boundsInRoot().roundToIntRect()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Live preview", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCFD8E4))
                        Text("Pick a channel to preview it here", fontSize = 12.sp, color = Muted)
                    }
                }
                Glass(Modifier.fillMaxWidth().weight(1f)) {
                    InfoPanel(vm, focusedChannel ?: controller.channel, focusedRec, active, now)
                }
            }
        }
    }

    sheetFor?.let { item ->
        when (item) {
            is RecItem.Sched -> OptionsSheet(
                "Scheduled", item.s.title ?: item.s.channelName,
                listOf(
                    SheetOption("Cancel this recording", "${fmtTime(item.s.start)} – ${fmtTime(item.s.end)}", danger = true) {
                        vm.cancelSchedule(item.s.id); onToast("Scheduled recording cancelled"); sheetFor = null
                    },
                    SheetOption("Keep it") { sheetFor = null },
                ),
                onDismiss = { sheetFor = null },
            )
            is RecItem.File -> OptionsSheet(
                "Recording", item.f.title,
                listOf(
                    SheetOption("Play") { sheetFor = null; onPlayRecording(item.f) },
                    SheetOption("Delete", fmtSize(item.f.size), danger = true) {
                        vm.deleteRecording(item.f); onToast("Deleted"); sheetFor = null
                    },
                    SheetOption("Close") { sheetFor = null },
                ),
                onDismiss = { sheetFor = null },
            )
            is RecItem.Active -> OptionsSheet(
                "Recording now", item.r.channelName,
                listOf(
                    SheetOption("Stop recording", fmtDuration(now - item.r.startedAt), danger = true) {
                        vm.stopRecording(item.r.id); onToast("Stopping recording…"); sheetFor = null
                    },
                    SheetOption("Close") { sheetFor = null },
                ),
                onDismiss = { sheetFor = null },
            )
        }
    }
}

@Composable
private fun RailIcon(r: RailRow, lit: Boolean) {
    val c = if (lit) Ink else Muted
    val glyph = when (r) {
        is RailRow.G -> if (r.name == com.tuner.tv.ALL_CHANNELS) "▦" else "▭"
        RailRow.Guide -> "☰"
        RailRow.Recs -> "●"
        RailRow.Settings -> "⚙"
    }
    Text(glyph, fontSize = 13.sp, color = if (r == RailRow.Recs) RecRed else c, modifier = Modifier.width(16.dp))
}

@Composable
private fun RecRow(item: RecItem, now: Long, channels: List<Channel>, onFocus: () -> Unit, onClick: () -> Unit, onMenu: () -> Unit) {
    FocusRow(
        onClick = onClick,
        onFocus = onFocus,
        height = 54.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) { onMenu(); true } else false
        },
    ) {
        val (name, sub, subColor) = when (item) {
            is RecItem.Active -> Triple(item.r.channelName, "● Recording  ${fmtDuration(now - item.r.startedAt)} · ${fmtSize(item.r.bytes)}", Color(0xFFFF8792))
            is RecItem.Sched -> Triple(item.s.title ?: item.s.channelName, "⏱ ${fmtDate(item.s.start)} ${fmtTime(item.s.start)} – ${fmtTime(item.s.end)}", Teal)
            is RecItem.File -> Triple(item.f.title, "${fmtDate(item.f.modified)} · ${fmtSize(item.f.size)}", Muted)
        }
        val chName = when (item) {
            is RecItem.Active -> item.r.channelName
            is RecItem.Sched -> item.s.channelName
            is RecItem.File -> item.f.title
        }
        val ch = channels.firstOrNull { it.name == chName } ?: Channel(-1, 0, chName, "", null, "", null)
        ChannelTile(ch)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.5.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 11.sp, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoPanel(vm: MainViewModel, channel: Channel?, rec: RecItem?, active: Map<String, ActiveRecording>, now: Long) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        if (rec != null) {
            val (title, line) = when (rec) {
                is RecItem.File -> rec.f.title to "Saved ${fmtDate(rec.f.modified)} ${fmtTime(rec.f.modified)} · ${fmtSize(rec.f.size)}"
                is RecItem.Sched -> (rec.s.title ?: rec.s.channelName) to "${rec.s.channelName} · ${fmtDate(rec.s.start)} ${fmtTime(rec.s.start)} – ${fmtTime(rec.s.end)}"
                is RecItem.Active -> rec.r.channelName to "Recording for ${fmtDuration(now - rec.r.startedAt)} · ${fmtSize(rec.r.bytes)}"
            }
            Text("RECORDING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RecRed, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, fontSize = 12.5.sp, color = Muted)
            Text("Saved to ${vm.storageLabel}", fontSize = 12.sp, color = Dim, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KeyHint("OK", if (rec is RecItem.File) "Play" else if (rec is RecItem.Active) "Watch" else "Options")
                KeyHint("≡", "Options")
            }
            return@Column
        }
        if (channel == null) {
            Text("Move right into the list to preview a channel.", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { KeyHint("→", "Channels"); KeyHint("OK", "Select") }
            return@Column
        }
        val (cur, next) = remember(channel.id, vm.epgVersion, now / 60_000) { vm.nowNext(channel) }
        val recording = active.values.any { it.channelId == channel.id }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CH ${channel.number}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teal, letterSpacing = 1.sp)
            if (recording) Pill("REC", Color(0xFF3A0D14), Color(0xFFFF8792)) else Pill("LIVE")
        }
        Spacer(Modifier.height(4.dp))
        Text(channel.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(channel.group, fontSize = 12.5.sp, color = Muted, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        if (cur != null) {
            Text(cur.title, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${fmtTime(cur.start)} – ${fmtTime(cur.stop)}", fontSize = 11.5.sp, color = Muted)
            Spacer(Modifier.height(5.dp))
            ProgressBar(cur.progress(now), Modifier.fillMaxWidth())
            if (next != null) {
                Spacer(Modifier.height(6.dp))
                Text("Next  ${fmtTime(next.start)}  ${next.title}", fontSize = 11.5.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(if (vm.epgStatus != null) vm.epgStatus!! else "No guide information for this channel.", fontSize = 12.sp, color = Dim, maxLines = 2)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KeyHint("OK", "Watch")
            KeyHint("▲▼", "Browse")
            KeyHint("←", "Groups")
        }
    }
}
