package com.trackr.app.ui.theme

val categoryColorPalette: List<Long> = listOf(
    0xFFE53935L, // Red
    0xFFFB8C00L, // Orange
    0xFFFFB300L, // Amber
    0xFF43A047L, // Green
    0xFF00897BL, // Teal
    0xFF00ACC1L, // Cyan
    0xFF1E88E5L, // Blue
    0xFF3949ABL, // Indigo
    0xFF8E24AAL, // Purple
    0xFFD81B60L, // Pink
    0xFF6D4C41L, // Brown
    0xFF757575L, // Grey
)

fun categoryColorForIndex(index: Int): Long = categoryColorPalette[index]

// @spec THEME-PROC-001, THEME-PROC-002
fun foregroundColorForBackground(argb: Long): Long {
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    fun linearize(c: Double) = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    val luminance = 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    return if (luminance < 0.179) 0xFFFFFFFFL else 0xFF000000L
}
