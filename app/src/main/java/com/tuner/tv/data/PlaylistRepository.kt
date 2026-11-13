package com.tuner.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Bigger than this and a Fire Stick runs out of memory reading it. */
const val MAX_PLAYLIST_BYTES = 40L * 1024 * 1024

private fun tooBig(bytes: Long) = IOException(
    "This playlist is over %.0f MB (%.0f MB so far), which is more than a Fire Stick can handle. Try a smaller playlist."
        .format(MAX_PLAYLIST_BYTES / 1_048_576.0, bytes / 1_048_576.0)
)

class PlaylistRepository(context: Context, private val settings: Settings) {

    private val cacheFile = File(context.filesDir, "playlist.m3u")

    /** Uses the cached copy unless [forceRefresh] is set or there is none yet. */
    suspend fun load(forceRefresh: Boolean, onProgress: (String) -> Unit = {}): Playlist = withContext(Dispatchers.IO) {
        val url = settings.playlistUrl() ?: throw IOException("No playlist set")
        if (forceRefresh || !cacheFile.exists()) download(url, onProgress)
        // Never try to read a file that will run the stick out of memory (and crash on every launch).
        if (cacheFile.length() > MAX_PLAYLIST_BYTES) {
            val size = cacheFile.length()
            cacheFile.delete()
            throw tooBig(size)
        }
        onProgress("Reading channels…")
        try {
            cacheFile.bufferedReader().useLines { M3uParser.parse(it) }
        } catch (e: OutOfMemoryError) {
            val size = cacheFile.length()
            cacheFile.delete()
            throw tooBig(size)
        }
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
                        if (done > MAX_PLAYLIST_BYTES) throw tooBig(done)
                        if (done - lastReport >= 512 * 1024) {
                            lastReport = done
                            onProgress("Downloading playlist… %.1f MB".format(done / 1_048_576.0))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }
}
