# Review backlog

Numbered, cross-segment work items from the code review of the reminders branch
(PR #4, `reminders` → `after-rename`).

Numbers are stable and are never reused — an item that is dropped keeps its
number and is marked so, so that references to "#7" mean the same thing in a
later session. Check an item off when it lands, and record the outcome where it
belongs: the spec text, the owning LLD, and that segment's arrow doc § Work
Required. Delete this file once everything is checked.

**Verified** means a reviewer constructed the failure from the code.
**Unverified** items still need that step before they are fixed — the claim may
not survive contact with the source.

## Items

- [x] **2** — Delete path is outside the load gate — *category-management*, verified
- [x] **3** — FIXED-mode notification suppression never suppresses — *reminders*, verified
- [x] **4** — `enableReminder` returns without arming — *reminders*, verified
- [x] **5** — Suppression ignores child-category events — *reminders*, verified
- [ ] **6** — Quick-log deep link re-fires on back-stack restore — *app-shell*, verified
- [ ] **7** — Permission banner can go stale — *category-management*, verified
- [ ] **8** — Occurrences-per-day field rejects most input — *reminders*, unverified
- [x] **9** — Exact-alarm prompt never re-checks — *reminders*, verified
- [x] **10** — Duplicate notification-permission request — *reminders*, verified
- [ ] **11** — `ReminderMode.valueOf` throws on an unrecognized mode — *local-storage*, verified
- [ ] **12** — Empty `times` on a FIXED reminder throws — *reminders*, verified
- [ ] **13** — Schema `4.json` describes an unreachable version — *local-storage*, verified
- [ ] **14** — Fake repository does not cascade reminder deletion — *local-storage*, verified
- [ ] **15** — `@spec` range shorthand is not greppable per ID — *cross-cutting*, verified
- [ ] **16** — `RemindersModule` cites a spec it does not implement — *app-shell*, verified
- [ ] **17** — Four copies of the time-picker dialog — *category-management*, verified
- [ ] **18** — Exact-alarm check hand-rolled instead of using the port — *reminders*, verified
- [x] **19** — `onAlarmFired` scans the whole event table for a MAX — *reminders*, verified
- [ ] **20** — App-startup work runs on every alarm-triggered process wake — *app-shell*, verified
- [ ] **21** — Collapse the eight reminder setters into one `setReminderUIState` — *category-management*, PR comment
- [ ] **22** — Localize the load flags to their `when` branches — *category-management*, PR comment
- [x] **23** — `HomeScreen` empty check should be a positive test — *event-logging*, PR comment
- [x] **24** — LLD should say the value is preserved — *local-storage*, PR comment
- [x] **25** — Conversation cruft belongs in the decision table — *local-storage*, PR comment
- [x] **26** — "Single row read" comment is confusing for a get-all — *local-storage*, PR comment
- [x] **27** — An Open Question deleted without justification — *local-storage*, PR comment
- [x] **28** — Redundant hardcoded tint on the notification icon — *reminders*, verified

## Detail

### 2 — Delete path is outside the CAT-UI-018 load gate
`CategoryEditViewModel.kt:483`, `CategoryEditScreen.kt:235`

`requestDelete()` has no `isLoaded` check and the trash `IconButton` gets no
`enabled`. The counts it consults, `ownEventCount`/`subCategoryCount`
(`CategoryEditViewModel.kt:275-283`), are `stateIn(..., Eagerly, 0)` and are not
part of `isLoaded`, so they read 0 until their COUNT queries emit. With both at
0, `deletionConfirmationIfNeeded` (`CategoryListViewModel.kt:41`) returns null
and the delete proceeds with no dialog, cascade-deleting the category's events
and image files. Subcategories are promoted rather than deleted
(`LocalTrackrRepository.kt:130-137`), but that reparenting also happens without
the confirmation the user should have seen.

Accepted approach: a second readiness flag covering the counts, consulted by
`requestDelete` — blocking until the counts land rather than disabling the
button, so the delay reads as latency rather than as a broken control. The form
itself has no reason to wait on COUNT queries, so this is a sibling of
`isLoaded`, not a fifth flag inside it. Spec-affecting: CAT-UI-018 needs a
clause or a sibling spec.

### 3 — FIXED-mode notification suppression never suppresses
`ReminderScheduling.kt:57`

The lookback window is derived from the gap to the previous scheduled fire,
found by walking backward from `firedAt` with a strict `isBefore`
(`ReminderScheduling.kt:35`). Production passes `Instant.now()`
(`ReminderReceiver.kt:23` → the default at `ReminderScheduler.kt:56`), which is
always strictly after the scheduled instant, so the walk returns the trigger
that just fired and the window collapses to delivery jitter. With times
`[08:00, 20:00]` delivered at `20:00:00.400` the lookback is 40 ms instead of
~60 min. Under the inexact `setAndAllowWhileIdle` path
(`AndroidAlarmScheduler.kt:29`) the delivery delay can be minutes.

Every existing test passes `firedAt` exactly equal to a scheduled time
(`ReminderSchedulingTest.kt:291-361`, `ReminderSchedulerTest.kt:198-236`) — the
single value at which the strict comparison skips today's entry, which is why
the suite is green. REM-SCHED-020 is effectively unimplemented.

### 4 — `enableReminder` returns without arming
`ReminderScheduler.kt:37`

The early return skips `alarmScheduler.arm()` at line 40, not just the
recompute, assuming a pending OS alarm exists for the stored `nextFireAt`.
Force-stop, app update, and OEM task-killers clear pending alarms while leaving
the row intact. `reconcileOnStartup` does not heal it — line 97 only re-arms a
null or stale `nextFireAt`. RANDOM-only, since `isNextFireAtValid` returns false
for FIXED.

Accepted approach: keep the skip-recompute/skip-save behavior, but always arm on
the way out. Arming is idempotent via `FLAG_UPDATE_CURRENT` and the per-category
data `Uri` (`AndroidAlarmScheduler.kt:40-47`), so the cost is one binder call.
Spec-affecting: REM-SCHED-013 currently reads as prohibiting the call.

### 5 — Suppression ignores child-category events
`ReminderScheduler.kt:59`

Uses `getEventsByCategory` (direct events only).
`getEventsByCategoryIdIncludingChildren` exists at `TrackrRepository.kt:32` and
is used for the same "this category's activity" semantics at
`HomeViewModel.kt:95-96`. REM-UI-001 allows reminders on MetaCategories, so a
MetaCategory whose logging happens entirely in its SubCategories has zero direct
rows and is never suppressed. Spec-affecting: REM-SCHED-020 is silent on
hierarchy.

### 6 — Quick-log deep link re-fires on back-stack restore
`MainActivity.kt:23`, `AppNavHost.kt:130`, `HomeViewModel.kt:151`

The notification's `EXTRA_CATEGORY_ID` is read but never cleared (no
`removeExtra`/`setIntent`), and the cold-start path bakes the id into the
NavHost `startDestination`. `HomeViewModel`'s init reads
`savedStateHandle["quickLogCategoryId"]` and fires from it;
`consumePendingQuickLogTarget()` clears only the in-memory `StateFlow`, never
the SavedStateHandle entry. After process death the timeline entry is restored
with the argument still set, a fresh `HomeViewModel` reads it, and the sheet
opens unprompted with `ActiveFilter.TopLevel` silently reapplied
(`HomeViewModel.kt:163`). Rotation is not the trigger — ViewModels survive it.

