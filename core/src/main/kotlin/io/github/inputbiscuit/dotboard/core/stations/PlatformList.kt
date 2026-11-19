package io.github.inputbiscuit.dotboard.core.stations

import io.github.inputbiscuit.dotboard.core.model.PlatformOrder

/**
 * The bundled platforms at each station, from OpenStreetMap (ODbL-1.0, see README). The API can't list a station's
 * platforms, so this is where the board setup starts; platforms seen on live boards are added to it.
 *
 * Bundled format is CSV `crs,platforms` with `#` comment lines, a header row, and space-separated platforms, e.g.
 * `PAD,1 2 3 4 5 6 7 8 9 10 11 12 14`. Built by `tools/platforms/build_platforms.py`.
 */
class PlatformList(private val byCrs: Map<String, List<String>>) {

    /** The station's platforms in [PlatformOrder], or empty if there's no list for it. */
    operator fun get(crs: String): List<String> = byCrs[crs.trim().uppercase()].orEmpty()

    val size: Int get() = byCrs.size

    companion object {
        fun parse(csv: String): PlatformList = PlatformList(
            csv.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .drop(1)
                .mapNotNull { line ->
                    val crs = line.substringBefore(',').trim().uppercase()
                    val platforms = line.substringAfter(',', "").split(' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                    if (crs.length != 3 || platforms.isEmpty()) null else crs to platforms.distinct().sortedWith(PlatformOrder)
                }
                .toMap(),
        )
    }
}
