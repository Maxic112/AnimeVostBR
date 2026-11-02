package com.animevostbr.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class SiteMode { AUTO, MAIN, MIRROR }

/**
  * The AnimeVost site and its mirror.
  *
  * The catalogue and videos come from the API (api.animevost.org), which does not depend on the
  * site. The site serves full-text search and the HTML fallback ([SiteSource]) when the API fails.
  * The main domain gets blocked, so there is a mirror vN.vost.pw whose number changes over time
  * (v13 → v14 → …); [refreshMirror] finds the current one.
  */
object Sites {

    const val MAIN_HOST = "animevost.org"
    const val DEFAULT_MIRROR = "v14.vost.pw"

    /** Automatic checks run at most every 6 hours; manual checks any time. */
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val MIRROR_RE = Regex("""\b(v(\d{1,3})\.vost\.pw)\b""", RegexOption.IGNORE_CASE)

    private val http by lazy {
        ApiClient.ok.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val mode: SiteMode get() = SiteMode.entries.getOrElse(Repo.prefs.siteMode) { SiteMode.AUTO }
    val mirrorHost: String get() = Repo.prefs.mirrorHost

    /** Auto mode: the main site failed, so following requests go to the mirror first. */
    @Volatile
    private var autoPreferMirror = false

    fun bases(): List<String> {
        val main = "https://$MAIN_HOST"
        val mirror = "https://$mirrorHost"
        return when (mode) {
            SiteMode.MAIN -> listOf(main)
            SiteMode.MIRROR -> listOf(mirror)
            SiteMode.AUTO -> if (autoPreferMirror) listOf(mirror, main) else listOf(main, mirror)
        }
    }

    /** GET with fallback across [bases]. Returns (site base, HTML). */
    fun fetch(path: String): Pair<String, String> {
        var last: Exception? = null
        for (base in bases()) {
            try {
                val html = get(base + path)
                if (mode == SiteMode.AUTO) autoPreferMirror = !base.endsWith(MAIN_HOST)
                return base to html
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Сайт недоступен")
    }

    /** Plain GET: anything but 200 throws. */
    fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", ApiClient.UA).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }


    data class MirrorCheck(
        val host: String,
        val changed: Boolean,
        /** Where the host was found: redirect, main page, mirror page, RSS or guess. */
        val source: String?,
        val alive: Boolean
    )

    /** v14.vost.pw → 14. */
    fun mirrorNumber(host: String): Int = MIRROR_RE.find(host)?.groupValues?.get(2)?.toIntOrNull() ?: 0

    fun mirrorsIn(text: String): List<String> =
        MIRROR_RE.findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()

    /** A live AnimeVost (DLE) page rather than a registrar placeholder. */
    fun looksLikeSite(html: String): Boolean =
        html.contains("animevost", ignoreCase = true) && html.contains("/tip/", ignoreCase = true)

    /**
      * Finds the current mirror and stores it. Without [force] runs at most every [CHECK_INTERVAL_MS];
      * returns null when the check was skipped.
      */
    suspend fun refreshMirror(force: Boolean = false): MirrorCheck? = withContext(Dispatchers.IO) {
        val prefs = Repo.prefs
        if (!force && System.currentTimeMillis() - prefs.mirrorCheckedAt < CHECK_INTERVAL_MS) {
            return@withContext null
        }
        val current = prefs.mirrorHost
        val found = linkedMapOf<String, String>() // host -> where it was found
        fun add(hosts: List<String>, source: String) = hosts.forEach { found.putIfAbsent(it, source) }

        // 1) The old mirror address redirects to the new one (that is how the site moves: v13 → v14)
        runCatching {
            val request = Request.Builder().url("https://$current/").header("User-Agent", ApiClient.UA).build()
            http.newCall(request).execute().use { r -> add(mirrorsIn(r.request.url.host), "редирект") }
        }
        // 2) The main site links the mirror on its home page and on /zerkalo.html
        runCatching { add(mirrorsIn(get("https://$MAIN_HOST/")), "главная $MAIN_HOST") }
        runCatching { add(mirrorsIn(get("https://$MAIN_HOST/zerkalo.html")), "страница «Зеркало»") }
        // 3) Every link in the mirror's RSS points to its current address
        runCatching { add(mirrorsIn(get("https://$current/rss.xml")), "RSS") }
        // 4) Next numbers, in case the main site is blocked and there is no redirect
        val n = mirrorNumber(current)
        if (n > 0) add((n + 1..n + 3).map { "v$it.vost.pw" }, "перебор")
        found.putIfAbsent(current, "сохранённый")

        // Validate all candidates in parallel (nonexistent domains wait for a timeout)
        // and pick the newest live one (highest number)
        val alive = coroutineScope {
            found.keys.map { host ->
                async { host.takeIf { runCatching { looksLikeSite(get("https://$host/")) }.getOrDefault(false) } }
            }.awaitAll()
        }.filterNotNull().maxByOrNull { mirrorNumber(it) }

        prefs.mirrorCheckedAt = System.currentTimeMillis()
        if (alive != null) {
            prefs.mirrorHost = alive
            prefs.mirrorSource = found[alive].orEmpty()
        }
        MirrorCheck(
            host = alive ?: current,
            changed = alive != null && alive != current,
            source = alive?.let { found[it] },
            alive = alive != null
        )
    }
}
