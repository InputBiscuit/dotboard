package io.github.inputbiscuit.dotboard.core.render

import java.text.Normalizer

class Glyph(val advance: Int, val rows: Int, private val bits: BooleanArray) {
    fun lit(x: Int, y: Int): Boolean = bits[y * advance + x]
}

/**
 * A fixed-height bitmap font. Glyphs are drawn dot-for-dot onto a [PixelBuffer]: no scaling, no anti-aliasing.
 * Each glyph's advance includes its trailing one-dot gap.
 */
class BitmapFont(val name: String, val ascent: Int, val descent: Int, private val glyphs: Map<Int, Glyph>) {

    /** Rows from the top of a line to the bottom of descenders. */
    val height: Int get() = ascent + descent

    fun glyph(codePoint: Int): Glyph? = glyphs[codePoint]

    /** Width of [text] in dots, excluding the final glyph's trailing gap. */
    fun width(text: String): Int {
        var w = 0
        forEachGlyph(text) { w += it.advance }
        return (w - 1).coerceAtLeast(0)
    }

    /**
     * Draws [text] with its top-left at ([x], [y]), only touching columns in [clipLeft] until [clipRight].
     * Returns the x after the last glyph.
     */
    fun draw(
        buffer: PixelBuffer,
        text: String,
        x: Int,
        y: Int,
        clipLeft: Int = 0,
        clipRight: Int = buffer.width,
    ): Int {
        var pen = x
        forEachGlyph(text) { g ->
            if (pen < clipRight && pen + g.advance > clipLeft) {
                for (gy in 0 until g.rows) for (gx in 0 until g.advance) {
                    val px = pen + gx
                    if (px in clipLeft until clipRight && g.lit(gx, gy)) buffer[px, y + gy] = true
                }
            }
            pen += g.advance
        }
        return pen
    }

    /** The longest prefix of [text] (ending on a whole glyph) that fits in [maxWidth], without trailing spaces. */
    fun fit(text: String, maxWidth: Int): String {
        if (width(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && width(text.substring(0, end)) > maxWidth) end--
        return text.substring(0, end).trimEnd()
    }

    /** Like [fit] but drops whole words (and dangling "&"/"and"), falling back to [fit] for one long word. */
    fun fitWords(text: String, maxWidth: Int): String {
        if (width(text) <= maxWidth) return text
        val words = text.split(' ')
        for (n in words.size - 1 downTo 1) {
            val candidate = words.take(n).dropLastWhile { it == "&" || it == "and" || it == "-" }.joinToString(" ")
            if (candidate.isNotEmpty() && width(candidate) <= maxWidth) return candidate
        }
        return fit(text, maxWidth)
    }

    /** Greedy word wrap into lines no wider than [maxWidth]; over-long words are cut with [fit]. */
    fun wrap(text: String, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (width(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotEmpty()) lines += line
                line = fit(word, maxWidth)
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private inline fun forEachGlyph(text: String, action: (Glyph) -> Unit) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val g = glyphs[cp] ?: substitute(cp)
            if (g != null) action(g)
        }
    }

    /** Close equivalents for characters the typeface doesn't have (accents, typographic punctuation, …). */
    private fun substitute(cp: Int): Glyph? {
        SUBSTITUTES[cp]?.let { glyphs[it.code]?.let { g -> return g } }
        val base = Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFD)
            .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        if (base != null && base.code != cp) glyphs[base.code]?.let { return it }
        return glyphs['?'.code]
    }

    companion object {
        private val SUBSTITUTES = mapOf(
            0x2018 to '\'', 0x2019 to '\'', 0x201C to '"', 0x201D to '"',
            0x2013 to '-', 0x2014 to '-', 0x2010 to '-', 0x00A0 to ' ',
            '['.code to '(', ']'.code to ')', '{'.code to '(', '}'.code to ')', '~'.code to '-', '`'.code to '\'',
            '@'.code to 'a', '$'.code to 'S', 0x00A3 to 'L',
        )

        /** Parses the `.dmf` format written by `tools/fontgen/FontGen.java`. */
        fun parse(text: String): BitmapFont {
            var name = ""
            var ascent = 0
            var descent = 0
            val glyphs = HashMap<Int, Glyph>()
            // Comments are "# ..."; glyph rows are only '#' and '.', so never contain a space.
            val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("# ") }.iterator()
            while (lines.hasNext()) {
                val parts = lines.next().trim().split(' ')
                when (parts[0]) {
                    "font" -> name = parts[1]
                    "ascent" -> ascent = parts[1].toInt()
                    "descent" -> descent = parts[1].toInt()
                    "glyph" -> {
                        val cp = parts[1].toInt(16)
                        val advance = parts[2].toInt()
                        val rows = ascent + descent
                        val bits = BooleanArray(advance * rows)
                        repeat(rows) { y ->
                            val row = lines.next()
                            require(row.length == advance) { "Bad row for U+${parts[1]} in $name" }
                            row.forEachIndexed { x, c -> bits[y * advance + x] = c == '#' }
                        }
                        glyphs[cp] = Glyph(advance, rows, bits)
                    }
                }
            }
            return BitmapFont(name, ascent, descent, glyphs)
        }

        fun fromResource(file: String): BitmapFont {
            val stream = requireNotNull(BitmapFont::class.java.getResourceAsStream("/io/github/inputbiscuit/dotboard/core/fonts/$file")) { "Missing font $file" }
            return stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }
    }
}

/** The Dot Matrix typeface cuts bundled with the app. */
class DotFonts(val regular: BitmapFont, val bold: BitmapFont, val boldTall: BitmapFont) {
    companion object {
        val default: DotFonts by lazy {
            DotFonts(
                regular = BitmapFont.fromResource("regular.dmf"),
                bold = BitmapFont.fromResource("bold.dmf"),
                boldTall = BitmapFont.fromResource("bold-tall.dmf"),
            )
        }
    }
}
