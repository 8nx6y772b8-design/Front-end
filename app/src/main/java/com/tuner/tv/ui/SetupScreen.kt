package com.tuner.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.compose.material3.Text as M3Text

@Composable
fun SetupScreen(
    initialUrl: String,
    canGoBack: Boolean,
    onSubmit: (String) -> Unit,
    onRefresh: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val valid = url.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }
    val fieldFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = canGoBack, onBack = onBack)

    // Start on the button when a playlist is already saved, so the keyboard doesn't pop up.
    LaunchedEffect(Unit) {
        runCatching { if (initialUrl.isBlank()) fieldFocus.requestFocus() else buttonFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(48.dp),
        ) {
            Text("Tuner", style = MaterialTheme.typography.displaySmall, color = Teal)
            Text(
                if (canGoBack) "Playlist settings" else "Add your playlist",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Enter the web address of an M3U playlist. Press the centre button on the remote to open the keyboard.",
                color = Muted,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { M3Text("Playlist URL") },
                placeholder = { M3Text("http://example.com/playlist.m3u") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSubmit(url) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Teal,
                    unfocusedBorderColor = Muted,
                    focusedLabelColor = Teal,
                    unfocusedLabelColor = Muted,
                    focusedPlaceholderColor = Muted,
                    unfocusedPlaceholderColor = Muted,
                    cursorColor = Teal,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    // Let the D-pad leave the text field.
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                            focusManager.moveFocus(FocusDirection.Down)
                        } else false
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onSubmit(url) },
                    enabled = valid,
                    modifier = Modifier.focusRequester(buttonFocus),
                ) { Text("Load playlist") }
                if (onRefresh != null) {
                    OutlinedButton(onClick = onRefresh) { Text("Refresh channels") }
                }
                if (canGoBack) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        }
    }
}
