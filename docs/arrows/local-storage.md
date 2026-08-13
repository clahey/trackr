# Arrow: local-storage

Room persistence layer: `TrackrRepository` interface, entities, DAOs, type converters, image store, and Android Auto Backup configuration.

## Status

**PARTIAL** — last audited 2026-07-27. 31 of 31 specs implemented. LS-BE-041 was reworded this pass to match actual (and accepted) behavior — fire-and-forget, no ordering guarantee against first UI frame — rather than fixing the code; see finding 2. Remaining gap is annotation-traceability only (corrected count: 12, not 9).

## References

### HLD
- docs/high-level-design.md (System Design, Future Backend Strategy)

### LLD
- docs/llds/local-storage.md

### EARS
- docs/specs/local-storage.md (31 specs: LS-BE-*)

### Tests
- app/src/test/java/net/clahey/trackr/data/local/converters/InstantConverterTest.kt
- app/src/test/java/net/clahey/trackr/data/local/converters/StringListConverterTest.kt
- app/src/test/java/net/clahey/trackr/FakeTrackrRepositoryTest.kt
- app/src/androidTest/java/net/clahey/trackr/data/local/MigrationTest.kt

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
| Repository/DAOs/Converters | LS-BE-001 to 062 | all | 0 | 0 |
| Image store / startup | LS-BE-070, LS-BE-080/081 | all | 0 | 0 |
| Auto Backup | LS-BE-090 to 092 | all | 0 | 0 |

**Summary:** 31 of 31 active specs implemented; 0 active gaps.

## Key Findings

1. **Auto Backup is genuinely configured, not just stubbed** — `data_extraction_rules.xml` and `backup_rules.xml` both include `trackr.db`, `images/`, and `datastore/` for both `cloud-backup` and `device-transfer`, and both carry `@spec LS-BE-090, LS-BE-091, LS-BE-092` annotations. This matches the HLD's stated v1 data-safety baseline. Verified by reading the XML directly, not just trusting the `[x]` marker. (LS-BE-090/091/092 are correctly annotated — an earlier pass wrongly counted them as unannotated; corrected in finding 3.)
2. **LS-BE-041 descoped, not fixed (2026-07-27, user decision).** The spec previously claimed an ordering guarantee ("before any user-visible UI is shown") that `TrackrApplication.kt`'s fire-and-forget `appScope.launch { repository.onStartup() }` never actually met — `MainActivity.kt` renders `setContent { ... AppScaffold() }` with no dependency on `onStartup()`. Rather than implementing a real block-on-first-frame fix, the spec and LLD were reworded to describe the guarantee that's actually needed today: `onStartup` only deletes locally-orphaned image files (LS-BE-040), a purely additive cleanup nothing in the UI reads, so fire-and-forget is fine. The spec now flags explicitly that this must be revisited if `onStartup` ever takes on a responsibility the UI depends on (e.g. a migration) — see `docs/specs/local-storage.md` LS-BE-041 and `docs/llds/local-storage.md`'s `onStartup` section.
3. **12 implemented specs have no `@spec` annotation anywhere** (corrected 2026-07-27; previously counted as 9, and wrongly included LS-BE-090/091/092 which are in fact annotated): `LS-BE-001, LS-BE-002, LS-BE-003, LS-BE-004, LS-BE-033, LS-BE-050, LS-BE-054, LS-BE-060, LS-BE-061, LS-BE-062, LS-BE-070, LS-BE-071`. LS-BE-050/054/071 are newly identified this pass — `EventValueConverter.kt`/`ValueTypeConverter.kt` carry only `DM-*` tags, and `EventEntity.kt`'s CASCADE FK has no tag at all.
4. **Test coverage gap**: LS-BE-011, 012, 013, 020, 021, 030-032, 040, 050, 052, 054, 060-062, 071 have no test-file `@spec` citation anywhere (LS-BE-052 is annotated only in main `EventValue.kt`, not in any test). `CategoryDaoTest.kt` and `EventDaoTest.kt` (2026-08-06) were removed — they were empty stubs with 0 `@Test` methods, so their `@spec` tags weren't backing any real coverage; removal made this gap explicit rather than creating it. LS-BE-070 (below) is no longer in this list.
5. **Real Room migration test coverage now exists** (2026-08-12): `MigrationTest.kt` uses `MigrationTestHelper` against an emulator/device to run `MIGRATION_2_3` through `MIGRATION_4_5` (plus the direct `MIGRATION_3_5` path) and assert on the resulting data — the first instrumented Room test in this project; `docs/schemas/` assets are wired into the `androidTest` source set for this. `MIGRATION_1_2` is the one migration this can't cover: no `1.json` schema was ever exported (predates `exportSchema = true` being enabled) and it isn't reconstructable after the fact, so there's no "before" schema `MigrationTestHelper` can build a v1 database from. LS-BE-070 is now backed by a real test for the first time.

## Work Required

### Must Fix
_None._

### Should Fix
1. Backfill `@spec` annotations on the 12 unannotated implemented specs listed in finding 3.
2. Add test-file `@spec` citations for the specs listed in finding 4.

### Nice to Have
_None noted this pass._
