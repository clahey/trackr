# Arrow: event-logging

Timeline screen, quick-log sheet, event edit screen, value-type-specific input widgets, image capture.

## Status

**PARTIAL** — last audited 2026-06-17, full reconciliation pass completed same day (git SHA `be05346`). 94 of 99 specs now confirmed implemented (79 pre-reconciliation + EL-UI-015 + 15 reconciled this pass). 5 genuine gaps remain: EL-UI-031, EL-UI-032, EL-UI-056, EL-UI-057b, EL-UI-077.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/event-logging.md

### EARS
- docs/specs/event-logging.md (99 specs: EL-NAV-*, EL-PROC-*, EL-UI-*)

### Tests
- app/src/androidTest/java/net/clahey/trackr/ui/components/ValueInputFieldFocusTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepository.kt
- app/src/test/java/net/clahey/trackr/ui/components/FormatValueTest.kt
- app/src/test/java/net/clahey/trackr/ui/components/ValueUIStateTest.kt
- app/src/test/java/net/clahey/trackr/ui/home/EventEditViewModelTest.kt
- app/src/test/java/net/clahey/trackr/ui/home/HomeViewModelTest.kt
- app/src/test/java/net/clahey/trackr/ui/home/QuickLogViewModelTest.kt

### Code
- app/src/main/java/net/clahey/trackr/ui/home/HomeScreen.kt
- app/src/main/java/net/clahey/trackr/ui/home/HomeViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/home/QuickLogViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/home/EventEditScreen.kt
- app/src/main/java/net/clahey/trackr/ui/home/EventEditViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/components/EventRow.kt
- app/src/main/java/net/clahey/trackr/ui/components/ValueInputField.kt
- app/src/main/java/net/clahey/trackr/ui/components/ValueUIState.kt
- app/src/main/java/net/clahey/trackr/data/local/EventDao.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalTrackrRepository.kt

## Architecture

**Purpose:** The core logging loop — timeline of events with filtering, a two-step quick-log sheet, full event edit screen, and per-`ValueType` input widgets (Number/Text/Boolean/Duration/Scale/Exercise/Error).

**Key Components:**
1. `HomeScreen`/`HomeViewModel` — timeline, filter chips, FAB
2. `QuickLogViewModel` — two-step quick-log sheet
3. `EventEditScreen`/`EventEditViewModel` — full edit, image handling
4. `ValueInputField` — per-type input widget dispatch (`ScaleInput`, `BoolInput`, `NumberInput`, `TextInput`, `DurationInput`, `ExerciseInput`, `ReadOnlyDisplay`, `MismatchedInput`)

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| Timeline Display / Filter / Undo | EL-UI-001 to 023c | all | 0 | 0 |
| Quick-Log Sheet | EL-UI-030 to 077 | most | 0 | 3 (EL-UI-031, EL-UI-032, EL-UI-077) |
| Value Input by ValueType | EL-UI-050 to 068c | all but one | 0 | 1 (EL-UI-056) |
| Event Edit Screen / Validation | EL-UI-040 to 057b | most | 0 | 1 (EL-UI-057b) |
| Navigation | EL-NAV-* | all | 0 | 0 |
| Process (image cleanup) | EL-PROC-* | all | 0 | 0 |

**Summary:** 94 of 99 active specs confirmed implemented; 5 genuine active gaps; 0 deferred. Fully reconciled — no more unverified `[ ]` markers in this segment.

## Key Findings

