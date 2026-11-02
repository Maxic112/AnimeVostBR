package com.animevostbr.core

import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

/**
  * Fallback data source: HTML of the site or its mirror (DataLife Engine) when the API fails.
  *
  *  - feed: `<article class="post" style="background-image:url('/uploads/…')">` with a link
  *    `/tip/<type>/<id>-<slug>.html`, the title in `<h2>` and categories (`/god/2026/`, `/zhanr/…`);
  *  - title: `/index.php?newsid=<id>` (no slug needed); the player script contains
  *    `var data = {"<episode name>":"<video id>", …}` with the same video ids as the API.
  */
object SiteSource {

    private const val VIDEO = "http://video.animetop.info"
    private const val PREVIEW = "http://media.animetop.info/img"

    private val ID_RE = Regex("""/(\d+)-[^/]*\.html""")
    private val BG_RE = Regex("""url\(\s*['"]?([^'")]+)""")
    private val YEAR_RE = Regex("""/god/(\d{4})/""")
    private val DATA_RE = Regex("""var\s+data\s*=\s*\{([^}]*)\}""")
    private val PAIR_RE = Regex(""""([^"]+)"\s*:\s*"(\d+)"""")
    private val TIMER_RE = Regex("""parseInt\(\s*(\d{9,})\s*-""")

    /** The title page serves both the card and the episodes, so keep it for a couple of minutes. */
    private val pageCache = ConcurrentHashMap<Int, Pair<Long, Pair<String, String>>>()
    private const val PAGE_TTL_MS = 2 * 60 * 1000L


    /** Feed page (1 = home). Returns the site base it came from and the titles. */
    fun feed(page: Int): Pair<String, List<Title>> {
        val (base, html) = Sites.fetch(if (page <= 1) "/" else "/page/$page/")
        return base to parseFeed(html, base)
    }

    fun title(id: Int): Title? {
        val (base, html) = titlePage(id)
        return parseTitle(html, base, id)
    }

    fun playlist(id: Int): List<PlaylistItem> = parsePlaylist(titlePage(id).second)

    private fun titlePage(id: Int): Pair<String, String> {
        pageCache[id]?.let { (at, page) -> if (System.currentTimeMillis() - at < PAGE_TTL_MS) return page }
        val page = Sites.fetch("/index.php?newsid=$id")
        pageCache[id] = System.currentTimeMillis() to page
        return page
    }


    fun parseFeed(html: String, base: String): List<Title> {
        val doc = Jsoup.parse(html, base)
        return doc.select("article.post").mapNotNull { art ->
            val link = art.selectFirst("a[href*=/tip/]")?.attr("abs:href") ?: return@mapNotNull null
            val id = ID_RE.find(link)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val name = art.selectFirst("h2")?.text()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val poster = BG_RE.find(art.attr("style"))?.groupValues?.get(1)?.let { absolute(base, it) }.orEmpty()
            val cats = art.select(".short-categori a").toList() // toList(): Elements has its own filter(NodeFilter)
            Title(
                id = id,
                title = name,
                poster = poster,
                year = cats.firstNotNullOfOrNull { YEAR_RE.find(it.attr("href"))?.groupValues?.get(1) }.orEmpty(),
                type = cats.firstOrNull { it.attr("href").contains("/tip/") }?.text().orEmpty(),
                genres = cats.filter { it.attr("href").contains("/zhanr/") }.map { it.text().lowercase() } // lowercase like the API
            )
        }
    }

    fun parseTitle(html: String, base: String, id: Int): Title? {
        val doc = Jsoup.parse(html, base)
        val info = doc.selectFirst(".infoContent") ?: return null
        val name = info.selectFirst("h3")?.text()?.trim().orEmpty().ifEmpty { return null }
        val fields = info.select("p").associate { p ->
            val label = p.selectFirst("strong")?.text()?.trim()?.removeSuffix(":").orEmpty()
            label to p.text().substringAfter(":").trim()
        }
        val series = parseSeries(html)
        val total = fields["Количество серий"]?.substringBefore(" ")?.trim()
        // API titles include a "[1-5 of N]" suffix; the site omits it, so rebuild it
        val planned = total?.filter { it.isDigit() }?.toIntOrNull()
        val tag = when {
            series.isEmpty() -> ""
            total != null && planned != null && planned >= series.size -> " [1-${series.size} из $total]"
            else -> " [1-${series.size}]"
        }
        return Title(
            id = id,
            title = name + tag,
            description = info.select("p").firstOrNull { it.text().startsWith("Описание") }
                ?.html()?.substringAfter("</strong>")?.trim().orEmpty(),
            genres = fields["Жанр"].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() },
            year = fields["Год выхода"].orEmpty(),
            poster = info.selectFirst("img")?.attr("src")?.let { absolute(base, it) }.orEmpty(),
            type = fields["Тип"].orEmpty(),
            director = fields["Режиссёр"].orEmpty(),
            series = series,
            timer = TIMER_RE.find(html)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
        )
    }

    fun parsePlaylist(html: String): List<PlaylistItem> =
        parseSeries(html).entries.mapIndexed { i, (name, vid) ->
            PlaylistItem(
                index = i + 1,
                label = name,
                hd = "$VIDEO/720/$vid.mp4",
                sd = "$VIDEO/$vid.mp4",
                preview = "$PREVIEW/$vid.jpg"
            )
        }

    /** Parses `var data = {...}` into an ordered map of episode name → video id. */
    fun parseSeries(html: String): Map<String, String> {
        val body = DATA_RE.find(html)?.groupValues?.get(1) ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        for (m in PAIR_RE.findAll(body)) result[m.groupValues[1].trim()] = m.groupValues[2]
        return result
    }

    private fun absolute(base: String, url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        else -> base.trimEnd('/') + "/" + url.trimStart('/')
    }
}
