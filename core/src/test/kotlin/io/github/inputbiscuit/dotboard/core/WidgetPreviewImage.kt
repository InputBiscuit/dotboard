package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.FakeDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.core.render.BoardFrame
import io.github.inputbiscuit.dotboard.core.render.BoardRenderer
import io.github.inputbiscuit.dotboard.core.render.DotRasterizer
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the launcher's widget-picker preview with the real renderer. Skipped unless asked for:
 *   ./gradlew :core:test --tests '*WidgetPreviewImage*' -PpreviewOut=$PWD/app/src/main/res/drawable-nodpi/widget_preview.png
 */
class WidgetPreviewImage {

    @Test fun `render widget preview png`() {
        val out = System.getProperty("dotboard.previewOut").orEmpty()
        assumeTrue("set -PpreviewOut to write the preview", out.isNotEmpty())

        val board = runBlocking {
            FakeDepartureRepository(
                scenario = { FakeScenario.MIXED },
                stationName = { "London Paddington" },
                now = { ZonedDateTime.of(2026, 11, 9, 22, 6, 0, 0, ZoneId.of("Europe/London")) },
                latencyMillis = 0,
            ).board(BoardQuery("PAD"))
        }
        val buffer = BoardRenderer().render(BoardFrame(BoardContent.Departures(board), LocalTime.of(22, 5, 41)), 256, 64)
        val rasterizer = DotRasterizer(pitch = 3, palette = LedTheme.AMBER.palette)
        val w = rasterizer.widthPx(buffer)
        val h = rasterizer.heightPx(buffer)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, w, h, rasterizer.rasterize(buffer), 0, w)
        File(out).apply { parentFile.mkdirs() }.also { ImageIO.write(image, "png", it) }
    }
}
