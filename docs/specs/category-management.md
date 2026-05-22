# Category Management Specs

LLD: `docs/llds/category-management.md`

---

## Category List

- [ ] **CAT-UI-001**: The system shall display all categories on the list screen ordered by `sortOrder` ascending, showing resolved emoji, name, and resolved value type for each row; MetaCategories appear as top-level rows with their SubCategories visually nested beneath them.
- [ ] **CAT-UI-002**: The category list screen shall display a drag handle on each row; when the user drops a row after dragging, the system shall persist the new order via `reorderCategories`; SubCategories may only be reordered within their parent's group, and MetaCategories may only be reordered within the top-level list.
- [x] **CAT-UI-003**: When the user long-presses a category row, the system shall present a context menu; all rows include Delete; a MetaCategory with no SubCategories additionally includes "Add to group"; a SubCategory additionally includes "Move to another group" and "Remove from group".
- [ ] **CAT-UI-004**: When the user initiates deletion of a category that has zero of its own events and, for a MetaCategory, zero SubCategories, the system shall delete it immediately without a confirmation dialog.
- [ ] **CAT-UI-005**: When the user initiates deletion of a category that does not qualify for immediate deletion (per CAT-UI-004), the system shall show a confirmation dialog; for a SubCategory the dialog shall state the number of events that will be permanently deleted; for a MetaCategory the dialog shall state the number of its own events that will be permanently deleted (omitted if zero) and the number of SubCategories that will be promoted to top-level categories (omitted if zero); at least one of these counts is non-zero since the category did not qualify for immediate deletion.
- [ ] **CAT-UI-006**: When the user confirms deletion of a SubCategory, the system shall delete the SubCategory and all its associated events. When the user confirms deletion of a MetaCategory, the system shall atomically promote each SubCategory to a MetaCategory (resolving any null fields to the parent's current values) and then delete the MetaCategory and its own events; the promoted SubCategories and all their events are preserved.

## Category Edit — Display

- [ ] **CAT-UI-010**: The category edit screen shall display input fields for name, emoji, color, and value type.
- [ ] **CAT-UI-017**: When the category edit screen loads in edit mode and the requested category is not found in the repository, the system shall navigate back to the category list and display a snackbar on the category list screen reading "Category not found."
- [ ] **CAT-UI-011**: While value type is Number, the category edit screen shall display a unit input field; for all other value types the unit field shall be hidden.
- [ ] **CAT-UI-012**: While editing an existing category, the edit screen shall display a delete action in the toolbar.
- [ ] **CAT-UI-013**: While creating a new category, the edit screen shall not display a delete action.
- [ ] **CAT-UI-014**: The color field shall display the preset palette (defined in `docs/llds/theme.md § Preset Palette`) and require a selection at all times; no free-form color entry in v1.
- [ ] **CAT-UI-015**: When editing an existing category whose color is not in the preset palette, the color picker shall display the current color as a distinct swatch above the preset palette, pre-selected; the user may keep it or replace it by tapping a preset.
- [ ] **CAT-UI-016**: The out-of-palette current color swatch shall not be shown when creating a new category.

## Category Edit — Validation

- [x] **CAT-UI-020**: When the user attempts to save a category with a name that is empty or contains only whitespace, the system shall display an inline error and not save.
- [x] **CAT-UI-021**: When the user attempts to save a category with an empty emoji field, the system shall display an inline error and not save.
- [x] **CAT-UI-022**: When the user attempts to save a category whose emoji field contains more than one grapheme cluster, the system shall display an inline error and not save.

## Category Edit — ValueType Change Warning and Migration

- [ ] **CAT-UI-030**: While the selected value type differs from the category's original value type, the conversion is not reversible (per the conversion table in `docs/llds/category-management.md`), and the category has one or more existing events affected by the change, the system shall display an inline warning below the value type picker; for a MetaCategory, the affected event count includes the MetaCategory's own events plus events of SubCategories whose valueType is null (inheriting); for a SubCategory, the affected event count includes only the SubCategory's own events.
- [x] **CAT-UI-031**: The inline value type warning shall disappear automatically when the user reverts the value type picker back to its original value.
- [x] **CAT-UI-036**: When the inline warning is shown for a fully-safe but irreversible conversion, the warning shall read: "Existing events will be converted. This change cannot be reversed by switching back."
- [x] **CAT-UI-037**: When the inline warning is shown for a partially-safe conversion, the warning shall read: "Some existing events may not be convertible and will display incorrectly."
- [x] **CAT-UI-038**: When the inline warning is shown for a non-safe conversion, the warning shall read: "Existing events cannot be converted and will display incorrectly."
- [x] **CAT-UI-032**: When the user saves a category edit in which the value type differs from the original, the system shall migrate all existing event values for that category according to the conversion table in `docs/llds/category-management.md`.
- [x] **CAT-UI-033**: For event values that cannot be converted to the new value type per the conversion table, the system shall leave those event values unchanged.
- [x] **CAT-UI-034**: When converting Text to Number, the system shall parse both bare numeric strings (e.g. `"3.5"`) and strings of the form `"<number> <unit>"` (e.g. `"3.5 kg"`), preserving the unit; unconvertible strings shall be left unchanged.
- [x] **CAT-UI-035**: When converting Text to Boolean, the system shall map `"Yes"` to `Boolean(true)` and `"No"` to `Boolean(false)`; all other strings shall be left unchanged.
- [ ] **CAT-UI-039**: When converting Number to Scale, the system shall convert values that are exact integers (no fractional part) in the range [1, 10] to the corresponding Scale value; non-integer Number values and values outside [1, 10] shall be left unchanged. When implemented, Scale → Number becomes a reversible pair and the conversion table shall be updated accordingly.
- [x] **CAT-UI-044**: When converting a None-type event to Exercise, the system shall produce ExerciseValue(sets=3, reps=15).
- [x] **CAT-UI-045**: When converting an Exercise-type event to Text, the system shall produce Text("$sets × $reps") using the Unicode multiplication sign.
- [x] **CAT-UI-046**: When converting a Text-type event to Exercise, the system shall attempt to parse the text as "$s × $r" or "$s x $r" where both s and r are integers ≥ 1; if parseable, it shall produce ExerciseValue(s, r); otherwise it shall leave the event value unchanged.
- [x] **CAT-UI-047**: The value type picker on the category edit screen shall include Exercise as a selectable option.

## Category Edit — Save Behavior

- [x] **CAT-UI-040**: When saving a new category, the system shall assign it a new UUID as its identifier.
- [x] **CAT-UI-041**: When saving a new category, the system shall assign it a `sortOrder` of `(min sortOrder across all categories) - 1`.
- [x] **CAT-UI-042**: When saving a new category, the system shall set `allowEmptyText` to `true`.
- [x] **CAT-UI-043**: When the category edit screen loads in create mode for a new MetaCategory, the system shall pre-select a default color by calling `getAndIncrementNextCategoryColorIndex(palette.size)` (LS-BE-081) and using `palette[index]` as the initial value of the color picker; the counter cycles within `[0, palette.size)` and is unaffected by category deletions. The user may override this by selecting any palette color before saving. When creating a new SubCategory, the color field opens in the inherit state (null) and the counter is not advanced.

## Category Hierarchy

### Category List — Color and Group Operations

- [ ] **CAT-UI-050**: Each category row in the category list shall display the resolved category color as a visual indicator (e.g., colored swatch or avatar).
- [ ] **CAT-UI-051**: The group picker (used for "Add to group" and "Move to another group") shall list all MetaCategories eligible to become the new parent (excluding the category's current parent in the "Move" case) and shall always include a "Create new group" option that creates a new MetaCategory and immediately sets it as the parent.
- [ ] **CAT-UI-052**: When the user selects "Add to group" or "Move to another group" for a category, the system shall preserve all of the category's current explicit field values as overrides (per DM-PROC-020).

### Category Edit — Subcategory Creation and Inheritance

- [x] **CAT-UI-053**: While editing a MetaCategory, the category edit screen shall display a "Create subcategory" action.
- [x] **CAT-UI-054**: When the user initiates subcategory creation, the system shall open the category edit screen in create mode with the parentId set to the MetaCategory's id and all inheritable fields (emoji, color, valueType) initialized as null.
- [x] **CAT-UI-055**: While editing a SubCategory, the emoji field shall offer an inherit option implemented as a toggle; when inherit is active, the field shows the parent's emoji in a subdued style and is not editable; when "Custom" is active, the field is editable.
- [x] **CAT-UI-056**: While editing a SubCategory, the color picker shall display an inherit swatch as the first item showing the parent's resolved color with a label; when `color` is null the inherit swatch shall be pre-selected; when `color` is non-null the inherit swatch shall be unselected and the current color (palette or out-of-palette swatch) shall be pre-selected; tapping the inherit swatch shall set `color` to null; the inherit swatch and the out-of-palette swatch (CAT-UI-015) may coexist when the SubCategory has an explicit out-of-palette color.
- [x] **CAT-UI-057**: While editing a SubCategory, the value type picker shall include a "Same as [ParentName] ([TypeName])" option as an additional selectable row; selecting it sets valueType to null (inherit from parent).
- [x] **CAT-UI-058**: While editing a SubCategory, the toolbar overflow menu shall contain "Remove from group" and "Move to another group".
- [x] **CAT-UI-059**: The category edit screen shall display a live preview card showing a mock timeline row rendered with the current effective name, resolved emoji, resolved color, and resolved value type.
- [x] **CAT-UI-060**: The live preview card shall update reactively as any field on the edit screen changes.

## Navigation

- [ ] **CAT-NAV-001**: When the user taps the FAB on the category list screen, the system shall navigate to the category edit screen in create mode for a new MetaCategory.
- [ ] **CAT-NAV-002**: When the user taps a category row on the category list screen, the system shall navigate to the category edit screen in edit mode for that category.
- [ ] **CAT-NAV-003**: When the user saves a category on the edit screen, the system shall navigate back to the category list.
- [ ] **CAT-NAV-004**: When the user taps back or cancel on the category edit screen without saving, the system shall navigate back to the category list without persisting any changes.
- [ ] **CAT-NAV-005**: When the user confirms deletion of a category from the edit screen toolbar, the system shall delete the category and navigate back to the category list.
- [x] **CAT-NAV-010**: When the user taps "Create subcategory" on a MetaCategory edit screen, the system shall navigate to the category edit screen in create mode with the parentId set to the MetaCategory's id.
- [x] **CAT-NAV-011**: When the user confirms "Remove from group" on a SubCategory edit screen, the system shall promote the SubCategory to a MetaCategory (per DM-PROC-019) and navigate back to the category list.
