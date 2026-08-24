# Arrow: event-logging

Timeline screen, quick-log sheet, event edit screen, value-type-specific input widgets, image capture.

## Status

**PARTIAL** — last audited 2026-07-27. 101 of 103 specs confirmed implemented (EL-UI-077 landed this pass, split into EL-UI-077/077a/077b/077c). 2 genuine gaps remain, none MVP-blocking: EL-UI-056 (error kind missing from display), EL-UI-057b (no field-level error highlighting).

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/event-logging.md

### EARS
- docs/specs/event-logging.md (118 specs: EL-NAV-*, EL-PROC-*, EL-UI-*)

### Tests
- app/src/androidTest/java/net/clahey/trackr/ui/components/TimePickerDialogTest.kt
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
- app/src/main/java/net/clahey/trackr/ui/components/TimePickerDialog.kt
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
| Quick-Log Sheet | EL-UI-030 to 077c | all | 0 | 0 |
| Value Input by ValueType | EL-UI-050 to 068c | all but one | 0 | 1 (EL-UI-056) |
| Event Edit Screen / Validation | EL-UI-040 to 057b | all but one | 0 | 1 (EL-UI-057b) |
| Navigation | EL-NAV-* | all | 0 | 0 |
| Process (image cleanup) | EL-PROC-* | all | 0 | 0 |

**Summary:** 116 of 118 active specs confirmed implemented; 2 genuine active gaps; 0 deferred. The count was recorded as 103 by the 2026-07-27 audit and had drifted as specs were added since — corrected 2026-08-21, not a new gap.

## Key Findings

1. **EL-UI-077 implemented this pass (2026-07-27).** Quick-log save now scrolls the timeline to the saved event's row via `HomeViewModel.onEventLogged(eventId, category)` / `scrollTarget` / `consumeScrollTarget()`, wired through a new `LaunchedEffect(scrollTarget, dayGroups)` in `HomeScreen.kt` that computes the flattened `LazyColumn` index (`flattenedIndexOfEvent`). Split into four specs during the LID pass: EL-UI-077 (scroll on filter match), EL-UI-077a (no-op on filter mismatch), EL-UI-077b (filter change discards a pending target), EL-UI-077c (arming clears the sibling `preFilterTopDay` anchor so the two scroll effects can't fire concurrently against the same `dayGroups` update — this was a real cross-spec interaction with EL-UI-017/018 caught in the Phase 4 edge audit). Code review during the same session also caught and fixed a latent `isProgrammaticScroll`-stuck-`true`-on-cancellation bug in both this new effect and the pre-existing `preFilterTopDay` effect (missing `try/finally` around the flag toggle across the suspending `animateScrollToItem` call).

2. **EL-UI-015 fixed in a prior pass this session** — timeline filter chips (`HomeScreen.kt`) now show the category's `resolvedColor` as a border when unselected and a filled background when active, via a shared `CategoryFilterChip` composable.
3. **Full reconciliation pass completed.** All 20 nominally-active-gap specs were individually checked against code. 15 were confirmed already implemented (stale markers); `@spec` annotations backfilled at each implementation site. 5 are confirmed genuine gaps — see below.
4. **15 stale markers corrected, now `[x]` with annotations added:**
   - `EL-UI-023c` (undo row not tappable for nav) — `UndoPlaceholderRow` (`HomeScreen.kt`) has no `clickable` modifier on the row, only the restore `TextButton` — correct by construction.
   - `EL-UI-051`/`EL-UI-051b` (Boolean two-button input, blocks save until tapped) — `BoolInput` (`ValueInputField.kt`) + `toEventValue`/`validateValueForSave` (`ValueUIState.kt`) confirm both the unpressed-by-default rendering and the save-blocking validation.
   - `EL-UI-052c` (Number field preserves exact text while editing) — `NumberInput` binds the raw string directly with no reformatting; parsing only happens at save time via `toEventValue`.
   - `EL-UI-053` (Text multi-line) — `TextInput` sets `minLines = 2`.
   - `EL-UI-055`/`055c`/`055d` (Duration: 3 fields, empty preserved while editing, empty-vs-zero leading-component display) — `DurationInput` + `toEventValue` (empty → 0 at save) + `durationToUIState` (empty hours/minutes when zero, seconds always shown) all confirmed.
   - `EL-UI-059b` (Sets/Reps preserve text, block save if empty/non-parseable/<1) — same raw-string-plus-save-time-validation pattern as Number/Duration, confirmed in `ExerciseInput` + `toEventValue`.
   - `EL-PROC-003` (crash recovery for orphaned images) — same mechanism as `LS-BE-040`'s `onStartup()` blanket "delete anything unreferenced" sweep; covers the crash case by construction, no extra code needed.
   - `EL-NAV-001`, `EL-NAV-003`, `EL-NAV-004`, `EL-NAV-007`, `EL-NAV-013` — all confirmed wired (FAB → sheet, dismiss-deletes-image, row-tap → edit, back-without-saving deletes new images, Save/Discard/Cancel dialog on both hardware back and the toolbar nav icon).
