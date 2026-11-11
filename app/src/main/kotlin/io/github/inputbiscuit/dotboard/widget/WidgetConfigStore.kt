package io.github.inputbiscuit.dotboard.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.widgetDataStore by preferencesDataStore(name = "widgets")

data class WidgetConfig(val crs: String, val filterCrs: String)

/** Each widget's own station and optional "calling at" filter, keyed by app widget id. */
class WidgetConfigStore(private val context: Context) {

    suspend fun get(id: Int): WidgetConfig? {
        val prefs = context.widgetDataStore.data.first()
        val crs = prefs[crsKey(id)] ?: return null
        return WidgetConfig(crs, prefs[filterKey(id)].orEmpty())
    }

    suspend fun set(id: Int, config: WidgetConfig) {
        context.widgetDataStore.edit {
            it[crsKey(id)] = config.crs.trim().uppercase()
            it[filterKey(id)] = config.filterCrs.trim().uppercase()
            it.remove(legacyFollowKey(id))
        }
    }

    suspend fun delete(ids: IntArray) {
        context.widgetDataStore.edit { prefs ->
            ids.forEach {
                prefs.remove(crsKey(it))
                prefs.remove(filterKey(it))
                prefs.remove(legacyFollowKey(it))
            }
        }
    }

    private fun crsKey(id: Int) = stringPreferencesKey("w${id}_crs")
    private fun filterKey(id: Int) = stringPreferencesKey("w${id}_filter")
    // Set by older versions' location-following boards; those boards now keep their last station.
    private fun legacyFollowKey(id: Int) = booleanPreferencesKey("w${id}_follow")
}
