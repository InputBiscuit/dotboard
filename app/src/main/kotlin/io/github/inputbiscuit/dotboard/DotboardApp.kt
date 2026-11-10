package io.github.inputbiscuit.dotboard

import android.app.Application
import android.content.Context
import io.github.inputbiscuit.dotboard.core.data.CachingDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.data.FileBoardStore
import io.github.inputbiscuit.dotboard.core.data.InMemoryBoardStore
import io.github.inputbiscuit.dotboard.core.data.LdbwsDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.ProxyDepartureRepository
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

    /** Live data with the user's own key, straight to the Rail Data Marketplace. */
    private val live by lazy {
        CachingDepartureRepository(
            LdbwsDepartureRepository(apiKey = { apiKeys.get() }),
            FileBoardStore(File(cacheDir, "boards")),
        )
    }

    /** Live data through Dotboard's API proxy (no key needed). */
    private val proxied by lazy {
        CachingDepartureRepository(
            ProxyDepartureRepository(BuildConfig.PROXY_BASE_URL),
            FileBoardStore(File(cacheDir, "boards-proxy")),
        )
    }

    /** Whether live data currently goes through the proxy (no own key entered and a proxy is built in). */
    suspend fun usesProxy(): Boolean = BuildConfig.PROXY_BASE_URL.isNotBlank() && apiKeys.get().isBlank()

    // One cache per scenario, so switching scenario never shows another scenario's cached board.
    private val samples = ConcurrentHashMap<FakeScenario, CachingDepartureRepository>()

    /**
     * The departures source: sample data if chosen in settings; otherwise live, with the user's own key if
     * they've entered one, else through the proxy.
     */
    suspend fun departures(): CachingDepartureRepository {
        val s = settings.current()
        if (!s.useSampleData) return if (usesProxy()) proxied else live
        return samples.getOrPut(s.sampleScenario) {
            CachingDepartureRepository(
                FakeDepartureRepository(scenario = { s.sampleScenario }, stationName = { stations.name(it) }),
                InMemoryBoardStore(),
            )
        }
    }
}

val Context.app: DotboardApp get() = applicationContext as DotboardApp
