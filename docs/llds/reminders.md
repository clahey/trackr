# Reminders

## Context and Design Philosophy

This segment owns per-category logging reminders: local notifications that prompt the user to log an event for a specific category, scheduled either at a fixed time or at a randomized time within a configurable window, one or more times a day, on user-chosen active days. It does not own the category's other fields (name/emoji/color/valueType) — those belong to `category-management` — but its configuration UI lives inside that segment's Category Edit screen.

The segment has three responsibilities: persisting reminder configuration (a thin addition to the existing Room schema), computing when a reminder should next fire (a pure, testable domain function), and driving the OS-level scheduling and notification mechanics (`AlarmManager`, a `BroadcastReceiver`, `NotificationManager`). Per `docs/high-level-design.md § Key Design Decisions`, delivery uses `AlarmManager.setExactAndAllowWhileIdle()`, one-shot with reschedule-on-fire, not `WorkManager` or `setAlarmClock()`.

## Data Model

### Reminder (domain type)

```kotlin
data class Reminder(
    val categoryId: String,
    val enabled: Boolean,
    val mode: ReminderMode,                  // FIXED or RANDOM
    val times: List<LocalTime>,              // FIXED only; one or more clock times, non-empty when mode == FIXED
    val windowStart: LocalTime?,             // RANDOM only
    val windowEnd: LocalTime?,               // RANDOM only; must be strictly after windowStart (no overnight wrap in v1 — see Decisions)
    val occurrencesPerDay: Int?,             // RANDOM only; >= 1
    val daysActive: Set<DayOfWeek>,          // non-empty; defaults to all 7 on creation
    val showCategoryInNotification: Boolean, // default false, per HLD "public surfaces default to discreet" guideline
    val nextFireAt: Instant?,                // the currently-armed alarm's target instant; null when disabled or not yet armed
)

enum class ReminderMode { FIXED, RANDOM }
```

One `Reminder` per category (zero or one row) — `FIXED` mode's `times` list covers "multiple fixed times a day" (e.g. `[08:00, 20:00]`) and `RANDOM` mode's `occurrencesPerDay` covers "multiple random times a day" (e.g. 4 occurrences spread across a 9am–9pm window), so a single row per category covers every recurrence shape without needing multiple independent reminder rules.

### ReminderEntity (Room)

| Column | Type | Notes |
|---|---|---|
| `categoryId` | `String` PK, FK → categories(id) CASCADE DELETE | one row per category |
| `enabled` | `Boolean` | |
| `mode` | `String` | `"fixed"` / `"random"` |
| `times` | `String?` | JSON list of `"HH:mm"` strings; FIXED only |
| `windowStart` | `String?` | `"HH:mm"`; RANDOM only |
| `windowEnd` | `String?` | `"HH:mm"`; RANDOM only |
| `occurrencesPerDay` | `Int?` | RANDOM only |
| `daysActive` | `String` | JSON list of `DayOfWeek` names, e.g. `["MON","TUE",...]` |
| `showCategoryInNotification` | `Boolean` | default `false` |
| `nextFireAt` | `Long?` | epoch millis; null when disabled |

