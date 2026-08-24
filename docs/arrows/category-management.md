# Arrow: category-management

Category list and edit screens: hierarchy (MetaCategory/SubCategory), inheritance, emoji/color/value-type fields, value-type migration.

## Status

**PARTIAL** — last audited 2026-08-14. 75 of 77 specs confirmed implemented, 2 legitimately deferred, 0 genuine gaps remain. CAT-UI-018 (initial-load edit gate) was added 2026-08-14 to close a real data-loss defect — see finding 6. CAT-UI-002 (drag-to-reorder) is now fully resolved — the generic widget landed as its own segment (`drag-reorder-list`) and this segment gained five new specs (CAT-UI-080-084) covering the category-specific adapter/persistence/reparent logic, all implemented. CAT-UI-011a was reworded this pass to match accepted current behavior (no minimum-value enforcement on Exercise default sets/reps) rather than fixed in code — see finding 0. 10 previously-stale `[ ]` markers (CAT-NAV-001-004, CAT-UI-010, CAT-UI-011, CAT-UI-063-066) reconciled to `[x]` this pass — the spec file was stale, not the code.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/category-management.md

### EARS
- docs/specs/category-management.md (77 specs: CAT-NAV-*, CAT-UI-*)

### Tests
- app/src/androidTest/java/net/clahey/trackr/ui/components/OutlinedFieldBoxTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelHierarchyTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelLoadGateTest.kt
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
| Edit — Display | CAT-UI-010 to 018 | 9 | 0 | 0 |
| Edit — Validation | CAT-UI-020 to 022 | all | 0 | 0 |
| Edit — ValueType migration | CAT-UI-030 to 047 | all | 0 | 0 |
| Edit — Default Value | CAT-UI-063 to 066 | all | 0 | 0 |
| Hierarchy | CAT-UI-050 to 076 | all | 0 | 0 |
| Drag-to-Reorder Adapter | CAT-UI-080 to 084 | all | 0 | 0 |
| Navigation | CAT-NAV-* | all | 0 | 0 |
| SubCategory group menu | CAT-UI-058, CAT-NAV-011 | 0 | 2 | 0 |

**Summary:** 75 of 77 active specs implemented; 0 active gaps; 2 deferred. No stale `[ ]` markers.

## Key Findings

0. **This pass (2026-07-27):** 10 more stale `[ ]` markers reconciled to `[x]` (CAT-NAV-001-004, CAT-UI-010, CAT-UI-011, CAT-UI-063-066) — all confirmed implemented by direct code read, not just inferred. **CAT-UI-002 (drag-to-reorder) is now fully resolved**: the generic widget (`DragReorderList.kt`) shipped as its own segment (see `drag-reorder-list.md`), and this segment gained CAT-UI-080-084 covering the category-specific adapter (transactional reparent, sibling reindex, value-type-migration confirmation dialog on cross-type reparent, drop-completion callback contract, and the concurrent-childless-guard rejection path) — all implemented and annotated in `LocalTrackrRepository.kt`, `SiblingReindex.kt`, `ValueTypeConversion.kt`. **CAT-UI-011a descoped, not fixed (user decision):** the spec previously required `≥1` enforcement on the Exercise default sets/reps fields, which `CategoryEditViewModel.kt`'s `save()` never actually implemented (`toIntOrNull() ?: 3` / `?: 15` — unparseable input falls back to 3/15, but a parseable non-positive value like `"0"` saves as-is, with no `SaveResult.ValidationError` path or error-state UI). Rather than adding that validation, the spec and LLD were reworded to describe this as accepted current behavior — low priority, not worth the added `ValidationError` branch and UI wiring right now. Also backfilled `@spec CAT-UI-047` on `ValueTypeSelector` in `CategoryEditScreen.kt` (implemented, was untagged).

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
6. **CAT-UI-018 added 2026-08-14, closing a real data-loss defect.** `CategoryEditViewModel` seeded its form state from independent one-shot reads while accepting input throughout, so a save issued before a read landed persisted defaults over stored values — writing a `MetaCategory` over a `SubCategory` whose parent hadn't loaded, or a disabled default reminder over a configured one. A read landing after the user had typed overwrote their input instead. The new spec gates all editing and saving until every initial-state read for the current mode completes, counting a read that finds no row as complete — without that clause the common case (a category with no reminder) would have stayed gated forever. `CAT-UI-017`'s not-found navigation is deliberately exempt from the gate. Seven tests in `CategoryEditViewModelLoadGateTest.kt`; `FakeTrackrRepository` gained opt-in read gates so the pre-load window is observable at all. The screen renders the gate with `enabled` threaded per widget rather than a blanket input-blocking wrapper — the wrapper left every field reachable via TalkBack, which activates through the semantics tree rather than pointer events. See `docs/llds/category-management.md` § Decisions and Open Question 11 (a category deleted between the two reads leaves a live editor for a deleted row — accepted, same background-sync reachability gate as Open Questions 9 and 10).

7. **CAT-UI-019 added 2026-08-15, closing a second pre-load defect.** CAT-UI-018 gated the form's fields and Save but not the delete action, and the counts the delete decision consults (`ownEventCount`/`subCategoryCount`) were `stateIn(..., Eagerly, 0)` and deliberately outside the gate. Since 0/0 is exactly CAT-UI-004's silent-delete condition, a trash tap landing before those queries emitted deleted a populated category with no confirmation. The fix removes the state rather than gating it: nothing observed those flows except the delete decision itself — the dialog renders from `DeleteConfirmation`'s own fields — so `requestDelete()` now reads both counts at request time, matching what `CategoryListViewModel` already did. Two eager Room subscriptions per edit-screen open went away with them. A shared `performDelete(id)` now carries the delete-then-cancel-alarm tail for both paths. Three tests in `CategoryEditViewModelLoadGateTest.kt`; `FakeTrackrRepository` gained a `countReadGate`.

**Audit-method lesson:** the false negative on CAT-UI-015/016 came from grepping Kotlin source for a literal UI string instead of accounting for string-resource indirection. Future audits of this segment (and others using `stringResource(R.string....)`) should grep `strings.xml` for the resolved text, or grep for the resource name itself, not the displayed string.

8. **Two shape fixes in `CategoryEditViewModel`, no behavior change (2026-08-23).** Each of the eight reminder fields was spelled out five times — a `ReminderUIState` property, a `ReminderSection` parameter, an `onXChange` parameter, a wiring lambda at the call site, and a `copy()` setter — so adding a field meant editing five places and a mistyped `copy()` target compiled silently. One `setReminderUIState` plus `onStateChange` collapses all five to one. `setReminderOccurrencesPerDay` was the only setter carrying logic, the REM-UI-006 digit filter, so the single setter applies it to the whole update: only an edit to `occurrencesPerDay` can fail it, since a rejected one never enters the state the next `copy()` is built from. Separately, CAT-UI-018's four load flags were seeded with correlated expressions that re-encoded init's `when` dispatch a second time; all four now seed `false` and each branch opens by naming the reads it does not issue. No spec changed — the LLD already describes `isLoaded` as the conjunction of the flags relevant to the current mode, which holds under either shape. Backlog #21/#22.

## Work Required

### Must Fix (before MVP / Play Store testing)
_None._

### Should Fix
_None._

### Nice to Have
1. **CAT-UI-011a** — could add "≥1 to save" enforcement on the Exercise category edit screen's default sets/reps fields (mirroring the `SaveResult.ValidationError` pattern used for `name`/`emoji`), but explicitly deprioritized by the user; not currently planned.
