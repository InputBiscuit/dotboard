package io.github.inputbiscuit.dotboard.core.render

import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.core.model.formatBoardTime
import java.time.LocalTime

/** What the board shows: departures, or a message such as "No API key" drawn in dot-matrix style. */
sealed interface BoardContent {
    data class Departures(val board: Board) : BoardContent
    /** [station], when known, is shown across the top like a departure board's own name (see [BoardFrame.stationName]). */
    data class Message(val title: String, val detail: String? = null, val station: String? = null) : BoardContent
}

/** What a board does with rows that the first train and its scrolling "Calling at" leave. */
enum class SpareSpace {
    /** As many trains as fit; later trains get their calling points only once every train is listed. */
    TRAINS,

    /** Calling points for the 2nd, 3rd, ... trains while they fit, then the rest of the trains on one line each. */
    CALLING_POINTS,
}

data class BoardFrame(
    val content: BoardContent,
    /** Wall-clock time for the bottom clock; null hides the clock. */
    val clock: LocalTime?,
    val use24h: Boolean = true,
    val showSeconds: Boolean = true,
    /** False keeps the clock's space (and segment) but leaves the digits for the widget's own TextClock. */
    val drawClock: Boolean = true,
    /**
     * When the board is a cached copy because the latest refresh failed, when it was fetched. Shown as
     * "Updated 22:05" at the left of the clock row.
     */
    val updatedAt: LocalTime? = null,
    /** Show when the train is due at each stop: "Calling at: Slough (10:42), Maidenhead (10:49) and …". */
    val callingPointTimes: Boolean = false,
    /** The station's name in bold across the top, "London Paddington Station", when the board has room. */
    val stationName: Boolean = false,
    val spareSpace: SpareSpace = SpareSpace.TRAINS,
)

/** Which animation frame to draw. [scrollDots] (smooth scrolling) wins over [page] (paged) for "Calling at". */
data class Animation(
    val scrollDots: Int? = null,
    val page: Int = 0,
    val alternate: Int = 0,
    /** False leaves the "Calling at" text out (label only), for a widget that scrolls it in a TextView. */
    val callingAtText: Boolean = true,
) {
    companion object {
        val Static = Animation()
        const val SCROLL_DOTS_PER_SECOND = 40L
        const val ALTERNATE_MILLIS = 5_000L

        /** Smooth, time-based animation for a live preview. */
        fun at(millis: Long, plan: BoardPlan): Animation = Animation(
            scrollDots = plan.scrollCycle?.let { ((millis * SCROLL_DOTS_PER_SECOND / 1000) % it).toInt() },
            alternate = (millis / ALTERNATE_MILLIS).toInt(),
        )
    }
}

/**
 * A horizontal slice of the board. Animated slices have several [frames] that a widget can flip through
 * on its own (ViewFlipper), so the home screen animates without waking the app.
 */
data class Segment(val top: Int, val height: Int, val kind: Kind, val frames: Int) {
    enum class Kind { STATIC, CALLING_AT, ALTERNATING, CLOCK }
}

