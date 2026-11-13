package com.tuner.tv.epg

import android.content.Context
import com.tuner.tv.data.Channel
import com.tuner.tv.data.Http
import com.tuner.tv.data.Playlist
import com.tuner.tv.data.Settings
import com.tuner.tv.data.normaliseName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class EpgRepository(context: Context, private val settings: Settings) {

    private val db = EpgDatabase(context)

    /** Which guide link will be used: manual, then Xtream, then the playlist header. */
    fun guideUrl(playlist: Playlist?): String? =
        settings.epgUrl.ifBlank { null } ?: settings.xtreamEpgUrl() ?: playlist?.epgUrl

    /** Downloads and stores the guide if it's stale (12h) or [force]d. Returns programme count. */
    suspend fun refresh(playlist: Playlist, force: Boolean): Long = withContext(Dispatchers.IO) {
        val url = guideUrl(playlist) ?: throw IOException("No guide link. Add one in Settings.")
        val fresh = System.currentTimeMillis() - settings.epgLastRefresh < 12 * 3_600_000L
        if (!force && fresh && db.count() > 0) return@withContext db.count()

        val now = System.currentTimeMillis()
        val parser = XmltvParser(
            wantedIds = playlist.channels.mapNotNull { it.tvgId?.lowercase() }.toHashSet(),
            wantedNames = playlist.channels.map { normaliseName(it.name) }.toHashSet(),
            from = now - 6 * 3_600_000L,
            to = now + 4 * 86_400_000L,
        )
        val conn = Http.open(url, 120_000)
        val count = try {
            Http.body(conn).use { input -> db.replaceAll { insert -> parser.parse(input, insert) } }
        } finally {
            conn.disconnect()
        }
        settings.epgLastRefresh = System.currentTimeMillis()
        count.toLong()
    }

    fun programmes(channel: Channel, from: Long, to: Long): List<Programme> =
        db.range(channel.guideKeys, from, to)

    fun nowNext(channel: Channel, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val list = db.range(channel.guideKeys, now, now + 12 * 3_600_000L)
        val current = list.firstOrNull { it.isOnAt(now) }
        val next = list.firstOrNull { it.start >= (current?.stop ?: now) }
        return current to next
    }

    fun hasData(): Boolean = db.count() > 0
}
