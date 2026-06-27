package net.clahey.trackr.ui.components

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

private class RecordingHapticFeedback : HapticFeedback {
    val events = mutableListOf<HapticFeedbackType>()
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        events.add(hapticFeedbackType)
    }
}

@RunWith(AndroidJUnit4::class)
class DragReorderListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun rowItem(id: String, depth: Int = 0) =
        DragListItem(id, depth, canHaveChildren = true, canBecomeChild = true)

    private fun flatItem(id: String) =
        DragListItem(id, depth = 0, canHaveChildren = false, canBecomeChild = true)

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
        // Aim well past the bottom of the list rather than into a specific drop band — the
        // exact on-screen distance Compose UI testing reports for a single moveTo() doesn't
        // line up 1:1 with the gesture's intended travel (touch slop + delivery behavior),
        // so a large overshoot is the robust way to reliably exercise the end-of-list path.
        val targetBounds = composeTestRule.onNodeWithTag("row_B").fetchSemanticsNode().boundsInRoot
        val pastTheEnd = Offset(
            targetBounds.center.x,
            targetBounds.bottom + targetBounds.height * 5,
        )
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            down(center)
            moveTo(pastTheEnd)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(DragMoveResult("A", null, listOf("B", "A")), result)
    }

    // @spec DRAG-UI-002
    // Regression test: picking up a row once gated its drag handle's own composition on
    // `!isDraggedRow`, which removed the handle (and the pointerInput coroutine tracking
    // this very gesture) from the tree the moment pickup recomposed — silently killing the
    // drag after its first event. A real device delivers move events as separate frames
    // with a recomposition in between, which is what exposed it; a single batched
    // down/moveTo/up (as below) does not.
    @Test
    fun handleStaysMountedForTheDraggedRowAcrossARecomposition() {
        composeTestRule.setContent {
            DragReorderList(items = listOf(rowItem("A"), rowItem("B")), onMove = { _, onSettled -> onSettled() }) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        // Cross touch slop to trigger pickup without releasing, then force the
        // pickup-triggered recomposition to actually run before checking anything.
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drag_handle_A").assertExists()
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput { up() }
    }

    // @spec DRAG-UI-002, DRAG-UI-010
    // End-to-end counterpart to the test above: splits the gesture across two
    // performTouchInput calls with a real recomposition forced in between, modeling how a
    // real device actually delivers touch input (vs. draggingHandlePastTheEndOfTheListReportsAMove's
    // single batched gesture, which completes before Compose gets a chance to recompose
    // mid-drag and so didn't catch this).
    @Test
    fun draggingAcrossARecompositionBetweenMoveEventsStillReportsTheMove() {
        var result: DragMoveResult? = null
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(rowItem("A"), rowItem("B")),
                onMove = { r, onSettled -> result = r; onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}"))
            }
        }
        val targetBounds = composeTestRule.onNodeWithTag("row_B").fetchSemanticsNode().boundsInRoot
        val pastTheEnd = Offset(
            targetBounds.center.x,
            targetBounds.bottom + targetBounds.height * 5,
        )
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            moveTo(pastTheEnd)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(DragMoveResult("A", null, listOf("B", "A")), result)
    }

    // @spec DRAG-UI-003
    // Regression test: the placeholder's tint was once applied to the full-width Row
    // hosting it rather than to the indented placeholder Box itself, so the indent had no
    // visible effect — the "drop area" always read as flush to the left edge regardless of
    // the dragged row's depth.
    @Test
    fun placeholderIsIndentedToMatchTheDraggedRowsDepth() {
        // S1 is sandwiched among several depth-1 siblings under A, and every row is given
        // generous padding so it's much taller than touch slop — that way, a small
        // slop-crossing move reliably lands within S1's own (now tall) row, registering no
        // target (own-placeholder no-op) and leaving S1's depth at its untouched original
        // value; even if it did overshoot onto a neighbor, every plausible landing spot is
        // still a depth-1 sibling, so the *resulting* depth is robustly 1 either way.
        val depth1Siblings = (1..8).map { DragListItem("S$it", depth = 1, canHaveChildren = false, canBecomeChild = true) }
        composeTestRule.setContent {
            DragReorderList(
                items = listOf(rowItem("A", depth = 0)) + depth1Siblings,
                onMove = { _, onSettled -> onSettled() },
            ) { item ->
                Text(item.id, modifier = Modifier.testTag("row_${item.id}").padding(vertical = 48.dp))
            }
        }
        // Pick up S1 without releasing, forcing the pickup recomposition to settle so the
        // placeholder is actually rendered before checking its bounds.
        val slopCrossingPx = with(composeTestRule.density) { 24.dp.toPx() }
        composeTestRule.onNodeWithTag("drag_handle_S1").performTouchInput {
            down(center)
            moveBy(Offset(0f, slopCrossingPx))
        }
        composeTestRule.waitForIdle()

        val placeholderBounds = composeTestRule.onNodeWithTag("drop_placeholder_S1").fetchSemanticsNode().boundsInRoot
        val expectedIndentPx = with(composeTestRule.density) { 40.dp.toPx() }
        assertEquals(expectedIndentPx, placeholderBounds.left, 2f)

        composeTestRule.onNodeWithTag("drag_handle_S1").performTouchInput { up() }
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
        val bBounds = composeTestRule.onNodeWithTag("row_B").fetchSemanticsNode().boundsInRoot
        val cBounds = composeTestRule.onNodeWithTag("row_C").fetchSemanticsNode().boundsInRoot

        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            down(center)
            moveTo(Offset(bBounds.center.x, bBounds.bottom - 5f))
        }
        composeTestRule.waitForIdle()
        // A's placeholder has now reflowed into C's former slot. Move there again — this is
        // "hovering the new location of the drop area" itself, not a different row.
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            moveTo(cBounds.center)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput { up() }
        composeTestRule.waitForIdle()

        assertEquals(DragMoveResult("A", null, listOf("B", "A", "C")), result)
    }

    // @spec DRAG-UI-002
    // Regression test: "after row T" and "before T's next sibling" (and, right at pickup,
    // "before my own next sibling", which is just my own starting position) are different
    // (target, zone) pairs that resolve to the exact same insertion position. Walking A
    // down through B and into C's top portion should cross exactly one *real* boundary —
    // "still basically where I started" to "now after B" — and tick exactly once for it,
    // not once per (target, zone) pair it technically passes through along the way.
    //
    // Driven via many small relative moveBy() steps rather than one big moveTo() jump: a
    // single moveTo() call was observed to deliver several internal sub-events whose
    // positions don't progress smoothly toward the requested target (confirmed via
    // diagnostic logging — one such call landed at roughly 99, then 110, then jumped
    // straight to 194 on a 96px-tall row, overshooting the intended target by ~50px), so
    // landing at a specific fraction of a specific row in one call isn't reliable. Many
    // small already-past-slop steps are.
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

        val stepPx = with(composeTestRule.density) { 8.dp.toPx() }
        val totalSteps = ((cBounds.center.y - aBounds.center.y) / stepPx).toInt().coerceAtLeast(1)

        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput { down(center) }
        repeat(totalSteps) {
            composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput { moveBy(Offset(0f, stepPx)) }
            composeTestRule.waitForIdle()
            assertTrue("expected at most 1 tick, saw ${haptics.events.size}", haptics.events.size <= 1)
        }
        assertEquals(1, haptics.events.size)

        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput { up() }
    }

    // @spec DRAG-UI-004
    // Regression test: holding a drag near the bottom edge long enough for sustained
    // auto-scroll to carry the dragged row's original position well off (or, per the
    // user's report, back onto) screen must not interrupt the gesture — it should still be
    // possible to keep adjusting the drop position and complete the move afterward.
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
        val nearBottomY = listBounds.bottom - with(composeTestRule.density) { 20.dp.toPx() }

        composeTestRule.onNodeWithTag("drag_handle_Item1").performTouchInput {
            down(center)
            moveTo(Offset(listBounds.center.x, nearBottomY))
        }
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(3000)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        // Nudge again and release — if the gesture survived the sustained auto-scroll,
        // this still reports a move; if it was silently interrupted, onMove never fires.
        composeTestRule.onNodeWithTag("drag_handle_Item1").performTouchInput {
            moveBy(Offset(0f, 5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drag_handle_Item1").performTouchInput { up() }
        composeTestRule.waitForIdle()

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
        val cBounds = composeTestRule.onNodeWithTag("row_C").fetchSemanticsNode().boundsInRoot
        val pastTheEnd = Offset(cBounds.center.x, cBounds.bottom + cBounds.height * 5)

        // Drop A at the end — onMove fires, but onSettled is deliberately not called yet,
        // simulating a caller still persisting the move (e.g. an in-flight database write).
        composeTestRule.onNodeWithTag("drag_handle_A").performTouchInput {
            down(center)
            moveTo(pastTheEnd)
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, moveCount)

        // While frozen, trying to start a second drag must not succeed.
        composeTestRule.onNodeWithTag("drag_handle_B").performTouchInput {
            down(center)
            moveBy(Offset(0f, 500f))
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, moveCount)

        // The caller finishes (persists or cancels, doesn't matter which) and calls onSettled.
        pendingOnSettled?.invoke()
        composeTestRule.waitForIdle()

        // Now a new drag works again.
        composeTestRule.onNodeWithTag("drag_handle_B").performTouchInput {
            down(center)
            moveBy(Offset(0f, 500f))
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(2, moveCount)
    }
}
