package com.tuner.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuner.tv.data.Channel
import com.tuner.tv.data.PlaylistRepository
import kotlinx.coroutines.launch

sealed interface UiState {
    data object NeedsSetup : UiState
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Ready(val channels: List<Channel>, val groups: List<Group>) : UiState
}

data class Group(val name: String, val channels: List<Channel>)

const val ALL_CHANNELS = "All channels"

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = PlaylistRepository(app)

    var state by mutableStateOf<UiState>(UiState.Loading)
        private set

    init {
        if (repo.playlistUrl.isNullOrBlank()) state = UiState.NeedsSetup else load(forceRefresh = false)
    }

    fun setPlaylist(url: String) {
        repo.playlistUrl = url.trim()
        load(forceRefresh = true)
    }

    fun load(forceRefresh: Boolean) {
        state = UiState.Loading
        viewModelScope.launch {
            state = try {
                val channels = repo.load(forceRefresh)
                if (channels.isEmpty()) {
                    UiState.Error("No channels found in that playlist. Check the link is an M3U playlist.")
                } else {
                    UiState.Ready(channels, buildGroups(channels))
                }
            } catch (e: Exception) {
                UiState.Error("Couldn't load the playlist: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun buildGroups(channels: List<Channel>): List<Group> {
        val byGroup = channels.groupBy { it.group } // keeps playlist order
        return listOf(Group(ALL_CHANNELS, channels)) + byGroup.map { (name, list) -> Group(name, list) }
    }
}
