package com.animevostbr.playback

import com.animevostbr.core.PlaylistItem
import com.animevostbr.core.Quality
import com.animevostbr.playback.PlaybackRules.Fallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player's choices of quality and link; both bugs found on the TV lived in this logic. */
class PlaybackRulesTest {

    private val item = PlaylistItem(1, "1 серия", hd = "http://v/720/1.mp4", sd = "http://v/1.mp4")

    @Test
    fun startQuality_mobileSettingOnlyOnMeteredNetwork() {
        assertEquals(Quality.FHD, PlaybackRules.startQuality(Quality.FHD, null, Quality.SD, metered = false))
        assertEquals(Quality.SD, PlaybackRules.startQuality(Quality.FHD, null, Quality.SD, metered = true))
        assertEquals(Quality.FHD, PlaybackRules.startQuality(Quality.FHD, null, null, metered = true))
        // a quality forced by an error wins over both settings
        assertEquals(Quality.SD, PlaybackRules.startQuality(Quality.HD, Quality.SD, Quality.HD, metered = true))
    }

    @Test
    fun spareLink_skipsTriedLinks() {
        val page = mapOf(Quality.HD to listOf("a", "b"))
        assertEquals("b", PlaybackRules.spareLink(page, Quality.HD, setOf("a")))
        assertNull(PlaybackRules.spareLink(page, Quality.HD, setOf("a", "b")))
        assertNull(PlaybackRules.spareLink(page, Quality.FHD, emptySet()))
    }

    @Test
    fun pageStale_afterFiveMinutes() {
        assertTrue(PlaybackRules.pageStale(pageAt = 0L, now = System.currentTimeMillis()))
        assertFalse(PlaybackRules.pageStale(pageAt = 1_000_000L, now = 1_000_000L + 60_000))
        assertTrue(PlaybackRules.pageStale(pageAt = 1_000_000L, now = 1_000_000L + PlaybackRules.PAGE_FRESH_MS + 1))
    }

    @Test
    fun lower_fullHdFallsToHdForThisEpisodeOnly() {
        assertEquals(Fallback.Lower(Quality.HD, "http://v/720/1.mp4", remember = false), PlaybackRules.lower(item, Quality.FHD, swapTried = false))
    }

    @Test
    fun lower_hdAndSdSwapOnceThenFail() {
        assertEquals(Fallback.Lower(Quality.SD, "http://v/1.mp4", remember = true), PlaybackRules.lower(item, Quality.HD, swapTried = false))
        assertEquals(Fallback.Lower(Quality.HD, "http://v/720/1.mp4", remember = true), PlaybackRules.lower(item, Quality.SD, swapTried = false))
        assertEquals(Fallback.Fail, PlaybackRules.lower(item, Quality.SD, swapTried = true))
        // with one file only there is nothing to swap to
        assertEquals(Fallback.Fail, PlaybackRules.lower(item.copy(hd = null), Quality.SD, swapTried = false))
    }

    @Test
    fun countdown_lastTenSecondsWhenThereIsANextEpisode() {
        val dur = 24 * 60_000L
        assertNull(PlaybackRules.countdown(dur - 20_000, dur, hasNext = true))
        assertEquals(10, PlaybackRules.countdown(dur - 10_000, dur, hasNext = true))
        assertEquals(1, PlaybackRules.countdown(dur - 300, dur, hasNext = true))
        assertNull(PlaybackRules.countdown(dur - 5_000, dur, hasNext = false))
        assertNull(PlaybackRules.countdown(25_000, 30_000, hasNext = true)) // a short clip
    }

    @Test
    fun skipIntro_offeredInTheFirstMinutesOfAnEpisode() {
        val dur = 24 * 60_000L
        assertTrue(PlaybackRules.offerSkipIntro(30_000, dur))
        assertFalse(PlaybackRules.offerSkipIntro(PlaybackRules.SKIP_INTRO_UNTIL_MS, dur))
        assertFalse(PlaybackRules.offerSkipIntro(0, dur))
        assertFalse(PlaybackRules.offerSkipIntro(30_000, 5 * 60_000L))
    }
}
