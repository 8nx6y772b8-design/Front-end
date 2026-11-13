package com.tuner.tv.epg

import com.tuner.tv.data.normaliseName
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Streaming XMLTV parser. Guide files can be 100MB+, so nothing is held in memory:
 * each matching programme is handed to [onProgramme] as it's read.
 *
 * Only programmes for channels in the playlist are kept. Guide channels are matched
 * by id (tvg-id) or, failing that, by display name.
 */
class XmltvParser(
    private val wantedIds: Set<String>,     // lower-case tvg-ids from the playlist
    private val wantedNames: Set<String>,   // normalised channel names from the playlist
    private val from: Long,
    private val to: Long,
) {
    fun parse(input: InputStream, onProgramme: (Programme) -> Unit) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val xp = factory.newPullParser()
        xp.setInput(input, null)

        val keyFor = HashMap<String, String>()   // guide channel id -> our key

        var event = xp.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (xp.name) {
                    "channel" -> readChannel(xp)?.let { (id, key) -> keyFor[id] = key }
                    "programme" -> {
                        val ch = xp.getAttributeValue(null, "channel") ?: ""
                        val key = keyFor[ch] ?: ch.lowercase().takeIf { it in wantedIds }
                        if (key == null) {
                            skip(xp)
                        } else {
                            val start = parseTime(xp.getAttributeValue(null, "start"))
                            val stop = parseTime(xp.getAttributeValue(null, "stop"))
                            val (title, desc) = readProgrammeText(xp)
                            if (start != null && stop != null && stop > from && start < to && title.isNotBlank()) {
                                onProgramme(Programme(key, start, stop, title, desc))
                            }
                        }
                    }
                }
            }
            event = xp.next()
        }
    }

    /** Returns (guide id, our key) if this guide channel is one we have. */
    private fun readChannel(xp: XmlPullParser): Pair<String, String>? {
        val id = xp.getAttributeValue(null, "id") ?: return null.also { skip(xp) }
        val names = ArrayList<String>()
        val depth = xp.depth
        while (true) {
            val e = xp.next()
            if (e == XmlPullParser.END_TAG && xp.depth == depth) break
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.START_TAG && xp.name == "display-name") names += xp.nextText()
        }
        if (id.lowercase() in wantedIds) return id to id.lowercase()
        for (n in names) {
            val norm = normaliseName(n)
            if (norm in wantedNames) return id to "n:$norm"
        }
        return null
    }

    private fun readProgrammeText(xp: XmlPullParser): Pair<String, String?> {
        var title = ""
        var desc: String? = null
        val depth = xp.depth
        while (true) {
            val e = xp.next()
            if (e == XmlPullParser.END_TAG && xp.depth == depth) break
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.START_TAG) {
                when (xp.name) {
                    "title" -> if (title.isEmpty()) title = xp.nextText().trim() else skip(xp)
                    "desc" -> if (desc == null) desc = xp.nextText().trim() else skip(xp)
                    else -> skip(xp)
                }
            }
        }
        return title to desc
    }

    private fun skip(xp: XmlPullParser) {
        if (xp.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (xp.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        /** "20261113143000 +1300" (offset optional, UTC if missing) -> epoch millis */
        fun parseTime(s: String?): Long? {
            if (s == null) return null
            val t = s.trim()
            if (t.length < 12) return null
            return try {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.clear()
                cal.set(
                    t.substring(0, 4).toInt(),
                    t.substring(4, 6).toInt() - 1,
                    t.substring(6, 8).toInt(),
                    t.substring(8, 10).toInt(),
                    t.substring(10, 12).toInt(),
                    if (t.length >= 14 && t[12].isDigit()) t.substring(12, 14).toInt() else 0,
                )
                var ms = cal.timeInMillis
                val off = t.substringAfter(' ', "").trim()
                if (off.length >= 5 && (off[0] == '+' || off[0] == '-')) {
                    val mins = off.substring(1, 3).toInt() * 60 + off.substring(3, 5).toInt()
                    ms -= (if (off[0] == '+') 1 else -1) * mins * 60_000L
                }
                ms
            } catch (e: Exception) {
                null
            }
        }
    }
}
