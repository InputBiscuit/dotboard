package io.github.inputbiscuit.dotboard.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.inputbiscuit.dotboard.BuildConfig
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.model.platforms
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.toBoardMessage
import io.github.inputbiscuit.dotboard.core.stations.StationList
import io.github.inputbiscuit.dotboard.data.Settings
import io.github.inputbiscuit.dotboard.data.platformBoard
import io.github.inputbiscuit.dotboard.ui.components.StationField
import io.github.inputbiscuit.dotboard.ui.home.BoardPreview
import io.github.inputbiscuit.dotboard.ui.theme.DotboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Chooses a widget's station, optional "calling at" and platform filters, and calling-point times, with a live preview. */
class WidgetConfigActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Backing out cancels placing the widget.
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            DotboardTheme {
                val stations by produceState<StationList?>(null) { value = app.stations.stations() }
                val settings by produceState(Settings()) { value = app.settings.current() }
                var crs by remember { mutableStateOf("") }
                var filter by remember { mutableStateOf("") }
                var follow by remember { mutableStateOf(false) }
                var platforms by remember { mutableStateOf(emptySet<String>()) }
                var keepNoPlatform by remember { mutableStateOf(false) }
                // Null follows the setting in Settings.
                var times by remember { mutableStateOf<Boolean?>(null) }
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val existing = app.widgetConfigs.get(widgetId)
                    val s = app.settings.current()
                    crs = existing?.crs ?: s.defaultCrs
                    filter = existing?.filterCrs ?: s.defaultFilterCrs
                    follow = existing?.followLocation ?: false
                    platforms = existing?.platforms.orEmpty()
                    keepNoPlatform = existing?.keepNoPlatform ?: false
                    times = existing?.callingPointTimes
                    loaded = true
                }

                // Platforms on the preview board, so the picker offers them even before the station's list has them.
                var previewPlatforms by remember { mutableStateOf(emptyList<String>()) }
                val preview by produceState<BoardContent>(
                    BoardContent.Message("Loading"), crs, filter, platforms, keepNoPlatform, loaded,
                ) {
                    if (!loaded) return@produceState
                    if (crs.length != 3) {
                        value = BoardContent.Message("Choose a station")
                        return@produceState
                    }
                    delay(300) // let typing settle
                    val rows = if (platforms.isEmpty()) BoardQuery.ROWS else BoardQuery.FILTERED_ROWS
                    val query = BoardQuery.of(crs, filter, rows)
                    value = try {
                        val board = app.departures().cached(query, 60_000).board
                        previewPlatforms = board.platforms()
                        if (!app.settings.current().useSampleData) app.platforms.record(board)
                        platformBoard(board, platforms, keepNoPlatform)
                    } catch (e: DepartureException) {
                        e.toBoardMessage()
                    }
                }

                Scaffold(topBar = { TopAppBar(title = { Text("Set up board") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val showTimes = times ?: settings.showCallingPointTimes
                        BoardPreview(preview, settings.theme, settings.use24h, callingPointTimes = showTimes)
                        if (loaded) {
                            if (BuildConfig.LOCATION) {
                                FollowLocationSection(
                                    stations = stations,
                                    following = follow,
                                    refreshMinutes = settings.refreshMinutes,
                                    onFollow = {
                                        if (it != crs) platforms = emptySet()
                                        crs = it
                                        follow = true
                                    },
                                    onStopFollowing = { follow = false },
                                )
                            }
                            // Typing or picking a station makes it a fixed board again.
                            StationField("Station", crs, {
                                // Platforms belong to a station, so a new station starts with all of them.
                                if (it != crs) platforms = emptySet()
                                crs = it
                                follow = false
                            }, stations)
                            StationField("Calling at", filter, { filter = it }, stations, optional = true)
                            if (!follow) {
                                PlatformPicker(
                                    crs = crs,
                                    alsoSeen = previewPlatforms,
                                    selected = platforms,
                                    keepNoPlatform = keepNoPlatform,
                                    onSelect = { platforms = it },
                                    onKeepNoPlatform = { keepNoPlatform = it },
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { times = !showTimes },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Times at calling points", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "For this board. The default is in Dotboard's settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Switch(checked = showTimes, onCheckedChange = { times = it })
                            }
                        }
                        if (settings.useSampleData) {
                            Text(
                                "Showing sample data. Turn it off in Dotboard's settings once you have an API key.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    val config = WidgetConfig(
                                        crs,
                                        filter,
                                        followLocation = follow,
                                        // A following board changes station, so it always shows all platforms.
                                        platforms = if (follow) emptySet() else platforms,
                                        keepNoPlatform = keepNoPlatform,
                                        // Only kept when it differs from Settings, so the board follows later changes there.
                                        callingPointTimes = times?.takeIf { it != app.settings.current().showCallingPointTimes },
                                    )
                                    app.widgetConfigs.set(widgetId, config)
                                    WidgetScheduler.ensure(this@WidgetConfigActivity)
                                    WidgetUpdater.update(this@WidgetConfigActivity, intArrayOf(widgetId), WidgetUpdater.Mode.RENDER)
                                    setResult(RESULT_OK, result)
                                    finish()
                                }
                            },
                            enabled = crs.length == 3,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add board") }
                    }
                }
            }
        }
    }
}
