# Category Management Specs

LLD: `docs/llds/category-management.md`

---

## Category List

- [x] **CAT-UI-001**: The system shall display all categories on the list screen in hierarchical sort order: MetaCategories sorted by their own `sortOrder` ascending, with each MetaCategory's SubCategories appearing immediately after their parent sorted by their own `sortOrder` ascending before the next MetaCategory. Each row shows resolved emoji, name, and resolved value type; SubCategory rows are visually indented.
- [x] **CAT-UI-002**: The category list screen shall integrate the `DragReorderList` widget (DRAG-UI-*) to support reordering and reparenting categories: each category maps to a widget item with `depth` 0 for a MetaCategory / 1 for a SubCategory, `canHaveChildren = (it is a MetaCategory)`, and `canBecomeChild = (it currently has no SubCategories)`.
- [x] **CAT-UI-080**: When the user drops a dragged category row (`DRAG-UI-` move result), the system shall, within a single database transaction: set the dragged category's parent to the destination group (null for a top-level drop, or the target MetaCategory's id for a nest), preserving all of its current explicit field values as overrides (per DM-PROC-020, same rule as CAT-UI-052); and reassign `sortOrder` for every member of the destination sibling group, in their resulting order, via a dense zero-based reindex. The source group's `sortOrder` values, if the drop changed which group the row belongs to, are left unchanged.
- [x] **CAT-UI-081**: When reparenting a category whose stored `valueType` is null (inherited) — via drag (CAT-UI-080), "Add to group," or "Move to another group" (CAT-UI-051/052) — to a `MetaCategory` whose `resolvedValueType` differs from the category's previous effective parent's `resolvedValueType`: if the category has zero of its own events, or the conversion is fully safe and reversible (per the conversion table in `docs/llds/category-management.md`), the system shall proceed without a dialog; otherwise it shall show a confirmation dialog using the same tiered message text as CAT-UI-036/037/038, with Confirm proceeding and Cancel aborting the reparent entirely (nothing is persisted). Whenever the effective value type actually changed — dialog shown or not — the system shall migrate the category's existing event values using the same conversion table as CAT-UI-032, scoped to this category's own events.
- [ ] **CAT-UI-082**: When the `DragReorderList` widget reports a drop (DRAG-UI-005/DRAG-UI-014), the category list screen shall call the widget's completion callback exactly once for that drop, regardless of which path the drop took: immediately after the repository call returns, for a drop that needs no value-type confirmation (CAT-UI-080 alone); after the migrating persist completes, if the user confirms a CAT-UI-081 dialog; or immediately, with nothing persisted, if the user cancels it.
- [x] **CAT-UI-003**: When the user long-presses a category row, the system shall present a context menu; all rows include Delete; a MetaCategory with no SubCategories additionally includes "Add to group"; a SubCategory additionally includes "Move to another group" and "Remove from group".
- [x] **CAT-UI-004**: When the user initiates deletion of a category that has zero of its own events and zero SubCategories, the system shall delete it immediately without a confirmation dialog.
- [x] **CAT-UI-005**: When the user initiates deletion of a category that does not qualify for immediate deletion (per CAT-UI-004), the system shall show a confirmation dialog; if `ownEventCount > 0` the dialog shall include a sentence stating the number of events that will be permanently deleted, beginning with that count; if `subCategoryCount > 0` the dialog shall include a sentence stating the number of SubCategories that will be promoted to top-level categories; at least one sentence is always shown.
- [x] **CAT-UI-006**: When the user confirms deletion of a category, the system shall call `deleteCategory`, which within a single database transaction promotes any SubCategories to top-level MetaCategories (resolving null fields to the parent's values at deletion time) and then deletes the category and all its own associated events; promoted SubCategories and all their events are preserved.

## Category Edit — Display

- [ ] **CAT-UI-010**: The category edit screen shall display input fields for name, emoji, color, and value type.
- [x] **CAT-UI-017**: When the category edit screen loads in edit mode and the requested category is not found in the repository, the system shall navigate back to the category list and display a snackbar on the category list screen reading "Category not found."
- [ ] **CAT-UI-011**: While effective value type is Number, the category edit screen shall display a "Unit (optional)" text field; for all other value types this field shall be hidden. The field is seeded from the category's stored `defaultValue` (as `NumberValue.unit`) on load.
- [ ] **CAT-UI-011a**: While effective value type is Exercise, the category edit screen shall display two integer input fields labeled "Default sets" and "Default reps"; both must be ≥ 1 to save. The fields are seeded from the category's stored `defaultValue` (as `ExerciseValue.sets`/`.reps`) on load, falling back to 3 and 15 if `defaultValue` is null.
- [x] **CAT-UI-012**: While editing an existing category, the edit screen shall display a delete action in the toolbar.
- [x] **CAT-UI-013**: While creating a new category, the edit screen shall not display a delete action.
- [x] **CAT-UI-014**: The color field shall display the preset palette (defined in `docs/llds/theme.md § Preset Palette`) and require a selection at all times; no free-form color entry in v1.
- [x] **CAT-UI-015**: When editing an existing category whose color is not in the preset palette, the color picker shall display the current color as a "Custom" swatch (labeled "Custom", 3 cells wide) to the right of the Inherited swatch when a parent is present, or alone on the top row when there is no parent; the Custom swatch is pre-selected; the user may keep it or replace it by tapping a preset.
- [x] **CAT-UI-016**: The out-of-palette current color swatch shall not be shown when creating a new category.

## Category Edit — Validation

- [x] **CAT-UI-020**: When the user attempts to save a category with a name that is empty or contains only whitespace, the system shall display an inline error and not save.
- [x] **CAT-UI-021**: When the user attempts to save a category with an empty emoji field, the system shall display an inline error and not save.
- [x] **CAT-UI-022**: When the user attempts to save a category whose emoji field contains more than one grapheme cluster, the system shall display an inline error and not save.

## Category Edit — ValueType Change Warning and Migration

- [x] **CAT-UI-030**: While the selected value type differs from the category's original value type, the conversion is not reversible (per the conversion table in `docs/llds/category-management.md`), and the category has one or more existing events affected by the change, the system shall display an inline warning below the value type picker; for a MetaCategory, the affected event count includes the MetaCategory's own events plus events of SubCategories whose valueType is null (inheriting); for a SubCategory, the affected event count includes only the SubCategory's own events.
- [x] **CAT-UI-031**: The inline value type warning shall disappear automatically when the user reverts the value type picker back to its original value.
- [x] **CAT-UI-036**: When the inline warning is shown for a fully-safe but irreversible conversion, the warning shall read: "Existing events will be converted. This change cannot be reversed by switching back."
- [x] **CAT-UI-037**: When the inline warning is shown for a partially-safe conversion, the warning shall read: "Some existing events may not be convertible and will display incorrectly."
- [x] **CAT-UI-038**: When the inline warning is shown for a non-safe conversion, the warning shall read: "Existing events cannot be converted and will display incorrectly."
- [x] **CAT-UI-032**: When the user saves a category edit in which the value type differs from the original, the system shall migrate all existing event values for that category according to the conversion table in `docs/llds/category-management.md`.
- [x] **CAT-UI-033**: For event values that cannot be converted to the new value type per the conversion table, the system shall leave those event values unchanged.
- [x] **CAT-UI-034**: When converting Text to Number, the system shall parse both bare numeric strings (e.g. `"3.5"`) and strings of the form `"<number> <unit>"` (e.g. `"3.5 kg"`), preserving the unit; unconvertible strings shall be left unchanged.
- [x] **CAT-UI-035**: When converting Text to Boolean, the system shall map `"Yes"` to `Boolean(true)` and `"No"` to `Boolean(false)`; all other strings shall be left unchanged.
- [x] **CAT-UI-039**: When converting Number to Scale, the system shall convert values that are exact integers (no fractional part) in the range [1, 10] with no unit set to the corresponding Scale value; non-integer Number values, values outside [1, 10], and values with a non-blank unit shall be left unchanged. When implemented, Scale → Number becomes a reversible pair and the conversion table shall be updated accordingly.
- [x] **CAT-UI-044**: When converting a None-type event to Exercise, the system shall produce ExerciseValue(sets=3, reps=15).
- [x] **CAT-UI-045**: When converting an Exercise-type event to Text, the system shall produce Text("$sets × $reps") using the Unicode multiplication sign.
- [x] **CAT-UI-046**: When converting a Text-type event to Exercise, the system shall attempt to parse the text as "$s × $r" or "$s x $r" where both s and r are integers ≥ 1; if parseable, it shall produce ExerciseValue(s, r); otherwise it shall leave the event value unchanged.
- [x] **CAT-UI-047**: The value type picker on the category edit screen shall include Exercise as a selectable option.

## Category Edit — Save Behavior

- [x] **CAT-UI-040**: When saving a new category, the system shall assign it a new UUID as its identifier.
- [x] **CAT-UI-041**: When saving a new category, the system shall assign it a `sortOrder` of `(min sortOrder across all categories) - 1`.
- [x] **CAT-UI-042**: When saving a new category, the system shall set `allowEmptyText` to `true`.
- [x] **CAT-UI-043**: When the category edit screen loads in create mode for a new MetaCategory, the system shall pre-select a default color by calling `getAndIncrementNextCategoryColorIndex(palette.size)` (LS-BE-081) and using `palette[index]` as the initial value of the color picker; the counter cycles within `[0, palette.size)` and is unaffected by category deletions. The user may override this by selecting any palette color before saving. When creating a new SubCategory, the color field opens in the inherit state (null) and the counter is not advanced.

## Category Edit — Default Value

- [ ] **CAT-UI-063**: When saving a Number category, the system shall store `defaultValue = NumberValue(existingValue ?: 0.0, unit)` where `existingValue` is the numeric component of any previously stored `defaultValue` and `unit` is null when the unit field is blank; the numeric component is never altered by the editor.
- [ ] **CAT-UI-064**: When saving an Exercise category, the system shall store `defaultValue = ExerciseValue(sets, reps)` using the current values of the default sets and reps fields.
- [ ] **CAT-UI-065**: When saving a category whose effective value type is neither Number nor Exercise, the system shall leave `defaultValue` unchanged; it shall not be cleared or overwritten.
- [ ] **CAT-UI-066**: When the category edit screen opens in SubCategory create mode, the system shall pre-populate the default value fields from the parent's `resolvedDefaultValue` only if its type matches the SubCategory's effective value type; if the types do not match or `resolvedDefaultValue` is null, the fields shall be pre-populated with the type default (3 and 15 for Exercise; blank for Number). When saving any category and `defaultValueDirty` is false, the system shall store the previously stored `defaultValue` unchanged (null for a new category, preserving any existing value in edit mode); CAT-UI-063 and CAT-UI-064 apply only when `defaultValueDirty` is true.

## Category Hierarchy

### Category List — Color and Group Operations

- [x] **CAT-UI-050**: Each category row in the category list shall display the resolved category color as a 48dp filled circle around the resolved emoji, matching the circle-avatar treatment used on timeline event rows (THEME-UI-011).
- [x] **CAT-UI-051**: The group picker (used for "Add to group" and "Move to another group") shall list all MetaCategories eligible to become the new parent (excluding the category's current parent in the "Move" case) and shall always include a "Create new group" option; tapping it shall open a name-entry dialog with a blank text field; the user enters the group name and confirms to create the MetaCategory and immediately set it as the parent.
- [x] **CAT-UI-052**: When the user selects "Add to group" or "Move to another group" for a category, the system shall preserve all of the category's current explicit field values as overrides (per DM-PROC-020).

### Category Edit — Subcategory Creation and Inheritance

- [x] **CAT-UI-053**: While editing a MetaCategory, the category edit screen shall display a "Create subcategory" action.
- [x] **CAT-UI-054**: When the user initiates subcategory creation, the system shall open the category edit screen in create mode with the parentId set to the MetaCategory's id and all inheritable fields (emoji, color, valueType) initialized as null.
- [x] **CAT-UI-055**: While editing a SubCategory, the emoji field shall display an Inherit toggle row as the first item inside the Emoji `OutlinedFieldBox` (CAT-UI-074), above the quick-pick row, containing, from left to right: the label "Inherit", the parent's emoji at full opacity when Inherit is ON or at reduced opacity when OFF, a flexible spacer, and a right-justified Switch; tapping anywhere in the row shall flip the Inherit state.
- [x] **CAT-UI-056**: While editing a SubCategory, the color picker shall display an "Inherited" swatch (labeled "Inherited", 3 cells wide) on its own row above the palette grid showing the parent's resolved color; when `color` is null the Inherited swatch shall be pre-selected; when `color` is non-null the Inherited swatch shall be unselected and the current color (palette or Custom swatch) shall be pre-selected; tapping the Inherited swatch shall set `color` to null; the Inherited swatch and the Custom swatch (CAT-UI-015) may coexist when the SubCategory has an explicit out-of-palette color, appearing side by side on the top row.
- [x] **CAT-UI-057**: While editing a SubCategory, the value type picker shall include a "Same as [ParentName] ([TypeName])" option as an additional selectable row; selecting it sets valueType to null (inherit from parent).
- [D] **CAT-UI-058**: While editing a SubCategory, the toolbar overflow menu shall contain "Remove from group" and "Move to another group".
- [x] **CAT-UI-059**: The category edit screen shall display a live preview card rendered using the shared `EventRow` composable with a synthetic category (current effective name, emoji, color, and value type) and a placeholder event (`notes = "Notes"`, value = a type-appropriate sample per the conversion table in `docs/llds/category-management.md § Live preview card`).
- [x] **CAT-UI-060**: The live preview card shall update reactively as any field on the edit screen changes.
- [x] **CAT-UI-061**: When the user switches the emoji field to Inherit mode, the previously entered custom value shall be preserved in the UI state and restored when the user switches back to Custom mode.
- [x] **CAT-UI-062**: When the emoji field is opened in Inherit mode (SubCategory create, or loading a SubCategory with `emoji = null`), `customValue` in `EmojiUIState` shall be pre-populated with the parent's emoji; this value becomes the initial custom selection when the user first switches to Custom mode.
- [x] **CAT-UI-068**: The emoji field shall display a horizontally scrollable quick-pick row of ~25 curated tracking-relevant emojis as tappable buttons, always visible regardless of Inherit state; the curated set is defined as a constant in the UI layer.
- [x] **CAT-UI-069**: When Inherit mode is OFF and the current custom emoji matches an emoji in the quick-pick row, that button shall be visually highlighted and the row shall be scrolled so the highlighted button is visible; when there is no match, or when Inherit mode is ON, no button is highlighted.
- [x] **CAT-UI-070**: The emoji field shall display a "Browse all" button, always visible regardless of Inherit state; tapping it shall open the emoji2 `EmojiPickerView` in a `ModalBottomSheet`.
- [x] **CAT-UI-071**: When the user selects an emoji in the emoji2 picker BottomSheet, the system shall set the custom emoji value to the selected emoji, dismiss the BottomSheet, and if Inherit mode was ON switch to Custom mode.
- [x] **CAT-UI-072**: While editing a SubCategory, when the user taps a quick-pick emoji while Inherit mode is ON, the system shall switch to Custom mode and set the tapped emoji as the custom value, overwriting any previously preserved custom value.
- [x] **CAT-UI-073**: When Inherit mode is OFF and the current custom emoji is non-empty but is not present in the quick-pick set, the system shall display the current emoji to the right of the Browse button.
- [x] **CAT-UI-074**: The category edit screen shall render the Emoji section (Inherit toggle row when present, quick-pick row, and Browse row) inside an `OutlinedFieldBox` labeled "Emoji", with the label embedded in the box's border in the visual style of `OutlinedTextField`'s floating label.
- [x] **CAT-UI-075**: The category edit screen shall render the Color section (color picker) inside an `OutlinedFieldBox` labeled "Color", with the label embedded in the box's border in the visual style of `OutlinedTextField`'s floating label.
- [x] **CAT-UI-076**: When the emoji field fails validation (CAT-UI-021, CAT-UI-022), the Emoji `OutlinedFieldBox`'s border and label shall render in the error color (`MaterialTheme.colorScheme.error`), matching `OutlinedTextField`'s `isError` styling, in addition to the existing inline error text below the field.

## Navigation

- [ ] **CAT-NAV-001**: When the user taps the FAB on the category list screen, the system shall navigate to the category edit screen in create mode for a new MetaCategory.
- [ ] **CAT-NAV-002**: When the user taps a category row on the category list screen, the system shall navigate to the category edit screen in edit mode for that category.
- [ ] **CAT-NAV-003**: When the user saves a category on the edit screen, the system shall navigate back to the category list.
- [ ] **CAT-NAV-004**: When the user taps back or cancel on the category edit screen without saving, the system shall navigate back to the category list without persisting any changes.
- [x] **CAT-NAV-005**: When the user confirms deletion of a category from the edit screen toolbar, the system shall delete the category and navigate back to the category list.
- [x] **CAT-NAV-010**: When the user taps "Create subcategory" on a MetaCategory edit screen, the system shall navigate to the category edit screen in create mode with the parentId set to the MetaCategory's id.
- [x] **CAT-NAV-006**: When the user attempts to navigate back (hardware back button or navigation icon) while the category edit form has unsaved changes, the system shall present a Save / Discard / Cancel prompt; Save shall persist the changes and navigate back; Discard shall navigate back without persisting changes; Cancel shall dismiss the prompt and return to the edit screen.
- [x] **CAT-UI-067**: The Save button on the category edit screen shall be visible only when the form has unsaved changes; in create mode the form is considered dirty from the start, so the Save button is visible immediately; it shall be hidden when the form is in its initial saved state in edit mode.
- [D] **CAT-NAV-011**: When the user confirms "Remove from group" on a SubCategory edit screen, the system shall promote the SubCategory to a MetaCategory (per DM-PROC-019) and navigate back to the category list.
