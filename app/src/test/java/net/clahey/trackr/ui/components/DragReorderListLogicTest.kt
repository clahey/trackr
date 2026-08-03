package net.clahey.trackr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The drag math runs over the widget's internal depth-annotated flat representation
// (FlatNode), which the public DragListItem tree is flattened into on input.
private fun item(id: String, depth: Int, canHaveChildren: Boolean = false, canBecomeChild: Boolean = true) =
    FlatNode(id, depth, canHaveChildren, canBecomeChild)

// A (childless meta) / B (meta with children: C, D) / E (childless meta)
private val fixture = listOf(
    item("A", depth = 0, canHaveChildren = true),
    item("B", depth = 0, canHaveChildren = true, canBecomeChild = false),
    item("C", depth = 1),
    item("D", depth = 1),
    item("E", depth = 0, canHaveChildren = true),
)

class DragReorderListLogicTest {

    // The same A / B(C,D) / E shape as `fixture`, expressed as the public input tree.
    private val tree = listOf(
        DragListItem("A", canHaveChildren = true, canBecomeChild = true),
        DragListItem(
            "B", canHaveChildren = true, canBecomeChild = false,
            children = listOf(
                DragListItem("C", canHaveChildren = false, canBecomeChild = true),
                DragListItem("D", canHaveChildren = false, canBecomeChild = true),
            ),
        ),
        DragListItem("E", canHaveChildren = true, canBecomeChild = true),
    )

    // Representation-only boundary (no behavioral spec): the public tree flattens
    // depth-first — each node immediately followed by its children — into the internal
    // depth-annotated list the rest of the drag math operates on.
    @Test fun `flatten turns the public tree into the internal depth-annotated list`() {
        assertEquals(fixture, flatten(tree))
    }

    // @spec DRAG-UI-006, DRAG-UI-007, DRAG-UI-008
    @Test fun `siblingGroup of a depth-0 item is every depth-0 item`() {
        assertEquals(listOf("A", "B", "E"), siblingGroup(fixture, "A").map { it.id })
    }

    @Test fun `siblingGroup of a depth-1 item is only its own parent's children`() {
        assertEquals(listOf("C", "D"), siblingGroup(fixture, "C").map { it.id })
    }

    @Test fun `groupAnchorOf a depth-0 item is null`() {
        assertNull(groupAnchorOf(fixture, "B"))
    }

    @Test fun `groupAnchorOf a depth-1 item is its parent`() {
        assertEquals("B", groupAnchorOf(fixture, "D"))
    }

    @Test fun `childrenOf returns the current children in order`() {
        assertEquals(listOf("C", "D"), childrenOf(fixture, "B").map { it.id })
    }

    @Test fun `childrenOf a childless item is empty`() {
        assertTrue(childrenOf(fixture, "A").isEmpty())
    }

    // @spec DRAG-UI-007, DRAG-UI-008
    @Test fun `hasChildrenStructurally is true when the next row is deeper`() {
        assertTrue(hasChildrenStructurally(fixture, fixture.indexOfFirst { it.id == "B" }, excludeId = null))
    }

    @Test fun `hasChildrenStructurally is false when the next row is not deeper`() {
        assertFalse(hasChildrenStructurally(fixture, fixture.indexOfFirst { it.id == "A" }, excludeId = null))
    }

    @Test fun `hasChildrenStructurally is false at the end of the list`() {
        assertFalse(hasChildrenStructurally(fixture, fixture.indexOfFirst { it.id == "E" }, excludeId = null))
    }

    @Test fun `hasChildrenStructurally excludes the dragged row from consideration`() {
        // B's only child is C; if C is the row being dragged, B reads as childless.
        val onlyChild = listOf(item("B", depth = 0, canHaveChildren = true), item("C", depth = 1))
        assertFalse(hasChildrenStructurally(onlyChild, 0, excludeId = "C"))
    }

    // @spec DRAG-UI-009
    @Test fun `dropZone offers no zone when dragged is ineligible and target can't have children`() {
        assertNull(
            dropZone(targetCanHaveChildren = false, targetHasChildren = false, draggedCanBecomeChild = false, rowFraction = 0.5f),
        )
    }

    @Test fun `dropZone is before-after only when dragged is ineligible but target can have children`() {
        assertEquals(
            DropZone.Before,
            dropZone(targetCanHaveChildren = true, targetHasChildren = true, draggedCanBecomeChild = false, rowFraction = 0.1f),
        )
        assertEquals(
            DropZone.After,
            dropZone(targetCanHaveChildren = true, targetHasChildren = true, draggedCanBecomeChild = false, rowFraction = 0.9f),
        )
    }

    // @spec DRAG-UI-006
    @Test fun `dropZone on a row that can't have children is a plain 50-50 split`() {
        assertEquals(DropZone.Before, dropZone(false, false, draggedCanBecomeChild = true, rowFraction = 0.49f))
        assertEquals(DropZone.After, dropZone(false, false, draggedCanBecomeChild = true, rowFraction = 0.51f))
    }

