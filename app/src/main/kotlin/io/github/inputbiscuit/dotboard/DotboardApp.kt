package io.github.inputbiscuit.dotboard

import android.app.Application
import android.content.Context
import io.github.inputbiscuit.dotboard.core.data.CachingDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.data.FileBoardStore
import io.github.inputbiscuit.dotboard.core.data.InMemoryBoardStore
import io.github.inputbiscuit.dotboard.core.data.LdbwsDepartureRepository
import io.github.inputbiscuit.dotboard.data.ApiKeyStore
import io.github.inputbiscuit.dotboard.data.SettingsRepository
import io.github.inputbiscuit.dotboard.data.StationRepository
import io.github.inputbiscuit.dotboard.widget.WidgetConfigStore
import io.github.inputbiscuit.dotboard.widget.WidgetScheduler
import io.github.inputbiscuit.dotboard.widget.WidgetUpdater
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Holds app-wide singletons. Deliberately no DI framework. */
class DotboardApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val apiKeys by lazy { ApiKeyStore(this) }
    val stations by lazy { StationRepository(this) }
    val widgetConfigs by lazy { WidgetConfigStore(this) }

    override fun onCreate() {
        super.onCreate()
        // Re-arm the widget refresh loops (e.g. after an app update) if any widgets are placed.
        if (WidgetUpdater.widgetIds(this).isNotEmpty()) WidgetScheduler.ensure(this)
    }

    private val live by lazy {
        CachingDepartureRepository(
            LdbwsDepartureRepository(apiKey = { apiKeys.get() }),
            FileBoardStore(File(cacheDir, "boards")),
        )
    }

    // One cache per scenario, so switching scenario never shows another scenario's cached board.
    private val samples = ConcurrentHashMap<FakeScenario, CachingDepartureRepository>()

    /** The departures source selected in settings: sample data (default) or the live API. */
    suspend fun departures(): CachingDepartureRepository {
        val s = settings.current()
        if (!s.useSampleData) return live
        return samples.getOrPut(s.sampleScenario) {
            CachingDepartureRepository(
                FakeDepartureRepository(scenario = { s.sampleScenario }, stationName = { stations.name(it) }),
                InMemoryBoardStore(),
            )
        }
    }
}

val Context.app: DotboardApp get() = applicationContext as DotboardApp
