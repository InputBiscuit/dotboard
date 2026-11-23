package io.github.inputbiscuit.dotboard.data

import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.CachingDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.PlatformOrder
import io.github.inputbiscuit.dotboard.core.model.onPlatforms
import io.github.inputbiscuit.dotboard.core.model.withoutDeparted
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.toBoardMessage
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What to draw, and if it's a cached board shown because the latest refresh failed (e.g. no connection or the
 * proxy is unreachable), when that board was fetched.
 */
data class LoadedBoard(val content: BoardContent, val updatedAt: LocalTime? = null)

/**
 * [board] filtered to [platforms]. If that leaves nothing although the station has trains, it says so, as big
 * stations (e.g. London termini) often show a platform only shortly before the train leaves.
 */
fun platformBoard(board: Board, platforms: Set<String>, keepUnknown: Boolean): BoardContent {
    val filtered = board.onPlatforms(platforms, keepUnknown)
    if (filtered.departures.isNotEmpty() || board.departures.isEmpty()) return BoardContent.Departures(filtered)
    val which = platforms.sortedWith(PlatformOrder).joinToString(", ")
    val label = if (platforms.size == 1) "Platform $which" else "Platforms $which"
    return BoardContent.Message("No trains yet", "$label: platforms are often shown just before departure.", board.stationName)
}

/**
 * Fetches a board no older than [maxAgeMillis]. If that fails, shows the last board fetched for the query (minus
 * departed trains) with its fetch time, rather than going blank; only with no cached board at all does the error
 * itself show.
 */
suspend fun CachingDepartureRepository.load(query: BoardQuery, maxAgeMillis: Long): LoadedBoard {
    val now = LocalTime.now()
    return try {
        LoadedBoard(BoardContent.Departures(cached(query, maxAgeMillis).board.withoutDeparted(now)))
    } catch (e: DepartureException) {
        val last = peek(query) ?: return LoadedBoard(e.toBoardMessage())
        val fetchedAt = Instant.ofEpochMilli(last.fetchedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        LoadedBoard(BoardContent.Departures(last.board.withoutDeparted(now)), updatedAt = fetchedAt)
    }
}
