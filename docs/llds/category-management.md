# Category Management

## Context and Design Philosophy

This segment covers creating, editing, and deleting user-defined categories. Categories are the schema of the tracking system — getting their definition right matters, and changes to an existing category's `valueType` have downstream consequences for historical events.

The segment owns two screens (category list and category edit), their ViewModels, and the validation rules for category fields. It does not own event logging or display — those belong to `event-logging`.

## Screens

### Category List Screen

Displays all categories ordered by `sortOrder ASC` (user-defined). MetaCategories appear as top-level rows; their SubCategories are displayed visually nested beneath them (indented or grouped). Each row shows the category's resolved color as a 48dp filled circle around the resolved emoji (same circle-avatar treatment as `EventRow`, per `theme.md § Category Color System`), the name, and the resolved value type. Supports:

- Navigate to **Category Edit** for a new category (FAB, primary action)
- Navigate to **Category Edit** for an existing category (tap row)
- Delete a category (long-press row → context menu)
- Reorder categories (drag handle on each row; see `docs/llds/drag-reorder-list.md` for the full drag interaction, drop-zone, and persistence design — drag also subsumes reparenting: dropping on/near a different group's rows moves the category there)
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
| Emoji | Wrapped in an `OutlinedFieldBox` (label "Emoji"). (1) Quick-pick row: horizontally scrollable row of ~25 curated tracking emojis; always visible and tappable; current selection highlighted and scrolled into view when it matches one of the 25. (2) Browse button: opens the emoji2 `EmojiPickerView` in a `ModalBottomSheet`; if the current custom selection is not in the quick-pick set, the selected emoji is displayed to the right of the Browse button. Tapping a quick-pick or the Browse button while in Inherit mode auto-switches to Custom mode. | Always | SubCategory only: Inherit toggle row, first item inside the `OutlinedFieldBox`, above the quick-pick row: `[Inherit label] [parent emoji — full opacity when ON, greyed when OFF] ──── [Switch right-justified]`; tapping anywhere in the row flips the state. Custom emoji value is preserved in `EmojiUIState` across Inherit ↔ Custom mode switches. |
| Color | Wrapped in an `OutlinedFieldBox` (label "Color"). Preset color palette picker; out-of-palette swatch if current color is not in palette | Always | Extra circle in the palette row showing the parent's color with a small label; selecting it sets `color = null` (inherit) |
| Value type | Segmented picker / dropdown (own `OutlinedTextField` border; not additionally wrapped) | Always | Extra "Same as [ParentName] ([TypeName])" row in the picker (e.g., "Same as Running (Exercise)"); selecting it sets `valueType = null` (inherit) |
| Default value | Type-dependent sub-fields (see below); each its own `OutlinedTextField`, not wrapped in an outer box | effective `valueType == Number` or `Exercise` | N/A (not shown for other inherited types) |

For a **MetaCategory**, none of the "inherit" options are shown (there is no parent). For a **SubCategory**, each inheritable field shows its inherit option. The inherit option for each field is shown first / in a visually distinct position so it is clearly a different kind of choice.

**Default value fields** (shown only when the effective `valueType` is `Number` or `Exercise`):
- **Number**: a single "Unit (optional)" text field. The stored `defaultValue` is always `NumberValue(0.0, unit)` where `unit` is null when the field is blank. The number component is fixed at 0 — the user edits the unit only.
- **Exercise**: two integer fields labeled "Default sets" and "Default reps", initialized to 3 and 15. Both must be ≥ 1 to save. The stored `defaultValue` is `ExerciseValue(sets, reps)`.

`allowEmptyText` is not exposed in the MVP editor; always written as `true` for new categories.

**Creating a subcategory:** tapping "Create subcategory" opens a new Category Edit screen with `parentId` set to the current MetaCategory. All inheritable fields open in the inherited state (null) so the subcategory tracks the parent by default. The live preview immediately reflects the inherited values. The user may override any field before saving.

**ValueType change warning and migration:** when saving a category edit with a changed `valueType`, the system migrates all existing event values using the conversion table below. Conversions listed as **fully safe** are silent — no inline warning is shown while editing. All other conversions show an inline warning below the value type picker while the changed type is selected; the warning disappears if the user reverts the type. Event values that cannot be converted per the table are left unchanged.

The same tier system and migration also gate **reparenting** a `SubCategory` whose `valueType` is `null` (inherited) when it changes which `MetaCategory`'s effective type it inherits — via drag, "Add to group," or "Move to another group." Reparenting has no edit-screen form to show an inline warning under, so it uses a confirmation dialog instead, with the same tiered message text; see § Drag-to-Reorder: Adapter & Persistence below for the mechanism, shared across all three reparent entry points.

**Conversion table:**

| From | To | Rule | Fully safe? | Reversible? |
|---|---|---|---|---|
| None | Number | `null` → `Number(0.0, null)` | Yes | No — Number→None is non-safe |
| None | Scale | `null` → `Scale(5)` | Yes | No — Scale→None is non-safe |
| None | Boolean | `null` → `Boolean(true)` | Yes | No — Boolean→None is non-safe |
| None | Text | `null` → `Text("")` | Yes | Yes — Text("")→None via Text→None row |
| None | Duration | `null` → `Duration(ZERO)` | Yes | No — Duration→None is non-safe |
| Scale | Number | `Scale(n)` → `Number(n.toDouble(), null)` | Yes | Yes — Number→Scale row (Scale's [1,10] integer invariant round-trips losslessly) |
| Number | Scale | `Number(v, u)` → `Scale(v.toInt())` if `v` is an exact integer in `[1, 10]` and `u` is null/blank; else leave unchanged (a populated unit blocks conversion rather than being silently dropped). | No | Yes — Scale→Number row, for the subset of values that convert |
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
- `onDragMove(result: DragMoveResult, onSettled: () -> Unit)`: the widget's `onMove` callback (see `docs/llds/drag-reorder-list.md § Settling`). When no value-type confirmation is needed, calls `onSettled` at the end of its own coroutine, after the repository call returns. When one is needed, `onSettled` is stored on `PendingValueTypeConfirmation` itself rather than called here, and invoked later from `confirmPendingValueTypeChange()` (after the migrating persist completes) or `cancelPendingValueTypeChange()` (immediately, nothing persisted) — whichever the user picks. Exactly one of these three call sites runs per drag drop.

### CategoryEditViewModel

- Accepts an optional `categoryId` and an optional `parentId` (set when creating a subcategory from a parent's edit screen); loads existing category on init if `categoryId` is provided
- **Stale category guard:** if `getCategoryById` returns null on init (edit mode only), sets `"snackbar_message"` on the previous back stack entry's `SavedStateHandle` and emits a navigate-back signal via `navigateBack: StateFlow<Boolean>`. The category list screen observes `"snackbar_message"` on its own back stack entry and shows a snackbar on resume.
- `parent: StateFlow<Category.MetaCategory?>` — loaded when `parentId` is non-null; drives the inherit/override UI and effective value resolution
- **Dirty tracking:** `isDirty: StateFlow<Boolean>` starts false; set to true by any user-initiated field mutation. The init block writes fields directly (no dirty side-effect). The Save button is visible only when `isDirty` is true. When `isDirty` is true, attempting to navigate back (hardware back button or navigation icon) shows an UnsavedChangesDialog (Save / Discard / Cancel); Save calls `save()` which navigates back via `SaveResult.Success`; Discard navigates back without persisting; Cancel dismisses the dialog. `UnsavedChangesDialog` is a shared composable in `ui/components/`.
- **Per-field form state for inheritable fields** (SubCategory mode only): `name`, `emojiUIState`, `colorState`, `valueTypeState` are private `MutableStateFlow`s exposed as read-only `StateFlow`s with public setter functions (`setName`, `setEmojiUIState`, `setColorState`, `setValueTypeState`) that set `isDirty = true`. `EmojiUIState(mode: EmojiMode, customValue: String)` where `mode` ∈ {INHERIT, CUSTOM}; `customValue` is always preserved across mode switches so switching back to Custom restores the previous entry. `colorState` and `valueTypeState` remain null = inherit. For MetaCategory, `emojiUIState` is always CUSTOM.
- **Default value form state**: `numberDefaultUnit: MutableStateFlow<String>` (used when effective `valueType == Number`); `exerciseDefaultSets: MutableStateFlow<String>` and `exerciseDefaultReps: MutableStateFlow<String>` (used when effective `valueType == Exercise`), initialized to "3" and "15".
  - **On load (edit mode)**: `numberDefaultUnit` is seeded from `(storedDefaultValue as? NumberValue)?.unit ?: ""`; `exerciseDefaultSets`/`Reps` from `(storedDefaultValue as? ExerciseValue)?.sets/reps` or the "3"/"15" fallback.
  - **On load (SubCategory create mode)**: pre-populate `numberDefaultUnit` and `exerciseDefaultSets`/`Reps` from the parent's `resolvedDefaultValue` (same pattern as `emojiUIState.customValue`); the stored `defaultValue` starts as null (inherit). A `defaultValueDirty: Boolean` flag (false on open) tracks whether the user has edited any default field; it is set to true on any edit.
  - **On save**: for Number and Exercise, compose and save the default value only if `defaultValueDirty` is true (or if in edit mode, where there is no inherited state to preserve). In SubCategory create mode with `defaultValueDirty == false`, save `defaultValue = null` (inherit). For Number (when saving), compose `NumberValue(existingStoredDefault?.value ?: 0.0, newUnit)` — the existing numeric value is preserved; only the unit is updated. For Exercise, compose `ExerciseValue(sets, reps)`. For all other types, leave `defaultValue` completely unchanged (do not overwrite it with null, even if the category editor does not show default fields). This ensures that unexpected or future-typed defaults are preserved.
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
- The live preview uses `resolvedDefaultValue` when non-null as the `previewEventValue`; when null, falls back to the hardcoded type sample (e.g., `Scale(7)`, `BooleanValue(true)`). This makes the preview reflect the actual defaults for Number and Exercise categories.

### Value Type Migration

The conversion function `convertEventValue(value, to)` lives in the domain layer (`domain/ValueTypeConversion.kt`) since it depends only on domain types. It is called by the repository implementation (inside a transaction) and is also importable by tests.

`TrackrRepository.saveCategoryAndMigrateEvents(category: Category, fromType: ValueType)` runs the category upsert and all event value updates inside a single Room transaction, ensuring the database is never left in a partially-migrated state.

### Drag-to-Reorder: Adapter & Persistence

The category list hosts the generic `DragReorderList` widget (`docs/llds/drag-reorder-list.md`); this section owns the category-specific glue — mapping `Category` to the widget's tree, gating a reparent on a value-type change, and persisting a drop. It lives in `ui/category/CategoryListScreen.kt` / `CategoryListViewModel.kt` and the repository, not in the generic widget file.

**Adapter (`CategoryListViewModel` / `CategoryListScreen`).**

- Builds the `DragListItem` tree from `categories: List<Category>` (already sorted by the existing `getCategories()` comparator, which already produces the parent-then-children grouping the adapter needs): each `MetaCategory` becomes a top-level node whose `children` are its `SubCategory` nodes, in order; a top-level `Category` with no parent and no children is a leaf top-level node. Per node: `canHaveChildren = (category is MetaCategory)`; `canBecomeChild = (subCategoryCount == 0)` — for a `MetaCategory` this means "has no current `SubCategory` children" (live count, same source as the existing delete-confirmation gate); a `SubCategory` is always `canBecomeChild = true` (it can never have children of its own, by the existing invariant). This is the one place the "eligibility happens to equal childlessness" coincidence is actually encoded — deliberately confined to the adapter, not the widget. The HLD's two-level cap is enforced here too, by construction: the adapter only ever nests `SubCategory`s one level under their `MetaCategory`, so the tree it hands the widget is never deeper than one level.
- On `onMove`, resolves `DragMoveResult` (see `docs/llds/drag-reorder-list.md § Generic Widget API`) into a repository call (see Persistence below): loads the moved category and, if `newParentId != null`, the new parent `MetaCategory`; reconstructs the moved item as a `MetaCategory` or `SubCategory` using the same variant-conversion pattern already used by `reparentCategoryInternal`/`removeFromGroup` in `CategoryListViewModel.kt` (preserve all current explicit field values as overrides — no field resets on a parent change).
- **Value-type-change check before persisting.** A `SubCategory` with `valueType = null` (inherited) gets its *effective* value type from whichever `MetaCategory` is its current parent — reparenting it to a different `MetaCategory` can silently change that effective type. This was already true of the existing "Add to group"/"Move to another group" actions (CAT-UI-051/052), which call the same `reparentCategoryInternal`; drag just makes it far more frequent. The adapter now checks: if the moved category's stored `valueType` is `null` and it's moving to a *different* `MetaCategory` whose `resolvedValueType` differs from the old parent's, compute the same warning tier `CategoryEditViewModel` already derives for an explicit value-type edit (see `valueTypeWarning` under § ViewModels → CategoryEditViewModel, and § Value Type Migration), against this category's own event count (it has no children, so no `includeSubCategoriesWithNullType` complication). If the tier warrants a warning (same gate as CAT-UI-030 — anything but a fully-safe-and-reversible conversion, and the event count is nonzero), show a confirmation dialog reusing the CAT-UI-036/037/038 message text before persisting anything; Cancel abandons the move entirely (the row stays where it was — see below); Confirm proceeds. Whenever the effective type actually changed (dialog shown or not — a fully-safe conversion still needs to run, just silently), the move is persisted via the migrating variant below instead of plain `moveCategory`.
- This same check is shared with `reparentCategoryInternal` (used by "Add to group"/"Move to another group"), not duplicated for drag alone — so all three reparent entry points behave consistently.
- **Calls the widget's `onSettled`.** Every path through `onMove` — the plain persist, the migrating persist, the dialog's Cancel, *and a rejected persist* (see Rejection below) — ends by calling `onSettled`, exactly once. For the plain/migrating persist, that's at the end of the same coroutine (after `repository.moveCategory`/`moveCategoryAndMigrateEvents` returns *or throws*). For the dialog path, `onSettled` can't be called from `onMove`'s own coroutine, since persistence (or abandoning it) doesn't happen until the user acts on a separate dialog later — it's threaded into `PendingValueTypeConfirmation` instead and invoked from both `confirmPendingValueTypeChange()` (after the migrating persist completes or throws) and `cancelPendingValueTypeChange()` (immediately, nothing persisted). The widget doesn't know or care which of these happened; it just unfreezes and re-reads `categories`, which by then either reflects the move or doesn't. See `docs/llds/drag-reorder-list.md § Settling` for the widget side of this protocol.

**Persistence (repository).**

A `TrackrRepository` method, alongside the existing `reorderCategories` (which remains for any future flat-list-only use; the drag widget does not call it):

```kotlin
suspend fun moveCategory(category: Category, orderedSiblingIds: List<String>)
```

When the move changes a `SubCategory`'s effective value type (see the Value-type-change check above), the adapter calls the migrating variant instead:

```kotlin
suspend fun moveCategoryAndMigrateEvents(category: Category, orderedSiblingIds: List<String>, fromType: ValueType)
```

— the same transaction and `sortOrder` reindex as `moveCategory`, plus the existing `convertEventValue` migration pass over the category's own events (mirroring `saveCategoryAndMigrateEvents`'s pattern).

- `category`: the moved item, already reconstructed as the correct domain variant (`MetaCategory` if `newParentId == null`, `SubCategory` with the resolved `parent` otherwise) by the adapter.
- `orderedSiblingIds`: the ordered id list of the **destination** group as the drop saw it (all `MetaCategory` ids, in order, for a top-level move; or the target parent's children ids, in order, including the moved item) — mirrors `DragMoveResult.orderedSiblingIds` directly. It is a **stale UI snapshot**, taken from the widget's rendered `items` (a `getCategories()` Flow emission) outside any transaction, and — for a drop that routes through a value-type confirmation dialog (CAT-UI-081) — held across an arbitrarily long user pause before it is applied. It is therefore treated as an *ordering hint*, not an authoritative membership list (see below).

`LocalTrackrRepository.moveCategory` runs the move in one `db.withTransaction`, reusing existing pieces:
1. `categoryDao.upsert(category.toEntity())` — same path as `saveCategory`, including its existing guard ("cannot nest a category with children"), which doubles as a safety net even though the widget should never offer an ineligible drop in the first place. This runs first, so the moved row already carries its destination `parentId` when the group is re-read in step 2.
2. **Reconcile against live state, then dense-reindex.** Rather than reindexing `orderedSiblingIds` directly — which would trust a snapshot taken before the transaction — the repository re-reads the destination group's *current* members within the same transaction (`getChildrenByParentIdOnce(parentId)` for a nest; a new `getTopLevelOnce()` / `parentId IS NULL` query for a top-level move) and reindexes **those**. The snapshot orders them: members present in `orderedSiblingIds` take that relative order; ids in `orderedSiblingIds` that are no longer members (deleted or reparented away since the snapshot) are dropped; and any current member absent from the snapshot (added concurrently, e.g. across a confirmation dialog) is folded back in at its prior position — immediately following the same current sibling it presently follows by `sortOrder`, or at the front if it currently precedes all snapshot-known members — never displacing the user's arranged order. Multiple unknown members that anchor to the same known sibling (or all sit ahead of every known member) keep their mutual `sortOrder` order. The resulting merged list is dense-reindexed `0..n-1` via `categoryDao.updateSortOrders`. This ordering rule is a pure function, `reconcileSiblingOrder(currentMembers, orderedSiblingIds)` in `domain/SiblingReindex.kt`, so both `LocalTrackrRepository` and the test `FakeTrackrRepository` share one implementation (and it is unit-tested in isolation).

This closes a read-outside-transaction (TOCTOU) gap: the reindex list is now derived from state read *inside* the transaction, so a concurrent change to the destination group between the UI snapshot and commit can no longer strand a sibling at a stale, colliding `sortOrder`. The moved category's target parent being deleted concurrently is **not** handled here — with no foreign key on `parentId`, the upsert simply leaves the row pointing at a missing parent, which `Mappers.toDomainList()`'s existing orphan recovery surfaces as a top-level category.

The **source** group (if the move changed which group the item belongs to) is left untouched — `sortOrder` values only need to express relative order among current siblings, and removing a member doesn't invalidate the relative order of the ones left behind. No renumbering needed there.

**Rejection: a leaf that gained children since the snapshot.** The adapter marks a childless `MetaCategory` `canBecomeChild = true` from the *snapshot*, so the widget offers nesting it under another group. Between that snapshot and the drop actually persisting (widened, again, by a possible CAT-UI-081 dialog), the category can concurrently gain a `SubCategory` — via "Create subcategory" or another "Add to group" — which would make the requested nest a two-level-deep violation. This invariant is enforced *authoritatively inside the transaction*: `moveCategory`/`saveCategory`'s existing `requireNoChildren` guard re-reads the child count within the write and throws `IllegalArgumentException` when the reconstructed `SubCategory` still has children, rolling the whole transaction back so no illegal nest is ever persisted. The ViewModel does **not** re-check the child count itself before persisting — that would reintroduce the same out-of-transaction TOCTOU the reconciliation above closes; the in-transaction guard is the single source of truth. `persistReparent` therefore treats persistence as fallible: it catches that rejection and, on every reparent entry point that shares it, (a) calls `onSettled` exactly once anyway so the drag widget unfreezes and animates the row back to its origin (the same no-op bounce-back as a cancelled dialog — the persist simply didn't change `categories`), and (b) surfaces a snackbar on the category list reading "'<name>' now has subcategories and can't be nested." "Move to another group" cannot hit this — it operates on a `SubCategory`, which by the two-level invariant can never have children — so only drag (CAT-UI-080) and "Add to group" (CAT-UI-051) are affected. The rejection is distinguished by exception type alone (`IllegalArgumentException`); `requireNoChildren` is the only guard on these write paths that raises it, so catching it at the `persistReparent` boundary is unambiguous today — if a second `require` is ever added on this path, promote the guard to a typed exception rather than widening the catch (see Open Questions).

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
    ├── [save]                   → back to list  (button only visible when dirty)
    ├── [back, clean]            → back to list
    └── [back, dirty]            → UnsavedChangesDialog → Save / Discard / Cancel
```

Deletion can be initiated from either the list (long-press) or the edit screen (toolbar). Both paths go through the same confirmation and deletion logic.

The group picker is a shared UI component used by "Add to group", "Move to another group" (from list and from edit screen), and "Create new group" within the picker itself.

## Field Section Box

`OutlinedFieldBox` (`ui/components/OutlinedFieldBox.kt`) is a shared composable that draws a rounded-rect outline border with its label embedded in a notch at the top — replicating `OutlinedTextField`'s floating-label border style for content that isn't a text field. It takes a `label: String`, an optional `isError: Boolean = false`, and arbitrary `content: @Composable ColumnScope.() -> Unit`. The corner shape is sourced from the M3 theme (`MaterialTheme.shapes.extraSmall`, matching `OutlinedTextField`'s default shape) rather than a hardcoded value, per `theme.md`'s "default M3 shape scale, no overrides" decision. When `isError` is true, the border and label render in `MaterialTheme.colorScheme.error`, matching `OutlinedTextField`'s `isError` styling; the border/label otherwise use `MaterialTheme.colorScheme.outline` / `onSurfaceVariant`. Used to visually group the Emoji and Color sections on the category edit screen, matching the "boxed field" look that `OutlinedTextField` already gives Name and Value type. Name and Value type are not additionally wrapped — they already read as boxed via their own `OutlinedTextField`s, and wrapping them again would nest borders. For the same reason, the Value type section's warning text and default-value sub-fields (Unit / Default sets & reps) are left outside any box; each already has its own `OutlinedTextField` border.

## Color Selection

The color picker is wrapped in an `OutlinedFieldBox` (label "Color"). Preset palette for v1. The palette is a fixed list of ARGB `Long` values defined in the UI layer. The domain model accepts any `Long` — the constraint is purely in the picker UI. Full color wheel deferred to a future version.

**Layout:** the palette is displayed as an adaptive grid of rounded-rectangle swatches (`RoundedCornerShape(8.dp)`). On screens narrower than 480dp the grid is 6 columns × 2 rows; on screens 480dp and wider it is 12 columns × 1 row. Each swatch is sized to fill its cell (equal width, 44dp tall). A selected swatch is indicated by a 3dp white border.

**Out-of-palette colors:** if an existing category's color is not in the preset palette (e.g., set by a future app version with a custom picker), a "Custom" swatch is shown labeled with the word "Custom", 3 cells wide. The user may keep it (it is pre-selected with a white border) or tap any preset to replace it. The Custom swatch is only shown when editing an existing category whose color is not in the palette — it is never shown when creating a new category.

**SubCategory color inherit option:** for a SubCategory, the color picker includes an "Inherited" swatch showing the parent's resolved color, labeled "Inherited", 3 cells wide. Selecting it sets `color = null`. The Inherited swatch appears on its own row above the palette grid, with the Custom swatch (if present) to its right on the same row. When the SubCategory's `color` is null, the Inherited swatch is pre-selected (white border); when `color` is non-null, the Inherited swatch is unselected and the current color (palette swatch or Custom swatch) is pre-selected. The Inherited swatch and the Custom swatch may coexist when the SubCategory has an explicit out-of-palette color.

## Emoji Input

The emoji field is wrapped in an `OutlinedFieldBox` (label "Emoji") and has three layers, rendered as a `Column` inside the box:

1. **Inherit toggle row** (SubCategory only): a single `Row` that is entirely `toggleable`. Layout: `[Inherit label] [parent emoji] [Spacer weight=1] [Switch]`. The parent emoji is shown at full alpha when Inherit mode is ON (it is the effective value); at reduced alpha when OFF (shown as a passive reference). Tapping anywhere in the row flips the Inherit state.

2. **Quick-pick row**: a horizontally scrollable `LazyRow` of ~25 curated tracking-relevant emoji buttons (defined as a constant in the UI layer). Always visible and tappable regardless of Inherit state. When the current custom selection matches one of the 25, that button is visually highlighted (e.g. a border or filled background) and the row is scrolled so it is visible. When Inherit is ON and the user taps a quick-pick, the system switches to Custom mode and sets the tapped emoji.

3. **Browse row**: a `[Browse all]` button that opens `EmojiPickerView` (from `androidx.emoji2:emoji2-emojipicker`) in a `ModalBottomSheet`. Selecting an emoji from the picker sets the custom value and dismisses the sheet; if Inherit was ON, it switches to Custom mode first. If the current custom selection is **not** in the quick-pick set (and is non-empty), the selected emoji is displayed in a `Text` or styled chip to the right of the Browse button — giving the user visual confirmation of their choice.

For MetaCategory (no parent), the Inherit toggle row is omitted; the field renders only the quick-pick row and Browse row.

**Validation:** empty or multi-grapheme-cluster custom value blocks save with an inline error (CAT-UI-021, CAT-UI-022). The quick-pick row and emoji2 picker both insert exactly one grapheme cluster by construction, so validation failures in practice come only from a stale or never-set state.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Category ordering | User-defined `sortOrder`; new at top (`currentMin - 1`) | `createdAt ASC`; alphabetical | User-defined gives most control; new-at-top is the natural expectation when you just created something |
|---|---|---|---|
| Delete confirmation trigger | Event count > 0 | Always confirm; never confirm | Zero-event categories are safe to delete silently; non-zero warrants explicit user acknowledgment of data loss |
| ValueType change behavior | Warn, don't block | Block change; silent change | User may intentionally reclassify a category; blocking is paternalistic; silent is dangerous |
| Color selection | Preset palette (v1) | Full color wheel | Preset covers the common case with minimal UI complexity; wheel deferred until requested |
| Emoji input | Quick-pick row (~25 curated emojis) + emoji2 `ModalBottomSheet` picker; no text field | System keyboard text field; full custom picker only | Quick-picks cover common tracking emojis without a dependency; emoji2 covers the full Unicode set; removing the text field avoids a confusing affordance (text box implying typed input) |
| Section visual grouping | Shared `OutlinedFieldBox` composable (notched-label border matching `OutlinedTextField`), applied to Emoji and Color only | Material3 `OutlinedCard` with internal header text; no visual grouping (status quo) | `OutlinedCard` reads as a distinct "card" surface, not a "field" — doesn't visually match `OutlinedTextField`'s border language; Name and Value type already have implicit boxes via their own `OutlinedTextField`s, so wrapping them again would double the border |
| `allowEmptyText` in editor | Hidden, always `true` in MVP | Exposed as a toggle | Not needed for initial use; field exists on the domain model for future exposure without migration |
| Event count query | Point suspend query on delete intent | Always load counts with list; no count check | Loading counts with the list adds overhead for an operation that rarely happens; always confirming is noisy |
| Destination-group-only renumbering on move | Renumber only the group the item lands in; leave the source group's existing `sortOrder` values untouched | Renumber both source and destination groups on every move | `sortOrder` only needs correct relative order, not contiguity; touching the source group is unnecessary write work |
| Reindex against live state, not the drop snapshot | Re-read the destination group's current members inside the transaction and reindex those, using `orderedSiblingIds` only as an ordering hint (known members in snapshot order, stale ids dropped, unknown current members kept at their prior position) | Reindex `orderedSiblingIds` verbatim (the drop-time snapshot) | `orderedSiblingIds` is read from the UI's `getCategories()` snapshot *outside* the transaction, and held across a possible CAT-UI-081 confirmation dialog — a classic read-outside-transaction (TOCTOU) window. Trusting it verbatim strands any concurrently-added sibling at a stale `sortOrder` colliding with a reassigned one; deriving the reindex list from an in-transaction read closes the gap. "Keep current position" for an unknown member (vs. append-at-end) preserves the user's arranged order exactly while leaving a surprise sibling where it already sat |
| Reparent rejection when a leaf gained children | Let the in-transaction `requireNoChildren` guard throw and roll back; `persistReparent` catches it, still fires `onSettled` once (row bounces back), and shows a snackbar | Re-check child count in the ViewModel before persisting; block the drop pre-emptively | A pre-persist ViewModel check re-reads outside the write transaction — the same TOCTOU class this whole change closes — so it can still race. The transaction guard is authoritative and already exists; the only work needed is to stop *dropping* its exception on the floor (which froze the widget and could crash the coroutine) and turn it into a bounce-back + snackbar |

## Open Questions & Future Decisions

### Deferred

1. **Preset color palette values** — specific colors TBD; defined in the UI layer, not the domain. Resolved when theme LLD is drafted.
2. **`allowEmptyText` editor exposure** — field is present on the domain model; exposing it in the editor is a UI decision deferred past MVP.
3. **Archiving vs. deletion** — soft-delete (archive) would preserve historical events without showing the category in the active list. Deferred to v2.
5. ~~**Inherit swatch iconography in the color picker**~~ — resolved. The swatch is now a labeled rounded rectangle reading "Inherited" rather than a circle with a "↑" character.

4. **"Move to another group" / "Remove from group" from the edit screen (CAT-UI-058, CAT-NAV-011)** — the group picker is already implemented on the list screen. The open question is what happens to unsaved form edits when the user reparents or promotes from within the edit screen. Options: (a) navigate back discarding unsaved changes (matches delete/removeFromGroup behavior but may surprise users); (b) save current form state atomically with the parent change; (c) prevent the action if there are unsaved changes. Deferred until the right UX is clear.
6. **Emoji search in the picker (extends CAT-UI-070, CAT-UI-071)** — the current "Browse all" picker (`androidx.emoji2:emoji2-emojipicker` 1.6.0's `EmojiPickerView`) is browse-only; its bundled data (loaded via the library's internal `BundledEmojiListLoader`) contains only emoji characters and skin-tone variant codepoints, no names or keywords, so there is no name/keyword data to filter on even via a fork. If search is built, the recommended data source is merging **`muan/emojilib`** (keyword/synonym search per emoji, e.g. "happy" → 😀; MIT, ~1.8k GitHub stars) with **`muan/unicode-emoji-json`** (name, group, `skin_tone_support` flag, plus a `data-emoji-components.json` table of skin-tone modifier codepoints; MIT, ~420 stars) — both maintained by the same author, both keyed by the same 1,914 emoji characters, and explicitly designed by their author to be merged together. Considered and rejected:
   - **`alexdametto/compose-emoji-picker`** — Compose-native MIT library with built-in search, but search is literal substring-match-on-name only (no synonyms); distributed only via JitPack (no Maven Central); low adoption signal (5 stars, single maintainer, repo created March 2025).
   - **A live emoji API (`emoji-api.com`)** — free, but its data is pinned to Unicode 15 (Aug 2023, already stale), has no documented ToS, attribution requirement, or rate limits and is explicitly labeled "still under development"; requires an API key and a network round-trip per search keystroke; doesn't remove the need for a bundled offline fallback anyway, so it adds risk without removing local-data work.
   - **The official Emojipedia API** — access is granted case-by-case on request, not generally/publicly available, so not viable to depend on.

   Deferred post-MVP; no EARS specs exist for this yet.
7. **Move Up / Move Down context-menu actions (extends CAT-UI-002)** — accessible, non-drag path for within-group reordering, needed because the kept context-menu actions (Add to group / Move to another group / Remove from group) cover reparenting but not reordering. Resolved as "yes, build this" during `drag-reorder-list.md`'s design (see that LLD's Open Questions § Resolved item 2); deferred as a follow-up task, not bundled into the drag feature's own implementation. No EARS specs exist for this yet.
8. **Typed rejection exception for reparent (relates to CAT-UI-084)** — the concurrent-child-gain rejection is currently distinguished by `IllegalArgumentException` type alone, which is unambiguous only because `requireNoChildren` is the single `require` on the move/save write paths. If another precondition guard is added there, replace the string-`require` with a dedicated domain exception (e.g. `CategoryHasChildrenException`) so `persistReparent`'s catch stays precise. Not worth the indirection today.
9. **A drop whose target group was deleted concurrently silently becomes a top-level move.** In `onDragMove`, `newParent` is resolved by `getCategoryById(newParentId) as? MetaCategory`; if the target group was deleted between the snapshot and the drop, this is `null`, and `reconstructForMove` then promotes the dragged item to a top-level `MetaCategory` instead of nesting it — the drop lands, just not where aimed. This is the reparent-target-deleted analogue of the `sortOrder` orphan-recovery escape hatch and is currently unspecified/untested. Deferred until observed to matter; the safe outcome (a valid top-level category) argues against urgency.

## References

- `docs/llds/data-model.md` — `Category` domain model, `ValueType` sealed class, `allowEmptyText`
- `docs/llds/local-storage.md` — `TrackrRepository` interface, cascade delete behavior
- `docs/llds/drag-reorder-list.md` — the generic drag-to-reorder widget (`DragMoveResult` contract, `onSettled` protocol); the category-specific adapter/persistence glue for `CAT-UI-002`/`CAT-UI-080`/`CAT-UI-081`/`CAT-UI-082` lives here in § Drag-to-Reorder: Adapter & Persistence. The widget's own behavior is specced separately as `DRAG-UI-001`–`016` in `docs/specs/drag-reorder-list.md`
