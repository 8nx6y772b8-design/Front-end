package com.tuner.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tuner.tv.MainViewModel
import com.tuner.tv.data.Settings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onToast: (String) -> Unit,
) {
    val s = vm.settings
    var type by rememberSaveable { mutableStateOf(s.sourceType) }
    var m3u by rememberSaveable { mutableStateOf(s.m3uUrl) }
    var server by rememberSaveable { mutableStateOf(s.xtServer) }
    var user by rememberSaveable { mutableStateOf(s.xtUser) }
    var pass by rememberSaveable { mutableStateOf(s.xtPass) }
    var epg by rememberSaveable { mutableStateOf(s.epgUrl) }
    var nasPath by rememberSaveable { mutableStateOf(s.nasPath) }
    var nasUser by rememberSaveable { mutableStateOf(s.nasUser) }
    var nasPass by rememberSaveable { mutableStateOf(s.nasPass) }
    var sourceMsg by remember { mutableStateOf<String?>(null) }
    var nasMsg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    BackHandler(enabled = canGoBack, onBack = onBack)
    LaunchedEffect(Unit) { focusSoon(firstFocus) }

    fun saveSource() {
        val ok = if (type == Settings.SOURCE_XTREAM) server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
        else m3u.trim().startsWith("http", ignoreCase = true)
        if (!ok) {
            sourceMsg = if (type == Settings.SOURCE_XTREAM) "Enter the server, username and password" else "Enter a link starting with http:// or https://"
            return
        }
        vm.saveSource(type, m3u, server, user, pass, epg)
        onSaved()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.width(18.dp))
            Text(if (canGoBack) "Settings" else "Welcome, let's add your channels", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // ---------------- playlist
            Glass(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Playlist")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Settings.SOURCE_M3U to "M3U link", Settings.SOURCE_XTREAM to "Xtream login").forEachIndexed { i, (k, label) ->
                            FocusRow(
                                onClick = { type = k },
                                selected = type == k,
                                height = 36.dp,
                                radius = 18.dp,
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ) {
                                Text((if (type == k) "● " else "○ ") + label, fontSize = 13.sp, color = if (type == k) Ink else Muted)
                            }
                        }
                    }
                    if (type == Settings.SOURCE_XTREAM) {
                        TvField("Server", server, { server = it }, placeholder = "http://example.com:8080", keyboardType = KeyboardType.Uri)
                        TvField("Username", user, { user = it })
                        TvField("Password", pass, { pass = it }, password = true)
                    } else {
                        TvField("Playlist link (M3U)", m3u, { m3u = it }, placeholder = "http://example.com/playlist.m3u", keyboardType = KeyboardType.Uri)
                    }
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("TV guide")
                    TvField("Guide link (XMLTV, optional)", epg, { epg = it },
                        placeholder = "Found automatically from your playlist", keyboardType = KeyboardType.Uri)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Save & load channels", { saveSource() })
                        if (canGoBack) {
                            PrimaryButton("Refresh guide", { vm.refreshEpg(force = true); onToast("Updating TV guide…") }, primary = false)
                            PrimaryButton("Back", onBack, primary = false)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Try demo channels", { vm.loadDemo(); onSaved() }, primary = false)
                    }
                    sourceMsg?.let { Text(it, fontSize = 12.sp, color = ErrorRed, lineHeight = 16.sp) }
                }
            }

            // ---------------- recordings
            Glass(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Recordings · network drive")
                    Text(
                        "Recordings save as video files in a shared folder on your network drive (SMB). Leave the folder blank to record onto the Fire Stick instead (limited space).",
                        fontSize = 12.sp, color = Muted, lineHeight = 17.sp,
                    )
                    TvField("Shared folder", nasPath, { nasPath = it }, placeholder = "\\\\NAS\\Recordings  or  192.168.1.50/Recordings", keyboardType = KeyboardType.Uri)
                    TvField("Username (blank for guest)", nasUser, { nasUser = it })
                    TvField("Password", nasPass, { nasPass = it }, password = true)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(if (testing) "Testing…" else "Test connection", {
                            if (!testing) {
                                testing = true
                                nasMsg = null
                                scope.launch {
                                    nasMsg = vm.testNas(nasPath, nasUser, nasPass)
                                    testing = false
                                }
                            }
                        }, primary = false)
                        PrimaryButton("Save drive", {
                            vm.saveNas(nasPath, nasUser, nasPass)
                            onToast("Recordings will save to ${vm.storageLabel}")
                        })
                    }
                    nasMsg?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("Connected")) Teal else ErrorRed, lineHeight = 17.sp) }
                    Spacer(Modifier.height(6.dp))
                    Text("Currently saving to: ${vm.storageLabel}", fontSize = 11.5.sp, color = Dim)
                    Text("Tip: find the folder on a Mac in Finder → Network, or on Windows in File Explorer → Network.", fontSize = 11.5.sp, color = Dim)
                }
            }
        }
    }
}
