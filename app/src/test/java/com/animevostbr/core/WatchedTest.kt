package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedTest {

    private val lite = TitleLite(1, "Тайтл", "")
    private val playlist = (1..3).map { PlaylistItem(it, "$it серия", hd = null, sd = "http://v/$it.mp4") }

    @Test
    fun markingAnEpisode_movesToTheStartOfTheNext() {
        val p = watchedThrough(lite, playlist, 2)
        assertEquals(3, p.episode)
        assertEquals(0L, p.positionMs)
        assertEquals("3 серия", p.label)
    }

    @Test
    fun markingTheLastEpisode_staysOnIt() {
        val p = watchedThrough(lite, playlist, 3)
        assertEquals(3, p.episode)
        assertEquals("3 серия", p.label)
    }
}
