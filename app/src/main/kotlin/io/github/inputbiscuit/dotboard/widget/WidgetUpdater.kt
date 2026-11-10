package io.github.inputbiscuit.dotboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import android.util.SizeF
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.data.LoadedBoard
import io.github.inputbiscuit.dotboard.data.load
import io.github.inputbiscuit.dotboard.location.ApproximateLocation
import java.time.LocalTime

/** Loads data (through the shared cache) and redraws widgets. */
object WidgetUpdater {

    enum class Mode {
        /** Redraw (resize, settings change, a train has left); fetch only if older than the refresh interval. */
        RENDER,

        /** Periodic WorkManager refresh: fetch (anything fetched in the last minute is reused). */
        BACKGROUND,

        /** Tap to refresh: always fetch. */
        FORCE,
    }

    private const val TAG = "DotboardWidget"

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, BoardWidgetProvider::class.java))

    suspend fun updateAll(context: Context, mode: Mode) = update(context, widgetIds(context), mode)

    suspend fun update(context: Context, ids: IntArray, mode: Mode) {
        if (ids.isEmpty()) return
        val app = context.app
        val manager = AppWidgetManager.getInstance(context)
        val settings = app.settings.current()
        val repo = app.departures()
        val maxAge = when (mode) {
            Mode.RENDER -> settings.refreshMinutes * 60_000L
            Mode.BACKGROUND -> 60_000L
            Mode.FORCE -> 0L
        }
        var nextDeparture: Long? = null
        // Looked up at most once per update, and only if a location-following board needs it.
        val location = lazyLocation(context, enabled = mode == Mode.BACKGROUND || mode == Mode.FORCE)

        for (id in ids) {
            // An unconfigured widget (e.g. pinned from the app) shows the default station.
            var config = app.widgetConfigs.get(id)
            if (config?.followLocation == true) config = follow(context, id, config, location)
            val crs = config?.crs ?: settings.defaultCrs
            val loaded = if (crs.length != 3) {
                LoadedBoard(BoardContent.Message("Tap to set up"))
            } else {
                repo.load(BoardQuery.of(crs, config?.filterCrs ?: settings.defaultFilterCrs), maxAge)
            }
            val content = loaded.content
            // The launcher ticks the clock (TextClock), so the board leaves the digits out.
            val frame = BoardFrame(content, LocalTime.now(), settings.use24h, drawClock = false, updatedAt = loaded.updatedAt)
            (content as? BoardContent.Departures)?.board?.departures?.firstOrNull()?.let { first ->
                millisUntilDeparted(first)?.let { nextDeparture = minOf(nextDeparture ?: it, it) }
            }
            val sizes = sizes(manager, id)
            val click = refreshIntent(context, id)
            try {
                manager.updateAppWidget(id, WidgetRenderer.build(context, frame, settings.theme, sizes, click))
            } catch (e: IllegalArgumentException) {
                // Over the launcher's bitmap budget (huge widget, many frames): fall back to one still image.
                Log.w(TAG, "Widget $id too large to animate, drawing a still board", e)
                manager.updateAppWidget(id, WidgetRenderer.build(context, frame, settings.theme, sizes, click, animated = false))
            }
        }

        // Drop the first train from the board once it has gone, if that's before the next periodic refresh.
        nextDeparture?.takeIf { it < settings.refreshMinutes * 60_000L }?.let { WidgetScheduler.redrawAfter(context, it) }
    }

    /** The user's approximate location, fetched on first use, or null if disabled or unavailable. */
    private fun lazyLocation(context: Context, enabled: Boolean): suspend () -> Location? {
        var fetched = false
        var location: Location? = null
        return {
            if (enabled && !fetched) {
                fetched = true
                location = (ApproximateLocation.current(context) as? ApproximateLocation.Result.Found)?.location
            }
            location
        }
    }

    /**
     * Moves a location-following board to the nearest station (with a margin, so it doesn't flip between two)
     * and saves it. Without a location (no permission, location off, no fix) it keeps its current station.
     */
    private suspend fun follow(context: Context, id: Int, config: WidgetConfig, location: suspend () -> Location?): WidgetConfig {
        // Logs say what happened, never where: no coordinates or station names.
        val here = location() ?: return config.also { Log.i(TAG, "Widget $id: no location available, keeping its station") }
        val station = context.app.stations.stations().followNearest(here.latitude, here.longitude, config.crs)
        if (station == null || station.crs == config.crs) {
            return config.also { Log.i(TAG, "Widget $id: location checked, still the nearest station") }
        }
        Log.i(TAG, "Widget $id: location checked, moved to the nearest station")
        return config.copy(crs = station.crs).also { context.app.widgetConfigs.set(id, it) }
    }

    /** Milliseconds until [d] has left (expected time, plus the departed filter's minute of grace), or null. */
    private fun millisUntilDeparted(d: Departure): Long? {
        val time = (d.status as? DepartureStatus.Expected)?.time ?: d.scheduled
        val departs = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        val now = LocalTime.now()
        val minutes = Math.floorMod(departs.toSecondOfDay() / 60 - now.toSecondOfDay() / 60, 24 * 60)
        if (minutes > 12 * 60) return null
        return (minutes + 2) * 60_000L - now.second * 1000L
    }

    /** The sizes (in dp) the widget is currently shown at, e.g. both screens of a foldable. */
    private fun sizes(manager: AppWidgetManager, id: Int): List<SizeF> {
        val options = manager.getAppWidgetOptions(id)
        options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (minW == 0 || minH == 0) return listOf(SizeF(250f, 60f))
        // Older launchers: portrait is narrow and tall, landscape wide and short.
        return listOf(SizeF(minW.toFloat(), maxH.toFloat()), SizeF(maxW.toFloat(), minH.toFloat()))
    }

    private fun refreshIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, BoardWidgetProvider::class.java)
            .setAction(BoardWidgetProvider.ACTION_REFRESH)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