    // @spec DRAG-UI-007
    @Test fun `dropZone on a childless eligible target is a 25-50-25 split`() {
        assertEquals(DropZone.Before, dropZone(true, targetHasChildren = false, draggedCanBecomeChild = true, rowFraction = 0.1f))
        assertEquals(DropZone.Nest, dropZone(true, targetHasChildren = false, draggedCanBecomeChild = true, rowFraction = 0.5f))
        assertEquals(DropZone.After, dropZone(true, targetHasChildren = false, draggedCanBecomeChild = true, rowFraction = 0.9f))
    }

    // @spec DRAG-UI-008
    @Test fun `dropZone on an eligible target that already has children is before-or-nest only`() {
        assertEquals(DropZone.Before, dropZone(true, targetHasChildren = true, draggedCanBecomeChild = true, rowFraction = 0.1f))
        assertEquals(DropZone.Nest, dropZone(true, targetHasChildren = true, draggedCanBecomeChild = true, rowFraction = 0.9f))
    }

    // @spec DRAG-UI-008
    @Test fun `computeMoveResult nesting into a target with no children`() {
        val result = computeMoveResult(fixture, draggedId = "C", target = DropTarget("A", DropZone.Nest))
        assertEquals(DragMoveResult("C", "A", listOf("C")), result)
    }

    @Test fun `computeMoveResult nesting into a target with existing children prepends`() {
        val result = computeMoveResult(fixture, draggedId = "A", target = DropTarget("B", DropZone.Nest))
        assertEquals(DragMoveResult("A", "B", listOf("A", "C", "D")), result)
    }

    @Test fun `computeMoveResult before-after on a depth-0 target joins the top-level group`() {
        val result = computeMoveResult(fixture, draggedId = "C", target = DropTarget("A", DropZone.Before))
        assertEquals(DragMoveResult("C", null, listOf("C", "A", "B", "E")), result)
    }

    @Test fun `computeMoveResult before-after on a depth-1 target joins that target's parent group`() {
        val result = computeMoveResult(fixture, draggedId = "E", target = DropTarget("D", DropZone.After))
        assertEquals(DragMoveResult("E", "B", listOf("C", "D", "E")), result)
    }

    @Test fun `computeMoveResult is null when the target is the dragged row itself`() {
        assertNull(computeMoveResult(fixture, "A", DropTarget("A", DropZone.Before)))
    }

    // @spec DRAG-UI-002
    @Test fun `hypotheticalOrder reorders and updates depth for before-after`() {
        val order = hypotheticalOrder(fixture, draggedId = "C", target = DropTarget("A", DropZone.Before))
        assertEquals(listOf("C", "A", "B", "D", "E"), order.map { it.id })
        assertEquals(0, order.first { it.id == "C" }.depth)
    }

    @Test fun `hypotheticalOrder updates depth for a nest`() {
        val order = hypotheticalOrder(fixture, draggedId = "A", target = DropTarget("E", DropZone.Nest))
        assertEquals(1, order.first { it.id == "A" }.depth)
    }

    @Test fun `hypotheticalOrder moves a dragged row's descendants along with it`() {
        val order = hypotheticalOrder(fixture, draggedId = "B", target = DropTarget("A", DropZone.After))
        assertEquals(listOf("A", "B", "C", "D", "E"), order.map { it.id })
        assertEquals(1, order.first { it.id == "C" }.depth)
    }

    @Test fun `hypotheticalOrder is unchanged for an invalid drop`() {
        assertEquals(fixture, hypotheticalOrder(fixture, "A", DropTarget("A", DropZone.Before)))
    }

    // @spec DRAG-UI-011, DRAG-UI-012
    @Test fun `shouldCollapseChildrenOf the dragged row itself follows tier 1`() {
        // rowId == dragged.id: collapse iff the dragged row has children (canBecomeChild irrelevant).
        val childless = DragListItem("B", canHaveChildren = true, canBecomeChild = false)
        val withChild = childless.copy(children = listOf(DragListItem("C", false, true)))
        assertTrue(shouldCollapseChildrenOf("B", withChild))
        assertFalse(shouldCollapseChildrenOf("B", childless))
    }

    @Test fun `shouldCollapseChildrenOf another row follows tier 2 regardless of tier 1`() {
        // Ineligible dragged (canBecomeChild = false): every other row collapses, regardless of children.
        val ineligible = DragListItem("B", canHaveChildren = true, canBecomeChild = false)
        assertTrue(shouldCollapseChildrenOf("A", ineligible.copy(children = listOf(DragListItem("C", false, true)))))
        assertTrue(shouldCollapseChildrenOf("A", ineligible))

        // Eligible dragged (canBecomeChild = true): other rows don't collapse.
        val eligible = DragListItem("A", canHaveChildren = true, canBecomeChild = true)
        assertFalse(shouldCollapseChildrenOf("B", eligible))
    }

    // computeDragTarget — every fixture row laid out at a uniform 100px height, in order:
    // A=[0,100) B=[100,200) C=[200,300) D=[300,400) E=[400,500).
    private val standardGeometry = fixture.mapIndexed { index, _ -> VisibleRowGeometry(index, index * 100, 100) }

