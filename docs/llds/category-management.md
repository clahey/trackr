# Category Management

## Context and Design Philosophy

This segment covers creating, editing, and deleting user-defined categories. Categories are the schema of the tracking system — getting their definition right matters, and changes to an existing category's `valueType` have downstream consequences for historical events.

The segment owns two screens (category list and category edit), their ViewModels, and the validation rules for category fields. It does not own event logging or display — those belong to `event-logging`.

## Screens

### Category List Screen

Displays all categories ordered by `sortOrder ASC` (user-defined). MetaCategories appear as top-level rows; their SubCategories are displayed visually nested beneath them (indented or grouped). Each row shows the category's resolved emoji, name, and resolved value type. Supports:

- Navigate to **Category Edit** for a new category (FAB, primary action)
- Navigate to **Category Edit** for an existing category (tap row)
- Delete a category (long-press row → context menu)
- Reorder categories (drag handle on each row; calls `repository.reorderCategories()` on drop; SubCategories can only be reordered within their parent's group)
- Group operations (long-press row → context menu, items depend on category type):

| Category type | Long-press group actions |
|---|---|
| `MetaCategory` with SubCategories | *(no group operations — cannot be nested while it has children)* |
| `MetaCategory` with no SubCategories | **"Add to group"** — open group picker to select a parent |
| `SubCategory` | **"Move to another group"**; **"Remove from group"** |

The **group picker** (used by "Add to group" and "Move to another group") lists all eligible MetaCategories (excluding the current parent for "Move to another group"). It always includes a **"Create new group"** option that creates a new MetaCategory and immediately sets it as the parent — this is why "Move to another group" is always available for SubCategories even when no other MetaCategory currently exists. When reparenting, all current explicit field values are preserved as overrides.

**Delete confirmation and child promotion:** when a MetaCategory is deleted, its SubCategories are **promoted to top-level MetaCategories** rather than deleted. Any null (inherited) fields on each SubCategory are resolved to the parent's current values at the time of deletion before the parent is removed. The delete operation is atomic: promotion of all children and deletion of the parent happen in a single transaction.

Because children are promoted (not deleted), the event count shown in the delete confirmation covers only the category's **own** events (for a MetaCategory, not its children's). The confirmation dialog renders two optional sentences: (1) if `ownEventCount > 0`, state the number of events that will be permanently deleted; (2) if `subCategoryCount > 0`, state the number of SubCategories that will be promoted to top-level categories. At least one sentence is always shown when a dialog appears. Silent deletion (no dialog) is allowed when `ownEventCount == 0 && subCategoryCount == 0`. `DeleteConfirmation` carries `ownEventCount` and `subCategoryCount` only — no `isMetaCategory`. Both ViewModels' `confirmDelete()` call `repository.deleteCategory(id)` unconditionally; the repository handles promotion internally. The dialog implementation is extracted as a shared `DeleteCategoryDialog` composable used by both the list and edit screens.

### Category Edit Screen

Used for both create and edit. Toolbar contains a **Delete** action (visible only when editing an existing category). For a MetaCategory, the toolbar also contains a **"Create subcategory"** action.

**Live preview card** — always visible on the edit screen. Renders the shared `EventRow` composable (from `ui/components/EventRow.kt`) with a synthetic `Category` built from current VM state and a placeholder `Event` whose fields are: `notes = "Notes"`, `onClick = {}`, `hasMismatch = false`, and a type-appropriate sample value:

| Effective value type | Placeholder event value |
|---|---|
| None | `null` |
| Number | `NumberValue(42.0, unit)` where `unit` is the current unit field value (null if blank) |
| Scale | `ScaleValue(7)` |
| Boolean | `BooleanValue(true)` |
| Text | `TextValue("Sample")` |
| Duration | `DurationValue(90s)` |
| Exercise | `ExerciseValue(sets=3, reps=15)` |

`onClick = null` (non-interactive). Timestamp fixed at noon local time so the time-of-day column always reads "12:00". Updates reactively as any field changes.

**Fields:**

| Field | Input | Shown when | SubCategory inherit option |
|---|---|---|---|
| Name | Text field | Always | N/A — name is never inherited |
| Emoji | Single-character text field (system emoji keyboard) | Always | SubCategory only: "Inherit" checkbox to the left of the text field. When checked (inheriting), the field is non-editable and shows the parent's emoji. When unchecked (custom), the field is editable. Custom value is preserved in `EmojiUIState` across mode switches. |
| Color | Preset color palette picker; out-of-palette swatch if current color is not in palette | Always | Extra circle in the palette row showing the parent's color with a small label; selecting it sets `color = null` (inherit) |
| Value type | Segmented picker / dropdown | Always | Extra "Same as [ParentName] ([TypeName])" row in the picker (e.g., "Same as Running (Exercise)"); selecting it sets `valueType = null` (inherit) |
| Unit | Text field | effective `valueType == Number` | N/A |

