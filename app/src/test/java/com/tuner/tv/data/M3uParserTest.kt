package com.tuner.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private fun parse(s: String) = M3uParser.parse(s.lineSequence())

    @Test
    fun parsesStandardEntries() {
        val list = parse(
            """
            #EXTM3U x-tvg-url="http://epg.example/guide.xml"
            #EXTINF:-1 tvg-id="one.nz" tvg-logo="http://logo/1.png" group-title="NZ",TVNZ 1
            http://example.com/one.m3u8
            #EXTINF:-1 tvg-id="news" group-title="News",News, Weather & Sport
            http://example.com/news.ts
            """.trimIndent()
        )
        assertEquals(2, list.size)
        assertEquals("TVNZ 1", list[0].name)
        assertEquals("NZ", list[0].group)
        assertEquals("http://logo/1.png", list[0].logo)
        assertEquals("one.nz", list[0].tvgId)
        assertEquals(1, list[0].number)
        assertEquals("News, Weather & Sport", list[1].name)
        assertEquals(2, list[1].number)
    }

    @Test
    fun commaInsideQuotedAttributeIsNotTheNameSeparator() {
        val list = parse(
            """
            #EXTINF:-1 group-title="Movies, Drama" tvg-logo="",Film Channel
            http://x/1
            """.trimIndent()
        )
        assertEquals("Film Channel", list[0].name)
        assertEquals("Movies, Drama", list[0].group)
        assertNull(list[0].logo)
    }

    @Test
    fun handlesExtGrpBlankLinesCrlfAndBareUrls() {
        val list = parse(
            "#EXTM3U\r\n\r\n#EXTINF:-1,Alpha\r\n#EXTGRP:Music\r\n#EXTVLCOPT:http-user-agent=x\r\nhttp://a/alpha\r\nhttp://b/bare.m3u8\r\n"
        )
        assertEquals(2, list.size)
        assertEquals("Alpha", list[0].name)
        assertEquals("Music", list[0].group)
        assertEquals("bare.m3u8", list[1].name)
        assertEquals(M3uParser.UNCATEGORISED, list[1].group)
    }

    @Test
    fun fallsBackToTvgNameWhenDisplayNameMissing() {
        val list = parse("#EXTINF:-1 tvg-name=\"Backup Name\",\nhttp://x/y")
        assertEquals("Backup Name", list[0].name)
    }
}
