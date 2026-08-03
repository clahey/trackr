# Data Model Specs

LLD: `docs/llds/data-model.md`

---

## ValueType

- [x] **DM-DATA-001**: The system shall represent category value types as a sealed class with variants: None, Scale, Boolean, Number, Text, Duration, Exercise, and Unknown(raw).
- [x] **DM-DATA-002**: When decoding a ValueType string that does not match any known variant name, the system shall produce Unknown(raw), preserving the original string verbatim.
- [x] **DM-DATA-003**: When encoding an Unknown(raw) ValueType, the system shall write the raw string verbatim without modification.
- [x] **DM-DATA-004**: When encoding a known ValueType (None, Scale, Boolean, Number, Text, Duration), the system shall serialize it to a fixed lowercase name string.

## EventValue

- [x] **DM-DATA-010**: The system shall represent event values as a sealed class with variants: Scale, BooleanValue, NumberValue, TextValue, DurationValue, ExerciseValue, and ErrorValue.
- [x] **DM-DATA-011**: The system shall represent the absence of a value (for None-type categories) as null rather than as an EventValue variant.
- [x] **DM-DATA-012**: Scale values shall carry an integer in the inclusive range 1–10.
- [x] **DM-DATA-013**: DurationValue values shall carry a non-negative `kotlin.time.Duration`; the domain model shall reject (via ErrorValue at read time) any stored value representing a negative duration.
- [x] **DM-DATA-014**: NumberValue values shall carry a finite Double and an optional unit string (null means unitless).
- [x] **DM-DATA-015**: ErrorValue shall carry an ErrorKind (UNPARSABLE, UNRECOGNIZED_TYPE, or OUT_OF_RANGE) and the original raw string for diagnostics and future recovery.
- [x] **DM-DATA-016**: ExerciseValue values shall carry two positive integer fields: sets (≥ 1) and reps (≥ 1).

## EventValue TypeConverter

- [x] **DM-PROC-001**: When encoding a null EventValue, the system shall produce a null string.
- [x] **DM-PROC-002**: When encoding an ErrorValue, the system shall write its raw field verbatim, without re-serializing, so that bytes written by a newer app version are preserved unchanged by an older version.
- [x] **DM-PROC-003**: When encoding a non-null, non-ErrorValue EventValue, the system shall serialize it as JSON using a "type" class discriminator.
- [x] **DM-PROC-004**: When decoding a null string, the system shall produce a null EventValue.
- [x] **DM-PROC-005**: When decoding a JSON string whose "type" discriminator names an unrecognized EventValue variant, the system shall produce ErrorValue(UNRECOGNIZED_TYPE, raw, inferredType) where inferredType is the value of the "type" field extracted from the JSON, or null if extraction fails.
- [x] **DM-PROC-006**: When decoding a string that cannot be parsed as JSON, the system shall produce ErrorValue(UNPARSABLE, raw).
- [x] **DM-PROC-007**: When decoding a Scale EventValue whose value is outside 1–10, the system shall produce ErrorValue(OUT_OF_RANGE, raw).
- [x] **DM-PROC-008**: When decoding a DurationValue whose minutes field is negative, the system shall produce ErrorValue(OUT_OF_RANGE, raw).
- [x] **DM-PROC-008b**: When decoding an ExerciseValue whose sets or reps is less than 1, the system shall produce ErrorValue(OUT_OF_RANGE, raw).
- [x] **DM-PROC-009**: The EventValue TypeConverter shall never propagate a serialization exception to the caller.

## Category

- [x] **DM-DATA-020**: The system shall identify each Category by a UUID string that remains stable across local storage and future cloud sync.
- [x] **DM-DATA-021**: Category color shall be stored as an ARGB-packed Long.
- [x] **DM-DATA-022**: A Number category's unit — carried as the `unit` field of the `NumberValue` in `Category.defaultValue` — shall be meaningful only when `valueType` is Number; the domain model shall not reject a non-null `defaultValue` for other value types.
- [x] **DM-DATA-023**: Category.allowEmptyText shall be meaningful only when valueType is Text; the domain model shall not reject the field for other value types.
- [x] **DM-DATA-024**: Category.sortOrder shall be an integer where a lower value indicates a higher position in the displayed list.
## Event

