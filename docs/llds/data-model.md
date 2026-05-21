# Data Model

## Context and Design Philosophy

This component defines the domain types used throughout the app above the database layer. It is the vocabulary every other component speaks. The storage layer (Room entities) mirrors these types closely but is a separate concern — the domain model must not import Room annotations or Compose types.

The central design challenge is supporting seven distinct value shapes in a single `events` table without a polymorphic schema. The chosen approach: store values as JSON `String?` in Room and provide a typed `EventValue` sealed class at the domain boundary via kotlinx.serialization TypeConverters.

## ValueType

Each `Category` declares one `ValueType`, which constrains the shape of `EventValue` that can be logged against it. The type lives on the category, not on individual events, so the UI always knows how to render an event without inspecting its value first.

`ValueType` is a sealed class (not an enum) so that unknown future variants round-trip safely through the DB, mirroring the `ErrorValue` strategy for `EventValue`.

```kotlin
sealed class ValueType {
    data object None : ValueType()
    data object Scale : ValueType()
    data object Boolean : ValueType()
    data object Number : ValueType()
    data object Text : ValueType()
    data object Duration : ValueType()
    data object Exercise : ValueType()
    data class Unknown(val raw: String) : ValueType()  // forward-compat: unknown future variant
}
```

| Variant | Meaning | EventValue variant |
|---|---|---|
| `None` | Occurrence only — log that it happened | `null` |
| `Scale` | Integer 1–10 | `EventValue.Scale` |
| `Boolean` | Yes / No | `EventValue.BooleanValue` |
| `Number` | Floating-point with optional unit | `EventValue.NumberValue` |
| `Text` | Free-form string | `EventValue.TextValue` |
| `Duration` | Duration in seconds (Int) | `EventValue.DurationValue` |
| `Exercise` | Sets × reps (both integers ≥ 1) | `EventValue.ExerciseValue` |
| `Unknown(raw)` | Unrecognized future variant | display as error; `raw` preserved for round-trip |

## EventValue Sealed Class

```kotlin
@Serializable
enum class ErrorKind {
    UNPARSABLE,         // raw string is not valid JSON or can't be deserialized
    UNRECOGNIZED_TYPE,  // JSON is valid but type discriminator names an unknown variant
    OUT_OF_RANGE,       // type recognized, value violates an invariant (e.g. Scale outside 1..10)
}

@Serializable
sealed class EventValue {
    @Serializable
    data class Scale(val value: Int) : EventValue()        // invariant: 1..10

    @Serializable
    data class BooleanValue(val value: Boolean) : EventValue()

    @Serializable
    data class NumberValue(
        val value: Double,
        val unit: String?,                                  // null = unitless
    ) : EventValue()

    @Serializable
    data class TextValue(val text: String) : EventValue()

    @Serializable(with = DurationAsSecondsSerializer::class)
    data class DurationValue(val duration: kotlin.time.Duration) : EventValue()  // invariant: >= Duration.ZERO

    @Serializable
    data class ExerciseValue(
        val sets: Int,
        val reps: Int,      // invariants: sets >= 1, reps >= 1
    ) : EventValue()

    @Serializable
    data class ErrorValue(
        val kind: ErrorKind,
        val raw: String,                // original JSON string, preserved for diagnostics and future recovery
        val inferredType: String? = null, // "type" discriminator extracted from raw JSON when kind == UNRECOGNIZED_TYPE; null otherwise
    ) : EventValue()
}
```

`NONE`-type events carry `null` for their value — there is no `EventValue.None` variant. Null is the canonical representation in both the DB column and the domain model. `@Serializable` is required on each non-null variant so kotlinx.serialization can encode/decode the polymorphic sealed class via the class discriminator. `ErrorValue` is produced by the TypeConverter when a value can't be decoded cleanly. On encode, `ErrorValue` bypasses normal serialization — `raw` is written verbatim — so an older app version reading then writing a value from a newer version leaves the DB bytes identical. This is the forward-compatibility contract.

