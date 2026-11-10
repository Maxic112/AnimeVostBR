package com.animevostbr.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.animevostbr.playback.PlaybackRules
import java.io.File

/** A downloaded (or downloading) episode. `dmId` is the system DownloadManager id. */
data class DownloadRecord(
    val dmId: Long,
    val lite: TitleLite,
    val index: Int,
    val label: String,
    val videoId: String,
    val quality: Quality,
    val path: String,
    val addedAt: Long
)

enum class DownloadState { WAITING, RUNNING, DONE, FAILED }

data class DownloadStatus(val record: DownloadRecord, val state: DownloadState, val bytes: Long, val total: Long)

/**
  * Episodes for watching offline, via the system DownloadManager: it shows its own notification,
  * resumes after network drops and keeps going when the app is closed. Files live in the app's
  * folder on the device storage and are removed with the app.
  */
object Downloads {

    private const val KEY = "downloads"
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<DownloadRecord>>() {}.type

    private fun prefs(context: Context) = context.getSharedPreferences("animevostbr_downloads", Context.MODE_PRIVATE)

    fun records(context: Context): List<DownloadRecord> =
        gson.fromJson<MutableList<DownloadRecord>>(prefs(context).getString(KEY, "[]"), listType) ?: mutableListOf()

    private fun save(context: Context, list: List<DownloadRecord>) =
        prefs(context).edit().putString(KEY, gson.toJson(list)).apply()

    private fun manager(context: Context) = context.getSystemService(DownloadManager::class.java)

    fun folder(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)

    fun freeBytes(context: Context): Long =
        folder(context)?.let { runCatching { StatFs(it.path).availableBytes }.getOrNull() } ?: 0L

    /** Starts a download; returns an error to show, or null. Full HD needs a fresh signed link from the site. */
    suspend fun start(context: Context, lite: TitleLite, item: PlaylistItem, quality: Quality): String? {
        val videoId = item.videoId ?: return "У этой серии нет файла для загрузки"
        if (records(context).any { it.lite.id == lite.id && it.videoId == videoId }) return "Эта серия уже скачана или скачивается"
        val dir = folder(context) ?: return "Нет доступа к памяти устройства"
        val url = when (quality) {
            Quality.FHD -> Repo.playerLinks(item)[Quality.FHD]?.firstOrNull()
                ?: return "Full HD у этой серии нет — выберите HD"
            else -> item.url(quality)?.let(PlaybackRules::secure) ?: return "Нет ссылки на видео"
        }
        val relative = "${lite.id}/${videoId}_${quality.name}.mp4"
        val file = File(dir, relative)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("${TitleParts.of(lite.title).ru} — ${item.label}")
            .setDescription("AnimeVostBR · ${quality.label}")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, relative)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("User-Agent", ApiClient.UA)
        val id = runCatching { manager(context).enqueue(request) }.getOrElse { return "Не удалось начать загрузку" }
        save(
            context,
            records(context) + DownloadRecord(
                dmId = id, lite = lite, index = item.index, label = item.label, videoId = videoId,
                quality = quality, path = file.path, addedAt = System.currentTimeMillis()
            )
        )
        return null
    }

    /** Newest first. A record whose system entry is gone counts as done if its file is complete. */
    fun statuses(context: Context): List<DownloadStatus> {
        val list = records(context)
        if (list.isEmpty()) return emptyList()
        val byId = mutableMapOf<Long, Triple<Int, Long, Long>>()
        runCatching {
            manager(context).query(DownloadManager.Query().setFilterById(*list.map { it.dmId }.toLongArray()))?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) byId[c.getLong(idCol)] = Triple(c.getInt(statusCol), c.getLong(doneCol), c.getLong(totalCol))
            }
        }
        return list.sortedByDescending { it.addedAt }.map { r ->
            val row = byId[r.dmId]
            val file = File(r.path)
            val state = when (row?.first) {
                DownloadManager.STATUS_SUCCESSFUL -> if (file.exists()) DownloadState.DONE else DownloadState.FAILED
                DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> DownloadState.WAITING
                DownloadManager.STATUS_FAILED -> DownloadState.FAILED
                else -> if (file.exists() && file.length() > 0) DownloadState.DONE else DownloadState.FAILED
            }
            DownloadStatus(r, state, row?.second ?: file.length(), row?.third ?: file.length())
        }
    }

    /** The finished file of an episode, for the player. */
    fun localFile(context: Context, titleId: Int, videoId: String?): Pair<File, Quality>? {
        if (videoId == null) return null
        val r = records(context).firstOrNull { it.lite.id == titleId && it.videoId == videoId } ?: return null
        val s = statuses(context).firstOrNull { it.record.dmId == r.dmId } ?: return null
        return if (s.state == DownloadState.DONE) File(r.path) to r.quality else null
    }

    /** Cancels or deletes: the system removes the file together with its entry. */
    fun remove(context: Context, record: DownloadRecord) {
        runCatching { manager(context).remove(record.dmId) }
        runCatching {
            val file = File(record.path)
            file.delete()
            // the title's folder goes with its last episode
            file.parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete()
        }
        save(context, records(context).filterNot { it.dmId == record.dmId })
    }
}