- [x] **DM-DATA-030**: The system shall identify each Event by a UUID string that remains stable across local storage and future cloud sync.
- [x] **DM-DATA-031**: Event.timestamp shall be a `java.time.Instant` representing when the event occurred (user-editable).
- [x] **DM-DATA-032**: Event.createdAt shall be a `java.time.Instant` recording the wall-clock moment the record was first created; it shall not be modified on subsequent edits.
- [x] **DM-DATA-033**: Event.value shall be null for events whose category has valueType None.
- [x] **DM-DATA-034**: Event.imagePaths shall be a list of absolute file-system paths to images in app-private storage; an empty list means no images are attached.

## Value Type Mismatch Helpers

- [x] **DM-PROC-013**: `matchesValueType(value: EventValue?, type: ValueType)` shall return `true` only when: the value's runtime type is the expected variant for `type`; or `value` is null and `type` is `None`; or `value` is an `ErrorValue` whose `inferredType` is non-null and equals `type.raw` when `type` is `Unknown`. It shall return `false` in all other cases, including: an `ErrorValue` whose `inferredType` does not match, or is null; a non-null value when `type` is `None`; a concrete value of the wrong type.
- [x] **DM-PROC-014**: `convertOrDefault` shall return `ConversionOutcome.Discard` when the target type is `None` or `Unknown`.
- [x] **DM-PROC-015**: `convertOrDefault` shall return `ConversionOutcome.Converted(v)` when `convertEventValue` produces a value whose runtime type matches the target type.
- [x] **DM-PROC-016**: `convertOrDefault` shall return `ConversionOutcome.UsedDefault(v)` when the input is an `ErrorValue` (regardless of target type, provided target is not `None`/`Unknown`) or when `convertEventValue` does not produce a value of the target type; `v` shall be `defaultForType(targetType)`.

## Category Hierarchy

- [x] **DM-DATA-025**: The system shall represent Category as a sealed class with two variants: MetaCategory (top-level, no parent) and SubCategory (child of exactly one MetaCategory).
- [x] **DM-DATA-026**: A MetaCategory shall carry non-null emoji, color, and valueType fields; it shall have no parentId or parent reference.
- [x] **DM-DATA-027**: A SubCategory shall carry nullable emoji, color, and valueType fields where null indicates inheritance from the parent; it shall carry a non-null MetaCategory parent reference populated at the repository layer.
- [x] **DM-DATA-028**: The system shall enforce a two-level constraint: a category with SubCategory children shall not be nested under another category; a SubCategory shall not be given children. Violations shall be rejected at the repository layer.
- [x] **DM-PROC-017**: When loading Category records, the system shall assemble MetaCategory and SubCategory domain objects from a single flat query of the categories table, making the assembly atomic with respect to concurrent reads. (Implementation: single `getAll()` query followed by an in-memory two-pass — first pass builds a MetaCategory map keyed by id, second pass attaches SubCategories to their parent; SubCategories whose parent id is absent are surfaced as MetaCategories per DM-PROC-022.)
- [x] **DM-PROC-018**: The `Category` sealed class shall declare abstract members `resolvedEmoji: String`, `resolvedColor: Long`, and `resolvedValueType: ValueType`; `MetaCategory` shall implement each by returning its own field directly; `SubCategory` shall implement each by returning its override field when non-null, or the parent's value otherwise.
- [x] **DM-PROC-019**: When un-nesting a SubCategory (removing it from its group), the system shall resolve any null (inherited) fields to the parent's current values at the time of the operation before persisting the record as a MetaCategory.
- [x] **DM-PROC-020**: When reparenting a category into a group, the system shall preserve all current explicit field values as overrides regardless of whether they match the new parent's values.
- [x] **DM-PROC-021**: When migrating events due to a MetaCategory valueType change, the system shall include events belonging to SubCategories whose valueType is null (inheriting); SubCategories with an explicit valueType override shall be excluded from the migration.
- [x] **DM-PROC-022**: When assembling Category records, if a SubCategory's parentId references an entity not present in the query result, the system shall surface that SubCategory as a MetaCategory using its own stored field values; any null fields (emoji, color, valueType) shall be resolved using the same null-field fallbacks used for MetaCategory assembly.

## Ordering and Invariants

- [x] **DM-PROC-010**: When two events share the same timestamp, the system shall order them by createdAt ascending, then by id string ascending as a tiebreaker.
- [x] **DM-PROC-011**: When logging a new event for a Number-type category, the system shall seed the event's `NumberValue.unit` from `Category.resolvedDefaultValue`'s `NumberValue.unit` at the time of logging, so historical events retain the unit that was in effect when they were created.
- [x] **DM-PROC-012**: The domain model shall not reject TextValue("") regardless of Category.allowEmptyText; enforcement of the empty-text policy is the responsibility of the event logging UI.
