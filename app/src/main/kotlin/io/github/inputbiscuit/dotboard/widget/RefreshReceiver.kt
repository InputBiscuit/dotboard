package io.github.inputbiscuit.dotboard.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tap to refresh. Not exported, so only the widget's own PendingIntent can trigger a fetch. */
class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            goAsyncWork { WidgetUpdater.update(context, intArrayOf(id), WidgetUpdater.Mode.FORCE) }
        }
    }
}
