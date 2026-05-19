# Category Management

## Context and Design Philosophy

This segment covers creating, editing, and deleting user-defined categories. Categories are the schema of the tracking system — getting their definition right matters, and changes to an existing category's `valueType` have downstream consequences for historical events.

The segment owns two screens (category list and category edit), their ViewModels, and the validation rules for category fields. It does not own event logging or display — those belong to `event-logging`.

## Screens

### Category List Screen

Displays all categories ordered by `sortOrder ASC` (user-defined). Each row shows the category emoji, name, and value type. Supports:

- Navigate to **Category Edit** for a new category (FAB, primary action)
- Navigate to **Category Edit** for an existing category (tap row)
- Delete a category (long-press row → context menu)
- Reorder categories (drag handle on each row; calls `repository.reorderCategories()` on drop)

**Delete confirmation:** before deleting, query the event count for that category. If count > 0, show a confirmation dialog stating how many events will be permanently deleted. If count = 0, delete immediately without confirmation.

### Category Edit Screen

Used for both create and edit. Toolbar contains a **Delete** action (visible only when editing an existing category). Fields:

| Field | Input | Shown when |
|---|---|---|
| Name | Text field | Always |
| Emoji | Single-character text field (system emoji keyboard) | Always |
| Color | Preset color palette picker; out-of-palette swatch if current color is not in palette | Always |
| Value type | Segmented picker / dropdown | Always |
| Unit | Text field | `valueType == Number` only |

`allowEmptyText` is not exposed in the MVP editor; always written as `true` for new categories.

**ValueType change warning and migration:** when saving a category edit with a changed `valueType`, the system migrates all existing event values using the conversion table below. Conversions listed as **fully safe** are silent — no inline warning is shown while editing. All other conversions show an inline warning below the value type picker while the changed type is selected; the warning disappears if the user reverts the type. Event values that cannot be converted per the table are left unchanged.

**Conversion table:**

| From | To | Rule | Fully safe? | Reversible? |
|---|---|---|---|---|
| None | Number | `null` → `Number(0.0, null)` | Yes | No — Number→None is non-safe |
| None | Scale | `null` → `Scale(5)` | Yes | No — Scale→None is non-safe |
| None | Boolean | `null` → `Boolean(true)` | Yes | No — Boolean→None is non-safe |
| None | Text | `null` → `Text("")` | Yes | Yes — Text("")→None via Text→None row |
| None | Duration | `null` → `Duration(ZERO)` | Yes | No — Duration→None is non-safe |
| Scale | Number | `Scale(n)` → `Number(n.toDouble(), null)` | Yes | No — Number→Scale is non-safe |
| Scale | Text | `Scale(n)` → `Text(n.toString())` | Yes | Yes — Text("n")→Scale via Text→Scale row |
| Boolean | Text | `Boolean(true)` → `Text("Yes")`, `Boolean(false)` → `Text("No")` | Yes | Yes — Text→Boolean row |
| Number | Text | `Number(v, u)` → `Text("v u")` or `Text("v")` if u is null | Yes | Yes — Text→Number row (parses unit) |
| Duration | Text | `Duration(d)` → `Text(ValueInputFieldd.toString())` | Yes | No — no reverse Duration parser |
| None | Exercise | `null` → `ExerciseValue(3, 15)` | Yes | No — Exercise→None is non-safe |
| Exercise | Text | `ExerciseValue(s, r)` → `Text("$s × $r")` | Yes | Yes — Text→Exercise row |
| Text | Exercise | parse `"$s × $r"` or `"$s x $r"` (both integers ≥ 1) → `ExerciseValue(s, r)`; else leave unchanged | No | — |
| Text | Boolean | `Text("Yes")` → `Boolean(true)`, `Text("No")` → `Boolean(false)`; else leave unchanged | No | — |
| Text | Number | parse as `<double>` or `<double> <unit>` → `Number(d, u)`; else leave unchanged | No | — |
| Text | Scale | if parseable as Int in [1..10] → `Scale(n)`; else leave unchanged | No | — |
| Text | None | if `Text("")` → `null`; else leave unchanged | No | — |
| all other pairs | — | leave unchanged | No | — |

**Validation (all enforced before save):**

| Field | Rule |
|---|---|
| Name | Non-empty after trim |
| Emoji | Non-empty; single grapheme cluster |
| Color | Always valid (current color always selected on open; user may switch to a preset) |
| ValueType | Always valid (picker, always has a selection) |

## ViewModels

### CategoryListViewModel

- Exposes `categories: StateFlow<List<Category>>` from `repository.getCategories()`
- `deleteCategory(id: String)`: queries event count first; exposes `pendingDeleteConfirmation: StateFlow<DeleteConfirmation?>` for the UI to show a dialog; executes deletion on confirmation
- `confirmDelete()` / `cancelDelete()`: resolve the pending confirmation

### CategoryEditViewModel

