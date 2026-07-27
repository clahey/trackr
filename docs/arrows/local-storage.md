# Arrow: local-storage

Room persistence layer: `TrackrRepository` interface, entities, DAOs, type converters, image store, and Android Auto Backup configuration.

## Status

**PARTIAL** — last audited 2026-07-27. 30 of 31 specs implemented. LS-BE-041's startup-ordering guarantee is now **confirmed** (not just suspected) unmet by the current code — a real gap, not a stale marker — plus an annotation-traceability gap (corrected count: 12, not 9).

## References

### HLD
- docs/high-level-design.md (System Design, Future Backend Strategy)

### LLD
- docs/llds/local-storage.md

### EARS
- docs/specs/local-storage.md (31 specs: LS-BE-*)

### Tests
- app/src/androidTest/java/net/clahey/trackr/data/local/CategoryDaoTest.kt
- app/src/androidTest/java/net/clahey/trackr/data/local/EventDaoTest.kt
- app/src/test/java/net/clahey/trackr/data/local/converters/InstantConverterTest.kt
- app/src/test/java/net/clahey/trackr/data/local/converters/StringListConverterTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt

### Code
- app/src/main/java/net/clahey/trackr/data/local/CategoryDao.kt
- app/src/main/java/net/clahey/trackr/data/local/EventDao.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalTrackrRepository.kt
- app/src/main/java/net/clahey/trackr/data/local/LocalImageStore.kt
- app/src/main/java/net/clahey/trackr/data/local/converters/InstantConverter.kt
- app/src/main/java/net/clahey/trackr/data/local/converters/StringListConverter.kt
- app/src/main/java/net/clahey/trackr/domain/EventValue.kt
- app/src/main/res/xml/data_extraction_rules.xml
- app/src/main/res/xml/backup_rules.xml

## Architecture

**Purpose:** Sole persistence seam (`TrackrRepository`) insulating ViewModels from storage, backed today by Room + local file storage for images, with Android Auto Backup as the v1 data-safety baseline (per HLD "Future Backend Strategy").

**Key Components:**
1. `TrackrRepository` interface — the seam for a future backend swap
2. Room entities, DAOs, TypeConverters — local persistence
3. `LocalImageStore` — filesystem image storage, FileProvider-backed
4. Auto Backup config (`data_extraction_rules.xml`, `backup_rules.xml`)

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| Repository/DAOs/Converters | LS-BE-001 to 062 | all but 1 | 0 | 1 |
| Image store / startup | LS-BE-070, LS-BE-080/081 | all | 0 | 0 |
| Auto Backup | LS-BE-090 to 092 | all | 0 | 0 |

**Summary:** 30 of 31 active specs implemented; 1 active gap (LS-BE-041).

## Key Findings

1. **Auto Backup is genuinely configured, not just stubbed** — `data_extraction_rules.xml` and `backup_rules.xml` both include `trackr.db`, `images/`, and `datastore/` for both `cloud-backup` and `device-transfer`, and both carry `@spec LS-BE-090, LS-BE-091, LS-BE-092` annotations. This matches the HLD's stated v1 data-safety baseline. Verified by reading the XML directly, not just trusting the `[x]` marker. (LS-BE-090/091/092 are correctly annotated — an earlier pass wrongly counted them as unannotated; corrected in finding 3.)
2. **LS-BE-041 confirmed a genuine, still-unresolved gap (2026-07-27).** ("`onStartup` shall be called once per app process start before any user-visible UI is shown".) `TrackrApplication.kt`'s `onCreate()` does `appScope.launch { repository.onStartup() }` — fire-and-forget on `Dispatchers.IO`, no join/await. `MainActivity.kt`'s `onCreate()` calls `setContent { ... AppScaffold() }` immediately, with no dependency on `onStartup()` having started or completed. The ordering guarantee is **not met**: the first UI frame can render before orphan-file cleanup even starts. This needs an implementation fix (e.g. block first frame on completion, or move the cleanup earlier in the startup sequence), not just a documentation decision.
3. **12 implemented specs have no `@spec` annotation anywhere** (corrected 2026-07-27; previously counted as 9, and wrongly included LS-BE-090/091/092 which are in fact annotated): `LS-BE-001, LS-BE-002, LS-BE-003, LS-BE-004, LS-BE-033, LS-BE-050, LS-BE-054, LS-BE-060, LS-BE-061, LS-BE-062, LS-BE-070, LS-BE-071`. LS-BE-050/054/071 are newly identified this pass — `EventValueConverter.kt`/`ValueTypeConverter.kt` carry only `DM-*` tags, and `EventEntity.kt`'s CASCADE FK has no tag at all.
4. **Test coverage gap**: LS-BE-050, 052, 054, 060-062, 070, 071 have no test-file `@spec` citation anywhere (LS-BE-052 is annotated only in main `EventValue.kt`, not in any test).

## Work Required

### Must Fix
_None — no user-visible symptom currently traced to the LS-BE-041 gap; tracked as Should Fix given it's a real ordering race, not just a doc mismatch._

### Should Fix
1. **LS-BE-041** — fix the actual ordering race: `TrackrApplication`'s fire-and-forget `onStartup()` launch does not satisfy the "before any user-visible UI is shown" guarantee. Needs a real implementation change (block first frame, or restructure startup sequencing), not a spec-wording fix.
2. Backfill `@spec` annotations on the 12 unannotated implemented specs listed in finding 3.
3. Add test-file `@spec` citations for the specs listed in finding 4.

### Nice to Have
_None noted this pass._