For a **MetaCategory**, none of the "inherit" options are shown (there is no parent). For a **SubCategory**, each inheritable field shows its inherit option. The inherit option for each field is shown first / in a visually distinct position so it is clearly a different kind of choice.

`allowEmptyText` is not exposed in the MVP editor; always written as `true` for new categories.

**Creating a subcategory:** tapping "Create subcategory" opens a new Category Edit screen with `parentId` set to the current MetaCategory. All inheritable fields open in the inherited state (null) so the subcategory tracks the parent by default. The live preview immediately reflects the inherited values. The user may override any field before saving.

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

- Accepts an optional `categoryId` and an optional `parentId` (set when creating a subcategory from a parent's edit screen); loads existing category on init if `categoryId` is provided
- **Stale category guard:** if `getCategoryById` returns null on init (edit mode only), sets `"snackbar_message"` on the previous back stack entry's `SavedStateHandle` and emits a navigate-back signal via `navigateBack: StateFlow<Boolean>`. The category list screen observes `"snackbar_message"` on its own back stack entry and shows a snackbar on resume.
- `parent: StateFlow<Category.MetaCategory?>` — loaded when `parentId` is non-null; drives the inherit/override UI and effective value resolution
- **Per-field form state for inheritable fields** (SubCategory mode only): `emojiUIState: MutableStateFlow<EmojiUIState>`, `colorState: MutableStateFlow<Long?>`, `valueTypeState: MutableStateFlow<ValueType?>`. `EmojiUIState(mode: EmojiMode, customValue: String)` where `mode` ∈ {INHERIT, CUSTOM}; `customValue` is always preserved across mode switches so switching back to Custom restores the previous entry. `colorState` and `valueTypeState` remain null = inherit. For MetaCategory, `emojiUIState` is always CUSTOM.
- **Effective values** (derived `StateFlow`s): `effectiveEmoji: StateFlow<String?>`, `effectiveColor: StateFlow<Long?>`, `effectiveValueType: StateFlow<ValueType?>` — each combines the corresponding state field with `parent` to resolve null to the parent's value; used by the live preview and validation
- `isEmojiInherited`, `isColorInherited`, `isValueTypeInherited` — `StateFlow<Boolean>` derived from whether the corresponding state is null; drive the inherit-option selection state in the UI
- `save()`: validates all fields using effective values; for a MetaCategory, validates that emoji/color/valueType are non-null (always true since there's no parent); for a SubCategory, the effective values are guaranteed non-null via parent fallback; constructs the appropriate `Category` sealed class variant
- Exposes `saveResult: StateFlow<SaveResult>` (`Idle`, `Success`, `ValidationError`)
- `ownEventCount: StateFlow<Int>` — live count of events belonging directly to this category, from `repository.getEventCountForCategory(categoryId, includeSubCategories = false)`; used with `subCategoryCount` to determine whether a confirmation dialog is needed before deletion
- `subCategoryCount: StateFlow<Int>` — live count of SubCategories whose `parentId` equals this category's id; zero for SubCategories and new categories; used alongside `ownEventCount` to gate the silent-delete path
- `affectedEventCount: StateFlow<Int>` — live count from `repository.getEventCountForCategory(categoryId, includeSubCategoriesWithNullType = true)`, covering the category's own events plus events of SubCategories whose `valueType` is null (inheriting the parent's type); for a SubCategory (which has no children), this equals its own event count; drives the ValueType change warning (a MetaCategory type change migrates inheriting children too)
- `valueTypeWarning: StateFlow<ValueTypeWarningTier?>` — null when no warning (conversion is reversible, `affectedEventCount == 0`, or effective `valueType == originalValueType`); otherwise one of three tiers derived from the conversion table:
  - `IrreversibleSafe`: conversion is fully safe but not reversible (e.g. None→Number, Duration→Text); message: *"Existing events will be converted. This change cannot be reversed by switching back."*
  - `Partial`: conversion migrates what it can but some events may not convert (e.g. Text→Number, Text→Boolean); message: *"Some existing events may not be convertible and will display incorrectly."*
  - `Unsafe`: no migration is performed — all other pairs (e.g. Number→None); message: *"Existing events cannot be converted and will display incorrectly."*
- `originalValueType` is set at load time to the **effective** valueType: for a MetaCategory it is `category.valueType`; for a SubCategory with a non-null override it is the override; for a SubCategory with null valueType (inheriting) it is `parent.valueType`. Null only for new categories, so `valueTypeWarning` is always null in create mode
- `save()`: when effective `valueType != originalValueType` (edit mode only), calls `repository.saveCategoryAndMigrateEvents(category, originalType)` to persist the category and migrate events atomically (including inheriting SubCategory events); otherwise calls `repository.saveCategory(category)`
- New categories are assigned `sortOrder = (min sortOrder across all categories) - 1`; using a global minimum avoids collisions when categories are reparented or promoted
- New MetaCategories pre-populate `color` on init via `repository.getAndIncrementNextCategoryColorIndex()`. New SubCategories open with all inheritable fields null (inheriting). `save()` always uses the current state values.

### Value Type Migration

The conversion function `convertEventValue(value, to)` lives in the domain layer (`domain/ValueTypeConversion.kt`) since it depends only on domain types. It is called by the repository implementation (inside a transaction) and is also importable by tests.

`TrackrRepository.saveCategoryAndMigrateEvents(category: Category, fromType: ValueType)` runs the category upsert and all event value updates inside a single Room transaction, ensuring the database is never left in a partially-migrated state.

### Event Count Queries

`CategoryEditViewModel` observes live counts to drive the delete confirmation gate and the ValueType change warning. Add to `TrackrRepository`:

```kotlin
fun getEventCountForCategory(categoryId: String, includeSubCategoriesWithNullType: Boolean): Flow<Int>
fun getSubCategoryCount(categoryId: String): Flow<Int>
```

- `getEventCountForCategory(id, includeSubCategoriesWithNullType = false)` — own events only; used for `ownEventCount` (delete gate) and SubCategory event counts.
- `getEventCountForCategory(id, includeSubCategoriesWithNullType = true)` — own events plus events of SubCategories whose `valueType` is null (inheriting the parent's type); used for `affectedEventCount` (ValueType warning).
- `getSubCategoryCount(id)` — count of SubCategories with this parentId; used for `subCategoryCount` (delete gate for MetaCategories).

All three return `Flow<Int>` so the UI stays current if events or subcategories change while the edit screen is open.

## Navigation

```
Categories (list)
    ├── [FAB]                    → Category Edit (new MetaCategory)
    ├── [tap row]                → Category Edit (existing)
    └── [long-press row]         → context menu
            ├── Delete           → confirmation → delete (with confirmation if events exist)
            ├── Add to group     → group picker → [pick or create parent] → reparent
            ├── Move to group    → group picker → [pick or create parent] → reparent  (SubCategory only)
            └── Remove from group → promote to MetaCategory  (SubCategory only)

Category Edit
    ├── [toolbar: Create subcategory] → Category Edit (new SubCategory, parentId set)  (MetaCategory only)
    ├── [toolbar: Delete]        → confirmation → delete → back to list  (edit mode only)
    ├── [save]                   → back to list
    └── [cancel/back]            → back to list
```

Deletion can be initiated from either the list (long-press) or the edit screen (toolbar). Both paths go through the same confirmation and deletion logic.

The group picker is a shared UI component used by "Add to group", "Move to another group" (from list and from edit screen), and "Create new group" within the picker itself.

## Color Selection

Preset palette for v1. The palette is a fixed list of ARGB `Long` values defined in the UI layer. The domain model accepts any `Long` — the constraint is purely in the picker UI. Full color wheel deferred to a future version.

**Out-of-palette colors:** if an existing category's color is not in the preset palette (e.g., set by a future app version with a custom picker), the editor displays the current color as a distinct "current color" swatch before the palette swatches. It is pre-selected. The user may keep it (no change) or tap any preset to replace it. The out-of-palette swatch is only shown when editing an existing category whose color is not in the palette — it is never shown when creating a new category.

**SubCategory color inherit option:** for a SubCategory, the color picker includes a special "inherit" swatch — a circle showing the parent's resolved color (which may itself be a custom color) labeled with a short identifier (e.g., the parent's name truncated). Selecting it sets `color = null`. This swatch appears as the first item in the picker row, before the out-of-palette swatch (if present) and the palette swatches. When the SubCategory's `color` is null, the inherit swatch is pre-selected; when `color` is non-null, the inherit swatch is unselected and the current color (palette swatch or out-of-palette swatch) is pre-selected. The inherit swatch and the out-of-palette swatch may coexist when the SubCategory has an explicit out-of-palette color.

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
2. **`allowEmptyText` editor exposure** — field is present on the domain model; exposing it in the editor is a UI decision deferred past MVP.
3. **Archiving vs. deletion** — soft-delete (archive) would preserve historical events without showing the category in the active list. Deferred to v2.
4. **"Move to another group" / "Remove from group" from the edit screen (CAT-UI-058, CAT-NAV-011)** — the group picker is already implemented on the list screen. The open question is what happens to unsaved form edits when the user reparents or promotes from within the edit screen. Options: (a) navigate back discarding unsaved changes (matches delete/removeFromGroup behavior but may surprise users); (b) save current form state atomically with the parent change; (c) prevent the action if there are unsaved changes. Deferred until the right UX is clear.

## References

- `docs/llds/data-model.md` — `Category` domain model, `ValueType` sealed class, `allowEmptyText`
- `docs/llds/local-storage.md` — `TrackrRepository` interface, cascade delete behavior
