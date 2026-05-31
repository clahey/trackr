# Event Logging Specs

LLD: `docs/llds/event-logging.md`

---

## Timeline Display

- [x] **EL-UI-001**: The timeline screen shall display events grouped by the calendar day of their timestamp in the user's local timezone, with the most recent day first and the most recent event first within each day.
- [x] **EL-UI-002**: Each event row shall display the category emoji, category name, a value summary formatted for the event's value type, and the time of day from the event's timestamp. (Category fields are blank when the event is orphaned.)
- [x] **EL-UI-079**: While an event has one or more attached images, each event row shall display a 48×48dp thumbnail of the first image to the left of the time label.
- [x] **EL-UI-004**: Each event row shall be presented as an elevated card visually distinct from the screen background.
- [x] **EL-UI-005**: Each event row shall display the category color as a filled circle avatar on the left; the category emoji shall be centered inside the circle using the WCAG foreground color computed by `foregroundColorForBackground(categoryColor)`.
- [x] **EL-UI-003**: Day group headers shall display "Today" for the current date, "Yesterday" for the prior date, and the full date for all older days.

## Category Filter

- [x] **EL-UI-010**: The timeline screen shall display a horizontally scrollable row of category filter chips; an "All" chip shall appear first, followed by one chip per MetaCategory.
- [x] **EL-UI-011**: While ActiveFilter.TopLevel is set, the timeline shall display events belonging to the MetaCategory and all of its SubCategories. While ActiveFilter.Sub is set, the timeline shall display only events belonging to that SubCategory.
- [x] **EL-UI-012**: When the user taps the "All" chip, the system shall clear the active filter. When the user taps the active MetaCategory chip while ActiveFilter.TopLevel is set, the system shall clear the filter to All. When the user taps a MetaCategory chip while ActiveFilter.Sub is set for a SubCategory of that MetaCategory, the system shall set the filter to ActiveFilter.TopLevel for that MetaCategory.
- [x] **EL-UI-013**: While ActiveFilter.Sub is set, opening the quick-log sheet shall open directly to step 2 for the selected SubCategory. While ActiveFilter.TopLevel is set and the MetaCategory has SubCategories, opening the quick-log sheet shall open step 1 in the drill-down view for that MetaCategory. While ActiveFilter.TopLevel is set and the MetaCategory has no SubCategories, opening the quick-log sheet shall open directly to step 2.
- [x] **EL-UI-013b**: When a category referenced by the active filter is deleted externally: if the filter is ActiveFilter.TopLevel and its MetaCategory is deleted, the system shall clear the filter to All; if the filter is ActiveFilter.Sub and its SubCategory is deleted, the system shall clear the filter to All; if the filter is ActiveFilter.Sub and its parent MetaCategory is deleted (the SubCategory is promoted to MetaCategory), the system shall promote the filter to ActiveFilter.TopLevel for the newly-promoted category.
- [x] **EL-UI-014**: When the user taps an inactive MetaCategory chip, the system shall set ActiveFilter.TopLevel for that MetaCategory.
- [ ] **EL-UI-015**: Each MetaCategory filter chip shall display a colored border using the category's resolvedColor when unselected and a filled background using the category's resolvedColor when active (ActiveFilter.TopLevel). SubCategory chips shall follow the same fill/border convention, active when ActiveFilter.Sub is set for that subcategory.
- [x] **EL-UI-016**: When a category filter is applied, the timeline shall scroll to keep the same calendar day approximately at the top of the view; if that day has no matching events, the timeline shall scroll to the nearest earlier day that does.
- [x] **EL-UI-017**: When the user activates a category filter from the unfiltered state (All), the system shall record the current top day as the pre-filter scroll position.
- [x] **EL-UI-018**: When the user switches from one active category filter to another, the system shall not update the recorded pre-filter scroll position, so that clearing the filter always returns to the position recorded when filtering first began.
- [x] **EL-UI-019**: When the user manually scrolls the timeline while a filter is active, the system shall discard the recorded pre-filter scroll position; the system-initiated anchor scroll (EL-UI-016) shall not discard it.
- [x] **EL-UI-019b**: When the user clears an active category filter and the pre-filter scroll position has not been discarded, the timeline shall restore scroll to the recorded pre-filter position.

## Swipe-to-Delete and Undo

- [x] **EL-UI-020**: When the user swipes an event row, the system shall immediately delete the event from the database and replace the row in place with an undo placeholder.
- [x] **EL-UI-021**: While an undo placeholder is visible, the system shall display a button in that position allowing the user to restore the deleted event.
- [x] **EL-UI-022**: When the user taps the undo button, the system shall restore the deleted event to its original position and remove the placeholder.
- [x] **EL-UI-023**: When the user performs any mutating action while an undo placeholder is visible — swiping another event, saving a new event, or opening an event for editing — the system shall discard the placeholder without restoring the event.
- [x] **EL-UI-023b**: When the category of a pending-delete event is deleted while the undo placeholder is visible, the system shall dismiss the placeholder without restoring the event.
- [ ] **EL-UI-023c**: The undo placeholder row shall not be tappable for navigation; only the restore button within it responds to taps.

