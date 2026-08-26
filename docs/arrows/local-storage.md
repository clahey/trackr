# Arrow: local-storage

Room persistence layer: `TrackrRepository` interface, entities, DAOs, type converters, image store, and Android Auto Backup configuration.

## Status

**PARTIAL** — last audited 2026-08-12 (deep full-text pass; supersedes the 2026-07-27 reference-only audit), annotation backfill completed 2026-08-14. 36 of 36 specs implemented and annotated. LS-BE-010 and LS-BE-011 were reworded in the 2026-08-12 pass to match actual (and correct) behavior — hierarchical category ordering and caller-assigned `sortOrder`, respectively — rather than changing code; see finding 6. Remaining work is test-file citations (finding 4) and one cross-segment reconciliation this segment can't make alone (see Work Required).

## References

### HLD
- docs/high-level-design.md (System Design, Future Backend Strategy)

### LLD
- docs/llds/local-storage.md

### EARS
- docs/specs/local-storage.md (36 specs: LS-BE-*)

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

**Summary:** 33 of 33 active specs implemented; 0 active gaps.

## Key Findings

1. **Auto Backup is genuinely configured, not just stubbed** — `data_extraction_rules.xml` and `backup_rules.xml` both include `trackr.db`, `images/`, and `datastore/` for both `cloud-backup` and `device-transfer`, and both carry `@spec LS-BE-090, LS-BE-091, LS-BE-092` annotations. This matches the HLD's stated v1 data-safety baseline. Verified by reading the XML directly, not just trusting the `[x]` marker. (LS-BE-090/091/092 are correctly annotated — an earlier pass wrongly counted them as unannotated; corrected in finding 3.)
2. **LS-BE-041 descoped, not fixed (2026-07-27, user decision).** The spec previously claimed an ordering guarantee ("before any user-visible UI is shown") that `TrackrApplication.kt`'s fire-and-forget `appScope.launch { repository.onStartup() }` never actually met — `MainActivity.kt` renders `setContent { ... AppScaffold() }` with no dependency on `onStartup()`. Rather than implementing a real block-on-first-frame fix, the spec and LLD were reworded to describe the guarantee that's actually needed today: `onStartup` only deletes locally-orphaned image files (LS-BE-040), a purely additive cleanup nothing in the UI reads, so fire-and-forget is fine. The spec now flags explicitly that this must be revisited if `onStartup` ever takes on a responsibility the UI depends on (e.g. a migration) — see `docs/specs/local-storage.md` LS-BE-041 and `docs/llds/local-storage.md`'s `onStartup` section.
3. **Annotation backfill complete (2026-08-14).** All 32 specs now carry a `@spec` annotation in production code; the segment has no traceability gap left on the code side. The 12 that had been missing were `LS-BE-001/002/003/004/033/050/054/060/061/062/070/071`. Each was verified against the code before tagging rather than tagged by name match: LS-BE-001/002 went on the `TrackrRepository` interface declaration (the Flow-reads/suspend-writes shape is a property of the whole interface, not any one method); LS-BE-003 on both `saveCategory` and `saveEvent`, the two upserts it names; LS-BE-050/054 joined the existing `DM-*` tags on the two converters, which is exactly the delegation those specs describe; LS-BE-070 on `DatabaseModule.provideDatabase`, where the behavior is the *absence* of `fallbackToDestructiveMigration` — worth a comment since a missing call is invisible to a reader; LS-BE-071 on `EventEntity`'s `@Entity` block.
4. **Test coverage gap**: LS-BE-011, 012, 013, 020, 021, 030-032, 040, 050, 052, 054, 060-062, 071 have no test-file `@spec` citation anywhere (LS-BE-052 is annotated only in main `EventValue.kt`, not in any test). `CategoryDaoTest.kt` and `EventDaoTest.kt` (2026-08-06) were removed — they were empty stubs with 0 `@Test` methods, so their `@spec` tags weren't backing any real coverage; removal made this gap explicit rather than creating it. LS-BE-070 (below) is no longer in this list.
5. **Real Room migration test coverage now exists** (2026-08-12): `MigrationTest.kt` uses `MigrationTestHelper` against an emulator/device to run `MIGRATION_2_3` and `MIGRATION_3_5` and assert on the resulting data — the first instrumented Room test in this project; `docs/schemas/` assets are wired into the `androidTest` source set for this. `MIGRATION_1_2` is the one migration this can't cover: no `1.json` schema was ever exported (predates `exportSchema = true` being enabled) and it isn't reconstructable after the fact, so there's no "before" schema `MigrationTestHelper` can build a v1 database from. LS-BE-070 is now backed by a real test for the first time. (`MIGRATION_3_4`/`MIGRATION_4_5`, the two-step path superseded by `MIGRATION_3_5`, were retired 2026-08-13 once the one device that had reached version 4 was confirmed upgraded past it — see finding 7.)
7. **`MIGRATION_3_4`/`MIGRATION_4_5` retired (2026-08-13).** They existed only to carry the one real device through its transient version-4 state to version 5; once that device was confirmed at version 5 (checked directly via `PRAGMA user_version` on the installed app's database), there was no remaining reason to keep the two-step path alive. `MIGRATION_3_5` is now the sole path from before the `reminders` table existed to the current schema. `MigrationTest.kt`'s `migrate4To5_coalescesNullWindowFieldsAndPreservesRealOnes` test (which existed specifically to verify `MIGRATION_4_5`'s `COALESCE` behavior) was removed along with it — that behavior no longer exists in the app.
6. **Deep full-text audit (2026-08-12)** — read every spec, the full LLD, and the actual code (not just checked reference existence). Result: 30/32 CONSISTENT outright; LS-BE-010 and LS-BE-011 reworded to match actual (correct) code rather than the code being wrong (LS-BE-010 now describes the real hierarchical grouping instead of a flat `sortOrder ASC` claim; LS-BE-011 now attributes `currentMin - 1` assignment to the caller, matching the LLD's own "caller sets sortOrder" comment). The migration chain and nullability story (the area most likely to have rotted after the recent NOT NULL change) checked out exactly against the LLD — no drift found there. Several LLD/HLD prose bugs were found and fixed in the same pass: `saveCategoryWithReminder`'s LLD prose said clearing a reminder "no-ops" when the code actually issues an explicit `DELETE` (and the LLD was also missing the `migrateFromType` param and the `nextFireAt`-preservation logic entirely); the HLD's "two tables" line was stale (three, including `reminders`); `ReminderEntity.mode`'s documented casing (`"fixed"`/`"random"`) was stale (actual: uppercase `.name`, lowercase decode-only fallback); a resolved "Deferred" Open Question (nullable `Long` params in `getEvents`) was removed since the dispatch-pattern decision already shipped. One finding was **not** fixable within this segment: `local-storage.md`'s claim that its event sort order "matches" `data-model.md`'s canonical ordering is false on the `createdAt` axis — `data-model.md § Same-timestamp ordering` says ascending, this segment's code/tests use descending. This segment's own ordering is correct and tested; the doc now states that plainly and flags the mismatch for `data-model`'s owner rather than asserting a false match (cross-segment, not fixed here — see Work Required).

7. **LS-BE-014 added 2026-08-16 (33 specs, was 32)** — `getLatestEventTimestampIncludingChildren`, a `MAX(timestamp)` aggregate over a category and its SubCategories. Requested by the `reminders` segment, whose fire path was loading and fully decoding every event row of a category to compute one maximum, on a Doze wakeup inside a `goAsync()` budget (see `reminders.md` finding 9). Added as a `suspend` one-shot rather than a `Flow` — the caller is a fired alarm making a single decision, with no subscriber to update. No schema change: it is a new query over existing columns.

8. **`onStartup`'s trigger moved out of this segment's sight (2026-08-24).** Finding 2's description of the call site is superseded: `TrackrApplication.onCreate` no longer launches `onStartup()` at all. `MainActivity.onCreate` does, via `app-shell`'s `UiStartupWork.runOnce()`, so the scan is skipped in a process Android creates only to deliver a reminder broadcast — it was previously charging a whole-event-table read and an image-directory listing to every alarm delivery. LS-BE-041's first clause was reworded to state the constraint this seam actually depends on (once per process, not necessarily at process start) and to cite APP-PROC-001 as the owner of *which* hook, rather than naming the hook itself in two segments. The rest of LS-BE-041 — the "acceptable only because it's additive cleanup" argument from finding 2 — is unchanged and now has more force, since the scan starts later than it used to. The spec's `@spec` annotation moved with the call site to `UiStartupWork.kt`; it still has no test-file citation (finding 4's gap shape, though LS-BE-041 was never in that list). Backlog #20, driven from `app-shell`.

9. **The reminders boundary got a decidable test, and this segment gained its first EARS for the reminders table (2026-08-24).** The old line — "this segment owns the mechanical storage, not the design intent behind it" — was too fuzzy to settle cases, so detail landed on both sides: REM-DATA-001 stated "at most one Reminder per category" (true of any backend) and "foreign key declared `CASCADE DELETE`" (only true of SQL) in one sentence, REM-DATA-006 said "in a single database transaction" where the intent was just *atomically*, and the `ReminderEntity` field table was written out in full in both LLDs — which had drifted a second time beyond the `mode` casing the 2026-08-12 audit caught, the `times` note differing between the copies. Replaced by: **would this be different on a document store or behind a server API?** Yes → storage, here; no → the feature's intent, theirs. Applying it moved two mechanisms down as new specs: LS-BE-072 (the `ReminderEntity` FK cascade, exactly parallel to LS-BE-071 for `EventEntity`, which had it all along) and LS-BE-015 (the `@Transaction` behind `saveCategoryWithReminder`, including the `nextFireAt` read-and-write-back). 35 specs, was 33. This LLD's `ReminderEntity` table is now the single copy. `ReminderDao` was left alone — it is documented only here already, so there was nothing to move. Backlog #35.

10. **Five category-write methods became one (2026-08-24).** `saveCategory`, `saveCategoryAndMigrateEvents`, `moveCategory`, `moveCategoryAndMigrateEvents`, and `saveCategoryWithReminder` were one transaction with three optional steps — reindex, migrate, reminder — so five of the eight combinations had names and the rest would have been written the day someone needed them. Now one `saveCategory(category, reminder, migrateEvents, orderedSiblingIds)`. Two findings fell out of reading them side by side: the `fromType: ValueType` parameter on all three migrating variants was **never read** — every one passed `category.resolvedValueType` to `migrateEventsForCategory`, so it was a Boolean flag wearing a type's clothes, and DM-PROC-021 wants nothing from the source type either; and `reminder = null` meaning *delete the row* was **unreachable in production** — `toReminder()` returns non-null and the two edit-screen call sites are its only callers, so the only thing exercising deletion was one fake-repository test. Null now means *leave the stored reminder untouched*, which is what 8 of the 10 call sites want, and `ReminderDao.deleteByCategoryId` is gone: a reminder is switched off with `enabled = false`, and only deleting the category removes a row (LS-BE-072). LS-BE-015 was widened to cover all three optional steps and now records the one ordering constraint that is not free — the category upsert must precede the reminder upsert, because `ReminderEntity`'s FK needs the row to exist. The interface method carries the codebase's first KDoc, since a nullable parameter whose null means "don't touch" is not guessable from a call site.

11. **`times` became `NOT NULL` (2026-08-25, schema version 6).** It was the last reminder column declared nullable that nothing ever wrote null to. `toEntity` encoded an empty times list as null, but nothing produces an empty list — `ReminderUIState.fromStored` refills one and the screen's remove-icon is gated on `times.size > 1` — and `"[]"` decodes to exactly what the null decoded to, so the branch chose between two encodings that already meant the same thing. Every other nullable column in the database means something a writer intends: `categories.parentId` (top-level), `categories.emoji`/`color`/`valueType` (inherit from parent), `categories.defaultValue` and `events.value`/`notes` (absent), `reminders.nextFireAt` (not armed). LS-BE-073 states the rule for the whole reminders row rather than leaving each column to be decided one at a time, and gives the migrations a spec about column shape to cite instead of REM-DATA-002's preservation-across-mode-switch.

    It shipped as two migrations — `MIGRATION_3_6` creating the table in the version-6 shape, `MIGRATION_5_6` rebuilding it with `COALESCE(times, '[]')` for the one device sitting at 5 — and collapsed to `MIGRATION_3_6` alone the same day, once that device was confirmed at 6 by pulling its database and reading `PRAGMA user_version`. `MIGRATION_3_5` and `MIGRATION_5_6` and their `MigrationTest` cases went with it, the same collapse `MIGRATION_3_4`/`MIGRATION_4_5` got (finding 7). The device held 9 reminder rows and none had a null `times`, so the `COALESCE` rewrote nothing — the tightening was pure schema on the only data it ever ran against. `5.json` outlives them: a schema version that shipped to a real device has to reach `master`'s history before it can be deleted from it, so it goes in the first commit after the merge, the same reason `4.json` is still here. 36 specs, was 35.

## Work Required

### Must Fix
1. **Blocked on the merge.** Delete `app/schemas/.../5.json` in the first commit after this branch reaches `master` — never on the branch. `4.json` is outstanding for the same reason (backlog #13). See finding 11.

### Should Fix
1. Add test-file `@spec` citations for the specs listed in finding 4.
2. **Cross-segment**: `data-model.md § Same-timestamp ordering` (DM-PROC-010) says `createdAt` ascending; this segment's implemented-and-tested behavior is `createdAt` descending. One of the two docs is wrong — needs `data-model`'s owner to reconcile (not this segment's call to make unilaterally).

### Nice to Have
_None noted this pass._
