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

    override suspend fun board(query: BoardQuery): Board = client.board(query.crs, query.filterCrs).toBoard()
}
