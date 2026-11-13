package com.tuner.tv.data

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

const val USER_AGENT = "Tuner/0.2 (Android TV)"

object Http {

    /**
     * Opens a GET connection, following redirects by hand (HttpURLConnection won't
     * follow http <-> https on its own). Throws on anything other than 2xx.
     */
    fun open(start: String, readTimeoutMs: Int = 60_000): HttpURLConnection {
        var url = URL(start)
        repeat(8) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "gzip")
            when (val code = conn.responseCode) {
                in 200..299 -> return conn
                301, 302, 303, 307, 308 -> {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect with no destination")
                    conn.disconnect()
                    url = URL(url, location)
                }
                else -> {
                    conn.disconnect()
                    throw IOException("Server returned HTTP $code")
                }
            }
        }
        throw IOException("Too many redirects")
    }

    /** The response body, un-gzipped if the server or file is gzip-compressed. */
    fun body(conn: HttpURLConnection): InputStream {
        val raw = BufferedInputStream(conn.inputStream, 64 * 1024)
        raw.mark(4)
        val b1 = raw.read()
        val b2 = raw.read()
        raw.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw, 64 * 1024) else raw
    }

    fun text(url: String): Pair<String, URL> {
        val conn = open(url, 20_000)
        try {
            val finalUrl = conn.url
            return body(conn).bufferedReader().use { it.readText() } to finalUrl
        } finally {
            conn.disconnect()
        }
    }
}
