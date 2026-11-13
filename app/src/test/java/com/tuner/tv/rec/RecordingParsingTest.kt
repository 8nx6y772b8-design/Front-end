package com.tuner.tv.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingParsingTest {
    @Test
    fun parsesLiveMediaPlaylist() {
        val pl = MediaPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:1200
            #EXTINF:6.0,
            seg1200.ts
            #EXTINF:6.0,
            seg1201.ts
            """.trimIndent()
        )
        assertEquals(6, pl.targetDuration)
        assertEquals(1200, pl.mediaSequence)
        assertEquals(listOf("seg1200.ts", "seg1201.ts"), pl.segments)
        assertNull(pl.mapUri)
        assertNull(pl.encryption)
        assertTrue(!pl.endList)
    }

    @Test
    fun detectsFmp4AndEncryption() {
        val pl = MediaPlaylist.parse("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n#EXTINF:4,\na.m4s\n#EXT-X-ENDLIST")
        assertEquals("init.mp4", pl.mapUri)
        assertEquals("AES-128", pl.encryption)
        assertTrue(pl.endList)
    }

    @Test
    fun parsesNetworkDrivePaths() {
        val a = NasConfig.parse("\\\\DiskStation\\video\\TV Recordings", "me", "pw")!!
        assertEquals("DiskStation", a.host)
        assertEquals("video", a.share)
        assertEquals("TV Recordings", a.folder)

        val b = NasConfig.parse("smb://192.168.1.50/Public", "", "")!!
        assertEquals("192.168.1.50", b.host)
        assertEquals("Public", b.share)
        assertEquals("", b.folder)

        val c = NasConfig.parse("nas/share/a/b", "WORKGROUP\\bob", "x")!!
        assertEquals("a\\b", c.folder)
        assertEquals("WORKGROUP", c.domain)
        assertEquals("bob", c.user)

        assertNull(NasConfig.parse("justahost", "", ""))
    }
}
