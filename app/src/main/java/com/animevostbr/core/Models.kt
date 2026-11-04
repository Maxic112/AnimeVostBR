package com.animevostbr.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.roundToInt

enum class Quality(val label: String) {
    FHD("Full HD"), HD("HD"), SD("SD");

    fun next(): Quality = entries[(ordinal + 1) % entries.size]
}

/** Minimal title data persisted locally (favorites, progress). */
data class TitleLite(
    val id: Int,
    val title: String,
    val poster: String,
    val year: String = "",
    val type: String = ""
)

/** Full title; /v1/last, /v1/info and /v1/search share this schema. */
data class Title(
    val id: Int,
    val title: String,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val year: String = "",
    val poster: String = "",
    val ratingSum: Int = 0,
    val votes: Int = 0,
    val timer: Long? = null,          // Unix time (UTC) of the next episode
    val type: String = "",
    val director: String = "",
    val series: Map<String, String> = emptyMap(), // episode name -> video file id
    val nextEpisodeText: String? = null
) {
    /** `rating` is the SUM of votes: average = rating / votes, mapped to 1..5 stars. */
    val stars: Int get() = if (votes > 0) (ratingSum.toDouble() / votes).roundToInt().coerceIn(1, 5) else 0
    val percent: Int get() = stars * 20
    /** Number of entries in `series`; numbers inside episode names are unreliable. */
    val episodesOut: Int get() = series.size

    fun totalEpisodes(): Int? = PROGRESS.find(title)?.groupValues?.get(3)?.toIntOrNull()
    fun toLite() = TitleLite(id, title, poster, year, type)

    companion object {
        fun minimal(id: Int, title: String, poster: String): Title =
            Title(id = id, title = title, poster = poster)
    }
}

/**
  * The API sends the title as one string: `<Russian> / <English> [1-5 of 12+] [next episode date]`
  * (bracket parts are in Russian). Split it for display.
  */
data class TitleParts(val ru: String, val en: String, val tags: List<String>) {
    companion object {
        private val TAG = Regex("""\[([^\]]+)]""")

        fun of(title: String): TitleParts {
            val tags = TAG.findAll(title).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
            val clean = title.replace(TAG, " ").replace(Regex("\\s+"), " ").trim()
            val ru = clean.substringBefore(" / ").trim()
            val en = clean.substringAfter(" / ", "").trim()
            return TitleParts(ru.ifEmpty { title.trim() }, en, tags)
        }
    }
}

/** `episode` is the playlist position, not the number from the episode name. */
data class Progress(
    val lite: TitleLite,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val label: String? = null,  // episode name from the playlist; absent in records saved by older versions
    val updatedAt: Long = 0L    // last watched (ms), orders the Continue tab
) {
    fun episodeLabel(): String = label?.takeIf { it.isNotBlank() } ?: "Серия $episode"
}

/**
  * `index` is the 1-based playlist position, `label` is the name sent by the server.
  * Either quality may be missing; [url] falls back to the other one.
  * The playlist has no Full HD link: [Repo.fullHdUrl] asks the site, [url] gives HD for it.
  */
data class PlaylistItem(
    val index: Int,
    val label: String,
    val hd: String?,
    val sd: String?,
    val preview: String? = null
) {
    val videoId: String? get() = VIDEO_ID.find(sd ?: hd.orEmpty())?.groupValues?.get(1)

    fun url(quality: Quality): String? = when (quality) {
        Quality.FHD, Quality.HD -> hd ?: sd
        Quality.SD -> sd ?: hd
    }
}

// Patterns verified against real API and site responses
private val VIDEO_ID = Regex("""/(\d+)\.mp4""")
private val SERIES_PAIR = Regex("""['"]([^'"]+)['"]\s*:\s*['"](\d+)['"]""")
private val PROGRESS = Regex("""\[(\d+)-(\d+) из (\d+)\+?]""")
private val NEXT_EP = Regex("""\[(\d+) серия - ([^\]]+?)]""")

/**
  * Lenient title-list parser for /v1/last, /v1/info and /v1/search: accepts a bare array
  * or an object wrapping it under data/items/list/… keys.
  */
object TitlesParser {

