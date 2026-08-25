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
not survive contact with the source. `[D]` marks an item deliberately deferred
past the merge, with the reason in its detail section.

## Items

- [x] **2** — Delete path is outside the load gate — *category-management*, verified
- [x] **3** — FIXED-mode notification suppression never suppresses — *reminders*, verified
- [x] **4** — `enableReminder` returns without arming — *reminders*, verified
- [x] **5** — Suppression ignores child-category events — *reminders*, verified
- [x] **6** — Quick-log deep link re-fires on back-stack restore — *event-logging*, verified on device
- [x] **7** — Permission banner can go stale — *category-management*, verified
- [x] **8** — Occurrences-per-day field rejects most input — *reminders*, verified; fix confirmed on device
- [x] **9** — Exact-alarm prompt never re-checks — *reminders*, verified
- [x] **10** — Duplicate notification-permission request — *reminders*, verified
- [x] **11** — `ReminderMode.valueOf` throws on an unrecognized mode — *local-storage*, verified
- [x] **12** — Empty `times` on a FIXED reminder throws — *reminders*, verified
- [D] **13** — Schema `4.json` describes an unreachable version — *local-storage*, verified; deferred past the merge on purpose
- [x] **14** — Fake repository does not cascade reminder deletion — *local-storage*, verified
- [x] **15** — `@spec` range shorthand is not greppable per ID — *cross-cutting*, verified
- [x] **16** — `RemindersModule` cites a spec it does not implement — *app-shell*, verified
- [x] **17** — Four copies of the time-picker dialog — *event-logging*, verified
- [x] **18** — Exact-alarm check hand-rolled instead of asking `AlarmScheduler` — *reminders*, verified
- [x] **19** — `onAlarmFired` scans the whole event table for a MAX — *reminders*, verified
- [x] **20** — App-startup work runs on every alarm-triggered process wake — *app-shell*, verified
- [x] **21** — Collapse the eight reminder setters into one `setReminderUIState` — *category-management*, PR comment
- [x] **22** — Localize the load flags to their `when` branches — *category-management*, PR comment
- [x] **23** — `HomeScreen` empty check should be a positive test — *event-logging*, PR comment
- [x] **24** — LLD should say the value is preserved — *local-storage*, PR comment
- [x] **25** — Conversation cruft belongs in the decision table — *local-storage*, PR comment
- [x] **26** — "Single row read" comment is confusing for a get-all — *local-storage*, PR comment
- [x] **27** — An Open Question deleted without justification — *local-storage*, PR comment
- [x] **28** — Redundant hardcoded tint on the notification icon — *reminders*, verified
- [x] **29** — Permission prompt sends the user to the less severe problem first — *reminders*, verified on device
- [x] **30** — No prompt or recovery path when notifications are denied — *reminders*, verified on device
- [x] **31** — One message for two different failures — *reminders*, verified on device
- [x] **32** — A blocked notification channel is undetectable — *reminders*, verified
- [x] **33** — Tapping an unexpanded multi-reminder row shows no reminders — *reminders*, verified on device
- [x] **34** — Tapping the yellow-dot icon shows no reminders — *reminders*, not a defect; superseded
- [ ] **35** — Storage detail sits in the reminders segment — *local-storage*, verified
- [—] **36** — Compose BOM is ~19 months behind — moved to the HLD, see Dropped

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

Fixed by reading with `SavedStateHandle.remove` — at the read, not when the target is
consumed, since EL-UI-083's unresolvable-category path opens no sheet and so
never reaches a consume call. `MainActivity`'s extra was left alone: it feeds
only `startDestination`, which is ignored whenever a back stack restores, and
`removeExtra` does not survive process death anyway.

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
`CategoryEditScreen.kt:823`

`onValueChange` dropped anything outside 1..12 while `value` came from an `Int`
in state, so a rejected keystroke left the state unchanged and the field sprang
back. Clearing it was impossible — an empty box parses to nothing — and with the
box unclearable the only way to change the number was to append to it. From the
default of `1` that reaches `11` and `12`; every value from 2 to 10 was
unreachable. Verified by reading the code rather than reproduced; the fix is
confirmed on device.

Fixed by holding the field as text so it can be emptied and retyped, with a
two-digit character check (1–99) in the ViewModel rather than in the screen's
lambda, where nothing could test it. Empty and `0` are typeable and refused at
save under their own validation key.

