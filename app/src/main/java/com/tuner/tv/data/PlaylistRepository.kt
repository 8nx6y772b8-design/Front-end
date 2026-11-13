package com.tuner.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class PlaylistRepository(context: Context, private val settings: Settings) {

    private val cacheFile = File(context.filesDir, "playlist.m3u")

    /** Uses the cached copy unless [forceRefresh] is set or there is none yet. */
    suspend fun load(forceRefresh: Boolean, onProgress: (String) -> Unit = {}): Playlist = withContext(Dispatchers.IO) {
        val url = settings.playlistUrl() ?: throw IOException("No playlist set")
        if (forceRefresh || !cacheFile.exists()) download(url, onProgress)
        onProgress("Reading channels…")
        cacheFile.bufferedReader().useLines { M3uParser.parse(it) }
    }

    fun clearCache() {
        cacheFile.delete()
    }

    private suspend fun download(url: String, onProgress: (String) -> Unit) {
        val tmp = File(cacheFile.parentFile, "playlist.tmp")
        onProgress("Connecting to the playlist server…")
        val conn = Http.open(url)
        try {
            onProgress("Downloading playlist…")
            Http.body(conn).use { input ->
                tmp.outputStream().buffered(256 * 1024).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport >= 512 * 1024) {
                            lastReport = done
                            onProgress("Downloading playlist… %.1f MB".format(done / 1_048_576.0))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }
}
