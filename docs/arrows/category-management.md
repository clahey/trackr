# Arrow: category-management

Category list and edit screens: hierarchy (MetaCategory/SubCategory), inheritance, emoji/color/value-type fields, value-type migration.

## Status

**PARTIAL** — last audited 2026-06-17, corrected same day (git SHA `be05346`). 53 of 71 specs marked implemented in the spec file, but the `[ ]` markers on the remaining 16 are significantly stale: 13 were confirmed already implemented by direct code inspection (CAT-UI-015/016 corrected after an initial false-negative grep). 3 genuine gaps confirmed; CAT-UI-050 fix in progress.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/category-management.md

### EARS
- docs/specs/category-management.md (71 specs: CAT-NAV-*, CAT-UI-*)

### Tests
- app/src/androidTest/java/net/clahey/trackr/ui/components/OutlinedFieldBoxTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelHierarchyTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelGroupTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelHierarchyTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelTest.kt

### Code
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditScreen.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListScreen.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/category/DeleteCategoryDialog.kt
- app/src/main/java/net/clahey/trackr/ui/components/OutlinedFieldBox.kt
- app/src/main/java/net/clahey/trackr/domain/ValueTypeConversion.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalTrackrRepository.kt
- app/src/main/java/net/clahey/trackr/ui/navigation/AppNavHost.kt

## Architecture

**Purpose:** Category list (hierarchical, drag-reorderable in spec) and the category edit form (name/emoji/color/value-type, inheritance from parent MetaCategory, value-type migration warnings).

**Key Components:**
1. `CategoryListScreen`/`CategoryListViewModel` — hierarchical list, group operations
2. `CategoryEditScreen`/`CategoryEditViewModel` — field editing, inheritance, default-value handling
3. `ValueTypeConversion` — migration table for value-type changes

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| List | CAT-UI-001 to 006 | 5 | 0 | 1 (CAT-UI-002, see below) |
| Edit — Display | CAT-UI-010 to 017 | confirmed-implemented, including CAT-UI-015/016 (see findings) | 0 | 0 |
| Edit — Validation | CAT-UI-020 to 022 | all | 0 | 0 |
| Edit — ValueType migration | CAT-UI-030 to 047 | most | 0 | CAT-UI-039 genuinely missing |
| Edit — Default Value | CAT-UI-063 to 066 | confirmed-implemented (stale markers) | 0 | 0 |
| Hierarchy | CAT-UI-050 to 076 | most | 0 | CAT-UI-050 genuinely missing (fix in progress) |
| Navigation | CAT-NAV-* | confirmed-implemented (stale markers) | 0 | 0 |

**Summary:** Raw count is 53 implemented / 16 active gap / 2 deferred. After spot-verification, the *real* picture is closer to 66 implemented / 3 active gap / 2 deferred — the spec file hasn't been kept in sync with completed work.

## Key Findings

1. **Spec checkboxes are significantly stale.** 13 of the 16 nominally-active-gap specs were confirmed already implemented by reading the actual code, not just inferred:
   - `CAT-NAV-001` (FAB → create category): `CategoryListScreen.kt:79`, `FloatingActionButton(onClick = { onNavigateToCategoryEdit(null) })`
   - `CAT-NAV-002`/`003`/`004` (row tap → edit, save → back, cancel → back without persisting): wired through `AppNavHost.kt` and the edit screen's save/cancel handlers
   - `CAT-UI-010` (name/emoji/color/value-type fields): all present in `CategoryEditScreen.kt`
   - `CAT-UI-011`/`CAT-UI-011a` (Unit field, Default sets/reps fields): `numberDefaultUnit`, `exerciseDefaultSets`/`exerciseDefaultReps` StateFlows fully wired in `CategoryEditViewModel.kt`
   - `CAT-UI-063`-`066` (defaultValue save behavior): `CategoryEditViewModel.kt:270` literally has an inline `// CAT-UI-066` comment next to the `defaultValueDirty` logic — implemented and even informally self-annotated, just never flipped to `[x]` or given a proper `@spec` comment
   - `CAT-UI-015`/`CAT-UI-016` (Custom out-of-palette color swatch): `CategoryEditScreen.kt:467-470`, `hasCustomColor` check + `SwatchSpec(... label = customLabel, isSelected = true)`, where `customLabel = stringResource(R.string.category_color_custom)` resolves to `"Custom"` in `strings.xml`. **Correction**: the initial audit pass missed this because it grepped for the literal string `"Custom"` in the `.kt` file, but the label is a string-resource reference, not an inline literal — a false negative in the audit method, not the code. User-caught and corrected same day. Now annotated with `@spec CAT-UI-014, CAT-UI-015, CAT-UI-016`.
2. **3 confirmed genuine gaps** (verified absent, not just unchecked):
   - `CAT-UI-002` — drag-to-reorder UI. The backend method `TrackrRepository.reorderCategories(orderedIds)` exists, but there is no drag handle, drag gesture, or drop-persistence code anywhere in `CategoryListScreen.kt`/`CategoryListViewModel.kt`. UI half entirely missing.
   - `CAT-UI-039` — Number→Scale conversion. `ValueTypeConversion.kt` has Scale→Number, Scale→Text, and Text→Scale, but no Number→Scale branch.
   - `CAT-UI-050` — color indicator on category list rows. Zero color-related code in `CategoryListScreen.kt` at audit time; rows showed emoji/name/value-type only. Same underlying gap as `theme`'s THEME-UI-010. **Fix in progress** as of this audit correction — LLD (`category-management.md`, `theme.md`) and EARS (CAT-UI-050, THEME-UI-010) already updated to specify a 48dp circle-avatar treatment matching `EventRow`; code not yet written.
3. No reverse orphans — every `@spec CAT-*` annotation in code points to a real spec ID.

**Audit-method lesson:** the false negative on CAT-UI-015/016 came from grepping Kotlin source for a literal UI string instead of accounting for string-resource indirection. Future audits of this segment (and others using `stringResource(R.string....)`) should grep `strings.xml` for the resolved text, or grep for the resource name itself, not the displayed string.

## Work Required

### Must Fix (before MVP / Play Store testing)
1. **CAT-UI-050** — add a color indicator to category list rows. Category color is a core, user-facing attribute (this session's `OutlinedFieldBox` work specifically polished the color picker in the editor); having it invisible everywhere else in the app is a visible, easy-to-notice gap for early testers. In progress.
2. **CAT-UI-002** — drag-to-reorder UI. Judgment call: if MVP testers are expected to create many categories, having no way to reorder them (beyond creation order) may be a real usability gap; if category counts are expected to stay small for initial testing, this can likely slip to a fast-follow.

### Should Fix
1. **CAT-UI-039** — Number→Scale conversion. Lower urgency than the above two since it's a narrow migration-path edge case (the spec itself was written anticipating future implementation).
2. **Reconcile the remaining stale `[ ]` markers** (CAT-NAV-001-004, CAT-UI-010, CAT-UI-011, CAT-UI-011a, CAT-UI-063-066 — CAT-UI-015/016 already corrected) to `[x]` and add proper `@spec` annotations (several currently only have informal inline comments, e.g. `// CAT-UI-066`) so the spec file can be trusted again without re-auditing by hand.

### Nice to Have
_None noted this pass._