Fix: clear the SavedStateHandle entry when the target is consumed.

### 7 — Permission banner can go stale
`CategoryListViewModel.kt:78`

`hasEnabledReminder` hangs a one-shot `getAllEnabledRemindersOnce()` off the
`categories` StateFlow via `mapLatest`, so it only recomputes when `categories`
emits. The operative mechanism is StateFlow conflation, not per-table Room
invalidation: `saveCategoryWithReminder` does touch the categories table, but
enabling a reminder without editing a category field re-emits an *equal*
`List<Category>`, which the StateFlow drops — so `mapLatest` never runs and the
REM-PERM-004 banner stays hidden. It self-heals whenever
`WhileSubscribed(5000)` lapses and re-subscribes, so the failure needs a
list→edit→list round trip faster than 5s. It also re-decodes every enabled
reminder on every unrelated category write.

Fix: a reactive `Flow<Boolean>` EXISTS query on `ReminderDao`, which addresses
the staleness and the waste together. Cross-segment — a new read on
local-storage, like LS-BE-014 was.

### 8 — Occurrences-per-day field rejects most input
`CategoryEditScreen.kt:826` (approximate — unverified)

`onValueChange` drops anything outside 1..12 while `value` comes from state, so
clearing the digit reverts and reachable values may be limited to those typable
by appending. Accepted approach: back the field with a string in the UI model,
matching `exerciseDefaultSets`/`exerciseDefaultReps`, and validate on save
through the existing `SaveResult.ValidationError` path.

Unlike CAT-UI-011a — where bounds enforcement was descoped because a bad value
only stores a bad default — `occurrencesPerDay` feeds the RANDOM sub-window
division, so a non-positive value is an arithmetic hazard downstream. The bound
needs an explicit spec line rather than inheriting the sets/reps precedent.

### 9 — Exact-alarm prompt never re-checks
`CategoryEditScreen.kt:764`

