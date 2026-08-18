# Arrow: reminders

Per-category logging reminders: FIXED/RANDOM scheduling, `AlarmManager` integration, notification delivery, and the Category Edit screen's Reminder section.

## Status

**PARTIAL** — audited 2026-08-12 (two passes same day: a reference/citation audit, then a deep full-text pass reading all 49 specs against actual code); test-citation backfill 2026-08-14. 49 of 49 specs implemented and annotated in code. 18 specs have no test-file `@spec` citation, down from 22; what remains is the structural gap (no instrumented Compose UI tests, notification tests, or real-`AlarmManager` tests exist anywhere in this project yet — not unique to reminders). The deep pass found and fixed a real design gap: `AndroidReminderNotifier`'s notification `PendingIntent`/id used `categoryId.hashCode()`, the exact collision-risk pattern this LLD's own "One alarm per category" decision rejected for alarms — now uses the same `data`-`Uri` identity technique as alarms (see finding 6). Four spec-wording imprecisions (REM-DATA-003, REM-DATA-004, REM-PERM-004, REM-NOTIF-005) were reworded to match actual behavior; none were behavioral bugs.

## References

### HLD
- docs/high-level-design.md (System Design, "Silence over spam" / "Public surfaces default to discreet" guidelines, `AlarmManager.setExactAndAllowWhileIdle()` decision)

### LLD
- docs/llds/reminders.md

### EARS
- docs/specs/reminders.md (49 specs: REM-DATA-* [8], REM-UI-* [11], REM-SCHED-* [20], REM-NOTIF-* [6], REM-PERM-* [4])

### Tests
- app/src/test/java/net/clahey/trackr/domain/ReminderSchedulingTest.kt
- app/src/test/java/net/clahey/trackr/reminders/ReminderSchedulerTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelReminderTest.kt
- app/src/test/java/net/clahey/trackr/data/local/MappersTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelTest.kt
- app/src/androidTest/java/net/clahey/trackr/data/local/MigrationTest.kt

### Code
- app/src/main/java/net/clahey/trackr/domain/Reminder.kt
- app/src/main/java/net/clahey/trackr/domain/ReminderScheduling.kt
- app/src/main/java/net/clahey/trackr/reminders/ReminderScheduler.kt
- app/src/main/java/net/clahey/trackr/reminders/ReminderReceiver.kt
- app/src/main/java/net/clahey/trackr/reminders/ReminderRearmReceiver.kt
- app/src/main/java/net/clahey/trackr/data/AlarmScheduler.kt
- app/src/main/java/net/clahey/trackr/data/local/AndroidAlarmScheduler.kt
- app/src/main/java/net/clahey/trackr/data/ReminderNotifier.kt
- app/src/main/java/net/clahey/trackr/data/local/AndroidReminderNotifier.kt
- app/src/main/java/net/clahey/trackr/data/local/ReminderEntity.kt
- app/src/main/java/net/clahey/trackr/data/local/ReminderDao.kt
- app/src/main/java/net/clahey/trackr/data/local/Mappers.kt
- app/src/main/java/net/clahey/trackr/data/local/Migrations.kt
- app/src/main/java/net/clahey/trackr/data/TrackrRepository.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalTrackrRepository.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditScreen.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListScreen.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListViewModel.kt

## Architecture

**Purpose:** Per-category opt-in reminders that nudge the user to log, at either fixed clock times or randomly within a window, without needing a backend — pure on-device `AlarmManager` scheduling.

