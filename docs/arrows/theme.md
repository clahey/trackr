# Arrow: theme

Material3 theme, preset color palette, and category-color resolution/application.

## Status

**PARTIAL** — last audited 2026-06-17 (git SHA `be05346`). 10 of 11 specs implemented; the one open item overlaps with a confirmed gap in `category-management`.

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

1. **THEME-UI-010** ("Category colors shall be applied as container background colors on category chips, category list rows, and the category edit screen color preview swatch") is marked an active gap. The `category-management` audit independently confirmed that the category **list row** currently has zero color-related code (`CAT-UI-050` finding) — so at least one of the three required locations is confirmed missing. The chip and edit-screen-preview locations were not independently re-verified here; treat as likely-partial rather than fully missing.

## Work Required

### Must Fix
1. Apply category color to category list rows — this is the same underlying gap as `category-management`'s CAT-UI-050; fixing one should fix both. (THEME-UI-010, CAT-UI-050)

### Should Fix
_None noted this pass._

### Nice to Have
_None noted this pass._
