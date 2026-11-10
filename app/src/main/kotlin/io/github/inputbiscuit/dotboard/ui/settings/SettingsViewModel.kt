package io.github.inputbiscuit.dotboard.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.stations.StationList
import io.github.inputbiscuit.dotboard.data.Settings
import io.github.inputbiscuit.dotboard.widget.WidgetScheduler
import io.github.inputbiscuit.dotboard.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.app

    val settings: StateFlow<Settings> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val hasApiKey: StateFlow<Boolean> = app.apiKeys.hasKey.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _stations = MutableStateFlow<StationList?>(null)
    val stations: StateFlow<StationList?> = _stations.asStateFlow()

    init {
        viewModelScope.launch { _stations.value = app.stations.stations() }
    }

    /**
     * Saves a change and redraws widgets. Changing the data source or station forces a fetch; looks-only
     * changes (theme, 12/24h) redraw from the cache.
     */
    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = app.settings.current()
            app.settings.update(transform)
            val after = app.settings.current()
            val dataChanged = before.useSampleData != after.useSampleData ||
                before.sampleScenario != after.sampleScenario ||
                before.defaultCrs != after.defaultCrs ||
                before.defaultFilterCrs != after.defaultFilterCrs
            if (before.refreshMinutes != after.refreshMinutes) WidgetScheduler.reschedule(app, after.refreshMinutes)
            WidgetUpdater.updateAll(app, if (dataChanged) WidgetUpdater.Mode.FORCE else WidgetUpdater.Mode.RENDER)
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            app.apiKeys.set(key)
            if (!app.settings.current().useSampleData) WidgetUpdater.updateAll(app, WidgetUpdater.Mode.FORCE)
        }
    }
}
