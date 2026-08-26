# Arrow: app-shell

App-level scaffolding: DI modules, application/activity entry points, and top-level navigation host.

## Status

**AUDITED** — last audited 2026-07-27. Changed 2026-08-24 by the startup-trigger split (backlog #20), which added APP-PROC-003 and APP-DI-005, rewrote APP-PROC-001/002, and gave the segment its first test file. All 26 specs implemented; remaining findings are traceability gaps.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/app-shell.md

### EARS
- docs/specs/app-shell.md (26 specs: APP-DI-*, APP-ID-*, APP-NAV-*, APP-PROC-*, APP-UI-*)

### Tests
- app/src/test/java/net/clahey/trackr/UiStartupWorkTest.kt (APP-PROC-001, APP-PROC-003)

### Code
- app/src/main/java/net/clahey/trackr/di/ClockModule.kt
- app/src/main/java/net/clahey/trackr/di/CoroutineModule.kt
- app/src/main/java/net/clahey/trackr/di/DatabaseModule.kt
- app/src/main/java/net/clahey/trackr/di/DataStoreModule.kt
- app/src/main/java/net/clahey/trackr/di/RepositoryModule.kt
- app/src/main/java/net/clahey/trackr/MainActivity.kt
- app/src/main/java/net/clahey/trackr/TrackrApplication.kt
- app/src/main/java/net/clahey/trackr/UiStartupWork.kt
- app/src/main/java/net/clahey/trackr/ui/navigation/AppNavHost.kt
- app/src/main/java/net/clahey/trackr/ui/home/EventEditViewModel.kt

## Architecture

**Purpose:** App entry point, Hilt DI wiring, and the top-level `NavHost` connecting the category and event-logging screens.

**Key Components:**
1. Hilt modules (`DatabaseModule`, `DataStoreModule`, `RepositoryModule`, `CoroutineModule`, `ClockModule`)
2. `TrackrApplication` — process entry, kicks off `reminderScheduler.reconcileOnStartup()` on every process start
3. `UiStartupWork` — kicks off `repository.onStartup()` once per process, triggered by `MainActivity` (LS-BE-041)
4. `AppNavHost` — navigation graph

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| DI | APP-DI-* | all | 0 | 0 |
| Identity | APP-ID-001 to 003 | all | 0 | 0 |
| Navigation/Process/UI | APP-NAV-*, APP-PROC-*, APP-UI-* | all | 0 | 0 |

**Summary:** 20 of 20 active specs implemented; 0 deferred, 0 active gaps.

## Key Findings

1. **APP-ID-001, APP-ID-002, APP-ID-003 are implemented with no `@spec` annotation** anywhere in the codebase — traceability gap only, not a functional one.
2. Only one test file cites an `APP-*` spec ID (`UiStartupWorkTest.kt`, added 2026-08-24). Navigation, DI, and identity remain untested; this is still the segment most likely to regress silently.
3. **The two startup jobs now have two different triggers (2026-08-24).** `TrackrApplication.onCreate` previously launched both the orphan image scan and the reminder reconcile, so a process Android created solely to deliver an alarm broadcast ran a whole-event-table read and an image-directory listing before the receiver got its few seconds of budget. The scan moved to `MainActivity.onCreate` (after `setContent`) behind `UiStartupWork.runOnce()`, an `@Singleton` guarding an `AtomicBoolean`; the reconcile stayed on process start, where an alarm wake is a useful moment to notice other reminders were dropped. The move is safe because only UI activity produces orphans — the uncollected set cannot grow during broadcast-only wakes — and a notification tap opens `MainActivity`, so even a notifications-only user collects on every tap. `TrackrApplication`'s hand-rolled `CoroutineScope` became a Hilt-provided `@ApplicationScope` singleton (APP-DI-005), which is what lets the scan survive an `Activity` destroyed mid-run. Backlog #20.

4. **`ClockModule` was documented nowhere (fixed 2026-08-24).** It provides a singleton `Clock.systemDefaultZone()` and had no LLD row, no spec, and no `@spec` annotation — an undocumented Hilt binding an agent would plausibly delete as unused or duplicate. It is a testability seam: `QuickLogViewModel` reads "now" through it in three places, and `QuickLogViewModelTest` substitutes `Clock.fixed(...)` to assert on exact instants instead of racing the wall clock. Now APP-DI-006, a module-table row here, and a sentence in `docs/llds/event-logging.md § Quick-Log Sheet`, which owns the reason. It slipped through because the binding is this segment's by location but serves event-logging's ViewModel, so neither LLD had claimed it — the same ownership split `RemindersModule` has, except that one got documented in the reminders LLD. Worth noting the codebase has two seams for one problem: `ReminderScheduler` takes `now`/`firedAt` as defaulted parameters instead. Both stand; nothing forces a choice.

## Work Required

### Must Fix
_None — fully implemented._

### Should Fix
1. Add `@spec APP-ID-001, APP-ID-002, APP-ID-003` annotations at their implementation site.
2. Extend test coverage beyond `UiStartupWorkTest` — navigation (APP-NAV-*), DI wiring, and the About screen have none.

### Nice to Have
_None noted this pass._
