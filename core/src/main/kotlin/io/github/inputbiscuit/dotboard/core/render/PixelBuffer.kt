package io.github.inputbiscuit.dotboard.core.render

/** A monochrome dot grid (like an SSD1322 panel's pixels): each cell is lit or unlit. */
class PixelBuffer(val width: Int, val height: Int) {
    init {
        require(width >= 0 && height >= 0)
    }

    private val bits = BooleanArray(width * height)

    operator fun get(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && bits[y * width + x]

    operator fun set(x: Int, y: Int, lit: Boolean) {
        if (x in 0 until width && y in 0 until height) bits[y * width + x] = lit
    }

    /** Rows [top, top + rows) as a new buffer. */
    fun crop(top: Int, rows: Int): PixelBuffer {
        val out = PixelBuffer(width, rows)
        for (y in 0 until rows) for (x in 0 until width) out[x, y] = this[x, top + y]
        return out
    }

    val litCount: Int get() = bits.count { it }

    /** '#' for lit, '.' for unlit, one line per row. Handy in tests. */
    fun toAscii(): String = buildString {
        for (y in 0 until height) {
            for (x in 0 until width) append(if (this@PixelBuffer[x, y]) '#' else '.')
            if (y < height - 1) append('\n')
        }
    }
}
