package com.tuner.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tuner.tv.Group
import com.tuner.tv.data.Channel

@Composable
fun BrowseScreen(
    groups: List<Group>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    focusChannelId: Int?,
    onPlay: (List<Channel>, Int) -> Unit,
    onSettings: () -> Unit,
) {
    val group = groups.firstOrNull { it.name == selectedGroup } ?: groups.first()
    val groupIndex = groups.indexOf(group)
    val channels = group.channels
    val restoreIndex = remember(channels, focusChannelId) { channels.indexOfFirst { it.id == focusChannelId } }

    // Group list: index 0 is Settings, so the selected group sits at groupIndex + 1.
    val groupListState = remember { LazyListState(firstVisibleItemIndex = (groupIndex + 1 - 3).coerceAtLeast(0)) }
    // A fresh scroll position for each group, starting near the last-watched channel.
    val channelListState = remember(group.name) {
        LazyListState(firstVisibleItemIndex = (restoreIndex - 3).coerceAtLeast(0))
    }

    val channelFocus = remember { FocusRequester() }
    val groupFocus = remember { FocusRequester() }

    // On entering the screen, focus the last-watched channel (or the current group).
    LaunchedEffect(Unit) {
        val target = if (restoreIndex >= 0) channelFocus else groupFocus
        repeat(5) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        // ---- Groups ----
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text("Tuner", style = MaterialTheme.typography.headlineSmall, color = Teal)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = groupListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    ListItem(
                        selected = false,
                        onClick = onSettings,
                        headlineContent = { Text("Settings", color = Muted) },
                    )
                }
                itemsIndexed(groups, key = { _, g -> "g:" + g.name }) { i, g ->
                    ListItem(
                        selected = g.name == group.name,
                        onClick = { onSelectGroup(g.name) },
                        headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text("${g.channels.size}", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier
                            .onFocusChanged { if (it.isFocused) onSelectGroup(g.name) }
                            .then(if (i == groupIndex) Modifier.focusRequester(groupFocus) else Modifier),
                    )
                }
            }
        }

        Spacer(Modifier.width(32.dp))

        // ---- Channels ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(group.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text("${channels.size} channels", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = channelListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                    ListItem(
                        selected = false,
                        onClick = { onPlay(channels, i) },
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${c.number}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(52.dp),
                                )
                                ChannelLogo(c)
                            }
                        },
                        headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = if (i == restoreIndex) Modifier.focusRequester(channelFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Panel),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(
                channel.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
    }
}
