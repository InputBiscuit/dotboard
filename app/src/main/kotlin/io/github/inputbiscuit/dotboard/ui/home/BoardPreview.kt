package io.github.inputbiscuit.dotboard.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import io.github.inputbiscuit.dotboard.core.render.Animation
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardGeometry
import io.github.inputbiscuit.dotboard.core.render.DotRasterizer
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import io.github.inputbiscuit.dotboard.render.BoardBitmaps
import java.time.LocalTime

/**
 * Full-width, live 256x64 board: smooth "Calling at" scrolling, alternating rows and a ticking clock.
 * Uses the same renderer as the widget.
 */
@Composable
fun BoardPreview(content: BoardContent, theme: LedTheme, use24h: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(BoardGeometry.BASE_COLS / BoardGeometry.BASE_ROWS.toFloat())) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val pitch = (widthPx / BoardGeometry.BASE_COLS).coerceAtLeast(2)
        val cols = BoardGeometry.BASE_COLS
        val rows = BoardGeometry.BASE_ROWS

        val rasterizer = remember(pitch, theme) { DotRasterizer(pitch, theme.palette) }
        val bitmap = remember(pitch) { Bitmap.createBitmap(cols * pitch, rows * pitch, Bitmap.Config.ARGB_8888) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val pixels = remember(pitch) { IntArray(cols * pitch * rows * pitch) }
        val currentContent by rememberUpdatedState(content)
        val currentUse24h by rememberUpdatedState(use24h)
        var frameCount by remember { mutableIntStateOf(0) }

        LaunchedEffect(rasterizer, bitmap) {
            val renderer = BoardBitmaps.renderer
            val start = System.currentTimeMillis()
            var lastDraw = 0L
            while (true) {
                withFrameMillis { }
                val now = System.currentTimeMillis()
                if (now - lastDraw < FRAME_MILLIS) continue
                lastDraw = now
                val frame = BoardFrame(currentContent, LocalTime.now(), currentUse24h, showSeconds = true)
                val plan = renderer.plan(frame, cols, rows)
                val buffer = renderer.render(plan, Animation.at(now - start, plan))
                rasterizer.rasterize(buffer, pixels)
                bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                frameCount++
            }
        }

        Canvas(Modifier.matchParentSize()) {
            frameCount // read so the canvas redraws on every new frame
            drawImage(
                image,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

private const val FRAME_MILLIS = 33L
