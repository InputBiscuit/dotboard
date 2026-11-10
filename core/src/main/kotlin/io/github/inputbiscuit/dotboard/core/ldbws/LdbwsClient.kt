package io.github.inputbiscuit.dotboard.core.ldbws

import io.github.inputbiscuit.dotboard.core.data.DepartureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class FilterType(val wire: String) { TO("to"), FROM("from") }

/**
 * Client for the Rail Data Marketplace "Live Departure Board" REST API (LDBWS JSON).
 *
 * Auth is the product's consumer key sent as the `x-apikey` header. Failures throw [DepartureException].
 */
class LdbwsClient(
    private val apiKey: String,
    private val http: OkHttpClient = defaultHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {

    /**
     * Departures from [crs] including subsequent calling points.
     *
     * @param numRows 1..150 services to return.
     * @param filterCrs only services that call at ([FilterType.TO]) or came from ([FilterType.FROM]) this station.
     * @param timeOffset minutes from now to start the board, -120..120.
     * @param timeWindow minutes after the start to include, -120..120.
     */
    suspend fun getDepBoardWithDetails(
        crs: String,
        numRows: Int = 10,
        filterCrs: String? = null,
        filterType: FilterType = FilterType.TO,
        timeOffset: Int? = null,
        timeWindow: Int? = null,
    ): StationBoard {
        if (apiKey.isBlank()) throw DepartureException.MissingApiKey()
        require(crs.length == 3) { "CRS must be 3 letters" }

        val url = baseUrl.newBuilder()
            .addPathSegment("GetDepBoardWithDetails")
            .addPathSegment(crs.uppercase())
            .addQueryParameter("numRows", numRows.coerceIn(1, 150).toString())
            .apply {
                if (!filterCrs.isNullOrBlank()) {
                    addQueryParameter("filterCrs", filterCrs.uppercase())
                    addQueryParameter("filterType", filterType.wire)
                }
                timeOffset?.let { addQueryParameter("timeOffset", it.coerceIn(-120, 120).toString()) }
                timeWindow?.let { addQueryParameter("timeWindow", it.coerceIn(-120, 120).toString()) }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("x-apikey", apiKey.trim())
            .header("Accept", "application/json")
            .build()

        val body = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) throw httpError(response.code, text)
                    text
                }
            } catch (e: IOException) {
                throw DepartureException.Network(e)
            }
        }

        return try {
            json.decodeFromString(StationBoard.serializer(), body)
        } catch (e: SerializationException) {
            throw DepartureException.BadResponse(e)
        } catch (e: IllegalArgumentException) {
            throw DepartureException.BadResponse(e)
        }
    }

    private fun httpError(code: Int, body: String): DepartureException {
        val fault = runCatching { json.decodeFromString(FaultResponse.serializer(), body).fault }.getOrNull()
        val errorCode = fault?.detail?.errorcode.orEmpty()
        return when {
            code == 401 || code == 403 || errorCode.contains("ApiKey", ignoreCase = true) ->
                DepartureException.InvalidApiKey(fault?.faultstring)
            else -> DepartureException.Http(code, fault?.faultstring)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL =
            "https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120"

        internal val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
