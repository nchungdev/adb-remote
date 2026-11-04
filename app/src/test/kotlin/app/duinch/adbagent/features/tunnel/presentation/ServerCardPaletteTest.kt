package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.duinch.remotetunnel.ui.ServerCardTone
import app.duinch.remotetunnel.ui.serverCardPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class ServerCardPaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = max(a.luminance(), b.luminance())
        val lo = min(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `header text meets WCAG AA in both themes`() {
        for (dark in listOf(false, true)) {
            for (tone in ServerCardTone.entries) {
                val palette = serverCardPalette(tone, dark)
                val ratio = contrast(palette.content, palette.card)
                assertTrue(ratio >= 4.5f, "$tone dark=$dark: $ratio")
            }
        }
    }
}
