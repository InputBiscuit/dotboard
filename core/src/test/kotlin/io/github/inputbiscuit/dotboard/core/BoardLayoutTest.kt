package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.CallingAt
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardPlan
import io.github.inputbiscuit.dotboard.core.render.BoardRenderer
import io.github.inputbiscuit.dotboard.core.render.DotFonts
import io.github.inputbiscuit.dotboard.core.render.Segment
import io.github.inputbiscuit.dotboard.core.render.SpareSpace
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The station name across the top, what fills spare rows, and the clock staying at the bottom. */
class BoardLayoutTest {

    private val renderer = BoardRenderer(margins = false)

    /** 11 lines of text above the clock. */
    private val tallRows = 160

    private fun departure(n: Int, stops: List<String> = listOf("Slough", "Reading")) = Departure(
        scheduled = "10:%02d".format(n),
        status = DepartureStatus.OnTime,
        destination = "Train $n",
        via = null,
        platform = "1",
        operator = null,
        callingPoints = stops.map { CallingAt(it, null, null, DepartureStatus.OnTime) },
        cancelReason = null,
        delayReason = null,
        serviceId = null,
    )

    private fun board(count: Int, name: String = "London Paddington") =
        Board(name, "PAD", null, null, (1..count).map { departure(it) }, emptyList())

    private fun plan(
        content: BoardContent,
        rows: Int = 64,
        cols: Int = 256,
        stationName: Boolean = false,
        spare: SpareSpace = SpareSpace.TRAINS,
    ): BoardPlan = renderer.plan(BoardFrame(content, LocalTime.NOON, stationName = stationName, spareSpace = spare), cols, rows)

    /** One letter per line: T(itle), D(eparture), C(alling at, scrolling), S(till calling at), A(lternating), M(essage). */
    private fun BoardPlan.shape(): String = inner.lines.joinToString("") {
        when (it) {
            is BoardPlan.Line.Text -> if (it.bold && it.y == 0 && inner.lines.size > 1 && it === inner.lines[0]) "T" else "M"
            is BoardPlan.Line.DepartureRow -> "D"
            is BoardPlan.Line.CallingAt -> "C"
            is BoardPlan.Line.CallingAtText -> "S"
            is BoardPlan.Line.Alternating -> "A"
        }
    }

    private fun BoardPlan.title() = (inner.lines.first() as BoardPlan.Line.Text).text

    private fun BoardPlan.clockTop() = segments.single { it.kind == Segment.Kind.CLOCK }.top

    @Test fun `station name takes the top line of a standard board, replacing the alternating row`() {
        val named = plan(BoardContent.Departures(board(5)), stationName = true)
        assertEquals("TDC", named.shape())
        assertEquals("London Paddington Station", named.title())
        assertEquals(listOf(0, 12, 24), named.inner.lines.map { it.y })
        assertEquals("DCA", plan(BoardContent.Departures(board(5))).shape())
    }

    @Test fun `station name drops a county in brackets, then Station, then whole words to fit`() {
        assertEquals("Adlington Station", plan(BoardContent.Departures(board(3, "Adlington (Cheshire)")), stationName = true).title())
        val long = "Northumberland Park (Tyne and Wear)"
        val narrow = plan(BoardContent.Departures(board(3, long)), cols = 130, stationName = true)
        assertTrue(DotFonts.default.bold.width(narrow.title()) <= 130, narrow.title())
        assertTrue(narrow.title().startsWith("Northumberland"), narrow.title())
    }

    @Test fun `station name is left out when the board has no room for it`() {
        assertEquals("D", plan(BoardContent.Departures(board(5)), rows = 40, stationName = true).shape())
    }

