# Local Storage Specs

LLD: `docs/llds/local-storage.md`

---

## Repository Interface

- [x] **LS-BE-001**: The system shall expose all category and event reads as `Flow`, so that UI layers receive updates reactively whenever the underlying data changes.
- [x] **LS-BE-002**: The system shall expose all category and event writes as `suspend` functions callable from a coroutine context.
- [x] **LS-BE-003**: `saveCategory` and `saveEvent` shall each perform an upsert — inserting the record if it does not exist, updating it if it does.
- [x] **LS-BE-004**: `getEvents` shall accept optional start and end `Instant` bounds; when a bound is null the query shall be unbounded on that side.

## Category Persistence

- [x] **LS-BE-010**: `getCategories` shall return categories grouped hierarchically: top-level categories ordered by their own `sortOrder ASC`, with each SubCategory listed immediately after its parent and ordered by the SubCategory's own `sortOrder ASC` among its siblings.
- [x] **LS-BE-011**: `saveCategory` shall persist the `sortOrder` it is given as-is, without recomputing it; assigning a new category's `sortOrder` (e.g. `currentMin - 1` to place it at the top) is the caller's responsibility.
- [x] **LS-BE-012**: `reorderCategories` shall accept an ordered list of category IDs and reassign sequential `sortOrder` values (0, 1, 2…) matching that order in a single operation.
- [x] **LS-BE-013**: `getEventCountForCategory` shall return a `Flow<Int>` that reflects the live count of events for a given category.
- [x] **LS-BE-014**: `getLatestEventTimestampIncludingChildren(categoryId)` shall return, in a single aggregate query, the greatest event `timestamp` among the category's own events and those of all its SubCategories, or null when neither has any. It is a `suspend` one-shot rather than a `Flow`: the caller is a fired alarm computing a single decision, with no subscriber to update.

## Event Persistence

- [x] **LS-BE-020**: `getEvents` shall return events ordered by `timestamp DESC`, then `createdAt DESC`, then `id ASC` as a tiebreaker.
- [x] **LS-BE-021**: `getEventsByCategory` shall return events in the same order as `getEvents`.

## Deletion and File Lifecycle

- [x] **LS-BE-030**: When deleting an event, the system shall delete the database row before deleting any associated image files, so that a crash between the two steps leaves recoverable orphaned files rather than a valid DB row with missing files.
- [x] **LS-BE-031**: When deleting a category, the system shall collect image paths from all child events, delete the category row (which cascades to child event rows atomically via Room), then delete the collected image files.
- [x] **LS-BE-032**: When `saveEvent` is called with a modified image list, the system shall read the previous image paths, upsert the event, then delete any paths that were removed — ensuring that a failed upsert leaves storage intact.
- [x] **LS-BE-033**: `deleteEventFiles` shall accept a list of absolute image path strings and delete each via `ImageStore.delete`; it shall be a separate function from `deleteEvent` so that callers may defer file cleanup (e.g., to support undo) without retaining a live DB row.

## Startup Orphan Recovery

- [x] **LS-BE-040**: `onStartup` shall scan the image storage directory and delete any file not referenced by a current event row in the database.
- [x] **LS-BE-041**: `onStartup` shall be launched once per app process, at a point app-shell chooses (`docs/specs/app-shell.md`, APP-PROC-001) and not necessarily at process start; it is fire-and-forget and not guaranteed to complete — or even begin — before the first UI frame. This is acceptable only because `onStartup` currently does nothing but delete unreferenced image files (LS-BE-040), a purely additive cleanup that nothing in the UI depends on. If `onStartup` ever takes on a responsibility the UI *does* depend on (e.g. a data migration), this guarantee must be revisited so the first frame actually blocks on completion.

## TypeConverters

- [x] **LS-BE-050**: The `EventValueConverter` shall encode and decode `EventValue?` to and from a nullable JSON string, delegating full encode/decode logic (including `ErrorValue` verbatim passthrough and invariant repair) to the rules specified in `docs/specs/data-model.md § EventValue TypeConverter`.
- [x] **LS-BE-051**: The `InstantConverter` shall encode `java.time.Instant` as epoch milliseconds (Long) and decode a Long back to an `Instant`.
- [x] **LS-BE-052**: The `DurationAsSecondsSerializer` shall encode `kotlin.time.Duration` as total seconds (Long) and decode a Long back to a `Duration`.
- [x] **LS-BE-053**: The `StringListConverter` shall encode `List<String>` as a JSON array string; on decode failure it shall return `emptyList()` rather than propagating an exception.
- [x] **LS-BE-054**: The `ValueTypeConverter` shall encode known `ValueType` variants as fixed lowercase name strings and encode `Unknown(raw)` as the raw string verbatim; unknown strings decoded from the database shall produce `ValueType.Unknown(raw)`.

## DataStore

- [x] **LS-BE-080**: The system shall persist a `next_category_color_index` integer in DataStore Preferences, initialized to 0 and never reset.
- [x] **LS-BE-081**: `getAndIncrementNextCategoryColorIndex(paletteSize: Int)` shall atomically return the current stored index and store `(current + 1) % paletteSize`, keeping the stored value in `[0, paletteSize)` so that concurrent calls never return the same index and the counter cycles at the caller-supplied palette size.
- [x] **LS-BE-093**: `addStarterCategories(specs)` shall, within a single transaction, read the existing category names and insert only the specs whose name is not already present (case-insensitive), assigning each a top-of-list `sortOrder` preserving the given order, and return the number inserted. The insert-selection and ordering logic is the pure `starterCategoriesToInsert` function shared with the test repository.

## ImageStore

- [x] **LS-BE-060**: `ImageStore.newFile` shall return a new `File` with a UUID-based name in the app-private image directory for the UI to write to.
- [x] **LS-BE-061**: `ImageStore.delete` shall delete the file at the given absolute path; if the file does not exist the call shall be a no-op.
- [x] **LS-BE-062**: `ImageStore.allStoredPaths` shall return the absolute paths of all files currently in the image storage directory.

## Auto Backup

- [x] **LS-BE-090**: The manifest shall declare `android:allowBackup="true"` and reference both `android:dataExtractionRules` (API 31+) and `android:fullBackupContent` (API 23–30) backup rule files.
- [x] **LS-BE-091**: The backup rules shall include the Room database (`domain="database" path="trackr.db"`), image files (`domain="file" path="images"`), and DataStore preferences (`domain="file" path="datastore"`); no other app data is included.
- [x] **LS-BE-092**: Both the API 31+ `data_extraction_rules.xml` and the pre-31 `backup_rules.xml` shall declare identical include sets so that backup behavior is consistent across API levels.

## Schema Integrity

- [x] **LS-BE-070**: The Room database shall have destructive migration disabled; any schema change must be accompanied by an explicit migration.
- [x] **LS-BE-071**: The `EventEntity.categoryId` foreign key shall be declared with `CASCADE DELETE` so that deleting a category atomically removes all its child events.
