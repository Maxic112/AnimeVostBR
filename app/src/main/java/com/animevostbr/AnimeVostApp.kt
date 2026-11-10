package com.animevostbr

import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.animevostbr.core.Sites
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.animevostbr.core.AppUpdate
import com.animevostbr.core.CrashLog
import com.animevostbr.core.Repo
import com.animevostbr.notify.AppUpdateWorker
import com.animevostbr.notify.UpdateWorker
import com.animevostbr.ui.UiState
import java.util.concurrent.TimeUnit

class AnimeVostApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this, runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty())
        Repo.init(this)
        UiState.themeMode = Repo.prefs.themeMode

        AppUpdate.init(this)

        createNotificationChannels()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // Throttled inside (6 h): the site moves to a new mirror over time (v14 → v15 …)
        scope.launch { runCatching { Sites.refreshMirror() } }
        // WorkManager may postpone the daily job for long on some TVs, so check on start as well
        scope.launch { AppUpdate.checkIfDue() }
        scheduleAppUpdateWork()
        if (Repo.prefs.notificationsEnabled) {
            scheduleUpdateWork()
        }
    }

    private fun scheduleAppUpdateWork() {
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            APP_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            UPDATES_CHANNEL_ID,
            getString(R.string.notif_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = getString(R.string.notif_channel_updates_desc) }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    fun scheduleUpdateWork() {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UPDATES_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelUpdateWork() {
        WorkManager.getInstance(this).cancelUniqueWork(UPDATES_WORK)
    }

    companion object {
        const val UPDATES_CHANNEL_ID = "animevostbr_updates"
        const val UPDATES_WORK = "animevostbr_updates_work"
        const val APP_UPDATE_WORK = "animevostbr_app_update_work"
    }
}
