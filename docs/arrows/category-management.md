# Arrow: category-management

Category list and edit screens: hierarchy (MetaCategory/SubCategory), inheritance, emoji/color/value-type fields, value-type migration.

## Status

**PARTIAL** — last audited 2026-07-27. 73 of 76 specs confirmed implemented, 2 legitimately deferred, 1 genuine gap remains (CAT-UI-011a). CAT-UI-002 (drag-to-reorder) is now fully resolved — the generic widget landed as its own segment (`drag-reorder-list`) and this segment gained five new specs (CAT-UI-080-084) covering the category-specific adapter/persistence/reparent logic, all implemented. 10 previously-stale `[ ]` markers (CAT-NAV-001-004, CAT-UI-010, CAT-UI-011, CAT-UI-063-066) reconciled to `[x]` this pass — the spec file was stale, not the code.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/category-management.md

### EARS
- docs/specs/category-management.md (76 specs: CAT-NAV-*, CAT-UI-*)

### Tests
- app/src/androidTest/java/net/clahey/trackr/ui/components/OutlinedFieldBoxTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelHierarchyTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelGroupTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelHierarchyTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelDragTest.kt
- app/src/test/java/net/clahey/trackr/domain/ValueTypeConversionTest.kt
- app/src/test/java/net/clahey/trackr/domain/SiblingReindexTest.kt

### Code
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditScreen.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListScreen.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/category/DeleteCategoryDialog.kt
- app/src/main/java/net/clahey/trackr/ui/components/OutlinedFieldBox.kt
- app/src/main/java/net/clahey/trackr/domain/ValueTypeConversion.kt
- app/src/main/java/net/clahey/trackr/domain/SiblingReindex.kt
- app/src/main/java/net/clahey/trackr/domain/CategoryHasChildrenException.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalTrackrRepository.kt
- app/src/main/java/net/clahey/trackr/ui/navigation/AppNavHost.kt

_Note: the drag-to-reorder **widget** itself (`DragReorderList.kt`) is owned by the `drag-reorder-list` segment — this segment owns only the category-specific adapter/persistence layer (CAT-UI-080-084)._

## Architecture

**Purpose:** Category list (hierarchical, drag-reorderable in spec) and the category edit form (name/emoji/color/value-type, inheritance from parent MetaCategory, value-type migration warnings).

**Key Components:**
1. `CategoryListScreen`/`CategoryListViewModel` — hierarchical list, group operations
2. `CategoryEditScreen`/`CategoryEditViewModel` — field editing, inheritance, default-value handling
3. `ValueTypeConversion` — migration table for value-type changes

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| List | CAT-UI-001 to 006 | 5 | 0 | 0 (CAT-UI-002 resolved — see findings) |
| Edit — Display | CAT-UI-010 to 017 | 8 | 0 | 1 (CAT-UI-011a — see findings) |
| Edit — Validation | CAT-UI-020 to 022 | all | 0 | 0 |
| Edit — ValueType migration | CAT-UI-030 to 047 | all | 0 | 0 |
| Edit — Default Value | CAT-UI-063 to 066 | all | 0 | 0 |
| Hierarchy | CAT-UI-050 to 076 | all | 0 | 0 |
| Drag-to-Reorder Adapter | CAT-UI-080 to 084 | all | 0 | 0 |
| Navigation | CAT-NAV-* | all | 0 | 0 |
| SubCategory group menu | CAT-UI-058, CAT-NAV-011 | 0 | 2 | 0 |

**Summary:** 73 of 76 active specs implemented; 1 genuine active gap (CAT-UI-011a); 2 deferred. Fully reconciled this pass — no more stale `[ ]` markers.

## Key Findings

0. **This pass (2026-07-27):** 10 more stale `[ ]` markers reconciled to `[x]` (CAT-NAV-001-004, CAT-UI-010, CAT-UI-011, CAT-UI-063-066) — all confirmed implemented by direct code read, not just inferred. **CAT-UI-002 (drag-to-reorder) is now fully resolved**: the generic widget (`DragReorderList.kt`) shipped as its own segment (see `drag-reorder-list.md`), and this segment gained CAT-UI-080-084 covering the category-specific adapter (transactional reparent, sibling reindex, value-type-migration confirmation dialog on cross-type reparent, drop-completion callback contract, and the concurrent-childless-guard rejection path) — all implemented and annotated in `LocalTrackrRepository.kt`, `SiblingReindex.kt`, `ValueTypeConversion.kt`. **CAT-UI-011a is confirmed a genuine, still-unfixed gap**: `CategoryEditViewModel.kt`'s `save()` (around the `_exerciseDefaultSets.value.toIntOrNull() ?: 3` / `?: 15` lines) has no `≥1` enforcement and no `SaveResult.ValidationError` path for the default sets/reps fields — `"0"` or a negative value parses via `toIntOrNull()` and saves unvalidated; `CategoryEditScreen.kt` has no error-state wiring for these fields either (only `name`/`emoji` have `ValidationError` checks). Also backfilled `@spec CAT-UI-047` on `ValueTypeSelector` in `CategoryEditScreen.kt` (implemented, was untagged).

