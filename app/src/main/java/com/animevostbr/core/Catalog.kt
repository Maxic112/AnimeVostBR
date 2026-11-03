package com.animevostbr.core

import java.util.Calendar

enum class CatalogKind(val segment: String, val label: String) {
    GENRE("zhanr", "Жанр"),
    TYPE("tip", "Тип"),
    YEAR("god", "Год")
}

/** One site listing: `/zhanr/drama/`, `/tip/ova/`, `/god/2024/`. */
data class CatalogFilter(val kind: CatalogKind, val slug: String, val name: String) {
    val path: String get() = "/${kind.segment}/$slug/"
}

/**
  * Catalog browsing by genre, type and year. The API has no such queries, so listings come from
  * the site; the lists mirror the site menu (slugs are transliterated names).
  */
object Catalog {

    val genres: List<CatalogFilter> = listOf(
        "boyevyye-iskusstva" to "Боевые искусства",
        "voyna" to "Война",
        "drama" to "Драма",
        "detektiv" to "Детектив",
        "istoriya" to "История",
        "komediya" to "Комедия",
        "mekha" to "Меха",
        "mistika" to "Мистика",
        "makho-sedze" to "Махо-сёдзё",
        "muzykalnyy" to "Музыкальный",
        "povsednevnost" to "Повседневность",
        "priklyucheniya" to "Приключения",
        "parodiya" to "Пародия",
        "romantika" to "Романтика",
        "senen" to "Сёнэн",
        "sedze" to "Сёдзё",
        "sport" to "Спорт",
        "skazka" to "Сказка",
        "sedze-ay" to "Сёдзё-ай",
        "senen-ay" to "Сёнэн-ай",
        "samurai" to "Самураи",
        "triller" to "Триллер",
        "uzhasy" to "Ужасы",
        "fantastika" to "Фантастика",
        "fentezi" to "Фэнтези",
        "shkola" to "Школа",
        "etti" to "Этти"
    ).map { (slug, name) -> CatalogFilter(CatalogKind.GENRE, slug, name) }.sortedBy { it.name }

    val types: List<CatalogFilter> = listOf(
        "tv" to "ТВ",
        "tv-speshl" to "ТВ-спэшл",
        "ova" to "OVA",
        "ona" to "ONA",
        "polnometrazhnyy-film" to "Полнометражный фильм",
        "korotkometrazhnyy-film" to "Короткометражный фильм",
        "dunkhua" to "Дунхуа"
    ).map { (slug, name) -> CatalogFilter(CatalogKind.TYPE, slug, name) }

    /** Newest first; before 1991 the site has only a few years. */
    val years: List<CatalogFilter>
        get() {
            val current = maxOf(Calendar.getInstance().get(Calendar.YEAR), 2026)
            return ((current downTo 1991) + listOf(1984, 1980, 1977, 1971))
                .map { CatalogFilter(CatalogKind.YEAR, it.toString(), it.toString()) }
        }

    fun find(kind: CatalogKind, slug: String): CatalogFilter = when (kind) {
        CatalogKind.GENRE -> genres.firstOrNull { it.slug == slug }
        CatalogKind.TYPE -> types.firstOrNull { it.slug == slug }
        CatalogKind.YEAR -> null
    } ?: CatalogFilter(kind, slug, slug)
}