## Quick-Log Sheet

- [x] **EL-UI-030**: The quick-log sheet shall present a two-step flow: step 1 is a grid of MetaCategory items (resolved emoji + name); tapping a MetaCategory with no SubCategories advances to step 2; tapping a MetaCategory with SubCategories replaces the grid with a drill-down view for that MetaCategory.
- [ ] **EL-UI-031**: Step 2 of the quick-log sheet shall display a value input appropriate for the selected category's value type, an optional single-photo field (at most one image), an optional notes field, a tappable timestamp field, and a save button.
- [x] **EL-UI-031c**: Step 2 of the quick-log sheet shall display a header row containing a back arrow on the left and the selected category's resolved emoji and name; tapping the back arrow shall return to step 1.
- [x] **EL-UI-031a**: While no image is attached in the quick-log sheet, the system shall display an "Add image" button; tapping it shall present a choice of "Take photo" (camera) or "Choose from gallery".
- [x] **EL-UI-031b**: While an image is attached in the quick-log sheet, the system shall display the attached photo as a full-width thumbnail with a Remove button and a Replace button below it; tapping Remove shall delete the image file and clear the attached image; tapping Replace shall present the same "Take photo" / "Choose from gallery" choice and, on selection, delete the previous image file and attach the new one.
- [x] **EL-UI-044a**: The event edit screen shall display an "Add image" button that presents a choice of "Take photo" (camera) or "Choose from gallery"; each image added is appended to the event's image list with no cap on total count.
- [ ] **EL-UI-032**: The timestamp field in the quick-log sheet shall default to the time the sheet was opened, and shall be tappable to allow the user to edit the date and time.
- [x] **EL-UI-033**: For a None-type category, step 2 shall omit the value input field, making the flow completable in three taps (FAB → category → save).
- [x] **EL-UI-034**: When the category selected in the quick-log sheet is deleted externally while the sheet is open at step 2, the system shall reset to step 1.
- [ ] **EL-UI-077**: When the user saves a new event via the quick-log sheet, the system shall dismiss the sheet and scroll the timeline to make the newly saved event visible.

## Event Edit Screen

- [x] **EL-UI-040**: The event edit screen shall display editable fields for timestamp, value, notes, and images.
- [x] **EL-UI-041**: The event edit screen shall display a delete action in the toolbar.
- [x] **EL-UI-045**: When the event edit screen loads and the requested event is not found in the repository, the system shall navigate back to the timeline and display a snackbar on the timeline screen reading "Event not found."
- [x] **EL-UI-042**: When the user taps the delete action on the event edit screen, the system shall show a confirmation dialog before deleting.
- [x] **EL-UI-043**: While an event carries an Unknown or ErrorValue, the value field on the edit screen shall be read-only; the timestamp, notes, and image fields shall remain editable.
- [x] **EL-UI-046**: The event edit screen shall display the category emoji and name as a read-only header above the editable fields; the header is omitted when the category cannot be resolved.
- [x] **EL-UI-044**: The event edit screen shall allow adding images via camera or gallery and removing individual images with no cap on total image count.
- [x] **EL-UI-044c**: The event edit screen shall render each attached image as a full-width thumbnail with a remove button overlaid in the top-right corner.
- [x] **EL-UI-044b**: When the user removes an image on the event edit screen, the system shall remove it from the displayed list; the file shall be deleted when the event is saved (not immediately).

## Value Input

