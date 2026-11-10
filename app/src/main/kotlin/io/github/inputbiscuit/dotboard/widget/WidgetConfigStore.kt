package io.github.inputbiscuit.dotboard.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.widgetDataStore by preferencesDataStore(name = "widgets")

/**
 * @param crs the board's station. For a [followLocation] board it's the station last resolved from the
 *   user's location, so redraws don't need a fresh location.
 * @param followLocation re-pick the nearest station on each background refresh (and tap).
 */
data class WidgetConfig(val crs: String, val filterCrs: String, val followLocation: Boolean = false)

/** Each widget's own station and optional "calling at" filter, keyed by app widget id. */
class WidgetConfigStore(private val context: Context) {

    suspend fun get(id: Int): WidgetConfig? {
        val prefs = context.widgetDataStore.data.first()
        val crs = prefs[crsKey(id)] ?: return null
        return WidgetConfig(crs, prefs[filterKey(id)].orEmpty(), prefs[followKey(id)] ?: false)
    }

    suspend fun set(id: Int, config: WidgetConfig) {
        context.widgetDataStore.edit {
            it[crsKey(id)] = config.crs.trim().uppercase()
            it[filterKey(id)] = config.filterCrs.trim().uppercase()
            it[followKey(id)] = config.followLocation
        }
    }

    suspend fun delete(ids: IntArray) {
        context.widgetDataStore.edit { prefs ->
            ids.forEach {
                prefs.remove(crsKey(it))
                prefs.remove(filterKey(it))
                prefs.remove(followKey(it))
            }
        }
    }

    private fun crsKey(id: Int) = stringPreferencesKey("w${id}_crs")
    private fun filterKey(id: Int) = stringPreferencesKey("w${id}_filter")
    private fun followKey(id: Int) = booleanPreferencesKey("w${id}_follow")
}