1. **Prior pass (2026-06-17): spec checkboxes were significantly stale.** 13 of the 16 nominally-active-gap specs from that pass were confirmed already implemented by reading the actual code, not just inferred:
   - `CAT-NAV-001` (FAB → create category): `CategoryListScreen.kt:79`, `FloatingActionButton(onClick = { onNavigateToCategoryEdit(null) })`
   - `CAT-NAV-002`/`003`/`004` (row tap → edit, save → back, cancel → back without persisting): wired through `AppNavHost.kt` and the edit screen's save/cancel handlers
   - `CAT-UI-010` (name/emoji/color/value-type fields): all present in `CategoryEditScreen.kt`
   - `CAT-UI-011`/`CAT-UI-011a` (Unit field, Default sets/reps fields): `numberDefaultUnit`, `exerciseDefaultSets`/`exerciseDefaultReps` StateFlows fully wired in `CategoryEditViewModel.kt`
   - `CAT-UI-063`-`066` (defaultValue save behavior): `CategoryEditViewModel.kt:270` literally has an inline `// CAT-UI-066` comment next to the `defaultValueDirty` logic — implemented and even informally self-annotated, just never flipped to `[x]` or given a proper `@spec` comment
   - `CAT-UI-015`/`CAT-UI-016` (Custom out-of-palette color swatch): `CategoryEditScreen.kt:467-470`, `hasCustomColor` check + `SwatchSpec(... label = customLabel, isSelected = true)`, where `customLabel = stringResource(R.string.category_color_custom)` resolves to `"Custom"` in `strings.xml`. **Correction**: the initial audit pass missed this because it grepped for the literal string `"Custom"` in the `.kt` file, but the label is a string-resource reference, not an inline literal — a false negative in the audit method, not the code. User-caught and corrected same day. Now annotated with `@spec CAT-UI-014, CAT-UI-015, CAT-UI-016`.
2. **CAT-UI-050 fixed this session.** Category list rows now render the resolved category color as a 48dp filled circle around the emoji (`CategoryListScreen.kt`'s `CategoryRow`), matching `EventRow`'s treatment exactly. LLD (`category-management.md`, `theme.md`) and EARS (CAT-UI-050, THEME-UI-010) updated first; spec flipped to `[x]`. No Compose UI test added — project preference is to defer new Compose tests to a future batch PR rather than add them piecemeal.
3. **CAT-UI-039 fixed this session.** `ValueTypeConversion.kt`'s `convertEventValue` gained a `NumberValue → Scale` branch: converts when the value is an exact integer in `[1, 10]` **and** the unit is null/blank (a populated unit blocks conversion rather than silently dropping it — refined from the original spec text during implementation). This makes `Scale → Number` a true reversible pair (Scale's `[1,10]` integer invariant round-trips losslessly), so `CategoryEditViewModel.warningTierFor` was updated: `Scale→Number` moved from "fully safe but irreversible" to "reversible, no warning"; `Number→Scale` classified "partial" (not all values convert). LLD conversion table, both specs' text, and tests (`ValueTypeConversionTest`, `CategoryEditViewModelTest`) updated together.
4. _(Historical, resolved this pass — see finding 0)_ CAT-UI-002 was the one confirmed genuine gap as of 2026-06-17: no drag handle, gesture, or drop-persistence code existed anywhere in `CategoryListScreen.kt`/`CategoryListViewModel.kt` despite the backend `reorderCategories` method existing. Now implemented via the `drag-reorder-list` segment + CAT-UI-080-084.
5. No reverse orphans — every `@spec CAT-*` annotation in code points to a real spec ID (re-verified 2026-07-27).

**Audit-method lesson:** the false negative on CAT-UI-015/016 came from grepping Kotlin source for a literal UI string instead of accounting for string-resource indirection. Future audits of this segment (and others using `stringResource(R.string....)`) should grep `strings.xml` for the resolved text, or grep for the resource name itself, not the displayed string.

## Work Required

### Must Fix (before MVP / Play Store testing)
_None._

### Should Fix
1. **CAT-UI-011a** — enforce "≥1 to save" on the Exercise category edit screen's default sets/reps fields. `CategoryEditViewModel.kt`'s `save()` needs a validation branch (mirroring the pattern already used for `name`/`emoji`) that produces `SaveResult.ValidationError` when either field parses to a value `< 1`, and `CategoryEditScreen.kt` needs the corresponding error-state UI wiring.

### Nice to Have
_None noted this pass._
