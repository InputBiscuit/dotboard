package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.model.Board
import java.io.IOException

/** What to show: departures from [crs], optionally only services calling at [filterCrs]. */
data class BoardQuery(val crs: String, val filterCrs: String? = null) {
    init {
        require(crs.length == 3) { "CRS must be 3 letters: $crs" }
    }

    /** Stable key for caching and sharing a board between widgets. */
    val key: String get() = crs.uppercase() + (filterCrs?.uppercase()?.let { ">$it" } ?: "")

    companion object {
        fun of(crs: String, filterCrs: String?): BoardQuery =
            BoardQuery(crs.trim().uppercase(), filterCrs?.trim()?.uppercase()?.takeIf { it.length == 3 })
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