CASCADE DELETE mirrors `EventEntity`'s FK — deleting a category removes its reminder row automatically. **This does not cancel a live `AlarmManager` alarm**, which is OS state outside the DB. That cancellation can't live inside `LocalTrackrRepository.deleteCategory()` itself — `ReminderScheduler` already depends on `TrackrRepository`, so the reverse call would be a circular dependency between segments. Instead, the **caller** of `deleteCategory` (`category-management`'s `CategoryListViewModel`/`CategoryEditViewModel`, both of which already sit above both `TrackrRepository` and can be given `ReminderScheduler` via DI) calls `repository.deleteCategory(id)` first, then `reminderScheduler.cancel(categoryId)` second — DB delete authoritative and first, OS alarm cleanup best-effort and second, mirroring this repository's existing image-deletion ordering (DB first, files after). If the process dies between the two calls, the orphaned alarm is still safe: it fires once, `onAlarmFired`'s existence check (see § Scheduling Engine) finds no `Reminder` row and no-ops. This is a cross-segment touch into `category-management.md`'s delete flow — flagged in Open Questions, not made yet.

### TrackrRepository additions (persistence only — no AlarmManager access)

```kotlin
fun getReminderForCategory(categoryId: String): Flow<Reminder?>
suspend fun saveReminder(reminder: Reminder)                          // upsert, standalone
suspend fun saveCategoryWithReminder(category: Category, reminder: Reminder?)  // atomic: category upsert + reminder upsert/clear in one transaction
suspend fun getAllEnabledRemindersOnce(): List<Reminder>              // boot / time-change re-arm
```

`saveCategoryWithReminder` is what `CategoryEditViewModel.save()` actually calls — the category's other fields and its reminder config are edited on the same screen and saved together, so they persist atomically in one transaction rather than as two independent writes (`reminder = null` clears any existing row). Arming/cancelling the `AlarmManager` alarm is **not** part of this transaction — it's a post-commit side effect the ViewModel triggers via `ReminderScheduler` once the save succeeds (see § Scheduling Engine), consistent with how image file deletion already happens after, not inside, this repository's transactions elsewhere.

These live in `local-storage.md`'s `TrackrRepository`/`LocalTrackrRepository`/a new `ReminderDao` — a cross-segment touch, flagged in Open Questions.

## Category Edit Integration (UI)

A new **"Reminder"** section appears in the Category Edit screen (`category-management`'s `CategoryEditScreen`/`CategoryEditViewModel`), below the Default Value fields. Available for both MetaCategory and SubCategory — reminders are not inherited (independent per category).

**Collapsed state (reminder off):** a single row — `[Reminder icon] Reminder  [off toggle]`.

**Expanded state (reminder on):**

| Field | Input | Shown when |
|---|---|---|
| Mode | Segmented control: "Fixed time" / "Random" | Always (reminder on) |
| Times | List of time chips, each opening a `TimePicker`; `[+ Add time]` to append another; tap-to-remove on each chip (minimum one) | Mode = Fixed |
| Window | Two `TimePicker` fields, "Start" and "End" | Mode = Random |
| Times per day | Stepper, 1–N (reasonable UI cap, e.g. 12) | Mode = Random |
| Active days | Row of 7 toggle chips (Mon–Sun), all on by default | Always (reminder on) |
| Show category name in notification | Switch, default off | Always (reminder on) |

**Validation:** at least one active day; Fixed mode requires at least one time; Random mode requires `windowEnd` strictly after `windowStart` and `occurrencesPerDay >= 1`.

**Save is atomic with the rest of the category edit.** The Reminder section has no independent save/cancel — it participates in the screen's existing dirty-tracking/save lifecycle exactly like Name, Emoji, or Default Value. Saving persists category fields and reminder config together via `saveCategoryWithReminder` (see § Data Model); discarding (via the existing `UnsavedChangesDialog`) discards reminder edits along with everything else.

**Permission handling.** Three separate moments, because "ask" and "warn" need different timing depending on whether a person is present to answer a dialog:

1. **On entering/expanding the Reminder section** (turning the collapsed row on, or opening an already-on one to edit): if `POST_NOTIFICATIONS` is not granted (Android 13+), the runtime permission dialog is requested immediately — proactively, not deferred to save. If exact-alarm scheduling is unavailable (`AlarmManager.canScheduleExactAlarms() == false`, API 31+) there is no equivalent in-app runtime dialog for that permission — Android only exposes it via the system "Alarms & reminders" settings screen — so an inline prompt offers to launch it directly (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`).
2. **On save**, if the reminder is enabled and either permission is still missing (denied, or the user didn't act on the step-1 prompts), a confirmation dialog blocks the save: *"Notifications aren't fully enabled — this reminder may not fire or may not be visible. Save anyway?"* **Cancel** aborts the save entirely (nothing persisted, screen stays open); **Save anyway** proceeds, persisting and arming with whatever degraded behavior is available (inexact scheduling and/or a silently-dropped notification — neither `AlarmManager` nor `NotificationManagerCompat` throw for a missing permission, they degrade silently, so nothing crashes either way).
3. **Ambient, live-checked banner** (not a stored flag) on the Category List screen: shown whenever at least one category has an enabled reminder **and** (`POST_NOTIFICATIONS` is denied **or** `canScheduleExactAlarms() == false`) at the moment the screen composes — *"Some reminders may not fire reliably — tap to review,"* linking to the relevant system settings. Because it's evaluated live rather than tracked as persisted state, it self-heals the instant permission is granted. This is the backstop for anything step 1/2 can't reach — permission missing (or later revoked) with no interactive save happening at all. It's deliberately *not* an active retry-at-startup mechanism on its own; § Scheduling Engine's startup reconciliation already retries scheduling itself on every launch, so the banner only needs to report whatever that retry couldn't fix.

## Scheduling Engine

`ReminderScheduler` (new `@Singleton`, injected with `TrackrRepository`, `AlarmManager`, `Context`) owns all `AlarmManager` interaction. `LocalTrackrRepository` and Room never talk to `AlarmManager` directly — persistence and OS scheduling stay separated, matching the HLD's System Design diagram.

**`computeNextFireTime(reminder: Reminder, after: Instant, zone: ZoneId, random: Random = Random.Default): Instant`** — domain function (lives in `domain/ReminderScheduling.kt`, unit-testable via an injected `Random` for determinism), finds the next fire instant strictly after `after`:

- **FIXED**: within `after`'s local date, find the first entry in `times` (sorted) whose instant is after `after`, on an active day; if none, advance date-by-date (skipping inactive days) to the next active day and take its earliest time.
- **RANDOM**: divide `[windowStart, windowEnd]` into `occurrencesPerDay` equal-length sub-windows. Find the earliest sub-window whose range is not yet exhausted relative to `after`; draw one uniform-random instant inside that sub-window using `random`. If all of today's sub-windows are exhausted, advance to the next active day and draw within its first sub-window. (Equal-width sub-windows with one random draw each avoids the clustering that N independent draws across the whole window risk — see Decisions.) Sub-window boundaries are static, derived purely from `windowStart`/`windowEnd`/`occurrencesPerDay` — "not yet exhausted" is a direct comparison against `after`, not a decrementing count, so no separate "occurrences remaining today" state is needed or stored; `after` (always the just-fired alarm's timestamp on the reschedule path) is enough on its own to know where in the day's sequence the next draw falls.

Exactly one alarm is ever armed per enabled reminder — never one-per-occurrence. After firing, the receiver calls back into the scheduler to compute and arm the next single occurrence.

**Arming:** `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, nextFireAt.toEpochMilli(), pendingIntent)` where `pendingIntent` carries `categoryId`. Falls back to `AlarmManager.setAndAllowWhileIdle()` (inexact) when `canScheduleExactAlarms() == false`.

**`ReminderReceiver` (BroadcastReceiver):** on receipt, reads `categoryId` from the intent and calls `ReminderScheduler.onAlarmFired(categoryId)`, which first re-reads `getReminderForCategory(categoryId)` and checks it's still non-null **and** `enabled` — a defensive existence check against the category (and its cascade-deleted `Reminder` row) having been removed, or the reminder having been disabled, since the alarm was armed. If either check fails, it's a no-op: no notification, no reschedule. Otherwise it posts the notification (see § Notifications), recomputes `nextFireAt` via `computeNextFireTime(reminder, after = firedAt, ...)`, persists it, and re-arms. This check is a safety net alongside — not a replacement for — the explicit `ReminderScheduler.cancel(categoryId)` call `deleteCategory` needs to make (see § Data Model): the explicit cancel avoids uselessly waking the device for an alarm that's already known to be moot, while this check covers any case that cancel doesn't reach (a disable racing a fire, most notably — see Decisions).

**Re-arming outside normal fire-and-reschedule.** Alarms don't survive reboot, and a fixed `nextFireAt: Instant` can land at the wrong local wall-clock time if the system clock or timezone changes underneath it (e.g. travel, DST). A single `ReminderRearmReceiver` (BroadcastReceiver registered in the manifest, not dynamically — `BOOT_COMPLETED`, `ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED` are all exempt from the implicit-broadcast background restrictions, so a manifest-registered receiver still receives them while the app isn't running) handles all three triggers identically: call `ReminderScheduler.rearmAll()`, which loads `getAllEnabledRemindersOnce()` and, for each, recomputes `nextFireAt` from `after = now` in the *current* zone (never the stale pre-change value) and re-arms.

**Startup reconciliation.** In addition to the broadcast-driven re-arms above, every app process start also runs a lightweight self-healing pass: `ReminderScheduler.reconcileOnStartup()` loads `getAllEnabledRemindersOnce()` and re-arms any reminder whose `nextFireAt` is null or already in the past (an alarm the OS silently dropped, a missed `BOOT_COMPLETED`, or simply an install where nothing has ever been armed) — reminders whose `nextFireAt` is still valid and in the future are left untouched, not needlessly re-armed. This is launched fire-and-forget from `TrackrApplication.onCreate()`, alongside — but not folded into — the existing `repository.onStartup()` call: `onStartup()` stays scoped to its documented purpose (image orphan cleanup, per `LS-BE-041`), and `ReminderScheduler` owns its own startup hook rather than becoming a second responsibility bolted onto `local-storage`'s. Neither blocks the first UI frame. This is a cross-segment touch into `app-shell` (`TrackrApplication.kt`), flagged in Open Questions.

**Enable/disable/edit:** `ReminderScheduler.enableReminder(reminder)` computes `nextFireAt` from `after = now` and arms (persistence of the `Reminder` row itself already happened via `saveCategoryWithReminder`; this call only writes the computed `nextFireAt` back and arms the alarm). `disableReminder(categoryId)` persists `enabled = false, nextFireAt = null` and cancels the pending alarm via `AlarmManager.cancel(pendingIntent)`. Editing an active reminder (mode, window, times, or **active days** — including narrowing them to exclude the day `nextFireAt` currently points at) re-runs `enableReminder` — always recomputed from `now`, never preserving a stale `nextFireAt` from before the edit.

## Notifications

**Channel:** one notification channel, `"reminders"`, created at app startup (idempotent) with default importance.

**Content:** title is always generic — "Time to log" — regardless of `showCategoryInNotification`. Body defaults to a generic prompt ("You have a category waiting to be logged"); when `showCategoryInNotification` is true, body becomes "{emoji} {category name}" instead. This follows the HLD's "public surfaces default to discreet" guideline — showing the category is opt-in per reminder, off by default.

**Grouping:** each reminder notification sets `setGroup("reminders")` (the same key for every category). When multiple fire close together, Android's own notification-shade stacking bundles them under the app rather than showing N separate top-level entries — no custom summary notification or bundling logic needed on our side; the OS default grouping-by-app-and-group-key already produces the right combined presentation.

**Tap action:** the notification's `PendingIntent` launches `MainActivity` targeting `Routes.timeline(quickLogCategoryId = categoryId)` (`app-shell.md § MainActivity`/`§ Navigation Graph`). `HomeViewModel` consumes that argument once on init and opens the quick-log sheet **directly** at an explicit target — `event-logging.md`'s `QuickLogTarget` — independent of `ActiveFilter`, rather than going through the chip-tap-driven opening path used for in-session taps:

```kotlin
sealed class QuickLogTarget {
    data class DrillDown(val meta: Category.MetaCategory) : QuickLogTarget()  // opens straight into the drill-down view
    data class DirectEntry(val category: Category) : QuickLogTarget()        // opens straight to step 2
}
```

- **MetaCategory reminder, has SubCategories** — opens `QuickLogTarget.DrillDown(meta)` (the drill-down view: back-button header, "Log to [Name] directly" tile, SubCategory grid). **Separately**, and only for this case, that same init-time handling also sets `ActiveFilter.TopLevel(meta)` on `HomeViewModel` — a normal, permanent filter change, the same call a chip tap makes, not a temporary state tied to the dialog's lifetime. It's set "behind" the sheet: it doesn't drive what the sheet shows (the `DrillDown` target already did that directly) and needs no clearing afterward, since it was never a special transient state to begin with — once the sheet closes, the timeline is simply left filtered to that group, exactly as if the user had tapped the chip themselves before opening the FAB.
- **MetaCategory reminder, no SubCategories** — opens `QuickLogTarget.DirectEntry(category)`. `ActiveFilter` is left untouched entirely — nothing is read or written.
- **SubCategory reminder** — opens `QuickLogTarget.DirectEntry(category)`. `ActiveFilter` is left untouched entirely.

Decoupling "what the sheet opens to" (always explicit, via `QuickLogTarget`) from "what the timeline's filter is" (touched only for the one case where it's actually wanted) avoids needing any new "temporarily override, then clear" lifecycle — the two non-sticky cases simply never touch `ActiveFilter`, so there's nothing to clear. `QuickLogTarget`, `HomeViewModel`'s `quickLogCategoryId` argument, and the `ActiveFilter.TopLevel(meta)` side effect are specced in full in `event-logging.md § Quick-Log Sheet`; the deep-link wiring that gets the argument there is specced in `app-shell.md`.

**No action buttons (Log now / Snooze) in v1** — tap-to-open covers the primary flow; see Open Questions.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Reminders per category | Exactly one (zero-or-one `Reminder` row) | Multiple independent reminder rules per category | `times`/`occurrencesPerDay` already cover "more than once a day" within one row; a second axis of multiple rows adds real schema/UI cost for marginal extra flexibility |
| Reminder storage shape | Separate `ReminderEntity` table, one row per category, FK → `categories(id)` CASCADE DELETE | Columns embedded directly on `CategoryEntity` | Most categories won't have a reminder configured, and the shape needs ~9 mostly mode-conditional, nullable columns — embedding would bloat the high-traffic `categories` table with columns null in the common case, and couple reminders' schema evolution to categories' migration history. A separate table also matches segment ownership (`reminders` doesn't own the category's other fields) and still gets atomic combined saves via `saveCategoryWithReminder`'s single transaction, and automatic cleanup via CASCADE DELETE — so splitting costs only a join to read both together, no loss of the "one save" or "one delete" ergonomics |
| Reminder mode representation | Single flat `Reminder`/`ReminderEntity` shape with mode-conditional nullable columns (`times` FIXED-only; `windowStart`/`windowEnd`/`occurrencesPerDay` RANDOM-only) | Polymorphic `Reminder` (sealed class per mode), normalized into per-mode subtype tables (table-per-subtype) so no column is nullable-because-of-a-sibling-mode | A subtype split forces a choice on every mode switch: either the inactive mode's values are dropped (switching Fixed → Random → Fixed loses the user's previously-configured times) or the app keeps an orphaned inactive-mode row around and merges it back in — which is exactly the data the flat shape already holds, just relocated behind a join and explicit child-row lifecycle management on every mode change. The flat shape lets a user flip between Fixed and Random while editing and get their prior config back for whichever mode they're not currently on, at the cost of a few columns unused by the sibling mode — an acceptable trade at this table's scale (one row per category, always read/written whole, never queried by mode-specific column) |
| Disabling a reminder | `enabled: Boolean` field; `disableReminder` sets `enabled = false, nextFireAt = null` and cancels the alarm, row otherwise untouched | Delete the `ReminderEntity` row to represent "off" | Disabling is meant to be a reversible toggle, not a reset — deleting the row would lose the configured mode/times/window/days, forcing the user to reconfigure from scratch on re-enable. Row presence is already spoken for by CASCADE DELETE (it means "this category still exists"); overloading it to also mean "temporarily off" would conflate the category's lifecycle with the reminder's on/off state. Keeping the row also gives the Category Edit screen somewhere to read a disabled-but-configured reminder's fields from |
| Availability window scope | Per-reminder (`windowStart`/`windowEnd` on the `Reminder` itself) | One global app-wide quiet-hours setting | Different categories plausibly want different windows (an exercise reminder in the evening, a mood check any waking hour); a single global window can't express that |
| Reminder inheritance | Independent per category, no inherit toggle | Inheritable like color/emoji/valueType | Reminders are a behavior tied to how the user wants to be nudged about a specific category, not a display trait; an "inherited random window" is a confusing concept for a field no one else in the hierarchy shares |
| Multi-occurrence random spacing | Divide the window into `occurrencesPerDay` equal sub-windows, one random draw per sub-window | N independent uniform draws across the whole window | Independent draws can cluster (two picks minutes apart, a long gap elsewhere); equal sub-windows guarantee spread while keeping each individual pick genuinely random |
| Scheduling separation | `ReminderScheduler` (new component) owns all `AlarmManager` calls; `TrackrRepository`/Room only persist `Reminder` rows | AlarmManager calls inside `LocalTrackrRepository` | Keeps `local-storage` a pure persistence layer, consistent with its existing scope (Room + `ImageStore`, no OS scheduling APIs) |
| Exact-alarm permission denial | Fall back to inexact (`setAndAllowWhileIdle`) scheduling, still create/arm the reminder, surface a warning in the UI | Block reminder creation until permission granted | Matches the app's existing graceful-degradation pattern for permissions (no `CAMERA` permission required for photos); a denied optional permission shouldn't disable an otherwise-working feature |
| Overnight (midnight-crossing) window | Not supported in v1 — `windowEnd` must be strictly after `windowStart` same day | Support wrap-around windows (e.g. 10pm–2am) | Adds real complexity (date-boundary handling in `computeNextFireTime`) for a use case not yet requested; validation simply rejects it for now |
| Category save + reminder save | Combined, atomic (`saveCategoryWithReminder`, one transaction) | Independent saves (reminder section has its own save/cancel) | Both are edited on the same screen in the same sitting; two independent saves would need their own separate dirty-tracking and could partially fail, leaving fields and reminder config out of sync |
| Alarm/notification permission UX | Three-tier: proactive ask on entering the Reminder section; a blocking "Save anyway?" confirmation on save if still missing; an ambient, live-checked (not stored) banner on the category list for anything missed by the first two, backed by an active startup reconciliation retry rather than a passive banner alone | Single inline warning at enable time only; block reminder creation until granted; banner with no active retry behind it | A single ask-at-enable-time moment misses permission revoked later or missing before the user ever opens the reminder UI (e.g. right after install); live-checking the banner instead of storing a flag means it self-heals the moment permission is granted; retrying at every startup (not just relying on the banner to prompt a manual fix) recovers automatically the moment permission is actually granted, with the banner only reporting what the retry still can't fix |
| Notification tap target and filter stickiness | A dedicated `QuickLogTarget` opens the sheet directly, decoupled from `ActiveFilter`; only the MetaCategory-with-children case *also* sets `ActiveFilter.TopLevel(meta)`, as a normal permanent filter change, not a temporary one | Reuse the existing `ActiveFilter`-driven opening path for all three cases, setting a filter transiently and clearing it back to `All` when the sheet closes for the two non-sticky cases | A transient-filter-plus-clear-on-close design needs new "this filter is temporary" tracking and a new clear-on-close hook that doesn't exist today; a direct target sidesteps both — the sheet's content is never inferred from filter state, and the two cases that shouldn't touch the filter simply never do, so there's nothing to undo |
| Missed/raced fire safety net | `onAlarmFired` re-reads the `Reminder` row and checks `enabled` before posting or rescheduling, in addition to the delete flow's explicit alarm cancel | Rely solely on explicit cancellation (delete, disable) to prevent stale fires | Cancellation and a concurrent fire can race (disable, or a delete, happening at the instant the alarm fires); a read-before-acting check in the receiver closes that race without needing to make cancellation itself atomic with the OS alarm state |
| Clock/timezone change handling | A manifest-registered receiver on `BOOT_COMPLETED` + `ACTION_TIME_CHANGED` + `ACTION_TIMEZONE_CHANGED`, all routed to the same "recompute every enabled reminder's `nextFireAt` from now and re-arm" path | Only re-arm on boot; leave time/timezone changes unhandled | A fixed `Instant` alarm doesn't move with the wall clock — without this, travel or a DST transition silently shifts every reminder's *local* fire time until it happens to fire once and reschedule itself |
| Notification grouping | Separate notification per category, all sharing one `setGroup()` key; rely on OS default stacking | Custom summary notification with app-authored combined text; one shared notification updated in place | The OS already stacks same-app/same-group notifications correctly by default; a custom summary is only worth the extra code if the default presentation turns out to be wrong in practice |
| Alarm cancellation on category delete | Ordered as a caller-side sequence in `category-management`'s delete flow: `repository.deleteCategory(id)` first, `reminderScheduler.cancel(categoryId)` second | Cancel the alarm inside `LocalTrackrRepository.deleteCategory()` itself | `ReminderScheduler` already depends on `TrackrRepository`; having `local-storage` call back into `reminders` would be a circular dependency between segments. DB-delete-first mirrors this repository's existing image-deletion ordering, and is safe if interrupted because of the missed/raced-fire safety net above |

## Open Questions & Future Decisions

### Deferred

1. **Notification action buttons** ("Log now" inline action, "Snooze") — deferred; tap-to-open covers v1.
2. **Overnight window support** — see Decisions; revisit if requested.
3. **Global pause** ("mute all reminders," e.g. for vacation) — no such control in v1; each reminder is toggled individually.
4. **Arrow overlay entry** (`docs/arrows/reminders.md` + `index.yaml`) — not created yet; deferred to the next `arrow-maintenance` pass or created alongside the code in Phase 6.

### Resolved

5. ✅ **Cross-segment cascade into `local-storage.md`** — `ReminderEntity`/`ReminderDao`, the `TrackrRepository` additions, and the version 3→4 migration are now specced there (§ Room Entities, § DAOs, § LocalTrackrRepository, § Migration Strategy).
6. ✅ **Cross-segment cascade into `category-management.md`** — the delete-flow alarm-cancel call and the Reminder field's presence on the Category Edit screen are now noted there (§ Category List Screen delete flow, § Category Edit Screen fields table).
7. ✅ **Cross-segment cascade into `event-logging.md`** — `QuickLogTarget` and `HomeViewModel`'s `quickLogCategoryId` argument are now specced there (§ Quick-Log Sheet).
8. ✅ **Cross-segment cascade into `app-shell.md`** — the second startup launch, the `MainActivity` deep-link handling, the `Routes.TIMELINE` argument, and the two `BroadcastReceiver`s' manifest registration are now noted there (§ Application Class, § MainActivity, § Navigation Graph, § Startup Sequence); that LLD's "Deep links" open question is resolved.

## References

- `docs/high-level-design.md § Key Design Decisions` — `AlarmManager.setExactAndAllowWhileIdle()` choice and rejected alternatives
- `docs/high-level-design.md § Guidelines` — "Silence over spam," "Public surfaces default to discreet"
- `docs/llds/category-management.md` — Category Edit screen, hosts this segment's configuration UI
- `docs/llds/local-storage.md` — `TrackrRepository`, Room entities/DAOs; owns the persistence additions this segment requires
