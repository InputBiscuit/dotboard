package io.github.inputbiscuit.dotboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.CachingDepartureRepository
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.PlatformOrder
import io.github.inputbiscuit.dotboard.core.model.platforms
import kotlinx.coroutines.flow.first

private val Context.platformDataStore by preferencesDataStore(name = "platforms")

/**
 * The platforms each station uses, for the board setup's platform filter. The API can't list them, so they're
 * worked out from one big board (up to 150 services over 2 hours) and kept for a week, and any new ones seen on
 * normal boards are added as they turn up.
 */
class PlatformStore(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    /**
     * The station's platforms, fetching the big board if there's no list from the last week. Empty if the station
     * shows no platform numbers. Throws `DepartureException` if the board can't be fetched.
     *
     * @param live false for sample data: the list is worked out from [repo] each time and never saved, so sample
     *   platforms don't end up in a real station's list (or the other way round).
     */
    suspend fun platforms(crs: String, repo: CachingDepartureRepository, live: Boolean = true): List<String> {
        val code = crs.trim().uppercase()
        if (!live) return discover(code, repo)
        val prefs = context.platformDataStore.data.first()
        val known = prefs[platformsKey(code)]
        val checkedAt = prefs[checkedKey(code)]
        if (known != null && checkedAt != null && clock() - checkedAt in 0..MAX_AGE_MILLIS) {
            return known.sortedWith(PlatformOrder)
        }
        val seen = discover(code, repo)
        context.platformDataStore.edit {
            it[platformsKey(code)] = (known.orEmpty() + seen).toSet()
            it[checkedKey(code)] = clock()
        }
        return (known.orEmpty() + seen).distinct().sortedWith(PlatformOrder)
    }

    private suspend fun discover(crs: String, repo: CachingDepartureRepository): List<String> =
        repo.cached(BoardQuery(crs, rows = BoardQuery.DISCOVERY_ROWS), maxAgeMillis = 60 * 60_000L).board.platforms()

    /** Adds platforms from a normal (live, not sample) board to the station's list, if it has one. */
    suspend fun record(board: Board) {
        val code = board.crs.trim().uppercase().takeIf { it.length == 3 } ?: return
        val seen = board.platforms().takeIf { it.isNotEmpty() } ?: return
        val known = context.platformDataStore.data.first()[platformsKey(code)] ?: return
        if (known.containsAll(seen)) return
        context.platformDataStore.edit { it[platformsKey(code)] = known + seen }
    }

    private fun platformsKey(crs: String) = stringSetPreferencesKey("${crs}_platforms")
    private fun checkedKey(crs: String) = longPreferencesKey("${crs}_checked")

    private companion object {
        const val MAX_AGE_MILLIS = 7 * 24 * 60 * 60_000L
    }
}
