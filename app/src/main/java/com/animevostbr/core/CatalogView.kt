package com.animevostbr.core

enum class CatalogSort(val label: String) {
    UPDATED("По обновлению"),
    NEW_FIRST("Сначала новые"),
    OLD_FIRST("Сначала старые"),
    TITLE("По названию"),
    RATING("По рейтингу")
}

/**
  * Extra filters and order on top of a site listing (genre, type or year). The site sorts listings only
  * in its desktop theme, which the app does not parse, so this runs over the loaded titles.
  * Null filter = any.
  */
data class CatalogView(
    val type: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val sort: CatalogSort = CatalogSort.UPDATED
) {
    val isDefault: Boolean get() = this == CatalogView()

    /** Short line for the screen header, e.g. "ТВ · 2024 · По рейтингу". */
    fun summary(): String = listOfNotNull(
        type, year, genre, sort.takeIf { it != CatalogSort.UPDATED }?.label
    ).joinToString(" · ")

    /** [ratings]: average by title id; site cards have none, the screen fetches them for the rating order. */
    fun apply(titles: List<Title>, ratings: Map<Int, Double>): List<Title> {
        val shown = titles.filter { t ->
            (type == null || t.type.equals(type, ignoreCase = true)) &&
                (year == null || t.year == year) &&
                (genre == null || t.genres.any { it.equals(genre, ignoreCase = true) })
        }
        return when (sort) {
            CatalogSort.UPDATED -> shown
            CatalogSort.NEW_FIRST -> shown.sortedByDescending { it.year.toIntOrNull() ?: 0 }
            CatalogSort.OLD_FIRST -> shown.sortedBy { it.year.toIntOrNull() ?: Int.MAX_VALUE }
            CatalogSort.TITLE -> shown.sortedBy { TitleParts.of(it.title).ru.lowercase() }
            // titles without a known rating go last, in their site order
            CatalogSort.RATING -> shown.sortedByDescending { ratings[it.id] ?: (if (it.votes > 0) it.average else -1.0) }
        }
    }

    companion object {
        /** Values to choose from, as found in the loaded titles; the listing's own dimension is left out. */
        fun options(titles: List<Title>, kind: CatalogKind): Triple<List<String>, List<String>, List<String>> {
            val types = if (kind == CatalogKind.TYPE) emptyList() else titles.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
            val years = if (kind == CatalogKind.YEAR) emptyList() else titles.map { it.year }.filter { it.isNotBlank() }.distinct().sortedDescending()
            val genres = if (kind == CatalogKind.GENRE) emptyList() else titles.flatMap { it.genres }.map { it.trim().lowercase() }
                .filter { it.isNotBlank() }.distinct().sorted()
            return Triple(types, years, genres)
        }
    }
}
