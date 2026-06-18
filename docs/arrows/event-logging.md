# Arrow: event-logging

Timeline screen, quick-log sheet, event edit screen, value-type-specific input widgets, image capture.

## Status

**PARTIAL** — last audited 2026-06-17 (git SHA `be05346`). 78 of 99 specs marked implemented; spot-checks of the remaining 21 found every sample (6 of 6) already implemented despite `[ ]` markers. This is the largest segment in the project by raw spec count and by raw "active gap" count, and shows the same stale-checkbox pattern as `category-management`, more pronounced.

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
| Screens | EL-UI-001 to ~032 | most (spot-checked subset confirmed) | 0 | unverified subset |
| Value Input by ValueType | EL-UI-05x | spot-checked subset confirmed (EL-UI-051, 051b) | 0 | unverified subset |
| ViewModels | various | unverified this pass | 0 | unverified |
| Navigation | EL-NAV-* | spot-checked subset confirmed (EL-NAV-001, 004) | 0 | unverified subset |

**Summary:** Raw count is 79 implemented / 20 active gap / 0 deferred (EL-UI-015 fixed this session). Every one of the 6 pre-existing active-gap specs spot-checked turned out to be implemented — treat the raw 20 as an upper bound on real gaps, not a trustworthy backlog, until reconciled.

## Key Findings

1. **EL-UI-015 fixed this session** — timeline filter chips (`HomeScreen.kt`) now show the category's `resolvedColor` as a border when unselected and a filled background when active, via a new shared `CategoryFilterChip` composable used by both MetaCategory and SubCategory chips. This was a genuine gap (verified absent before the fix, not a stale marker) and was also `theme`'s THEME-UI-010 for the chip surface specifically.
2. **6 of 6 spot-checked "active gap" specs (pre-existing, separate from EL-UI-015) are confirmed already implemented:**
   - `EL-UI-051`/`EL-UI-051b` (Boolean Yes/No two-button input, unselected-by-default state) — `ValueInputField.kt`'s `BoolInput` composable implements exactly this, including the unpressed-when-`selected == null` state. No `@spec` annotation present.
   - `EL-NAV-001` (FAB opens quick-log sheet) — `HomeScreen.kt:174`, `FloatingActionButton(onClick = { ...; showSheet = true })`. Carries `@spec EL-UI-013, EL-UI-075` but not `EL-NAV-001` itself.
   - `EL-NAV-004` (tap event row → navigate to edit) — `HomeScreen.kt:282`, `onNavigateToEventEdit(entry.event.id, filterId)`.
   - `EL-UI-031`/`031a`/`031b` (quick-log step 2 fields: value input, photo, notes, timestamp, save) — present in `HomeScreen.kt` with an `@spec EL-UI-031a, EL-UI-031b` annotation already on part of it.
   - `EL-UI-053` (Text multi-line input) — `TextInput` composable exists and is dispatched for `ValueUIState.Text`.
3. **This is the largest unverified surface in the project.** 15 of the remaining 20 nominal active gaps were not individually checked this pass (budget); given the 6/6 hit rate on "actually implemented" for the pre-existing sample, the prior is strongly that most of the remaining 15 are also done, but this is an inference, not a verification — don't treat it as confirmed.
4. No reverse orphans — every `@spec EL-*` annotation in code points to a real spec ID.

## Work Required

### Must Fix (before MVP / Play Store testing)
1. **Run a full reconciliation pass on the remaining ~15 unverified active-gap specs** before treating this segment's spec file as an accurate "what's left" list. Given the 6/6 confirmed-implemented hit rate so far, the real gap count here is likely small, but it hasn't been proven — and this is the core logging loop, the single most important flow for an MVP.

### Should Fix
1. Backfill `@spec` annotations on all confirmed-implemented-but-unannotated specs found in this pass (EL-UI-051, EL-UI-051b, EL-NAV-001, EL-NAV-004) and whatever the full reconciliation turns up.

### Nice to Have
_None noted this pass._
