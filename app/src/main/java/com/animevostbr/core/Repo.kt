package com.animevostbr.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
  * Single data entry point: JSON API → HTML site fallback, with an in-memory cache.
  */
object Repo {

    lateinit var prefs: Prefs
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(context.applicationContext)
    }

    private val cache = ConcurrentHashMap<Int, Title>()
    @Volatile
    private var lastList: List<Title> = emptyList()
    @Volatile
    private var lastPage = 0
    @Volatile
    private var lastEnd = false

    val lastCached: List<Title> get() = lastList

    /** Whether the last first-page load succeeded (refresh: no network ≠ nothing new). */
    @Volatile
    var lastFetchOk = true
        private set

    /**
      * Where the feed came from: null — the API, otherwise the site/mirror host used by the
      * [SiteSource] fallback after the API failed.
      */
    @Volatile
    var feedFromSite: String? = null
        private set

    val lastExhausted: Boolean get() = lastEnd

    /** First feed page; resets previously loaded pages. */
    suspend fun fetchLast(): List<Title> = withContext(Dispatchers.IO) {
        try {
            val list = fetchLastPage(1)
            lastFetchOk = list.isNotEmpty()
            if (list.isNotEmpty()) {
                lastList = list
                lastPage = 1
            }
            lastList
        } catch (e: Exception) {
            lastFetchOk = false
            lastList // offline/failure: keep the previous feed
        }
    }

    /** Loads the next feed page and returns the whole accumulated feed. */
    suspend fun loadMoreLast(): List<Title> = withContext(Dispatchers.IO) {
        if (lastEnd || lastPage == 0) return@withContext lastList
        try {
            val page = fetchLastPage(lastPage + 1)
            val known = lastList.mapTo(HashSet()) { it.id }
            lastList = lastList + page.filter { it.id !in known }
            lastPage++
        } catch (e: Exception) {
            // network failure: retry on the next scroll
        }
        lastList
    }

    /** Feed page from the API, or from the site/mirror if the API fails. Sets [lastEnd]. */
    private suspend fun fetchLastPage(page: Int): List<Title> {
        val fromApi = runCatching { TitlesParser.parseList(ApiClient.api.last(page).string()) }.getOrNull()
        val list = if (!fromApi.isNullOrEmpty()) {
            feedFromSite = null
            lastEnd = fromApi.size < AnimeVostApi.LAST_PAGE_SIZE
            fromApi
        } else {
            val (base, fromSite) = SiteSource.feed(page)
            feedFromSite = base.substringAfter("://")
            lastEnd = fromSite.isEmpty()
            fromSite
        }
        list.forEach { cache.putIfAbsent(it.id, it) }
        return list
    }

    fun cachedTitle(id: Int): Title? = cache[id]

    suspend fun titleById(id: Int): Title? {
        // partial cards (site feed, site search) have no description — fetch the full one
        cache[id]?.takeIf { it.description.isNotEmpty() }?.let { return it }
        return withContext(Dispatchers.IO) {
            val fromApi = runCatching {
                TitlesParser.parseList(ApiClient.api.info(id.toString()).string()).firstOrNull()
            }.getOrNull()
            (fromApi ?: runCatching { SiteSource.title(id) }.getOrNull() ?: cache[id])
                ?.also { cache[it.id] = it }
        }
    }

    suspend fun playlist(id: Int): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val fromApi = runCatching { PlaylistParser.parse(ApiClient.api.playlist(id.toString()).string()) }
            .getOrDefault(emptyList())
        fromApi.ifEmpty { runCatching { SiteSource.playlist(id) }.getOrDefault(emptyList()) }
    }

    /** Null when the episode has no Full HD or the site cannot be reached. */
    suspend fun fullHdUrl(item: PlaylistItem): String? = withContext(Dispatchers.IO) {
        item.videoId?.let { id -> runCatching { SiteSource.fullHd(id) }.getOrNull() }
    }

    /** One page of a catalog listing; an empty list past the last page. Throws on network errors. */
    suspend fun catalogPage(filter: CatalogFilter, page: Int): List<Title> = withContext(Dispatchers.IO) {
        val list = try {
            SiteSource.listing(filter.path, page)
        } catch (e: Sites.HttpStatusException) {
            if (e.code == 404) emptyList() else throw e
        }
        list.forEach { cache.putIfAbsent(it.id, it) }
        list
    }

    suspend fun search(query: String): List<Title> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        // 1) API: substring search over Russian and English titles
        try {
            val raw = withContext(Dispatchers.IO) { ApiClient.api.search(q).string() }
            val list = TitlesParser.parseList(raw)
            if (list.isNotEmpty()) {
                list.forEach { cache[it.id] = it }
                return list
            }
        } catch (e: Exception) {
            // HTTP 404 means nothing found; either way try the site
        }

        // 2) Fallback: the site's full-text search finds what the API substring match misses
        //    (punctuation, word order)
        val web = SiteSearch.search(q)
        web.forEach { cache[it.id] = it }
        return web
    }

    /**
      * Typing suggestions: API only (fast, no site fallback). The API matches a substring
      * anywhere in the title, so results are re-ranked: titles starting with the query first,
      * then matches at the start of a word.
      */
    suspend fun suggest(query: String, limit: Int = 8): List<Title> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val list = try {
            withContext(Dispatchers.IO) { TitlesParser.parseList(ApiClient.api.search(q).string()) }
        } catch (e: Exception) {
            return emptyList() // 404 (nothing found) or no network
        }
        list.forEach { cache[it.id] = it }
        return list.sortedBy { suggestRank(it.title, q) }.take(limit)
    }

    fun favorites(): List<TitleLite> = prefs.favorites().toList()
    fun isFavorite(id: Int): Boolean = prefs.isFavorite(id)
    fun toggleFavorite(lite: TitleLite): Boolean = prefs.toggleFavorite(lite)

    fun progress(id: Int): Progress? = prefs.progress(id)
    fun allProgress(): List<Progress> = prefs.allProgress()
    fun saveProgress(p: Progress) = prefs.saveProgress(p)
    fun removeProgress(id: Int) = prefs.removeProgress(id)
    fun clearProgress() = prefs.clearProgress()

    fun seenMap(): Map<Int, Int> = prefs.seenMap()
    fun saveSeen(map: Map<Int, Int>) = prefs.saveSeen(map)
}

/** 0 — the Russian or English title starts with the query, 1 — a word starts with it, 2 — other. */
internal fun suggestRank(title: String, query: String): Int {
    val q = query.trim()
    return when {
        title.split(" / ").any { it.trim().startsWith(q, ignoreCase = true) } -> 0
        Regex("""(^|[\s\-:«"(.])""" + Regex.escape(q), RegexOption.IGNORE_CASE).containsMatchIn(title) -> 1
        else -> 2
    }
}
