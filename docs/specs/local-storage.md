# Local Storage Specs

LLD: `docs/llds/local-storage.md`

---

## Repository Interface

- [ ] **LS-BE-001**: The system shall expose all category and event reads as `Flow`, so that UI layers receive updates reactively whenever the underlying data changes.
- [ ] **LS-BE-002**: The system shall expose all category and event writes as `suspend` functions callable from a coroutine context.
- [ ] **LS-BE-003**: `saveCategory` and `saveEvent` shall each perform an upsert — inserting the record if it does not exist, updating it if it does.
- [ ] **LS-BE-004**: `getEvents` shall accept optional start and end `Instant` bounds; when a bound is null the query shall be unbounded on that side.

## Category Persistence

- [x] **LS-BE-010**: The system shall store categories ordered by `sortOrder ASC`; `getCategories` shall return them in this order.
- [x] **LS-BE-011**: When `saveCategory` is called for a new category, the system shall assign it a `sortOrder` of `currentMin - 1`, placing it at the top of the list.
- [x] **LS-BE-012**: `reorderCategories` shall accept an ordered list of category IDs and reassign sequential `sortOrder` values (0, 1, 2…) matching that order in a single operation.
- [x] **LS-BE-013**: `getEventCountForCategory` shall return a `Flow<Int>` that reflects the live count of events for a given category.

## Event Persistence

- [x] **LS-BE-020**: `getEvents` shall return events ordered by `timestamp DESC`, then `createdAt DESC`, then `id ASC` as a tiebreaker.
- [x] **LS-BE-021**: `getEventsByCategory` shall return events in the same order as `getEvents`.

## Deletion and File Lifecycle

- [x] **LS-BE-030**: When deleting an event, the system shall delete the database row before deleting any associated image files, so that a crash between the two steps leaves recoverable orphaned files rather than a valid DB row with missing files.
- [x] **LS-BE-031**: When deleting a category, the system shall collect image paths from all child events, delete the category row (which cascades to child event rows atomically via Room), then delete the collected image files.
- [x] **LS-BE-032**: When `saveEvent` is called with a modified image list, the system shall read the previous image paths, upsert the event, then delete any paths that were removed — ensuring that a failed upsert leaves storage intact.

## Startup Orphan Recovery

- [x] **LS-BE-040**: `onStartup` shall scan the image storage directory and delete any file not referenced by a current event row in the database.
- [ ] **LS-BE-041**: `onStartup` shall be called once per app process start before any user-visible UI is shown.

## TypeConverters

- [ ] **LS-BE-050**: The `EventValueConverter` shall encode and decode `EventValue?` to and from a nullable JSON string, delegating full encode/decode logic (including `ErrorValue` verbatim passthrough and invariant repair) to the rules specified in `docs/specs/data-model.md § EventValue TypeConverter`.
- [x] **LS-BE-051**: The `InstantConverter` shall encode `java.time.Instant` as epoch milliseconds (Long) and decode a Long back to an `Instant`.
- [ ] **LS-BE-052**: The `DurationAsSecondsSerializer` shall encode `kotlin.time.Duration` as total seconds (Long) and decode a Long back to a `Duration`.
- [x] **LS-BE-053**: The `StringListConverter` shall encode `List<String>` as a JSON array string; on decode failure it shall return `emptyList()` rather than propagating an exception.
- [ ] **LS-BE-054**: The `ValueTypeConverter` shall encode known `ValueType` variants as fixed lowercase name strings and encode `Unknown(raw)` as the raw string verbatim; unknown strings decoded from the database shall produce `ValueType.Unknown(raw)`.

## DataStore

- [x] **LS-BE-080**: The system shall persist a `next_category_color_index` integer in DataStore Preferences, initialized to 0 and never reset.
- [x] **LS-BE-081**: `getAndIncrementNextCategoryColorIndex(paletteSize: Int)` shall atomically return the current stored index and store `(current + 1) % paletteSize`, keeping the stored value in `[0, paletteSize)` so that concurrent calls never return the same index and the counter cycles at the caller-supplied palette size.

## ImageStore

- [ ] **LS-BE-060**: `ImageStore.newFile` shall return a new `File` with a UUID-based name in the app-private image directory for the UI to write to.
- [ ] **LS-BE-061**: `ImageStore.delete` shall delete the file at the given absolute path; if the file does not exist the call shall be a no-op.
- [ ] **LS-BE-062**: `ImageStore.allStoredPaths` shall return the absolute paths of all files currently in the image storage directory.

## Schema Integrity

- [ ] **LS-BE-070**: The Room database shall have destructive migration disabled; any schema change must be accompanied by an explicit migration.
- [ ] **LS-BE-071**: The `EventEntity.categoryId` foreign key shall be declared with `CASCADE DELETE` so that deleting a category atomically removes all its child events.