/** A laid-out board for a given grid size. Create with [BoardRenderer.plan]. */
class BoardPlan internal constructor(
    val cols: Int,
    val rows: Int,
    internal val marginX: Int,
    internal val marginY: Int,
    internal val inner: Inner,
) {
    private val callingAt get() = inner.lines.filterIsInstance<Line.CallingAt>().firstOrNull()

    /** The "Calling at" line, for widgets that scroll it themselves. Positions are in full-board dots. */
    data class CallingAtInfo(val text: String, val top: Int, val regionX: Int, val regionWidth: Int, val scrolls: Boolean)

    val callingAtInfo: CallingAtInfo?
        get() = callingAt?.let {
            CallingAtInfo(it.text, it.y + marginY, it.regionX + marginX, it.regionWidth, it.scrolls)
        }

    /** Whether the clock digits are part of the rendered board (false: the widget shows its own TextClock). */
    val drawsClock: Boolean get() = inner.frame.drawClock

    /** Dots per full scroll loop of the "Calling at" text, or null if it fits without scrolling. */
    val scrollCycle: Int? get() = callingAt?.takeIf { it.scrolls }?.let { it.regionWidth + it.textWidth + 1 }

    val segments: List<Segment> by lazy {
        val animated = inner.lines.mapNotNull { line ->
            val top = line.y + marginY
            when (line) {
                is Line.CallingAt -> if (line.pages.size > 1) {
                    Segment(top, LINE_PITCH, Segment.Kind.CALLING_AT, line.pages.size)
                } else {
                    null
                }
                is Line.Alternating -> Segment(top, LINE_PITCH, Segment.Kind.ALTERNATING, line.options.size)
                else -> null
            }
        }.plus(listOfNotNull(inner.clockTop?.let { Segment(it + marginY, inner.clockHeight, Segment.Kind.CLOCK, 1) }))
            .map { it.copy(height = it.height.coerceAtMost(rows - it.top)) }

        buildList {
            var y = 0
            for (s in animated) {
                if (s.top > y) add(Segment(y, s.top - y, Segment.Kind.STATIC, 1))
                add(s)
                y = s.top + s.height
            }
            if (y < rows) add(Segment(y, rows - y, Segment.Kind.STATIC, 1))
        }
    }

    /** The layout inside the margins, in its own coordinates. */
    internal class Inner(
        val cols: Int,
        val rows: Int,
        val frame: BoardFrame,
        val lines: List<Line>,
        val clockTop: Int?,
        val clockHeight: Int,
        val columns: Columns,
    )

    internal class Columns(
        val ordinalX: Int?,
        val timeX: Int,
        val destX: Int,
        val destRight: Int,
        val platX: Int?,
        val platWidth: Int,
    )

    internal sealed interface Line {
        val y: Int

        class DepartureRow(override val y: Int, val departure: Departure, val ordinal: Int) : Line
        class CallingAt(
            override val y: Int,
            val label: String?,
            val regionX: Int,
            val regionWidth: Int,
            val text: String,
            val textWidth: Int,
            val pages: List<String>,
        ) : Line {
            val scrolls get() = textWidth > regionWidth
        }
        class Alternating(override val y: Int, val options: List<Pair<Int, Departure>>) : Line
        class Text(override val y: Int, val text: String, val bold: Boolean) : Line

        /** One line of a later train's calling points: still text, wrapped, with the label on its first line only. */
        class CallingAtText(override val y: Int, val label: String?, val x: Int, val text: String) : Line
    }
}

/** Rows per text line: 9-row font plus a 3-row gap. */
internal const val LINE_PITCH = 12

/** The station name needs room for itself, the first train and its "Calling at". */
private const val MIN_LINES_FOR_NAME = 3

/**
 * Lays out and draws a UK-style departure board onto a [PixelBuffer]. Pure Kotlin: no Android types, so it's
 * unit-testable and shared by the app preview and the widget.
 *
 * At 256x64 (SSD1322-sized) the layout is:
 *  1st  22:09  Scarborough        Plat 4    On time
 *  Calling at: York, Malton and Scarborough   (scrolls)
 *  2nd / 3rd departure                        (alternates)
 *                 22:05:41                    (clock)
 * Taller grids get more departure rows (or later trains' calling points, see [SpareSpace]), with the clock kept at
 * the bottom; narrow ones drop the ordinal and platform columns. [BoardFrame.stationName] adds the station's name
 * as a bold top line.
 */