`inferredType` on `ErrorValue` is a decode-time annotation, not a stored field. When the converter produces `ErrorValue(UNRECOGNIZED_TYPE, raw)`, it also extracts the `"type"` discriminator from the raw JSON and stores it in `inferredType`. This allows `matchesValueType` to recognise that an `ErrorValue` whose `inferredType` matches an `Unknown` category's `raw` string represents a coherent future-type pair — both the value and the category come from the same unrecognized type — and to return `true` rather than flagging a spurious mismatch. `inferredType` has a default of `null`; it is never serialized to disk (encoding always uses `raw` verbatim).

`DurationValue` uses `kotlin.time.Duration` at the domain level. Because kotlinx.serialization encodes `Duration` as an ISO 8601 string by default, a `DurationAsSecondsSerializer` is used to store it as total seconds (Long) in the JSON — compact and unambiguous. The serializer lives in the `local-storage` segment alongside `EventValueConverter`.

### Invariants and repair strategy

Invariants are enforced at two layers:

- **At input time (UI):** the UI prevents out-of-range values from being submitted.
- **At read time (TypeConverter):** violations produce an `ErrorValue` with the appropriate `ErrorKind` and the original raw string. The exception is never propagated.

| Condition | Result |
|---|---|
| `Scale.value` outside `1..10` | `ErrorValue(OUT_OF_RANGE, raw)` |
| `DurationValue.duration` < `Duration.ZERO` | `ErrorValue(OUT_OF_RANGE, raw)` |
| `ExerciseValue.sets` < 1 or `ExerciseValue.reps` < 1 | `ErrorValue(OUT_OF_RANGE, raw)` |
| `NumberValue.value` | Any finite Double including negative — no constraint |
| Unknown type discriminator | `ErrorValue(UNRECOGNIZED_TYPE, raw, inferredType = <"type" field>)` — preserves data from future app versions; `inferredType` enables mismatch detection against `Unknown` category types |
| Unparsable JSON | `ErrorValue(UNPARSABLE, raw)` |
| `null` (NONE-type event) | `null` — passed through; no decoding attempted |

### TypeConverter (Room boundary)

Requires kotlinx.serialization ≥ 1.6.3 for `data object` support. Pin this version or higher in `libs.versions.toml`.

```kotlin
object EventValueConverter {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun encode(value: EventValue?): String? = when (value) {
        null -> null
        is EventValue.ErrorValue -> value.raw  // preserve original bytes exactly — no re-serialization
        else -> json.encodeToString(EventValue.serializer(), value)
    }

    @TypeConverter
    fun decode(raw: String?): EventValue? {
        raw ?: return null
        return try {
            val decoded = json.decodeFromString(EventValue.serializer(), raw)
            when {
                decoded is EventValue.Scale && decoded.value !in 1..10 ->
                    EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, raw)
                decoded is EventValue.DurationValue && decoded.duration < Duration.ZERO ->
                    EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, raw)
                decoded is EventValue.ExerciseValue && (decoded.sets < 1 || decoded.reps < 1) ->
                    EventValue.ErrorValue(ErrorKind.OUT_OF_RANGE, raw)
                else -> decoded
            }
        } catch (e: SerializationException) {
            // Distinguish unrecognized type discriminator from unparsable JSON
            if (raw.contains("\"type\"")) {
                val inferredType = try {
                    json.decodeFromString<JsonObject>(raw)["type"]?.jsonPrimitive?.content
                } catch (_: Exception) { null }
                EventValue.ErrorValue(ErrorKind.UNRECOGNIZED_TYPE, raw, inferredType = inferredType)
            } else {
                EventValue.ErrorValue(ErrorKind.UNPARSABLE, raw)
            }
        }
    }
}
```

