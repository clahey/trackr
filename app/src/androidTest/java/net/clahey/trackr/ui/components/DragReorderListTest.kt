package net.clahey.trackr.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

private class RecordingHapticFeedback : HapticFeedback {
    val events = mutableListOf<HapticFeedbackType>()
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        events.add(hapticFeedbackType)
    }
}

// --- Reusable drag drivers -------------------------------------------------------------
//
// Every drag here is driven as many small already-past-slop moveBy() steps (waitForIdle
// between each) rather than one batched moveTo() jump. A single large moveTo() delivers
// non-monotonic sub-events whose *net* on-screen travel varies with row height and density,
// so a batched jump lands inconsistently across screen geometries — it undershot the empty
// area below the list on a 7-inch tablet AVD, so drops there never registered. Small steps
// make the net travel deterministic and, as a bonus, force a real recomposition between
// move events (closer to how a device actually delivers a drag), which is what the pickup
// regression guards below rely on.

private val DRAG_STEP = 8.dp

private fun ComposeContentTestRule.pressHandle(handleTag: String) {
    onNodeWithTag(handleTag).performTouchInput { down(center) }
}

private fun ComposeContentTestRule.releaseHandle(handleTag: String) {
    onNodeWithTag(handleTag).performTouchInput { up() }
    waitForIdle()
}

/**
 * Moves the active drag pointer on [handleTag] by [totalDy] px in [DRAG_STEP]-sized steps,
 * letting the tree settle between each (and, cumulatively, crossing touch slop to pick the
 * row up). [afterEachStep] runs after every settled step — used by the haptic-count checks.
 */
private fun ComposeContentTestRule.dragBy(
    handleTag: String,
    totalDy: Float,
    afterEachStep: () -> Unit = {},
) {
    val stepPx = with(density) { DRAG_STEP.toPx() }
    val steps = (abs(totalDy) / stepPx).roundToInt().coerceAtLeast(1)
    val dir = if (totalDy < 0f) -stepPx else stepPx
    repeat(steps) {
        onNodeWithTag(handleTag).performTouchInput { moveBy(Offset(0f, dir)) }
        waitForIdle()
        afterEachStep()
    }
}

/** Presses [handleTag] and crosses touch slop to pick the row up, holding the pointer down. */
private fun ComposeContentTestRule.pickUpAndHold(handleTag: String) {
    val slopPx = with(density) { 24.dp.toPx() }
    pressHandle(handleTag)
    dragBy(handleTag, slopPx)
}

/** Full press -> step -> release drag that lands the pointer at [targetYInRoot]. */
private fun ComposeContentTestRule.dragRowToY(handleTag: String, targetYInRoot: Float) {
    val startY = onNodeWithTag(handleTag).fetchSemanticsNode().boundsInRoot.center.y
    pressHandle(handleTag)
    dragBy(handleTag, targetYInRoot - startY)
    releaseHandle(handleTag)
}

/**
 * Full drag that drops [handleTag]'s row into the empty space past [lastRowTag] (end of
 * list). Aims several row-heights below the last row, not just one: the widget tracks a
 * point offset below the finger, and a nestable last row will capture a pointer that merely
 * reaches its nest band — so the drop has to land unambiguously past the row's bottom. The
 * stepped driver reaches this far target deterministically (a single moveTo() would not).
 */
private fun ComposeContentTestRule.dropRowPastEnd(handleTag: String, lastRowTag: String) {
    val last = onNodeWithTag(lastRowTag).fetchSemanticsNode().boundsInRoot
    dragRowToY(handleTag, last.bottom + last.height * 5)
}