5. **Timestamp editing fixed (2026-06-18).** Discovered while scoping the EL-UI-031/032 fix: the event edit screen's timestamp field was *also* non-functional — hardcoded `readOnly = true` with a no-op `onValueChange`, despite EL-UI-040 and EL-UI-043 both being marked `[x]` and asserting it was editable. Both were false positives (the inverse of the stale-`[ ]` pattern found everywhere else this pass) and were flipped back to `[ ]` until the real fix landed. Built one shared `TimestampField` composable (`ui/components/TimestampField.kt`) used by both the quick-log sheet and the event edit screen:
   - Tapping the date opens a `DatePickerDialog` that updates only the date; tapping the time opens a `TimePicker` dialog (defaulting to the current time) that updates only the time. Dismissing either leaves the timestamp unchanged. (Revised 2026-06-19, after using the real picker: an earlier design chained date-confirm into a time picker defaulting to midnight, with a brief midnight-vs-current-time refinement in between — removed entirely per user feedback that independent date/time editing matched expectations better.)
   - Each field also got two usability fixes from the same feedback: a trailing dropdown-chevron icon (`ExposedDropdownMenuDefaults.TrailingIcon`) so the fields visually read as tap-to-pick selectors, and a full-field click target (a transparent click-catcher `Box` layered on top of the `OutlinedTextField`) — without it, `OutlinedTextField`'s own pointer-input handling swallowed clicks anywhere except the label text.
   - Tapping the time opens just the time dialog, defaulting to the **current** time; confirm updates only the time, keeping the existing date.
   - Pure date/time math (`utcMillisToLocalDate`, `localDateToUtcMillis`, `combineDateAndTime`) extracted as testable top-level functions, covering the M3 `DatePicker` UTC-boundary conversion specifically — unit tested in `TimestampFieldTest.kt`. The composable itself is untested (project preference: no new Compose UI tests until a dedicated batch PR).
   - On the event edit screen, the field is disabled (`enabled = false`) on the pager's prev/next preview pages, matching the existing read-only treatment there.
   - EL-UI-031, EL-UI-032, EL-UI-040, EL-UI-043 all flipped to `[x]`.
