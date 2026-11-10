package io.github.inputbiscuit.dotboard.core.render

import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.core.model.formatBoardTime
import java.time.LocalTime

/** What the board shows: departures, or a message such as "No API key" drawn in dot-matrix style. */
sealed interface BoardContent {
    data class Departures(val board: Board) : BoardContent
    data class Message(val title: String, val detail: String? = null) : BoardContent
}

data class BoardFrame(
    val content: BoardContent,
    /** Wall-clock time for the bottom clock; null hides the clock. */
    val clock: LocalTime?,
    val use24h: Boolean = true,
    val showSeconds: Boolean = true,
    /** False keeps the clock's space (and segment) but leaves the digits for the widget's own TextClock. */
    val drawClock: Boolean = true,
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
    }
}

/** Rows per text line: 9-row font plus a 3-row gap. */
internal const val LINE_PITCH = 12

/**
 * Lays out and draws a UK-style departure board onto a [PixelBuffer]. Pure Kotlin: no Android types, so it's
 * unit-testable and shared by the app preview and the widget.
 *
 * At 256x64 (SSD1322-sized) the layout is:
 *  1st  22:09  Scarborough        Plat 4    On time
 *  Calling at: York, Malton and Scarborough   (scrolls)
 *  2nd / 3rd departure                        (alternates)
 *                 22:05:41                    (clock)
 * Taller grids get more departure rows; narrow ones drop the ordinal and platform columns.
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
        val marginY = if (!margins) 0 else if (rows >= 60) 2 else if (rows >= 40) 1 else 0
        val inner = innerPlan(frame, cols - 2 * marginX, rows - 2 * marginY)
        return BoardPlan(cols, rows, marginX, marginY, inner)
    }

    private fun innerPlan(frame: BoardFrame, cols: Int, rows: Int): BoardPlan.Inner {
        val f = fonts.regular
        val showClock = frame.clock != null && rows >= 40
        val clockTop = if (showClock) rows - fonts.boldTall.height - (if (rows >= 64) 2 else 1) else null
        val textBottom = clockTop?.let { it - LINE_PITCH } ?: rows
        val lineCount = if (textBottom >= f.height) (textBottom - f.height) / LINE_PITCH + 1 else 0
        val columns = columns(cols)

        val lines = when (val content = frame.content) {
            is BoardContent.Departures -> {
                val deps = content.board.departures
                if (deps.isEmpty()) {
                    messageLines(BoardContent.Message("No services", content.board.stationName), cols, lineCount, textBottom)
                } else {
                    departureLines(deps, cols, lineCount)
                }
            }
            is BoardContent.Message -> messageLines(content, cols, lineCount, textBottom)
        }
        // Centre the clock in the space below the last line of text rather than pinning it to the bottom.
        val centredClockTop = clockTop?.let { lowest ->
            val textEnd = (lines.maxOfOrNull { it.y } ?: -LINE_PITCH) + f.height
            (textEnd + (rows - textEnd - fonts.boldTall.height) / 2).coerceIn(textEnd + 3, lowest)
        }
        return BoardPlan.Inner(cols, rows, frame, lines, centredClockTop, fonts.boldTall.height, columns)
    }

    fun render(plan: BoardPlan, animation: Animation = Animation.Static): PixelBuffer {
        val inner = plan.inner
        val content = PixelBuffer(inner.cols, inner.rows)
        for (line in inner.lines) drawLine(content, inner, line, animation)
        if (inner.frame.drawClock) inner.clockTop?.let { drawClock(content, inner, it) }
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

    private fun departureLines(deps: List<Departure>, cols: Int, lineCount: Int): List<BoardPlan.Line> {
        if (lineCount <= 0) return emptyList()
        val lines = mutableListOf<BoardPlan.Line>(BoardPlan.Line.DepartureRow(0, deps[0], 1))
        if (lineCount >= 2) lines += callingAtLine(LINE_PITCH, deps[0], cols)

        val others = deps.drop(1).mapIndexed { i, d -> (i + 2) to d }
        val slots = lineCount - 2
        if (slots >= 1 && others.isNotEmpty()) {
            if (others.size <= slots) {
                others.forEachIndexed { i, (ordinal, d) -> lines += BoardPlan.Line.DepartureRow(y(2 + i), d, ordinal) }
            } else {
                others.take(slots - 1).forEachIndexed { i, (ordinal, d) ->
                    lines += BoardPlan.Line.DepartureRow(y(2 + i), d, ordinal)
                }
                lines += BoardPlan.Line.Alternating(y(1 + slots), others.drop(slots - 1).take(2))
            }
        }
        return lines
    }

    private fun callingAtLine(y: Int, d: Departure, cols: Int): BoardPlan.Line.CallingAt {
        val f = fonts.regular
        val (label, text) = if (d.status == DepartureStatus.Cancelled) {
            null to (d.cancelReason?.withFullStop() ?: "This train has been cancelled.")
        } else {
            val names = d.callingPoints.map { it.name }.filter { it.isNotBlank() }.ifEmpty { listOf(d.destination) }
            val calling = if (names.size == 1) {
                "${names[0]} only."
            } else {
                names.dropLast(1).joinToString(", ") + " and " + names.last() + "."
            }
            "Calling at:" to (calling + (d.delayReason?.let { "  ${it.withFullStop()}" } ?: ""))
        }
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

    private fun messageLines(
        message: BoardContent.Message,
        cols: Int,
        lineCount: Int,
        textBottom: Int,
    ): List<BoardPlan.Line> {
        if (lineCount <= 0) return emptyList()
        val texts = buildList {
            add(fonts.bold.fit(message.title, cols) to true)
            message.detail?.let { detail -> fonts.regular.wrap(detail, cols).forEach { add(it to false) } }
        }.take(lineCount)
        val blockHeight = (texts.size - 1) * LINE_PITCH + fonts.regular.height
        val top = ((textBottom - blockHeight) / 2).coerceAtLeast(0)
        return texts.mapIndexed { i, (text, bold) -> BoardPlan.Line.Text(top + i * LINE_PITCH, text, bold) }
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
