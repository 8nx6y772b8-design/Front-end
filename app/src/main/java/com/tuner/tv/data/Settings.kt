package com.tuner.tv.data

import android.content.Context
import java.net.URLEncoder

/** All user settings, stored in SharedPreferences. */
class Settings(context: Context) {

    private val p = context.applicationContext.getSharedPreferences("tuner", Context.MODE_PRIVATE)

    private fun str(key: String) = p.getString(key, "") ?: ""
    private fun put(key: String, value: String) = p.edit().putString(key, value.trim()).apply()

    /** "m3u" or "xtream" */
    var sourceType: String
        get() = p.getString("source", SOURCE_M3U) ?: SOURCE_M3U
        set(v) = put("source", v)

    var m3uUrl: String get() = str("playlist_url"); set(v) = put("playlist_url", v)
    var xtServer: String get() = str("xt_server"); set(v) = put("xt_server", v)
    var xtUser: String get() = str("xt_user"); set(v) = put("xt_user", v)
    var xtPass: String get() = str("xt_pass"); set(v) = put("xt_pass", v)

    /** Optional manual guide (XMLTV) link. Blank = detect automatically. */
    var epgUrl: String get() = str("epg_url"); set(v) = put("epg_url", v)
    var epgLastRefresh: Long
        get() = p.getLong("epg_last", 0)
        set(v) = p.edit().putLong("epg_last", v).apply()

    /** Network drive: \\NAS\share\folder, smb://NAS/share/folder or NAS/share/folder */
    var nasPath: String get() = str("nas_path"); set(v) = put("nas_path", v)
    var nasUser: String get() = str("nas_user"); set(v) = put("nas_user", v)
    var nasPass: String get() = str("nas_pass"); set(v) = put("nas_pass", v)

    var lastChannelId: Int
        get() = p.getInt("last_channel", -1)
        set(v) = p.edit().putInt("last_channel", v).apply()

    var schedulesJson: String
        get() = p.getString("schedules", "[]") ?: "[]"
        set(v) = p.edit().putString("schedules", v).apply()

    val hasSource: Boolean get() = playlistUrl() != null

    private fun xtBase(): String? {
        var s = xtServer.trim().trimEnd('/')
        if (s.isBlank() || xtUser.isBlank() || xtPass.isBlank()) return null
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        return s
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The playlist link to download, built from whichever source is chosen. */
    fun playlistUrl(): String? = when (sourceType) {
        SOURCE_XTREAM -> xtBase()?.let {
            "$it/get.php?username=${enc(xtUser)}&password=${enc(xtPass)}&type=m3u_plus&output=m3u8"
        }
        else -> m3uUrl.ifBlank { null }
    }

    /** Guide link the Xtream login provides, if that's the source. */
    fun xtreamEpgUrl(): String? = if (sourceType == SOURCE_XTREAM) {
        xtBase()?.let { "$it/xmltv.php?username=${enc(xtUser)}&password=${enc(xtPass)}" }
    } else null

    companion object {
        const val SOURCE_M3U = "m3u"
        const val SOURCE_XTREAM = "xtream"
    }
}
