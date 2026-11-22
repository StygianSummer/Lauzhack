package tea.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.min

/**
 * Off-screen renderer for looking at Compose UI from a jvm test, no window needed.
 *
 * ```
 * Screenshots.render("home_light", widthDp = 400, heightDp = 800) { TeaTheme(dark = false) { HomeScreen(...) } }
 * ```
 * PNGs go to `shared/build/screenshots/` (never commit them). Open them with an image viewer
 * (or an agent's file reader) and compare with `static/` and plan §8.
 */
object Screenshots {
    val outDir: File = File(System.getProperty("tea.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    /**
     * Renders [content] at [widthDp] x [heightDp] (dp, scaled by [density]) and writes `<name>.png`.
     * With [cropToContent], [content] is measured with wrap-content height and the image is cut to
     * it (use for long pages; screens using fillMaxSize should pass false). Images taller than
     * [tileHeightPx] are also split into `<name>_01.png`, `<name>_02.png`, … for easier viewing.
     * @return the files written (full image first).
     */
    fun render(
        name: String,
        widthDp: Int = 400,
        heightDp: Int = 800,
        density: Float = 1.5f,
        cropToContent: Boolean = false,
        tileHeightPx: Int = 1400,
        content: @Composable () -> Unit,
    ): List<File> {
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        var measured = h
        val scene = ImageComposeScene(w, h, Density(density)) {
            if (cropToContent) {
                Box(
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = false)
                        .onSizeChanged { measured = it.height },
                ) { content() }
            } else {
                content()
            }
        }
        try {
            // A few frames so async resources (fonts, images) and short animations settle.
            var t = 0L
            repeat(6) {
                scene.render(t)
                t += 200_000_000L
            }
            val image = scene.render(t)
            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val full = File(outDir, "$name.png")
            full.writeBytes(bytes)
            val written = mutableListOf(full)
            val buffered = ImageIO.read(full)
            val height = min(if (cropToContent) measured else h, buffered.height)
            val cropped = if (height < buffered.height) buffered.getSubimage(0, 0, buffered.width, height) else buffered
            if (cropped !== buffered) ImageIO.write(cropped, "png", full)
            if (height > tileHeightPx) {
                val tiles = ceil(height / tileHeightPx.toDouble()).toInt()
                for (i in 0 until tiles) {
                    val y = i * tileHeightPx
                    val tile = cropped.getSubimage(0, y, cropped.width, min(tileHeightPx, height - y))
                    val f = File(outDir, "${name}_${(i + 1).toString().padStart(2, '0')}.png")
                    ImageIO.write(tile, "png", f)
                    written += f
                }
            }
            return written
        } finally {
            scene.close()
        }
    }
}