Unlike CAT-UI-011a — where bounds enforcement was descoped because a bad value
only stores a bad default — `occurrencesPerDay` feeds the RANDOM sub-window
division, so a non-positive value is an arithmetic hazard downstream. That is
why the bound got its own spec line (REM-UI-006a) rather than inheriting the
sets/reps precedent, which accepts any text and falls back on parse.

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

A third case belonged with these two, found while verifying #8:
`ReminderScheduling.kt:113` computes `totalNanos / reminder.occurrencesPerDay`,
so a stored `0` is an `ArithmeticException` on the same path.

Fixed together, along with three more the same audit turned up — `DayOfWeek.valueOf`
on an unrecognized weekday name, and `LocalTime.parse` on a `times` entry or a
window bound that is not `HH:mm`. `toDomain()` is now total: one `try` around
the whole decode falling back to `Reminder.default(categoryId)`, with the
parseable-but-unusable cases as `require` calls inside it, so a column added
later is covered without anyone remembering to guard it. Enumerating repairs
per field would have been correct only until the next column. REM-DATA-010
carries the rule.

### 13 — Schema `4.json` describes an unreachable version
`app/schemas/net.clahey.trackr.data.local.TrackrDatabase/4.json`

Version 4 existed only in unmerged commits on this branch, so no distributed
build ever wrote `user_version = 4`. There is no 4→5 migration and no
`fallbackToDestructiveMigration` (`DatabaseModule.kt:24-31`), so the file
implies a reachable state that is not reachable. Deleting it is the right end
state.

**Do not delete it on this branch.** This branch squash-merges, so a file added
and deleted within it nets to nothing in the squashed commit and `4.json` would
never exist in `master`'s history at all. The schema of a version that briefly
existed is worth keeping on the record, so the file rides through the merge and
is deleted by a separate commit afterwards. Nothing depends on it in the
meantime: `MigrationTest.kt` reads only the schemas its own migrations use, and
`MIGRATION_3_5` is the sole path across that range.

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

Four sites, not three: `CategoryEditViewModel.kt:105` carries one on a comment
continuation line that has no `@spec` on it, so a grep for `@spec.*\.\.` misses
it. Three of the four were duplicates at call sites of owners that already
carried literal lists — `ReminderSection`'s own definition, and the ViewModel's
class annotation — and were deleted rather than expanded. The fourth became the
two specs the ViewModel actually owns, REM-UI-001 and REM-UI-011; everything
else it used to claim is annotated on the member that implements it.

### 16 — `RemindersModule` cites a spec it does not implement
`RemindersModule.kt:18`

Cites `APP-REM-001`, which owns the fact that `AndroidManifest.xml` declares the
two receivers — already annotated at its real entry point in the manifest. The
module's actual bindings (`AlarmScheduler`, `ReminderNotifier`, `AlarmManager`,
`NotificationManager`) are covered by no spec, so this is code with no correct
intent linkage plus a polluted citation.

The missing intent traced upstream: the LLD still described `ReminderScheduler`
as injected with `AlarmManager` and `Context`, so there was nothing for the
module to cite. REM-SCHED-021 and REM-NOTIF-013 now state what the two
interfaces are for — every scheduling decision reachable from a unit test with
no Android runtime — and the module cites those. REM-SCHED-021 also carries the
requirement #18 is about, and stays open until it lands.

### 17 — Four copies of the time-picker dialog
`CategoryEditScreen.kt:917`, `:932`, `:966`, and `ui/components/TimestampField.kt:144`

The same `rememberTimePickerState` + `AlertDialog` body appears three times in
`CategoryEditScreen` and a fourth time in the shared component. Extract one
`TimePickerDialog(initial, onConfirm, onDismiss)` into `ui/components/` and have
all four call it.

Done. The component is owned by the event-logging segment, not
category-management as this item first labelled it: `TimestampField` is one of
its callers, and that LLD already carried the reason the component exists — M3
ships a `DatePickerDialog` and no `TimePicker` equivalent. A components-level
LLD was considered and rejected; `ui/components/` is a directory, not an intent,
and its contents belong to five different segments. Extraction surfaced that the
add-time picker's 09:00 opening hour was hardcoded and unspecified, now stated in
REM-UI-004. `TimestampField`'s dialog was the one hand-built from
`Dialog`/`Surface` and picks up `AlertDialog`'s padding and elevation.

