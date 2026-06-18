# Arrow: theme

Material3 theme, preset color palette, and category-color resolution/application.

## Status

**OK** — last audited 2026-06-17, updated same day (git SHA `be05346`). 11 of 11 specs implemented. THEME-UI-010 narrowed to the edit-screen palette swatches (now `[x]`); filter-chip color treatment is owned by `event-logging`'s EL-UI-015, now also implemented.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/theme.md

### EARS
- docs/specs/theme.md (11 specs: THEME-UI-*, THEME-PROC-*)

### Tests
- app/src/test/java/net/clahey/trackr/ui/theme/CategoryColorsTest.kt

### Code
- app/src/main/java/net/clahey/trackr/ui/theme/CategoryColors.kt
- app/src/main/java/net/clahey/trackr/ui/theme/Theme.kt
- app/src/main/java/net/clahey/trackr/ui/components/EventRow.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListScreen.kt
- app/src/main/java/net/clahey/trackr/ui/home/HomeScreen.kt (filter chips, `CategoryFilterChip`)

## Architecture

**Purpose:** Defines the preset color palette (`docs/llds/theme.md § Preset Palette`) and the rules for resolving/applying category color across the UI.

**Key Components:**
1. Preset palette — fixed `Long` ARGB values, UI-layer constant
2. `CategoryColors` — resolution helpers
3. Material3 `Theme.kt` — app-wide theming

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| Palette / resolution | THEME-PROC-* | all | 0 | 0 |
| Application | THEME-UI-* | 10 of 10 | 0 | 0 |

**Summary:** 11 of 11 active specs implemented; 0 active gaps.

## Key Findings

1. **CAT-UI-050 landed** — `CategoryListScreen.kt`'s `CategoryRow` now renders the resolved category color as a 48dp filled circle around the emoji (`Box` + `CircleShape` background + `foregroundColorForBackground`), mirroring `EventRow` exactly. THEME-UI-010's text was narrowed accordingly to drop "category list rows" from its container-background scope (that surface is now circle-avatar, like event rows).
2. **EL-UI-015 landed** — `HomeScreen.kt` now has a shared `CategoryFilterChip` composable used by both MetaCategory and SubCategory chips: colored border via `FilterChipDefaults.filterChipBorder` when unselected, filled `selectedContainerColor` + `foregroundColorForBackground` label when active. THEME-UI-010 was narrowed to drop chips entirely — chip color is a selection-state pattern distinct from the always-filled "container background" pattern, so its specification now lives solely in `event-logging`'s EL-UI-015 to avoid two segments owning the same behavior.

## Work Required

### Must Fix
_None — fully implemented._

### Should Fix
_None noted this pass._

### Nice to Have
_None noted this pass._
