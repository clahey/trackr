# Arrow: data-model

Domain model: `ValueType`, the `EventValue` sealed class hierarchy, and core domain entities (`Category`, `Event`).

## Status

**AUDITED** — last audited 2026-07-27 (re-verified; no functional code changes to this segment since the 2026-06-17 pass — the only diff in its territory was new `CAT-UI-*`-tagged methods in `LocalTrackrRepository.kt`/`ValueTypeConversion.kt` owned by `category-management`, not this segment). All 48 specs implemented; the only finding is a traceability gap (missing `@spec` annotations), not a functional one. Traceability list corrected this pass: 21 IDs, not 24 (DM-PROC-019 added, DM-PROC-011/017 confirmed already annotated and removed).

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

1. **21 implemented specs have no `@spec` annotation anywhere in code** (corrected 2026-07-27; previously counted as 24) — `DM-DATA-001, DM-DATA-010–016, DM-DATA-020–024, DM-DATA-030–034, DM-PROC-010, DM-PROC-012, DM-PROC-019`. The behavior exists (spec status is `[x]`, re-confirmed by direct code read including a spot-check of DM-DATA-013's negative-duration rejection and DM-PROC-007/008/008b's range checks in `EventValueConverter.kt`), it just isn't linked back to a `// @spec` comment. This is a code-hygiene/traceability gap, not a missing feature. (DM-PROC-011 and DM-PROC-017 were previously miscounted into this list — both are in fact already annotated, just not in a test file for DM-PROC-011's case.)
2. **Test-file coverage gap, distinct from the annotation gap above**: DM-PROC-011 (annotated in main code, not cited by any test) and DM-DATA-025/026/027 (annotated in `Category.kt`, but no test file cites them — `CategoryTest.kt` only cites DM-PROC-018).
3. No reverse orphans in this segment — every `@spec DM-*` annotation found in code points to a spec ID that exists in `docs/specs/data-model.md` (re-verified 2026-07-27).

## Work Required

### Must Fix
_None — fully implemented._

### Should Fix
1. Add `// @spec DM-DATA-...`/`DM-PROC-...` annotations to the 21 unannotated implementations listed above, so future audits don't have to re-derive coverage by reading code.
2. Add test-file `@spec` citations for DM-PROC-011 and DM-DATA-025/026/027.

### Nice to Have
_None noted this pass._
