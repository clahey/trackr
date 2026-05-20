# Data Model Specs

LLD: `docs/llds/data-model.md`

---

## ValueType

- [ ] **DM-DATA-001**: The system shall represent category value types as a sealed class with variants: None, Scale, Boolean, Number, Text, Duration, Exercise, and Unknown(raw).
- [x] **DM-DATA-002**: When decoding a ValueType string that does not match any known variant name, the system shall produce Unknown(raw), preserving the original string verbatim.
- [x] **DM-DATA-003**: When encoding an Unknown(raw) ValueType, the system shall write the raw string verbatim without modification.
- [x] **DM-DATA-004**: When encoding a known ValueType (None, Scale, Boolean, Number, Text, Duration), the system shall serialize it to a fixed lowercase name string.

## EventValue

- [ ] **DM-DATA-010**: The system shall represent event values as a sealed class with variants: Scale, BooleanValue, NumberValue, TextValue, DurationValue, ExerciseValue, and ErrorValue.
- [ ] **DM-DATA-011**: The system shall represent the absence of a value (for None-type categories) as null rather than as an EventValue variant.
- [ ] **DM-DATA-012**: Scale values shall carry an integer in the inclusive range 1–10.
- [ ] **DM-DATA-013**: DurationValue values shall carry a non-negative `kotlin.time.Duration`; the domain model shall reject (via ErrorValue at read time) any stored value representing a negative duration.
- [ ] **DM-DATA-014**: NumberValue values shall carry a finite Double and an optional unit string (null means unitless).
- [ ] **DM-DATA-015**: ErrorValue shall carry an ErrorKind (UNPARSABLE, UNRECOGNIZED_TYPE, or OUT_OF_RANGE) and the original raw string for diagnostics and future recovery.
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

- [ ] **DM-DATA-020**: The system shall identify each Category by a UUID string that remains stable across local storage and future cloud sync.
- [ ] **DM-DATA-021**: Category color shall be stored as an ARGB-packed Long.
- [ ] **DM-DATA-022**: Category.unit shall be meaningful only when valueType is Number; the domain model shall not reject a non-null unit value for other value types.
- [ ] **DM-DATA-023**: Category.allowEmptyText shall be meaningful only when valueType is Text; the domain model shall not reject the field for other value types.
- [ ] **DM-DATA-024**: Category.sortOrder shall be an integer where a lower value indicates a higher position in the displayed list.
## Event

- [ ] **DM-DATA-030**: The system shall identify each Event by a UUID string that remains stable across local storage and future cloud sync.
- [ ] **DM-DATA-031**: Event.timestamp shall be a `java.time.Instant` representing when the event occurred (user-editable).
- [ ] **DM-DATA-032**: Event.createdAt shall be a `java.time.Instant` recording the wall-clock moment the record was first created; it shall not be modified on subsequent edits.
- [ ] **DM-DATA-033**: Event.value shall be null for events whose category has valueType None.
- [ ] **DM-DATA-034**: Event.imagePaths shall be a list of absolute file-system paths to images in app-private storage; an empty list means no images are attached.

## Value Type Mismatch Helpers

- [x] **DM-PROC-013**: `matchesValueType(value: EventValue?, type: ValueType)` shall return `true` only when: the value's runtime type is the expected variant for `type`; or `value` is null and `type` is `None`; or `value` is an `ErrorValue` whose `inferredType` is non-null and equals `type.raw` when `type` is `Unknown`. It shall return `false` in all other cases, including: an `ErrorValue` whose `inferredType` does not match, or is null; a non-null value when `type` is `None`; a concrete value of the wrong type.
- [x] **DM-PROC-014**: `convertOrDefault` shall return `ConversionOutcome.Discard` when the target type is `None` or `Unknown`.
- [x] **DM-PROC-015**: `convertOrDefault` shall return `ConversionOutcome.Converted(v)` when `convertEventValue` produces a value whose runtime type matches the target type.
- [x] **DM-PROC-016**: `convertOrDefault` shall return `ConversionOutcome.UsedDefault(v)` when the input is an `ErrorValue` (regardless of target type, provided target is not `None`/`Unknown`) or when `convertEventValue` does not produce a value of the target type; `v` shall be `defaultForType(targetType)`.

## Ordering and Invariants

- [ ] **DM-PROC-010**: When two events share the same timestamp, the system shall order them by createdAt ascending, then by id string ascending as a tiebreaker.
- [ ] **DM-PROC-011**: When logging a new event for a Number-type category, the system shall copy Category.unit into NumberValue.unit at the time of logging, so historical events retain the unit that was in effect when they were created.
- [ ] **DM-PROC-012**: The domain model shall not reject TextValue("") regardless of Category.allowEmptyText; enforcement of the empty-text policy is the responsibility of the event logging UI.
