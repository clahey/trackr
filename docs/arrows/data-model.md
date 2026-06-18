# Arrow: data-model

Domain model: `ValueType`, the `EventValue` sealed class hierarchy, and core domain entities (`Category`, `Event`).

## Status

**AUDITED** — last audited 2026-06-17 (git SHA `be05346`). All 48 specs implemented; the only finding is a traceability gap (missing `@spec` annotations), not a functional one.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/data-model.md

### EARS
- docs/specs/data-model.md (48 specs: DM-DATA-*, DM-PROC-*)

### Tests
- app/src/test/java/net/clahey/trackr/data/local/converters/EventValueConverterTest.kt
- app/src/test/java/net/clahey/trackr/data/local/converters/ValueTypeConverterTest.kt
- app/src/test/java/net/clahey/trackr/data/local/MappersTest.kt
- app/src/test/java/net/clahey/trackr/domain/CategoryTest.kt
- app/src/test/java/net/clahey/trackr/domain/ValueTypeConversionTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryEditViewModelHierarchyTest.kt
- app/src/test/java/net/clahey/trackr/ui/category/CategoryListViewModelGroupTest.kt

### Code
- app/src/main/java/net/clahey/trackr/domain/Category.kt
- app/src/main/java/net/clahey/trackr/domain/ValueTypeConversion.kt
- app/src/main/java/net/clahey/trackr/data/local/converters/EventValueConverter.kt
- app/src/main/java/net/clahey/trackr/data/local/converters/ValueTypeConverter.kt
- app/src/main/java/net/clahey/trackr/data/local/EventDao.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalTrackrRepository.kt
- app/src/main/java/net/clahey/trackr/data/local/Mappers.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryEditViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListViewModel.kt
- app/src/main/java/net/clahey/trackr/ui/components/ValueInputField.kt

## Architecture

**Purpose:** Defines the core typed-value model (`ValueType` enum, `EventValue` sealed hierarchy covering Number/Text/Boolean/Duration/Scale/Exercise/Error/Unknown) and the `Category`/`Event` domain entities that the rest of the app builds on.

**Key Components:**
1. `ValueType` — enum of supported tracking value types
2. `EventValue` sealed class — typed runtime representation, including `ErrorValue` for unconvertible/corrupt data
3. `Category`/`Event` domain models — hierarchy (MetaCategory/SubCategory) and inheritance resolution

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| ValueType | DM-DATA-001 to ~004 | all | 0 | 0 |
| EventValue sealed class | DM-DATA-010 to 034, DM-PROC-001 to 019 | all | 0 | 0 |
| Domain Models | remaining DM-DATA-*/DM-PROC-* | all | 0 | 0 |

**Summary:** 48 of 48 active specs implemented. 0 deferred, 0 active gaps.

## Key Findings

1. **24 implemented specs have no `@spec` annotation anywhere in code** — DM-DATA-001, 010-016, 020-024, 030-034, and DM-PROC-010, DM-PROC-012 (full list reproducible via the audit's annotation cross-check). The behavior exists (spec status is `[x]` and was not contradicted by inspection of `Category.kt`/`ValueTypeConversion.kt`), it just isn't linked back to a `// @spec` comment. This is a code-hygiene/traceability gap, not a missing feature.
2. No reverse orphans in this segment — every `@spec DM-*` annotation found in code points to a spec ID that exists in `docs/specs/data-model.md`.

## Work Required

### Must Fix
_None — fully implemented._

### Should Fix
1. Add `// @spec DM-DATA-...` annotations to the unannotated implementations listed above, so future audits don't have to re-derive coverage by reading code.

### Nice to Have
_None noted this pass._
