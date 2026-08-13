# Arrow: local-storage

Room persistence layer: `TrackrRepository` interface, entities, DAOs, type converters, image store, and Android Auto Backup configuration.

## Status

**PARTIAL** — last audited 2026-08-12 (deep full-text pass; supersedes the 2026-07-27 reference-only audit). 32 of 32 specs implemented (corrected count — LS-BE-093 had been dropped from the prior pass's total). LS-BE-010 and LS-BE-011 were reworded this pass to match actual (and correct) behavior — hierarchical category ordering and caller-assigned `sortOrder`, respectively — rather than changing code; see finding 6. Remaining gap is annotation-traceability only (12 unannotated specs, unchanged from prior pass).

## References

### HLD
- docs/high-level-design.md (System Design, Future Backend Strategy)

### LLD
- docs/llds/local-storage.md

### EARS
- docs/specs/local-storage.md (32 specs: LS-BE-*)

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
| Image store / startup | LS-BE-070, LS-BE-080/081, LS-BE-093 | all | 0 | 0 |
| Auto Backup | LS-BE-090 to 092 | all | 0 | 0 |

**Summary:** 32 of 32 active specs implemented; 0 active gaps.

## Key Findings

1. **Auto Backup is genuinely configured, not just stubbed** — `data_extraction_rules.xml` and `backup_rules.xml` both include `trackr.db`, `images/`, and `datastore/` for both `cloud-backup` and `device-transfer`, and both carry `@spec LS-BE-090, LS-BE-091, LS-BE-092` annotations. This matches the HLD's stated v1 data-safety baseline. Verified by reading the XML directly, not just trusting the `[x]` marker. (LS-BE-090/091/092 are correctly annotated — an earlier pass wrongly counted them as unannotated; corrected in finding 3.)
2. **LS-BE-041 descoped, not fixed (2026-07-27, user decision).** The spec previously claimed an ordering guarantee ("before any user-visible UI is shown") that `TrackrApplication.kt`'s fire-and-forget `appScope.launch { repository.onStartup() }` never actually met — `MainActivity.kt` renders `setContent { ... AppScaffold() }` with no dependency on `onStartup()`. Rather than implementing a real block-on-first-frame fix, the spec and LLD were reworded to describe the guarantee that's actually needed today: `onStartup` only deletes locally-orphaned image files (LS-BE-040), a purely additive cleanup nothing in the UI reads, so fire-and-forget is fine. The spec now flags explicitly that this must be revisited if `onStartup` ever takes on a responsibility the UI depends on (e.g. a migration) — see `docs/specs/local-storage.md` LS-BE-041 and `docs/llds/local-storage.md`'s `onStartup` section.
3. **12 implemented specs have no `@spec` annotation anywhere** (corrected 2026-07-27; previously counted as 9, and wrongly included LS-BE-090/091/092 which are in fact annotated): `LS-BE-001, LS-BE-002, LS-BE-003, LS-BE-004, LS-BE-033, LS-BE-050, LS-BE-054, LS-BE-060, LS-BE-061, LS-BE-062, LS-BE-070, LS-BE-071`. LS-BE-050/054/071 are newly identified this pass — `EventValueConverter.kt`/`ValueTypeConverter.kt` carry only `DM-*` tags, and `EventEntity.kt`'s CASCADE FK has no tag at all.
4. **Test coverage gap**: LS-BE-011, 012, 013, 020, 021, 030-032, 040, 050, 052, 054, 060-062, 071 have no test-file `@spec` citation anywhere (LS-BE-052 is annotated only in main `EventValue.kt`, not in any test). `CategoryDaoTest.kt` and `EventDaoTest.kt` (2026-08-06) were removed — they were empty stubs with 0 `@Test` methods, so their `@spec` tags weren't backing any real coverage; removal made this gap explicit rather than creating it. LS-BE-070 (below) is no longer in this list.
5. **Real Room migration test coverage now exists** (2026-08-12): `MigrationTest.kt` uses `MigrationTestHelper` against an emulator/device to run `MIGRATION_2_3` and `MIGRATION_3_5` and assert on the resulting data — the first instrumented Room test in this project; `docs/schemas/` assets are wired into the `androidTest` source set for this. `MIGRATION_1_2` is the one migration this can't cover: no `1.json` schema was ever exported (predates `exportSchema = true` being enabled) and it isn't reconstructable after the fact, so there's no "before" schema `MigrationTestHelper` can build a v1 database from. LS-BE-070 is now backed by a real test for the first time. (`MIGRATION_3_4`/`MIGRATION_4_5`, the two-step path superseded by `MIGRATION_3_5`, were retired 2026-08-13 once the one device that had reached version 4 was confirmed upgraded past it — see finding 7.)
7. **`MIGRATION_3_4`/`MIGRATION_4_5` retired (2026-08-13).** They existed only to carry the one real device through its transient version-4 state to version 5; once that device was confirmed at version 5 (checked directly via `PRAGMA user_version` on the installed app's database), there was no remaining reason to keep the two-step path alive. `MIGRATION_3_5` is now the sole path from before the `reminders` table existed to the current schema. `MigrationTest.kt`'s `migrate4To5_coalescesNullWindowFieldsAndPreservesRealOnes` test (which existed specifically to verify `MIGRATION_4_5`'s `COALESCE` behavior) was removed along with it — that behavior no longer exists in the app.
6. **Deep full-text audit (2026-08-12)** — read every spec, the full LLD, and the actual code (not just checked reference existence). Result: 30/32 CONSISTENT outright; LS-BE-010 and LS-BE-011 reworded to match actual (correct) code rather than the code being wrong (LS-BE-010 now describes the real hierarchical grouping instead of a flat `sortOrder ASC` claim; LS-BE-011 now attributes `currentMin - 1` assignment to the caller, matching the LLD's own "caller sets sortOrder" comment). The migration chain and nullability story (the area most likely to have rotted after the recent NOT NULL change) checked out exactly against the LLD — no drift found there. Several LLD/HLD prose bugs were found and fixed in the same pass: `saveCategoryWithReminder`'s LLD prose said clearing a reminder "no-ops" when the code actually issues an explicit `DELETE` (and the LLD was also missing the `migrateFromType` param and the `nextFireAt`-preservation logic entirely); the HLD's "two tables" line was stale (three, including `reminders`); `ReminderEntity.mode`'s documented casing (`"fixed"`/`"random"`) was stale (actual: uppercase `.name`, lowercase decode-only fallback); a resolved "Deferred" Open Question (nullable `Long` params in `getEvents`) was removed since the dispatch-pattern decision already shipped. One finding was **not** fixable within this segment: `local-storage.md`'s claim that its event sort order "matches" `data-model.md`'s canonical ordering is false on the `createdAt` axis — `data-model.md § Same-timestamp ordering` says ascending, this segment's code/tests use descending. This segment's own ordering is correct and tested; the doc now states that plainly and flags the mismatch for `data-model`'s owner rather than asserting a false match (cross-segment, not fixed here — see Work Required).

## Work Required

### Must Fix
_None._

### Should Fix
1. Backfill `@spec` annotations on the 12 unannotated implemented specs listed in finding 3.
2. Add test-file `@spec` citations for the specs listed in finding 4.
3. **Cross-segment**: `data-model.md § Same-timestamp ordering` (DM-PROC-010) says `createdAt` ascending; this segment's implemented-and-tested behavior is `createdAt` descending. One of the two docs is wrong — needs `data-model`'s owner to reconcile (not this segment's call to make unilaterally).

### Nice to Have
_None noted this pass._