6. **2 confirmed genuine gaps remain**, none MVP-blocking:
   - **EL-UI-056**: error/unknown value display shows the raw value but not the error *kind*. `formatValue`'s `EventValue.ErrorValue` branch is `"[Error: ${value.raw}]"` — `value.kind` (e.g. `UNPARSABLE`, `OUT_OF_RANGE`) is never referenced.
   - **EL-UI-057b**: no input field ever gets `isError = true`. Validation failures (`SaveResult.ValidationError`) only render a text message below the field in both the quick-log sheet and event edit screen — no highlighted-field treatment, and so also no possibility of the two screens being inconsistent (they're identically incomplete).
7. No reverse orphans — every `@spec EL-*` annotation in code points to a real spec ID (verified again this pass for the new EL-UI-077/077a/077b/077c annotations).
8. **The quick-log deep-link argument outlived its own consumption (fixed 2026-08-19).** `HomeViewModel` read `quickLogCategoryId` from the `SavedStateHandle` and left it there. The handle is saved and restored along with its back stack entry, so killing the process and reopening the task from Recents handed the same argument to a fresh `HomeViewModel`, which reopened the quick-log sheet unprompted and, for a MetaCategory with SubCategories, reapplied `ActiveFilter.TopLevel` (EL-UI-082) that the user had not asked for. Reproduced on device with `am kill`, and both branches verified fixed the same way — note that `force-stop` cannot reproduce it, since it discards the saved state there is nothing left to restore. Now read with `SavedStateHandle.remove`, which clears it at the read rather than on consumption: EL-UI-083's unresolvable-category path shows a snackbar and opens no sheet, so a dismissal-time clear would have left that case leaking. EL-UI-080 reworded — "consumed exactly once on init" described the bug, since the buggy code did exactly that, once per `HomeViewModel`. 2 tests in `HomeViewModelTest.kt`, each constructing a second ViewModel against the same handle to model the restore.

    `MainActivity`'s notification extra is deliberately *not* cleared. It feeds only the nav graph's `startDestination`, which `NavController` ignores whenever there is a back stack to restore — and `removeExtra` would not survive process death anyway, since the system redelivers its own copy of the intent. So APP-NAV-005/006 are untouched and this stayed inside one segment.

9. **Outstanding reminders listed on the timeline (added 2026-08-21).** Tapping a bundled reminder notification landed in the app with nothing to show for it, and tapping the launcher icon's notification dot never could — the launcher opens the app through its own intent, carrying no deep link, so that half was never fixable app-side. The timeline now lists reminders that have fired and not been dealt with, under the notification's own "Time to log" title (EL-UI-096..099). A row tap resolves the same `QuickLogTarget` a notification tap resolves, filter side effect included, by way of an `openQuickLogFor` extracted from the deep-link path so the two genuinely share one route rather than resembling each other. A swipe dismisses in either direction with threshold haptics and no undo, unlike the event row directly below it — deleting an event destroys something, dismissing a nudge that returns does not. The rows sit above the empty state as well as the event list, since a reminder can be outstanding for a category with no events yet. What "outstanding" means and where the answer comes from belongs to the reminders segment (REM-NOTIF-008); this screen renders it and reports taps. Header and row styling are known-rough and deliberately left for a later pass.

10. **One time-picker dialog instead of four (2026-08-24).** The same `rememberTimePickerState` + dialog body appeared three times in `CategoryEditScreen.kt`'s reminder editor and a fourth time inside `TimestampField`, that one hand-built from `Dialog`/`Surface` rather than an `AlertDialog`. All four now call `TimePickerDialog(initial, onConfirm, onDismiss)` (`ui/components/TimePickerDialog.kt`), designed in this segment's LLD § Time Picker Dialog: M3 ships a `DatePickerDialog` but no `TimePicker` equivalent, which is the component's entire reason for existing, and that sentence was already written here. Closing the dialog stays with the caller, since each one already owns the state deciding whether it shows. The component carries no `@spec` of its own — the four entry points above it already cite REM-UI-004/005 and EL-UI-032/040, and it is a helper below them. `TimestampField`'s dialog picks up `AlertDialog`'s standard padding and elevation in the trade, accepted as the more consistent of the two looks. Three tests in `TimePickerDialogTest.kt`: the M3 clock face turned out to be addressable by content description, so one drives a real selection rather than only confirming the seed — minutes rather than hours, since the hour labels depend on 12-/24-hour mode. Two `@OptIn(ExperimentalMaterial3Api::class)` annotations became dead with the old bodies and were removed. Backlog #17.

## Work Required

### Must Fix (before MVP / Play Store testing)
_None._

### Should Fix
1. **EL-UI-057b** — highlight the failing field (`isError = true`) on validation failure, consistently between the quick-log sheet and event edit screen.
2. **EL-UI-056** — include the error kind in the read-only display text for `ErrorValue`/Unknown events, not just the raw value.

### Nice to Have
_None noted this pass._
