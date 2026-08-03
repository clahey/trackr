# Arrow: app-shell

App-level scaffolding: DI modules, application/activity entry points, and top-level navigation host.

## Status

**AUDITED** — last audited 2026-07-27 (re-verified; no code or spec changes since the 2026-06-17 pass — `git diff` over this segment's paths since then is empty). All 17 specs implemented; only finding is a traceability gap.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/app-shell.md

### EARS
- docs/specs/app-shell.md (17 specs: APP-DI-*, APP-ID-*, APP-NAV-*, APP-PROC-*, APP-UI-*)

### Tests
_No dedicated test files found citing APP-* spec IDs this pass._

### Code
- app/src/main/java/net/clahey/trackr/di/DatabaseModule.kt
- app/src/main/java/net/clahey/trackr/di/DataStoreModule.kt
- app/src/main/java/net/clahey/trackr/di/RepositoryModule.kt
- app/src/main/java/net/clahey/trackr/MainActivity.kt
- app/src/main/java/net/clahey/trackr/TrackrApplication.kt
- app/src/main/java/net/clahey/trackr/ui/navigation/AppNavHost.kt
- app/src/main/java/net/clahey/trackr/ui/home/EventEditViewModel.kt

## Architecture

**Purpose:** App entry point, Hilt DI wiring, and the top-level `NavHost` connecting the category and event-logging screens.

**Key Components:**
1. Hilt modules (`DatabaseModule`, `DataStoreModule`, `RepositoryModule`)
2. `TrackrApplication` — process entry, kicks off `repository.onStartup()` (see `local-storage` LS-BE-041 finding)
3. `AppNavHost` — navigation graph

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| DI | APP-DI-* | all | 0 | 0 |
| Identity | APP-ID-001 to 003 | all | 0 | 0 |
| Navigation/Process/UI | APP-NAV-*, APP-PROC-*, APP-UI-* | all | 0 | 0 |

**Summary:** 17 of 17 active specs implemented; 0 deferred, 0 active gaps.

## Key Findings

1. **APP-ID-001, APP-ID-002, APP-ID-003 are implemented with no `@spec` annotation** anywhere in the codebase — traceability gap only, not a functional one.
2. No dedicated test files were found citing any `APP-*` spec ID. Worth confirming whether app-shell behavior is covered indirectly (e.g., via Compose navigation tests elsewhere) or genuinely untested.

## Work Required

### Must Fix
_None — fully implemented._

### Should Fix
1. Add `@spec APP-ID-001, APP-ID-002, APP-ID-003` annotations at their implementation site.
2. Confirm whether app-shell/navigation has any test coverage at all; if not, this is the segment most likely to regress silently.

### Nice to Have
_None noted this pass._
