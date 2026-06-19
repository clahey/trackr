# Arrow: event-logging

Timeline screen, quick-log sheet, event edit screen, value-type-specific input widgets, image capture.

## Status

**PARTIAL** — last audited 2026-06-17, full reconciliation pass and timestamp-field fix completed 2026-06-18. 96 of 99 specs now confirmed implemented. 3 genuine gaps remain, none MVP-blocking: EL-UI-056 (error kind missing from display), EL-UI-057b (no field-level error highlighting), EL-UI-077 (post-save scroll to new event).

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
- app/src/test/java/net/clahey/trackr/ui/components/TimestampFieldTest.kt
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
- app/src/main/java/net/clahey/trackr/ui/components/TimestampField.kt
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
| Quick-Log Sheet | EL-UI-030 to 077 | all but one | 0 | 1 (EL-UI-077) |
| Value Input by ValueType | EL-UI-050 to 068c | all but one | 0 | 1 (EL-UI-056) |
| Event Edit Screen / Validation | EL-UI-040 to 057b | all but one | 0 | 1 (EL-UI-057b) |
| Navigation | EL-NAV-* | all | 0 | 0 |
| Process (image cleanup) | EL-PROC-* | all | 0 | 0 |

**Summary:** 96 of 99 active specs confirmed implemented; 3 genuine active gaps; 0 deferred. Fully reconciled — no more unverified `[ ]` markers in this segment.

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
4. **Timestamp editing fixed (2026-06-18).** Discovered while scoping the EL-UI-031/032 fix: the event edit screen's timestamp field was *also* non-functional — hardcoded `readOnly = true` with a no-op `onValueChange`, despite EL-UI-040 and EL-UI-043 both being marked `[x]` and asserting it was editable. Both were false positives (the inverse of the stale-`[ ]` pattern found everywhere else this pass) and were flipped back to `[ ]` until the real fix landed. Built one shared `TimestampField` composable (`ui/components/TimestampField.kt`) used by both the quick-log sheet and the event edit screen:
   - Tapping the date opens a `DatePickerDialog` that updates only the date; tapping the time opens a `TimePicker` dialog (defaulting to the current time) that updates only the time. Dismissing either leaves the timestamp unchanged. (Revised 2026-06-19, after using the real picker: an earlier design chained date-confirm into a time picker defaulting to midnight, with a brief midnight-vs-current-time refinement in between — removed entirely per user feedback that independent date/time editing matched expectations better.)
   - Each field also got two usability fixes from the same feedback: a trailing dropdown-chevron icon (`ExposedDropdownMenuDefaults.TrailingIcon`) so the fields visually read as tap-to-pick selectors, and a full-field click target (a transparent click-catcher `Box` layered on top of the `OutlinedTextField`) — without it, `OutlinedTextField`'s own pointer-input handling swallowed clicks anywhere except the label text.
   - Tapping the time opens just the time dialog, defaulting to the **current** time; confirm updates only the time, keeping the existing date.
   - Pure date/time math (`utcMillisToLocalDate`, `localDateToUtcMillis`, `combineDateAndTime`) extracted as testable top-level functions, covering the M3 `DatePicker` UTC-boundary conversion specifically — unit tested in `TimestampFieldTest.kt`. The composable itself is untested (project preference: no new Compose UI tests until a dedicated batch PR).
   - On the event edit screen, the field is disabled (`enabled = false`) on the pager's prev/next preview pages, matching the existing read-only treatment there.
   - EL-UI-031, EL-UI-032, EL-UI-040, EL-UI-043 all flipped to `[x]`.
5. **3 confirmed genuine gaps remain**, none MVP-blocking:
   - **EL-UI-077**: `LaunchedEffect(saveResult)` dismisses the quick-log sheet on save but never scrolls the timeline list to the new event.
   - **EL-UI-056**: error/unknown value display shows the raw value but not the error *kind*. `formatValue`'s `EventValue.ErrorValue` branch is `"[Error: ${value.raw}]"` — `value.kind` (e.g. `UNPARSABLE`, `OUT_OF_RANGE`) is never referenced.
   - **EL-UI-057b**: no input field ever gets `isError = true`. Validation failures (`SaveResult.ValidationError`) only render a text message below the field in both the quick-log sheet and event edit screen — no highlighted-field treatment, and so also no possibility of the two screens being inconsistent (they're identically incomplete).
6. No reverse orphans — every `@spec EL-*` annotation in code points to a real spec ID.

## Work Required

### Must Fix (before MVP / Play Store testing)
_None — the timestamp gap (the only one assessed as a real workflow blocker) is fixed._

### Should Fix
1. **Scroll the timeline to the newly saved event after a quick-log save** (EL-UI-077) — likely a `LazyListState.animateScrollToItem` call alongside the existing sheet-dismiss logic in `HomeScreen.kt`.
2. **EL-UI-057b** — highlight the failing field (`isError = true`) on validation failure, consistently between the quick-log sheet and event edit screen.
3. **EL-UI-056** — include the error kind in the read-only display text for `ErrorValue`/Unknown events, not just the raw value.

### Nice to Have
_None noted this pass._
