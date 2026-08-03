# Theme Specs

LLD: `docs/llds/theme.md`

---

## App Theming

- [x] **THEME-UI-001**: On devices running Android 12 (API 31) or higher, the system shall apply dynamic color theming derived from the user's system wallpaper color scheme.
- [x] **THEME-UI-002**: On devices running Android below API 31, the system shall apply a static light or dark color scheme seeded from the brand color `0xFF37618E`.
- [x] **THEME-UI-003**: The system shall switch between light and dark color schemes based on the device's system dark mode setting; no in-app dark mode toggle shall be provided in v1.
- [x] **THEME-UI-004**: The system shall use the default Material 3 type scale throughout; no custom fonts shall be applied in v1.
- [x] **THEME-UI-005**: The system shall use the default Material 3 shape scale throughout; no custom corner radius overrides shall be applied in v1.

## Category Color System

- [x] **THEME-UI-010**: Category colors shall be applied as container background colors on the category edit screen color picker's palette swatches. (Timeline filter chips use a distinct selection-state treatment, specified separately as EL-UI-015.)
- [x] **THEME-UI-011**: On timeline event rows, the category color shall fill a 48dp circle avatar on the left of each row; the category emoji shall be centered inside using `foregroundColorForBackground(categoryColor)`; the row card surface shall use the M3 surface container color.
- [x] **THEME-PROC-001**: When rendering a category color as a container background, the system shall compute the foreground color (text and icons) using WCAG relative luminance: white for backgrounds with relative luminance below 0.179, black for backgrounds at or above 0.179.
- [x] **THEME-PROC-002**: The foreground color for category containers shall be either white or black only; Material 3's `contentColorFor()` shall not be used for category colors, as they are arbitrary user values outside the app's tonal palette.

## Preset Palette

- [x] **THEME-UI-020**: The category color picker shall present exactly the following 12 colors in order:

  | Name | ARGB |
  |---|---|
  | Red | `0xFFE53935` |
  | Orange | `0xFFFB8C00` |
  | Amber | `0xFFFFB300` |
  | Green | `0xFF43A047` |
  | Teal | `0xFF00897B` |
  | Cyan | `0xFF00ACC1` |
  | Blue | `0xFF1E88E5` |
  | Indigo | `0xFF3949AB` |
  | Purple | `0xFF8E24AA` |
  | Pink | `0xFFD81B60` |
  | Brown | `0xFF6D4C41` |
  | Grey | `0xFF757575` |

- [x] **THEME-UI-021**: When a new category is created, the system shall assign a default color of `palette[nextIndex]` where `nextIndex` is obtained from `getAndIncrementNextCategoryColorIndex(palette.size)` (see LS-BE-081), which cycles within `[0, palette.size)` so that consecutive categories always receive distinct colors and adding palette entries in the future does not retroactively change which color is next.
