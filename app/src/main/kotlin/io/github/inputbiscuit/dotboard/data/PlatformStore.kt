package io.github.inputbiscuit.dotboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.CachingDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.PlatformOrder
import io.github.inputbiscuit.dotboard.core.model.platforms
import io.github.inputbiscuit.dotboard.core.stations.PlatformList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.platformDataStore by preferencesDataStore(name = "platforms")

/**
 * The platforms each station uses, for the board setup's platform filter. The API can't list them, so the list
 * starts from the bundled OpenStreetMap one (`assets/platforms.csv`), adds every platform on one big board (up to
 * 150 services over 2 hours, checked once a week), and then any new ones seen on normal boards as they turn up.
 */
class PlatformStore(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    private val mutex = Mutex()
    private var bundled: PlatformList? = null

    /** The bundled list for [crs] (empty if there's none), loaded from assets on first use. */
    suspend fun bundled(crs: String): List<String> = mutex.withLock {
        bundled ?: withContext(Dispatchers.IO) {
            context.assets.open("platforms.csv").bufferedReader().use { PlatformList.parse(it.readText()) }
        }.also { bundled = it }
    }[crs]

    /**
     * The station's platforms: the bundled list plus any seen on boards, fetching the big board if it hasn't been
     * checked in the last week. Empty if neither has any. Throws `DepartureException` only if the board can't be
     * fetched and there's no bundled list to fall back on.
     *
     * @param live false for sample data: the list comes from [repo]'s sample board alone and is never saved, so
     *   sample platforms don't end up in a real station's list (or the other way round).
     */
    suspend fun platforms(crs: String, repo: CachingDepartureRepository, live: Boolean = true): List<String> {
        val code = crs.trim().uppercase()
        if (!live) return discover(code, repo)
        val prefs = context.platformDataStore.data.first()
        val known = bundled(code) + prefs[platformsKey(code)].orEmpty()
        val checkedAt = prefs[checkedKey(code)]
        if (checkedAt != null && clock() - checkedAt in 0..MAX_AGE_MILLIS) return known.distinct().sortedWith(PlatformOrder)
        val seen = try {
            discover(code, repo)
        } catch (e: DepartureException) {
            if (known.isEmpty()) throw e
            return known.distinct().sortedWith(PlatformOrder)
        }
        context.platformDataStore.edit {
            it[platformsKey(code)] = prefs[platformsKey(code)].orEmpty() + seen
            it[checkedKey(code)] = clock()
        }
        return (known + seen).distinct().sortedWith(PlatformOrder)
    }

    private suspend fun discover(crs: String, repo: CachingDepartureRepository): List<String> =
        repo.cached(BoardQuery(crs, rows = BoardQuery.DISCOVERY_ROWS), maxAgeMillis = 60 * 60_000L).board.platforms()

    /** Adds platforms from a normal (live, not sample) board that aren't in the bundled or saved list yet. */
    suspend fun record(board: Board) {
        val code = board.crs.trim().uppercase().takeIf { it.length == 3 } ?: return
        val seen = board.platforms().takeIf { it.isNotEmpty() } ?: return
        val saved = context.platformDataStore.data.first()[platformsKey(code)].orEmpty()
        val new = seen - saved - bundled(code).toSet()
        if (new.isEmpty()) return
        context.platformDataStore.edit { it[platformsKey(code)] = saved + new }
    }

    private fun platformsKey(crs: String) = stringSetPreferencesKey("${crs}_platforms")
    private fun checkedKey(crs: String) = longPreferencesKey("${crs}_checked")

    private companion object {
        const val MAX_AGE_MILLIS = 7 * 24 * 60 * 60_000L
    }
}
