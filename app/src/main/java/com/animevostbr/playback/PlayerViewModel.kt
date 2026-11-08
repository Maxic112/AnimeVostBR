package com.animevostbr.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.animevostbr.core.PlaylistItem
import com.animevostbr.core.Progress
import com.animevostbr.core.Quality
import com.animevostbr.core.Repo
import com.animevostbr.core.TitleLite
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Player page links are fetched again after this, so expired ones get replaced. */
private const val PAGE_FRESH_MS = 5 * 60 * 1000L

/**
  * Owns the ExoPlayer instance, the episode list, quality and watch progress.
  */
class PlayerViewModel(
    app: Application,
    savedState: SavedStateHandle
) : AndroidViewModel(app) {

    private val titleId: Int = savedState["id"] ?: -1
    private val startEp: Int = savedState["ep"] ?: 1

    var items by mutableStateOf<List<PlaylistItem>>(emptyList())
        private set
    var currentEp by mutableStateOf(startEp)
        private set
    var quality by mutableStateOf(Repo.prefs.defaultQuality)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var hasPrev by mutableStateOf(false)
        private set
    /** Short message over the video, e.g. that HD was replaced with SD. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** The other quality was already tried for the current episode after an error. */
    private var fallbackTried = false

    /**
      * Quality forced by a playback error. Old titles often lack HD for every episode,
      * so do not wait for the error again on each next one.
      */
    private var qualityOverride: Quality? = null

    /** Player page request: Full HD link, or a spare link after an error. */
    private var linkJob: Job? = null

    /** The site player page is being fetched; the screen shows a spinner. */
    var loadingLink by mutableStateOf(false)
        private set

    /** Player page links of the current episode and when they were fetched. */
    private var pageLinks: Map<Quality, List<String>> = emptyMap()
    private var pageAt = 0L

    /** Links of the current episode already played or failed. */
    private val triedLinks = mutableSetOf<String>()

    private var resumeMs: Long =
        Repo.progress(titleId)?.takeIf { it.episode == startEp && it.positionMs > 0 }?.positionMs ?: 0L

    val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        playWhenReady = true
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) = onPlaybackFailed()
        })
        viewModelScope.launch {
            items = Repo.playlist(titleId)
            if (items.isEmpty()) {
                error = "Не удалось загрузить серии.\nПроверьте подключение к интернету."
            } else {
                // a stale progress record may point past the end of the playlist
                playEpisode(startEp.coerceIn(1, items.size))
            }
        }
    }

    /** `ep` is the 1-based playlist position. */
    fun itemFor(ep: Int): PlaylistItem? = items.getOrNull(ep - 1)

    val currentLabel: String get() = itemFor(currentEp)?.label ?: "Серия $currentEp"

    fun playEpisode(ep: Int, startMs: Long? = null) {
        val item = itemFor(ep) ?: return
        quality = qualityOverride ?: Repo.prefs.defaultQuality
        fallbackTried = false
        val url = item.url(quality) ?: run {
            error = "Нет видеопотока: ${item.label}"
            return
        }
        currentEp = item.index
        error = null
        hasNext = itemFor(item.index + 1) != null
        hasPrev = itemFor(item.index - 1) != null
        val start = startMs ?: if (item.index == startEp && resumeMs > 0) resumeMs else 0L
        linkJob?.cancel()
        pageLinks = emptyMap()
        pageAt = 0L
        triedLinks.clear()
        if (quality == Quality.FHD) {
            // not clearMediaItems(): an empty player reports STATE_ENDED, which skips to the next episode
            player.stop()
            linkJob = viewModelScope.launch {
                val fullHd = sitePlayerLinks(item)[Quality.FHD]?.firstOrNull()
                if (fullHd == null) replaceFullHd()
                startPlayback(fullHd ?: url, start)
            }
        } else {
            startPlayback(url, start)
        }
    }

    /** Fetches the player page unless it is fresh; its signed links expire after about 3 hours. */
    private suspend fun sitePlayerLinks(item: PlaylistItem): Map<Quality, List<String>> {
        if (System.currentTimeMillis() - pageAt > PAGE_FRESH_MS) {
            loadingLink = true
            try {
                pageLinks = Repo.playerLinks(item)
                pageAt = System.currentTimeMillis()
            } finally {
                loadingLink = false
            }
        }
        return pageLinks
    }

    /** Full HD is looked up per episode, so unlike [qualityOverride] this lasts for one episode only. */
    private fun replaceFullHd() {
        notice = "${Quality.FHD.label} недоступно для этой серии — включено ${Quality.HD.label}"
        quality = Quality.HD
    }

    private fun startPlayback(url: String, startMs: Long) {
        val mediaItem = MediaItem.Builder().setUri(url).apply {
            when {
                url.contains("m3u8", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                url.contains(".mpd", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()

        triedLinks += url
        // a fresh decoder: some TV hardware decoders freeze when one instance changes resolution
        player.stop()
        player.setMediaItem(mediaItem, startMs)
        player.prepare()
        player.play()
    }

    /**
      * Playback error. First another link of the same quality from the site player: a link may
      * have expired during a long pause, or the video host may be blocked. Then a lower quality.
      */
    private fun onPlaybackFailed() {
        val item = itemFor(currentEp) ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val failed = quality
        linkJob?.cancel()
        linkJob = viewModelScope.launch {
            val spare = sitePlayerLinks(item)[failed]?.firstOrNull { it !in triedLinks }
            if (spare != null) startPlayback(spare, position) else lowerQuality(item, position)
        }
    }

    /** Some episodes exist in one quality only (e.g. HD returns 404 for old episodes). */
    private fun lowerQuality(item: PlaylistItem, position: Long) {
        val hdUrl = item.url(Quality.HD)
        if (quality == Quality.FHD && hdUrl != null) {
            replaceFullHd()
            startPlayback(hdUrl, position)
            return
        }
        val other = if (quality == Quality.HD) Quality.SD else Quality.HD
        val otherUrl = item.url(other)
        if (!fallbackTried && otherUrl != null && otherUrl != item.url(quality)) {
            fallbackTried = true
            notice = "${quality.label} недоступно для этой серии — включено ${other.label}"
            quality = other
            qualityOverride = other
            startPlayback(otherUrl, position)
        } else {
            error = "Не удалось воспроизвести: ${item.label}.\nВидеофайл недоступен на сервере."
        }
    }

    fun showNotice(text: String) {
        notice = text
    }

    fun clearNotice() {
        notice = null
    }

    fun next() {
        val nextEp = currentEp + 1
        if (itemFor(nextEp) != null) {
            resumeMs = 0L
            playEpisode(nextEp)
        }
    }

    fun prev() {
        val prevEp = currentEp - 1
        if (itemFor(prevEp) != null) {
            resumeMs = 0L
            playEpisode(prevEp)
        }
    }

    fun toggleQuality() {
        quality = quality.next()
        Repo.prefs.defaultQuality = quality
        qualityOverride = null // the user picked a quality explicitly
        playEpisode(currentEp, player.currentPosition.takeIf { it > 0 })
    }

    fun saveProgress() {
        // the player still holds the previous episode while the link loads
        if (titleId <= 0 || linkJob?.isActive == true) return
        val dur = player.contentDuration
        if (dur <= 0) return

        // the repo cache also knows titles opened from search, not only from the feed
        val lite: TitleLite = Repo.cachedTitle(titleId)?.toLite()
            ?: Repo.lastCached.firstOrNull { it.id == titleId }?.toLite()
            ?: Repo.favorites().firstOrNull { it.id == titleId }
            ?: Repo.progress(titleId)?.lite
            ?: TitleLite(titleId, "", "")

        val pos = player.currentPosition.coerceIn(0, dur)
        // less than 30 s left counts as watched,
        // so the next visit starts the following episode from the beginning
        val label = itemFor(currentEp)?.label
        if (dur - pos < 30_000) {
            Repo.saveProgress(Progress(lite, currentEp, 0L, dur, label))
        } else {
            Repo.saveProgress(Progress(lite, currentEp, pos, dur, label))
        }
    }

    override fun onCleared() {
        saveProgress()
        player.release()
        super.onCleared()
    }
}