    fun parseList(raw: String): List<Title> = try {
        val root = JsonParser.parseString(raw)
        findArray(root)?.mapNotNull { if (it.isJsonObject) parseTitle(it.asJsonObject) else null } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun parseSingle(raw: String): Title? = parseList(raw).firstOrNull()

    fun findArray(root: JsonElement): JsonArray? {
        if (root.isJsonArray) return root.asJsonArray
        if (root.isJsonObject) {
            val o = root.asJsonObject
            val keys = listOf("data", "playlist", "list", "items", "episodes", "titles", "results", "response")
            for (k in keys) {
                val el = o.get(k) ?: continue
                if (el.isJsonArray) return el.asJsonArray
                if (el.isJsonObject) {
                    for (k2 in keys) {
                        val el2 = el.asJsonObject.get(k2) ?: continue
                        if (el2.isJsonArray) return el2.asJsonArray
                    }
                }
            }
        }
        return null
    }

    fun parseTitle(o: JsonObject): Title? {
        val id = o.str("id")?.toIntOrNull() ?: o.str("news_id")?.toIntOrNull() ?: return null
        val title = (o.str("title") ?: o.str("name") ?: "").trim()
        if (title.isEmpty()) return null
        val genreRaw = o.str("genre") ?: o.str("genres") ?: ""
        return Title(
            id = id,
            title = title,
            description = o.str("description") ?: o.str("descr") ?: "",
            genres = genreRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            year = o.str("year") ?: "",
            poster = posterUrl(o.str("urlImagePreview") ?: o.str("image") ?: o.str("preview") ?: o.str("poster") ?: ""),
            ratingSum = o.str("rating")?.toIntOrNull() ?: 0,
            votes = o.str("votes")?.toIntOrNull() ?: 0,
            // fully released titles have timer = 0, meaning "no date", not 1 Jan 1970
            timer = (o.str("timer")?.toLongOrNull() ?: o.str("time")?.takeIf { it.length >= 10 }?.toLongOrNull())
                ?.takeIf { it > 0 },
            type = o.str("type") ?: "",
            director = o.str("director") ?: "",
            series = parseSeries(o.str("series") ?: ""),
            nextEpisodeText = NEXT_EP.find(title)?.groupValues?.get(2)
        )
    }

    /**
      * static.openni.ru serves an expired HTTPS certificate while the API links posters over
      * https, which Coil refuses to load; the same files are served over plain http.
      */
    fun posterUrl(url: String): String =
        if (url.startsWith("https://static.openni.ru/", ignoreCase = true)) "http://" + url.substring(8) else url

    /**
      * `series` is NOT JSON but a Python dict literal with single quotes:
      * `{'<episode name>':'<video id>', ...}` — parsed with a regex, order preserved. Keys are the
      * same names as in the playlist (not reliable episode numbers), values are video file ids.
      */
    fun parseSeries(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (m in SERIES_PAIR.findAll(raw)) {
            result[m.groupValues[1].trim()] = m.groupValues[2]
        }
        return result
    }

}

private fun JsonObject.str(key: String): String? {
    val el = get(key) ?: return null
    return if (el.isJsonPrimitive) el.asJsonPrimitive.asString else null
}

/**
  * Playlist parser (/v1/playlist): a bare array of {name, hd, std, preview}.
  *
  * Array order is episode order, so the episode key is its position (1, 2, 3…): names such as
  * "215-216", "492(1)", OVA or film entries cannot be used as numbers.
  */
object PlaylistParser {

    fun parse(raw: String): List<PlaylistItem> = try {
        val arr = TitlesParser.findArray(JsonParser.parseString(raw)) ?: run {
            android.util.Log.w("PlaylistParser", "Неизвестный формат плейлиста: ${raw.take(400)}")
            return emptyList()
        }
        arr.mapNotNull { parseItem(it) }
            .mapIndexed { i, item -> item.copy(index = i + 1, label = item.label.ifEmpty { "Серия ${i + 1}" }) }
    } catch (e: Exception) {
        android.util.Log.w("PlaylistParser", "Ошибка разбора плейлиста: ${e.message}")
        emptyList()
    }

    private fun parseItem(el: JsonElement): PlaylistItem? {
        if (!el.isJsonObject) return null
        val o = el.asJsonObject
        val hd = o.url("hd")
        val sd = o.url("std") ?: o.url("sd")
        if (hd == null && sd == null) return null
        return PlaylistItem(
            index = 0, // assigned by position in parse()
            label = o.str("name")?.trim().orEmpty(),
            hd = hd,
            sd = sd,
            preview = o.url("preview")
        )
    }

    private fun JsonObject.url(key: String): String? =
        str(key)?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
}
