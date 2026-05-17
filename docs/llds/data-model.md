# Data Model

## Context and Design Philosophy

This component defines the domain types used throughout the app above the database layer. It is the vocabulary every other component speaks. The storage layer (Room entities) mirrors these types closely but is a separate concern — the domain model must not import Room annotations or Compose types.

The central design challenge is supporting six distinct value shapes in a single `events` table without a polymorphic schema. The chosen approach: store values as JSON `String?` in Room and provide a typed `EventValue` sealed class at the domain boundary via kotlinx.serialization TypeConverters.

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
    data class ErrorValue(
        val kind: ErrorKind,
        val raw: String,    // original JSON string, preserved for diagnostics and future recovery
    ) : EventValue()
}
```

`NONE`-type events carry `null` for their value — there is no `EventValue.None` variant. Null is the canonical representation in both the DB column and the domain model. `@Serializable` is required on each non-null variant so kotlinx.serialization can encode/decode the polymorphic sealed class via the class discriminator. `ErrorValue` is produced by the TypeConverter when a value can't be decoded cleanly. On encode, `ErrorValue` bypasses normal serialization — `raw` is written verbatim — so an older app version reading then writing a value from a newer version leaves the DB bytes identical. This is the forward-compatibility contract.

`DurationValue` uses `kotlin.time.Duration` at the domain level. Because kotlinx.serialization encodes `Duration` as an ISO 8601 string by default, a `DurationAsSecondsSerializer` is used to store it as total seconds (Long) in the JSON — compact and unambiguous. The serializer lives in the `local-storage` segment alongside `EventValueConverter`.

### Invariants and repair strategy

Invariants are enforced at two layers:

- **At input time (UI):** the UI prevents out-of-range values from being submitted.
- **At read time (TypeConverter):** violations produce an `ErrorValue` with the appropriate `ErrorKind` and the original raw string. The exception is never propagated.

| Condition | Result |
|---|---|
| `Scale.value` outside `1..10` | `ErrorValue(OUT_OF_RANGE, raw)` |
| `DurationValue.duration` < `Duration.ZERO` | `ErrorValue(OUT_OF_RANGE, raw)` |
| `NumberValue.value` | Any finite Double including negative — no constraint |
| Unknown type discriminator | `ErrorValue(UNRECOGNIZED_TYPE, raw)` — preserves data from future app versions |
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
                else -> decoded
            }
        } catch (e: SerializationException) {
            // Distinguish unrecognized type discriminator from unparsable JSON
            val kind = if (raw.contains("\"type\"")) ErrorKind.UNRECOGNIZED_TYPE
                       else ErrorKind.UNPARSABLE
            EventValue.ErrorValue(kind, raw)
        }
    }
}
```

The converter lives in the `local-storage` segment (it's a Room concern) but consumes types defined here.

## Domain Models

These are plain Kotlin data classes with no framework dependencies.

```kotlin
data class Category(
    val id: String,              // UUID string
    val name: String,
    val emoji: String,           // single emoji character
    val color: Long,             // ARGB packed as Long (matches Compose Color's internal rep)
    val valueType: ValueType,
    val unit: String?,           // only meaningful when valueType == NUMBER
    val allowEmptyText: Boolean, // only meaningful when valueType == TEXT; read by event UI to gate submission
    val sortOrder: Int,          // ascending; lower = higher in list; new categories get currentMin - 1
)

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

### ValueType change on a Category

Changing a category's `valueType` after events have been logged is **not prevented at the domain layer**. The result is a category whose historical events carry `EventValue` instances that no longer match the current `valueType`. The UI must handle this gracefully — rendering mismatched events as a raw fallback rather than crashing. Whether to allow the edit is a UI/UX decision, not a domain constraint.

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

## Open Questions & Future Decisions

### Deferred

1. **Category color picker** — how is `color` selected by the user? Preset palette vs. full color wheel. This is a UI concern but constrains what ARGB values appear in the domain.
2. **ValueType extensibility** — if users want a custom value type in the future, the sealed class must be opened or replaced with a plugin model. Deferred to v2.
3. **Multi-value events** — can a single event carry more than one value (e.g., systolic + diastolic blood pressure)? Currently no. Revisit if requested.

## References

- `docs/high-level-design.md` — system design and value model decision
- [kotlinx.serialization sealed classes](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md)