The converter lives in the `local-storage` segment (it's a Room concern) but consumes types defined here.

## Domain Models

These are plain Kotlin domain types with no framework dependencies.

`Category` is a sealed class with two variants. `MetaCategory` is a top-level category whose emoji, color, and valueType are always explicitly set. `SubCategory` is a child of a `MetaCategory`; its emoji, color, and valueType are nullable — null means inherit from the parent.

```kotlin
sealed class Category {
    abstract val id: String
    abstract val name: String
    abstract val unit: String?
    abstract val allowEmptyText: Boolean
    abstract val sortOrder: Int
    abstract val resolvedEmoji: String
    abstract val resolvedColor: Long
    abstract val resolvedValueType: ValueType

    data class MetaCategory(
        override val id: String,
        override val name: String,
        val emoji: String,               // always non-null
        val color: Long,                 // ARGB packed as Long; always non-null
        val valueType: ValueType,        // always non-null
        override val unit: String?,      // only meaningful when effective valueType == NUMBER
        override val allowEmptyText: Boolean,
        override val sortOrder: Int,     // ascending within the top-level list
    ) : Category() {
        override val resolvedEmoji get() = emoji
        override val resolvedColor get() = color
        override val resolvedValueType get() = valueType
    }

    data class SubCategory(
        override val id: String,
        override val name: String,
        val emoji: String?,              // null = inherit from parent
        val color: Long?,               // null = inherit from parent
        val valueType: ValueType?,       // null = inherit from parent
        override val unit: String?,
        override val allowEmptyText: Boolean,
        override val sortOrder: Int,     // ascending within the parent's subcategory list
        val parent: MetaCategory,        // always non-null; populated at repository layer
    ) : Category() {
        override val resolvedEmoji get() = emoji ?: parent.emoji
        override val resolvedColor get() = color ?: parent.color
        override val resolvedValueType get() = valueType ?: parent.valueType
    }
}
```

The resolved properties require no null-assertion operators — the type system guarantees `MetaCategory` fields are non-null and `SubCategory` always has a non-null parent. All UI and ViewModel code uses `resolvedEmoji`, `resolvedColor`, `resolvedValueType` for display and behaviour. The raw nullable fields are inspected only to determine whether a subcategory overrides a specific field.

**DB entity** (`CategoryEntity`) remains a flat table with `parentId: String?` column; the sealed class variants are assembled in the repository layer. `getCategories()` and `getCategoryById()` use a LEFT JOIN:

```sql
SELECT c.*, p.* FROM categories c
LEFT JOIN categories p ON c.parentId = p.id
WHERE [condition]
```

The repository builds a `MetaCategory` for every row where `parentId IS NULL` and a `SubCategory` for every row where `parentId IS NOT NULL`, attaching the parent instance directly. No persistent category cache is needed — the parent map is built fresh per query emission.

**Two-level constraint:** no `MetaCategory` has a non-null `parentId` (encoded in the type — `MetaCategory` has no `parentId` field). No `SubCategory` may have children; this is enforced by the repository (throws on attempt) and prevented in the UI (the create-subcategory action is not shown for a category that already has children, and the add-to-group action is not shown for a category that already has children).

data class Event(
    val id: String,                  // UUID string
    val categoryId: String,
    val timestamp: Instant,          // user-editable log time (java.time.Instant)
    val value: EventValue?,          // null for NONE-type categories; non-null for all others
    val notes: String?,
    val imagePaths: List<String>,    // absolute paths within app-private storage; empty = no images
    val createdAt: Instant,          // wall-clock creation time; never edited
)
```

### imagePaths

`imagePaths` holds absolute file-system paths to images stored in app-private storage (`context.filesDir`). An empty list means no images. The domain model makes no distinction between camera-sourced and gallery-sourced images — both are copied into app-private storage at capture time and referenced by path. The `local-storage` segment owns the TypeConverter (`List<String>` ↔ JSON string). Deletion of an event must also delete its image files — the `local-storage` or repository layer is responsible for this cascade.

The quick-log flow captures at most one image; additional images can be attached via the event edit screen. The domain model places no cap on list size.

### timestamp vs createdAt

`timestamp` is the time the user says the event occurred — it is editable and is what appears in the timeline. `createdAt` is the wall-clock `Instant` at which the record was first created and is never edited. They diverge when the user logs a past event. Both are stored in Room as epoch-millis Long values via an `InstantConverter` TypeConverter in the `local-storage` segment.

### Same-timestamp ordering

When two events share the same `timestamp`, the canonical sort order is `createdAt` ascending, then `id` (UUID string) as a final tiebreaker. Manual reordering is out of scope for v1.

### Empty TextValue

`Category.allowEmptyText` controls whether empty strings may be submitted for `TEXT`-type events. This is a domain field read by the event logging UI — the UI gates submission on it, but the domain model itself does not reject `TextValue("")`. The MVP category editor does not expose this setting; all UI-created categories are initialized with `allowEmptyText = true`.

### Unit: category default vs. event snapshot

`Category.unit` is the default unit presented to the user when logging a new event. At log time, `NumberValue.unit` is populated from `Category.unit` — each event stores its own copy. This means historical events retain the unit that was in effect when they were logged, even if the category's unit is later changed. `Category.unit` and `NumberValue.unit` may therefore diverge for historical records; this is correct behavior, not an inconsistency.

When `valueType != NUMBER`, `Category.unit` is ignored. It is not an error for it to be non-null.

### Category hierarchy: inheritance and constraints

The two-level hierarchy is enforced entirely at the repository and UI layers; the domain types themselves carry no runtime checks.

**Inheritance:** a `SubCategory` field is inherited when its value is null. The resolved value is always the non-null fallback from the parent (guaranteed non-null by the `MetaCategory` type). `unit` and `allowEmptyText` are not inheritable — they are always set explicitly on both MetaCategory and SubCategory.

**Reparenting an existing category into a group:** all current explicit field values are kept as overrides regardless of whether they match the new parent's values. The caller is responsible for providing the correct field values; the repository performs no automatic field-merging on reparent.

**Un-nesting (removing from group):** any null field on the SubCategory is set to the parent's current value at the time the operation is committed, then `parentId` is cleared and the record becomes a MetaCategory.

### Value type mismatch helpers

Three domain-layer functions and a supporting type support the mismatch detection and conversion UI (see `event-logging.md § Value type mismatch`):

- `matchesValueType(value: EventValue?, type: ValueType): Boolean` — true when the value's runtime type is the expected variant for `type`. `ErrorValue` → true only when `type is Unknown` and `value.inferredType == type.raw` (coherent future-type pair); false otherwise. `Unknown` category type → false unless matched by an `ErrorValue` as above. `null` → true only for `None`. `None` type with non-null value → false.
- `convertOrDefault(value: EventValue, targetType: ValueType): ConversionOutcome` — returns `Discard` if the target is `None`/`Unknown`; otherwise calls `convertEventValue(value, targetType)` and returns `Converted(v)` if the result satisfies `matchesValueType`, or `UsedDefault(defaultForType(targetType))` if not. `ErrorValue` always yields `UsedDefault` because `convertEventValue` returns it unchanged and `matchesValueType(ErrorValue, X)` is always false.
- `ConversionOutcome` — sealed class: `Converted(value: EventValue)`, `UsedDefault(value: EventValue)`, `Discard`.
- `defaultForType(type: ValueType): EventValue?` — returns the zero-arg constructor default for each known type (null for `None` and `Unknown`).

All four live in `domain/ValueTypeConversion.kt` alongside `convertEventValue`.

### ValueType change on a Category

Changing a category's `valueType` is permitted and handled at the repository layer — the domain model does not prevent it. When a type change is saved, `TrackrRepository.saveCategoryAndMigrateEvents(category, fromType)` runs an atomic transaction: it upserts the category and migrates all historical events by calling `convertEventValue(event.value, category.valueType)` on each one.

For a `MetaCategory`, the migration also covers events belonging to any `SubCategory` whose `valueType` is null (inheriting the parent's type) — those subcategories' effective type changes alongside the parent's. SubCategories with an explicit `valueType` override are excluded from the migration pass and keep their own type unchanged.

`convertEventValue` performs best-effort conversion — e.g., `Scale(7)` → `NumberValue(7.0, null)` when changing Scale → Number, or `TextValue("7")` → `Scale(7)` when changing Text → Scale. When no conversion path exists (e.g., `DurationValue` → `Scale`), the original value is returned unchanged, leaving the event with a mismatched value in the database.

Mismatches can also arise when reading data written by a newer app version (an `Unknown` category type or an `ErrorValue`). The mismatch helpers (`matchesValueType`, `convertOrDefault`, `defaultForType`) support detection and recovery. The event-logging segment owns the UI for surfacing and resolving mismatches.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Value storage format | JSON `String?` via kotlinx.serialization | Separate columns per type; separate tables per type; protobuf | Single-column JSON avoids schema migrations when new value types are added; kotlinx.serialization is compile-time safe and idiomatic Kotlin |
| Sealed class vs. interface | `sealed class EventValue` | Open interface; `Any?` with ValueType cast | Sealed class gives exhaustive `when` expressions; compiler enforces handling all variants |
| Timestamp representation | `java.time.Instant` in domain; stored as epoch-millis Long in Room via `InstantConverter` | Plain `Long`; `kotlinx.datetime.Instant` | `Instant` is semantically correct and type-safe; no extra library needed (Java stdlib + desugaring); `Long` is error-prone without context about units |
| Scale range | 1..10 (Int) | Float 0.0–1.0; configurable per category | Integer 1–10 is the universal convention for subjective scales; simpler UI (slider snaps to integers) |
| Duration representation | `kotlin.time.Duration` in domain; serialized as total seconds (Long) via `DurationAsSecondsSerializer` | Plain `Int` seconds; `java.time.Duration` | `kotlin.time.Duration` is idiomatic Kotlin and gives UI convenient decomposition (`.toComponents`); serializing as Long seconds avoids ISO 8601 string overhead and keeps the JSON compact |
| IDs | UUID strings | Auto-increment Long | UUIDs are stable across local/cloud sync boundary — no re-keying when a backend is added |
| Image storage | File paths in `imagePaths: List<String>` | Blob in DB; separate images table | File paths keep the DB row small; blobs bloat SQLite and slow queries; separate table adds join overhead for a simple ordered list |
| Image path type | Absolute path string | URI string; relative path | Absolute paths are unambiguous at read time; URI strings require resolution context; relative paths need a known root |
| Empty text policy | `Category.allowEmptyText` field; enforced in event UI | Domain-layer rejection; hardcoded UI rule | Storing the policy on Category makes it inspectable and extensible without code changes; UI enforcement (not domain) keeps the domain model simple |
| Category hierarchy | `Category` sealed class (`MetaCategory` / `SubCategory`); nullable inheritable fields on SubCategory | Single `data class` with nullable fields + `!!` operators; separate `ResolvedCategory` wrapper type | Sealed class encodes invariants in the type system: MetaCategory fields are provably non-null, SubCategory parent is provably non-null, no `!!` needed anywhere; a `ResolvedCategory` wrapper adds an extra type to thread through every call site |
| Category assembly from DB | LEFT JOIN in a single query; parent assembled in repository layer | Two queries in a transaction; always-eager join via Room `@Relation` | Single JOIN is atomic by nature (one read), simpler than a two-query transaction, and more explicit than `@Relation` about what is being loaded |

## Open Questions & Future Decisions

### Deferred

1. **Category color picker** — how is `color` selected by the user? Preset palette vs. full color wheel. This is a UI concern but constrains what ARGB values appear in the domain.
2. **ValueType extensibility** — if users want a custom value type in the future, the sealed class must be opened or replaced with a plugin model. Deferred to v2.
3. **Multi-value events** — can a single event carry more than one value (e.g., systolic + diastolic blood pressure)? Currently no. Revisit if requested.

## References

- `docs/high-level-design.md` — system design and value model decision
- [kotlinx.serialization sealed classes](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md)
