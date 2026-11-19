package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.ldbws.LdbwsClient
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.toBoard
import okhttp3.OkHttpClient

/** Live data from the Rail Data Marketplace LDBWS API. The key is read on every call so changes apply at once. */
class LdbwsDepartureRepository(
    private val apiKey: suspend () -> String,
    private val http: OkHttpClient = LdbwsClient.defaultHttpClient,
) : DepartureRepository {

    override suspend fun board(query: BoardQuery): Board =
        LdbwsClient(apiKey(), http)
            .getDepBoardWithDetails(
                query.crs,
                numRows = query.rows,
                filterCrs = query.filterCrs,
                timeWindow = BoardQuery.DISCOVERY_WINDOW_MINUTES.takeIf { query.rows == BoardQuery.DISCOVERY_ROWS },
            )
            .toBoard()
}
