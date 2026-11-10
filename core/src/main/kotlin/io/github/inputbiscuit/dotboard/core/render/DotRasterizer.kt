package io.github.inputbiscuit.dotboard.core.render

/**
 * Turns a [PixelBuffer] into ARGB pixels where every cell is a round LED dot of [pitch] pixels:
 * lit cells in the palette's lit colour, unlit cells as faint dim dots, on the background colour.
 * The dot edge is anti-aliased; the grid itself stays exact.
 */
class DotRasterizer(val pitch: Int, val palette: LedPalette, dotDiameter: Float = 0.84f) {

    init {
        require(pitch >= 1)
    }

    private val coverage = coverage(dotDiameter)
    private val litStamp = IntArray(pitch * pitch) { blend(palette.background, palette.lit, coverage[it]) }
    private val unlitStamp = IntArray(pitch * pitch) { blend(palette.background, palette.unlit, coverage[it]) }

    /**
     * A "dot mask" for [cols] x [rows] cells: background colour everywhere except a see-through round hole at
     * each dot (same size and edge as the drawn dots). Laid over anything bright, e.g. a scrolling TextView on
     * the unlit colour, it turns it into LED dots on the board's grid.
     */
    fun mask(cols: Int, rows: Int): IntArray {
        val rgb = palette.background and 0x00FFFFFF
        val stamp = IntArray(pitch * pitch) { (((1 - coverage[it]) * 255).toInt() shl 24) or rgb }
        val w = cols * pitch
        val out = IntArray(w * rows * pitch)
        for (cy in 0 until rows) for (cx in 0 until cols) {
            val origin = cy * pitch * w + cx * pitch
            for (row in 0 until pitch) System.arraycopy(stamp, row * pitch, out, origin + row * w, pitch)
        }
        return out
    }

    fun widthPx(buffer: PixelBuffer) = buffer.width * pitch
    fun heightPx(buffer: PixelBuffer) = buffer.height * pitch

    /** Row-major ARGB, `widthPx * heightPx` long. Reuses [into] when it's the right size. */
    fun rasterize(buffer: PixelBuffer, into: IntArray? = null): IntArray {
        val w = widthPx(buffer)
        val out = into?.takeIf { it.size == w * heightPx(buffer) } ?: IntArray(w * heightPx(buffer))
        for (cy in 0 until buffer.height) {
            for (cx in 0 until buffer.width) {
                val stamp = if (buffer[cx, cy]) litStamp else unlitStamp
                val origin = cy * pitch * w + cx * pitch
                for (row in 0 until pitch) System.arraycopy(stamp, row * pitch, out, origin + row * w, pitch)
            }
        }
        return out
    }

    /** How much of each pixel in one cell the round dot covers (0..1), supersampled 4x4 for a smooth edge. */
    private fun coverage(diameter: Float): FloatArray {
        val r = pitch * diameter / 2f
        val c = pitch / 2f
        val samples = 4
        return FloatArray(pitch * pitch) { i ->
            val px = i % pitch
            val py = i / pitch
            var inside = 0
            for (sy in 0 until samples) for (sx in 0 until samples) {
                val dx = px + (sx + 0.5f) / samples - c
                val dy = py + (sy + 0.5f) / samples - c
                if (dx * dx + dy * dy <= r * r) inside++
            }
            inside / (samples * samples).toFloat()
        }
    }

    private fun blend(bg: Int, fg: Int, t: Float): Int {
        fun ch(shift: Int) = ((bg shr shift and 0xFF) * (1 - t) + (fg shr shift and 0xFF) * t).toInt() and 0xFF
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** How a dot grid fits a pixel area: [cols] x [rows] dots of [pitch] px each. */
data class BoardGeometry(val cols: Int, val rows: Int, val pitch: Int) {
    val widthPx get() = cols * pitch
    val heightPx get() = rows * pitch

    companion object {
        const val BASE_COLS = 256
        const val BASE_ROWS = 64
        const val MIN_PITCH = 3

        /**
         * The largest dot pitch that still gives at least a 256x64 board (or the minimum pitch for small areas),
         * then as many dots as fit. Wider or taller areas get extra columns or rows rather than bigger dots.
         */
        fun fit(widthPx: Int, heightPx: Int, minPitch: Int = MIN_PITCH): BoardGeometry {
            val pitch = maxOf(minPitch, minOf(widthPx / BASE_COLS, heightPx / BASE_ROWS))
            return BoardGeometry(
                cols = (widthPx / pitch).coerceAtLeast(1),
                rows = (heightPx / pitch).coerceAtLeast(1),
                pitch = pitch,
            )
        }
    }
}
