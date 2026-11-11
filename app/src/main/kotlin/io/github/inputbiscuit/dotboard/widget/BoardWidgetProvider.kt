package io.github.inputbiscuit.dotboard.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.inputbiscuit.dotboard.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BoardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetScheduler.ensure(context)
        goAsyncWork { WidgetUpdater.update(context, ids, WidgetUpdater.Mode.RENDER) }
    }

    /** Resized, or moved between a foldable's screens: redraw for the new size from the cache. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        goAsyncWork { WidgetUpdater.update(context, intArrayOf(id), WidgetUpdater.Mode.RENDER) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        goAsyncWork { context.app.widgetConfigs.delete(ids) }
    }

    override fun onEnabled(context: Context) = WidgetScheduler.ensure(context)

    override fun onDisabled(context: Context) = WidgetScheduler.cancel(context)
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive, with a safety timeout. */
internal fun BroadcastReceiver.goAsyncWork(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            withTimeout(25_000) { block() }
        } catch (e: Exception) {
            Log.w("DotboardWidget", "Widget work failed", e)
        } finally {
            pending.finish()
        }
    }
}
