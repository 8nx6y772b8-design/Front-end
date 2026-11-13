package com.tuner.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class PlaylistRepository(context: Context, private val settings: Settings) {

    private val cacheFile = File(context.filesDir, "playlist.m3u")

    /** Uses the cached copy unless [forceRefresh] is set or there is none yet. */
    suspend fun load(forceRefresh: Boolean): Playlist = withContext(Dispatchers.IO) {
        val url = settings.playlistUrl() ?: throw IOException("No playlist set")
        if (forceRefresh || !cacheFile.exists()) download(url)
        cacheFile.bufferedReader().useLines { M3uParser.parse(it) }
    }

    fun clearCache() {
        cacheFile.delete()
    }

    private fun download(url: String) {
        val tmp = File(cacheFile.parentFile, "playlist.tmp")
        val conn = Http.open(url)
        try {
            Http.body(conn).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }
}
