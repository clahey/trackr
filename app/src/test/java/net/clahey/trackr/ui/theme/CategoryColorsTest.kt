package net.clahey.trackr.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryColorsTest {

    // @spec THEME-PROC-001
    @Test fun `dark red produces black foreground`() {
        // 0xFFE53935 — Red 600, relative luminance ~0.198 which is >= 0.179 threshold
        val fg = foregroundColorForBackground(0xFFE53935L)
        assertEquals(0xFF000000L, fg)
    }

    // @spec THEME-PROC-001
    @Test fun `amber produces black foreground`() {
        // 0xFFFFB300 — Amber, luminance above 0.179
        val fg = foregroundColorForBackground(0xFFFFB300L)
        assertEquals(0xFF000000L, fg)
    }

    // @spec THEME-PROC-001
    @Test fun `white produces black foreground`() {
        val fg = foregroundColorForBackground(0xFFFFFFFFL)
        assertEquals(0xFF000000L, fg)
    }

    // @spec THEME-PROC-001
    @Test fun `black produces white foreground`() {
        val fg = foregroundColorForBackground(0xFF000000L)
        assertEquals(0xFFFFFFFFL, fg)
    }

    // @spec THEME-PROC-001 — luminance threshold is 0.179
    @Test fun `color just below threshold produces white foreground`() {
        // Pure blue 0xFF0000FF has relative luminance ~0.0722, below threshold
        val fg = foregroundColorForBackground(0xFF0000FFL)
        assertEquals(0xFFFFFFFFL, fg)
    }

    // @spec THEME-PROC-002
    @Test fun `foreground is always white or black`() {
        val testColors = listOf(
            0xFFE53935L, 0xFFFB8C00L, 0xFFFFB300L, 0xFF43A047L,
            0xFF00897BL, 0xFF00ACC1L, 0xFF1E88E5L, 0xFF3949ABL,
            0xFF8E24AAL, 0xFFD81B60L, 0xFF6D4C41L, 0xFF757575L,
        )
        for (color in testColors) {
            val fg = foregroundColorForBackground(color)
            assertTrue(
                "Foreground for $color should be white or black, was $fg",
                fg == 0xFFFFFFFFL || fg == 0xFF000000L,
            )
        }
    }

    // @spec THEME-UI-020
    @Test fun `palette contains exactly 12 colors`() {
        assertEquals(12, categoryColorPalette.size)
    }

    // @spec THEME-UI-020
    @Test fun `palette contains expected colors in order`() {
        assertEquals(0xFFE53935L, categoryColorPalette[0])  // Red
        assertEquals(0xFFFB8C00L, categoryColorPalette[1])  // Orange
        assertEquals(0xFFFFB300L, categoryColorPalette[2])  // Amber
        assertEquals(0xFF757575L, categoryColorPalette[11]) // Grey
    }

    // @spec THEME-UI-021
    @Test fun `index 0 returns Red`() {
        assertEquals(0xFFE53935L, categoryColorForIndex(0))
    }

    // @spec THEME-UI-021
    @Test fun `index 11 returns Grey`() {
        assertEquals(0xFF757575L, categoryColorForIndex(11))
    }
}

private fun assertTrue(message: String, condition: Boolean) {
    if (!condition) throw AssertionError(message)
}
