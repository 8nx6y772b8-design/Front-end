package com.tuner.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tuner.tv.MainViewModel
import com.tuner.tv.data.Channel
import com.tuner.tv.epg.Programme
import com.tuner.tv.rec.ActiveRecording

private val CH_COL = 210.dp
private val HOUR_W = 300.dp
private const val SLOT = 30 * 60_000L
private const val ROW_H = 50

private fun floorSlot(t: Long) = t - t % SLOT

/**
 * TiviMate-style timeline. Focus is handled by hand (one focusable box), which keeps
 * the D-pad predictable across programmes of different lengths.
 */
@Composable
fun GuideScreen(
    vm: MainViewModel,
    channels: List<Channel>,
    startChannelId: Int?,
    active: Map<String, ActiveRecording>,
    onWatch: (Channel) -> Unit,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val now by rememberNow(30_000)
    var row by remember { mutableIntStateOf(channels.indexOfFirst { it.id == startChannelId }.coerceAtLeast(0)) }
    var focusTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var windowStart by remember { mutableLongStateOf(floorSlot(System.currentTimeMillis())) }
    val cache = remember(vm.epgVersion) { mutableStateMapOf<Int, List<Programme>>() }
    var sheet by remember { mutableStateOf<Pair<Channel, Programme?>?>(null) }
    val listState = rememberLazyListState((row - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }

    LaunchedEffect(sheet) { if (sheet == null) focusSoon(focus) }
    BackHandler(enabled = sheet == null) { onBack() }

    LaunchedEffect(row) {
        val first = listState.firstVisibleItemIndex
        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(6)
        if (row < first + 1 || row > first + visible - 2) listState.animateScrollToItem((row - 2).coerceAtLeast(0))
    }

    fun progs(r: Int) = cache[channels[r].id].orEmpty()
    fun focusedProg(): Programme? = progs(row).firstOrNull { it.isOnAt(focusTime) }
    fun isScheduled(c: Channel, p: Programme) = vm.schedules.any {
        it.channelId == c.id && it.start <= p.start && it.end >= p.stop - 60_000 && it.start >= p.start - 15 * 60_000
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        // header
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.width(18.dp))
            Text("TV Guide", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.width(12.dp))
            Text(fmtDate(focusTime), fontSize = 13.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(now), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val gridW: Dp = maxWidth - CH_COL
            val spanMs = (gridW.value / HOUR_W.value * 3_600_000f).toLong()
            val dpPerMs = gridW.value / spanMs
            val minTime = floorSlot(System.currentTimeMillis()) - 4 * SLOT
            val maxTime = System.currentTimeMillis() + 4 * 86_400_000L

            Box(
                Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || sheet != null) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> { if (row > 0) row--; true }
                        Key.DirectionDown -> { if (row < channels.lastIndex) row++; true }
                        Key.DirectionRight -> {
                            val p = focusedProg()
                            val nxt = progs(row).firstOrNull { it.start >= (p?.stop ?: (focusTime + 1)) }
                            focusTime = (nxt?.start ?: ((p?.stop ?: focusTime) + if (p == null) SLOT else 0)).coerceAtMost(maxTime)
                            while (focusTime >= windowStart + spanMs - SLOT / 2) windowStart += SLOT
                            true
                        }
                        Key.DirectionLeft -> {
                            val p = focusedProg()
                            val prev = progs(row).lastOrNull { it.stop <= (p?.start ?: focusTime) }
                            focusTime = (prev?.start ?: ((p?.start ?: focusTime) - SLOT)).coerceAtLeast(minTime)
                            if (prev != null && prev.stop > windowStart && prev.start < windowStart) focusTime = maxOf(prev.start, minTime)
                            while (focusTime < windowStart && windowStart > minTime) windowStart -= SLOT
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            val c = channels[row]
                            val p = focusedProg()
                            val nowMs = System.currentTimeMillis()
                            if (p == null || p.start <= nowMs) onWatch(c) else sheet = c to p
                            true
                        }
                        Key.Menu -> { sheet = channels[row] to focusedProg(); true }
                        else -> false
                    }
                }.focusRequester(focus).focusable(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    // time ruler
                    Box(Modifier.fillMaxWidth().height(24.dp).padding(start = CH_COL).clipToBounds()) {
                        var t = windowStart
                        while (t < windowStart + spanMs) {
                            Text(fmtTime(t), fontSize = 11.sp, color = Muted,
                                modifier = Modifier.offset(x = ((t - windowStart) * dpPerMs).dp + 6.dp))
                            t += SLOT
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(state = listState, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                                LaunchedEffect(c.id, vm.epgVersion) {
                                    if (cache[c.id] == null) {
                                        val n = System.currentTimeMillis()
                                        cache[c.id] = vm.programmes(c, n - 6 * 3_600_000L, n + 4 * 86_400_000L)
                                    }
                                }
                                GuideRow(
                                    channel = c,
                                    programmes = cache[c.id],
                                    windowStart = windowStart,
                                    spanMs = spanMs,
                                    dpPerMs = dpPerMs,
                                    focusedRow = i == row,
                                    focusTime = focusTime,
                                    now = now,
                                    recordingNow = active.values.any { it.channelId == c.id },
                                    isScheduled = { p -> isScheduled(c, p) },
                                )
                            }
                        }
                        // "now" line
                        if (now in windowStart until windowStart + spanMs) {
                            Box(
                                Modifier.offset(x = CH_COL + ((now - windowStart) * dpPerMs).dp).width(2.dp).fillMaxHeight()
                                    .background(Teal.copy(alpha = 0.85f))
                            )
                        }
                    }
                }
                if (cache.isEmpty() && !vm.epg.hasData()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Glass {
                            Text(
                                vm.epgStatus ?: "No TV guide yet. Add a guide link in Settings.",
                                fontSize = 15.sp, color = Muted, modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }
        }

        // details of the focused programme
        Spacer(Modifier.height(12.dp))
        Glass(Modifier.fillMaxWidth().height(92.dp)) {
            val c = channels[row]
            val p = focusedProg()
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelTile(c, 64.dp, 42.dp, 13.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p?.title ?: c.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val whenText = when {
                        p == null -> "${c.number}  ${c.name}  ·  No guide information"
                        p.isOnAt(now) -> "${c.name}  ·  ${fmtTime(p.start)} – ${fmtTime(p.stop)}  ·  On now"
                        p.start > now -> "${c.name}  ·  ${fmtDate(p.start)} ${fmtTime(p.start)} – ${fmtTime(p.stop)}" +
                            if (isScheduled(c, p)) "  ·  ● Recording scheduled" else ""
                        else -> "${c.name}  ·  ${fmtTime(p.start)} – ${fmtTime(p.stop)}  ·  Finished"
                    }
                    Text(whenText, fontSize = 12.sp, color = Muted, maxLines = 1)
                    p?.desc?.let { Text(it, fontSize = 11.5.sp, color = Dim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyHint("OK", if (p != null && p.start > now) "Record" else "Watch")
                    KeyHint("≡", "Options")
                }
            }
        }
    }

    sheet?.let { (c, p) ->
        val nowMs = System.currentTimeMillis()
        val options = buildList {
            if (p == null || p.start <= nowMs) add(SheetOption("Watch now") { sheet = null; onWatch(c) })
            if (p != null && p.isOnAt(nowMs)) {
                add(SheetOption("Record until it ends", fmtTime(p.stop), danger = true) {
                    vm.recordUntil(c, p.stop + 3 * 60_000, p.title)
                    onToast("● Recording “${p.title}”"); sheet = null
                })
            }
            if (p != null && p.start > nowMs) {
                val existing = vm.schedules.firstOrNull {
                    it.channelId == c.id && it.start <= p.start && it.end >= p.stop - 60_000 && it.start >= p.start - 15 * 60_000
                }
                if (existing != null) {
                    add(SheetOption("Cancel recording", danger = true) {
                        vm.cancelSchedule(existing.id); onToast("Recording cancelled"); sheet = null
                    })
                } else {
                    add(SheetOption("Record this programme", "${fmtTime(p.start)} – ${fmtTime(p.stop)}", danger = true) {
                        vm.schedule(c, p.start - 60_000, p.stop + 3 * 60_000, p.title)
                        onToast("⏱ Will record “${p.title}” at ${fmtTime(p.start)}"); sheet = null
                    })
                }
            }
            add(SheetOption("Close") { sheet = null })
        }
        OptionsSheet(
            title = c.name,
            subtitle = p?.title ?: "No guide information",
            options = options,
            note = "Recordings start 1 minute early and run 3 minutes over, in case the programme runs late. They save to ${vm.storageLabel}.",
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programmes: List<Programme>?,
    windowStart: Long,
    spanMs: Long,
    dpPerMs: Float,
    focusedRow: Boolean,
    focusTime: Long,
    now: Long,
    recordingNow: Boolean,
    isScheduled: (Programme) -> Boolean,
) {
    val winEnd = windowStart + spanMs
    Row(Modifier.fillMaxWidth().height(ROW_H.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(CH_COL).fillMaxHeight().padding(end = 6.dp, top = 3.dp, bottom = 3.dp).clip(RoundedCornerShape(10.dp))
                .background(if (focusedRow) Color(0x17FFFFFF) else Color.Transparent).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${channel.number}", fontSize = 11.sp, color = if (focusedRow) Teal else Dim, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(30.dp))
            ChannelTile(channel, 40.dp, 28.dp, 10.sp)
            Spacer(Modifier.width(8.dp))
            Text(channel.name, fontSize = 12.5.sp, color = if (focusedRow) Ink else Color(0xFFB8C2D0), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val visible = programmes?.filter { it.stop > windowStart && it.start < winEnd }
            if (visible != null && visible.isEmpty()) {
                Cell("No guide information", null, 0.dp, ((spanMs) * dpPerMs).dp, focusedRow, false, false, false)
            }
            visible?.forEach { p ->
                val s = maxOf(p.start, windowStart)
                val e = minOf(p.stop, winEnd)
                Cell(
                    title = (if (p.start < windowStart) "‹ " else "") + p.title,
                    time = "${fmtTime(p.start)} – ${fmtTime(p.stop)}",
                    x = ((s - windowStart) * dpPerMs).dp,
                    w = ((e - s) * dpPerMs).dp,
                    focused = focusedRow && p.isOnAt(focusTime),
                    onNow = p.isOnAt(now),
                    recDot = (recordingNow && p.isOnAt(now)) || isScheduled(p),
                    past = p.stop <= now,
                )
            }
        }
    }
}

@Composable
private fun Cell(title: String, time: String?, x: Dp, w: Dp, focused: Boolean, onNow: Boolean, recDot: Boolean, past: Boolean) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.offset(x = x).width((w - 3.dp).coerceAtLeast(2.dp)).fillMaxHeight().padding(vertical = 3.dp)
            .clip(shape)
            .background(
                when {
                    focused -> FocusFill
                    onNow -> androidx.compose.ui.graphics.SolidColor(Color(0x1CFFFFFF))
                    else -> androidx.compose.ui.graphics.SolidColor(Color(0x0DFFFFFF))
                }
            )
            .then(if (focused) Modifier.border(1.5.dp, Teal, shape) else Modifier.border(1.dp, Color(0x0FFFFFFF), shape))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recDot) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(RecRed))
                    Spacer(Modifier.width(5.dp))
                }
                Text(title, fontSize = 12.5.sp, color = if (past) Dim else Ink, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (time != null) Text(time, fontSize = 10.sp, color = if (focused) Color(0xFFCFE9E5) else Dim, maxLines = 1)
        }
    }
}