### 18 — Exact-alarm check hand-rolled instead of asking `AlarmScheduler`
`CategoryEditScreen.kt:159`

`Build.VERSION.SDK_INT < S || canScheduleExactAlarms()` is written out in the
UI while `AlarmScheduler.canScheduleExact()` (`AndroidAlarmScheduler.kt:22`) is
the injectable, fakeable form and goes uncalled. None of it is reachable from
ViewModel tests — which is why `save()` grew three defaulted boolean
parameters.

Down to one site. The two that *displayed* this state read through
`rememberExactAlarmAvailable()` after #9/#10, and the banner's tap handler
stopped reading it at all after #29–#31. What remains is `doSave`'s check, which
REM-PERM-003 requires be read at the moment of the save — so it wants
`AlarmScheduler`, not the composable.

Fixed by exposing `canScheduleExact()` on `ReminderScheduler` and having `save()`
ask it, which drops the parameter. Via `ReminderScheduler` rather than injecting
`AlarmScheduler` into the ViewModel: the ViewModel already depends on the
scheduler and nothing else scheduling-related, and roughly twenty construction
sites across four test files would otherwise have gained an argument they have
no interest in. The permission table test now sets availability on
`FakeAlarmScheduler` instead of passing it, which is what turns it into a test
of the decision rather than of its own assertion. Closes REM-SCHED-021, added
by #16.

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

Done. The two jobs were split rather than gated together, because they wanted
opposite answers. The orphan image scan moved to `MainActivity.onCreate` (after
`setContent`) behind `UiStartupWork.runOnce()`, an `@Singleton` guarding an
`AtomicBoolean` — the guard matters because `onCreate` runs again on every
configuration change. The reminder reconcile stayed on process start: an alarm
wake is a useful moment to notice that *other* reminders were dropped, and
REM-SCHED-019's exact-alarm upgrade check wants to run often rather than only
when someone opens the app.

What makes moving the scan safe is that only UI activity produces orphans, so
the uncollected set cannot grow during broadcast-only wakes — and a notification
tap opens `MainActivity`, so even someone who never launches from the launcher
collects on every tap. The write race stayed as described above: it is bounded
by the staleness buffer and both writers compute near-identical values, so it
was documented as an accepted cost rather than fixed.

`TrackrApplication`'s hand-rolled `CoroutineScope` became a Hilt-provided
`@ApplicationScope` singleton (new `CoroutineModule`, APP-DI-005). That is not
cosmetic: the scan is launched from an Activity but must survive that Activity
being destroyed mid-run, since the guard is already set and nothing would retry.

APP-PROC-001 rewritten, APP-PROC-002 reworded to say which kind of process start
it means, APP-PROC-003 added for the once-per-process guard. Cascaded into
local-storage (LS-BE-041 now cites APP-PROC-001 as the owner of the hook rather
than naming it a second time) and reminders (REM-SCHED-017 lost a clause about
not blocking a UI frame that a broadcast-woken process does not have). Three
unit tests in `UiStartupWorkTest.kt` — the segment's first test file.

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

### 29 — Permission prompt sends the user to the less severe problem first
`CategoryListScreen.kt:171`

The banner's tap handler checks exact alarms first and falls through to
notification settings, so when both are missing it opens "Alarms & reminders" —
the less damaging of the two. Notifications off means the reminder produces
nothing visible at all; exact alarms off means it still fires, just imprecisely.
The user fixes the timing, returns, and the banner is still there.

### 30 — No prompt or recovery path when notifications are denied
`CategoryEditScreen.kt:760`

The Reminder section shows an inline card when exact alarms are unavailable but
nothing at all when notifications are denied — only the one-shot system dialog,
which Android will not show again after a denial. So denying leaves the edit
screen with no indication anything is wrong and no way to fix it. REM-PERM-003's
save-time dialog warns but only offers "Save anyway"; it does not link out.

### 31 — One message for two different failures
`strings.xml:162` (`reminder_banner_message`)

The banner reads "Some reminders may not fire reliably" regardless of cause.
That is timing language, and it is wrong for the notifications case, where
reminders will not be shown at all rather than shown late.

