package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.stations.StationList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationListTest {

    private val small = StationList.parse(
        """
        crs,name
        PAD,London Paddington
        RDG,Reading
        RDW,Reading West
        SLO,Slough
        bad line
        """.trimIndent(),
    )

    @Test fun `skips header and malformed lines`() {
        assertEquals(4, small.stations.size)
    }

    @Test fun `looks up by CRS case-insensitively`() {
        assertEquals("London Paddington", small["pad"]?.name)
    }

    @Test fun `search ranks exact CRS then prefix then contains`() {
        assertEquals(listOf("RDG", "RDW"), small.search("read").map { it.crs })
        assertEquals("SLO", small.search("slo").first().crs)
        assertEquals(listOf("RDW"), small.search("west").map { it.crs })
    }

    @Test fun `parses locations when present`() {
        val list = StationList.parse("crs,name,lat,lon\nPAD,London Paddington,51.517095,-0.177317\nRDG,Reading,,\n")
        assertEquals(51.517095, list["PAD"]?.lat)
        assertEquals(-0.177317, list["PAD"]?.lon)
        assertEquals(null, list["RDG"]?.lat)
    }

    @Test fun `haversine distance is about right`() {
        // London Paddington to London Euston is roughly 3.3 km.
        val d = StationList.distanceMetres(51.517095, -0.177317, 51.528853, -0.134191)
        assertTrue(d in 3_100.0..3_400.0, "got $d")
        assertEquals(0.0, StationList.distanceMetres(51.0, -1.0, 51.0, -1.0))
    }

    @Test fun `nearest uses the bundled locations`() {
        val list = StationList.parse(File("../app/src/main/assets/stations.csv").readText())
        // Standing at London Paddington: Paddington first, then other nearby London stations.
        val near = list.nearest(51.517095, -0.177317, limit = 3)
        assertEquals("PAD", near.first().station.crs)
        assertTrue(near.first().distanceMetres < 50)
        assertTrue(near.zipWithNext().all { (a, b) -> a.distanceMetres <= b.distanceMetres })
        assertTrue(near.all { it.distanceMetres < 10_000 })
        // Central London (Trafalgar Square): Charing Cross is closest.
        assertEquals("CHX", list.nearest(51.5080, -0.1281, limit = 1).single().station.crs)
    }

    @Test fun `following keeps the current station unless another is clearly nearer`() {
        val list = StationList.parse(
            """
            crs,name,lat,lon
            AAA,Alpha,51.0000,-1.0000
            BBB,Bravo,51.0000,-0.9900
            """.trimIndent(),
        )
        // Alpha and Bravo are ~700 m apart. Standing just on Bravo's side of halfway: stay on Alpha.
        assertEquals("AAA", list.followNearest(51.0, -0.9945, currentCrs = "AAA")?.crs)
        // Right next to Bravo: switch.
        assertEquals("BBB", list.followNearest(51.0, -0.9902, currentCrs = "AAA")?.crs)
        // No current station yet: just the nearest.
        assertEquals("BBB", list.followNearest(51.0, -0.9945, currentCrs = null)?.crs)
        // Unknown current station: the nearest.
        assertEquals("BBB", list.followNearest(51.0, -0.9945, currentCrs = "ZZZ")?.crs)
    }

    @Test fun `stations without locations are skipped by nearest`() {
        val list = StationList.parse("crs,name\nPAD,London Paddington\n")
        assertTrue(list.nearest(51.52, -0.18).isEmpty())
    }

    @Test fun `bundled asset parses completely and contains London Paddington`() {
        val asset = File("../app/src/main/assets/stations.csv")
        val list = StationList.parse(asset.readText())
        val lines = asset.readLines().count { it.isNotBlank() } - 1
        assertEquals(lines, list.stations.size)
        assertEquals("London Paddington", list["PAD"]?.name)
        assertTrue(list.stations.all { it.crs.matches(Regex("[A-Z]{3}")) })
        assertTrue(list.stations.all { it.lat != null && it.lon != null }, "every bundled station has a location")
    }
}
