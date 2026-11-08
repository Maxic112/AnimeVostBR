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
    fun playerPage_givesLinksOfEveryQuality() {
        val links = SiteSource.parsePlayerLinks(fixture("site_player_fhd.html"))
        // the Full HD list also names 720p and 480p backups: only the 1080 file counts
        assertEquals(listOf("https://fhd-a.example/1080/1000000001.mp4?md5=TOKEN&time=1793803633"), links[Quality.FHD])
        assertEquals(listOf("https://hd-a.example/720/1000000001.mp4?md5=TOKEN&time=1793803633"), links[Quality.HD])
        assertEquals(
            listOf(
                "https://sd-a.example/1000000001.mp4?md5=TOKEN&time=1793803633",
                "https://sd-b.example/1000000001.mp4?md5=TOKEN&time=1793803633"
            ),
            links[Quality.SD]
        )
    }

    @Test
    fun spareTemplate_doesNotCount() {
        val links = SiteSource.parsePlayerLinks(fixture("site_player_no_fhd.html"))
        assertNull(links[Quality.FHD])
        assertEquals(1, links[Quality.HD]?.size)
    }

    @Test
    fun averageRating_isSumOverVotes() {
        assertEquals(4.25, Title(id = 1, title = "t", ratingSum = 17, votes = 4).average, 0.001)
        assertEquals(0.0, Title(id = 1, title = "t").average, 0.0)
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