- Accepts an optional `categoryId`; loads existing category on init if provided
- **Stale category guard:** if `getCategoryById` returns null on init (edit mode only), sets `"snackbar_message"` on the previous back stack entry's `SavedStateHandle` and emits a navigate-back signal via `navigateBack: StateFlow<Boolean>`. The category list screen observes `"snackbar_message"` on its own back stack entry and shows a snackbar on resume.
- Exposes form field state as individual `StateFlow`s (or a single `FormState` data class)
- `save()`: validates all fields, constructs a `Category` with a new UUID (create) or existing id (edit), calls `repository.saveCategory()`
- Exposes `saveResult: StateFlow<SaveResult>` (`Idle`, `Success`, `ValidationError`)
- `eventCount: StateFlow<Int>` — live count from `repository.getEventCountForCategory()`; drives delete button visibility and ValueType change warning (both trigger when count > 0)
- `valueTypeWarning: StateFlow<ValueTypeWarningTier?>` — null when no warning (conversion is reversible, `eventCount == 0`, or `valueType == originalValueType`); otherwise one of three tiers derived from the conversion table:
  - `IrreversibleSafe`: conversion is fully safe but not reversible (e.g. None→Number, Duration→Text); message: *"Existing events will be converted. This change cannot be reversed by switching back."*
  - `Partial`: conversion migrates what it can but some events may not convert (e.g. Text→Number, Text→Boolean); message: *"Some existing events may not be convertible and will display incorrectly."*
  - `Unsafe`: no migration is performed — all other pairs (e.g. Number→None); message: *"Existing events cannot be converted and will display incorrectly."*
- `originalValueType` is set at load time; null for new categories, so `valueTypeWarning` is always null in create mode
- `save()`: when `valueType != originalValueType` (edit mode only), calls `repository.saveCategoryAndMigrateEvents(category, originalType)` to persist the category and migrate events atomically; otherwise calls `repository.saveCategory(category)`
- New categories are assigned `sortOrder = currentMin - 1` (placing them at the top); the repository provides the current minimum via `CategoryDao.getMinSortOrder()`
- New categories pre-populate `color` on init via `repository.getAndIncrementNextCategoryColorIndex()`, which returns `index % paletteSize` mapped to the preset palette; the counter is monotonically increasing and unaffected by deletions. The user may override the pre-selected color in the picker before saving. `save()` always uses `color.value` for both new and existing categories.

### Value Type Migration

The conversion function `convertEventValue(value, from, to)` lives in the domain layer (`domain/ValueTypeConversion.kt`) since it depends only on domain types. It is called by the repository implementation (inside a transaction) and is also importable by tests.

`TrackrRepository.saveCategoryAndMigrateEvents(category: Category, fromType: ValueType)` runs the category upsert and all event value updates inside a single Room transaction, ensuring the database is never left in a partially-migrated state.

### Event Count Query

`CategoryEditViewModel` observes a live event count to drive both the delete button visibility and the ValueType change warning. Add to `TrackrRepository`:

```kotlin
fun getEventCountForCategory(categoryId: String): Flow<Int>
```

A `Flow` is required because the count affects visible UI state (delete button, change warning) that must stay current if events are added or removed while the edit screen is open.

## Navigation

```
Categories (list)
    ├── [FAB]              → Category Edit (new)
    ├── [tap row]          → Category Edit (existing)
    └── [long-press row]   → context menu → Delete (with confirmation if events exist)

Category Edit
    ├── [toolbar: Delete]  → confirmation → delete → back to list  (edit mode only)
    ├── [save]             → back to list
    └── [cancel/back]      → back to list
```

Deletion can be initiated from either the list (long-press) or the edit screen (toolbar). Both paths go through the same confirmation and deletion logic.

## Color Selection

Preset palette for v1. The palette is a fixed list of ARGB `Long` values defined in the UI layer. The domain model accepts any `Long` — the constraint is purely in the picker UI. Full color wheel deferred to a future version.

**Out-of-palette colors:** if an existing category's color is not in the preset palette (e.g., set by a future app version with a custom picker), the editor displays the current color as a distinct "current color" swatch above the preset palette. It is pre-selected. The user may keep it (no change) or tap any preset to replace it. The out-of-palette swatch is only shown when editing an existing category whose color is not in the palette — it is never shown when creating a new category.

## Emoji Input

Free-form single-character text field; the system emoji keyboard is used for input. Validation enforces a single grapheme cluster (not just a single `Char` — emoji can be multi-codepoint). Invalid input (empty or multi-grapheme) blocks save with an inline error.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Category ordering | User-defined `sortOrder`; new at top (`currentMin - 1`) | `createdAt ASC`; alphabetical | User-defined gives most control; new-at-top is the natural expectation when you just created something |
|---|---|---|---|
| Delete confirmation trigger | Event count > 0 | Always confirm; never confirm | Zero-event categories are safe to delete silently; non-zero warrants explicit user acknowledgment of data loss |
| ValueType change behavior | Warn, don't block | Block change; silent change | User may intentionally reclassify a category; blocking is paternalistic; silent is dangerous |
| Color selection | Preset palette (v1) | Full color wheel | Preset covers the common case with minimal UI complexity; wheel deferred until requested |
| Emoji input | System keyboard, single grapheme validation | Custom emoji picker | System keyboard is zero-code; a picker adds significant UI complexity for marginal benefit |
| `allowEmptyText` in editor | Hidden, always `true` in MVP | Exposed as a toggle | Not needed for initial use; field exists on the domain model for future exposure without migration |
| Event count query | Point suspend query on delete intent | Always load counts with list; no count check | Loading counts with the list adds overhead for an operation that rarely happens; always confirming is noisy |

## Open Questions & Future Decisions

### Deferred

1. **Preset color palette values** — specific colors TBD; defined in the UI layer, not the domain. Resolved when theme LLD is drafted.
3. **`allowEmptyText` editor exposure** — field is present on the domain model; exposing it in the editor is a UI decision deferred past MVP.
4. **Archiving vs. deletion** — soft-delete (archive) would preserve historical events without showing the category in the active list. Deferred to v2.

## References

- `docs/llds/data-model.md` — `Category` domain model, `ValueType` sealed class, `allowEmptyText`
- `docs/llds/local-storage.md` — `TrackrRepository` interface, cascade delete behavior
