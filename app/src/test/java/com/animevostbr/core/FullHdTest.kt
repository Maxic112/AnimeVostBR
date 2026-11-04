package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Full HD comes from the site player page, not from the playlist. */
class FullHdTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
            .readText(Charsets.UTF_8)

    @Test
    fun playerPage_givesFullHdLink() {
        assertEquals(
            "https://fhd-a.example/1080/1000000001.mp4?md5=TOKEN&time=1793803633",
            SiteSource.parseFullHd(fixture("site_player_fhd.html"))
        )
    }

    @Test
    fun spareTemplate_doesNotCount() {
        assertNull(SiteSource.parseFullHd(fixture("site_player_no_fhd.html")))
    }

    @Test
    fun videoId_comesFromPlaylistLinks() {
        val item = PlaylistItem(1, "1 серия", "http://host/720/747648616.mp4", "http://host/747648616.mp4")
        assertEquals("747648616", item.videoId)
        assertEquals("747648616", item.copy(sd = null).videoId)
    }

    @Test
    fun qualityButton_cyclesThroughAll() {
        assertEquals(Quality.HD, Quality.FHD.next())
        assertEquals(Quality.SD, Quality.HD.next())
        assertEquals(Quality.FHD, Quality.SD.next())
    }
}
