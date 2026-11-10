package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The API lives on two domains; an ISP block page or a dead server must not count as an answer. */
class ApiHostsTest {

    @Test
    fun jsonIsAnAnswer_evenNotFound() {
        assertTrue(ApiClient.isApiAnswer(200, "application/json; charset=utf-8"))
        assertTrue(ApiClient.isApiAnswer(404, "application/json")) // search: nothing found
    }

    @Test
    fun blockPagesAndServerErrorsAreNot() {
        assertFalse(ApiClient.isApiAnswer(200, "text/html; charset=utf-8")) // operator stub page
        assertFalse(ApiClient.isApiAnswer(451, "text/html"))
        assertFalse(ApiClient.isApiAnswer(502, "application/json"))
        assertFalse(ApiClient.isApiAnswer(200, null))
    }

    @Test
    fun posters_haveSiteAndMirrorFallbacks() {
        val url = "https://static.openni.ru/uploads/posts/2026-10/1_1.jpg"
        assertEquals("http://static.openni.ru/uploads/posts/2026-10/1_1.jpg", TitlesParser.posterUrl(url))
        assertEquals(
            listOf(
                "https://v14.vost.pw/uploads/posts/2026-10/1_1.jpg",
                "https://animevost.org/uploads/posts/2026-10/1_1.jpg"
            ),
            TitlesParser.posterFallbacks(url, "v14.vost.pw")
        )
        assertTrue(TitlesParser.posterFallbacks("https://example.com/a.jpg", "v14.vost.pw").isEmpty())
    }

    @Test
    fun animetopGoesFirst() {
        assertEquals("api.animetop.info", ApiClient.API_HOSTS.first())
        assertTrue("api.animevost.org" in ApiClient.API_HOSTS)
    }
}