- [x] **EL-UI-050**: For Scale-type categories, the value input shall be a horizontal slider with integer snap accepting values in the range 1–10.
- [ ] **EL-UI-051**: For Boolean-type categories, the value input shall be a two-button row labeled "Yes" and "No".
- [ ] **EL-UI-051b**: When a Boolean value input is first displayed with no prior selection, both buttons shall appear unpressed; the system shall not allow saving until one button has been tapped.
- [x] **EL-UI-052**: For Number-type categories, the value input shall consist of a numeric text field and a separate unit text field; the unit field shall be pre-filled from the category's unit and shall be user-editable; an empty unit field shall be saved as null (unitless).
- [x] **EL-UI-052b**: The Number value field shall not allow saving when the value field is empty or not parseable as a number.
- [ ] **EL-UI-052c**: While the user is editing a Number value or unit field, the system shall preserve the exact text entered — including partial input such as a trailing decimal point, leading sign, or empty field — without snapping back to the previously committed value.
- [ ] **EL-UI-053**: For Text-type categories, the value input shall be a multi-line text field.
- [x] **EL-UI-054**: While the selected Text-type category has `allowEmptyText` set to false, the system shall not allow saving with an empty text value.
- [ ] **EL-UI-055**: For Duration-type categories, the value input shall be three separate numeric fields for hours, minutes, and seconds; the value shall be stored as a `kotlin.time.Duration`.
- [x] **EL-UI-055b**: The Duration input shall reject negative values in any field and shall reject values ≥ 60 in the minutes and seconds fields; the system shall not allow saving an invalid duration.
- [ ] **EL-UI-055c**: While the user is editing a Duration field, the system shall preserve an empty field as entered; at save time an empty field shall be treated as zero.
- [ ] **EL-UI-055d**: When a Duration value is initialized for display, leading-zero components shall appear as empty rather than "0": the hours field is empty when hours is zero; the minutes field is empty when both hours and minutes are zero; the seconds field always displays a value, showing "0" for zero seconds.
- [ ] **EL-UI-056**: For Unknown or ErrorValue events, the value field shall display the error kind and raw value string in a read-only format.
- [x] **EL-UI-057**: When save is invoked on either the quick-log sheet or the event edit screen, the system shall validate the value via a shared `validateValueForSave(value, category)` function: (a) if `value.toEventValue()` is null and `value` is not `ValueUIState.None`, save shall be blocked — this covers all partial or invalid inputs (EL-UI-051b, EL-UI-052b, EL-UI-055b, EL-UI-059b); (b) if `category.resolvedValueType` is `Text` and `allowEmptyText` is false and `value.toEventValue()` is an empty `TextValue`, save shall be blocked (EL-UI-054). All other states, including `None` with a null result, are valid.
- [ ] **EL-UI-057b**: When save is blocked by a validation error, the system shall highlight the failing input field in an error state; both the event edit screen and the quick-log sheet shall use the same visual error treatment.
- [x] **EL-UI-058**: When the quick-log sheet displays a Number, Text, or Duration value input in step 2, the system shall automatically focus the input field (the hours field for Duration) so the software keyboard is raised without an additional tap.
- [x] **EL-UI-059**: For Exercise-type categories, the value input shall consist of two integer-only fields labeled "Sets" and "Reps" (integer keyboard, no decimal point); both fields shall require a value of 1 or greater before saving is permitted. Initial values are seeded per EL-UI-078.
- [ ] **EL-UI-059b**: While the user is editing a Sets or Reps field, the system shall preserve the exact text entered — including an empty or non-parseable field — without discarding or snapping the change; saving shall be blocked if either field is empty or less than 1.
- [x] **EL-UI-060**: Exercise values shall be displayed in the timeline and event edit screen as "$sets × $reps" using the Unicode multiplication sign (×, U+00D7).

## Category Hierarchy — Filter and Quick-Log

- [x] **EL-UI-070**: When a MetaCategory chip is active (ActiveFilter.TopLevel), the system shall display that MetaCategory's SubCategory chips inline to the right of the MetaCategory chip in the same scrollable row, visually grouped.
- [x] **EL-UI-071**: When the user taps a SubCategory chip, the system shall set ActiveFilter.Sub for that SubCategory; the parent MetaCategory chip shall return to an unselected (border) style while the SubCategory chips remain visible; the tapped SubCategory chip shall appear active (filled).
- [x] **EL-UI-072**: Each MetaCategory cell in the quick-log step 1 grid shall display the resolved category color as a colored border.
- [x] **EL-UI-073**: When the user taps a MetaCategory cell in the quick-log step 1 grid and that MetaCategory has SubCategories, the system shall replace the grid with a drill-down view showing a back button, the MetaCategory name as a header, a full-width "Log to [Name] directly" tile, and the SubCategory tiles in the same grid style; tapping the back button returns to the top-level grid.
- [x] **EL-UI-074**: The first tile in the drill-down view shall allow the user to log an event directly to the MetaCategory (not to a SubCategory); tapping it shall advance to step 2 with the MetaCategory as the selected category.
- [x] **EL-UI-075**: While ActiveFilter.TopLevel is active for a MetaCategory that has SubCategories, opening the quick-log sheet shall present step 1 in the drill-down view for that MetaCategory.
- [x] **EL-UI-076**: When a MetaCategory is deleted while the quick-log sheet step 1 is showing that MetaCategory's drill-down view, the system shall return to the top-level grid.

## Value Type Mismatch UI

