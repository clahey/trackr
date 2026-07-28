# Local Storage

## Context and Design Philosophy

This segment implements the persistence layer using Room (SQLite). It owns: the Room database, entities, DAOs, TypeConverters, entity↔domain mappers, and `LocalTrackrRepository` — the concrete implementation of `TrackrRepository`.

The `TrackrRepository` interface is also defined here; it is the sole seam between the domain/UI layers and storage. Everything above the interface speaks domain types (`Category`, `Event`, `EventValue`); everything below speaks Room entities and SQL.

Image files are a second resource managed by this segment. File lifecycle (write on capture, delete on event/category deletion, orphan recovery at startup) is the repository's responsibility, not the UI's.

## TrackrRepository Interface

```kotlin
interface TrackrRepository {
    // Categories
    fun getCategories(): Flow<List<Category>>  // hierarchical sort: MetaCategories by sortOrder, SubCategories after parent
    fun getCategoryById(id: String): Flow<Category?>
    suspend fun saveCategory(category: Category)   // upsert; caller sets sortOrder
    suspend fun saveCategoryAndMigrateEvents(category: Category, fromType: ValueType)  // atomic upsert + event migration
    suspend fun deleteCategory(id: String)  // promotes SubCategories if MetaCategory; atomic
    suspend fun reorderCategories(orderedIds: List<String>)  // reassigns sortOrder to match list
    suspend fun moveCategory(category: Category, orderedSiblingIds: List<String>)  // reparent + reindex destination group in one transaction
    suspend fun moveCategoryAndMigrateEvents(category: Category, orderedSiblingIds: List<String>, fromType: ValueType)  // moveCategory + event migration when the effective valueType changed
    fun getEventCountForCategory(categoryId: String, includeSubCategoriesWithNullType: Boolean = false): Flow<Int>
    fun getSubCategoryCount(categoryId: String): Flow<Int>

    // Events
    fun getEvents(start: Instant? = null, end: Instant? = null): Flow<List<Event>>
    fun getEventsByCategory(categoryId: String): Flow<List<Event>>
    fun getEventsByCategoryIdIncludingChildren(id: String): Flow<List<Event>>  // id + all SubCategories (any valueType)
    fun getEventById(id: String): Flow<Event?>
    suspend fun saveEvent(event: Event)            // upsert
    suspend fun deleteEvent(id: String)

    // Preferences
    suspend fun getAndIncrementNextCategoryColorIndex(paletteSize: Int): Int  // atomically returns current index then increments

    // Reminders (persistence only — no AlarmManager access; see docs/llds/reminders.md)
    fun getReminderForCategory(categoryId: String): Flow<Reminder?>
    suspend fun saveReminder(reminder: Reminder)                                  // upsert, standalone
    suspend fun saveCategoryWithReminder(category: Category, reminder: Reminder?) // atomic: category upsert + reminder upsert/clear in one transaction
    suspend fun getAllEnabledRemindersOnce(): List<Reminder>                      // boot / time-change re-arm

    // Lifecycle
    suspend fun onStartup()
}
```

All reads return `Flow`. Writes are `suspend`. `saveCategory` and `saveEvent` are upserts. `getEvents` accepts optional epoch-millis bounds; null = unbounded.

## Room Entities

Entities mirror domain models with Room annotations. They are package-private to the `local` package and never exposed above the repository.

### CategoryEntity

| Column | Type | Notes |
|---|---|---|
| `id` | `String` PK | UUID |
| `name` | `String` | |
| `emoji` | `String` | single emoji |
| `color` | `Long` | ARGB packed |
| `valueType` | `String` | `ValueType` serialized via `ValueTypeConverter` |
| `defaultValue` | `String?` | `EventValue?` as JSON via `EventValueConverter`; null for most types; always non-null for Number and Exercise |
| `allowEmptyText` | `Boolean` | meaningful only when valueType = Text |
| `sortOrder` | `Int` | ascending; lower = higher in list; indexed |

### EventEntity

