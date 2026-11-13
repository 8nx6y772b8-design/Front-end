package com.tuner.tv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvTimeTest {
    @Test
    fun parsesOffsetTimes() {
        // 2026-11-13 14:30:00 +1300 == 2026-11-13 01:30:00 UTC
        assertEquals(1794533400000L, XmltvParser.parseTime("20261113143000 +1300"))
    }

    @Test
    fun parsesUtcWhenNoOffset() {
        assertEquals(1794533400000L, XmltvParser.parseTime("20261113013000"))
    }

    @Test
    fun negativeOffset() {
        assertEquals(1794533400000L, XmltvParser.parseTime("20261112203000 -0500"))
    }

    @Test
    fun rejectsJunk() {
        assertNull(XmltvParser.parseTime("soon"))
        assertNull(XmltvParser.parseTime(null))
    }
}
