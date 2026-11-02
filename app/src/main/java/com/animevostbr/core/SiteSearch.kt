package com.animevostbr.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
  * Full-text search of the site (DataLife Engine), used when the API substring search finds
  * nothing. Sites are tried according to the site setting ([Sites]).
  */
object SiteSearch {

    private val NEWS_URL = Regex("""/tip/[^/]+/(\d+)-[^/]+\.html""")

    suspend fun search(query: String, page: Int = 1): List<Title> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        for (base in Sites.bases()) {
            try {
                val url = buildString {
                    append(base)
                    append("/index.php?do=search&subaction=search&search_start=")
                    append(page)
                    append("&story=")
                    append(URLEncoder.encode(trimmed, "UTF-8"))
                }
                val doc = Jsoup.connect(url)
                    .userAgent(ApiClient.UA)
                    .timeout(20000)
                    .maxBodySize(0)
                    .get()
                val found = parseResults(doc, base)
                if (found.isNotEmpty()) return@withContext found
            } catch (e: Exception) {
                // try the next site
            }
        }
        emptyList()
    }

    private fun parseResults(doc: Document, base: String): List<Title> {
        val result = mutableListOf<Title>()
        val seen = mutableSetOf<Int>()
        for (a in doc.select("a[href]")) {
            var href = a.attr("abs:href")
            if (href.isBlank()) href = a.attr("href")
            val m = NEWS_URL.find(href) ?: continue
            val id = m.groupValues[1].toIntOrNull() ?: continue
            if (!seen.add(id)) continue

            val img = a.selectFirst("img")
            var titleText = img?.attr("alt")?.trim().orEmpty()
            if (titleText.isEmpty()) titleText = a.attr("title").trim()
            if (titleText.isEmpty()) titleText = a.selectFirst("h2, h3, h4")?.text()?.trim().orEmpty()
            if (titleText.isEmpty()) titleText = a.text().trim()
            if (titleText.isEmpty()) continue

            var poster = img?.attr("abs:src").orEmpty()
            if (poster.isEmpty()) poster = img?.attr("src").orEmpty()
            if (poster.isNotEmpty() && !poster.startsWith("http")) {
                poster = base + poster
            }

            result += Title.minimal(id, titleText, poster)
            if (result.size >= 60) break
        }
        return result
    }
}