| Column | Type | Notes |
|---|---|---|
| `id` | `String` PK | UUID |
| `categoryId` | `String` FK → categories(id) CASCADE DELETE | indexed |
| `timestamp` | `Long` | epoch millis via `InstantConverter`; user-editable; indexed |
| `value` | `String?` | `EventValue?` as JSON; null for None-type events |
| `notes` | `String?` | |
| `imagePaths` | `String` | `List<String>` as JSON array; `"[]"` when empty |
| `createdAt` | `Long` | epoch millis via `InstantConverter`; wall-clock creation time; indexed |

### ReminderEntity

| Column | Type | Notes |
|---|---|---|
| `categoryId` | `String` PK, FK → categories(id) CASCADE DELETE | one row per category |
| `enabled` | `Boolean` | |
| `mode` | `String` | `"fixed"` / `"random"` |
| `times` | `String?` | JSON list of `"HH:mm"` strings; FIXED only |
| `windowStart` | `String?` | `"HH:mm"`; RANDOM only |
| `windowEnd` | `String?` | `"HH:mm"`; RANDOM only |
| `occurrencesPerDay` | `Int?` | RANDOM only |
| `daysActive` | `String` | JSON list of `DayOfWeek` names |
| `showCategoryInNotification` | `Boolean` | default `false` |
| `nextFireAt` | `Long?` | epoch millis; null when disabled |

Field-level rationale (mode/window/frequency shape, why one row per category, why `nextFireAt` is stored rather than derived on demand) lives in `docs/llds/reminders.md § Data Model` — this segment owns the mechanical storage, not the design intent behind it.

## TypeConverters

### EventValueConverter

Contract: `EventValue?` ↔ `String?`. Null passes through. See `docs/llds/data-model.md § TypeConverter` for full encode/decode logic including `ErrorValue` repair and forward-compat round-trip.

### InstantConverter

Contract: `java.time.Instant` ↔ `Long` (epoch millis). Used for `Event.timestamp` and `Event.createdAt`. Encode: `instant.toEpochMilli()`. Decode: `Instant.ofEpochMilli(value)`.

### StringListConverter

Contract: `List<String>` ↔ non-null `String` (JSON array). On decode failure, returns `emptyList()` — corrupt `imagePaths` treated as no images rather than crashing.

### ValueTypeConverter

Contract: `ValueType` ↔ `String`.

- **Encode:** known variants serialize to a fixed lowercase name string (`"none"`, `"scale"`, etc.); `Unknown(raw)` serializes to `raw` verbatim — preserving the original string for round-trip.
- **Decode:** matches against known names; unrecognized string → `ValueType.Unknown(raw)`.

This mirrors the `ErrorValue` forward-compatibility contract: an old app version reading a category with an unknown `ValueType` stores `Unknown(raw)` and writes `raw` back unchanged.

## DAOs

### CategoryDao

| Method | Return | Notes |
|---|---|---|
| `getAll()` | `Flow<List<CategoryEntity>>` | ordered by `sortOrder ASC` |
| `getAllOnce()` | `List<CategoryEntity>` | suspend |
| `getByIdOnce(id)` | `CategoryEntity?` | suspend; for pre-deletion cleanup |
| `getByIdWithParent(id)` | `Flow<CategoryWithParent?>` | `@Transaction`; uses `@Relation` to fetch parent row in a second query |
| `countByParentId(parentId)` | `Flow<Int>` | |
| `countByParentIdOnce(parentId)` | `Int` | suspend |
| `getChildrenByParentIdOnce(parentId)` | `List<CategoryEntity>` | suspend; a nest destination group's current members |
| `getTopLevelOnce()` | `List<CategoryEntity>` | suspend; `parentId IS NULL` — a top-level destination group's current members |
| `getMinSortOrder()` | `Int?` | suspend; null if no categories exist; used when inserting new category at top |
| `updateSortOrders(ids: List<String>)` | `Unit` | suspend; reassigns sequential sortOrder values (0, 1, 2…) matching the provided order |
| `upsert(entity)` | `Unit` | suspend |
| `deleteById(id)` | `Unit` | suspend |

