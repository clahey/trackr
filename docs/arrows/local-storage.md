# Arrow: local-storage

Room persistence layer: `TrackrRepository` interface, entities, DAOs, type converters, image store, and Android Auto Backup configuration.

## Status

**PARTIAL** — last audited 2026-06-17 (git SHA `be05346`). 30 of 31 specs implemented; one open item (startup-ordering guarantee) needs verification, plus an annotation-traceability gap.

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

1. **Auto Backup is genuinely configured, not just stubbed** — `data_extraction_rules.xml` and `backup_rules.xml` both include `trackr.db`, `images/`, and `datastore/` for both `cloud-backup` and `device-transfer`, and both carry `@spec LS-BE-090, LS-BE-091, LS-BE-092` annotations. This matches the HLD's stated v1 data-safety baseline. Verified by reading the XML directly, not just trusting the `[x]` marker.
2. **LS-BE-041** ("`onStartup` shall be called once per app process start before any user-visible UI is shown") is marked an active gap. `TrackrApplication.kt` does call `repository.onStartup()` in `appScope.launch { ... }` at app startup, but that's a fire-and-forget coroutine launch, not a guarantee that it completes (or even starts) before the first UI frame. The spec's ordering guarantee is plausibly *not* met as written — worth a closer look before relying on it for orphan-image cleanup timing.
3. **9 implemented specs have no `@spec` annotation**: LS-BE-001-004, 033, 060-062, 070, 090-092 region — annotation/traceability gap only.

## Work Required

### Must Fix
_None confirmed — LS-BE-041 needs verification before being called either a real gap or a stale marker (see Should Fix)._

### Should Fix
1. Verify whether `TrackrApplication`'s fire-and-forget `onStartup()` launch actually satisfies LS-BE-041's ordering guarantee, or whether it's a real race with first-frame render. (LS-BE-041)
2. Backfill `@spec` annotations on the 9 unannotated implemented specs listed above.

### Nice to Have
_None noted this pass._