Reads `canScheduleExactAlarms()` inline during composition. Returning from
Settings changes no observed state, so nothing recomposes and the card keeps
asking for a permission the user just granted. `CategoryListScreen` already
solves this with a `LifecycleEventObserver` + `permissionRecheckTrigger` +
`remember(trigger)` (`CategoryListScreen.kt:95-108`) — the fix is to port that
pattern, not to invent one. It also removes a binder IPC per recomposition.

### 10 — Duplicate notification-permission request
`CategoryEditScreen.kt:752`, `:761`

The Switch's `onCheckedChange` calls `requestNotificationPermissionIfNeeded()`,
and the `LaunchedEffect(Unit)` inside the newly-visible `if (reminderOn)` block
calls it again as that block enters composition. The line-752 call is fully
subsumed: toggling on always enters that block. REM-PERM-001 is annotated on
the Switch, so check the spec's wording before deleting the call.

### 11 — `ReminderMode.valueOf` throws on an unrecognized mode
`Mappers.kt:113`

Unguarded `valueOf(mode.uppercase())` throws `IllegalArgumentException`, which
propagates through `getAllEnabledRemindersOnce()` into `reconcileOnStartup` in
an unsupervised startup coroutine — a crash on every launch. No current write
path produces an out-of-range value (`Mappers.kt:127` writes `mode.name`;
lowercase legacy values are handled), so this is a robustness regression from
the previously total decode rather than a reachable defect. The empty-
`daysActive` guard next door suggests the asymmetry is unintentional.

### 12 — Empty `times` on a FIXED reminder throws
`ReminderScheduling.kt:44`

`sortedTimes.first()` has no empty guard, so an enabled FIXED reminder with
empty times throws `NoSuchElementException` on the same unsupervised startup
path as #11. Not writable today: `CategoryEditViewModel.kt:57` rejects the save,
`CategoryEditScreen.kt:895` hides the delete affordance below two entries, and
reopening refills a default.

### 13 — Schema `4.json` describes an unreachable version
`app/schemas/net.clahey.trackr.data.local.TrackrDatabase/4.json`

Version 4 existed only in unmerged commits on this branch, so no distributed
build ever wrote `user_version = 4`. There is no 4→5 migration and no
`fallbackToDestructiveMigration` (`DatabaseModule.kt:24-31`), so the file
implies a reachable state that is not reachable. Either delete it or write the
migration; the current state is a trap for a future reader.

### 14 — Fake repository does not cascade reminder deletion
`FakeTrackrRepository.kt:105`

`deleteCategory` updates `categories` and `events` and never touches the
`reminders` map, while the real `ReminderEntity` drops the row via
`onDelete = ForeignKey.CASCADE`. The test double models the opposite of
production, so an orphan-reminder regression in `LocalTrackrRepository` would
not be caught by the unit suite. Promoted children keep their own reminders —
they are reparented, not deleted.

### 15 — `@spec` range shorthand is not greppable per ID
`CategoryEditViewModel.kt:285`, `CategoryEditScreen.kt:148`, `CategoryEditScreen.kt:358`

`REM-UI-001..011` and `REM-PERM-001..004` are not EARS IDs and match nothing in
`docs/specs/`. `grep REM-UI-006` does not find these sites, so the range form
defeats the traceability the annotation exists for. CLAUDE.md's format is a
comma-separated list of literal IDs.

### 16 — `RemindersModule` cites a spec it does not implement
`RemindersModule.kt:18`

Cites `APP-REM-001`, which owns the fact that `AndroidManifest.xml` declares the
two receivers — already annotated at its real entry point in the manifest. The
module's actual bindings (`AlarmScheduler`, `ReminderNotifier`, `AlarmManager`,
`NotificationManager`) are covered by no spec, so this is code with no correct
intent linkage plus a polluted citation.

### 17 — Four copies of the time-picker dialog
`CategoryEditScreen.kt:917`, `:932`, `:966`, and `ui/components/TimestampField.kt:144`

The same `rememberTimePickerState` + `AlertDialog` body appears three times in
`CategoryEditScreen` and a fourth time in the shared component. Extract one
`TimePickerDialog(initial, onConfirm, onDismiss)` into `ui/components/` and have
all four call it.

### 18 — Exact-alarm check hand-rolled instead of using the port
`CategoryEditScreen.kt:154`, `CategoryListScreen.kt:171`

`Build.VERSION.SDK_INT < S || canScheduleExactAlarms()` is written out in the
UI while `AlarmScheduler.canScheduleExact()` (`AndroidAlarmScheduler.kt:22`) is
the injectable, fakeable form and goes uncalled. None of it is reachable from
ViewModel tests — which is why `save()` grew three defaulted boolean
parameters.