### EventDao

| Method | Return | Notes |
|---|---|---|
| `getAll(start, end)` | `Flow<List<EventEntity>>` | nullable Long bounds; ordered `timestamp DESC, createdAt DESC, id ASC` |
| `getByCategory(categoryId)` | `Flow<List<EventEntity>>` | same order |
| `getById(id)` | `Flow<EventEntity?>` | |
| `getByIdOnce(id)` | `EventEntity?` | suspend; for pre-deletion cleanup |
| `getByCategoryOnce(categoryId)` | `List<EventEntity>` | suspend; for category deletion cleanup |
| `getByCategoryIncludingChildren(categoryId)` | `Flow<List<EventEntity>>` | live; returns events for categoryId plus all SubCategories (any valueType); used for filtered timeline |
| `getByCategoryIncludingChildrenWithNullTypeOnce(categoryId)` | `List<EventEntity>` | suspend; returns events for categoryId plus SubCategories with `valueType IS NULL` only; used by event migration on MetaCategory valueType change |
| `getAllOnce()` | `List<EventEntity>` | suspend; for startup orphan scan |
| `countByCategory(categoryId)` | `Flow<Int>` | live count; for edit screen UI state |
| `countByCategoryIncludingChildrenWithNullType(categoryId)` | `Flow<Int>` | live count for categoryId plus SubCategories with `valueType IS NULL`; same JOIN pattern as `getByCategoryIncludingChildrenWithNullTypeOnce` |
| `upsert(entity)` | `Unit` | suspend |
| `deleteById(id)` | `Unit` | suspend |

Sort order (`timestamp DESC, createdAt DESC, id ASC`) matches the canonical ordering in `data-model.md § Same-timestamp ordering`.

### ReminderDao

| Method | Return | Notes |
|---|---|---|
| `getByCategoryId(categoryId)` | `Flow<ReminderEntity?>` | |
| `getAllEnabledOnce()` | `List<ReminderEntity>` | suspend; boot / time-change re-arm and startup reconciliation |
| `upsert(entity)` | `Unit` | suspend |

No explicit `deleteByCategoryId` — removal happens via the `categories` row's CASCADE DELETE, not a direct call.

## Entity ↔ Domain Mappers

Extension functions in the `local` package. `CategoryEntity.toDomain()`, `Category.toEntity()`, `EventEntity.toDomain()`, `Event.toEntity()`, `ReminderEntity.toDomain()`, `Reminder.toEntity()`. Each maps field-for-field, delegating type conversion to the converters above.

**`CategoryWithParent`**: a Room result POJO with `@Embedded val category: CategoryEntity` and `@Relation(parentColumn = "parentId", entityColumn = "id") val parent: CategoryEntity?`. Room fetches the parent in a second `SELECT * FROM categories WHERE id IN (...)` query within the same transaction. Used only by `getCategoryById` in the repository.

## LocalTrackrRepository

Implements `TrackrRepository`. Injected with `CategoryDao`, `EventDao`, and `ImageStore`.

**Deletion order (DB first, files after):** `deleteEvent(id)` removes only the DB row; `deleteEventFiles(imagePaths)` handles file cleanup and is called separately. This split lets callers defer file deletion — `HomeViewModel` defers it to `clearPendingDelete()` so undo can restore an event with its images intact; `EventEditViewModel` calls both immediately. If the process dies between the two calls, orphaned files are recovered at next startup via LS-BE-040. The reverse order (files first) risks unrecoverable data loss if the DB write fails.

**`saveEvent` image diffing:** reads the old entity before upserting, computes removed paths (`old - new`), upserts, then deletes removed files. Upsert-before-delete ensures a failed upsert leaves storage intact; orphaned files from a crash after upsert are recovered at startup.

