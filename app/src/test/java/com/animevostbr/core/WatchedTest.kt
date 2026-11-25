package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedTest {

    private val lite = TitleLite(1, "Тайтл", "")
    private val playlist = (1..3).map { PlaylistItem(it, "$it серия", hd = null, sd = "http://v/$it.mp4") }

    // the record the player saves when an episode ends: that episode, position 0
    @Test
    fun markingAnEpisode_recordsItAsFinished() {
        val p = watchedThrough(lite, playlist, 2)
        assertEquals(2, p.episode)
        assertEquals(0L, p.positionMs)
        assertEquals("2 серия", p.label)
    }

    @Test
    fun markingTheLastEpisode_recordsTheLast() {
        val p = watchedThrough(lite, playlist, 3)
        assertEquals(3, p.episode)
        assertEquals("3 серия", p.label)
    }
}
