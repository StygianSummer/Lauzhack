package tea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import tea.ui.screens.gallery.GalleryContent
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Renders the whole gallery off-screen (light, dark, compact) and checks the PNGs exist. */
class GalleryRenderTest {
    private val now = Instant.parse("2026-11-21T14:05:00Z")

    private fun gallery(
        name: String,
        dark: Boolean,
        widthDp: Int,
        largeText: Boolean = false,
        heightDp: Int = 12000,
    ) {
        val files = Screenshots.render(name, widthDp = widthDp, heightDp = heightDp, density = 1.25f, cropToContent = true) {
            TeaTheme(dark = dark, largeText = largeText) {
                Column(
                    androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .background(TeaTheme.colors.paper)
                        .padding(TeaSpacing.s2),
                    verticalArrangement = Arrangement.spacedBy(TeaSpacing.s3),
                ) {
                    GalleryContent(now)
                }
            }
        }
        assertTrue(files.all { it.length() > 0 })
    }

    @Test fun galleryLight() = gallery("gallery_light", dark = false, widthDp = 760)
    @Test fun galleryDark() = gallery("gallery_dark", dark = true, widthDp = 760)
    @Test fun galleryCompact() = gallery("gallery_compact", dark = false, widthDp = 380, largeText = true, heightDp = 16000)
    @Test fun galleryCompactDark() = gallery("gallery_compact_dark", dark = true, widthDp = 380, largeText = true, heightDp = 16000)
}
