package com.tuner.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.tuner.tv.ALL_CHANNELS
import com.tuner.tv.MainViewModel
import com.tuner.tv.UiState
import com.tuner.tv.data.Channel

private sealed interface Screen {
    data object Browse : Screen
    data object Settings : Screen
    data class Player(val channels: List<Channel>, val index: Int) : Screen
}

@Composable
fun TunerApp(vm: MainViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Browse) }
    var selectedGroup by rememberSaveable { mutableStateOf(ALL_CHANNELS) }
    var focusChannelId by remember { mutableStateOf(vm.repo.lastChannelId.takeIf { it >= 0 }) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            val state = vm.state
            when {
                state is UiState.Loading -> LoadingScreen()

                state is UiState.NeedsSetup || screen == Screen.Settings -> SetupScreen(
                    initialUrl = vm.repo.playlistUrl.orEmpty(),
                    canGoBack = state is UiState.Ready,
                    onSubmit = { url ->
                        vm.setPlaylist(url)
                        screen = Screen.Browse
                    },
                    onRefresh = {
                        vm.load(forceRefresh = true)
                        screen = Screen.Browse
                    }.takeIf { !vm.repo.playlistUrl.isNullOrBlank() },
                    onBack = { screen = Screen.Browse },
                )

                state is UiState.Error -> ErrorScreen(
                    message = state.message,
                    onRetry = { vm.load(forceRefresh = true) },
                    onChangePlaylist = { screen = Screen.Settings },
                )

                state is UiState.Ready -> when (val s = screen) {
                    is Screen.Player -> PlayerScreen(
                        channels = s.channels,
                        startIndex = s.index,
                        onChannelChanged = { vm.repo.lastChannelId = it.id },
                        onExit = { last ->
                            focusChannelId = last.id
                            screen = Screen.Browse
                        },
                    )
                    else -> BrowseScreen(
                        groups = state.groups,
                        selectedGroup = selectedGroup,
                        onSelectGroup = { selectedGroup = it },
                        focusChannelId = focusChannelId,
                        onPlay = { list, index -> screen = Screen.Player(list, index) },
                        onSettings = { screen = Screen.Settings },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading playlist…", style = MaterialTheme.typography.headlineSmall, color = Muted)
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onChangePlaylist: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.widthIn(max = 800.dp).padding(32.dp),
        ) {
            Text("Something went wrong", style = MaterialTheme.typography.headlineMedium)
            Text(message, color = Muted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text("Try again") }
                OutlinedButton(onClick = onChangePlaylist) { Text("Change playlist") }
            }
        }
    }
}
