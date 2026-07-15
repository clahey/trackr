# Event Logging

## Context and Design Philosophy

This segment covers the full event lifecycle from the user's perspective: logging a new event, viewing the timeline, and editing or deleting existing events. The HLD goal of "log any event in under three taps" is the primary constraint on the quick-log flow design.

The segment owns three screens (timeline, quick-log sheet, event edit) and their ViewModels. It consumes `TrackrRepository` and `ImageStore` but owns no persistence logic.

## Screens

### Home / Timeline Screen

The primary screen. Displays all events grouped by calendar day of `timestamp` (user's local timezone), most recent day first, most recent event first within each day.

Each event row is rendered by the shared `EventRow` composable (`ui/components/EventRow.kt`). Layout:
- **Left**: 48dp filled circle using the category color; category emoji centered inside with WCAG foreground color (see `docs/llds/theme.md § Circle avatar`)
- **Center**: category name (subtitle), value summary (formatted per `ValueType`), and notes (if any) stacked vertically
- **Right (before time)**: when `event.imagePaths` is non-empty, a 48×48dp square thumbnail of the first image, clipped to `RoundedCornerShape(4.dp)`, loaded via Coil `AsyncImage` from the absolute file path
- **Right**: time of day (from `timestamp`)

`EventRow` is extracted to `ui/components/` so it can be reused by the Category Edit screen's live preview (see `docs/llds/category-management.md § Category Edit Screen`). Its `onClick` parameter is nullable (`(() -> Unit)?`); when null the card renders as non-interactive (no ripple, no click affordance).

Supports:
- **Log new event** — FAB opens the Quick-Log Sheet
- **Edit event** — tap row → Event Edit screen
- **Delete event (swipe)** — swipe row to delete; row is replaced in place by an undo placeholder button
- **Delete event (edit screen)** — toolbar delete action with confirmation dialog (less common path)

Day headers show the date (e.g., "Today", "Yesterday", full date for older).

A horizontally scrollable row of category chips sits below the app bar. **Only `MetaCategory` (top-level) chips appear in this row**, plus an "All" chip first.

**Chip color:** each chip uses the category's `resolvedColor` — colored border when unselected; filled background with the category color when active.

**Two-level filter UX:**
- Tapping an inactive MetaCategory chip → activates that chip (filled background); filter covers that category **plus all its SubCategories'** events; that chip's SubCategory chips appear inline to the right of it in the same scrollable row, visually grouped (e.g., a subtle separator before them)
- Tapping a SubCategory chip → narrows filter to `ActiveFilter.Sub`; the MetaCategory chip returns to unselected style (border) but the subcategory chips remain visible; the tapped subcategory chip is shown as active (filled)
- Tapping the MetaCategory chip when a SubCategory of that meta is active → promotes filter to `ActiveFilter.TopLevel(meta)` (shows meta + all children); the MetaCategory chip becomes active (filled); all subcategory chips remain visible
- Tapping the active MetaCategory chip again (when `ActiveFilter.TopLevel`), or tapping "All" → clears to `All`; subcategory chips disappear
- Tapping a different MetaCategory chip while a SubCategory filter is active → switches to the new MetaCategory's filter (pre-filter scroll position is not updated per EL-UI-018)
- MetaCategories with no SubCategories: no subcategory chips appear when their chip is tapped

**Active filter state** is a sealed class in `HomeViewModel`:
```kotlin
sealed class ActiveFilter {
    data object All : ActiveFilter()
    data class TopLevel(val category: Category.MetaCategory) : ActiveFilter()
    data class Sub(val parent: Category.MetaCategory, val sub: Category.SubCategory) : ActiveFilter()
}
```

When `ActiveFilter.TopLevel` is active, the FAB quick-log sheet opens in an expanded subcategory picker for that MetaCategory (if it has subcategories) rather than advancing directly to step 2. When `ActiveFilter.Sub` is active, the quick-log opens directly to step 2 for the selected subcategory.

**Filter scroll behavior:** Day groups with no matching events are hidden when a filter is active. When a filter is applied (including switching between active filters), the timeline scrolls to keep the same calendar day approximately at the top (or the nearest earlier day that has matching events, if the current day has none). The pre-filter scroll position (top day) is recorded only on the transition from no filter to a filter — switching between active filters preserves the existing record so that clearing always returns to the original unfiltered position. When the filter is cleared, if the user has not manually scrolled since the filter was first applied, the timeline restores to the recorded pre-filter position. Manual scrolling (user-initiated only; not the system-initiated anchor scroll) discards the recorded position.

### Timeline Empty States

When the timeline has no rows to show, `HomeViewModel` exposes which of three empty states applies via `emptyState: StateFlow<TimelineEmptyState?>` (null when there is content), computed from the day groups, the category list, and the active filter:

- **`NoCategories`** (no categories exist): a welcome state offering **Add starter categories** (creates the starter set per CAT-UI-090; the timeline then lands on `NoEvents`, pointing at the FAB, rather than a picker appearing on its own) and **Create a category** (a plain trip to category creation — it does *not* set the reopen flag, so on return the timeline just reflects the new state: `NoEvents` if a category was created, the welcome state if cancelled).
- **`NoEvents`** (categories exist, nothing logged, no filter): "No events yet — tap + to log your first one." No category prompt — they already have categories; the FAB is the action.
- **`NoFilterMatch`** (a filter is active with no matching events): names the filtered category and offers **Clear filter** (`setFilter(All)`), so it never reads as though the whole app is empty.

`emptyState` is derived in the ViewModel (not the composable) so the branch logic is unit-tested directly. The filter chip row still renders above the empty view in the `NoEvents`/`NoFilterMatch` cases (EL-UI-093/094); it is naturally absent in `NoCategories` (no categories to chip).

### Quick-Log Sheet

A bottom sheet opened from the timeline FAB. Two-step flow to minimize taps:

**Step 1 — Category picker**
Grid of MetaCategory items (resolved emoji + name; colored border using the category's resolved color). Tapping a MetaCategory with no SubCategories advances directly to step 2. Tapping a MetaCategory with SubCategories **drills down**: the MetaCategory grid is replaced by a drill-down view showing a back button + the MetaCategory name as a header, a full-width "Log to [Name] directly" tile at the top, and the SubCategory tiles in the same 3-column grid below. Tapping the back button returns to the top-level grid. Tapping any tile advances to step 2 for that category.

When `ActiveFilter.TopLevel(meta)` is active and the filtered MetaCategory has SubCategories, the quick-log opens directly in the drill-down view for that MetaCategory, skipping the initial tap. When `ActiveFilter.TopLevel(meta)` is active and the filtered MetaCategory has no SubCategories, step 2 opens directly (existing behaviour). When `ActiveFilter.Sub` is active, step 2 opens directly for the subcategory.

**Inline category creation.** The top-level grid always ends with a persistent **"+ New category"** tile; the drill-down grid always ends with a **"+ New subcategory"** tile (creating a child of the expanded MetaCategory). On a fresh install with no categories, step 1 shows the "Choose a category" heading over a grid holding only the "+ New category" tile — the primary action (FAB → step 1) is never a dead end (EL-UI-090, EL-UI-091). Tapping "+ New category" navigates to Category Edit for a new top-level category; "+ New subcategory" navigates with `parentId` set to the expanded MetaCategory. Both first set `QuickLogViewModel.pendingCategoryCreate` so the sheet reopens on return; navigating away dismisses the sheet, but `QuickLogViewModel` survives on the back stack, preserving `expandedMetaCategoryId` (EL-NAV-020).

**Reopen after inline create.** The reopen intent (`pendingCategoryCreate`) is set **only** by the sheet's "+ New" tiles, so on return it reliably means "you came from the sheet" — no proxy like "do categories exist" is needed (the FAB can open the sheet even with zero categories, so that proxy would be wrong). `HomeScreen` reads it once per composition (a `LaunchedEffect(Unit)`, so it fires on return, not on the initiating tap). If Category Edit reported a new id (`created_category_id`, written synchronously before it popped — see `docs/llds/category-management.md § CategoryEditViewModel`), the sheet awaits that category appearing in `categories`, `selectCategory`s it, and opens at **step 2**; otherwise (cancel) it reopens at **step 1** with the preserved `expandedMetaCategoryId` restoring any drill-down context. The reported id is cleared on *every* return regardless of the flag: the welcome empty state's "Create a category" deliberately does **not** set the flag (it's a plain trip to category creation), so it returns straight to the timeline and its `created_category_id` is consumed here rather than lingering to mis-fire a later sheet create (EL-NAV-021).

**Step 2 — Value + details**
- Value input (see Value Input section below); for Number, Text, and Duration types the input field is automatically focused on entry so the keyboard rises without an extra tap
- Optional: single photo (camera or gallery picker)
- Optional: notes text field
- Timestamp: defaults to now; rendered via the shared `TimestampField` component (see `## Timestamp Field`)
- **Save** button — writes event and dismisses sheet; scrolling the timeline to the newly saved event is deferred (EL-UI-077)

For `ValueType.None` categories, step 2 has no value input — save is immediately accessible, achieving the three-tap goal (FAB → category → save).

### Event Edit Screen

Full edit view for an existing event. Toolbar contains a **Delete** action.

Read-only header:
- Category emoji and name — displayed above the editable fields; if the category cannot be resolved (orphaned event), the header is omitted.

Editable fields:
- Timestamp — rendered via the shared `TimestampField` component (see `## Timestamp Field`); disabled (non-interactive) on the pager's prev/next preview pages, matching the existing `readOnly` treatment of the other fields on those pages
- Value (see Value Input section)
- Notes
- Images (add via camera or gallery; remove individually; no cap on count)

Save navigates back to timeline. Delete shows a confirmation dialog, then deletes and navigates back.

**Back navigation:** both the hardware back button (`BackHandler`) and the navigation icon check `isDirty` before navigating. When dirty, a Save / Discard / Cancel dialog is shown. Save calls `viewModel.save()` (persists and navigates back via `SaveResult.Success`). Discard calls `viewModel.cancel()` (cleans up newly captured images) then `onNavigateBack(null)`. Cancel dismisses the dialog. When not dirty, both navigate back immediately. The dialog state is local to the screen (`showBackDiscardDialog`) because the post-action callback `onNavigateBack` is screen-level.

**Swipe navigation:** the event edit screen is a `HorizontalPager` whose pages are the events matching the filter active when the screen was opened (timestamp DESC, createdAt DESC, id ASC — same order as the timeline). Swiping left moves to the next older event; swiping right moves to the next newer event. At the list edges, the pager rubber-bands with a dark background visible beyond the edge.

The filter context (`filterCategoryId: String?`) is passed as an optional navigation argument:
- `null` → all events (`getEvents()`)
- MetaCategory id → `getEventsByCategoryIdIncludingChildren(id)` (TopLevel filter)
- SubCategory id → `getEventsByCategoryIdIncludingChildren(id)` (Sub filter, consistent with HomeViewModel)

**Unsaved changes on swipe:** `EventEditViewModel` tracks an `isDirty` flag set whenever any field is edited. When `isDirty` is true, `pageCount` is set to 1 so the pager rubber-bands naturally on any swipe attempt. When the user releases, a prompt offers Save / Discard / Cancel. Both Save and Discard resolve the changes in place without navigating — `isDirty` clears and `pageCount` returns to N, leaving the user free to swipe afterward. Discard runs the same newly-captured-image cleanup as EL-PROC-002.

## Timestamp Field

A shared composable (`ui/components/TimestampField.kt`) used by both the quick-log sheet and the event edit screen. Renders the current `Instant` (converted to local date/time via `ZoneId.systemDefault()`) as two independently-tappable read-only sub-fields side by side: Date and Time. Takes `timestamp: Instant`, `onTimestampChange: (Instant) -> Unit`, and `enabled: Boolean = true`.

Each sub-field is styled with a trailing dropdown chevron (`ExposedDropdownMenuDefaults.TrailingIcon`, the standard M3 dropdown-affordance icon, rotated while its dialog is open) so the fields read as tap-to-pick selectors rather than free-text inputs, even though they open a dialog rather than an actual anchored dropdown menu. The entire field rectangle is one click target: each field is a `Box` with the `OutlinedTextField` underneath and a transparent click-catcher `Box` (`Modifier.matchParentSize().clickable { ... }`) layered on top, since `OutlinedTextField`'s own internal pointer-input handling for the text/content area otherwise swallows clicks before a `clickable()` on the field's own modifier would see them — without the overlay, only the label text reliably registers taps.

**Tapping the date** opens a `DatePickerDialog` (Material3, native — no third-party dependency) seeded to the current date. Confirm updates only the date component, keeping the existing time unchanged; dismiss (tap outside or Cancel) leaves the timestamp unchanged. Date and time are edited independently — picking a date never opens or affects the time, and vice versa (an earlier design chained date confirm into a time picker; removed per user feedback after using the real picker — independent editing matched expectations better).

**Tapping the time** opens a custom `Dialog` wrapping `TimePicker` (M3 has no built-in dialog wrapper for `TimePicker`/`TimeInput`, unlike `DatePicker`), seeded to the *current* timestamp's time. Confirm updates only the time component, keeping the existing date. Dismiss leaves the timestamp unchanged.

**Implementation note — M3 `DatePicker` UTC boundary:** `DatePickerState.selectedDateMillis` represents UTC midnight of the selected date, not a local-zone instant. Converting the selected millis to a `LocalDate` must go through `ZoneOffset.UTC`, not `ZoneId.systemDefault()`, or the date can shift by one day near timezone boundaries (e.g. for negative UTC-offset zones in the evening). The final combined date+time is converted to the stored `Instant` via `ZoneId.systemDefault()` as normal — only the picker's own boundary needs the UTC-specific handling.

**Enabled/disabled:** the event edit screen's pager prev/next preview pages pass `enabled = false`, making the field fully non-interactive (no dialogs open on tap), consistent with those pages' read-only treatment of the other fields.

## Value Input by ValueType

| ValueType | Input widget | Notes |
|---|---|---|
| `None` | — | No input; field omitted |
| `Scale` | Horizontal slider | Integer snap 1–10 |
| `Boolean` | Two-button row | Labeled "Yes" / "No" in v1; custom labels deferred (see Open Questions) |
| `Number` | Numeric text field + separate unit text field | Unit field pre-filled from `Category.resolvedDefaultValue` (as `NumberValue.unit`); user-editable; empty saves as null (unitless) |
| `Text` | Multi-line text field | Empty string gated by `Category.allowEmptyText` |
| `Duration` | Three separate numeric fields (H / M / S) | Domain type is `kotlin.time.Duration`; fields decomposed via `.toComponents` |
| `Exercise` | Two integer-only fields (Sets / Reps) | Defaults from `Category.resolvedDefaultValue` (as `ExerciseValue.sets`/`.reps`); both ≥ 1 required to save; formatted as "$sets × $reps" in the timeline |
| `Unknown(raw)` | Read-only display of raw value | Cannot edit — type unknown to this version |
| `ErrorValue` | Read-only display with error indicator | Shows `ErrorKind` and raw string; cannot edit |

`Unknown` and `ErrorValue` are read-only in both Quick-Log (these categories/events won't normally appear in the picker or edit flow) and Event Edit. An event carrying `ErrorValue` can still have its notes, timestamp, and images edited.

### ValueUIState

The value editing exchange type between ViewModels and `ValueInputField`. Lives in `ui/components/ValueUIState.kt` alongside `ValueInputField`. ViewModels hold `MutableStateFlow<ValueUIState>`.

```kotlin
sealed class ValueUIState {
    data object None : ValueUIState()
    data class Number(val text: String, val unit: String) : ValueUIState()
    data class Text(val text: String) : ValueUIState()
    data class Scale(val value: Int) : ValueUIState()
    data class Bool(val selected: Boolean?) : ValueUIState()
    data class Duration(val hoursText: String, val minutesText: String, val secondsText: String) : ValueUIState()
    data class Exercise(val setsText: String, val repsText: String) : ValueUIState()
    data class ReadOnly(val displayText: String) : ValueUIState()
    data class Mismatched(
        val originalValue: EventValue,
        val targetType: ValueType,          // stored so outcome can be recomputed as editableState changes
        val editableState: ValueUIState?,   // null for ErrorValue / Discard outcomes
    ) : ValueUIState() {
        // computed so the banner label always reflects the current editable value
        val outcome: ConversionOutcome get() {
            val ev = editableState?.toEventValue()
            return when {
                ev != null -> convertOrDefault(ev, targetType)
                editableState != null -> // partial/invalid input — offer default, not a conversion of originalValue
                    defaultForType(targetType)?.let { ConversionOutcome.UsedDefault(it) } ?: ConversionOutcome.Discard
                else -> convertOrDefault(originalValue, targetType)
            }
        }
    }
}
```

**Conversion helpers** in `ui/components/ValueUIState.kt`:

- `EventValue.toValueUIState(): ValueUIState` — maps each concrete variant to its `ValueUIState` counterpart. `ErrorValue` and `Unknown` category path → `ReadOnly(formatValue(this))`.
- `EventValue?.toValueUIState(valueType: ValueType): ValueUIState` — entry point used by ViewModels on load. If `this` is null, returns `defaultValueUIStateForType(valueType)` — for `None` type this is `ValueUIState.None`; for any other type it shows the default empty input so the user can enter a value. If `matchesValueType(this, valueType)`, returns `toValueUIState()`. Otherwise builds `Mismatched(originalValue=this, targetType=valueType, editableState=editableStateFor(this, valueType))`.
- `editableStateFor(value: EventValue, valueType: ValueType): ValueUIState?` — returns `null` when `value` is `ErrorValue` or when `convertOrDefault` yields `Discard`; otherwise `value.toValueUIState()`.
- `ValueUIState.toEventValue(): EventValue?` — converts UI state to an `EventValue` for persistence. Returns `null` for invalid or partial input (e.g., empty or non-numeric Number text). For `Mismatched`, returns `editableState?.toEventValue() ?: originalValue` (preserves the original uneditable value when `editableState` is null).
- `defaultValueUIStateForType(type: ValueType): ValueUIState` — fallback default state used when `category.resolvedDefaultValue` is null: `None`→`None`; `Number`→`Number("", "")` ; `Text`→`Text("")`; `Scale`→`Scale(5)`; `Boolean`→`Bool(null)` (both buttons unpressed; saving blocked until a selection is made); `Duration`→`Duration("", "", "0")` (hours and minutes empty, seconds "0", ensuring at least one non-empty field); `Exercise`→`Exercise("3","15")`; `Unknown`→`ReadOnly("")`.
- `durationToUIState(hours: Long, minutes: Int, seconds: Int): Duration` — computes display strings: `hoursText = if (hours > 0) hours.toString() else ""`; `minutesText = if (hours > 0 || minutes > 0) minutes.toString() else ""`; `secondsText = seconds.toString()` (always shown, even as `"0"`). Leading-zero components are elided as empty; once a non-zero component is encountered all smaller components are displayed (including as `"0"`). Used by `EventValue.DurationValue.toValueUIState()`.
- `ValueUIState.matchesType(type: ValueType): Boolean` — returns true when the `ValueUIState` variant is consistent with `type` (e.g., `Scale` matches `ValueType.Scale`; `None` matches `None` and `Unknown`). Parallel to `matchesValueType` for `EventValue`. Used by `selectCategory` to detect same-type switches without going through `toEventValue()`.
- `validateValueForSave(value: ValueUIState, category: Category): String?` — shared save-validation helper used by both ViewModels. Returns the failing field name (`"value"`) or null if valid. Two checks in order: (1) if `value.toEventValue()` is null and `value` is not `ValueUIState.None`, the input is invalid or partial → return `"value"`; (2) if `category.resolvedValueType` is `Text` and `!category.allowEmptyText` and `value.toEventValue()` is an empty `TextValue` → return `"value"`. All other states are valid (including `None` with null result, `Duration` with all-empty fields yielding zero, etc.).

**`ValueInputField` signature:**
```kotlin
@Composable
fun ValueInputField(
    uiState: ValueUIState,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean = false,
)
```
`defaultUnit` and `valueType` parameters are eliminated. Unit is carried in `ValueUIState.Number.unit` (seeded by the ViewModel at state creation). The widget dispatches on the concrete `ValueUIState` variant, not on a separate `valueType`. Partial and intermediate text (e.g., a trailing decimal point, empty Number field, empty Duration component) is stored verbatim in the state — the widget never silently replaces user input.

- **Number:** an empty or non-parseable text field produces `null` from `toEventValue()`; validation blocks save (EL-UI-052b unchanged).
- **Bool(null):** renders both buttons unpressed; `toEventValue()` returns `null`; validation blocks save until a selection is made.
- **Duration:** an empty component field is treated as `0` by `toEventValue()`; only the `seconds` field is guaranteed non-empty in default state.

### ValueType change

When a category's `valueType` is changed, `TrackrRepository.saveCategoryAndMigrateEvents` migrates historical events in a single transaction using `convertEventValue`. The conversion is best-effort: values that have a defined conversion path (e.g., `Scale` → `Number`, parseable `Text` → `Scale`) are converted; values with no path are left unchanged in the database, creating a mismatch between the event's stored value type and the category's current type.

Mismatches can also arise when the app reads data from a newer app version — an `Unknown` category type or an `ErrorValue`. The event-logging UI is responsible for detecting and surfacing these cases.

### Value type mismatch

Mismatch detection occurs at the ViewModel layer (not inside `ValueInputField`). When an event is loaded, `EventEditViewModel` calls `event.value.toValueUIState(category.resolvedValueType)`, which produces `ValueUIState.Mismatched` whenever `matchesValueType` returns false.

**`matchesValueType(value: EventValue?, type: ValueType): Boolean`** — domain-layer helper. Returns `true` only when the value's runtime type is the expected variant for `type`, or when an `ErrorValue` with `inferredType` matches an `Unknown` category's raw string (coherent future-type pair — both from the same unrecognized type). Returns `false` for: `ErrorValue` without a matching `Unknown` type, `Unknown` category type without a matching `ErrorValue`, `None` type with non-null value, or a concrete value of the wrong type. `null` on `None` type returns `true`.

**`convertOrDefault(value: EventValue, targetType: ValueType): ConversionOutcome`** — domain layer. Returns a sealed class with three cases:
- `ConversionOutcome.Converted(value: EventValue)` — `convertEventValue` produced a value of the right type (genuine conversion from the stored data).
- `ConversionOutcome.UsedDefault(value: EventValue)` — conversion failed, no path exists, or the value is `ErrorValue`; `defaultForType(targetType)` is used instead.
- `ConversionOutcome.Discard` — target type is `None` or `Unknown`; the right action is to clear the event value.

**Timeline display:** events with `!matchesValueType(event.value, category.valueType)` display the raw stored value via the existing `formatValue` followed by a warning icon. No DB write on view.

**Value action banner (inside `ValueInputField`):** shown when `uiState is ValueUIState.Mismatched`. Contains:
- A short description: *"Stored value doesn't match the category type."*
- An action button whose label depends on `outcome`, using `describeValue(v: EventValue): String` for the value descriptions (a UI-layer function that produces verbose human-readable text — e.g., `"2 sets × 5 reps"` for `ExerciseValue(2, 5)`, `"7/10"` for `Scale(7)`, `"Yes"` for `BooleanValue(true)`, `"3.5 kg"` for `NumberValue(3.5, "kg")`):
  - `Converted(v)`: **"Convert to [describeValue(v)]"**
  - `UsedDefault(v)`: **"Replace with default: [describeValue(v)]"**
  - `Discard`: **"Discard value"**
  - Tapping calls `onStateChange`: for `Converted(v)` or `UsedDefault(v)`, with `v.toValueUIState()`; for `Discard`, with `ValueUIState.None`.
- The banner is informational — the Save button remains enabled while the banner is visible. If the user saves without resolving the banner, `toEventValue()` on the `Mismatched` state persists the edited (or original) value unchanged with respect to type conversion.
- While the banner is shown, `ValueInputField` renders the `editableState` sub-field so the user can refine the existing value (e.g., edit a TextValue to "Yes" before accepting a Boolean conversion) before tapping the action button. For `ErrorValue` and for `Discard` outcomes (None or Unknown target), `editableState` is null and no editable input is shown; the banner action is the only resolution path. When the user edits the sub-field, `ValueInputField` wraps the callback: `onStateChange(mismatched.copy(editableState = newSub))` — the `Mismatched` wrapper is preserved so the banner remains visible while the sub-value is being refined.

## ViewModels

### HomeViewModel

- `activeFilter: StateFlow<ActiveFilter>` — current filter state (`All`, `TopLevel`, or `Sub`); see sealed class above
- `dayGroups: StateFlow<List<DayGroup>>` — derived from `repository.getEventsByCategoryIdIncludingChildren(id)` when a filter is active (`TopLevel` uses MetaCategory id, `Sub` uses SubCategory id), `repository.getEvents()` otherwise; grouped by calendar day of `timestamp` in local timezone; events with no matching category in the current category list are omitted (handles the brief `combine()` window where the two flows are out of sync after a deletion)
- `preFilterTopDay: StateFlow<LocalDate?>` — the calendar day that was at the top of the timeline when a filter was first applied (transition from no filter); null if no filter is active or if the user has manually scrolled since the filter was first applied. Not updated when switching between active filters.
- `setFilter(filter: ActiveFilter)` — sets `activeFilter`; records the current top day in `preFilterTopDay` only when transitioning from `All` to a non-`All` filter; switching between two non-`All` filters preserves the existing `preFilterTopDay`; `All` clears the filter
- `onUserScrolled()` — called by the UI when the user manually scrolls; clears `preFilterTopDay`
- `pendingDelete: StateFlow<Event?>` — the event swiped away but not yet committed; null when no undo is available
- `swipeDelete(event: Event)` — calls `repository.deleteEvent()` (DB row only; files are not deleted yet); stores the full `Event` in `pendingDelete` and injects an undo placeholder into the day group at the event's original position; if a previous `pendingDelete` exists, calls `repository.deleteEventFiles()` for it first
- `undoDelete()` — calls `repository.saveEvent(pendingDelete!!)` to restore (image files were never deleted); clears `pendingDelete`
- `clearPendingDelete()` — called internally before any other mutating action (new log, another delete, open event); calls `repository.deleteEventFiles(pendingDelete!!.imagePaths)` then discards `pendingDelete`

**Active filter guard:** `HomeViewModel` observes the category list. If the active filter references a deleted category, the filter clears to `All` regardless of whether the deleted category is a MetaCategory or SubCategory.

**Undo window closes when any of these occur:**
- Another event is swiped to delete
- A new event is logged (sheet saves)
- An event is opened for editing

The DB delete fires immediately on swipe — undo is a restore (`saveEvent`) not a rollback. Image file deletion is deferred to `clearPendingDelete()` so that undo can restore the event with its images intact. The placeholder occupies the deleted event's original position in the day group until `pendingDelete` is cleared.

`DayGroup` is a ViewModel-layer data class (not a domain model):
```kotlin
data class DayGroup(val date: LocalDate, val events: List<DayEntry>)

sealed class DayEntry {
    data class Entry(val event: Event, val category: Category) : DayEntry()
    data class UndoPlaceholder(val event: Event) : DayEntry()
}
```

### QuickLogViewModel

- `categories: StateFlow<List<Category>>` — from `repository.getCategories()`
- `selectedCategory: StateFlow<Category?>` — set when user picks in step 1
- `expandedMetaCategoryId: MutableStateFlow<String?>` — which MetaCategory is shown in the drill-down view; null = top-level grid is shown
- Form state: `timestamp`, `value: MutableStateFlow<ValueUIState>`, `notes`, `imagePath` (single, nullable)
- `timestamp` defaults to `Instant.now()` at sheet open; user-editable
- `value` is initialized to `ValueUIState.None`; updated to the type-appropriate default when a category is selected (see `selectCategory` below)
- `valueDirty: MutableStateFlow<Boolean>` — tracks whether the user has interacted with the value input during this sheet session. Set to `false` on init and `reset()`; set to `true` when the UI calls `updateValue()`. Never reset on `selectCategory` — edits persist across back-and-forth category navigation within one session.
- `updateValue(state: ValueUIState)`: sets `value.value = state` and `valueDirty.value = true`. The UI calls this instead of writing to `value` directly, so dirty tracking is centralised.
- `selectCategory(category)`: sets `selectedCategory`; does **not** clear `expandedMetaCategoryId`, so pressing back from step 2 restores the drill-down view if the user arrived via one. Then:
  1. If `!valueDirty` → seed per EL-UI-078: if `resolvedDefaultValue` is non-null and `matchesValueType(resolvedDefaultValue, resolvedValueType)` is true, use `resolvedDefaultValue.toValueUIState()`; else use `defaultValueUIStateForType(targetType)` (user hasn't typed anything this session).
  2. Unwrap `Mismatched`: if `editableState` is non-null use it as `effectiveState`; if `editableState` is null, call `originalValue.toValueUIState()` to reconstruct an effective state from the stored EventValue (preserves the value across a Discard pass-through, though intermediate text precision may normalize, e.g. "75" → "75.0").
  3. If `effectiveState` is `None` → seed per EL-UI-078 as in step 1.
  4. If `effectiveState.matchesType(targetType)` → use `effectiveState` verbatim (preserves in-progress text without snap-back). For `Number`, variant match is sufficient — unit field is not compared.
  5. Otherwise: `ev = effectiveState.toEventValue()`. If null (partial/invalid input such as `Bool(null)`) → seed per EL-UI-078 as in step 1. If non-null → `Mismatched(originalValue=ev, targetType=targetType, editableState=editableStateFor(ev, targetType))`.
  `ValueUIState.matchesType(ValueType): Boolean` — parallel to `matchesValueType`; `ReadOnly` and `Mismatched` return false for all types.
- `save()`: calls `validateValueForSave(value.value, category)`; if it returns a field name, emits `SaveResult.ValidationError` and returns. Otherwise calls `value.value.toEventValue()`, generates a UUID, writes image to `ImageStore.newFile()` if present, calls `repository.saveEvent()`, clears `imagePath` to null (transferring ownership of the file to the repository), emits `SaveResult.Success`.
- `reset()`: called when sheet is dismissed (with or without saving); clears all form state — including `value` back to `ValueUIState.None`, `valueDirty` back to `false`, and `saveResult` back to `Idle` — and deletes any captured-but-unsaved image file (if `imagePath` is still non-null, meaning `save()` did not run)
- **Deleted category guard:** observes `categories`; if `selectedCategory` is no longer present in the list (deleted externally while sheet is open), resets `selectedCategory` to null and returns the user to step 1. If a MetaCategory is deleted while the user is in its drill-down view, the view collapses back to the top-level grid.
- `pendingCategoryCreate: StateFlow<Boolean>` — reopen intent, set by `beginCategoryCreate()` when the user taps a "+ New category"/"+ New subcategory" tile, before `HomeScreen` navigates to Category Edit. `consumePendingCategoryCreate(): Boolean` atomically reads and clears it; `HomeScreen` calls it once per composition to detect a return from the create excursion. It lives on the ViewModel (not `HomeScreen` local state) because the ViewModel outlives the disposed-and-recomposed `HomeScreen` across the navigation round-trip (EL-NAV-020, EL-NAV-021). Not touched by `reset()` — an in-flight create intent must survive a sheet dismissal.

### EventEditViewModel

- Loads event by ID via `repository.getEventById()`; also loads its category via `repository.getCategoryById()` for value type context and to populate `category: StateFlow<Category?>`
- Form state (`timestamp`, `value`, `notes`, `imagePaths`) is exposed as read-only `StateFlow`s backed by private `MutableStateFlow`s. Mutations go through `setValue(ValueUIState)`, `setNotes(String)`, `addImage(path)`, and `removeImage(path)` — each sets `isDirty` automatically. `isDirty: StateFlow<Boolean>` is cleared by save and discard operations.
- On load: initializes `value` by calling `event.value.toValueUIState(category.resolvedValueType)`, which produces `ValueUIState.Mismatched` when the stored value does not match the category type. Stores `originalEvent` and `originalImagePaths` for discard and cleanup.
- `save()`: delegates to `performSave()`; on success emits `SaveResult.Success` to trigger navigate-back.
- `saveInPlace()`: delegates to `performSave()` without emitting `SaveResult.Success`; clears `showDiscardDialog`; used when saving from the unsaved-changes swipe dialog so changes are committed without navigating.
- `performSave()` (private): calls `validateValueForSave(value.value, category)` (category may be null — treat as no validation); if invalid, emits `SaveResult.ValidationError` and returns false. Otherwise calls `repository.saveEvent()`, updates `originalEvent` and `originalImagePaths`, clears `isDirty`, returns true.
- `discardInPlace()`: deletes any images added during this edit session (present in `imagePaths.value` but not in `originalImagePaths`); calls `restoreFormFields(originalEvent)` to revert all form fields; clears `isDirty` and `showDiscardDialog`.
- `restoreFormFields(event: Event)` (private): synchronously restores `_timestamp`, `_notes`, `_imagePaths`, and `_value` from the given event, using the already-loaded `_category` for value type resolution. Used by both `loadEventData` and `discardInPlace` to avoid duplication.
- `showDiscardDialog: StateFlow<Boolean>` — set by `scrollEnded()` when dirty; cleared by `saveInPlace()`, `discardInPlace()`, and `dismissDiscardDialog()`. Owned by the ViewModel so the dirty check happens against live state with no stale-closure risk.
- `scrollEnded()`: called by the screen when a pager scroll ends; sets `showDiscardDialog` if `isDirty`.
- `pageSettled(page: Int)`: called by the screen when the pager settles on a new page; if not dirty, computes `delta = page - currentIndex.value` and calls `navigateToAdjacent(delta)`.
- `dismissDiscardDialog()`: clears `showDiscardDialog` (Cancel action).
- `deleteEvent()`: confirmation via `pendingDelete: StateFlow<Boolean>`, then `repository.deleteEvent()` (DB row) followed by `repository.deleteEventFiles(imagePaths)` (no undo path here)
- **Stale event guard:** if `getEventById` returns null on init, emits a navigate-back signal via `navigateBack: StateFlow<Boolean>`; the UI layer invokes `onNavigateBack(errorMessage)` with a non-null message, which the caller (timeline) displays as a snackbar.
- Mismatch detection is owned by `EventEditViewModel` (embedded in `ValueUIState.Mismatched` on load); `ValueInputField` renders the banner and calls `onStateChange` to resolve it. The ViewModel does not expose a separate `conversionOutcome` or `applyConversion()` method.
- **Swipe navigation:** `filterCategoryId: String?` (from nav arg) scopes the event list. `_events: StateFlow<List<Event>>` (private) is the source of truth — populated from `getEvents()` (no filter) or `getEventsByCategoryIdIncludingChildren(id)` (filtered), ordered timestamp DESC. `eventIds: StateFlow<List<String>>` and `currentIndex: StateFlow<Int>` derive from `_events`. `prevEventState: StateFlow<EventDisplayState?>` and `nextEventState: StateFlow<EventDisplayState?>` index directly into `_events` for the adjacent event objects and fetch only their categories — no per-event re-fetch. `navigateToAdjacent(delta: Int)` reads `_events.value[newIndex]` directly.

## Image Handling

**Image rendering:** `io.coil-kt:coil-compose` is the image loading library. `AsyncImage` is used wherever a local file path must be rendered as a bitmap; pass the path as `File(path).toUri()` to produce an explicit `file://` URI. Coil handles background decoding, memory caching, and error states (shows nothing when the file is missing — tolerated, recovered by startup orphan scan).

**FileProvider** — camera capture requires sharing a `file://` URI with the system camera app via `FileProvider`. The app declares a `FileProvider` in `AndroidManifest.xml` (authority `${packageName}.fileprovider`) backed by `res/xml/file_paths.xml` exposing `context.filesDir`. No `CAMERA` permission is required on Android 10+ when delegating to the system camera via `TakePicture` intent.

**Camera capture flow (both screens):**
1. Create file: `imageStore.newFile()` → hold absolute path as the pending camera path in the ViewModel
2. Convert to FileProvider URI: `FileProvider.getUriForFile(context, authority, File(path))`
3. Launch `TakePicture` contract
4. Result `true` → commit path to image state; result `false` → delete file, discard path

**Gallery flow:** `PickVisualMedia` returns a content URI. Copy to `imageStore.newFile()` via `contentResolver.openInputStream(uri)`. Store the resulting absolute file path — never store a content URI (violates DM-DATA-034; does not survive process restart). If the copy fails, delete the destination file and surface no image added.

**Quick-log:** `imagePath: MutableStateFlow<String?>` holds the single image path (null = no image). While null, show an "Add image" button that opens a dialog with "Take photo" and "Choose from gallery". While non-null, display the captured photo as a full-width thumbnail (160dp tall, `ContentScale.Crop`, `RoundedCornerShape(8.dp)`, loaded via Coil `AsyncImage`) with a **Remove** button (deletes file, clears `imagePath`) and a **Replace** button (opens picker; on selection, deletes old file and sets new path) below. `reset()` deletes and clears `imagePath` if still non-null (i.e., if `save()` did not already clear it). For camera, `imagePath` doubles as the pending camera path — if camera result is `false`, delete and clear it.

**Event edit:** `pendingCameraPath: MutableStateFlow<String?> = null` tracks the file created before launching the camera. On camera result `true`, append `pendingCameraPath` to `imagePaths` and clear it. On result `false`, delete and clear. `cancel()` and `onCleared()` delete `pendingCameraPath` (if non-null) in addition to new images not in `originalImagePaths`. Gallery picks are copied to app-private storage then appended to `imagePaths` directly (no pending state needed). The "Add image" button is always visible. Each image in `imagePaths` is rendered as a full-width thumbnail (160dp tall, `ContentScale.Crop`, `RoundedCornerShape(8.dp)`, loaded via Coil `AsyncImage`) with a remove `IconButton` overlaid in the top-right corner.

**Unsaved captures — edit screen:** `EventEditViewModel` tracks newly captured paths separately. On explicit cancel (back without save) or `onCleared()`, any newly captured path not present in the saved event's `imagePaths` is deleted via `ImageStore`. Process kill before `onCleared()` is the only gap — recovered by the startup orphan scan.

**Unsaved captures — quick-log sheet:** if the user captures an image then dismisses without saving, `QuickLogViewModel.reset()` deletes the file. Process kill during the sheet has the same gap — recovered by startup orphan scan.

## Navigation

```
Home (timeline)
    ├── [FAB]              → Quick-Log Sheet (bottom sheet)
    │       ├── [save]     → dismiss sheet, timeline refreshes via Flow
    │       ├── [dismiss]  → reset(), delete any unsaved image
    │       ├── [+ New category]           → Category Edit (new top-level) → reopen sheet on return
    │       ├── [+ New subcategory]        → Category Edit (new sub, parentId) → reopen sheet on return
    │       ├── [back in step 2]           → step 1 (selectedCategory = null; expandedMetaCategoryId preserved → drill-down if present)
    │       └── [back in step 1 drill-down] → top-level grid (expandedMetaCategoryId = null)
    └── [tap event]        → Event Edit
            ├── [save]                        → back to Home
            ├── [delete]                      → confirm → back to Home
            ├── [back]                        → cancel, clean up unsaved captures
            ├── [swipe, clean state]          → adjacent event (slide animation)
            └── [swipe attempt, dirty state]  → rubber-band → Save / Discard / Cancel dialog (resolves in place, no navigation)
```

System back and edge swipe are intercepted by `BackHandler` within the sheet: step 2 intercepts first (returns to step 1), then drill-down (returns to top-level grid); step 1 top-level lets the sheet's native dismiss handler take over.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Quick-log flow | Two-step: category picker → value form | Single combined form; category pre-selected | Two-step keeps step 2 focused; pre-selection requires knowing the category in advance |
| Timeline grouping | ViewModel groups by `LocalDate` of `timestamp` | Repository returns pre-grouped; group in UI | ViewModel is the right layer — grouping is presentation logic, not storage logic; keeps the repository general |
| Default timestamp | `Instant.now()` at sheet open | Now at save time | User-editable timestamp should show the current time as its default from the moment the sheet opens, not when they tap save |
| `ErrorValue` / `Unknown` in edit | Read-only value field; other fields remain editable | Block editing entirely; allow raw edit | Preserving notes/timestamp/image editing is useful even when the value is unreadable; raw JSON edit is dangerous |
| Unsaved image cleanup | ViewModel tracks newly captured paths; cleans up on cancel / `onCleared()` | Rely on startup orphan scan | Startup scan is a safety net, not the primary path; prompt cleanup avoids accumulating stale files during a session; process-kill gap covered by scan |
| Gallery storage | Copy content URI to app-private file at pick time | Store content URI string | Content URIs are not guaranteed to survive process restart; DM-DATA-034 requires absolute file-system paths |
| Camera permission | No explicit `CAMERA` permission; use `TakePicture` intent | Request `CAMERA` permission | `TakePicture` delegates to system camera which already holds the permission; avoids a runtime permission prompt |
| Quick-log image UI (photo present) | Photo indicator + Remove button + Replace button | Remove only; Replace only (re-tap Add) | Remove-only requires two taps to change photo; re-tapping Add to replace is not discoverable; explicit Remove + Replace is clear and efficient |
| Category deleted during quick-log step 2 | `QuickLogViewModel` observes `categories`; drops `selectedCategory` and returns to step 1 if it disappears | Block deletion while sheet is open; crash | Reactive Flow already provides the signal; returning to step 1 is graceful and requires no special locking |
| Process-kill during image capture | Recovered by startup orphan scan | Transactional capture (not feasible) | File system and process lifecycle can't be made atomic; orphan scan is the standard recovery pattern already established in `local-storage` |
| `DayGroup` location | ViewModel-layer data class | Domain model; UI-only | Not a persistence concept; belongs to the presentation layer |
| Event delete UX | Swipe → immediate delete + in-place undo placeholder; window closes on next action | Confirmation dialog; undo snackbar | Events are deleted frequently — dialogs are disruptive; snackbars disappear on a timer which is easy to miss; in-place placeholder is visible and persistent until the user takes another action |
| Undo mechanism | Hard delete + restore via `saveEvent` | Soft delete (pending flag in DB) | No schema change needed; restore is a normal save; soft delete complicates all queries with a filter |
| Category filter UI | Horizontal chip row; single selection; "All" chip | Toolbar dropdown; drawer; no filter in v1 | Chip row keeps filters always visible and one tap away; single selection covers the common case without multi-select complexity |
| Filter + FAB interaction | Active filter pre-selects category in quick-log step 2 | Ignore filter; always show picker | If the user filtered to a category, they likely want to log to it — skipping the picker saves a tap |
| First-run empty state | Persistent "+ New category" tile in the step-1 grid, shown for everyone | Dedicated empty-state screen; disable the FAB until a category exists | One affordance serves both first-run and returning users; keeps the primary action live on a fresh install; no first-run/returning mode split to maintain |
| Post-create landing | Reopen the sheet pre-selected at step 2; a cancelled create reopens at step 1 | Return to the timeline (tap FAB again); always reopen at step 1 | Keeps the user in the logging flow they started; fewest taps from "I need a category" to logging against it |
| Reopen-intent signal | `pendingCategoryCreate` flag on the surviving ViewModel, read once per `HomeScreen` composition; new category id carried separately via the nav result | Drive reopen purely off the `created_category_id` nav result | A nav result only exists for the create case; the flag also reopens on cancel. Reading it per-composition (not via an effect keyed on the flag) fires it on return, not on the initiating tap, sidestepping the set-then-navigate race |
| Timeline empty states | Three distinct states (`NoCategories` / `NoEvents` / `NoFilterMatch`) derived in `HomeViewModel` | One generic "nothing here" view; compute in the composable | First-run, empty-but-set-up, and filtered-no-match want different copy and actions — a generic view either nags set-up users or reads as broken under a filter. Deriving in the VM keeps the branch logic unit-testable |
| First-run starter path | "Add starter categories" seeds the set, then the timeline lands on the no-events state (points at the FAB) | Open the log sheet right after seeding; auto-create a "day zero" milestone event | A picker opening on its own reads as appearing from nowhere; landing on the no-events state teaches the FAB, which is the durable gesture. A milestone event would need a synthetic "Trackr" category that clutters the filter/quick-log UI and injects data the user didn't log |
| Category-create entry points | Two: the in-sheet "+ New" tiles (set the reopen flag → return to the sheet) and the welcome "Create a category" (plain nav, no flag, no auto-log) | One shared path that always reopens/pre-selects | The welcome button has no sheet to return to and shouldn't auto-start logging; letting only the sheet tiles set the flag makes the flag itself the origin signal and keeps the welcome path a plain there-and-back |
| Scale widget | Horizontal slider with integer snap | Segmented row (10 buttons) | Slider conveys the continuous 1–10 range visually; segmented row adds tap-target complexity for 10 values |
| Boolean widget | Two-button row (Yes / No) | Toggle switch | Toggle is ambiguous about which state is "on"; two-button row is explicit and symmetric |
| Duration widget | Three separate H / M / S numeric fields | Single seconds field; HH:MM:SS text entry | Three fields allow independent editing of each component; single seconds field is unintuitive for durations > 60s |
| Value ↔ ViewModel exchange type | `ValueUIState` sealed class (pure UI text/state) | `EventValue?` directly; `EventValue?` + separate UI flags | `EventValue?` forces early parse on every keystroke — partial input (e.g., "3.") must be discarded or snapped, causing snap-back bugs; a pure UI state type lets partial text survive in the ViewModel until save |
| `ValueUIState` location | `ui/components/ValueUIState.kt` alongside `ValueInputField` | Domain layer; separate `ui/model/` package | The type is a UI concern — it carries text fields and selection state; placing it next to its sole renderer avoids a long-distance dependency |
| Mismatch detection layer | ViewModel on load (via `toValueUIState(valueType)`) | `ValueInputField` computes inline from `value + valueType` args | ViewModel-layer detection eliminates the `valueType` param from `ValueInputField`, lets the VM own the full value lifecycle, and removes the risk of the widget and ViewModel diverging on mismatch state |
| `Mismatched.toEventValue()` on save-without-resolve | Returns `editableState?.toEventValue() ?: originalValue` | Discard and use type default; block save | Saving without resolving the banner is a valid user choice (defer the decision); returning the edited sub-state or the original value preserves data rather than silently overwriting it |

## Open Questions & Future Decisions

### Pending work

1. ~~**Unit in `ValueType.Number`**~~ — resolved by the `Category.defaultValue` redesign. `Category.unit` replaced with `Category.defaultValue: EventValue?`; the unit for Number categories is stored as `NumberValue(0.0, unit)` in `defaultValue`. Seeding at log time uses `resolvedDefaultValue.toValueUIState()` when non-null, falling back to `defaultValueUIStateForType` otherwise (see `docs/llds/category-management.md`).
2. **`toEventValue()` null ambiguity** — the method returns `null` for two distinct reasons: (a) `ValueUIState.None` (category type is None, no value is appropriate); (b) invalid/partial input (e.g., `Bool(null)`, unparseable Number text). Current callers always have the category type in scope and disambiguate correctly, so this is harmless now. Worthwhile medium-size refactor: introduce `EventValue.None` (making `Event.value: EventValue` non-nullable) so callers get a typed signal instead of relying on ambient context — expected to clarify the code broadly throughout the stack.

### Open questions

1. **Empty timeline state** — what the home screen shows when there are no events yet. Copy/illustration TBD. Distinct from the empty *category picker* (the quick-log step-1 grid with no categories), whose dead-end is resolved by the always-present "+ New category" tile (EL-UI-090).

### Resolved edges — inline category creation (EL-UI-090/091, EL-NAV-020/021)

- **Room-`Flow` lag on reopen:** the `created_category_id` result is present immediately on return (written before the pop), but the new category row may trail it by a frame or two in `categories`. The step-2 reopen awaits the row's appearance before `selectCategory`, so it never selects a not-yet-loaded category.
- **Create-then-delete id:** an id naming a category that isn't in `categories` (only reachable out-of-flow — the reopened sheet offers no delete) is a natural no-op: reopen is gated on `pendingCategoryCreate`, not on the id resolving, so the sheet still reopens at step 1 and the step-2 advance simply never fires. No hang, no bound needed.
- **Active filter vs. explicit create:** when a filter pre-selected a category and the user instead created one inline, the reopen selects the *created* category (explicit intent wins over the filter's pre-selection).

### Deferred until after MVP

1. **Duration input improvements** — current H / M / S fields are functional but could be improved: stopwatch capture, natural-language input ("1h 30m"), or single-number + unit picker are candidates.
2. **Boolean custom labels** — v1 uses "Yes" / "No" labels on the two-button row. Future: allow categories to specify custom label pairs (e.g., "Taken" / "Skipped" for medication, "Good" / "Bad" for mood).
3. **Timeline date range filtering** — the repository supports `start`/`end` bounds; no UI for it in v1. Could be added as a filter/search later.
4. **Multi-category filter** — v1 supports single-category filter only. Multi-select deferred.

## References

- `docs/llds/data-model.md` — `Event`, `Category`, `EventValue`, `ValueType`
- `docs/llds/local-storage.md` — `TrackrRepository` interface, `ImageStore`
- `docs/llds/category-management.md` — category list and edit flows (parallel segment)
- `docs/high-level-design.md` — three-tap goal, image capture sources
