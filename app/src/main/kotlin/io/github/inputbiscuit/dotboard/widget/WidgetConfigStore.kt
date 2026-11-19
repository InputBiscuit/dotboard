package io.github.inputbiscuit.dotboard.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.inputbiscuit.dotboard.BuildConfig
import kotlinx.coroutines.flow.first

private val Context.widgetDataStore by preferencesDataStore(name = "widgets")

/**
 * @param crs the board's station. For a [followLocation] board it's the station last resolved from the
 *   user's location, so redraws don't need a fresh location.
 * @param followLocation re-pick the nearest station on each background refresh (and tap). Always false in
 *   builds without location (BuildConfig.LOCATION).
 * @param platforms only show services from these platforms; empty = all platforms.
 * @param keepNoPlatform with [platforms] set, also show services that have no platform yet.
 * @param callingPointTimes this board's own choice of times at calling points, or null to follow Settings.
 */
data class WidgetConfig(
    val crs: String,
    val filterCrs: String,
    val followLocation: Boolean = false,
    val platforms: Set<String> = emptySet(),
    val keepNoPlatform: Boolean = false,
    val callingPointTimes: Boolean? = null,
)

/** Each widget's own station and optional "calling at" filter, keyed by app widget id. */
class WidgetConfigStore(private val context: Context) {

    suspend fun get(id: Int): WidgetConfig? {
        val prefs = context.widgetDataStore.data.first()
        val crs = prefs[crsKey(id)] ?: return null
        return WidgetConfig(
            crs = crs,
            filterCrs = prefs[filterKey(id)].orEmpty(),
            followLocation = BuildConfig.LOCATION && prefs[followKey(id)] == true,
            platforms = prefs[platformsKey(id)].orEmpty(),
            keepNoPlatform = prefs[keepNoPlatformKey(id)] ?: false,
            callingPointTimes = prefs[timesKey(id)],
        )
    }

    suspend fun set(id: Int, config: WidgetConfig) {
        context.widgetDataStore.edit {
            it[crsKey(id)] = config.crs.trim().uppercase()
            it[filterKey(id)] = config.filterCrs.trim().uppercase()
            it[followKey(id)] = BuildConfig.LOCATION && config.followLocation
            it[platformsKey(id)] = config.platforms.mapTo(HashSet()) { p -> p.trim().uppercase() }
            it[keepNoPlatformKey(id)] = config.keepNoPlatform
            config.callingPointTimes?.let { times -> it[timesKey(id)] = times } ?: it.remove(timesKey(id))
        }
    }

    suspend fun delete(ids: IntArray) {
        context.widgetDataStore.edit { prefs ->
            ids.forEach {
                prefs.remove(crsKey(it))
                prefs.remove(filterKey(it))
                prefs.remove(followKey(it))
                prefs.remove(platformsKey(it))
                prefs.remove(keepNoPlatformKey(it))
                prefs.remove(timesKey(it))
            }
        }
    }

    private fun crsKey(id: Int) = stringPreferencesKey("w${id}_crs")
    private fun filterKey(id: Int) = stringPreferencesKey("w${id}_filter")
    private fun followKey(id: Int) = booleanPreferencesKey("w${id}_follow")
    private fun platformsKey(id: Int) = stringSetPreferencesKey("w${id}_platforms")
    private fun keepNoPlatformKey(id: Int) = booleanPreferencesKey("w${id}_keep_no_platform")
    private fun timesKey(id: Int) = booleanPreferencesKey("w${id}_calling_point_times")
}