1. **EL-UI-015 fixed in a prior pass this session** — timeline filter chips (`HomeScreen.kt`) now show the category's `resolvedColor` as a border when unselected and a filled background when active, via a shared `CategoryFilterChip` composable.
2. **Full reconciliation pass completed.** All 20 nominally-active-gap specs were individually checked against code. 15 were confirmed already implemented (stale markers); `@spec` annotations backfilled at each implementation site. 5 are confirmed genuine gaps — see below.
3. **15 stale markers corrected, now `[x]` with annotations added:**
   - `EL-UI-023c` (undo row not tappable for nav) — `UndoPlaceholderRow` (`HomeScreen.kt`) has no `clickable` modifier on the row, only the restore `TextButton` — correct by construction.
   - `EL-UI-051`/`EL-UI-051b` (Boolean two-button input, blocks save until tapped) — `BoolInput` (`ValueInputField.kt`) + `toEventValue`/`validateValueForSave` (`ValueUIState.kt`) confirm both the unpressed-by-default rendering and the save-blocking validation.
   - `EL-UI-052c` (Number field preserves exact text while editing) — `NumberInput` binds the raw string directly with no reformatting; parsing only happens at save time via `toEventValue`.
   - `EL-UI-053` (Text multi-line) — `TextInput` sets `minLines = 2`.
   - `EL-UI-055`/`055c`/`055d` (Duration: 3 fields, empty preserved while editing, empty-vs-zero leading-component display) — `DurationInput` + `toEventValue` (empty → 0 at save) + `durationToUIState` (empty hours/minutes when zero, seconds always shown) all confirmed.
   - `EL-UI-059b` (Sets/Reps preserve text, block save if empty/non-parseable/<1) — same raw-string-plus-save-time-validation pattern as Number/Duration, confirmed in `ExerciseInput` + `toEventValue`.
   - `EL-PROC-003` (crash recovery for orphaned images) — same mechanism as `LS-BE-040`'s `onStartup()` blanket "delete anything unreferenced" sweep; covers the crash case by construction, no extra code needed.
   - `EL-NAV-001`, `EL-NAV-003`, `EL-NAV-004`, `EL-NAV-007`, `EL-NAV-013` — all confirmed wired (FAB → sheet, dismiss-deletes-image, row-tap → edit, back-without-saving deletes new images, Save/Discard/Cancel dialog on both hardware back and the toolbar nav icon).
4. **5 confirmed genuine gaps**, all newly found this pass (previous spot-check had wrongly treated EL-UI-031 as confirmed, based on its `031a`/`031b` sub-specs being done — the parent line bundles a 5th requirement, the timestamp field, which is absent):
   - **EL-UI-031 / EL-UI-032 / EL-UI-077 share one root cause**: the quick-log sheet has *no timestamp UI at all*. `QuickLogViewModel.timestamp` defaults correctly to "now" (satisfying half of EL-UI-032), but there is no tappable field anywhere in `HomeScreen.kt`'s `QuickLogSheet` to view or edit it — so EL-UI-031's "tappable timestamp field" requirement and EL-UI-032 as a whole are unmet. Separately, `LaunchedEffect(saveResult)` dismisses the sheet on save but never scrolls the timeline list to the new event (EL-UI-077).
   - **EL-UI-056**: error/unknown value display shows the raw value but not the error *kind*. `formatValue`'s `EventValue.ErrorValue` branch is `"[Error: ${value.raw}]"` — `value.kind` (e.g. `UNPARSABLE`, `OUT_OF_RANGE`) is never referenced.
   - **EL-UI-057b**: no input field ever gets `isError = true`. Validation failures (`SaveResult.ValidationError`) only render a text message below the field in both the quick-log sheet and event edit screen — no highlighted-field treatment, and so also no possibility of the two screens being inconsistent (they're identically incomplete).
5. No reverse orphans — every `@spec EL-*` annotation in code points to a real spec ID.

## Work Required

### Must Fix (before MVP / Play Store testing)
1. **Add a timestamp field to the quick-log sheet** (EL-UI-031, EL-UI-032) — tappable, opens a date/time picker, defaults to sheet-open time. This is the only gap in the segment that blocks a real user workflow (logging an event for a time other than "now").
2. **Scroll the timeline to the newly saved event after a quick-log save** (EL-UI-077) — likely a `LazyListState.animateScrollToItem` call alongside the existing sheet-dismiss logic in `HomeScreen.kt`.

### Should Fix
1. **EL-UI-057b** — highlight the failing field (`isError = true`) on validation failure, consistently between the quick-log sheet and event edit screen.
2. **EL-UI-056** — include the error kind in the read-only display text for `ErrorValue`/Unknown events, not just the raw value.

### Nice to Have
_None noted this pass._