- [x] **EL-UI-061**: When an event row in the timeline has a non-null value that does not satisfy `matchesValueType(value, category.valueType)`, the system shall display the raw stored value (formatted by `formatValue`) followed by a warning indicator; the value shall not be corrected in the database by viewing the timeline.
- [x] **EL-UI-062**: When the value input is given a non-null `value` and a `valueType` such that `matchesValueType(value, valueType)` is false, the system shall display a value action banner above the input reading "Stored value doesn't match the category type.", and shall render the editable input for the value's own type (not the target `valueType`) so the user can refine the existing value before accepting the banner action; for `ErrorValue` and when the outcome is `Discard`, no editable input is shown.
- [x] **EL-UI-063**: When the outcome of `convertOrDefault(effectiveValue, targetType)` is `Converted(v)`, the banner action button shall be labeled "Convert to [description]" where description is a human-readable rendering of `v`; `effectiveValue` is `editableState.toEventValue()` when that yields a non-null result, otherwise `originalValue`.
- [x] **EL-UI-064**: When the outcome is `UsedDefault(v)`, the banner action button shall be labeled "Replace with default: [description]" where description is a human-readable rendering of `v`; the outcome updates live as the user edits the sub-field.
- [x] **EL-UI-065**: When the outcome is `Discard` (category type is None or Unknown), the banner action button shall be labeled "Discard value".
- [x] **EL-UI-066**: When the user taps the banner action button, the system shall dismiss the banner and update the value: for `Converted(v)` or `UsedDefault(v)`, to `v`; for `Discard`, to null.
- [x] **EL-UI-067**: When `toValueUIState(valueType)` is called with a null event value, the system shall return `ValueUIState.None` if `valueType` is `None` or `Unknown`; otherwise it shall return the default empty editable UI state for `valueType` (equivalent to `defaultValueUIStateForType(valueType)`).
- [x] **EL-UI-068**: When the user selects a category in the quick-log sheet and has not yet interacted with the value input during this session, the system shall seed the value from the category's `resolvedDefaultValue` per EL-UI-078 regardless of the previously displayed type.
- [x] **EL-UI-078**: When seeding a new event value for a category, the system shall: if `category.resolvedDefaultValue` is non-null and `matchesValueType(resolvedDefaultValue, resolvedValueType)` is true, use `resolvedDefaultValue.toValueUIState()`; otherwise use `defaultValueUIStateForType(resolvedValueType)`.
- [x] **EL-UI-068b**: When the user selects a category in the quick-log sheet and has previously interacted with the value input during this session, the system shall: (a) unwrap any `Mismatched` state — using its editable sub-state if present, or reconstructing a UI state from the original stored value if not (so the value is preserved even after passing through a Discard-outcome category); (b) if the effective state's type matches the new category's resolved type, preserve it verbatim — for `Number`, variant match is sufficient and the unit field is not compared; (c) if the effective state's type does not match and yields a valid `EventValue`, display the value action mismatch banner; (d) if the effective state yields no valid `EventValue` (partial or invalid input), seed from `resolvedDefaultValue` per EL-UI-078.
- [x] **EL-UI-068c**: Value input edits made during a quick-log session shall persist across category switches within that session; only dismissing the sheet shall clear them.

## Image Handling

- [x] **EL-PROC-001**: When the user dismisses the quick-log sheet without saving, the system shall delete any attached image file.
- [x] **EL-PROC-002**: When the user navigates away from the event edit screen without saving, the system shall delete any image files captured during that editing session that are not part of the previously saved event.
- [ ] **EL-PROC-003**: If the process is killed before image cleanup can occur, the startup orphan scan (LS-BE-040) shall recover any unreferenced image files on next launch.

## Navigation

- [ ] **EL-NAV-001**: When the user taps the FAB on the timeline screen, the system shall open the quick-log sheet.
- [x] **EL-NAV-002**: When the user saves a new event in the quick-log sheet, the system shall dismiss the sheet and update the timeline.
- [x] **EL-NAV-002b**: After the quick-log sheet is dismissed for any reason (save or user dismiss), the system shall reset all form state — including the save result — so that the sheet opens in step 1 with a clean state on the next open.
- [ ] **EL-NAV-003**: When the user dismisses the quick-log sheet without saving, the system shall delete any unsaved captured image and return to the timeline.
- [x] **EL-NAV-003b**: When the user presses the system back button or performs a back edge swipe while the quick-log sheet is at step 2, the system shall return to step 1 without dismissing the sheet.
- [x] **EL-NAV-003c**: When the user presses the system back button or performs a back edge swipe while the quick-log sheet is at step 1 in the drill-down view, the system shall return to the top-level category grid without dismissing the sheet.
- [ ] **EL-NAV-004**: When the user taps an event row on the timeline, the system shall navigate to the event edit screen for that event.
- [x] **EL-NAV-005**: When the user saves changes on the event edit screen, the system shall navigate back to the timeline.
- [x] **EL-NAV-006**: When the user confirms deletion on the event edit screen, the system shall delete the event and navigate back to the timeline.
- [ ] **EL-NAV-007**: When the user navigates back from the event edit screen without saving, the system shall delete any newly captured images not part of the previously saved event and return to the timeline.
