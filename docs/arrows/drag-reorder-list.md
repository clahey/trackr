# Arrow: drag-reorder-list

Generic, domain-agnostic Compose widget for drag-to-reorder over a two-level tree; consumed by category-management's category list.

## Status

**AUDITED** — first overlay pass, 2026-07-27. All 18 active specs confirmed implemented against `DragReorderList.kt` and its wiring in `CategoryListScreen.kt`/`CategoryListViewModel.kt`. 2 specs are genuinely deferred (not secretly implemented, not abandoned — both have explicit LLD justification and no code trace). 0 gaps. No reverse orphans.

## References

### HLD
- docs/high-level-design.md (System Design)

### LLD
- docs/llds/drag-reorder-list.md

### EARS
- docs/specs/drag-reorder-list.md (20 specs: DRAG-UI-*; 18 implemented, 2 deferred)

### Tests
- app/src/test/java/net/clahey/trackr/ui/components/DragReorderListLogicTest.kt
- app/src/androidTest/java/net/clahey/trackr/ui/components/DragReorderListTest.kt

### Code
- app/src/main/java/net/clahey/trackr/ui/components/DragReorderList.kt
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListScreen.kt (consumer)
- app/src/main/java/net/clahey/trackr/ui/category/CategoryListViewModel.kt (consumer)

## Architecture

**Purpose:** A generic, domain-agnostic Compose widget (`DragReorderList`) implementing drag-to-reorder over a two-level tree (`DragListItem`). It owns pickup (dedicated drag handle), live reflow with haptic feedback, drop-zone geometry (2/3-band splits depending on target eligibility/children), collapse-on-pickup, auto-scroll, and a settle/freeze protocol (`onMove`/`onSettled`) so the caller can persist asynchronously. It knows nothing about `Category` — the category-specific adapter (mapping, persistence, value-type gating) lives in `category-management` (CAT-UI-002/080/081/082), consciously kept out of this widget per the LLD's decoupling rationale.

**Key Components:**
1. `DragReorderList` — the widget itself: gesture interception, live reflow, drop-zone geometry, auto-scroll, settle protocol
2. `DragListItem` / `DragMoveResult` — the generic data contract the caller adapts its domain model to
3. `CategoryListScreen`/`CategoryListViewModel` — the sole current consumer, adapting `Category` into `DragListItem`

## Spec Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| Pickup | DRAG-UI-001, 016 | 2 | 0 | 0 |
| Live Reflow & Visual Feedback | DRAG-UI-002-005, 015, 019, 020 | 7 | 1 (DRAG-UI-018) | 0 |
| Drop Zones | DRAG-UI-006-010 | 5 | 0 | 0 |
| Collapse on Pickup | DRAG-UI-011, 012 | 2 | 1 (DRAG-UI-017) | 0 |
| Gesture Exclusivity | DRAG-UI-013 | 1 | 0 | 0 |
| Settling | DRAG-UI-014 | 1 | 0 | 0 |

**Summary:** 18 of 20 specs implemented; 2 legitimately deferred; 0 gaps.

## Key Findings

1. **All major behavioral claims sanity-checked directly against code**: pickup/slop (`gestureInterceptor` awaitFirstDown on the Initial pass), auto-scroll (`LaunchedEffect` scrollBy loop, `updateDropTarget` re-invoked per scroll step), drop-zone geometry (`dropZone` — matches the LLD's 2/3-band logic exactly), reflow (`hypotheticalOrder`/`displayOrder`), collapse (`shouldCollapseChildrenOf`, two independent tiers as specced per DRAG-UI-011/012). No drift found between LLD, specs, and code.
2. **Deferred items confirmed legitimate.** Grep for `DRAG-UI-017`/`DRAG-UI-018` in `app/src` returns nothing — no code or comment references either, correctly. The LLD's Open Questions section explains both: DRAG-UI-018 (proportional auto-scroll rate) and DRAG-UI-017 (scroll-position correction after collapse) both await on-device tuning; the shipping baseline (constant-rate auto-scroll, no post-collapse correction) is accepted as-is for now.
3. **No reverse orphans.** Every `@spec DRAG-UI-*` annotation across `DragReorderList.kt`, `DragReorderListLogicTest.kt`, and `DragReorderListTest.kt` cites an ID present in `docs/specs/drag-reorder-list.md`.
4. **One documented-but-unspecced edge case**: a `canHaveChildren = false` node with children (garbage input, since the current adapter never produces this state) has no defined before/after-band behavior. Not a spec gap — no EARS ID claims to cover it, and the input is unreachable given the current adapter.
5. **Dependencies**: `DragReorderList.kt` has zero `data.*`/`domain.*` imports — fully generic, not blocked by data-model or local-storage. It does reference `MaterialTheme.colorScheme.primary`/`primaryContainer` for placeholder styling, a light dependency on theme. `category-management` is the sole consumer and integrator (LLD cross-references both ways via CAT-UI-002/080/081/082).

## Work Required

### Must Fix (before MVP / Play Store testing)
_None._

### Should Fix
_None._

### Nice to Have
1. **DRAG-UI-018** — proportional auto-scroll rate scaling with edge-zone depth, deferred pending on-device feel-tuning.
2. **DRAG-UI-017** — scroll-position correction after a collapse shortens the list, deferred pending on-device tuning of the correction mechanism.
