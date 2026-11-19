package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.ProxyDepartureRepository
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.CallingAt
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.core.model.PlatformOrder
import io.github.inputbiscuit.dotboard.core.model.onPlatforms
import io.github.inputbiscuit.dotboard.core.model.platforms
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardRenderer
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PlatformAndTimesTest {

    private fun departure(platform: String?, vararg stops: CallingAt) = Departure(
        scheduled = "10:30",
        status = DepartureStatus.OnTime,
        destination = "Reading",
        via = null,
        platform = platform,
        operator = null,
        callingPoints = stops.toList(),
        cancelReason = null,
        delayReason = null,
        serviceId = null,
    )

    private fun board(vararg deps: Departure) = Board("London Paddington", "PAD", null, null, deps.toList(), emptyList())

    private fun callingText(d: Departure, times: Boolean, use24h: Boolean = true): String? =
        BoardRenderer(margins = false)
            .plan(BoardFrame(BoardContent.Departures(board(d)), LocalTime.NOON, use24h, callingPointTimes = times), 256, 64)
            .callingAtInfo?.text

    private val stops = arrayOf(
        CallingAt("Slough", "SLO", "10:42", DepartureStatus.OnTime),
        CallingAt("Maidenhead", "MAI", "10:49", DepartureStatus.Expected("10:53")),
        CallingAt("Twyford", "TWY", "10:55", DepartureStatus.Cancelled),
        CallingAt("Reading", "RDG", "13:02", DepartureStatus.Delayed),
    )

    @Test fun `calling points stay plain by default`() {
        assertEquals("Slough, Maidenhead, Twyford and Reading.", callingText(departure("1", *stops), times = false))
    }

    @Test fun `calling point times use the expected time when late and leave out cancelled stops`() {
        assertEquals(
            "Slough (10:42), Maidenhead (10:53) and Reading (13:02).",
            callingText(departure("1", *stops), times = true),
        )
    }

    @Test fun `calling point times follow the 12 hour clock`() {
        assertEquals(
            "Slough (10:42), Maidenhead (10:53) and Reading (1:02).",
            callingText(departure("1", *stops), times = true, use24h = false),
        )
    }

    @Test fun `a stop with no time is shown without one`() {
        val d = departure("1", CallingAt("Reading", "RDG", null, DepartureStatus.Unknown))
        assertEquals("Reading only.", callingText(d, times = true))
    }

    @Test fun `platforms sort the way stations number them`() {
        assertEquals(
            listOf("1", "2", "9", "10", "10A", "10B", "11", "A", "B"),
            listOf("10A", "B", "11", "2", "10", "A", "1", "10B", "9").sortedWith(PlatformOrder),
        )
    }

    @Test fun `board platforms are distinct, trimmed and ignore missing ones`() {
        val b = board(departure("4"), departure(" 10a "), departure(null), departure(""), departure("4"))
        assertEquals(listOf("4", "10A"), b.platforms())
    }

    @Test fun `platform filter keeps only chosen platforms, and unknown ones only if asked`() {
        val b = board(departure("1"), departure("2"), departure(null), departure("10A"))
        assertSame(b, b.onPlatforms(emptySet(), keepUnknown = false), "no filter: same board")
        assertEquals(listOf("1", "10A"), b.onPlatforms(setOf("1", "10a"), keepUnknown = false).departures.map { it.platform })
        assertEquals(listOf("1", null), b.onPlatforms(setOf("1"), keepUnknown = true).departures.map { it.platform })
    }

    @Test fun `bigger boards get their own cache key`() {
        assertEquals("PAD", BoardQuery("PAD").key)
        assertEquals("PAD>RDG#50", BoardQuery.of("pad", "rdg", BoardQuery.FILTERED_ROWS).key)
        assertEquals("PAD#150", BoardQuery("PAD", rows = BoardQuery.DISCOVERY_ROWS).key)
        assertFailsWith<IllegalArgumentException> { BoardQuery("PAD", rows = 20) }
    }

    @Test fun `proxy requests bigger boards in canonical form`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val fixture = requireNotNull(javaClass.classLoader.getResource("depboard_PAD.json")).readText()
            repeat(2) { server.enqueue(MockResponse.Builder().body(fixture).build()) }
            val repo = ProxyDepartureRepository(server.url("/").toString())

            repo.board(BoardQuery.of("PAD", "RDG", BoardQuery.FILTERED_ROWS))
            var url = server.takeRequest().url
            assertEquals(listOf("filter", "rows"), url.queryParameterNames.toList())
            assertEquals("50", url.queryParameter("rows"))

            repo.board(BoardQuery("PAD"))
            url = server.takeRequest().url
            assertEquals(0, url.querySize, "the default board has no rows parameter")
        }
    }
}
