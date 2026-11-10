package io.github.inputbiscuit.dotboard.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.toBoardMessage
import io.github.inputbiscuit.dotboard.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.app

    val settings: StateFlow<Settings> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _content = MutableStateFlow<BoardContent>(BoardContent.Message("Loading"))
    val content: StateFlow<BoardContent> = _content.asStateFlow()

    init {
        viewModelScope.launch {
            // Reload whenever the source or station changes; refresh on the configured interval meanwhile.
            app.settings.settings
                .map { listOf(it.useSampleData, it.sampleScenario, it.defaultCrs, it.defaultFilterCrs, it.refreshMinutes) }
                .distinctUntilChanged()
                .collectLatest {
                    val s = app.settings.current()
                    while (true) {
                        load(s, force = false)
                        delay(s.refreshMinutes * 60_000L)
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(app.settings.current(), force = true) }
    }

    private suspend fun load(s: Settings, force: Boolean) {
        if (s.defaultCrs.length != 3) {
            _content.value = BoardContent.Message("No station", "Choose one in settings")
            return
        }
        val query = BoardQuery.of(s.defaultCrs, s.defaultFilterCrs)
        val repo = app.departures()
        _content.value = try {
            BoardContent.Departures(repo.cached(query, if (force) 0 else s.refreshMinutes * 60_000L).board)
        } catch (e: DepartureException) {
            repo.peek(query)?.let { BoardContent.Departures(it.board) } ?: e.toBoardMessage()
        }
    }
}