    @Test fun `messages that know the station show it, and No services doesn't repeat it`() {
        val message = plan(BoardContent.Message("No trains yet", "Platform 9", station = "London Paddington"), stationName = true)
        assertEquals("London Paddington Station", message.title())
        val empty = plan(BoardContent.Departures(board(0)), stationName = true)
        assertEquals(listOf("London Paddington Station", "No services"), empty.inner.lines.map { (it as BoardPlan.Line.Text).text })
        val unnamed = plan(BoardContent.Departures(board(0)))
        assertEquals(listOf("No services", "London Paddington"), unnamed.inner.lines.map { (it as BoardPlan.Line.Text).text })
    }

    @Test fun `when everything fits both choices show every train with its calling points`() {
        for (spare in SpareSpace.entries) {
            assertEquals("DCDSDSDSDS", plan(BoardContent.Departures(board(5)), tallRows, spare = spare).shape(), spare.name)
        }
    }

    @Test fun `more trains lists every train first and gives calling points only what's left`() {
        // 8 trains in 11 lines: 3 spare lines after one row each, so the 2nd, 3rd and 4th get calling points.
        assertEquals("DCDSDSDSDDDD", plan(BoardContent.Departures(board(8)), tallRows + 12).shape())
        assertEquals("DCDSDSDDDDD", plan(BoardContent.Departures(board(8)), tallRows).shape())
        // More trains than lines: one line each, the last alternating, no calling points.
        assertEquals("DCDDDDDDDDA", plan(BoardContent.Departures(board(20)), tallRows).shape())
    }

    @Test fun `calling points first expands trains in order, then lists the rest`() {
        // 2nd to 5th get calling points (8 lines), then one line left for the 6th and 7th alternating.
        assertEquals("DCDSDSDSDSA", plan(BoardContent.Departures(board(8)), tallRows, spare = SpareSpace.CALLING_POINTS).shape())
        // A standard board has no room for a 2nd train's calling points, so it's unchanged.
        assertEquals("DCA", plan(BoardContent.Departures(board(8)), spare = SpareSpace.CALLING_POINTS).shape())
    }

    @Test fun `long calling lists wrap under the label, and a list that doesn't fit whole isn't started`() {
        fun boardWith(stops: Int) = Board(
            "London Paddington", "PAD", null, null,
            listOf(departure(1), departure(2, (1..stops).map { "Station number $it" }), departure(3)), emptyList(),
        )
        val tall = plan(BoardContent.Departures(boardWith(8)), tallRows, spare = SpareSpace.CALLING_POINTS)
        val lines = tall.inner.lines
        val second = lines.indexOfFirst { it is BoardPlan.Line.DepartureRow && it.ordinal == 2 }
        val still = lines.drop(second + 1).takeWhile { it is BoardPlan.Line.CallingAtText }.map { it as BoardPlan.Line.CallingAtText }
        assertTrue(still.size > 1, "wrapped over ${still.size} lines")
        assertEquals("Calling at:", still.first().label)
        assertTrue(still.drop(1).all { it.label == null && it.x == still.first().x })
        // Too long for the board: the 2nd train gets one line, and the 3rd follows straight after it.
        assertEquals("DCDD", plan(BoardContent.Departures(boardWith(30)), tallRows, spare = SpareSpace.CALLING_POINTS).shape())
    }

    @Test fun `the clock stays at the bottom of a tall board when it runs out of trains`() {
        val few = plan(BoardContent.Departures(board(2)), tallRows)
        val many = plan(BoardContent.Departures(board(20)), tallRows)
        assertEquals(many.clockTop(), few.clockTop())
        assertTrue(few.clockTop() >= tallRows - 9 - 12, "clock at ${few.clockTop()}")
        // A standard board's clock is where it always was.
        assertEquals(plan(BoardContent.Departures(board(5))).clockTop(), plan(BoardContent.Departures(board(1))).clockTop())
    }

    @Test fun `trainRows counts the trains a board can list`() {
        assertEquals(2, renderer.trainRows(64, stationName = false))
        assertEquals(1, renderer.trainRows(64, stationName = true))
        assertEquals(10, renderer.trainRows(tallRows, stationName = false))
        assertEquals(9, renderer.trainRows(tallRows, stationName = true))
    }
}
