package com.tuner.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val USER_AGENT = "Tuner/0.1 (Android TV)"

class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "playlist.m3u")

    var playlistUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var lastChannelId: Int
        get() = prefs.getInt(KEY_LAST_CHANNEL, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_CHANNEL, value).apply()

    /** Uses the cached copy unless [forceRefresh] is set or there is none yet. */
    suspend fun load(forceRefresh: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        val url = playlistUrl ?: throw IOException("No playlist set")
        if (forceRefresh || !cacheFile.exists()) download(url)
        cacheFile.bufferedReader().useLines { M3uParser.parse(it) }
    }

    private fun download(url: String) {
        val tmp = File(cacheFile.parentFile, "playlist.tmp")
        val conn = open(url)
        try {
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }

    /** HttpURLConnection won't follow http <-> https redirects on its own, so do it by hand. */
    private fun open(start: String): HttpURLConnection {
        var url = URL(start)
        repeat(6) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            when (code) {
                in 200..299 -> return conn
                301, 302, 303, 307, 308 -> {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect with no destination")
                    conn.disconnect()
                    url = URL(url, location)
                }
                else -> {
                    conn.disconnect()
                    throw IOException("Playlist server returned HTTP $code")
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private companion object {
        const val KEY_URL = "playlist_url"
        const val KEY_LAST_CHANNEL = "last_channel"
    }
}
