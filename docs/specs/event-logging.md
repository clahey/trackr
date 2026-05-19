# Event Logging Specs

LLD: `docs/llds/event-logging.md`

---

## Timeline Display

- [x] **EL-UI-001**: The timeline screen shall display events grouped by the calendar day of their timestamp in the user's local timezone, with the most recent day first and the most recent event first within each day.
- [ ] **EL-UI-002**: Each event row shall display the category emoji, category name, a value summary formatted for the event's value type, and the time of day from the event's timestamp.
- [ ] **EL-UI-003**: Day group headers shall display "Today" for the current date, "Yesterday" for the prior date, and the full date for all older days.

## Category Filter

- [ ] **EL-UI-010**: The timeline screen shall display a horizontally scrollable row of category filter chips; an "All" chip shall appear first, followed by one chip per category.
- [x] **EL-UI-011**: While a category filter chip is active, the timeline shall display only events belonging to that category.
- [x] **EL-UI-012**: When the user taps the currently active category chip or the "All" chip, the system shall clear the active filter and display all events.
- [x] **EL-UI-013**: While a category filter is active, opening the quick-log sheet shall skip step 1 and open directly to step 2 with the filtered category pre-selected.
- [x] **EL-UI-013b**: When the active filter category is deleted, the system shall clear the filter and display all events.
- [ ] **EL-UI-014**: When the user taps an inactive category chip, the system shall set it as the active filter.
- [ ] **EL-UI-015**: The active filter chip shall be visually distinguished from inactive chips.
- [ ] **EL-UI-016**: When a category filter is applied, the timeline shall scroll to keep the same calendar day approximately at the top of the view; if that day has no matching events, the timeline shall scroll to the nearest earlier day that does.
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

- [x] **EL-UI-030**: The quick-log sheet shall present a two-step flow: step 1 is a grid of all categories (emoji + name); tapping a category advances to step 2.
- [ ] **EL-UI-031**: Step 2 of the quick-log sheet shall display a value input appropriate for the selected category's value type, an optional single-photo field (at most one image), an optional notes field, a tappable timestamp field, and a save button.
- [ ] **EL-UI-032**: The timestamp field in the quick-log sheet shall default to the time the sheet was opened, and shall be tappable to allow the user to edit the date and time.
- [ ] **EL-UI-033**: For a None-type category, step 2 shall omit the value input field, making the flow completable in three taps (FAB → category → save).
- [x] **EL-UI-034**: When the category selected in the quick-log sheet is deleted externally while the sheet is open at step 2, the system shall reset to step 1.

## Event Edit Screen

- [x] **EL-UI-040**: The event edit screen shall display editable fields for timestamp, value, notes, and images.
- [ ] **EL-UI-041**: The event edit screen shall display a delete action in the toolbar.
- [x] **EL-UI-042**: When the user taps the delete action on the event edit screen, the system shall show a confirmation dialog before deleting.
- [x] **EL-UI-043**: While an event carries an Unknown or ErrorValue, the value field on the edit screen shall be read-only; the timestamp, notes, and image fields shall remain editable.
- [ ] **EL-UI-044**: The event edit screen shall allow adding images via camera or gallery and removing individual images with no cap on total image count.

## Value Input

- [ ] **EL-UI-050**: For Scale-type categories, the value input shall be a horizontal slider with integer snap accepting values in the range 1–10.
- [ ] **EL-UI-051**: For Boolean-type categories, the value input shall be a two-button row labeled "Yes" and "No".
- [x] **EL-UI-052**: For Number-type categories, the value input shall consist of a numeric text field and a separate unit text field; the unit field shall be pre-filled from the category's unit and shall be user-editable; an empty unit field shall be saved as null (unitless).
- [ ] **EL-UI-053**: For Text-type categories, the value input shall be a multi-line text field.
- [x] **EL-UI-054**: While the selected Text-type category has `allowEmptyText` set to false, the system shall not allow saving with an empty text value.
- [ ] **EL-UI-055**: For Duration-type categories, the value input shall be three separate numeric fields for hours, minutes, and seconds; the value shall be stored as a `kotlin.time.Duration`.
- [x] **EL-UI-055b**: The Duration input shall reject negative values in any field and shall reject values ≥ 60 in the minutes and seconds fields; the system shall not allow saving an invalid duration.
- [x] **EL-UI-052b**: The Number value field shall not allow saving when the value field is empty or not parseable as a number.
- [ ] **EL-UI-056**: For Unknown or ErrorValue events, the value field shall display the error kind and raw value string in a read-only format.

## Image Handling

- [x] **EL-PROC-001**: When the user dismisses the quick-log sheet without saving after capturing an image, the system shall delete the captured image file.
- [x] **EL-PROC-002**: When the user navigates away from the event edit screen without saving, the system shall delete any image files captured during that editing session that are not part of the previously saved event.
- [ ] **EL-PROC-003**: If the process is killed before image cleanup can occur, the startup orphan scan (LS-BE-040) shall recover any unreferenced image files on next launch.

## Navigation

- [ ] **EL-NAV-001**: When the user taps the FAB on the timeline screen, the system shall open the quick-log sheet.
- [x] **EL-NAV-002**: When the user saves a new event in the quick-log sheet, the system shall dismiss the sheet and update the timeline.
- [ ] **EL-NAV-003**: When the user dismisses the quick-log sheet without saving, the system shall delete any unsaved captured image and return to the timeline.
- [ ] **EL-NAV-004**: When the user taps an event row on the timeline, the system shall navigate to the event edit screen for that event.
- [x] **EL-NAV-005**: When the user saves changes on the event edit screen, the system shall navigate back to the timeline.
- [x] **EL-NAV-006**: When the user confirms deletion on the event edit screen, the system shall delete the event and navigate back to the timeline.
- [ ] **EL-NAV-007**: When the user navigates back from the event edit screen without saving, the system shall delete any newly captured images not part of the previously saved event and return to the timeline.
