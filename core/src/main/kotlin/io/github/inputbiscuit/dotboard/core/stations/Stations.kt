package io.github.inputbiscuit.dotboard.core.stations

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Station(val crs: String, val name: String, val lat: Double? = null, val lon: Double? = null)

/** A station and how far it is from somewhere, in metres. */
data class NearbyStation(val station: Station, val distanceMetres: Double)

/**
 * The bundled UK station list (CRS code, name and location).
 *
 * Source: davwheat/uk-railway-stations `stations.csv`, ODbL-1.0 — see README for attribution.
 * Bundled format is CSV `crs,name,lat,lon` with a header row and no quoting (names contain no commas).
 * The older two-column `crs,name` format still parses, without locations.
 */
class StationList(val stations: List<Station>) {

    private val byCrs = stations.associateBy { it.crs }

    operator fun get(crs: String): Station? = byCrs[crs.trim().uppercase()]

    /** Exact CRS match first, then name prefix matches, then other name matches. */
    fun search(query: String, limit: Int = 8): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val exact = if (q.length == 3) listOfNotNull(get(q)) else emptyList()
        val prefix = stations.filter { it.name.startsWith(q, ignoreCase = true) }
        val contains = stations.filter { it.name.contains(q, ignoreCase = true) }
        return (exact + prefix + contains).distinct().take(limit)
    }

    /** The [limit] stations closest to ([lat], [lon]) as the crow flies, nearest first. No network needed. */
    fun nearest(lat: Double, lon: Double, limit: Int = 5): List<NearbyStation> =
        stations.mapNotNull { s ->
            if (s.lat == null || s.lon == null) null else NearbyStation(s, distanceMetres(lat, lon, s.lat, s.lon))
        }.sortedBy { it.distanceMetres }.take(limit)

    /**
     * The station a location-following board should show: the nearest one, unless [currentCrs] is less than
     * [marginMetres] further away than it. That stops a board halfway between two stations swapping back and
     * forth on every refresh.
     */
    fun followNearest(lat: Double, lon: Double, currentCrs: String?, marginMetres: Double = 300.0): Station? {
        val nearest = nearest(lat, lon, limit = 1).firstOrNull() ?: return null
        val current = currentCrs?.let { get(it) }?.takeIf { it.lat != null && it.lon != null } ?: return nearest.station
        val currentDistance = distanceMetres(lat, lon, current.lat!!, current.lon!!)
        return if (currentDistance - nearest.distanceMetres < marginMetres) current else nearest.station
    }

    companion object {
        private const val EARTH_RADIUS_METRES = 6_371_000.0

        /** Great-circle (haversine) distance; plenty accurate for "which station is nearest". */
        fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_METRES * asin(sqrt(a))
        }

        fun parse(csv: String): StationList = StationList(
            csv.lineSequence()
                .drop(1)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val parts = line.split(',')
                    if (parts.size < 2 || parts[0].length != 3) return@mapNotNull null
                    Station(
                        crs = parts[0].uppercase(),
                        name = parts[1].trim(),
                        lat = parts.getOrNull(2)?.trim()?.toDoubleOrNull(),
                        lon = parts.getOrNull(3)?.trim()?.toDoubleOrNull(),
                    )
                }
                .toList(),
        )
    }
}
