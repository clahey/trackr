# Arrow: theme

Material3 theme, preset color palette, and category-color resolution/application.

## Status

**PARTIAL** — last audited 2026-06-17, updated same day (git SHA `be05346`). 10 of 11 specs implemented; the remaining gap is now scoped to filter chips only (category list rows fixed; CAT-UI-050 landed).

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
| Application | THEME-UI-* | 9 of 10 | 0 | 1 |

**Summary:** 10 of 11 active specs implemented; 1 active gap (THEME-UI-010).

## Key Findings

1. **CAT-UI-050 landed** — `CategoryListScreen.kt`'s `CategoryRow` now renders the resolved category color as a 48dp filled circle around the emoji (`Box` + `CircleShape` background + `foregroundColorForBackground`), mirroring `EventRow` exactly. THEME-UI-010's text was narrowed accordingly to drop "category list rows" from its container-background scope (that surface is now circle-avatar, like event rows).
2. **THEME-UI-010 remains an active gap, now scoped to filter chips only.** Checked `HomeScreen.kt`'s `FilterChip` calls directly — they apply no category-color styling (no custom `containerColor`, no border color). This is the same underlying gap as `event-logging`'s EL-UI-015 ("MetaCategory filter chip shall display a colored border... filled background... when active"). The edit-screen color picker's palette swatches (the other remaining THEME-UI-010 location) are confirmed implemented via `CategoryEditScreen.kt`'s `Swatch` composable.

## Work Required

### Must Fix
1. Apply category color to timeline filter chips (border when unselected, filled background when active) — single fix should close both THEME-UI-010 and EL-UI-015.

### Should Fix
_None noted this pass._

### Nice to Have
_None noted this pass._
