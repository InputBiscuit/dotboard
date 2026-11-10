package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.ldbws.LdbwsClient
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.toBoard
import okhttp3.OkHttpClient

/** Live data from the Rail Data Marketplace LDBWS API. The key is read on every call so changes apply at once. */
class LdbwsDepartureRepository(
    private val apiKey: suspend () -> String,
    private val http: OkHttpClient = LdbwsClient.defaultHttpClient,
    private val numRows: Int = 10,
) : DepartureRepository {

    override suspend fun board(query: BoardQuery): Board =
        LdbwsClient(apiKey(), http)
            .getDepBoardWithDetails(query.crs, numRows = numRows, filterCrs = query.filterCrs)
            .toBoard()
}
