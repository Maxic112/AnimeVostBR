package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog listings (genre, type, year) from the site. */
class CatalogTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
            .readText(Charsets.UTF_8)

    @Test
    fun genrePage_parsesTitles() {
        val list = SiteSource.parseFeed(fixture("site_genre_drama.html"), "https://animevost.org")
        assertEquals(10, list.size)
        val first = list.first()
        assertEquals(4064, first.id)
        assertEquals("2026", first.year)
        assertEquals("ТВ", first.type)
        assertTrue(first.poster.startsWith("https://animevost.org/uploads/"))
        assertTrue(list.all { "драма" in it.genres })
    }

    @Test
    fun filters_buildSitePaths() {
        assertEquals("/zhanr/drama/", Catalog.find(CatalogKind.GENRE, "drama").path)
        assertEquals("Драма", Catalog.find(CatalogKind.GENRE, "drama").name)
        assertEquals("/tip/ova/", Catalog.find(CatalogKind.TYPE, "ova").path)
        assertEquals("/god/2024/", Catalog.find(CatalogKind.YEAR, "2024").path)
        assertEquals(27, Catalog.genres.size)
        assertEquals(7, Catalog.types.size)
        assertTrue(Catalog.years.first().slug.toInt() >= 2026)
    }
}