**Key Components:**
1. `Reminder` (domain) / `ReminderEntity` (Room) — one row per category, flat shape holding both FIXED- and RANDOM-mode fields together (§ local-storage's "Reminder mode representation" decision)
2. `ReminderScheduling.kt` — pure functions computing fire times and validity, independent of `AlarmManager`/DB
3. `ReminderScheduler` — owns all `AlarmManager` interaction (arm/cancel/enable/disable/rearm/reconcile), the only component that touches the OS scheduler
4. `ReminderReceiver` / `ReminderRearmReceiver` — `BroadcastReceiver`s for alarm fires and boot/clock-change re-arms
5. `ReminderUIState` (in `CategoryEditViewModel`) — the Category Edit screen's Reminder section state, one object owning its own validation

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| Data model | REM-DATA-* (8) | all | 0 | 0 |
| Category Edit UI | REM-UI-* (11) | all | 0 | 0 |
| Scheduling engine | REM-SCHED-* (20) | all | 0 | 0 |
| Notifications | REM-NOTIF-* (6) | all | 0 | 0 |
| Permissions | REM-PERM-* (5) | all | 0 | 0 |

**Summary:** 50 of 50 active specs implemented; 0 active behavioral gaps. 18 specs have no test-file `@spec` citation, down from 22 (finding 2) — REM-DATA-003/004/005 and REM-SCHED-002 were closed 2026-08-14. All 49 specs text-verified against actual code as of the 2026-08-12 deep pass (finding 6).

## Key Findings

1. **Full `@spec` annotation coverage in production code.** Every one of the 49 REM-* specs has at least one `@spec` citation somewhere in `app/src/main` — no traceability gap on the code side, unlike `local-storage`'s 12 unannotated specs.
2. **22 specs have no test-file `@spec` citation**, but this isn't one uniform gap — it splits into three distinct causes:
   - **No instrumented test infra exists yet for the relevant surface, project-wide** (not reminders-specific): `REM-UI-001` through `REM-UI-008` and `REM-PERM-001`/`REM-PERM-002` (Category Edit screen's Reminder section — no screen-level Compose UI test exists for `CategoryEditScreen` at all, matching the rest of the app); `REM-NOTIF-001` through `REM-NOTIF-006` (notification channel/content — no `NotificationManager`-level test exists anywhere in the project); `REM-SCHED-009`/`REM-SCHED-010` (the shared `PendingIntent` helper and the exact-vs-inexact `AlarmManager` API selection — both live in `AndroidAlarmScheduler.kt`, the real implementation, which `FakeAlarmScheduler` doesn't replicate closely enough to exercise this logic, and no instrumented test of it exists — same shape of gap `MigrationTest.kt` just closed for Room, not yet done here).
   - **Testable the same way `MigrationTest.kt` now demonstrates; written 2026-08-14, then removed at the user's request** — `REM-DATA-001` (at-most-one-`Reminder`-per-category PK + `CASCADE DELETE` FK). Real SQLite constraint behavior `FakeTrackrRepository` can't enforce, so it needs a real database; a `ReminderConstraintTest.kt` covering both constraints passed on the emulator and was then dropped as not worth carrying for now. Still an open Should Fix, with the shape of the fix known.
   - **~~Annotation-only gap~~ — closed 2026-08-14**: `REM-DATA-005` and `REM-SCHED-002` turned out to be genuinely exercised already and were cited on the tests that do it — `enableReminder computes and arms on first enable` pins REM-SCHED-002's `after` = now (the armed instant is the first fixed time *after* `now`, so it would differ under any other `after`), and it plus `disableReminder clears nextFireAt...` cover REM-DATA-005's set-on-arm and null-on-disable halves. `REM-DATA-003`/`REM-DATA-004` were **not** actually covered — the prior pass's guess that `MappersTest.kt` came close was wrong, since those tests exercise the decode-fallback path, not the UI-state-seeding path the specs describe — so a dedicated test (`a category with no reminder row seeds the UI state defaults`) was written rather than mislabelling an existing one.
3. **`REM-DATA-003`/`REM-DATA-004` shared the same wording gap; both now reworded (fixed 2026-08-12).** Both said a field "shall default to X when a `Reminder` is first created," but `Reminder` (the domain data class) has no default parameter values at all — the defaults (`showCategoryInNotification = false`, `daysActive` = all seven days) live on `ReminderUIState`'s constructor (`CategoryEditViewModel.kt`), applied at the UI-state-seeding layer. Both specs, and the LLD's matching Decisions-table row for `windowStart`/`windowEnd`/`occurrencesPerDay`, were reworded to attribute the defaults correctly. No behavior changed.
4. **Migration test coverage added this pass** (`MigrationTest.kt`) closes what would otherwise be a much larger gap — `REM-DATA-001`/`REM-DATA-002` and `LS-BE-070` now have real, device-verified coverage for the reminders table's schema evolution via `MIGRATION_3_5`. (`MIGRATION_3_4`/`MIGRATION_4_5`, the transitional two-step path, were retired 2026-08-13 once the one device that had reached version 4 was confirmed upgraded to version 5 — `MIGRATION_3_5` is now the sole path.) `MIGRATION_1_2` (unrelated to reminders) remains untestable — see `docs/llds/local-storage.md` § Decisions.

6. **Deep full-text audit (2026-08-12)** — read all 49 REM-* specs against actual code (not just citation existence), re-verified the LLD's description of five recently-changed areas (`ReminderUIState` consolidation, `rearmAll`'s skip-a-day fix, `reconcileOnStartup`'s double-arm fix, `bufferMinutes`/`wasExactAvailable`, NOT NULL window fields) — all confirmed in sync, no drift. Found and fixed: `REM-PERM-004`'s "evaluated... on every composition" wording didn't match the actual memoized (`remember(...)`, `ON_RESUME`-triggered) implementation — reworded, no behavioral bug (self-heals on the only realistic trigger). `REM-NOTIF-005` attributed `Routes.timeline(...)` construction to the reminders-segment `PendingIntent`, when that construction actually happens downstream in app-shell's `MainActivity` — reworded to describe the actual extra passed, with a pointer to app-shell's `APP-NAV-005`/`006`. `reminders.md`'s `ReminderEntity.mode` field-notes table documented stale lowercase casing (`"fixed"`/`"random"`); actual writes are uppercase `.name`, lowercase is decode-only legacy fallback — corrected, same fix applied in `local-storage.md`'s copy of the table. Most significantly: `AndroidReminderNotifier.postReminderNotification()`'s `PendingIntent`/notification-id derivation from `reminder.categoryId.hashCode()` directly contradicted this LLD's own "One alarm per category" reasoning (which explicitly rejected a hash-derived request code for alarms due to 32-bit collision risk) — fixed to use the same `data`-`Uri` identity technique as the alarm `PendingIntent` (`trackr://reminder/$categoryId`, constant request code); the `NotificationManager.notify` id itself has no `Uri`-based equivalent in the Android API and still uses `hashCode()`, now documented as an accepted residual risk in a new Decisions row rather than an unexplained inconsistency.

7. **REM-SCHED-020 suppression never fired (fixed 2026-08-15).** The lookback window was sized by walking backward from the *delivery* instant rather than the occurrence's scheduled instant. Delivery is always at or after the scheduled time, and the backward walk qualifies candidates strictly before its pivot, so the walk returned the occurrence being delivered and collapsed the lookback to the delivery jitter — suppression never triggered in production. The whole existing suite passed because every test pinned `firedAt` to an exact scheduled time, the single value at which the strict comparison still skips the current occurrence. `shouldSuppressFixedNotification` now takes `scheduledAt` and `firedAt` separately, pivots on the former, and bounds the window at the latter. The upper bound closed a second defect found by the new tests: with no bound, a future-dated event (timestamps are user-editable) suppressed every reminder it postdated. Spec and LLD were reworded to distinguish the two instants, with a Decisions row covering the delayed-delivery case; five tests added.

8. **`enableReminder` left reminders unarmed after the OS dropped their alarms (fixed 2026-08-16).** A valid stored `nextFireAt` was treated as evidence that an alarm was still pending for it, so the method returned before arming. Force-stop, app update, and OEM task-kill all clear pending alarms while leaving the row intact, and `reconcileOnStartup` doesn't heal that case — it only re-arms a `nextFireAt` that is null or stale, so a still-future one stayed silently unarmed until a reboot or timezone change ran `rearmAll`. RANDOM-only, since FIXED never takes the early return. Two existing tests asserted `armCalls.isEmpty()` and so pinned the bug in place; both were rewritten to assert the arm happens with the unchanged instant. REM-SCHED-013 had to be reworded first — as written it prohibited the fix — and REM-SCHED-015 no longer calls the valid-`nextFireAt` path "a no-op."

9. **Suppression ignored SubCategory events and scanned the whole event table (fixed 2026-08-16).** `onAlarmFired` read `getEventsByCategory`, so a reminder on a MetaCategory whose logging all happens in its SubCategories had no events of its own and could never suppress. The same call also loaded and fully decoded every event row of the category — `EventValue` JSON and image-path list included — on a Doze wakeup inside the receiver's `goAsync()` budget, to compute a single `MAX`, and discarded all of it for RANDOM reminders that cannot suppress at all. Both are now one `MAX` aggregate spanning the category and its SubCategories (LS-BE-014, a cross-segment addition to `local-storage`), skipped outright for RANDOM. REM-SCHED-020 gained the hierarchy clause.

10. **Permission prompts read state that never refreshed (fixed 2026-08-18).** The Reminder section requested `POST_NOTIFICATIONS` twice for one toggle — once from the Switch's `onCheckedChange`, once from the `LaunchedEffect` in the block that toggle makes visible — where REM-PERM-001's two triggers are both covered by the latter. Separately, both the section's exact-alarm prompt and the list screen's REM-PERM-004 banner sampled permission state during composition with no way to learn it had changed: the prompt had no re-check at all, and the banner's `ON_RESUME` trigger does not fire in multi-window mode, where every visible activity stays `RESUMED` while the user grants the permission in an adjacent Settings pane. Both now read through `rememberExactAlarmAvailable()` / `rememberNotificationsEnabled()` (`ui/components/PermissionState.kt`), the first observing `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` — the only signal that arrives regardless of focus, and available on exactly the API levels where the prompt can appear. The notification half remains best-effort on window focus, since no equivalent broadcast exists. New spec REM-PERM-005 holds the mechanism so REM-PERM-002 and REM-PERM-004 cite it rather than each restating it. No tests: screen-level Compose UI testing has no infrastructure anywhere in this project (finding 2); verified manually, split-screen included.

    This also narrows backlog #18. Its four hand-rolled `canScheduleExactAlarms()` sites split into two that *display* state — now behind the helpers — and two that read it at the moment of an action (`doSave`'s check, per REM-PERM-003's "at that moment", and the banner's tap handler choosing a settings screen), which are correct as direct calls. Only those two remain for #18 to re-point through `AlarmScheduler.canScheduleExact()`.

## Work Required

### Must Fix
_None._

### Should Fix
1. Add a Room instrumented test for `REM-DATA-001` (CASCADE DELETE + PK uniqueness on `reminders`), mirroring `MigrationTest.kt`'s approach. Written and passing on 2026-08-14, then removed at the user's request — deferred, not abandoned.

### Nice to Have
1. Screen-level Compose UI test for `CategoryEditScreen`'s Reminder section (`REM-UI-*`, `REM-PERM-001`/`002`) — no precedent yet anywhere in the project; would be a first, not a small addition.
2. Notification-content test for `AndroidReminderNotifier` (`REM-NOTIF-*`) — same "no precedent yet" caveat.
3. Instrumented test for `AndroidAlarmScheduler`'s `PendingIntent` construction and exact/inexact API selection (`REM-SCHED-009`/`010`).