    // @spec DRAG-UI-002
    @Test fun `computeDragTarget resolves a genuinely new valid zone`() {
        // Dragging C (eligible), hovering A (childless, eligible target) dead center -> Nest.
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 50f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = null,
        )
        assertEquals(DropTarget("A", DropZone.Nest), result)
    }

    // @spec DRAG-UI-002
    @Test fun `computeDragTarget preserves the current target when hovering the dragged row's own relocated placeholder`() {
        // C has been reflowed to sit right after A (e.g. nested under it); the pointer is
        // now over that relocated placeholder itself, not over a different row.
        val order = listOf(
            item("A", depth = 0, canHaveChildren = true),
            item("C", depth = 1),
            item("B", depth = 0, canHaveChildren = true, canBecomeChild = false),
            item("D", depth = 1),
            item("E", depth = 0, canHaveChildren = true),
        )
        val result = computeDragTarget(
            order = order, draggedId = "C", y = 150f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = DropTarget("A", DropZone.Nest),
        )
        assertEquals(DropTarget("A", DropZone.Nest), result)
    }

    // @spec DRAG-UI-003
    @Test fun `computeDragTarget preserves the current target when hovering a row with no valid zone`() {
        // Dragging B (ineligible to become a child); hovering C, which can't have children,
        // offers no zone at all for an ineligible dragged row (dropZone returns None).
        val result = computeDragTarget(
            order = fixture, draggedId = "B", y = 250f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = DropTarget("A", DropZone.Before),
        )
        assertEquals(DropTarget("A", DropZone.Before), result)
    }

    // @spec DRAG-UI-019
    @Test fun `computeDragTarget clamps a pointer above the top edge to the top row`() {
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = -50f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = DropTarget("E", DropZone.After),
        )
        assertEquals(DropTarget("A", DropZone.Before), result)
    }

    // @spec DRAG-UI-019
    @Test fun `computeDragTarget clamps a pointer below the bottom edge to the bottom row`() {
        // Viewport ends at 490, so the last row (E, 400..500) is partially below the fold and
        // more can still scroll in; a pointer far below resolves against E as though at the edge.
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 5000f, visible = standardGeometry,
            canScrollForward = true, viewportHeight = 490f, current = null,
        )
        assertEquals(DropTarget("E", DropZone.After), result)
    }

    // @spec DRAG-UI-010
    @Test fun `computeDragTarget falls back to end-of-list when past the last row and unable to scroll`() {
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 600f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = null,
        )
        assertEquals(DropTarget("E", DropZone.After), result)
    }

    @Test fun `computeDragTarget does not fall back to end-of-list while more content can still scroll into view`() {
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 600f, visible = standardGeometry,
            canScrollForward = true, viewportHeight = 500f, current = DropTarget("A", DropZone.Before),
        )
        assertEquals(DropTarget("A", DropZone.Before), result)
    }

    // @spec DRAG-UI-002
    @Test fun `computeDragTarget treats after-T and before-T's-next-sibling as the same position`() {
        // Dragging C; current state is "after A". The pointer is now over B's top half
        // ("before B") — B is A's next top-level sibling, so this resolves to the exact
        // same insertion index as "after A". Must report unchanged: no haptic, no write.
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 110f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = DropTarget("A", DropZone.After),
        )
        assertEquals(DropTarget("A", DropZone.After), result)
    }

    // @spec DRAG-UI-002
    @Test fun `computeDragTarget treats nest-as-first-child and before-the-current-first-child as the same position`() {
        // Dragging E; current state is "before C" (C is B's current first child). The
        // pointer is now over B's bottom half ("nest as B's first child") — same result.
        val result = computeDragTarget(
            order = fixture, draggedId = "E", y = 190f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = DropTarget("C", DropZone.Before),
        )
        assertEquals(DropTarget("C", DropZone.Before), result)
    }

    // @spec DRAG-UI-002
    @Test fun `computeDragTarget still updates for a genuinely different position`() {
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 450f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = DropTarget("A", DropZone.Before),
        )
        assertEquals(DropTarget("E", DropZone.Nest), result)
    }

    // @spec DRAG-UI-002
    @Test fun `computeDragTarget does not register a target when the first resolved zone is the dragged row's own current position`() {
        // Dragging C; "before D" (D is C's own next sibling) describes exactly where C
        // already is, not a move — touch slop alone is often enough to land here right at
        // pickup, and it must not register a target (no haptic, no reflow) for it.
        val result = computeDragTarget(
            order = fixture, draggedId = "C", y = 310f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = null,
        )
        assertEquals(null, result)
    }

    @Test fun `computeDragTarget does not register a target for the symmetric after-my-previous-sibling case`() {
        // Dragging D; "after C" (C is D's own previous sibling) is also exactly where D
        // already is.
        val result = computeDragTarget(
            order = fixture, draggedId = "D", y = 290f, visible = standardGeometry,
            canScrollForward = false, viewportHeight = 500f, current = null,
        )
        assertEquals(null, result)
    }
}
