package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
  * Parsers against real API and site responses (test resources under fixtures/).
  */
class ParsersTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
            .readText(Charsets.UTF_8)


    @Test
    fun lastPage_parsesAllTitles() {
        val list = TitlesParser.parseList(fixture("last_page1_q20.json"))
        assertEquals(20, list.size)
        val first = list.first()
        assertEquals(4032, first.id)
        assertEquals(5, first.episodesOut)
        assertEquals(listOf("1 серия", "2 серия", "3 серия", "4 серия", "5 серия"), first.series.keys.toList())
        assertEquals(12, first.totalEpisodes())
        // static.openni.ru has an expired HTTPS certificate — posters go over http
        assertEquals("http://static.openni.ru/uploads/posts/2026-09/1790061699_1.jpg", first.poster)
        assertTrue(first.stars in 1..5)
    }

    @Test
    fun lastWithoutParams_returnsSingleTitle() {
        // hence Api.last() always sends page and quantity
        assertEquals(1, TitlesParser.parseList(fixture("last_noparams.json")).size)
    }

    @Test
    fun info_parsesSingleTitle() {
        val t = TitlesParser.parseSingle(fixture("info_4032.json"))
        assertEquals(4032, t?.id)
    }

    @Test
    fun search_parsesResults() {
        val list = TitlesParser.parseList(fixture("search_name_naruto.json"))
        assertEquals(22, list.size)
        assertTrue(list.all { it.title.contains("Наруто") || it.title.contains("Naruto", ignoreCase = true) })
        // fully released title: timer = 0 means no next-episode date
        assertEquals(null, list.first().timer)
    }

    @Test
    fun searchNotFound_isEmpty() {
        assertTrue(TitlesParser.parseList(fixture("search_notfound_404.json")).isEmpty())
    }

    @Test
    fun parseSeries_keepsAllEpisodeNamesInOrder() {
        val series = TitlesParser.parseSeries("{'1 серия':'11','215-216 серия':'22','ОВА 1':'33','Фильм':'44'}")
        assertEquals(listOf("1 серия", "215-216 серия", "ОВА 1", "Фильм"), series.keys.toList())
        assertEquals("44", series["Фильм"])
    }


    @Test
    fun playlist_readsHdAndStd() {
        val items = PlaylistParser.parse(fixture("playlist_4032.json"))
        assertEquals(5, items.size)
        val first = items.first()
        assertEquals(1, first.index)
        assertEquals("1 серия", first.label)
        assertEquals("http://video.animetop.info/720/747648616.mp4", first.url(Quality.HD))
        assertEquals("http://video.animetop.info/747648616.mp4", first.url(Quality.SD))
        assertNotEquals(first.url(Quality.HD), first.url(Quality.SD))
        assertEquals("http://media.animetop.info/img/747648616.jpg", first.preview)
    }

    @Test
    fun playlist_matchesSeriesFromFeed() {
        val title = TitlesParser.parseList(fixture("last_page1_q20.json")).first { it.id == 4032 }
        val items = PlaylistParser.parse(fixture("playlist_4032.json"))
        assertEquals(title.series.keys.toList(), items.map { it.label })
        assertEquals(title.episodesOut, items.size)
    }

    @Test
    fun playlist_indexIsPositionNotNumberInName() {
        val ova = PlaylistParser.parse(fixture("playlist_2491_ova.json"))
        assertSequential(ova)
        assertEquals("ОВА 4", ova.last().label)

        val excerpt = PlaylistParser.parse(fixture("playlist_179_excerpt.json"))
        assertSequential(excerpt)
        assertTrue(excerpt.any { it.label == "215-216 серия" })
        assertTrue(excerpt.any { it.label == "492(1) серия" })

        val ranges = PlaylistParser.parse(fixture("playlist_2722_ranges.json"))
        assertSequential(ranges)
        assertEquals("1-40 серии", ranges.first().label)
    }

    @Test
    fun playlist_singleFilm() {
        val items = PlaylistParser.parse(fixture("playlist_4062_film.json"))
        assertEquals(1, items.size)
        assertEquals("Фильм", items.single().label)
        assertEquals(1, items.single().index)
    }

    @Test
    fun playlist_garbageIsEmpty() {
        assertTrue(PlaylistParser.parse("<html>500</html>").isEmpty())
        assertTrue(PlaylistParser.parse("""{"error":"Ничего не найдено"}""").isEmpty())
    }

    private fun assertSequential(items: List<PlaylistItem>) {
        assertEquals((1..items.size).toList(), items.map { it.index })
    }

    @Test
    fun suggestRank_prefersTitlesStartingWithQuery() {
        val titles = listOf(
            "Инари в нашем доме / Wagaya no Oinari-sama",
            "Семинар Извращенцев / Hen Semi",
            "Наруто / Naruto [1-220 из 220]",
            "Боруто: Новое поколение Наруто / Boruto"
        )
        val ranked = titles.sortedBy { suggestRank(it, "нар") }
        assertEquals("Наруто / Naruto [1-220 из 220]", ranked[0])
        assertEquals("Боруто: Новое поколение Наруто / Boruto", ranked[1])
        assertEquals(0, suggestRank("Ван Пис / One Piece", "one"))
    }

    @Test
    fun titleParts_splitsNamesAndTags() {
        val p = TitleParts.of("Магия вернувшегося (второй сезон) / Kikansha no Mahou 2nd Season [1-4 из 12+] [5 серия - 4 ноября]")
        assertEquals("Магия вернувшегося (второй сезон)", p.ru)
        assertEquals("Kikansha no Mahou 2nd Season", p.en)
        assertEquals(listOf("1-4 из 12+", "5 серия - 4 ноября"), p.tags)

        val noEn = TitleParts.of("Фильм без перевода [1 из 1]")
        assertEquals("Фильм без перевода", noEn.ru)
        assertEquals("", noEn.en)
        assertEquals(listOf("1 из 1"), noEn.tags)
    }


    @Test
    fun siteFeed_parsesArticles() {
        val list = SiteSource.parseFeed(fixture("site_main_v14.html"), "https://v14.vost.pw")
        assertEquals(10, list.size)
        val t = list.first { it.id == 4032 }
        assertTrue(t.title.startsWith("Я очнулся будучи пилотом"))
        assertEquals("https://v14.vost.pw/uploads/posts/2026-09/1790061699_1.jpg", t.poster)
        assertEquals("2026", t.year)
        assertEquals("ТВ", t.type)
        assertTrue("приключения" in t.genres)
    }

    @Test
    fun siteTitle_parsesInfoAndSeries() {
        val html = fixture("site_title_4032.html")
        val t = SiteSource.parseTitle(html, "https://v14.vost.pw", 4032)!!
        assertTrue(t.title.endsWith("[1-5 из 12+]"))
        assertEquals("2026", t.year)
        assertEquals("ТВ", t.type)
        assertEquals("Нагахама Норихико", t.director)
        assertEquals(5, t.episodesOut)
        assertEquals(1794163708L, t.timer)
        assertTrue(t.description.startsWith("Самый обычный офисный сотрудник"))
    }

    @Test
    fun sitePlaylist_matchesApi() {
        val fromSite = SiteSource.parsePlaylist(fixture("site_title_4032.html"))
        val fromApi = PlaylistParser.parse(fixture("playlist_4032.json"))
        assertEquals(fromApi.map { it.label }, fromSite.map { it.label })
        assertEquals(fromApi.map { it.hd }, fromSite.map { it.hd })
        assertEquals(fromApi.map { it.sd }, fromSite.map { it.sd })
    }

    @Test
    fun mirror_foundOnSitePages() {
        assertEquals(listOf("v14.vost.pw"), Sites.mirrorsIn(fixture("site_zerkalo.html")))
        assertEquals(14, Sites.mirrorNumber("v14.vost.pw"))
        assertEquals(0, Sites.mirrorNumber("plg.vost.pw"))
        assertTrue(Sites.looksLikeSite(fixture("site_main_v14.html")))
    }
}
