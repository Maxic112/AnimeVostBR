package com.animevostbr.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.animevostbr.core.AppUpdate

/** Daily check for a new app release on GitHub; the result shows up as orange dots in the UI. */
class AppUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { AppUpdate.check() } // offline — wait for the next run
        return Result.success()
    }
}
