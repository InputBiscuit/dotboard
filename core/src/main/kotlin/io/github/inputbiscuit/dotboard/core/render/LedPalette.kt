package io.github.inputbiscuit.dotboard.core.render

/** ARGB colours for a dot-matrix panel. */
data class LedPalette(val lit: Int, val unlit: Int, val background: Int = 0xFF000000.toInt())

enum class LedTheme(val label: String, val palette: LedPalette) {
    AMBER("Amber", palette(0xFFFFB300.toInt())),
    YELLOW_GREEN("Yellow-green", palette(0xFFC8F03C.toInt())),
    WHITE("White", palette(0xFFF2F2EE.toInt())),
}

/** Unlit dots are the lit colour at ~10% brightness: just visible on black, like a real panel. */
private fun palette(lit: Int): LedPalette {
    fun dim(shift: Int) = (((lit shr shift) and 0xFF) * 0.10).toInt() shl shift
    return LedPalette(lit = lit, unlit = 0xFF000000.toInt() or dim(16) or dim(8) or dim(0))
}
