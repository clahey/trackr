# Category Management Specs

LLD: `docs/llds/category-management.md`

---

## Category List

- [ ] **CAT-UI-001**: The system shall display all categories on the list screen ordered by `sortOrder` ascending, showing emoji, name, and value type for each row.
- [ ] **CAT-UI-002**: The category list screen shall display a drag handle on each row; when the user drops a row after dragging, the system shall persist the new order via `reorderCategories`.
- [ ] **CAT-UI-003**: When the user long-presses a category row, the system shall present a context menu containing a delete action.
- [ ] **CAT-UI-004**: When the user initiates deletion of a category that has zero events, the system shall delete it immediately without a confirmation dialog.
- [ ] **CAT-UI-005**: When the user initiates deletion of a category that has one or more events, the system shall show a confirmation dialog stating the number of events that will be permanently deleted.
- [ ] **CAT-UI-006**: When the user confirms deletion of a category (from either the list context menu or the edit screen toolbar), the system shall delete the category and all its associated events.

## Category Edit — Display

- [ ] **CAT-UI-010**: The category edit screen shall display input fields for name, emoji, color, and value type.
- [ ] **CAT-UI-011**: While value type is Number, the category edit screen shall display a unit input field; for all other value types the unit field shall be hidden.
- [ ] **CAT-UI-012**: While editing an existing category, the edit screen shall display a delete action in the toolbar.
- [ ] **CAT-UI-013**: While creating a new category, the edit screen shall not display a delete action.
- [ ] **CAT-UI-014**: The color field shall display the preset palette (defined in `docs/llds/theme.md § Preset Palette`) and require a selection at all times; no free-form color entry in v1.
- [ ] **CAT-UI-015**: When editing an existing category whose color is not in the preset palette, the color picker shall display the current color as a distinct swatch above the preset palette, pre-selected; the user may keep it or replace it by tapping a preset.
- [ ] **CAT-UI-016**: The out-of-palette current color swatch shall not be shown when creating a new category.

## Category Edit — Validation

- [ ] **CAT-UI-020**: When the user attempts to save a category with a name that is empty or contains only whitespace, the system shall display an inline error and not save.
- [ ] **CAT-UI-021**: When the user attempts to save a category with an empty emoji field, the system shall display an inline error and not save.
- [ ] **CAT-UI-022**: When the user attempts to save a category whose emoji field contains more than one grapheme cluster, the system shall display an inline error and not save.

## Category Edit — ValueType Change Warning

- [ ] **CAT-UI-030**: When the user changes the value type of a category that has one or more existing events, the system shall display a warning that historical events may display incorrectly.
- [ ] **CAT-UI-031**: The value type change warning shall inform without blocking — the user may proceed with or dismiss the change after seeing it.

## Category Edit — Save Behavior

- [ ] **CAT-UI-040**: When saving a new category, the system shall assign it a new UUID as its identifier.
- [ ] **CAT-UI-041**: When saving a new category, the system shall assign it a `sortOrder` of `currentMin - 1`, placing it at the top of the list.
- [ ] **CAT-UI-042**: When saving a new category, the system shall set `allowEmptyText` to `true`.
- [ ] **CAT-UI-043**: When saving a new category, the system shall assign a default color by calling `getAndIncrementNextCategoryColorIndex(palette.size)` (LS-BE-081) and using `palette[index]` as a direct lookup; the counter cycles within `[0, palette.size)` and is unaffected by category deletions.

## Navigation

- [ ] **CAT-NAV-001**: When the user taps the FAB on the category list screen, the system shall navigate to the category edit screen in create mode.
- [ ] **CAT-NAV-002**: When the user taps a category row on the category list screen, the system shall navigate to the category edit screen in edit mode for that category.
- [ ] **CAT-NAV-003**: When the user saves a category on the edit screen, the system shall navigate back to the category list.
- [ ] **CAT-NAV-004**: When the user taps back or cancel on the category edit screen without saving, the system shall navigate back to the category list without persisting any changes.
- [ ] **CAT-NAV-005**: When the user confirms deletion of a category from the edit screen toolbar, the system shall delete the category and navigate back to the category list.
