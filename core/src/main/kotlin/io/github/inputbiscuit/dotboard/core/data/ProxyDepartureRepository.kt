package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.ldbws.LdbwsClient
import io.github.inputbiscuit.dotboard.core.ldbws.ProxyClient
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.toBoard
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Live data through Dotboard's API proxy, for users who haven't entered their own key. */
class ProxyDepartureRepository(
    baseUrl: String,
    http: OkHttpClient = LdbwsClient.defaultHttpClient,
) : DepartureRepository {

    private val client = ProxyClient(baseUrl.toHttpUrl(), http)

    override suspend fun board(query: BoardQuery): Board = try {
        client.board(query.crs, query.filterCrs, query.rows).toBoard()
    } catch (e: DepartureException.Http) {
        // A proxy from before bigger boards rejects "rows": a filtered board falls back to a normal one rather than
        // no board. Platform discovery doesn't, so a 10-row board isn't taken as the station's full platform list.
        if (e.code != 400 || query.rows != BoardQuery.FILTERED_ROWS) throw e
        client.board(query.crs, query.filterCrs).toBoard()
    }
}
