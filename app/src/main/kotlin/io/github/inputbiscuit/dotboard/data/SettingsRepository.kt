package io.github.inputbiscuit.dotboard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class Settings(
    val useSampleData: Boolean = false,
    val sampleScenario: FakeScenario = FakeScenario.MIXED,
    val defaultCrs: String = "PAD",
    val defaultFilterCrs: String = "",
    val theme: LedTheme = LedTheme.AMBER,
    /** How often boards refresh in the background (WorkManager, so 15 minutes at least). */
    val refreshMinutes: Int = 15,
    val use24h: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    companion object {
        val REFRESH_CHOICES = listOf(15, 30, 60)
    }
}

/** App settings in DataStore. The API key is kept separately, encrypted, by [ApiKeyStore]. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[USE_SAMPLE] = s.useSampleData
            prefs[SCENARIO] = s.sampleScenario.name
            prefs[DEFAULT_CRS] = s.defaultCrs.trim().uppercase()
            prefs[DEFAULT_FILTER] = s.defaultFilterCrs.trim().uppercase()
            prefs[THEME] = s.theme.name
            prefs[REFRESH_MINUTES] = s.refreshMinutes
            prefs[USE_24H] = s.use24h
            prefs[ONBOARDING_DONE] = s.onboardingDone
        }
    }

    private fun Preferences.toSettings() = Settings(
        useSampleData = this[USE_SAMPLE] ?: false,
        sampleScenario = enumOrDefault(this[SCENARIO], FakeScenario.MIXED),
        defaultCrs = this[DEFAULT_CRS] ?: "PAD",
        defaultFilterCrs = this[DEFAULT_FILTER].orEmpty(),
        theme = enumOrDefault(this[THEME], LedTheme.AMBER),
        // Earlier builds offered 1-10 minute screen-on refreshes; anything not offered now becomes 15.
        refreshMinutes = this[REFRESH_MINUTES]?.takeIf { it in Settings.REFRESH_CHOICES } ?: 15,
        use24h = this[USE_24H] ?: true,
        onboardingDone = this[ONBOARDING_DONE] ?: false,
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val USE_SAMPLE = booleanPreferencesKey("use_sample_data")
        val SCENARIO = stringPreferencesKey("sample_scenario")
        val DEFAULT_CRS = stringPreferencesKey("default_crs")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter_crs")
        val THEME = stringPreferencesKey("theme")
        val REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
        val USE_24H = booleanPreferencesKey("use_24h")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
