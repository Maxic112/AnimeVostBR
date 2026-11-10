package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogViewTest {

    private fun t(id: Int, name: String, year: String, type: String, genres: List<String>, sum: Int = 0, votes: Int = 0) =
        Title(id = id, title = "$name / En", year = year, type = type, genres = genres, ratingSum = sum, votes = votes)

    private val list = listOf(
        t(1, "Бета", "2024", "ТВ", listOf("драма", "комедия")),
        t(2, "Альфа", "2020", "OVA", listOf("драма")),
        t(3, "Гамма", "2024", "ТВ", listOf("фэнтези"), sum = 45, votes = 10),
        t(4, "Дельта", "2018", "ТВ", listOf("драма"))
    )

    @Test
    fun filtersCombine() {
        assertEquals(listOf(1, 3), CatalogView(type = "ТВ", year = "2024").apply(list, emptyMap()).map { it.id })
        assertEquals(listOf(1, 2, 4), CatalogView(genre = "Драма").apply(list, emptyMap()).map { it.id })
        assertTrue(CatalogView(type = "Фильм").apply(list, emptyMap()).isEmpty())
    }

    @Test
    fun sortOrders() {
        assertEquals(listOf(1, 2, 3, 4), CatalogView().apply(list, emptyMap()).map { it.id })
        assertEquals(listOf(2, 1, 3, 4), CatalogView(sort = CatalogSort.TITLE).apply(list, emptyMap()).map { it.id })
        assertEquals(listOf(4, 2, 1, 3), CatalogView(sort = CatalogSort.OLD_FIRST).apply(list, emptyMap()).map { it.id })
        // fetched ratings first, the card's own rating (3: 4.5) counts too, unknown last in site order
        assertEquals(listOf(3, 4, 1, 2), CatalogView(sort = CatalogSort.RATING).apply(list, mapOf(4 to 4.0)).map { it.id })
    }

    @Test
    fun optionsLeaveOutTheListingsOwnDimension() {
        val (types, years, genres) = CatalogView.options(list, CatalogKind.GENRE)
        assertEquals(listOf("OVA", "ТВ"), types)
        assertEquals(listOf("2024", "2020", "2018"), years)
        assertTrue(genres.isEmpty())
    }

    @Test
    fun summary() {
        assertEquals("ТВ · 2024 · По рейтингу", CatalogView(type = "ТВ", year = "2024", sort = CatalogSort.RATING).summary())
        assertTrue(CatalogView().isDefault)
    }
}
