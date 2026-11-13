package com.tuner.tv.data

data class Playlist(val channels: List<Channel>, val epgUrl: String?)

/**
 * Forgiving M3U / M3U8 playlist parser.
 *
 *   #EXTM3U x-tvg-url="http://example.com/guide.xml"
 *   #EXTINF:-1 tvg-id="tvnz1.nz" tvg-logo="http://..." group-title="NZ",TVNZ 1
 *   http://example.com/stream.m3u8
 *
 * Also accepts #EXTGRP group lines, missing attributes, blank lines and stray
 * comments. Streams with no #EXTINF line are kept, named by URL.
 */
object M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")
    const val UNCATEGORISED = "Uncategorised"

    fun parse(lines: Sequence<String>): Playlist {
        val out = ArrayList<Channel>()
        var info: String? = null
        var extGroup: String? = null
        var epgUrl: String? = null

        for (raw in lines) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attrs(line)
                    epgUrl = (attrs["x-tvg-url"] ?: attrs["url-tvg"])
                        ?.split(',')?.firstOrNull()?.trim()?.ifBlank { null }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = line
                    extGroup = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    extGroup = line.substringAfter(':').trim().ifEmpty { null }
                line.startsWith("#") -> Unit
                else -> {
                    val currentInfo = info
                    val a = if (currentInfo != null) attrs(currentInfo) else emptyMap()
                    val name = currentInfo?.let(::displayName)?.ifBlank { null }
                        ?: a["tvg-name"]?.ifBlank { null }
                        ?: line.substringAfterLast('/')
                    out += Channel(
                        id = out.size,
                        number = out.size + 1,
                        name = name,
                        url = line,
                        logo = a["tvg-logo"]?.ifBlank { null },
                        group = a["group-title"]?.ifBlank { null } ?: extGroup ?: UNCATEGORISED,
                        tvgId = a["tvg-id"]?.ifBlank { null },
                    )
                    info = null
                    extGroup = null
                }
            }
        }
        return Playlist(out, epgUrl)
    }

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

    /** The channel name is everything after the first comma that isn't inside quotes. */
    private fun displayName(extinf: String): String {
        var inQuotes = false
        for ((i, c) in extinf.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return extinf.substring(i + 1).trim()
        }
        return ""
    }
}
