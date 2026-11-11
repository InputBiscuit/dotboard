package io.github.inputbiscuit.dotboard.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.toBoardMessage
import io.github.inputbiscuit.dotboard.core.stations.StationList
import io.github.inputbiscuit.dotboard.data.Settings
import io.github.inputbiscuit.dotboard.ui.components.StationField
import io.github.inputbiscuit.dotboard.ui.home.BoardPreview
import io.github.inputbiscuit.dotboard.ui.theme.DotboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Chooses a widget's station and optional "calling at" filter, with a live preview. */
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
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val existing = app.widgetConfigs.get(widgetId)
                    val s = app.settings.current()
                    crs = existing?.crs ?: s.defaultCrs
                    filter = existing?.filterCrs ?: s.defaultFilterCrs
                    loaded = true
                }

                val preview by produceState<BoardContent>(BoardContent.Message("Loading"), crs, filter, loaded) {
                    if (!loaded) return@produceState
                    if (crs.length != 3) {
                        value = BoardContent.Message("Choose a station")
                        return@produceState
                    }
                    delay(300) // let typing settle
                    val query = BoardQuery.of(crs, filter)
                    value = try {
                        BoardContent.Departures(app.departures().cached(query, 60_000).board)
                    } catch (e: DepartureException) {
                        e.toBoardMessage()
                    }
                }

                Scaffold(topBar = { TopAppBar(title = { Text("Set up board") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BoardPreview(preview, settings.theme, settings.use24h)
                        if (loaded) {
                            StationField("Station", crs, { crs = it }, stations)
                            StationField("Calling at", filter, { filter = it }, stations, optional = true)
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
                                    app.widgetConfigs.set(widgetId, WidgetConfig(crs, filter))
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