Narrowed by the #9/#10 fix: the two sites that *displayed* this state now read
through `rememberExactAlarmAvailable()`. The two left are point-in-time reads at
a user action — `doSave`'s check, which REM-PERM-003 requires be read "at that
moment", and the banner's tap handler picking a settings screen — so they want
the port, not the composable.

### 19 — `onAlarmFired` scans the whole event table for a MAX
`ReminderScheduler.kt:59`

Loads and fully decodes every event row of the category (EventValue JSON,
image-path list) to compute `maxOfOrNull { it.timestamp }`, on a Doze wakeup
inside a `goAsync()` budget, and discards all of it for RANDOM reminders where
`shouldSuppressFixedNotification` short-circuits on the mode check. Wants a DAO
aggregate (`SELECT MAX(timestamp) ...`) behind the FIXED-mode branch.

### 20 — App-startup work runs on every alarm-triggered process wake
`TrackrApplication.kt:34-36`

The receivers declare no `android:process`, so an alarm delivered to a dead
process runs `TrackrApplication.onCreate` first — `repository.onStartup()`
(event-table read plus image-directory GC) and `reconcileOnStartup()` (DataStore
read, all enabled reminders, an arm per reminder). Before reminders existed this
ran only on user launch.

The write race is narrower than first thought: `reconcileOnStartup` only
recomputes a `nextFireAt` that is null or stale past its 10/30-minute buffer,
and the reminder being delivered normally has a fresh one, so it is left
untouched. The two writers collide only when delivery ran later than the buffer
— reachable on the inexact `setAndAllowWhileIdle` path in Doze. The cost on
every alarm wake is unconditional regardless.

The least severe of the cluster and the only one needing a design decision
rather than a fix.

### 21 — Collapse the eight reminder setters into one `setReminderUIState`
`CategoryEditViewModel.kt:265`, `CategoryEditScreen.kt:359-378`

Each of the eight reminder fields is spelled out five times: as a
`ReminderUIState` property, a `ReminderSection` parameter, an `onXChange`
parameter, a wiring lambda at the call site, and a
`copy(x = value)` setter. Adding a field means editing five places, and a
mistyped `copy()` target compiles silently. One `setReminderUIState` plus
`onStateChange: (ReminderUIState) -> Unit`, with each control calling
`onStateChange(state.copy(field = ...))`, collapses all five to one.

### 22 — Localize the load flags to their `when` branches
`CategoryEditViewModel.kt:123`

The four flags are seeded with correlated expressions (`categoryId != null ||
parentId == null` and friends) that re-encode init's `when` dispatch a second
time, so a reader must cross-check the two to convince themselves the gate ever
opens. Seeding all four false and setting the inapplicable ones true at the top
of each `when` branch keeps that logic in one place.

### 23 — `HomeScreen` empty check should be a positive test
`HomeScreen.kt:194`

Invert to test for content directly rather than early-returning on the empty
case.

### 24 — LLD should say the value is preserved
`docs/llds/local-storage.md:88`

### 25 — Conversation cruft belongs in the decision table
`docs/llds/local-storage.md:193`

The reminders aside reads as residue from the change that introduced it; the
substance belongs in a Decisions row.

### 26 — "Single row read" comment is confusing for a get-all
`docs/llds/local-storage.md:195`

The comment reasons about a single-row read, but the method fetches all rows.
The conclusion (no transaction needed) stands; the reasoning as written does
not.

### 27 — An Open Question deleted without justification
`docs/llds/local-storage.md:247`

Only one question was actually removed — nullable `Long` params in `getEvents`.
The other three were renumbered in place, which is what made the hunk look like
a wholesale deletion. The removal is correct: the question's own fallback
shipped (`LocalTrackrRepository.getEvents` dispatches to four fixed-shape DAO
queries), so it is answered, not open. Nothing recorded that answer, though,
which is why the deletion read as unexplained — the dispatch is now described
in § Repository Contract.

### 28 — Redundant hardcoded tint on the notification icon
`app/src/main/res/drawable/ic_notification_reminder.xml:6`

`android:tint="#FFFFFFFF"` is redundant: the system tints notification small
icons itself, and hardcoding white can fight that treatment. The drawable
itself is legitimate — `setSmallIcon` takes a resource id, so a Compose
`ImageVector` cannot serve here.

## Dropped

### 1 — Receivers skip `super.onReceive`, so Hilt never injects
Retracted. Reminders have been observed firing on a real device, and
`ReminderScheduler.onAlarmFired` is what posts the notification, so injection
demonstrably works. `ReminderRearmReceiver` is structurally identical, so the
same holds for it. Whether the reboot and timezone-change paths work end to end
is a separate, still-untested question.
