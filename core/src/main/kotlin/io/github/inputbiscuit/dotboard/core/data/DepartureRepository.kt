package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.model.Board
import java.io.IOException

/**
 * What to show: departures from [crs], optionally only services calling at [filterCrs].
 *
 * @param rows how many services to ask for: [ROWS] for a normal board, [FILTERED_ROWS] for a board filtered to
 *   some platforms in the app (so one platform at a busy station still has a few trains), or [DISCOVERY_ROWS]
 *   (over a 2-hour window) to find out which platforms a station uses.
 */
data class BoardQuery(val crs: String, val filterCrs: String? = null, val rows: Int = ROWS) {
    init {
        require(crs.length == 3) { "CRS must be 3 letters: $crs" }
        require(rows in ALLOWED_ROWS) { "rows must be one of $ALLOWED_ROWS: $rows" }
    }

    /** Stable key for caching and sharing a board between widgets. */
    val key: String
        get() = crs.uppercase() + (filterCrs?.uppercase()?.let { ">$it" } ?: "") + (if (rows != ROWS) "#$rows" else "")

    companion object {
        const val ROWS = 10
        const val FILTERED_ROWS = 50
        const val DISCOVERY_ROWS = 150

        /** Minutes ahead a [DISCOVERY_ROWS] board covers (the API's maximum). */
        const val DISCOVERY_WINDOW_MINUTES = 120

        val ALLOWED_ROWS = setOf(ROWS, FILTERED_ROWS, DISCOVERY_ROWS)

        fun of(crs: String, filterCrs: String?, rows: Int = ROWS): BoardQuery =
            BoardQuery(crs.trim().uppercase(), filterCrs?.trim()?.uppercase()?.takeIf { it.length == 3 }, rows)
    }
}

/** Everything that can go wrong fetching a board, in terms the UI (and the board's error screens) need. */
sealed class DepartureException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey : DepartureException("No API key")
    class InvalidApiKey(detail: String?) : DepartureException(detail ?: "Invalid API key")
    class Network(cause: IOException) : DepartureException(cause.message ?: "No connection", cause)
    class Http(val code: Int, detail: String?) : DepartureException("HTTP $code${detail?.let { ": $it" } ?: ""}")
    class BadResponse(cause: Throwable) : DepartureException("Unexpected response from server", cause)
}

/** Source of departure boards. Implementations throw [DepartureException] on failure. */
interface DepartureRepository {
    suspend fun board(query: BoardQuery): Board
}
