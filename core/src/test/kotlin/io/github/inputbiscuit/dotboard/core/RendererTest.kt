package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.FakeDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.render.Animation
import io.github.inputbiscuit.dotboard.core.render.BitmapFont
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardGeometry
import io.github.inputbiscuit.dotboard.core.render.BoardRenderer
import io.github.inputbiscuit.dotboard.core.render.DotFonts
import io.github.inputbiscuit.dotboard.core.render.DotRasterizer
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import io.github.inputbiscuit.dotboard.core.render.PixelBuffer
import io.github.inputbiscuit.dotboard.core.render.Segment
import kotlinx.coroutines.test.runTest
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RendererTest {

    private val fonts = DotFonts.default
    // No margins, so positions in these tests are simple; margins have their own test.
    private val renderer = BoardRenderer(fonts, margins = false)
    private val clock = LocalTime.of(22, 5, 41)

    private fun sample(scenario: FakeScenario): Board = kotlinx.coroutines.runBlocking {
        FakeDepartureRepository(
            scenario = { scenario },
            stationName = { "London Paddington" },
            now = { ZonedDateTime.of(2026, 11, 9, 22, 6, 0, 0, ZoneId.of("Europe/London")) },
            latencyMillis = 0,
        ).board(BoardQuery("PAD"))
    }

    /** Text rendered by itself, for comparing against regions of the board. */
    private fun textAt(text: String, font: BitmapFont = fonts.regular): PixelBuffer =
        PixelBuffer(font.width(text) + 1, font.height).also { font.draw(it, text, 0, 0) }

    /** True if [text] appears at exactly ([x], [y]) in [board], dot for dot. */
    private fun hasText(board: PixelBuffer, text: String, x: Int, y: Int, font: BitmapFont = fonts.regular): Boolean {
        val t = textAt(text, font)
        for (ty in 0 until t.height) for (tx in 0 until t.width - 1) if (t[tx, ty] != board[x + tx, y + ty]) return false
        return true
    }

    @Test fun `fonts load with expected metrics`() {
        assertEquals(7, fonts.regular.ascent)
        assertEquals(2, fonts.regular.descent)
        assertEquals(9, fonts.boldTall.height)
        // 'H' is 4 dots wide plus a 1-dot gap.
        assertEquals(4, fonts.regular.width("H"))
        assertEquals(9, fonts.regular.width("HH"))
    }

    @Test fun `text snaps to the grid with no partial dots`() {
        val b = textAt("H")
        assertEquals(
            """
            #..#.
            #..#.
            #..#.
            ####.
            #..#.
            #..#.
            #..#.
            .....
            .....
            """.trimIndent(),
            b.toAscii(),
        )
    }

    @Test fun `missing characters fall back to close equivalents`() {
        assertEquals(fonts.regular.width("-"), fonts.regular.width("–"))
        assertEquals(fonts.regular.width("e"), fonts.regular.width("é"))
    }

    @Test fun `256x64 board has first departure, calling at, second row and clock`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock)
        val plan = renderer.plan(frame, 256, 64)
        val board = renderer.render(plan)

        assertTrue(hasText(board, "1st", 0, 0))
        assertTrue(hasText(board, "22:09", fonts.regular.width("10th") + 4, 0))
        val onTime = "On time"
        assertTrue(hasText(board, onTime, 256 - fonts.regular.width(onTime), 0), "status right-aligned")
        assertTrue(hasText(board, "Calling at:", 0, 12))
        assertTrue(hasText(board, "2nd", 0, 24))

        // Row 3 alternates between the 2nd and 3rd departures.
        val alt = renderer.render(plan, Animation(alternate = 1))
        assertTrue(hasText(alt, "3rd", 0, 24))
        assertTrue(hasText(alt, "Cancelled", 256 - fonts.regular.width("Cancelled"), 24))

        // Clock: HH:MM tall + seconds, centred horizontally, below the text rows.
        val main = "22:05"
        val total = fonts.boldTall.width(main) + 1 + fonts.bold.width(":41")
        val clockY = (36 until 64 - 9).firstOrNull { hasText(board, main, (256 - total) / 2, it, fonts.boldTall) }
        assertTrue(clockY != null, "clock drawn below row 3")
        assertTrue(hasText(board, ":41", (256 - total) / 2 + fonts.boldTall.width(main) + 1, clockY + 2, fonts.bold))
    }

    @Test fun `delayed status reads Exp hh mm`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock)
        val board = renderer.render(frame, 256, 64)
        // The 2nd departure (22:14, expected 22:19) is on row 3, frame 0.
        val exp = "Exp 22:19"
        assertTrue(hasText(board, exp, 256 - fonts.regular.width(exp), 24))
    }

    @Test fun `fitWords drops whole words and dangling ampersands`() {
        val f = fonts.regular
        val text = "Manchester Airport & Liverpool Lime Street"
        assertEquals("Manchester Airport", f.fitWords(text, f.width("Manchester Airport & Liv")))
        assertEquals(text, f.fitWords(text, 1000))
        assertEquals("Manch", f.fitWords("Manchester", f.width("Manch")))
    }

    @Test fun `long destinations are clipped before the platform column`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.LONG_NAMES)), clock)
        val plan = renderer.plan(frame, 256, 64)
        val board = renderer.render(plan)
        val platX = 256 - maxOf(fonts.regular.width("Cancelled"), fonts.regular.width("Exp 00:00")) -
            (fonts.regular.width("Plat 10A") + 4)
        assertTrue(hasText(board, "Plat 10A", platX, 0))
        // The two dot columns just before the platform stay dark on the text rows.
        for (y in 0 until 9) {
            assertFalse(board[platX - 1, y])
            assertFalse(board[platX - 2, y])
        }
    }

    @Test fun `long calling list is paged for the widget and scrolls in the preview`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.LONG_NAMES)), clock)
        val plan = renderer.plan(frame, 256, 64)
        val calling = plan.segments.single { it.kind == Segment.Kind.CALLING_AT }
        assertTrue(calling.frames > 2)
        assertEquals(12, calling.top)

        val a = renderer.render(plan, Animation(scrollDots = 10)).crop(12, 12)
        val b = renderer.render(plan, Animation(scrollDots = 50)).crop(12, 12)
        assertNotEquals(a.toAscii(), b.toAscii())
        assertTrue(hasText(a, "Calling at:", 0, 0), "label stays put while text scrolls")
    }

    @Test fun `segments tile the board exactly`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock)
        for ((cols, rows) in listOf(256 to 64, 360 to 70, 260 to 115, 180 to 45)) {
            val plan = renderer.plan(frame, cols, rows)
            var y = 0
            for (s in plan.segments) {
                assertEquals(y, s.top)
                y += s.height
            }
            assertEquals(rows, y)
        }
    }

    @Test fun `margins keep the edges dark so rounded corners don't clip text`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock)
        val withMargins = BoardRenderer(fonts)
        val plan = withMargins.plan(frame, 256, 64)
        val board = withMargins.render(plan)
        for (y in 0 until 64) {
            for (x in listOf(0, 1, 2, 253, 254, 255)) assertFalse(board[x, y], "edge column $x row $y")
        }
        for (x in 0 until 256) assertFalse(board[x, 0] || board[x, 1], "top rows")
        assertTrue(hasText(board, "1st", 3, 2))
        // Segments still tile the full board and are shifted by the top margin.
        assertEquals(64, plan.segments.sumOf { it.height })
        assertEquals(14, plan.segments.single { it.kind == Segment.Kind.ALTERNATING }.top - 12)
    }

    @Test fun `taller boards show more departure rows`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock)
        val board = renderer.render(frame, 256, 110)
        assertTrue(hasText(board, "2nd", 0, 24))
        assertTrue(hasText(board, "3rd", 0, 36))
        assertTrue(hasText(board, "4th", 0, 48))
    }

    @Test fun `narrow boards drop ordinal and platform`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock)
        val board = renderer.render(frame, 140, 64)
        assertTrue(hasText(board, "22:09", 0, 0))
    }

    @Test fun `no services and errors are drawn as centred messages`() {
        val empty = renderer.render(BoardFrame(BoardContent.Departures(sample(FakeScenario.NO_SERVICES)), clock), 256, 64)
        val title = "No services"
        assertTrue((0 until 40).any { y -> hasText(empty, title, (256 - fonts.bold.width(title)) / 2, y, fonts.bold) })

        val error = renderer.render(BoardFrame(BoardContent.Message("No API key"), null), 256, 64)
        assertTrue(error.litCount > 0)
    }

    @Test fun `12 hour mode drops leading zero and suffix`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), LocalTime.of(9, 5, 0), use24h = false)
        val board = renderer.render(frame, 256, 64)
        assertTrue(hasText(board, "10:09", fonts.regular.width("10th") + 4, 0))
    }

    /** Writes ASCII renders to build/board-previews.txt for eyeballing layout changes. */
    @Test fun `write ascii previews`() {
        val out = java.io.File("build/board-previews.txt")
        out.writeText("")
        for (scenario in listOf(FakeScenario.MIXED, FakeScenario.LONG_NAMES, FakeScenario.NO_SERVICES)) {
            val frame = BoardFrame(BoardContent.Departures(sample(scenario)), clock)
            for ((cols, rows) in listOf(256 to 64, 180 to 45)) {
                out.appendText("== $scenario ${cols}x$rows\n${renderer.render(frame, cols, rows).toAscii()}\n\n")
            }
        }
        out.appendText("== error\n" + renderer.render(BoardFrame(BoardContent.Message("No API key", "Open Dotboard to add one"), clock), 256, 64).toAscii())
    }

    @Test fun `rasterizer makes round dots of the right size and colour`() {
        val buffer = PixelBuffer(2, 1).apply { this[0, 0] = true }
        val pitch = 10
        val palette = LedTheme.AMBER.palette
        val px = DotRasterizer(pitch, palette).rasterize(buffer)
        assertEquals(2 * pitch * pitch, px.size)
        val w = 2 * pitch
        assertEquals(palette.lit, px[5 * w + 5], "lit dot centre")
        assertEquals(palette.background, px[0], "corner is background (round, not square)")
        assertEquals(palette.unlit, px[5 * w + 15], "unlit dot centre is dim")
    }

    @Test fun `dot mask is opaque between dots and see-through at dot centres`() {
        val pitch = 10
        val px = DotRasterizer(pitch, LedTheme.AMBER.palette).mask(2, 1)
        val w = 2 * pitch
        assertEquals(2 * pitch * pitch, px.size)
        assertEquals(0, px[5 * w + 5] ushr 24, "hole centre is transparent")
        assertEquals(0, px[5 * w + 15] ushr 24, "second hole centre is transparent")
        assertEquals(0xFF, px[0] ushr 24, "corner between dots is opaque")
        assertEquals(0, px[0] and 0xFFFFFF, "mask colour is the black background")
    }

    @Test fun `clock gets its own segment and can be left for the widget to draw`() {
        val frame = BoardFrame(BoardContent.Departures(sample(FakeScenario.MIXED)), clock, drawClock = false)
        val plan = renderer.plan(frame, 256, 64)
        val clockSegment = plan.segments.single { it.kind == Segment.Kind.CLOCK }
        assertEquals(9, clockSegment.height)
        val band = renderer.render(plan).crop(clockSegment.top, clockSegment.height)
        assertEquals(0, band.litCount, "no clock digits drawn")
        val drawn = renderer.render(frame.copy(drawClock = true), 256, 64)
        assertTrue(drawn.crop(clockSegment.top, clockSegment.height).litCount > 0)
    }

    @Test fun `geometry keeps at least the base grid and grows with the area`() {
        assertEquals(BoardGeometry(256, 64, 4), BoardGeometry.fit(1024, 256))
        val phone4x1 = BoardGeometry.fit(1080, 210)
        assertEquals(3, phone4x1.pitch)
        assertEquals(360, phone4x1.cols)
        assertEquals(70, phone4x1.rows)
        val tall = BoardGeometry.fit(2340, 1040)
        assertEquals(9, tall.pitch)
        assertTrue(tall.rows > 64)
    }
}
