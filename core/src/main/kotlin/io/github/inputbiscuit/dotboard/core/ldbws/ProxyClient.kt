package io.github.inputbiscuit.dotboard.core.ldbws

import io.github.inputbiscuit.dotboard.core.data.DepartureException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client for Dotboard's API proxy (see `proxy/`): `GET {base}/v1/board/{CRS}[?filter={CRS}]`, which returns
 * the Live Departure Board JSON fetched with the proxy's own key. No key is sent from the app.
 */
class ProxyClient(
    private val baseUrl: HttpUrl,
    private val http: OkHttpClient = LdbwsClient.defaultHttpClient,
) {

    /** Departures from [crs] (with calling points), optionally only services calling at [filterCrs]. */
    suspend fun board(crs: String, filterCrs: String? = null): StationBoard {
        require(crs.length == 3) { "CRS must be 3 letters" }
        // The proxy only accepts (and caches) upper-case codes and a single "filter" parameter.
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/board")
            .addPathSegment(crs.uppercase())
            .apply { if (!filterCrs.isNullOrBlank()) addQueryParameter("filter", filterCrs.uppercase()) }
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        // Any error from the proxy (including its own key being rejected upstream) is a service problem for the
        // user, not something they can fix with a key, so it's never reported as InvalidApiKey.
        return fetchStationBoard(http, request) { code, _ -> DepartureException.Http(code, null) }
    }
}