**`getCategories` sort order:** after `toDomainList()`, results are sorted hierarchically: MetaCategories by their own `sortOrder` ascending, with each MetaCategory's SubCategories appearing immediately after, sorted by their own `sortOrder`. SubCategories that surface as MetaCategories (per orphan handling below) sort by their own `sortOrder`.

**`deleteCategory`:** runs inside a single Room transaction. Within the transaction: fetches child entities (by parentId); if any exist, upserts each child with `parentId = null` (resolving null emoji/color/valueType fields to the parent's stored values); collects image paths for the parent's own events; deletes the parent DB row (CASCADE removes its own events but not promoted children). Image file deletion happens after the transaction commits. When there are no children the transaction is a no-op promotion step followed by the same delete.

**`moveCategory` / `moveCategoryAndMigrateEvents`:** the general "reparent a category and reorder its destination sibling group" capability. Both run inside a single Room transaction: upsert the reparented row (reusing `saveCategory`'s childlessness guard, DM-DATA-028), then re-read the destination group's *current* members within that same transaction (`getChildrenByParentIdOnce` for a nest, `getTopLevelOnce` for a top-level move) and dense-reindex them via `updateSortOrders`, ordering them by reconciling the caller's `orderedSiblingIds` against those live members through the pure function `reconcileSiblingOrder` (`domain/SiblingReindex.kt`) — shared by both this real repository and the test `FakeTrackrRepository`, and unit-tested in isolation, so the fake stays behavior-consistent with the real reindex. Re-reading membership *inside* the transaction — rather than reindexing the caller's `orderedSiblingIds` directly — closes a **read-outside-transaction (TOCTOU) gap**: the reindex list is derived from state read inside the transaction, so a concurrent change to the destination group between the caller's read and the commit can no longer strand a sibling at a stale, colliding `sortOrder`. This is general to any caller that supplies an explicit new order (drag today; a future menu/keyboard Move Up/Down would use the same method). The **source** group (when the move changes which group the row belongs to) is left untouched — `sortOrder` only has to express relative order among current siblings, and removing a member doesn't invalidate the relative order of the ones left behind, so no renumbering is needed there. The migrating variant additionally runs the `convertEventValue` pass over the category's own events, mirroring `saveCategoryAndMigrateEvents`. The reconcile ordering rule is specified by CAT-UI-083; the caller-side concern of *where `orderedSiblingIds` comes from* (a drag drop's snapshot, possibly held across a CAT-UI-081 dialog) is `category-management.md § Drag-to-Reorder: Adapter & Persistence`.

**Orphaned SubCategory handling in `toDomainList()`:** if a `CategoryEntity` has a non-null `parentId` that is not present among the loaded entities, it is surfaced as a `Category.MetaCategory` using its own stored fields, with null-field fallbacks matching MetaCategory assembly (`"" / 0xFFE53935L / ValueType.None`). This can occur when a MetaCategory is deleted mid-session or when the DB is in an inconsistent state; see DM-PROC-022.

**`onStartup`:** called once at app startup, launched fire-and-forget from `TrackrApplication.onCreate()` — it does not block the first UI frame (per LS-BE-041). `LocalTrackrRepository` uses it to scan `filesDir/images` and delete any file not referenced by a DB event row, a purely additive cleanup with no ordering requirement against the UI. Future implementations may use it for different initialization behavior (sync, token refresh, etc.) — if that behavior is something the UI depends on, the fire-and-forget launch must be revisited to actually block first frame. (Reminder scheduling reconciliation, added by the `reminders` segment, is a **separate** fire-and-forget call from `TrackrApplication.onCreate()` — not folded into this method. Re-arming alarms needs `AlarmManager` access, which sits outside this segment's persistence-only seam; it isn't this method's concern regardless of what else `onStartup` happens to do today.)

**`saveReminder` / `getReminderForCategory` / `getAllEnabledRemindersOnce`:** thin pass-throughs to `ReminderDao`, mapping via `ReminderEntity.toDomain()`/`Reminder.toEntity()`. No transaction needed — each is a single-row read or upsert.

**`saveCategoryWithReminder`:** runs inside a single Room transaction: upserts the `CategoryEntity` (via the same path `saveCategory` uses, including its childlessness guard) and upserts (or, when `reminder == null`, no-ops — there's nothing to delete explicitly; a category can only reach this method while it still exists) the `ReminderEntity`. This is a persistence-only transaction — it does not touch `AlarmManager`; arming or cancelling the alarm is a separate post-commit step the caller (`ReminderScheduler`, per `docs/llds/reminders.md § Scheduling Engine`) performs after this method returns successfully.

## ImageStore

A thin `@Singleton` wrapper around `context.filesDir/images`. Responsibilities:

- `newFile(extension)` — returns a new `File` with a UUID name for the UI to write to
- `delete(absolutePath)` — deletes a file; no-op if already gone
- `allStoredPaths()` — returns all file paths in the image directory (used by `onStartup` orphan scan)

Image files are written by the UI before calling `saveEvent`. Deletion is always the repository's responsibility.

## DataStore

Jetpack DataStore Preferences stores simple app-wide state that doesn't belong in the Room schema. In v1, one key is stored:

| Key | Type | Initial value | Notes |
|---|---|---|---|
| `next_category_color_index` | `Int` | `0` | Wraps modulo palette size; never reset on deletion |

`LocalTrackrRepository.getAndIncrementNextCategoryColorIndex(paletteSize)` reads the current value, writes `(value + 1) % paletteSize`, and returns the original value — atomically within a DataStore transaction. The caller receives the index to apply; the store always holds the next one.

## Room Database

Three entities (`CategoryEntity`, `EventEntity`, `ReminderEntity`), version 4, `exportSchema = true` (schema JSON exported to `app/schemas/`). Four TypeConverters registered at the database level: `EventValueConverter`, `InstantConverter`, `StringListConverter`, `ValueTypeConverter`. Destructive migration disabled — data loss on schema change is never acceptable.

## Migration Strategy

### Version 1 → 2
No prior version at v1.

### Version 2 → 3
Removes `unit` column and adds `default_value` column on `categories`. SQLite does not support `DROP COLUMN` on older Android versions, so the migration recreates the table:

1. Create `categories_new` with the new schema (no `unit`, with `default_value TEXT`).
2. Copy all rows: for each row where `valueType = 'number'` and `unit IS NOT NULL`, encode `default_value` as `{"type":"number","value":0.0,"unit":"<unit>"}` (using the same JSON format as `EventValueConverter`); all other rows get `default_value = NULL`.
3. Drop `categories`.
4. Rename `categories_new` to `categories`.
5. Recreate any indexes dropped by the table recreation.

### Version 3 → 4
Adds the `reminders` table (`ReminderEntity`, § Room Entities), a plain `CREATE TABLE` — no existing table is altered, so no row copy or recreation is needed. `categoryId` carries the FK → `categories(id) ON DELETE CASCADE`.

## Android Auto Backup

Auto Backup (API 23+) backs up app data to the user's Google account automatically when the device is idle, charging, and on Wi-Fi. Restores on reinstall or new device. This is the v1 data-safety baseline; no server infrastructure is required.

**Backed-up data:**

| Domain | Path | Contents |
|---|---|---|
| `database` | `trackr.db` | Room database (categories + events) |
| `file` | `images` | Photo attachments (`filesDir/images/`) |
| `file` | `datastore` | DataStore Preferences (`filesDir/datastore/`) |

Room WAL files (`trackr.db-wal`, `trackr.db-shm`) are included automatically when the database domain is specified.

**Backup rule files:**
- `res/xml/data_extraction_rules.xml` — API 31+ (Cloud Backup + Device-to-Device transfer, referenced via `android:dataExtractionRules`)
- `res/xml/backup_rules.xml` — API 23–30 (referenced via `android:fullBackupContent`)

Both files declare the same include set. Only the three entries above are backed up; everything else in `filesDir` is excluded by the whitelist-only rules.

**25 MB limit:** Auto Backup caps at 25 MB per app. Heavy photo usage could approach this; if exceeded, Google backs up a subset. Post-v1 cloud sync (AppSync/S3) will supersede this constraint.

**Restore + orphan scan:** since Auto Backup runs while the app is not in use (idle + charging + Wi-Fi), the DB and images are captured in a consistent state. The `onStartup()` orphan scan after restore is a no-op in the normal case. Room handles WAL checkpoint on first open if needed.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Repository interface location | Defined in this segment | Separate `domain` module | No separate module at this scale; the interface is the seam, not the module boundary |
| Next color index storage | DataStore Preferences | Room metadata table; SharedPreferences | DataStore is the idiomatic Jetpack replacement for SharedPreferences; a Room table would be over-engineered for a single integer |
| Next color index strategy | Wrapping counter `(n + 1) % paletteSize`; never reset on deletion | Compute from current category count | Count-based would repeat colors after deletions; a stored counter that cycles through the palette guarantees even distribution |
| DAO write style | `@Upsert` (Room 2.5+) | `@Insert(onConflict = REPLACE)`; separate insert/update | `@Upsert` is correct and idiomatic; REPLACE deletes-then-inserts which resets FKs |
| Flow vs. suspend for reads | `Flow` | `suspend` returning snapshot | `Flow` gives reactive UI updates for free |
| ValueType storage | Sealed class serialized to name string; `Unknown(raw)` round-trips verbatim | Enum ordinal; enum name with TEXT fallback | Sealed class enables lossless round-trip of unknown future variants; TEXT fallback silently loses the original value |
| `imagePaths` storage | JSON string via `StringListConverter` | Join table (`event_images`); native Room collection support (not available) | Room has no native collection type; JSON string avoids a join for a simple ordered list always loaded with the event. May revisit with a join table if ordering or querying per-image becomes necessary. |
| `imagePaths` null vs. empty | Non-null `"[]"` | Nullable column | Avoids null-vs-empty ambiguity |
| Deletion order | DB first, files after | Files first | DB is atomic; file orphans are recoverable. Reversed order risks unrecoverable loss if DB write fails after file delete. |
| Orphaned file recovery | Startup scan | Journal; periodic background job | Simple and correct; journal adds per-save overhead; background job adds scheduling complexity |
| `saveEvent` image diff order | Read → upsert → delete removed files | Read → delete → upsert | Upsert-first leaves storage intact on failure; delete-first risks missing files if upsert fails |
| `imagePaths` decode failure | Return `emptyList()` | Crash; propagate exception | Data-loss acceptable vs. crash for image paths; events remain accessible |
| Startup lifecycle hook name | `onStartup()` on `TrackrRepository` | `cleanupOrphanedImages()`; `initialize()` | Generic name keeps the interface implementation-agnostic; future backends (GraphQL, sync) can use the same hook for different startup behavior without renaming |
| Category sort order | `sortOrder: Int ASC`; new = `currentMin - 1`; reorder reassigns sequentially | `createdAt ASC`; alphabetical; linked-list prev/next | `sortOrder` int is simple to query and reorder; linked-list avoids bulk updates but complicates queries; alphabetical removes user control |

## Open Questions & Future Decisions

### Deferred

1. **Nullable Long params in `getEvents` SQL** — Room's handling of `Long?` in `IS NULL OR` queries needs verification during implementation. Fallback: two separate DAO methods (`getAll()`, `getAllInRange(start, end)`) dispatched in the repository.
2. **`imagePaths` join table** — if per-image ordering, querying, or metadata is needed, a `event_images` join table may be preferable. Deferred until requirements emerge.
3. **Pagination** — `getEvents` returns all rows. A `PagingSource` (Paging 3) may be needed at scale. Deferred until observed.
4. **Backup / export** — out of scope for v1.

## References

- `docs/llds/data-model.md` — domain types, `EventValueConverter`, sort order, `ValueType` sealed class
- `docs/high-level-design.md` — repository-as-seam decision
- [Room documentation](https://developer.android.com/training/data-storage/room)
