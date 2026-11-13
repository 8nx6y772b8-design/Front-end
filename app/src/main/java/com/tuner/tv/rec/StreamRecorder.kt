package com.tuner.tv.rec

import com.tuner.tv.data.Http
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

/**
 * Saves a live stream to a file, byte for byte (no re-encoding, so it's light on the
 * Fire Stick).
 *  - Plain MPEG-TS streams are copied straight through.
 *  - HLS streams are followed segment by segment. TS segments join into one valid .ts;
 *    fMP4 segments (with an init segment) join into one valid .mp4.
 */
class StreamRecorder(private val url: String) {

    enum class Kind { HLS, PROGRESSIVE }

    data class Probe(val kind: Kind, val extension: String, val mediaUrl: URL)

    /** Works out what kind of stream this is, so the file gets the right extension. */
    fun probe(): Probe {
        val conn = Http.open(url, 20_000)
        try {
            val type = conn.contentType?.lowercase() ?: ""
            val finalUrl = conn.url
            val looksHls = "mpegurl" in type || ".m3u8" in url.lowercase() || ".m3u8" in finalUrl.path.lowercase()
            if (!looksHls) return Probe(Kind.PROGRESSIVE, "ts", finalUrl)
            val text = Http.body(conn).bufferedReader().use { it.readText() }
            if (!text.trimStart().startsWith("#EXTM3U")) return Probe(Kind.PROGRESSIVE, "ts", finalUrl)
            val mediaUrl = if ("#EXT-X-STREAM-INF" in text) bestVariant(text, finalUrl) else finalUrl
            val media = if (mediaUrl == finalUrl) text else Http.text(mediaUrl.toString()).first
            val pl = MediaPlaylist.parse(media)
            pl.encryption?.let { throw IOException("This channel is encrypted ($it) and can't be recorded") }
            return Probe(Kind.HLS, if (pl.mapUri != null) "mp4" else "ts", mediaUrl)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Records until [shouldStop] returns true or the stream ends. Network drops are
     * retried; if it keeps failing the exception is thrown.
     */
    fun record(probe: Probe, out: OutputStream, shouldStop: () -> Boolean, onBytes: (Long) -> Unit) {
        var failures = 0
        val state = HlsState()
        while (!shouldStop()) {
            try {
                when (probe.kind) {
                    Kind.PROGRESSIVE -> copyProgressive(probe.mediaUrl, out, shouldStop, onBytes)
                    Kind.HLS -> followHls(probe.mediaUrl, state, out, shouldStop, onBytes)
                }
                if (state.ended) return
                failures = 0
            } catch (e: IOException) {
                if (shouldStop()) return
                if (++failures > 30) throw e
                Thread.sleep(minOf(2_000L * failures, 15_000L))
            }
        }
    }

    private fun copyProgressive(url: URL, out: OutputStream, shouldStop: () -> Boolean, onBytes: (Long) -> Unit) {
        val conn = Http.open(url.toString(), 30_000)
        try {
            conn.inputStream.use { copy(it, out, shouldStop, onBytes) }
        } finally {
            conn.disconnect()
        }
    }

    private class HlsState {
        var lastSeq = -1L
        var wroteInit = false
        var ended = false
    }

    private fun followHls(mediaUrl: URL, st: HlsState, out: OutputStream, shouldStop: () -> Boolean, onBytes: (Long) -> Unit) {
        while (!shouldStop()) {
            val pl = MediaPlaylist.parse(Http.text(mediaUrl.toString()).first)
            if (!st.wroteInit && pl.mapUri != null) {
                download(URL(mediaUrl, pl.mapUri), out, shouldStop, onBytes)
                st.wroteInit = true
            }
            // On the first pass only take the last few segments, i.e. "now", not the whole timeshift window.
            val firstIndex = if (st.lastSeq < 0) maxOf(0, pl.segments.size - 3) else 0
            for (i in firstIndex until pl.segments.size) {
                val seq = pl.mediaSequence + i
                if (seq <= st.lastSeq) continue
                if (shouldStop()) return
                download(URL(mediaUrl, pl.segments[i]), out, shouldStop, onBytes)
                st.lastSeq = seq
            }
            if (pl.endList) {
                st.ended = true
                return
            }
            sleepUnlessStopped(maxOf(1_000L, pl.targetDuration * 500L), shouldStop)
        }
    }

    private fun download(url: URL, out: OutputStream, shouldStop: () -> Boolean, onBytes: (Long) -> Unit) {
        val conn = Http.open(url.toString(), 30_000)
        try {
            conn.inputStream.use { copy(it, out, { false }, onBytes) }  // always finish a segment
        } finally {
            conn.disconnect()
        }
    }

    private fun copy(input: InputStream, out: OutputStream, shouldStop: () -> Boolean, onBytes: (Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        while (!shouldStop()) {
            val n = input.read(buf)
            if (n < 0) return
            out.write(buf, 0, n)
            onBytes(n.toLong())
        }
    }

    private fun sleepUnlessStopped(ms: Long, shouldStop: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until && !shouldStop()) Thread.sleep(200)
    }

    private fun bestVariant(master: String, base: URL): URL {
        var best: String? = null
        var bestBw = -1L
        val lines = master.lines()
        for ((i, line) in lines.withIndex()) {
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: continue
                if (bw > bestBw) {
                    bestBw = bw
                    best = uri.trim()
                }
            }
        }
        return URL(base, best ?: throw IOException("No stream variants found"))
    }
}

/** The parts of an HLS media playlist the recorder needs. */
data class MediaPlaylist(
    val targetDuration: Long,
    val mediaSequence: Long,
    val segments: List<String>,
    val mapUri: String?,
    val endList: Boolean,
    val encryption: String?,
) {
    companion object {
        fun parse(text: String): MediaPlaylist {
            var target = 6L
            var seq = 0L
            var map: String? = null
            var end = false
            var enc: String? = null
            val segs = ArrayList<String>()
            for (raw in text.lines()) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(':').trim().toDoubleOrNull()?.toLong() ?: 6
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> seq = line.substringAfter(':').trim().toLongOrNull() ?: 0
                    line.startsWith("#EXT-X-MAP:") -> map = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    line.startsWith("#EXT-X-KEY:") -> {
                        val method = Regex("METHOD=([A-Z0-9-]+)").find(line)?.groupValues?.get(1)
                        enc = if (method == null || method == "NONE") null else method
                    }
                    line.startsWith("#EXT-X-ENDLIST") -> end = true
                    line.startsWith("#") -> Unit
                    else -> segs += line
                }
            }
            return MediaPlaylist(target, seq, segs, map, end, enc)
        }
    }
}
