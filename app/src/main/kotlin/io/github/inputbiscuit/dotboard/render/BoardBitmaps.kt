package io.github.inputbiscuit.dotboard.render

import android.graphics.Bitmap
import io.github.inputbiscuit.dotboard.core.render.Animation
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardGeometry
import io.github.inputbiscuit.dotboard.core.render.BoardRenderer
import io.github.inputbiscuit.dotboard.core.render.DotRasterizer
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import io.github.inputbiscuit.dotboard.core.render.PixelBuffer

/** Android adapter over the pure-Kotlin renderer: board data + pixel size in, [Bitmap] out. */
object BoardBitmaps {

    val renderer by lazy { BoardRenderer() }

    fun render(
        frame: BoardFrame,
        widthPx: Int,
        heightPx: Int,
        theme: LedTheme,
        animation: Animation = Animation.Static,
    ): Bitmap {
        val g = BoardGeometry.fit(widthPx, heightPx)
        return toBitmap(renderer.render(frame, g.cols, g.rows, animation), DotRasterizer(g.pitch, theme.palette))
    }

    fun toBitmap(buffer: PixelBuffer, rasterizer: DotRasterizer): Bitmap {
        val w = rasterizer.widthPx(buffer)
        val h = rasterizer.heightPx(buffer)
        return Bitmap.createBitmap(rasterizer.rasterize(buffer), w, h, Bitmap.Config.ARGB_8888)
    }
}