@RunWith(AndroidJUnit4::class)
class DragReorderListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun rowItem(id: String, children: List<DragListItem> = emptyList()) =
        DragListItem(id, canHaveChildren = true, canBecomeChild = true, children = children)

    private fun flatItem(id: String) =
        DragListItem(id, canHaveChildren = false, canBecomeChild = true)

    // A group row that can hold children but is itself ineligible to become one — the shape
    // that triggers the "collapse every other row's children" rule (DRAG-UI-012).
    private fun ineligibleRow(id: String, children: List<DragListItem> = emptyList()) =
        DragListItem(id, canHaveChildren = true, canBecomeChild = false, children = children)

    // @spec DRAG-UI-001
    @Test
    fun handleHiddenWhenOnlyOneRowExists() {
        composeTestRule.setContent {
            DragReorderList(items = listOf(rowItem("A")), onMove = { _, onSettled -> onSettled() }) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        composeTestRule.onNodeWithTag("drag_handle_A").assertDoesNotExist()
    }

    // @spec DRAG-UI-001
    @Test
    fun handleShownWhenMoreThanOneRowExists() {
        composeTestRule.setContent {
            DragReorderList(items = listOf(rowItem("A"), rowItem("B")), onMove = { _, onSettled -> onSettled() }) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        composeTestRule.onNodeWithTag("drag_handle_A").assertExists()
        composeTestRule.onNodeWithTag("drag_handle_B").assertExists()
    }

    // @spec DRAG-UI-010
    // Aims into the empty space past the last row rather than at a specific band; because the
    // drag is stepped (see the drag drivers), it also forces a recomposition mid-drag — the
    // condition that once silently killed the gesture (see handleStaysMounted... below).
    @Test
    fun draggingHandlePastTheEndOfTheListReportsAMove() {
        var result: DragMoveResult? = null
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(rowItem("A"), rowItem("B")),
                onMove = { r, onSettled -> result = r; onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        composeTestRule.dropRowPastEnd(handleTag = "drag_handle_A", lastRowTag = "row_B")

        assertEquals(DragMoveResult("A", null, listOf("B", "A")), result)
    }

    // @spec DRAG-UI-002
    // Regression test: picking up a row once gated its drag handle's own composition on
    // `!isDraggedRow`, which removed the handle (and the pointerInput coroutine tracking
    // this very gesture) from the tree the moment pickup recomposed — silently killing the
    // drag after its first event. Stepping the pickup forces the pickup-triggered
    // recomposition to actually run mid-gesture, which is what exposes it.
    @Test
    fun handleStaysMountedForTheDraggedRowAcrossARecomposition() {
        composeTestRule.setContent {
            DragReorderList(items = listOf(rowItem("A"), rowItem("B")), onMove = { _, onSettled -> onSettled() }) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        composeTestRule.pickUpAndHold("drag_handle_A")
        composeTestRule.onNodeWithTag("drag_handle_A").assertExists()
        composeTestRule.releaseHandle("drag_handle_A")
    }

    // @spec DRAG-UI-003
    // Regression test: the placeholder's tint was once applied to the full-width Row
    // hosting it rather than to the indented placeholder Box itself, so the indent had no
    // visible effect — the "drop area" always read as flush to the left edge regardless of
    // the dragged row's depth.
    @Test
    fun placeholderIsIndentedToMatchTheDraggedRowsDepth() {
        // S1 is sandwiched among several depth-1 siblings under A, and every row is given
        // generous padding so it's much taller than touch slop — that way, the small
        // slop-crossing pickup reliably lands within S1's own (now tall) row, registering no
        // target (own-placeholder no-op) and leaving S1's depth at its untouched original
        // value; even if it did overshoot onto a neighbor, every plausible landing spot is
        // still a depth-1 sibling, so the *resulting* depth is robustly 1 either way.
        val subChildren = (1..8).map { flatItem("S$it") }
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(rowItem("A", children = subChildren)),
                onMove = { _, onSettled -> onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}").padding(vertical = 48.dp))
            }
        }
        // Pick up S1 without releasing, so the placeholder is actually rendered before we
        // read its bounds.
        composeTestRule.pickUpAndHold("drag_handle_S1")

        val placeholderBounds = composeTestRule.onNodeWithTag("drop_placeholder_S1").fetchSemanticsNode().boundsInRoot
        val expectedIndentPx = with(composeTestRule.density) { 40.dp.toPx() }
        assertEquals(expectedIndentPx, placeholderBounds.left, 2f)

        composeTestRule.releaseHandle("drag_handle_S1")
    }

    // @spec DRAG-UI-002, DRAG-UI-003
    // Regression test: once live reflow relocated the dragged row's placeholder to sit
    // under the pointer, hovering that same spot again was treated as "no valid zone" and
    // cleared the target, snapping the list back to its pre-reflow layout — which put the
    // pointer back over the *original* target, re-detecting it, reflowing again, and
    // repeating. This drags A past B (settling on "after B"), then moves to where A's
    // placeholder has just relocated to (C's former position) — under the bug, that second
    // move clears the target and the drop is lost; fixed, hovering the placeholder is a
    // no-op and the drop still lands where it was left.
    @Test
    fun draggingOntoTheRelocatedPlaceholderDoesNotClearTheTarget() {
        var result: DragMoveResult? = null
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(flatItem("A"), flatItem("B"), flatItem("C")),
                onMove = { r, onSettled -> result = r; onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        val aBounds = composeTestRule.onNodeWithTag("row_A").fetchSemanticsNode().boundsInRoot
        val bBounds = composeTestRule.onNodeWithTag("row_B").fetchSemanticsNode().boundsInRoot
        val cBounds = composeTestRule.onNodeWithTag("row_C").fetchSemanticsNode().boundsInRoot

        // Drag A down into B's lower half ("after B"); A's placeholder reflows into C's slot.
        composeTestRule.pressHandle("drag_handle_A")
        composeTestRule.dragBy("drag_handle_A", (bBounds.bottom - 5f) - aBounds.center.y)
        // Now move onto that relocated placeholder itself (C's former position), not a
        // different row — under the bug this cleared the target and lost the drop.
        composeTestRule.dragBy("drag_handle_A", cBounds.center.y - (bBounds.bottom - 5f))
        composeTestRule.releaseHandle("drag_handle_A")

        assertEquals(DragMoveResult("A", null, listOf("B", "A", "C")), result)
    }

    // @spec DRAG-UI-002
    // Regression test: "after row T" and "before T's next sibling" (and, right at pickup,
    // "before my own next sibling", which is just my own starting position) are different
    // (target, zone) pairs that resolve to the exact same insertion position. Walking A
    // down through B and into C's top portion should cross exactly one *real* boundary —
    // "still basically where I started" to "now after B" — and tick exactly once for it,
    // not once per (target, zone) pair it technically passes through along the way. The
    // stepped driver is essential here: the tick count is asserted after every step.
    @Test
    fun noSpuriousHapticWhenCrossingFromAfterOneRowToBeforeItsNextSibling() {
        val haptics = RecordingHapticFeedback()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                DragReorderList(
                    items = listOf(flatItem("A"), flatItem("B"), flatItem("C")),
                    onMove = { _, onSettled -> onSettled() },
                ) { item ->
                    Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
                }
            }
        }
        val aBounds = composeTestRule.onNodeWithTag("row_A").fetchSemanticsNode().boundsInRoot
        val cBounds = composeTestRule.onNodeWithTag("row_C").fetchSemanticsNode().boundsInRoot

        composeTestRule.pressHandle("drag_handle_A")
        composeTestRule.dragBy("drag_handle_A", cBounds.center.y - aBounds.center.y) {
            assertTrue("expected at most 1 tick, saw ${haptics.events.size}", haptics.events.size <= 1)
        }
        assertEquals(1, haptics.events.size)
        composeTestRule.releaseHandle("drag_handle_A")
    }

    // @spec DRAG-UI-004, DRAG-UI-015
    // Exercises auto-scroll (DRAG-UI-004) as the precondition for the survival guarantee
    // (DRAG-UI-015): holding a drag near the bottom edge long enough for sustained
    // auto-scroll to carry the dragged row's original position well off (or, per the
    // user's report, back onto) screen must not interrupt the gesture — it should still be
    // possible to keep adjusting the drop position and complete the move afterward. Fails
    // under the per-row gesture host (the disposed row cancels the gesture coroutine
    // mid-drag); the persistent overlay strip — the default gesture host — fixes it.
    // mainClock.autoAdvance is turned off so advanceTimeBy() deterministically drives the
    // auto-scroll LaunchedEffect's delay(16) loop through many iterations without relying
    // on real wall-clock time.
    @Test
    fun draggingNearTheBottomEdgeSurvivesSustainedAutoScroll() {
        var result: DragMoveResult? = null
        val manyItems = (1..30).map { flatItem("Item$it") }
        composeTestRule.setContent {
            DragReorderList(
                items = manyItems,
                onMove = { r, onSettled -> result = r; onSettled() },
                modifier = Modifier.testTag("drag_list"),
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        val listBounds = composeTestRule.onNodeWithTag("drag_list").fetchSemanticsNode().boundsInRoot
        val item1CenterY = composeTestRule.onNodeWithTag("drag_handle_Item1").fetchSemanticsNode().boundsInRoot.center.y
        val nearBottomY = listBounds.bottom - with(composeTestRule.density) { 20.dp.toPx() }

        composeTestRule.pressHandle("drag_handle_Item1")
        composeTestRule.dragBy("drag_handle_Item1", nearBottomY - item1CenterY)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(3000)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        // Nudge again and release — if the gesture survived the sustained auto-scroll, this
        // still reports a move; if it was silently interrupted, onMove never fires.
        composeTestRule.dragBy("drag_handle_Item1", with(composeTestRule.density) { DRAG_STEP.toPx() })
        composeTestRule.releaseHandle("drag_handle_Item1")

        assertNotNull("gesture appears to have been interrupted by auto-scroll", result)
    }

    // @spec DRAG-UI-014
    // After a drop, the widget must wait for the caller's onSettled callback before
    // allowing another drag to start — otherwise a second drag could start (and even
    // complete) while the first one's persistence is still in flight.
    @Test
    fun blocksANewDragUntilTheCallerCallsOnSettled() {
        var moveCount = 0
        var pendingOnSettled: (() -> Unit)? = null
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(flatItem("A"), flatItem("B"), flatItem("C")),
                onMove = { _, onSettled -> moveCount++; pendingOnSettled = onSettled },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        // Drop A at the end — onMove fires, but onSettled is deliberately not called yet,
        // simulating a caller still persisting the move (e.g. an in-flight database write).
        composeTestRule.dropRowPastEnd("drag_handle_A", "row_C")
        assertEquals(1, moveCount)

        // While frozen, trying to start a second drag must not succeed.
        composeTestRule.dropRowPastEnd("drag_handle_B", "row_C")
        assertEquals(1, moveCount)

        // The caller finishes (persists or cancels, doesn't matter which) and calls onSettled.
        pendingOnSettled?.invoke()
        composeTestRule.waitForIdle()

        // Now a new drag works again.
        composeTestRule.dropRowPastEnd("drag_handle_B", "row_C")
        assertEquals(2, moveCount)
    }

    // @spec DRAG-UI-007
    // End-to-end counterpart to DragReorderListLogicTest's nesting cases: proves the full
    // gesture path (pointer -> onDragMove -> reported result) carries a nest drop through to
    // a non-null newParentId, not merely that computeMoveResult can produce one. Which nest
    // band applies (the 25/50/25 split for a childless target here, vs. the 50/50 split for
    // a target that already has children, DRAG-UI-008) is exhaustively unit-tested; this
    // covers only the wiring.
    @Test
    fun nestingARowUnderAnotherReportsTheNewParent() {
        var result: DragMoveResult? = null
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(rowItem("A"), rowItem("B")),
                onMove = { r, onSettled -> result = r; onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}").padding(vertical = 48.dp))
            }
        }
        // Drag B's handle up onto A's vertical center — the middle (nest) band of a childless
        // eligible target. The generous row padding makes that band tall enough to land in
        // reliably despite per-step jitter.
        val aCenterY = composeTestRule.onNodeWithTag("row_A").fetchSemanticsNode().boundsInRoot.center.y
        composeTestRule.dragRowToY("drag_handle_B", aCenterY)

        assertEquals(DragMoveResult("B", "A", listOf("B")), result)
    }

    // @spec DRAG-UI-011
    // Picking up a row that has children collapses its own children for the duration of the
    // drag and restores them on drop. The band math (shouldCollapseChildrenOf) is
    // unit-tested; this checks that the collapse actually removes the child rows from the
    // rendered list (AnimatedVisibility drops them from composition once the exit settles).
    @Test
    fun pickingUpAParentCollapsesItsOwnChildren() {
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(
                    rowItem("A", children = listOf(flatItem("S1"), flatItem("S2"))),
                    rowItem("B"),
                ),
                onMove = { _, onSettled -> onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        composeTestRule.onNodeWithTag("row_S1").assertExists()

        composeTestRule.pickUpAndHold("drag_handle_A")
        composeTestRule.onNodeWithTag("row_S1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("row_S2").assertDoesNotExist()

        // Dropping restores them.
        composeTestRule.releaseHandle("drag_handle_A")
        composeTestRule.onNodeWithTag("row_S1").assertExists()
    }

    // @spec DRAG-UI-012
    // Picking up a row that is ineligible to become a child (canBecomeChild = false)
    // collapses *every other* row's children, independently of whether the dragged row has
    // children of its own. Here the dragged row B has none, and a sibling group A/S1 is the
    // one whose child must disappear on pickup.
    @Test
    fun pickingUpAnIneligibleRowCollapsesEveryOtherRowsChildren() {
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(
                    ineligibleRow("B"),
                    rowItem("A", children = listOf(flatItem("S1"))),
                ),
                onMove = { _, onSettled -> onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        composeTestRule.onNodeWithTag("row_S1").assertExists()

        composeTestRule.pickUpAndHold("drag_handle_B")
        composeTestRule.onNodeWithTag("row_S1").assertDoesNotExist()

        composeTestRule.releaseHandle("drag_handle_B")
        composeTestRule.onNodeWithTag("row_S1").assertExists()
    }

    // @spec DRAG-UI-014
    // The untested half of blocksANewDragUntilTheCallerCallsOnSettled: after a drop whose
    // caller settles *without* persisting (items never change), the widget must stop
    // rendering the dropped order and revert to `items` — the dropped row animates back to
    // where it started rather than staying where it was dropped.
    @Test
    fun anUnpersistedDropAnimatesTheRowBackToItsOriginalPosition() {
        var pendingOnSettled: (() -> Unit)? = null
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(flatItem("A"), flatItem("B"), flatItem("C")),
                onMove = { _, onSettled -> pendingOnSettled = onSettled },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        fun topOf(id: String) =
            composeTestRule.onNodeWithTag("row_$id").fetchSemanticsNode().boundsInRoot.top

        // Drop A past the end. The caller captures onSettled but neither calls it nor updates
        // items — modeling a persist still in flight (or a decision not to persist).
        composeTestRule.dropRowPastEnd("drag_handle_A", "row_C")
        // Until onSettled, the widget keeps rendering the dropped order: A is now last.
        assertTrue("expected A to render below C before settle", topOf("A") > topOf("C"))

        // Caller settles without persisting; items still read A, B, C, so the widget reverts.
        pendingOnSettled?.invoke()
        composeTestRule.waitForIdle()
        assertTrue("expected A to animate back above B after settle", topOf("A") < topOf("B"))
    }

    // @spec DRAG-UI-020
    // Regression test: the dragged row's placeholder is a keyed LazyColumn item, so when the
    // dragged row is the list's scroll anchor (the first visible row) and the live reflow
    // relocates its placeholder, LazyColumn's key-based scroll preservation follows the
    // placeholder key and yanks the viewport — cascading the list toward the far end. Item1 is
    // dragged from the top (list scrolled to the top, so it is the first visible row) down past
    // Item2. Fixed: the viewport stays put, Item2 slides into the top slot, and the far-down
    // rows stay off-screen. Buggy: the viewport scrolls away, Item2 leaves the screen and
    // bottom rows appear.
    @Test
    fun reflowKeepsTheViewportPutWhenTheDraggedRowIsTheScrollAnchor() {
        val manyItems = (1..40).map { flatItem("Item$it") }
        composeTestRule.setContent {
            DragReorderList(
                items = manyItems,
                onMove = { _, onSettled -> onSettled() },
                modifier = Modifier.testTag("drag_list").height(400.dp),
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}").padding(vertical = 12.dp))
            }
        }
        val listTop = composeTestRule.onNodeWithTag("drag_list").fetchSemanticsNode().boundsInRoot.top
        val rowHeight = composeTestRule.onNodeWithTag("row_Item1").fetchSemanticsNode().boundsInRoot.height

        // Item1 is the first visible row. Drag it down past Item2 so the reflow relocates its
        // placeholder below Item2 — which makes Item2 the first item regardless of exactly where
        // Item1 lands.
        composeTestRule.pressHandle("drag_handle_Item1")
        composeTestRule.dragBy("drag_handle_Item1", rowHeight * 2.5f)

        // The viewport must not have scrolled to follow the placeholder: Item2 is at the top and
        // the far-down rows are still off-screen.
        composeTestRule.onNodeWithTag("row_Item2").assertExists()
        val row2Top = composeTestRule.onNodeWithTag("row_Item2").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "expected Item2 pinned near the viewport top ($listTop) but it was at $row2Top",
            abs(row2Top - listTop) < rowHeight,
        )
        composeTestRule.onNodeWithTag("row_Item40").assertDoesNotExist()

        composeTestRule.releaseHandle("drag_handle_Item1")
    }

    // @spec DRAG-UI-020
    // The mirror of the anchor cascade: dragging a row *up* to become the first item moves its
    // placeholder into the first slot. Without the pin, LazyColumn keeps the old first row pinned
    // and tucks the prepended placeholder above the viewport, so the drop preview disappears off
    // the top (the "hiccup" scrolling up). Fixed: the placeholder renders in the top slot.
    @Test
    fun reflowKeepsThePreviewVisibleWhenTheDraggedRowBecomesTheFirstItem() {
        val manyItems = (1..40).map { flatItem("Item$it") }
        composeTestRule.setContent {
            DragReorderList(
                items = manyItems,
                onMove = { _, onSettled -> onSettled() },
                modifier = Modifier.testTag("drag_list").height(400.dp),
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}").padding(vertical = 12.dp))
            }
        }
        val listTop = composeTestRule.onNodeWithTag("drag_list").fetchSemanticsNode().boundsInRoot.top
        val rowHeight = composeTestRule.onNodeWithTag("row_Item1").fetchSemanticsNode().boundsInRoot.height

        // Pick up Item3 and drag it up past the top (clamped to the top edge, DRAG-UI-019) so its
        // placeholder lands before Item1 — the first slot.
        composeTestRule.pressHandle("drag_handle_Item3")
        composeTestRule.dragBy("drag_handle_Item3", -rowHeight * 4f)

        // The preview must render in the top slot, not be hidden above the viewport.
        composeTestRule.onNodeWithTag("drop_placeholder_Item3").assertExists()
        val placeholderTop =
            composeTestRule.onNodeWithTag("drop_placeholder_Item3").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "expected Item3's placeholder in the top slot (near $listTop) but it was at $placeholderTop",
            placeholderTop >= listTop - rowHeight * 0.5f && placeholderTop < listTop + rowHeight,
        )

        composeTestRule.releaseHandle("drag_handle_Item3")
    }
}
