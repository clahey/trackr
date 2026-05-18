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

fun categoryColorForIndex(index: Int): Long = categoryColorPalette[index % categoryColorPalette.size]

fun foregroundColorForBackground(argb: Long): Long = TODO()