### 32 — A blocked notification channel is undetectable
`PermissionState.kt` (`rememberNotificationsEnabled`), `CategoryEditScreen.kt:158`

Every notification check calls `areNotificationsEnabled()`, which reports
app-level state only. `AndroidReminderNotifier` posts to the `"reminders"`
channel (`REMINDER_NOTIFICATION_CHANNEL_ID`), and nothing anywhere reads
`getNotificationChannel("reminders")?.importance`. Blocking just that channel
leaves `areNotificationsEnabled()` true, so no banner, no save-time warning, and
`notify()` silently drops the notification.

Long-pressing a notification and tapping "Turn off notifications" blocks the
*channel*, not the app — the most common way a person mutes something is
precisely the case the app cannot see.

The fix is a third input to `reminderPermissionProblem()` (REM-PERM-006) and its
two live reads, ranked alongside `NotificationsDisabled`. The runtime permission
request at `CategoryEditScreen.kt:728` is *not* a site: it gates a system dialog,
and no dialog can unblock a channel, so it stays app-level.

### 33 — Tapping an unexpanded multi-reminder row shows no reminders
`AndroidReminderNotifier` (grouping)

Not an in-app row: the collapsed *notification bundle*. Every reminder
notification set `setGroup("reminders")` with no group summary posted, which
REM-NOTIF-004 mandated outright. The bundle Android generates for two or more
notifications in that situation carries no content intent, so tapping the
collapsed group does nothing — and only ever with multiple reminders, which is
why it read as being about "multiple".

Fixed by posting a real summary (`setGroupSummary(true)`, `setAutoCancel(false)`)
that opens the timeline. REM-NOTIF-004 reversed. Confirmed on device.

### 34 — Tapping the yellow-dot icon shows no reminders
Not a defect. Tapping a launcher icon that carries a notification dot opens the
app through the launcher's own intent; it does not deliver the notification's
`PendingIntent`, so there is no category extra and no deep link. The dot only
signals that notifications exist, and the long-press menu is the launcher's UI,
not ours. No app-side change makes that tap open a quick-log.

Superseded by the outstanding-reminders list: landing in the app now shows what
is waiting, which is the outcome the report was after.

### 35 — Storage detail sits in the reminders segment
`docs/specs/reminders.md` (REM-DATA-001/006/008), `docs/llds/reminders.md § Data Model`,
`docs/llds/local-storage.md:82,97,160`

`local-storage.md:97` draws the boundary as "this segment owns the mechanical
storage, not the design intent behind it," which is too fuzzy to decide cases
with — so implementation detail ended up on both sides. REM-DATA-001 states "at
most one Reminder per category" (true of any backend) and "foreign key declared
`CASCADE DELETE`" (only true of SQL) in one sentence; REM-DATA-006 says "in a
single database transaction" where the intent is just *atomically*. The
`ReminderEntity` field table is written out in full in both LLDs, and that
already drifted — the 2026-08-12 audit found stale lowercase `mode` casing and
had to correct two copies.

Better test for the boundary: **would this be different on a document store or
behind a server API?** If yes it is implementation and belongs to local-storage;
if no it is the feature's intent and stays with the feature. Decidable, unlike
"mechanical."

Two directions, and it is not yet settled which: move reminders' storage detail
down into local-storage so `ReminderDao` is documented like the other DAOs, or
conclude the other DAOs are the under-documented ones and level up instead.
Either way the `ReminderEntity` table wants one canonical copy and a pointer.

## Dropped

### 36 — Compose BOM is ~19 months behind
Moved to `docs/high-level-design.md § Open Questions & Future Decisions`. It is
not a finding from the PR #4 review, and this file is meant to be deleted once
the review is burned down — a project-level dependency-currency question, and
the UI test gap tied to it, outlive that. The HLD had no Open Questions section
before this; it does now.

### 1 — Receivers skip `super.onReceive`, so Hilt never injects
Retracted. Reminders have been observed firing on a real device, and
`ReminderScheduler.onAlarmFired` is what posts the notification, so injection
demonstrably works. `ReminderRearmReceiver` is structurally identical, so the
same holds for it. Whether the reboot and timezone-change paths work end to end
is a separate, still-untested question.
