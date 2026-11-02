package com.animevostbr.notify

import com.animevostbr.core.Sites
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.animevostbr.AnimeVostApp
import com.animevostbr.MainActivity
import com.animevostbr.R
import com.animevostbr.core.ApiClient
import com.animevostbr.core.Repo
import com.animevostbr.core.Title
import com.animevostbr.core.TitlesParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
  * Periodic check for new episodes of favorite titles.
  *
  * /v1/last is ordered by update time, so a title with a new episode moves to the top and the
  * first pages are enough. Episode counts (entries in `series`) are compared with stored ones.
  */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // also refresh the mirror when the app has not been opened for a while (throttled inside)
        runCatching { Sites.refreshMirror() }
        if (!Repo.prefs.notificationsEnabled) return Result.success()
        val favoriteIds = Repo.favorites().mapTo(HashSet()) { it.id }
        if (favoriteIds.isEmpty()) return Result.success()
        return try {
            val list = withContext(Dispatchers.IO) {
                (1..PAGES).flatMap { page -> TitlesParser.parseList(ApiClient.api.last(page).string()) }
            }
            if (list.isEmpty()) return Result.success()

            val seen = Repo.seenMap().toMutableMap()
            val fresh = mutableListOf<Title>()
            for (t in list) {
                if (t.id !in favoriteIds) continue
                val out = t.episodesOut
                val prev = seen[t.id]
                // notify only for titles seen before; the first sighting just records the count
                if (prev != null && out > prev) fresh += t
                seen[t.id] = out
            }
            Repo.saveSeen(seen)

            for (t in fresh.take(5)) {
                notifyNewEpisode(t.id, t.title.substringBefore(" / ").trim(), t.series.keys.lastOrNull())
            }
            Result.success()
        } catch (e: Exception) {
            Result.success() // network unavailable — wait for the next run
        }
    }

    private fun notifyNewEpisode(id: Int, title: String, episodeName: String?) {
        val context = applicationContext
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TITLE_ID, id)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AnimeVostApp.UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sakura)
            .setContentTitle(if (episodeName != null) "Вышла $episodeName" else "Вышла новая серия")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // notification permission missing
        }
    }

    companion object {
        /** Feed pages (40 titles each) scanned per run. */
        private const val PAGES = 2
    }
}
