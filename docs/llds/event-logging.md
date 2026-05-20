# Event Logging

## Context and Design Philosophy

This segment covers the full event lifecycle from the user's perspective: logging a new event, viewing the timeline, and editing or deleting existing events. The HLD goal of "log any event in under three taps" is the primary constraint on the quick-log flow design.

The segment owns three screens (timeline, quick-log sheet, event edit) and their ViewModels. It consumes `TrackrRepository` and `ImageStore` but owns no persistence logic.

## Screens

### Home / Timeline Screen

The primary screen. Displays all events grouped by calendar day of `timestamp` (user's local timezone), most recent day first, most recent event first within each day.

Each event row is an elevated card (visually distinct from the screen background). Layout:
- **Left**: 48dp filled circle using the category color; category emoji centered inside with WCAG foreground color (see `docs/llds/theme.md § Circle avatar`)
- **Center**: category name (subtitle), value summary (formatted per `ValueType`), and notes (if any) stacked vertically
- **Right**: time of day (from `timestamp`)

Supports:
- **Log new event** — FAB opens the Quick-Log Sheet
- **Edit event** — tap row → Event Edit screen
- **Delete event (swipe)** — swipe row to delete; row is replaced in place by an undo placeholder button
- **Delete event (edit screen)** — toolbar delete action with confirmation dialog (less common path)

Day headers show the date (e.g., "Today", "Yesterday", full date for older).

A horizontally scrollable row of category chips sits below the app bar. An "All" chip appears first; tapping any inactive category chip sets it as the active filter (visually highlighted); tapping the active chip again (or "All") clears the filter. When a filter is active, the FAB quick-log sheet opens directly to step 2 with that category pre-selected.

**Filter scroll behavior:** Day groups with no matching events are hidden when a filter is active. When a filter is applied (including switching between active filters), the timeline scrolls to keep the same calendar day approximately at the top (or the nearest earlier day that has matching events, if the current day has none). The pre-filter scroll position (top day) is recorded only on the transition from no filter to a filter — switching between active filters preserves the existing record so that clearing always returns to the original unfiltered position. When the filter is cleared, if the user has not manually scrolled since the filter was first applied, the timeline restores to the recorded pre-filter position. Manual scrolling (user-initiated only; not the system-initiated anchor scroll) discards the recorded position.

### Quick-Log Sheet

A bottom sheet opened from the timeline FAB. Two-step flow to minimize taps:

**Step 1 — Category picker**
Grid of all categories (emoji + name). Tapping one advances to step 2.

**Step 2 — Value + details**
- Value input (see Value Input section below); for Number, Text, and Duration types the input field is automatically focused on entry so the keyboard rises without an extra tap
- Optional: single photo (camera or gallery picker)
- Optional: notes text field
- Timestamp: defaults to now; tappable to edit (date + time picker)
- **Save** button — writes event and dismisses sheet

For `ValueType.None` categories, step 2 has no value input — save is immediately accessible, achieving the three-tap goal (FAB → category → save).

### Event Edit Screen

Full edit view for an existing event. Toolbar contains a **Delete** action.

Read-only header:
- Category emoji and name — displayed above the editable fields; if the category cannot be resolved (orphaned event), the header is omitted.

Editable fields:
- Timestamp (date + time picker)
- Value (see Value Input section)
- Notes
- Images (add via camera or gallery; remove individually; no cap on count)

Save navigates back to timeline. Delete shows a confirmation dialog, then deletes and navigates back.

## Value Input by ValueType

| ValueType | Input widget | Notes |
|---|---|---|
| `None` | — | No input; field omitted |
| `Scale` | Horizontal slider | Integer snap 1–10 |
| `Boolean` | Two-button row | Labeled "Yes" / "No" in v1; custom labels deferred (see Open Questions) |
| `Number` | Numeric text field + separate unit text field | Unit field pre-filled from `Category.unit`; user-editable; empty saves as null (unitless) |
| `Text` | Multi-line text field | Empty string gated by `Category.allowEmptyText` |
| `Duration` | Three separate numeric fields (H / M / S) | Domain type is `kotlin.time.Duration`; fields decomposed via `.toComponents` |
| `Exercise` | Two integer-only fields (Sets / Reps) | Default 3 / 15; both ≥ 1 required to save; formatted as "$sets × $reps" in the timeline |
| `Unknown(raw)` | Read-only display of raw value | Cannot edit — type unknown to this version |
| `ErrorValue` | Read-only display with error indicator | Shows `ErrorKind` and raw string; cannot edit |

`Unknown` and `ErrorValue` are read-only in both Quick-Log (these categories/events won't normally appear in the picker or edit flow) and Event Edit. An event carrying `ErrorValue` can still have its notes, timestamp, and images edited.

### ValueType change

When a category's `valueType` is changed, `TrackrRepository.saveCategoryAndMigrateEvents` migrates historical events in a single transaction using `convertEventValue`. The conversion is best-effort: values that have a defined conversion path (e.g., `Scale` → `Number`, parseable `Text` → `Scale`) are converted; values with no path are left unchanged in the database, creating a mismatch between the event's stored value type and the category's current type.

Mismatches can also arise when the app reads data from a newer app version — an `Unknown` category type or an `ErrorValue`. The event-logging UI is responsible for detecting and surfacing these cases.

### Value type mismatch

A **value action banner** is shown on the edit screen whenever `conversionOutcome` is non-null — i.e., the stored value cannot be meaningfully used as-is for the category's current type. This covers three cases:

1. **Type mismatch** — the stored `EventValue` is a concrete type that does not match the category's `valueType` (e.g., `TextValue("hello")` on an Exercise category). Happens when migration leaves some events unconverted.
2. **`ErrorValue`** — the stored value could not be decoded at all. Read-only per EL-UI-043; the banner offers a "replace with default" action.
3. **`Unknown` category type** — the category's type is unrecognized by this app version; no input is possible.

**Detection:** `matchesValueType(value: EventValue?, type: ValueType): Boolean` — domain-layer helper. Returns `true` only when the value's runtime type is the expected variant for `type`, or when an `ErrorValue` with `inferredType` matches an `Unknown` category's raw string (coherent future-type pair — both from the same unrecognized type). Returns `false` for: `ErrorValue` without a matching `Unknown` type, `Unknown` category type without a matching `ErrorValue`, `None` type with non-null value, or a concrete value of the wrong type. `null` on `None` type returns `true`.

**Conversion-or-default:** `convertOrDefault(value: EventValue, targetType: ValueType): ConversionOutcome` — domain layer. Returns a sealed class with three cases:
- `ConversionOutcome.Converted(value: EventValue)` — `convertEventValue` produced a value of the right type (genuine conversion from the stored data).
- `ConversionOutcome.UsedDefault(value: EventValue)` — conversion failed, no path exists, or the value is `ErrorValue`; `defaultForType(targetType)` is used instead.
- `ConversionOutcome.Discard` — target type is `None` or `Unknown`; the right action is to clear the event value.

**Timeline display:** events with `!matchesValueType(event.value, category.valueType)` display the raw stored value via the existing `formatValue` followed by a warning icon. No DB write on view.

**Edit screen — value action banner:** shown below the value input whenever `conversionOutcome` is non-null. Contains:
- A short description: *"Stored value doesn't match the category type."*
- An action button whose label depends on `conversionOutcome`, using `describeValue(v: EventValue): String` for the value descriptions (a UI-layer function that produces verbose human-readable text — e.g., `"2 sets × 5 reps"` for `ExerciseValue(2, 5)`, `"7/10"` for `Scale(7)`, `"Yes"` for `BooleanValue(true)`, `"3.5 kg"` for `NumberValue(3.5, "kg")`):
  - `Converted(v)`: **"Convert to [describeValue(v)]"**
  - `UsedDefault(v)`: **"Replace with default: [describeValue(v)]"**
  - `Discard`: **"Discard value"**
  - In all cases, tapping clears the banner and sets `value`: for `Converted(v)` or `UsedDefault(v)`, to `v`; for `Discard`, to null (only reachable when category type is None or Unknown).
- The banner is informational — the Save button remains enabled while the banner is visible. If the user saves without resolving the banner, the mismatched value is persisted unchanged.

**Edit screen — value input while banner is shown:** the value input fields render using the category's type with whatever fallback defaults `ValueInputField` uses for a mismatched value (the same `?: Default()` fallback already present). The value is not in a valid editable state until the action button is tapped or the user manually edits a field (which itself updates `value` and can clear the banner automatically if the new value matches).

## ViewModels

### HomeViewModel

- `activeFilter: StateFlow<Category?>` — the currently selected category chip; null = all categories
- `dayGroups: StateFlow<List<DayGroup>>` — derived from `repository.getEventsByCategory()` when a filter is active, `repository.getEvents()` otherwise; grouped by calendar day of `timestamp` in local timezone
- `preFilterTopDay: StateFlow<LocalDate?>` — the calendar day that was at the top of the timeline when a filter was first applied (transition from no filter); null if no filter is active or if the user has manually scrolled since the filter was first applied. Not updated when switching between active filters.
- `setFilter(category: Category?)` — sets `activeFilter`; records the current top day in `preFilterTopDay` only when transitioning from null to a non-null filter; switching between two non-null filters preserves the existing `preFilterTopDay`; null clears the filter
- `onUserScrolled()` — called by the UI when the user manually scrolls; clears `preFilterTopDay`
- `pendingDelete: StateFlow<Event?>` — the event swiped away but not yet committed; null when no undo is available
- `swipeDelete(event: Event)` — immediately calls `repository.deleteEvent()`; stores the full `Event` in `pendingDelete` and injects an undo placeholder into the day group at the event's original position
- `undoDelete()` — calls `repository.saveEvent(pendingDelete!!)` to restore; clears `pendingDelete`
- `clearPendingDelete()` — called internally before any other mutating action (new log, another delete, open event); discards `pendingDelete` without restoring

**Undo window closes when any of these occur:**
- Another event is swiped to delete
- A new event is logged (sheet saves)
- An event is opened for editing

The DB delete fires immediately on swipe — undo is a restore (`saveEvent`) not a rollback. The placeholder occupies the deleted event's original position in the day group until `pendingDelete` is cleared.

`DayGroup` is a ViewModel-layer data class (not a domain model):
```kotlin
data class DayGroup(val date: LocalDate, val events: List<DayEntry>)

sealed class DayEntry {
    data class Entry(val event: Event, val category: Category?) : DayEntry()
    data class UndoPlaceholder(val event: Event) : DayEntry()
}
```

### QuickLogViewModel

- `categories: StateFlow<List<Category>>` — from `repository.getCategories()`
- `selectedCategory: StateFlow<Category?>` — set when user picks in step 1
- Form state: `timestamp`, `value`, `notes`, `imagePath` (single, nullable)
- `timestamp` defaults to `Instant.now()` at sheet open; user-editable
- `save()`: validates, generates a UUID for the new event, writes image to `ImageStore.newFile()` if present, calls `repository.saveEvent()`, emits `SaveResult`
- `reset()`: called when sheet is dismissed (with or without saving); clears all form state including `saveResult` back to `Idle`, and deletes any captured-but-unsaved image file
- **Deleted category guard:** observes `categories`; if `selectedCategory` is no longer present in the list (deleted externally while sheet is open), resets `selectedCategory` to null and returns the user to step 1

### EventEditViewModel

- Loads event by ID via `repository.getEventById()`; also loads its category via `repository.getCategoryById()` for value type context and to populate `category: StateFlow<Category?>`
- Full form state mirroring `Event` fields
- `save()`: diffs image paths, calls `repository.saveEvent()`
- `deleteEvent()`: confirmation via `pendingDelete: StateFlow<Boolean>`, then `repository.deleteEvent()`
- **Stale event guard:** if `getEventById` returns null on init, sets `"snackbar_message"` on the previous back stack entry's `SavedStateHandle` and emits a navigate-back signal via `navigateBack: StateFlow<Boolean>`. The timeline screen observes `"snackbar_message"` on its own back stack entry and shows a snackbar on resume.
- `conversionOutcome: StateFlow<ConversionOutcome?>` — pre-computed result of `convertOrDefault`; null when no mismatch; drives banner visibility and the action button label (Converted → "Convert to X", UsedDefault → "Replace with default: X", Discard → "Discard value")
- `applyConversion()`: sets `value` to the outcome's value for Converted/UsedDefault, or null for Discard (only reachable when category type is None or Unknown); `isValueEditable` is set to true; banner clears reactively via `conversionOutcome` becoming null

## Image Handling

**Quick-log:** captures at most one image. The image file is written to `ImageStore.newFile()` at capture time. If the user dismisses the sheet without saving, `QuickLogViewModel.reset()` deletes the file.

**Event edit:** multiple images. Add via camera or gallery (each appended to `imagePaths`). Remove individually (tapping a remove button on the thumbnail). The repository diffs old vs. new paths on `saveEvent` and deletes removed files.

**Unsaved captures — edit screen:** `EventEditViewModel` tracks newly captured paths separately. On explicit cancel (back without save) or `onCleared()`, any newly captured path not present in the saved event's `imagePaths` is deleted via `ImageStore`. Process kill before `onCleared()` is the only gap — recovered by the startup orphan scan.

**Unsaved captures — quick-log sheet:** if the user captures an image then dismisses without saving, `QuickLogViewModel.reset()` deletes the file. Process kill during the sheet has the same gap — recovered by startup orphan scan.

## Navigation

```
Home (timeline)
    ├── [FAB]              → Quick-Log Sheet (bottom sheet)
    │       ├── [save]     → dismiss sheet, timeline refreshes via Flow
    │       └── [dismiss]  → reset(), delete any unsaved image
    └── [tap event]        → Event Edit
            ├── [save]     → back to Home
            ├── [delete]   → confirm → back to Home
            └── [back]     → cancel, clean up unsaved captures
```

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Quick-log flow | Two-step: category picker → value form | Single combined form; category pre-selected | Two-step keeps step 2 focused; pre-selection requires knowing the category in advance |
| Timeline grouping | ViewModel groups by `LocalDate` of `timestamp` | Repository returns pre-grouped; group in UI | ViewModel is the right layer — grouping is presentation logic, not storage logic; keeps the repository general |
| Default timestamp | `Instant.now()` at sheet open | Now at save time | User-editable timestamp should show the current time as its default from the moment the sheet opens, not when they tap save |
| `ErrorValue` / `Unknown` in edit | Read-only value field; other fields remain editable | Block editing entirely; allow raw edit | Preserving notes/timestamp/image editing is useful even when the value is unreadable; raw JSON edit is dangerous |
| Unsaved image cleanup | ViewModel tracks newly captured paths; cleans up on cancel / `onCleared()` | Rely on startup orphan scan | Startup scan is a safety net, not the primary path; prompt cleanup avoids accumulating stale files during a session; process-kill gap covered by scan |
| Category deleted during quick-log step 2 | `QuickLogViewModel` observes `categories`; drops `selectedCategory` and returns to step 1 if it disappears | Block deletion while sheet is open; crash | Reactive Flow already provides the signal; returning to step 1 is graceful and requires no special locking |
| Process-kill during image capture | Recovered by startup orphan scan | Transactional capture (not feasible) | File system and process lifecycle can't be made atomic; orphan scan is the standard recovery pattern already established in `local-storage` |
| `DayGroup` location | ViewModel-layer data class | Domain model; UI-only | Not a persistence concept; belongs to the presentation layer |
| Event delete UX | Swipe → immediate delete + in-place undo placeholder; window closes on next action | Confirmation dialog; undo snackbar | Events are deleted frequently — dialogs are disruptive; snackbars disappear on a timer which is easy to miss; in-place placeholder is visible and persistent until the user takes another action |
| Undo mechanism | Hard delete + restore via `saveEvent` | Soft delete (pending flag in DB) | No schema change needed; restore is a normal save; soft delete complicates all queries with a filter |
| Category filter UI | Horizontal chip row; single selection; "All" chip | Toolbar dropdown; drawer; no filter in v1 | Chip row keeps filters always visible and one tap away; single selection covers the common case without multi-select complexity |
| Filter + FAB interaction | Active filter pre-selects category in quick-log step 2 | Ignore filter; always show picker | If the user filtered to a category, they likely want to log to it — skipping the picker saves a tap |
| Scale widget | Horizontal slider with integer snap | Segmented row (10 buttons) | Slider conveys the continuous 1–10 range visually; segmented row adds tap-target complexity for 10 values |
| Boolean widget | Two-button row (Yes / No) | Toggle switch | Toggle is ambiguous about which state is "on"; two-button row is explicit and symmetric |
| Duration widget | Three separate H / M / S numeric fields | Single seconds field; HH:MM:SS text entry | Three fields allow independent editing of each component; single seconds field is unintuitive for durations > 60s |

## Open Questions & Future Decisions

### Deferred

1. **Duration input improvements** — current H / M / S fields are functional but could be improved: stopwatch capture, natural-language input ("1h 30m"), or single-number + unit picker are candidates.
2. **Boolean custom labels** — v1 uses "Yes" / "No" labels on the two-button row. Future: allow categories to specify custom label pairs (e.g., "Taken" / "Skipped" for medication, "Good" / "Bad" for mood).
3. **Timeline date range filtering** — the repository supports `start`/`end` bounds; no UI for it in v1. Could be added as a filter/search later.
4. **Multi-category filter** — v1 supports single-category filter only. Multi-select deferred.
5. **Editing a `None`-type event's value** — currently no input shown. If a category later changes `valueType` away from None, historical None events show no value; edit screen should probably show the new input type for those. Edge case deferred.
6. **Empty timeline state** — what the home screen shows when there are no events yet. Copy/illustration TBD.

## References

- `docs/llds/data-model.md` — `Event`, `Category`, `EventValue`, `ValueType`
- `docs/llds/local-storage.md` — `TrackrRepository` interface, `ImageStore`
- `docs/llds/category-management.md` — category list and edit flows (parallel segment)
- `docs/high-level-design.md` — three-tap goal, image capture sources