class BoardRenderer(
    private val fonts: DotFonts = DotFonts.default,
    /** Inset the content a few dots so rounded widget corners don't clip it. */
    private val margins: Boolean = true,
) {

    fun render(frame: BoardFrame, cols: Int, rows: Int, animation: Animation = Animation.Static): PixelBuffer =
        render(plan(frame, cols, rows), animation)

    fun plan(frame: BoardFrame, cols: Int, rows: Int): BoardPlan {
        val marginX = if (!margins) 0 else if (cols >= 200) 3 else if (cols >= 120) 2 else 0
        val marginY = marginY(rows)
        val inner = innerPlan(frame, cols - 2 * marginX, rows - 2 * marginY)
        return BoardPlan(cols, rows, marginX, marginY, inner)
    }

    /**
     * How many trains a board [rows] dots tall (with a clock) can list on one line each: the first train plus every
     * line after its "Calling at" and the station name. Lets a widget ask for more trains when it has room.
     */
    fun trainRows(rows: Int, stationName: Boolean): Int {
        val lines = textLines(rows - 2 * marginY(rows), clock = true)
        return (lines - (if (stationName && lines >= MIN_LINES_FOR_NAME) 1 else 0) - 1).coerceAtLeast(0)
    }

    private fun marginY(rows: Int) = if (!margins) 0 else if (rows >= 60) 2 else if (rows >= 40) 1 else 0

    private fun clockTop(rows: Int, clock: Boolean): Int? =
        if (clock && rows >= 40) rows - fonts.boldTall.height - (if (rows >= 64) 2 else 1) else null

    /** Lines of text above the clock (or in the whole board, without one). */
    private fun textLines(rows: Int, clock: Boolean): Int {
        val textBottom = clockTop(rows, clock)?.let { it - LINE_PITCH } ?: rows
        return if (textBottom >= fonts.regular.height) (textBottom - fonts.regular.height) / LINE_PITCH + 1 else 0
    }

    private fun innerPlan(frame: BoardFrame, cols: Int, rows: Int): BoardPlan.Inner {
        val f = fonts.regular
        val clockTop = clockTop(rows, frame.clock != null)
        val textBottom = clockTop?.let { it - LINE_PITCH } ?: rows
        val lineCount = textLines(rows, frame.clock != null)
        val columns = columns(cols)

        val stationName = when (val content = frame.content) {
            is BoardContent.Departures -> content.board.stationName
            is BoardContent.Message -> content.station
        }
        // The name only when it leaves room for the first train and its "Calling at".
        val title = stationName?.takeIf { frame.stationName && it.isNotBlank() && lineCount >= MIN_LINES_FOR_NAME }
            ?.let { stationTitle(it, cols) }
        val first = if (title != null) 1 else 0

        val lines = buildList {
            title?.let { add(BoardPlan.Line.Text(0, it, bold = true)) }
            addAll(
                when (val content = frame.content) {
                    is BoardContent.Departures -> {
                        val deps = content.board.departures
                        if (deps.isEmpty()) {
                            // With the name across the top, the message doesn't repeat it.
                            val message = BoardContent.Message("No services", content.board.stationName.takeIf { title == null })
                            messageLines(message, cols, lineCount - first, y(first), textBottom)
                        } else {
                            departureLines(deps, cols, lineCount - first, first, frame)
                        }
                    }
                    is BoardContent.Message -> messageLines(content, cols, lineCount - first, y(first), textBottom)
                },
            )
        }
        // Centre the clock in the space below the text area: at the bottom of a tall board even when it runs out of
        // trains, and close under the last line of a full one.
        val placedClockTop = clockTop?.let { lowest ->
            val textEnd = y(lineCount - 1) + f.height
            (textEnd + (rows - textEnd - fonts.boldTall.height) / 2).coerceIn(textEnd + 3, lowest)
        }
        return BoardPlan.Inner(cols, rows, frame, lines, placedClockTop, fonts.boldTall.height, columns)
    }

    /**
     * "London Paddington Station" in the bold font. A county in brackets goes first ("Adlington (Cheshire)" is
     * "Adlington Station": the board is only ever for one station), then " Station", then whole words.
     */
    private fun stationTitle(name: String, cols: Int): String {
        val bold = fonts.bold
        val short = name.replace(Regex("""\s*\([^)]*\)"""), "").trim().ifEmpty { name.trim() }
        val full = if (short.endsWith(" Station")) short else "$short Station"
        return listOf(full, short).firstOrNull { bold.width(it) <= cols } ?: bold.fitWords(short, cols)
    }

    fun render(plan: BoardPlan, animation: Animation = Animation.Static): PixelBuffer {
        val inner = plan.inner
        val content = PixelBuffer(inner.cols, inner.rows)
        for (line in inner.lines) drawLine(content, inner, line, animation)
        if (inner.frame.drawClock) inner.clockTop?.let { drawClock(content, inner, it) }
        inner.clockTop?.let { drawUpdated(content, inner, it) }
        if (plan.marginX == 0 && plan.marginY == 0) return content
        val buffer = PixelBuffer(plan.cols, plan.rows)
        for (y in 0 until inner.rows) for (x in 0 until inner.cols) {
            if (content[x, y]) buffer[x + plan.marginX, y + plan.marginY] = true
        }
        return buffer
    }

    /** One frame of one segment, for the widget's per-segment images. */
    fun renderSegment(plan: BoardPlan, segment: Segment, frame: Int): PixelBuffer {
        val animation = when (segment.kind) {
            Segment.Kind.STATIC, Segment.Kind.CLOCK -> Animation.Static
            Segment.Kind.CALLING_AT -> Animation(page = frame)
            Segment.Kind.ALTERNATING -> Animation(alternate = frame)
        }
        return render(plan, animation).crop(segment.top, segment.height)
    }

    private fun columns(cols: Int): BoardPlan.Columns {
        val f = fonts.regular
        val ordinalW = if (cols >= 150) f.width("10th") + 4 else 0
        val timeW = f.width("00:00") + 4
        val statusW = maxOf(f.width("Cancelled"), f.width("Exp 00:00"))
        val platW = if (cols >= 190) f.width("Plat 10A") + 4 else 0
        val statusX = cols - statusW
        val platX = statusX - platW
        return BoardPlan.Columns(
            ordinalX = if (ordinalW > 0) 0 else null,
            timeX = ordinalW,
            destX = ordinalW + timeW,
            destRight = if (platW > 0) platX - 2 else statusX - 3,
            platX = if (platW > 0) platX else null,
            platWidth = platW,
        )
    }

    /**
     * The first train and its scrolling "Calling at", then the later trains in [lineCount] lines starting at line
     * [first]. Later trains' calling points (still, wrapped text) are added as [BoardFrame.spareSpace] allows, from
     * the 2nd train down, stopping at the first that doesn't fit whole. If the trains don't all fit, the last row
     * alternates between the next two.
     */
    private fun departureLines(
        deps: List<Departure>,
        cols: Int,
        lineCount: Int,
        first: Int,
        frame: BoardFrame,
    ): List<BoardPlan.Line> {
        if (lineCount <= 0) return emptyList()
        val lines = mutableListOf<BoardPlan.Line>(BoardPlan.Line.DepartureRow(y(first), deps[0], 1))
        if (lineCount >= 2) lines += callingAtLine(y(first + 1), deps[0], cols, frame)

        val others = deps.drop(1).mapIndexed { i, d -> (i + 2) to d }
        val slots = lineCount - 2
        if (slots < 1 || others.isEmpty()) return lines

        // Wrapped lazily: only the trains that might get their calling points are measured.
        val stops = others.map { (_, d) -> lazy { stillCallingAt(d, cols, frame) } }
        // TRAINS: rows for every train come first, so only what's left over goes to calling points.
        // CALLING_POINTS: each expanded train costs its own row too.
        var spare = when (frame.spareSpace) {
            SpareSpace.TRAINS -> if (others.size <= slots) slots - others.size else 0
            SpareSpace.CALLING_POINTS -> slots
        }
        val rowCost = if (frame.spareSpace == SpareSpace.CALLING_POINTS) 1 else 0
        var expanded = 0
        while (expanded < others.size && rowCost + stops[expanded].value.size <= spare) {
            spare -= rowCost + stops[expanded].value.size
            expanded++
        }

        var line = first + 2
        for (i in 0 until expanded) {
            val (ordinal, d) = others[i]
            lines += BoardPlan.Line.DepartureRow(y(line++), d, ordinal)
            stops[i].value.forEach { lines += it.at(y(line++)) }
        }
        val rest = others.drop(expanded)
        val left = first + lineCount - line
        if (rest.size <= left) {
            rest.forEach { (ordinal, d) -> lines += BoardPlan.Line.DepartureRow(y(line++), d, ordinal) }
        } else if (left >= 1) {
            rest.take(left - 1).forEach { (ordinal, d) -> lines += BoardPlan.Line.DepartureRow(y(line++), d, ordinal) }
            lines += BoardPlan.Line.Alternating(y(line), rest.drop(left - 1).take(2))
        }
        return lines
    }

    /** A later train's "Calling at" (or cancellation) as still lines wrapped to the board; y is set when placed. */
    private fun stillCallingAt(d: Departure, cols: Int, frame: BoardFrame): List<BoardPlan.Line.CallingAtText> {
        val (label, text) = callingText(d, frame)
        val x = label?.let { fonts.regular.width(it) + 4 } ?: 0
        return fonts.regular.wrap(text, (cols - x).coerceAtLeast(1)).mapIndexed { i, page ->
            BoardPlan.Line.CallingAtText(0, label.takeIf { i == 0 }, x, page)
        }
    }

    private fun BoardPlan.Line.CallingAtText.at(y: Int) = BoardPlan.Line.CallingAtText(y, label, x, text)

    private fun callingAtLine(y: Int, d: Departure, cols: Int, frame: BoardFrame): BoardPlan.Line.CallingAt {
        val f = fonts.regular
        val (label, text) = callingText(d, frame)
        val regionX = label?.let { f.width(it) + 4 } ?: 0
        val regionWidth = (cols - regionX).coerceAtLeast(1)
        return BoardPlan.Line.CallingAt(
            y = y,
            label = label,
            regionX = regionX,
            regionWidth = regionWidth,
            text = text,
            textWidth = f.width(text),
            pages = f.wrap(text, regionWidth).ifEmpty { listOf("") },
        )
    }

    /** "Calling at:" and the stops (with times if chosen, and any delay reason), or a cancelled train's reason. */
    private fun callingText(d: Departure, frame: BoardFrame): Pair<String?, String> =
        if (d.status == DepartureStatus.Cancelled) {
            null to (d.cancelReason?.withFullStop() ?: "This train has been cancelled.")
        } else {
            val names = if (frame.callingPointTimes) {
                // With times, stops the train no longer calls at are left out, as there's no time to show.
                d.callingPoints.filter { it.name.isNotBlank() && it.status != DepartureStatus.Cancelled }.map { stop ->
                    stop.time?.let { "${stop.name} (${formatBoardTime(it, frame.use24h)})" } ?: stop.name
                }
            } else {
                d.callingPoints.map { it.name }.filter { it.isNotBlank() }
            }.ifEmpty { listOf(d.destination) }
            val calling = if (names.size == 1) {
                "${names[0]} only."
            } else {
                names.dropLast(1).joinToString(", ") + " and " + names.last() + "."
            }
            "Calling at:" to (calling + (d.delayReason?.let { "  ${it.withFullStop()}" } ?: ""))
        }

    /** The message centred between [top] and [textBottom]. */
    private fun messageLines(
        message: BoardContent.Message,
        cols: Int,
        lineCount: Int,
        top: Int,
        textBottom: Int,
    ): List<BoardPlan.Line> {
        if (lineCount <= 0) return emptyList()
        val texts = buildList {
            add(fonts.bold.fit(message.title, cols) to true)
            message.detail?.let { detail -> fonts.regular.wrap(detail, cols).forEach { add(it to false) } }
        }.take(lineCount)
        val blockHeight = (texts.size - 1) * LINE_PITCH + fonts.regular.height
        val blockTop = top + ((textBottom - top - blockHeight) / 2).coerceAtLeast(0)
        return texts.mapIndexed { i, (text, bold) -> BoardPlan.Line.Text(blockTop + i * LINE_PITCH, text, bold) }
    }

    private fun drawLine(buffer: PixelBuffer, plan: BoardPlan.Inner, line: BoardPlan.Line, animation: Animation) {
        when (line) {
            is BoardPlan.Line.DepartureRow -> drawDeparture(buffer, plan, line.y, line.departure, line.ordinal)
            is BoardPlan.Line.Alternating -> {
                val (ordinal, d) = line.options[Math.floorMod(animation.alternate, line.options.size)]
                drawDeparture(buffer, plan, line.y, d, ordinal)
            }
            is BoardPlan.Line.CallingAt -> drawCallingAt(buffer, line, animation)
            is BoardPlan.Line.Text -> {
                val font = if (line.bold) fonts.bold else fonts.regular
                font.draw(buffer, line.text, (plan.cols - font.width(line.text)) / 2, line.y)
            }
            is BoardPlan.Line.CallingAtText -> {
                line.label?.let { fonts.regular.draw(buffer, it, 0, line.y) }
                fonts.regular.draw(buffer, line.text, line.x, line.y)
            }
        }
    }

    private fun drawDeparture(buffer: PixelBuffer, plan: BoardPlan.Inner, y: Int, d: Departure, ordinal: Int) {
        val f = fonts.regular
        val c = plan.columns
        val use24h = plan.frame.use24h
        c.ordinalX?.let { f.draw(buffer, ordinal(ordinal), it, y) }
        f.draw(buffer, formatBoardTime(d.scheduled, use24h), c.timeX, y)
        f.draw(buffer, f.fitWords(d.destination, c.destRight - c.destX), c.destX, y)
        if (c.platX != null && !d.platform.isNullOrBlank()) {
            f.draw(buffer, f.fit("Plat ${d.platform}", c.platWidth - 2), c.platX, y)
        }
        val status = d.status.boardText(use24h)
        f.draw(buffer, status, plan.cols - f.width(status), y)
    }

    private fun drawCallingAt(buffer: PixelBuffer, line: BoardPlan.Line.CallingAt, animation: Animation) {
        val f = fonts.regular
        line.label?.let { f.draw(buffer, it, 0, line.y) }
        val clipRight = line.regionX + line.regionWidth
        when {
            !animation.callingAtText -> Unit
            !line.scrolls -> f.draw(buffer, line.text, line.regionX, line.y)
            animation.scrollDots != null -> {
                val x = clipRight - animation.scrollDots
                f.draw(buffer, line.text, x, line.y, clipLeft = line.regionX, clipRight = clipRight)
            }
            else -> {
                val page = line.pages[Math.floorMod(animation.page, line.pages.size)]
                f.draw(buffer, page, line.regionX, line.y, clipLeft = line.regionX, clipRight = clipRight)
            }
        }
    }

    private fun drawClock(buffer: PixelBuffer, plan: BoardPlan.Inner, top: Int) {
        val time = plan.frame.clock ?: return
        val hours = if (plan.frame.use24h) time.hour else (time.hour % 12).let { if (it == 0) 12 else it }
        val main = if (plan.frame.use24h) "%02d:%02d".format(hours, time.minute) else "%d:%02d".format(hours, time.minute)
        val seconds = if (plan.frame.showSeconds) ":%02d".format(time.second) else ""

        val tall = fonts.boldTall
        val small = fonts.bold
        val mainW = tall.width(main)
        val total = mainW + if (seconds.isEmpty()) 0 else small.width(seconds) + 1
        val x = (plan.cols - total) / 2
        tall.draw(buffer, main, x, top)
        // Bottom-align the smaller seconds with the tall digits (which use the descender rows).
        if (seconds.isNotEmpty()) small.draw(buffer, seconds, x + mainW + 1, top + tall.descent)
    }

    /**
     * "Updated 22:05" at the left of the clock row, bottom-aligned with the clock's seconds, when showing a cached
     * board. Shortened to "22:05", or left out, so it never runs into the (centred) clock, which may be the
     * widget's own TextClock rather than drawn here.
     */
    private fun drawUpdated(buffer: PixelBuffer, plan: BoardPlan.Inner, top: Int) {
        val updated = plan.frame.updatedAt ?: return
        val f = fonts.regular
        val time = formatBoardTime("%02d:%02d".format(updated.hour, updated.minute), plan.frame.use24h)
        val clockWidth = fonts.boldTall.width("00:00") + 1 + fonts.bold.width(":00")
        val room = (plan.cols - clockWidth) / 2 - 3
        val text = listOf("Updated $time", time).firstOrNull { f.width(it) <= room } ?: return
        f.draw(buffer, text, 0, top + fonts.boldTall.descent)
    }

    private fun y(line: Int) = line * LINE_PITCH

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    private fun String.withFullStop() = trimEnd().let { if (it.endsWith(".")) it else "$it." }
}
