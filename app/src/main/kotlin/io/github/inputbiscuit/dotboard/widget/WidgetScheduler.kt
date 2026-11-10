package io.github.inputbiscuit.dotboard.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * All widget updates go through WorkManager; there are no alarms. The launcher animates the board and ticks
 * the clock itself, so the app only wakes to:
 *  - refresh data on the interval from settings (15 min minimum, with network), and
 *  - redraw from the cache (no network) shortly after the first listed train has left, so departed trains
 *    don't linger until the next refresh.
 */
object WidgetScheduler {

    private const val REFRESH_WORK = "widget-refresh"
    private const val DEPARTURE_WORK = "widget-next-departure"

    /** Starts the periodic refresh if it isn't already scheduled. */
    fun ensure(context: Context, refreshMinutes: Int = 15) = schedule(context, refreshMinutes, ExistingPeriodicWorkPolicy.KEEP)

    /** Applies a new refresh interval. */
    fun reschedule(context: Context, refreshMinutes: Int) = schedule(context, refreshMinutes, ExistingPeriodicWorkPolicy.UPDATE)

    private fun schedule(context: Context, refreshMinutes: Int, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(refreshMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REFRESH_WORK, policy, request)
    }

    /** Redraw from the cache after [delayMillis] (replacing any earlier request). */
    fun redrawAfter(context: Context, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<RedrawWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(60_000), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DEPARTURE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(REFRESH_WORK)
            cancelUniqueWork(DEPARTURE_WORK)
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext, WidgetUpdater.Mode.BACKGROUND)
        return Result.success()
    }
}

class RedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext, WidgetUpdater.Mode.RENDER)
        return Result.success()
    }
}
