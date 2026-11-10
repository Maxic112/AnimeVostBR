package com.animevostbr.playback

import com.animevostbr.core.PlaylistItem
import com.animevostbr.core.Quality

/** Player decisions without Android dependencies, covered by JVM tests. */
object PlaybackRules {

    /** Player page links are fetched again after this, so expired ones get replaced. */
    const val PAGE_FRESH_MS = 5 * 60 * 1000L

    /** The countdown to the next episode shows during the last seconds of an episode. */
    const val COUNTDOWN_S = 10

    /** "Skip opening" is offered at the start of an episode and jumps this far. */
    const val SKIP_INTRO_UNTIL_MS = 6 * 60 * 1000L
    const val SKIP_INTRO_MS = 90_000L

    /**
      * Quality to start an episode with: one forced by a playback error, then the mobile-network
      * setting on a metered connection, then the default.
      */
    fun startQuality(default: Quality, override: Quality?, mobile: Quality?, metered: Boolean): Quality =
        override ?: mobile?.takeIf { metered } ?: default

    fun pageStale(pageAt: Long, now: Long): Boolean = now - pageAt > PAGE_FRESH_MS

    /** A link of the failed quality from the player page that has not been played yet. */
    fun spareLink(page: Map<Quality, List<String>>, quality: Quality, tried: Set<String>): String? =
        page[quality]?.firstOrNull { it !in tried }

    private const val VIDEO_HTTP = "http://video.animetop.info/"
    private const val VIDEO_HTTPS = "https://video.animetop.info/"

    /**
      * The API gives plain-HTTP video links. Some mobile operators tamper with unencrypted traffic, so the
      * video host is played over HTTPS (it supports both).
      */
    fun secure(url: String): String =
        if (url.startsWith(VIDEO_HTTP)) VIDEO_HTTPS + url.removePrefix(VIDEO_HTTP) else url

    /** After a failure: the plain-HTTP twin of an HTTPS video link first, then the player page links. */
    fun nextLink(failed: String?, page: Map<Quality, List<String>>, quality: Quality, tried: Set<String>): String? {
        val twin = failed?.takeIf { it.startsWith(VIDEO_HTTPS) }?.let { VIDEO_HTTP + it.removePrefix(VIDEO_HTTPS) }
        return twin?.takeIf { it !in tried } ?: spareLink(page, quality, tried)
    }

    sealed interface Fallback {
        /** [remember]: keep the quality for the next episodes of the title. */
        data class Lower(val quality: Quality, val url: String, val remember: Boolean) : Fallback
        data object Fail : Fallback
    }

    /**
      * After every link of a quality failed. Full HD falls to HD for this episode only: it is looked up
      * per episode. HD and SD swap once and the choice is kept, since old titles often lack HD throughout.
      */
    fun lower(item: PlaylistItem, quality: Quality, swapTried: Boolean): Fallback {
        if (quality == Quality.FHD) item.url(Quality.HD)?.let { return Fallback.Lower(Quality.HD, it, remember = false) }
        val other = if (quality == Quality.HD) Quality.SD else Quality.HD
        val url = item.url(other)
        return if (!swapTried && url != null && url != item.url(quality)) {
            Fallback.Lower(other, url, remember = true)
        } else {
            Fallback.Fail
        }
    }

    /** Whole seconds left while the next-episode countdown shows, otherwise null. */
    fun countdown(positionMs: Long, durationMs: Long, hasNext: Boolean): Int? {
        if (!hasNext || durationMs < 60_000 || positionMs <= 0) return null
        val left = durationMs - positionMs
        return if (left in 1..COUNTDOWN_S * 1000L) ((left + 999) / 1000).toInt() else null
    }

    /** Short clips and films' later parts get no "skip opening" button. */
    fun offerSkipIntro(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 10 * 60 * 1000L && positionMs in 1 until SKIP_INTRO_UNTIL_MS
}
