package io.github.inputbiscuit.dotboard.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.stations.Station
import io.github.inputbiscuit.dotboard.core.stations.StationList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugUiState(
    val useSampleData: Boolean = true,
    val scenario: FakeScenario = FakeScenario.MIXED,
    val apiKey: String = "",
    val crs: String = "",
    val filterCrs: String = "",
    val stationName: String? = null,
    val filterName: String? = null,
    val suggestions: List<Station> = emptyList(),
    val loading: Boolean = false,
    val board: Board? = null,
    val stale: Boolean = false,
    val error: String? = null,
)

class DebugViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.app
    private var stations: StationList? = null
    private var fetchJob: Job? = null

    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            stations = app.stations.stations()
            val s = app.settings.current()
            val key = app.apiKeys.get()
            _state.update {
                it.copy(
                    useSampleData = s.useSampleData,
                    scenario = s.sampleScenario,
                    apiKey = key,
                    crs = s.defaultCrs,
                    filterCrs = s.defaultFilterCrs,
                    stationName = lookup(s.defaultCrs),
                    filterName = lookup(s.defaultFilterCrs),
                )
            }
        }
    }

    fun onUseSampleData(value: Boolean) {
        _state.update { it.copy(useSampleData = value, board = null, error = null) }
        viewModelScope.launch { app.settings.update { it.copy(useSampleData = value) } }
    }

    fun onScenario(value: FakeScenario) {
        _state.update { it.copy(scenario = value) }
        viewModelScope.launch { app.settings.update { it.copy(sampleScenario = value) } }
    }

    fun onApiKey(value: String) = _state.update { it.copy(apiKey = value) }

    fun onCrs(value: String) = _state.update {
        it.copy(crs = value, stationName = lookup(value), suggestions = suggest(value))
    }

    fun onFilterCrs(value: String) = _state.update {
        it.copy(filterCrs = value, filterName = lookup(value), suggestions = emptyList())
    }

    fun pickStation(station: Station) = _state.update {
        it.copy(crs = station.crs, stationName = station.name, suggestions = emptyList())
    }

    fun fetch() {
        val s = _state.value
        val crs = s.crs.trim().uppercase()
        if (crs.length != 3) {
            _state.update { it.copy(error = "Enter a 3-letter CRS code (e.g. EAR)") }
            return
        }
        val query = BoardQuery.of(crs, s.filterCrs)
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, suggestions = emptyList()) }
            app.apiKeys.set(s.apiKey)
            app.settings.update { it.copy(defaultCrs = crs, defaultFilterCrs = query.filterCrs.orEmpty()) }
            val repo = app.departures()
            try {
                // maxAge 0: the debug screen always fetches (still shared with any in-flight widget fetch).
                val cached = repo.cached(query, maxAgeMillis = 0)
                _state.update { it.copy(loading = false, board = cached.board, stale = false) }
            } catch (e: DepartureException) {
                val last = repo.peek(query)
                _state.update { it.copy(loading = false, error = e.describe(), board = last?.board, stale = last != null) }
            }
        }
    }

    private fun lookup(value: String): String? =
        value.trim().takeIf { it.length == 3 }?.let { stations?.get(it)?.name }

    private fun suggest(value: String): List<Station> {
        val q = value.trim()
        if (q.length < 2 || lookup(q) != null) return emptyList()
        return stations?.search(q, limit = 5).orEmpty()
    }

    private fun DepartureException.describe(): String = when (this) {
        is DepartureException.MissingApiKey -> "No API key"
        is DepartureException.InvalidApiKey -> "API key rejected: $message"
        is DepartureException.Network -> "No connection ($message)"
        is DepartureException.Http -> message ?: "Server error"
        is DepartureException.BadResponse -> "Unexpected response from server"
    }
}
