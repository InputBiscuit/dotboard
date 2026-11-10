package io.github.inputbiscuit.dotboard.widget

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import io.github.inputbiscuit.dotboard.R
import io.github.inputbiscuit.dotboard.core.render.Animation
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardGeometry
import io.github.inputbiscuit.dotboard.core.render.BoardPlan
import io.github.inputbiscuit.dotboard.core.render.BoardRenderer
import io.github.inputbiscuit.dotboard.core.render.DotRasterizer
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import io.github.inputbiscuit.dotboard.core.render.PixelBuffer
import io.github.inputbiscuit.dotboard.core.render.Segment
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Builds the widget's RemoteViews as a vertical stack of slices of the board, all animated by the launcher so
 * the app only wakes up to refresh data:
 *  - static rows: bitmaps from the dot-matrix renderer (exact Dot Matrix typeface);
 *  - the alternating 2nd/3rd row: a ViewFlipper of pre-rendered frames;
 *  - "Calling at" (when it doesn't fit): a marquee TextView under a dot mask;
 *  - the clock: TextClocks under a dot mask.
 * RemoteViews ignore custom fonts, so the moving and ticking text uses a system font; the dot mask (opaque
 * between dots, see-through at each dot) turns it into LED dots on the board's grid.
 */
object WidgetRenderer {

    /** The widget insets the board itself (see [cornerInsetPx]), so no extra renderer margin. */
    private val widgetRenderer by lazy { BoardRenderer(margins = false) }

    /** Dot rows for the system-font text: cap height of scrolling text, and the clock's digits. */
    private const val TEXT_CAP_ROWS = 7
    private const val CLOCK_ROWS = 9
    private const val SECONDS_ROWS = 7

    /** Must match android:fontFamily in widget_segment_marquee.xml. Medium weight ≈ 1-dot strokes, like the dot font. */
    private const val MARQUEE_FAMILY = "sans-serif-condensed-medium"

    /**
     * Padding that keeps the board clear of the launcher's rounded corners: for corner radius r, an inset of
     * r(1 - 1/√2) on each side puts the board's corners exactly on the curve, so nothing is clipped.
     * Uses the system widget radius (Android 12+), or 16dp if a launcher reports none.
     */
    fun cornerInsetPx(context: Context): Int {
        val res = context.resources
        val radius = res.getDimensionPixelSize(android.R.dimen.system_app_widget_background_radius)
            .takeIf { it > 0 } ?: (16 * res.displayMetrics.density).toInt()
        return ceil(radius * (1 - 1 / sqrt(2.0))).toInt() + 1
    }

    /** Padding around the board, in px. */
    data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * The corner inset, nudged so text sits comfortably clear of the curve: more at the top (the first row's
     * text sits right at the top edge) and a little more at the sides. The bottom gives back what the top
     * gained, so the board's height (and the clock's centring in the space below the rows) is unchanged.
     */
    fun boardInsets(context: Context): Insets {
        val inset = cornerInsetPx(context)
        val dp = context.resources.displayMetrics.density
        val extraTop = (4 * dp).toInt()
        val extraSide = (3 * dp).toInt()
        return Insets(
            left = inset + extraSide,
            top = inset + extraTop,
            right = inset + extraSide,
            bottom = (inset - extraTop).coerceAtLeast((2 * dp).toInt()),
        )
    }

    /**
     * One RemoteViews per distinct size the launcher reports (e.g. a foldable's inner and outer screens);
     * the launcher picks the best fit. [animated] false draws one still bitmap (used if over the bitmap budget).
     */
    fun build(
        context: Context,
        frame: BoardFrame,
        theme: LedTheme,
        sizesDp: List<SizeF>,
        onClick: PendingIntent,
        animated: Boolean = true,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val bySize = sizesDp.distinct().associateWith { size ->
            forSize(context, frame, theme, (size.width * density).toInt(), (size.height * density).toInt(), onClick, animated)
        }
        return if (bySize.size == 1) bySize.values.first() else RemoteViews(bySize)
    }

    private fun forSize(
        context: Context,
        frame: BoardFrame,
        theme: LedTheme,
        widthPx: Int,
        heightPx: Int,
        onClick: PendingIntent,
        animated: Boolean,
    ): RemoteViews {
        val insets = boardInsets(context)
        val g = BoardGeometry.fit(
            (widthPx - insets.left - insets.right).coerceAtLeast(1),
            (heightPx - insets.top - insets.bottom).coerceAtLeast(1),
        )
        // A still board has no TextClock, so it draws its own clock digits.
        val plan = widgetRenderer.plan(if (animated) frame else frame.copy(drawClock = true), g.cols, g.rows)
        val slice = Slice(context.packageName, plan, DotRasterizer(g.pitch, theme.palette), theme, onClick)

        val root = RemoteViews(context.packageName, R.layout.widget_board)
        root.setViewPadding(android.R.id.background, insets.left, insets.top, insets.right, insets.bottom)
        root.removeAllViews(R.id.segments)
        val segments = if (animated) plan.segments else listOf(Segment(0, plan.rows, Segment.Kind.STATIC, 1))
        for (segment in segments) {
            root.addView(
                R.id.segments,
                when {
                    segment.kind == Segment.Kind.CALLING_AT -> slice.marquee(segment)
                    segment.kind == Segment.Kind.CLOCK && !plan.drawsClock -> slice.clock(segment, frame.use24h)
                    segment.frames > 1 -> slice.flipper(segment)
                    else -> slice.image(segment)
                },
            )
        }
        root.setOnClickPendingIntent(android.R.id.background, onClick)
        root.setContentDescription(android.R.id.background, describe(frame))
        return root
    }

    /** Builds the views for slices of one board at one size. */
    private class Slice(
        val pkg: String,
        val plan: BoardPlan,
        val rasterizer: DotRasterizer,
        val theme: LedTheme,
        val onClick: PendingIntent,
    ) {
        val pitch = rasterizer.pitch.toFloat()

        fun image(segment: Segment, frame: Int = 0): RemoteViews = RemoteViews(pkg, R.layout.widget_segment_image).apply {
            setImageViewBitmap(R.id.image, bitmap(widgetRenderer.renderSegment(plan, segment, frame)))
        }

        fun flipper(segment: Segment): RemoteViews = RemoteViews(pkg, R.layout.widget_segment_flipper_alternating).apply {
            repeat(segment.frames) { addView(R.id.flipper, image(segment, it)) }
        }

        /** "Calling at" scrolled by the launcher: dot-font label from the bitmap, marquee text under the mask. */
        fun marquee(segment: Segment): RemoteViews = RemoteViews(pkg, R.layout.widget_segment_marquee).apply {
            val info = requireNotNull(plan.callingAtInfo)
            val background = widgetRenderer.render(plan, Animation(callingAtText = false)).crop(segment.top, segment.height)
            setImageViewBitmap(R.id.image, bitmap(background))
            setImageViewBitmap(R.id.mask, mask(segment))

            val (size, ascent) = fitSystemFont(MARQUEE_FAMILY, TEXT_CAP_ROWS * pitch, "H", Typeface.NORMAL)
            setTextViewText(R.id.marquee, info.text)
            setTextColor(R.id.marquee, theme.palette.lit)
            setInt(R.id.marquee, "setBackgroundColor", theme.palette.unlit)
            setTextViewTextSize(R.id.marquee, TypedValue.COMPLEX_UNIT_PX, size)
            setViewLayoutMargin(R.id.marquee, RemoteViews.MARGIN_START, info.regionX * pitch, TypedValue.COMPLEX_UNIT_PX)
            // Put the baseline on the bottom of the cap rows, like the dot font.
            val baseline = (info.top - segment.top + TEXT_CAP_ROWS) * pitch
            setViewLayoutMargin(R.id.marquee, RemoteViews.MARGIN_TOP, baseline - ascent, TypedValue.COMPLEX_UNIT_PX)
            // A focusable TextView would swallow the first tap, so make it refresh too.
            setOnClickPendingIntent(R.id.marquee, onClick)
        }

        /** The clock as TextClocks (ticked by the launcher, seconds included) under the mask. */
        fun clock(segment: Segment, use24h: Boolean): RemoteViews = RemoteViews(pkg, R.layout.widget_segment_clock).apply {
            setImageViewBitmap(R.id.image, bitmap(widgetRenderer.renderSegment(plan, segment, 0)))
            setImageViewBitmap(R.id.mask, mask(segment))
            // Unlit colour only behind the TextClocks; elsewhere the band's bitmap (unlit dots, and "Updated hh:mm"
            // when the board is stale) shows through the mask.
            setInt(R.id.clock_row, "setBackgroundColor", theme.palette.unlit)

            val main = if (use24h) "HH:mm" else "h:mm"
            val (mainSize, mainAscent) = fitSystemFont("sans-serif", CLOCK_ROWS * pitch, "0")
            val (secondsSize, _) = fitSystemFont("sans-serif", SECONDS_ROWS * pitch, "0")
            for ((id, pattern, size) in listOf(Triple(R.id.clock_main, main, mainSize), Triple(R.id.clock_seconds, ":ss", secondsSize))) {
                setCharSequence(id, "setFormat24Hour", pattern)
                setCharSequence(id, "setFormat12Hour", pattern)
                setTextColor(id, theme.palette.lit)
                setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, size)
            }
            // The row aligns on the big digits' baseline: put it on the band's bottom dot row.
            setViewLayoutMargin(R.id.clock_row, RemoteViews.MARGIN_TOP, CLOCK_ROWS * pitch - mainAscent, TypedValue.COMPLEX_UNIT_PX)
        }

        private fun mask(segment: Segment): Bitmap {
            val w = plan.cols * rasterizer.pitch
            val h = segment.height * rasterizer.pitch
            return Bitmap.createBitmap(rasterizer.mask(plan.cols, segment.height), w, h, Bitmap.Config.ARGB_8888)
        }

        // RGB_565 halves bitmap memory; amber/green/white on black survive it fine.
        private fun bitmap(buffer: PixelBuffer): Bitmap = Bitmap.createBitmap(
            rasterizer.rasterize(buffer), rasterizer.widthPx(buffer), rasterizer.heightPx(buffer), Bitmap.Config.RGB_565,
        )

        /**
         * Text size (px) giving [capPx]-tall [sample] glyphs in a bold system [family], and the resulting ascent,
         * measured with the same fonts the launcher will use on this device.
         */
        private fun fitSystemFont(family: String, capPx: Float, sample: String, style: Int = Typeface.BOLD): Pair<Float, Float> {
            val paint = Paint().apply {
                typeface = Typeface.create(family, style)
                textSize = 100f
            }
            val bounds = Rect().also { paint.getTextBounds(sample, 0, sample.length, it) }
            paint.textSize = capPx / -bounds.top * 100f
            return paint.textSize to -paint.fontMetrics.ascent
        }
    }

    /** Spoken by TalkBack, since the board itself is just pictures. */
    private fun describe(frame: BoardFrame): String = when (val c = frame.content) {
        is BoardContent.Message -> listOfNotNull(c.title, c.detail).joinToString(". ")
        is BoardContent.Departures -> buildString {
            append("Departures from ${c.board.stationName}. ")
            if (c.board.departures.isEmpty()) append("No services.")
            c.board.departures.take(3).forEach { d ->
                append("${d.scheduled} to ${d.destination}")
                d.platform?.let { append(", platform $it") }
                append(", ${d.status.boardText.ifEmpty { "no information" }}. ")
            }
            append("Tap to refresh.")
        }
    }
}
