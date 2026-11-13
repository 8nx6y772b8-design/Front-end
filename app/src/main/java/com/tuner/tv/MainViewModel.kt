package com.tuner.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuner.tv.data.Channel
import com.tuner.tv.data.Playlist
import com.tuner.tv.data.PlaylistRepository
import com.tuner.tv.data.Settings
import com.tuner.tv.epg.EpgRepository
import com.tuner.tv.epg.Programme
import com.tuner.tv.rec.NasConfig
import com.tuner.tv.rec.RecordRequest
import com.tuner.tv.rec.RecorderService
import com.tuner.tv.rec.RecordingFile
import com.tuner.tv.rec.RecordingStorage
import com.tuner.tv.rec.Schedule
import com.tuner.tv.rec.ScheduleStore
import com.tuner.tv.rec.SmbSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object NeedsSetup : UiState
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Ready(val playlist: Playlist, val groups: List<Group>) : UiState
}

data class Group(val name: String, val channels: List<Channel>)

const val ALL_CHANNELS = "All channels"

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val playlists = PlaylistRepository(app, settings)
    val epg = EpgRepository(app, settings)
    private val scheduleStore = ScheduleStore(app)

    var state by mutableStateOf<UiState>(UiState.Loading)
        private set

    /** Bumped whenever the guide data changes, so screens re-read it. */
    var epgVersion by mutableIntStateOf(0)
        private set
    var epgStatus by mutableStateOf<String?>(null)
        private set

    var schedules by mutableStateOf<List<Schedule>>(emptyList())
        private set
    var recordingFiles by mutableStateOf<List<RecordingFile>>(emptyList())
        private set
    var recordingsError by mutableStateOf<String?>(null)
        private set

    val nas: NasConfig? get() = NasConfig.parse(settings.nasPath, settings.nasUser, settings.nasPass)
    val storageLabel: String get() = RecordingStorage.from(app, settings).label

    init {
        scheduleStore.rearmAll()
        schedules = scheduleStore.all()
        if (!settings.hasSource) state = UiState.NeedsSetup else load(forceRefresh = false)
    }

    // ------------------------------------------------------------ playlist

    fun load(forceRefresh: Boolean) {
        state = UiState.Loading
        viewModelScope.launch {
            state = try {
                val pl = playlists.load(forceRefresh)
                if (pl.channels.isEmpty()) {
                    UiState.Error("No channels found. Check the playlist link or login details.")
                } else {
                    UiState.Ready(pl, buildGroups(pl.channels))
                }
            } catch (e: Exception) {
                UiState.Error("Couldn't load the playlist: ${e.message ?: e.javaClass.simpleName}")
            }
            if (state is UiState.Ready) {
                refreshEpg(force = forceRefresh)
                refreshRecordings()
            }
        }
    }

    fun saveSource(type: String, m3u: String, server: String, user: String, pass: String, epgUrl: String) {
        settings.sourceType = type
        settings.m3uUrl = m3u
        settings.xtServer = server
        settings.xtUser = user
        settings.xtPass = pass
        if (epgUrl.trim() != settings.epgUrl) settings.epgLastRefresh = 0
        settings.epgUrl = epgUrl
        playlists.clearCache()
        load(forceRefresh = true)
    }

    private fun buildGroups(channels: List<Channel>): List<Group> =
        listOf(Group(ALL_CHANNELS, channels)) + channels.groupBy { it.group }.map { (n, l) -> Group(n, l) }

    // ------------------------------------------------------------ guide

    fun refreshEpg(force: Boolean) {
        val pl = (state as? UiState.Ready)?.playlist ?: return
        if (epg.guideUrl(pl) == null) {
            epgStatus = "No TV guide link yet. Add one in Settings."
            return
        }
        viewModelScope.launch {
            if (force || !epg.hasData()) epgStatus = "Updating TV guide…"
            epgStatus = try {
                val n = epg.refresh(pl, force)
                epgVersion++
                if (n == 0L) "The guide had no programmes matching your channels" else null
            } catch (e: Exception) {
                "TV guide: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun nowNext(c: Channel): Pair<Programme?, Programme?> = runCatching { epg.nowNext(c) }.getOrDefault(null to null)

    suspend fun programmes(c: Channel, from: Long, to: Long): List<Programme> =
        withContext(Dispatchers.IO) { runCatching { epg.programmes(c, from, to) }.getOrDefault(emptyList()) }

    // ------------------------------------------------------------ recordings

    fun recordNow(c: Channel) {
        RecorderService.start(app, RecordRequest(channelId = c.id, channelName = c.name, url = c.url))
    }

    fun recordUntil(c: Channel, end: Long, title: String?) {
        RecorderService.start(app, RecordRequest(channelId = c.id, channelName = c.name, url = c.url, endAt = end, title = title))
    }

    fun stopRecording(id: String) = RecorderService.stop(app, id)

    fun schedule(c: Channel, start: Long, end: Long, title: String?) {
        scheduleStore.add(Schedule.new(c.id, c.name, c.url, start, end, title))
        schedules = scheduleStore.all()
    }

    fun cancelSchedule(id: String) {
        scheduleStore.remove(id)
        schedules = scheduleStore.all()
    }

    fun refreshRecordings() {
        schedules = scheduleStore.all()
        viewModelScope.launch {
            val storage = RecordingStorage.from(app, settings)
            try {
                recordingFiles = withContext(Dispatchers.IO) { storage.list() }
                recordingsError = null
            } catch (e: Exception) {
                recordingsError = SmbSession.friendly(e)
            }
        }
    }

    fun deleteRecording(f: RecordingFile) {
        viewModelScope.launch {
            val storage = RecordingStorage.from(app, settings)
            runCatching { withContext(Dispatchers.IO) { storage.delete(f) } }
            refreshRecordings()
        }
    }

    fun saveNas(path: String, user: String, pass: String) {
        settings.nasPath = path
        settings.nasUser = user
        settings.nasPass = pass
        refreshRecordings()
    }

    /** Tries writing a small file to the drive. Returns a message to show. */
    suspend fun testNas(path: String, user: String, pass: String): String = withContext(Dispatchers.IO) {
        val cfg = NasConfig.parse(path, user, pass)
            ?: return@withContext "Enter the drive as \\\\NAS-NAME\\SharedFolder (a folder after it is optional)"
        try {
            com.tuner.tv.rec.SmbStorage(cfg).test()
            "Connected: recordings will save to ${cfg.display}"
        } catch (e: Exception) {
            "Couldn't connect: ${SmbSession.friendly(e)}"
        }
    }
}
