package net.clahey.trackr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.clahey.trackr.R
import kotlin.math.roundToInt

/**
 * A node in the [DragReorderList] input tree. Each node carries its own ordered [children];
 * a leaf has an empty list. Ids must be unique across the entire tree.
 *
 * @property id unique across the entire tree
 * @property canHaveChildren whether this row can accept a nest-drop (domain policy)
 * @property canBecomeChild whether this node is currently eligible to become a child (domain policy)
 * @property children nested items, in order; empty for a leaf
 */
data class DragListItem(
    val id: String,
    val canHaveChildren: Boolean,
    val canBecomeChild: Boolean,
    val children: List<DragListItem> = emptyList(),
)

// The internal depth-annotated representation the drag math runs over. The public
// [DragListItem] tree is flattened depth-first into a `List<FlatNode>` on input (see
// [flatten]); zone resolution, hypothetical reordering, and sibling-group computation all
// operate on this flat list, and the result is mapped back to tree terms on drop.
internal data class FlatNode(
    val id: String,
    val depth: Int,
    val canHaveChildren: Boolean,
    val canBecomeChild: Boolean,
)

/**
 * Flattens the public [items] tree depth-first — each node immediately followed by its own
 * subtree — into the internal depth-annotated list the rest of the drag math operates on.
 * Top-level nodes are depth 0, their children depth 1, and so on.
 */
internal fun flatten(items: List<DragListItem>): List<FlatNode> {
    val result = mutableListOf<FlatNode>()
    fun visit(item: DragListItem, depth: Int) {
        result.add(FlatNode(item.id, depth, item.canHaveChildren, item.canBecomeChild))
        item.children.forEach { visit(it, depth + 1) }
    }
    items.forEach { visit(it, 0) }
    return result
}

/** Every node in the [items] tree, keyed by id, for resolving a flat row back to its source node. */
private fun nodesById(items: List<DragListItem>): Map<String, DragListItem> {
    val result = mutableMapOf<String, DragListItem>()
    fun visit(item: DragListItem) {
        result[item.id] = item
        item.children.forEach { visit(it) }
    }
    items.forEach { visit(it) }
    return result
}

/**
 * Where a drop at the current pointer position would land relative to the target row:
 * [Before] or [After] as a sibling in the target's own group, [Nest] as the target's child,
 * or [None] when the target offers no valid zone for the current drag.
 */
internal sealed class DropZone {
    data object Before : DropZone()
    data object After : DropZone()
    data object Nest : DropZone()
}

/**
 * The structural move reported to the caller on drop (see [DragReorderList]'s `onMove`).
 *
 * @property movedId the id of the row that was dragged
 * @property newParentId the id of the moved item's new parent, or null if it is now a
 *   top-level item
 * @property orderedSiblingIds the moved item's destination sibling group in final order,
 *   including the moved item
 */
data class DragMoveResult(
    val movedId: String,
    val newParentId: String?,
    val orderedSiblingIds: List<String>,
)

/**
 * The contiguous run of items at [anchorId]'s own depth, bounded by the nearest
 * preceding/following item at a shallower depth. For a depth-0 anchor, that's every
 * depth-0 item in the list (the outermost group).
 */
internal fun siblingGroup(items: List<FlatNode>, anchorId: String): List<FlatNode> {
    val anchorIndex = items.indexOfFirst { it.id == anchorId }
    val depth = items[anchorIndex].depth
    if (depth == 0) return items.filter { it.depth == 0 }
    var start = anchorIndex
    while (start > 0 && items[start - 1].depth >= depth) start--
    var end = anchorIndex
    while (end < items.size - 1 && items[end + 1].depth >= depth) end++
    return items.subList(start, end + 1).filter { it.depth == depth }
}

/** The id of the nearest preceding shallower-depth item, or null if [id] is already depth 0. */
internal fun groupAnchorOf(items: List<FlatNode>, id: String): String? {
    val index = items.indexOfFirst { it.id == id }
    val depth = items[index].depth
    if (depth == 0) return null
    for (i in index - 1 downTo 0) {
        if (items[i].depth < depth) return items[i].id
    }
    return null
}

/** Items immediately nested one level under [parentId] (its current children). */
internal fun childrenOf(items: List<FlatNode>, parentId: String): List<FlatNode> {
    val index = items.indexOfFirst { it.id == parentId }
    val parentDepth = items[index].depth
    val result = mutableListOf<FlatNode>()
    var i = index + 1
    while (i < items.size && items[i].depth > parentDepth) {
        if (items[i].depth == parentDepth + 1) result.add(items[i])
        i++
    }
    return result
}

/**
 * Whether the item at [index] currently has children, derived structurally from the
 * flattened list rather than any caller-supplied field — the item immediately after it
 * (excluding [excludeId], the row conceptually lifted out for an active drag) has a
 * greater depth. Deliberately independent of `canBecomeChild`: see
 * docs/llds/drag-reorder-list.md § Drop Zone Geometry.
 */
internal fun hasChildrenStructurally(
    items: List<FlatNode>,
    index: Int,
    excludeId: String?,
): Boolean {
    val depth = items[index].depth
    var i = index + 1
    while (i < items.size && items[i].id == excludeId) i++
    return i < items.size && items[i].depth > depth
}

/**
 * Resolves which [DropZone] the pointer is in over a target row, from the row's vertical band
 * geometry, or null when the row offers no valid zone for this drag (an ineligible drag over a
 * target that can't have children). The band layout depends on the target's role: a target that
 * can't have children is a 50/50 before/after split; a nest-eligible target is 25/50/25 when
 * childless and 50/50 (before / nest-as-first-child) when it already has children; an ineligible
 * drag (`draggedCanBecomeChild = false`) never offers a nest band. See
 * `docs/llds/drag-reorder-list.md` § Drop Zone Geometry.
 *
 * @param targetCanHaveChildren whether the target row can accept a nest-drop
 * @param targetHasChildren whether the target currently has children (read structurally)
 * @param draggedCanBecomeChild whether the dragged row is currently eligible to nest
 * @param pointerFraction the pointer's vertical position within the target row, 0f..1f
 */
// @spec DRAG-UI-006, DRAG-UI-007, DRAG-UI-008, DRAG-UI-009
internal fun dropZone(
    targetCanHaveChildren: Boolean,
    targetHasChildren: Boolean,
    draggedCanBecomeChild: Boolean,
    pointerFraction: Float,
): DropZone? {
    if (!draggedCanBecomeChild) {
        if (!targetCanHaveChildren) return null
        return if (pointerFraction < 0.5f) DropZone.Before else DropZone.After
    }
    if (!targetCanHaveChildren) {
        return if (pointerFraction < 0.5f) DropZone.Before else DropZone.After
    }
    return if (!targetHasChildren) {
        when {
            pointerFraction < 0.25f -> DropZone.Before
            pointerFraction < 0.75f -> DropZone.Nest
            else -> DropZone.After
        }
    } else {
        if (pointerFraction < 0.5f) DropZone.Before else DropZone.Nest
    }
}

// @spec DRAG-UI-006, DRAG-UI-007, DRAG-UI-008
internal fun computeMoveResult(
    items: List<FlatNode>,
    draggedId: String,
    target: DropTarget,
): DragMoveResult? {
    val (targetId, zone) = target
    if (targetId == draggedId) return null
    return when (zone) {
        DropZone.Nest -> {
            val children = childrenOf(items, targetId).filter { it.id != draggedId }.map { it.id }
            DragMoveResult(draggedId, targetId, listOf(draggedId) + children)
        }

        DropZone.Before, DropZone.After -> {
            val siblingIds =
                siblingGroup(items, targetId).filter { it.id != draggedId }.map { it.id }
            val targetPos = siblingIds.indexOf(targetId)
            val insertPos = if (zone == DropZone.Before) targetPos else targetPos + 1
            val ordered = siblingIds.toMutableList().apply { add(insertPos, draggedId) }
            DragMoveResult(draggedId, groupAnchorOf(items, targetId), ordered)
        }
    }
}

private fun countDescendants(items: List<FlatNode>, index: Int): Int {
    val depth = items[index].depth
    var count = 0
    var i = index + 1
    while (i < items.size && items[i].depth > depth) {
        count++
        i++
    }
    return count
}

/**
 * The full flattened list as it would read if dropped at [target] right now — drives the
 * live-reflow animation. Returns [items] unchanged for an invalid drop.
 */
// @spec DRAG-UI-002
internal fun hypotheticalOrder(
    items: List<FlatNode>,
    draggedId: String,
    target: DropTarget,
): List<FlatNode> {
    val (targetId, zone) = target
    if (targetId == draggedId) return items
    val draggedIndex = items.indexOfFirst { it.id == draggedId }
    val draggedItem = items[draggedIndex]
    val subtreeEnd = draggedIndex + 1 + countDescendants(items, draggedIndex)
    val draggedSubtree = items.subList(draggedIndex, subtreeEnd)
    val remaining = items.toMutableList().apply { subList(draggedIndex, subtreeEnd).clear() }
    val targetIndex = remaining.indexOfFirst { it.id == targetId }
    val targetDepth = remaining[targetIndex].depth
    val newDepth = if (zone == DropZone.Nest) targetDepth + 1 else targetDepth
    val depthDelta = newDepth - draggedItem.depth
    val movedSubtree = draggedSubtree.map { it.copy(depth = it.depth + depthDelta) }
    // "After T" joins T's sibling group at the position right after T — in a flattened
    // tree-list, that's after T's *entire* subtree, not T's own row. Inserting at
    // targetIndex + 1 (correct for Nest, which prepends as T's first child) would instead
    // wedge the dragged row between T and T's own children whenever T has any.
    val insertionIndex = when (zone) {
        DropZone.Before -> targetIndex
        DropZone.Nest -> targetIndex + 1
        DropZone.After -> targetIndex + 1 + countDescendants(remaining, targetIndex)
    }
    return remaining.toMutableList().apply { addAll(insertionIndex, movedSubtree) }
}

/**
 * Whether [rowId]'s children should currently be collapsed during an active drag of
 * [dragged] — two independent tiers (docs/llds/drag-reorder-list.md § Collapse-on-pickup):
 * the dragged row's own children collapse whenever it has children; every other row's
 * children collapse whenever the dragged row isn't eligible to become a child, regardless
 * of whether the dragged row itself has children.
 */
// @spec DRAG-UI-011, DRAG-UI-012
internal fun shouldCollapseChildrenOf(
    rowId: String,
    dragged: FlatNode,
    draggedHasChildren: Boolean,
): Boolean =
    if (rowId == dragged.id) draggedHasChildren else !dragged.canBecomeChild

/** A visible row's position/size within the list, as reported by `LazyListItemInfo`. */
internal data class VisibleRowGeometry(val index: Int, val offset: Int, val size: Int)

/** A drop position: the target row's id and the zone within it. */
internal data class DropTarget(val id: String, val zone: DropZone)

/**
 * The raw drop position the pointer is over right now, against [order] — the order actually
 * currently rendered (so [visible]'s indices stay valid against it). Returns null when there's
 * nothing to report: nothing hit, the hit row offers no valid zone, or the hit row is the
 * dragged row's own already-relocated placeholder.
 */
private fun resolveHitDropCandidate(
    order: List<FlatNode>,
    draggedId: String,
    pointerYInList: Float,
    visible: List<VisibleRowGeometry>,
    canScrollForward: Boolean,
    viewportHeight: Float,
): DropTarget? {
    // A pointer past the top or bottom edge resolves as though at that edge (DRAG-UI-019).
    val y = pointerYInList.coerceIn(0f, viewportHeight)
    val hit = visible.firstOrNull { y >= it.offset && y < it.offset + it.size }
    if (hit != null) {
        val targetItem = order.getOrNull(hit.index) ?: return null
        if (targetItem.id == draggedId) return null
        val dItem = order.firstOrNull { it.id == draggedId } ?: return null
        val fraction = ((y - hit.offset) / hit.size.toFloat()).coerceIn(0f, 1f)
        val targetHasChildren = hasChildrenStructurally(order, hit.index, excludeId = draggedId)
        val zone = dropZone(
            targetItem.canHaveChildren, targetHasChildren, dItem.canBecomeChild, fraction
        ) ?: return null
        return DropTarget(targetItem.id, zone)
    }
    val lastInfo = visible.lastOrNull()
    if (lastInfo != null && y >= lastInfo.offset + lastInfo.size && !canScrollForward) {
        val lastTopLevel = order.lastOrNull { it.depth == 0 && it.id != draggedId }
        if (lastTopLevel != null) return DropTarget(lastTopLevel.id, DropZone.After)
    }
    return null
}

/**
 * The drop this would be if [draggedId] were dropped back exactly where it already is —
 * its own current group anchor and its own current sibling list, [draggedId] included at
 * its current position. Used as the baseline a candidate is compared against before any
 * real target has been established yet (see [computeDragTarget]): the very first zone
 * resolved after pickup is often "before my own next sibling" or "after my own previous
 * sibling" (touch slop alone is frequently enough to land there), which is not a move at
 * all — it's exactly where the row started.
 */
private fun identityMoveResult(order: List<FlatNode>, draggedId: String): DragMoveResult =
    DragMoveResult(
        draggedId,
        groupAnchorOf(order, draggedId),
        siblingGroup(order, draggedId).map { it.id })

/**
 * Resolves the (target, zone) the drag should report given the row currently under the
 * pointer, against [order] — the order actually currently rendered (so [visible]'s
 * indices stay valid against it).
 *
 * Returns [current] **unchanged** whenever there's no genuinely new
 * *position* to report. That includes the obvious cases (nothing hit; the hit row offers
 * no valid zone — DRAG-UI-003: "the gap simply stays at its last valid position"; the hit
 * row is the dragged row's own already-relocated placeholder, which would otherwise reset
 * the target to null, snap the list back to its pre-reflow layout, put the pointer back
 * over the *previous* target, re-detect it, reflow again, and repeat — a perpetual flip
 * that reads as rapid haptics and a jumping floating row). It also covers a subtler case:
 * two *different* (target, zone) pairs — including the dragged row's own starting
 * position, via [identityMoveResult] — can resolve to the exact same drop position by
 * construction of the unifying before/after rule: "after row T" and "before T's next
 * sibling" both insert at the same index in the same group, and likewise "nest as T's
 * first child" and "before T's current first child". Comparing the raw pair instead of
 * the resulting [computeMoveResult] output would report those as a change — a spurious
 * haptic and a target/zone write for a move that doesn't actually move anything — every
 * time the pointer crosses that exact boundary, including right at pickup before the
 * pointer has moved anywhere meaningful at all.
 */
// @spec DRAG-UI-002, DRAG-UI-003, DRAG-UI-010, DRAG-UI-019
internal fun computeDragTarget(
    order: List<FlatNode>,
    draggedId: String,
    pointerYInList: Float,
    visible: List<VisibleRowGeometry>,
    canScrollForward: Boolean,
    viewportHeight: Float,
    current: DropTarget?,
): DropTarget? {
    val candidate = resolveHitDropCandidate(
        order,
        draggedId,
        pointerYInList,
        visible,
        canScrollForward,
        viewportHeight,
    )
        ?: return current
    if (candidate == current) return current
    val candidateResult = computeMoveResult(order, draggedId, candidate)
        ?: return candidate
    val baselineResult = if (current != null) {
        computeMoveResult(order, draggedId, current)
    } else {
        identityMoveResult(order, draggedId)
    }
    // Same resulting drop position (movedId is draggedId in both, so structural equality
    // on the whole result is equivalent to comparing parent + sibling order) — treat as no
    // change: no haptic, no target write.
    return if (candidateResult == baselineResult) current else candidate
}

/**
 * One in-progress drag — held by [DragReorderState.activeDrag], non-null exactly while a drag is
 * active. [id], [startPosition], and [rowSize] are fixed at pickup; [delta], [target], [pointerY],
 * and [pointerId] change as the drag proceeds and are each their own [mutableStateOf], so a
 * per-frame update invalidates only their own readers rather than every reader of the drag (see
 * docs/llds/drag-reorder-list.md § Generic Widget API "Drag state holder").
 */
@Stable
internal class ActiveDrag(
    val id: String,
    val startPosition: Offset,
    val rowSize: IntSize,
) {
    var delta by mutableStateOf(Offset.Zero)
    var target by mutableStateOf<DropTarget?>(null)
    var pointerY by mutableStateOf(startPosition.y + rowSize.height / 2f)
    var pointerId by mutableStateOf<PointerId?>(null)
}

/**
 * Holds all of [DragReorderList]'s mutable drag state plus its [LazyListState], and owns the
 * drag state *transitions* (pickup, per-move update, target/zone resolution, drop, cancel) —
 * see `docs/llds/drag-reorder-list.md § Generic Widget API "Drag state holder"`. Collected into
 * one container rather than a dozen loose `remember { mutableStateOf(...) }` so the transitions
 * are auditable as a unit and the deferred node-graph refactor (LLD Open Questions § Deferred
 * item 3) has one place to change [settledDisplayOrder]'s element type.
 *
 * The transitions take their volatile inputs — the haptic handle, the caller's `onMove`, the
 * current flattened order — as arguments rather than capturing them, so the holder is correct
 * regardless of which composition created it: the single pickup coroutine (see [DragReorderList])
 * is launched once and never relaunched, and a captured-closure holder would silently bind to
 * first-composition values.
 */
@Stable
internal class DragReorderState(val listState: LazyListState) {
    // The active drag, or null when idle. Grouping the per-drag substate into one nullable value
    // ties its lifetime together — pickup constructs it, drop and cancel null it — so a reset can't
    // clear some fields and leave others stale.
    var activeDrag by mutableStateOf<ActiveDrag?>(null)

    // The root Box's origin in root coordinates. The interceptor spans the full list width (not
    // just the handle column), so it bounds a touch-down against each handle's full x/y extent —
    // a touch on row content at the same y as a handle must not read as "on the handle"
    // (DRAG-UI-001/DRAG-UI-016). Kept as a point, not split coordinates: positionInRoot() hands
    // back an Offset, and the two axes are always written and read together.
    var rootOrigin by mutableStateOf(Offset.Zero)

    // Non-null from the moment of drop until the caller calls the onSettled callback passed
    // alongside that drop's result (DRAG-UI-014) — the order the list had at the moment of drop,
    // kept frozen so the list doesn't snap back to the caller's (still-stale) `items` and then
    // re-animate forward the instant `items` catches up. Also gates new pickups: see the
    // exclusivity overlay and the interceptor in [DragReorderList].
    var settledDisplayOrder by mutableStateOf<List<FlatNode>?>(null)

    // The order the current layout actually reflects — what listState.layoutInfo's indices point
    // into. [resolveTargetAndZone] hit-tests against this, not the live displayOrder, because the
    // pointer coroutine runs ahead of layout: displayOrder can already describe an arrangement
    // that hasn't been laid out yet, so hit-testing it against the still-old layoutInfo indices
    // would resolve the wrong target/zone. [DragReorderList] advances this from a SideEffect, so
    // it trails displayOrder by a frame in step with layout (see the note there).
    var renderedOrder by mutableStateOf<List<FlatNode>>(emptyList())

    // Each visible row's drag-handle icon bounds, so the interceptor can tell whether a
    // touch-down landed on a handle (start a drag) versus elsewhere (fall through to scroll).
    // Each row prunes its own entry on disposal, so the map tracks live rows, not every id seen.
    val handleCoordinates = mutableMapOf<String, LayoutCoordinates>()

    fun pickUp(rowId: String) {
        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rowId } ?: return
        activeDrag = ActiveDrag(
            id = rowId,
            startPosition = Offset(0f, row.offset.toFloat()),
            rowSize = IntSize(0, row.size),
        )
    }

    fun onDragMove(change: PointerInputChange, amount: Offset, haptics: HapticFeedback) {
        change.consume()
        val drag = activeDrag ?: return
        drag.pointerId = change.id
        drag.delta += amount
        drag.pointerY = drag.startPosition.y + drag.delta.y + drag.rowSize.height / 2f
        resolveTargetAndZone(haptics)
    }

    // The picked-up row is read from live state (activeDrag / renderedOrder), never from a
    // closure, so the single interceptor coroutine hosting the call is correct regardless of
    // which row was grabbed. `currentItems` and `onMove` are passed in (rather than captured)
    // for the same reason — see the class KDoc.
    fun endDragAndMaybeMove(
        currentItems: List<FlatNode>,
        haptics: HapticFeedback,
        onMove: (result: DragMoveResult, onSettled: () -> Unit) -> Unit,
    ) {
        val drag = activeDrag
        val finalTarget = drag?.target
        if (finalTarget != null) {
            computeMoveResult(currentItems, drag.id, finalTarget)?.let { result ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                settledDisplayOrder = hypotheticalOrder(currentItems, drag.id, finalTarget)
                onMove(result) { settledDisplayOrder = null }
            }
        }
        reset()
    }

    fun cancelDrag() = reset()

    // Recomputes everything from live state (activeDrag / renderedOrder) rather than closing
    // over outer vals — this is called from the pointerInput coroutine, launched once per row
    // and never relaunched, so every read must go through live state or it stays bound to the
    // row's first-composition closure.
    fun resolveTargetAndZone(haptics: HapticFeedback) {
        val drag = activeDrag ?: return
        val order = renderedOrder
        if (order.none { it.id == drag.id }) return
        val visible = listState.layoutInfo.visibleItemsInfo.map {
            VisibleRowGeometry(it.index, it.offset, it.size)
        }
        val resolved = computeDragTarget(
            order = order,
            draggedId = drag.id,
            pointerYInList = drag.pointerY,
            visible = visible,
            canScrollForward = listState.canScrollForward,
            viewportHeight = listState.layoutInfo.viewportSize.height.toFloat(),
            current = drag.target,
        )
        if (resolved != drag.target) {
            if (resolved != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            drag.target = resolved
        }
    }

    private fun reset() {
        activeDrag = null
    }
}

/** Creates a [DragReorderState] scoped to the calling composition, retaining its scroll position. */
@Composable
private fun rememberDragReorderState(): DragReorderState {
    val listState = rememberLazyListState()
    return remember(listState) { DragReorderState(listState) }
}

/**
 * A drag-to-reorder list over an ordered tree of [DragListItem]s. A row is picked up by its
 * trailing drag handle and dragged to reorder among siblings or re-parent (nest / promote);
 * the rest of the list reflows live to show where a drop would land. Knows nothing about the
 * caller's domain — it reports a structural [DragMoveResult] and lets the caller persist it.
 *
 * See `docs/llds/drag-reorder-list.md` for the interaction model and design rationale.
 *
 * @param items the top-level nodes, in order; each carries its own ordered subtree
 * @param onMove invoked once on drop with the resulting move and an `onSettled` callback the
 *   caller must eventually invoke exactly once, after persisting the move or deciding not to
 *   (see § Settling)
 * @param modifier applied to the widget's root
 * @param content renders one row; invoked lazily per visible row, for nested nodes as well as
 *   top-level ones
 */
// @spec DRAG-UI-001, DRAG-UI-002, DRAG-UI-003, DRAG-UI-004, DRAG-UI-005, DRAG-UI-011, DRAG-UI-012, DRAG-UI-013, DRAG-UI-015
@Composable
fun DragReorderList(
    items: List<DragListItem>,
    onMove: (result: DragMoveResult, onSettled: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (DragListItem) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val state = rememberDragReorderState()
    val listState = state.listState

    // The drag math runs over the internal flattened representation; the public `items` tree
    // is flattened on input (DragListItem -> FlatNode). The interceptor's pointerInput
    // coroutine is launched once (keyed on Unit) and never relaunched, so anything it reads
    // that isn't itself a MutableState (like the flattened `items`) must go through
    // rememberUpdatedState, or the coroutine stays bound to its first-composition closure.
    val currentItems by rememberUpdatedState(flatten(items))
    // The original tree node for each id, so the flat display rows can be rendered back via
    // the caller's `content`, which takes a (tree) DragListItem.
    val sourceNodes = remember(items) { nodesById(items) }

    val activeDrag = state.activeDrag
    val draggedIndex =
        activeDrag?.let { drag -> currentItems.indexOfFirst { it.id == drag.id } } ?: -1
    val draggedItem = if (draggedIndex >= 0) currentItems[draggedIndex] else null
    val draggedHasChildren = if (draggedIndex >= 0) hasChildrenStructurally(
        currentItems,
        draggedIndex,
        excludeId = null
    ) else false

    val displayOrder = when {
        activeDrag?.target != null ->
            hypotheticalOrder(currentItems, activeDrag.id, activeDrag.target!!)

        state.settledDisplayOrder != null -> state.settledDisplayOrder!!
        else -> currentItems
    }

    // Advance the holder's renderedOrder to match this composition's displayOrder — but from a
    // SideEffect, which runs only after the composition commits and its layout is applied (and
    // only for the composition that actually commits, not a superseded one). So renderedOrder
    // doesn't jump forward the instant displayOrder is recomputed; it advances on the frame
    // boundary, staying in step with what listState.layoutInfo reflects. That one-frame trail is
    // the point: the pointer coroutine reading renderedOrder mid-frame sees the order actually
    // laid out, so a move that lands in the dragged row's new, not-yet-laid-out slot has no
    // effect until layout catches up — nothing is rendered there yet to hit. Writing displayOrder
    // in directly (during composition) would instead race ahead of the layout it's hit-tested
    // against, reading as the wrong target/zone, flickering haptics, and a jumping floating row.
    SideEffect { state.renderedOrder = displayOrder }

    // @spec DRAG-UI-004
    // Scoped to the drag: the effect exists only while a drag is active, so it launches on pickup
    // and is disposed on drop/cancel. Keyed on activeDrag (not Unit) so it also relaunches if the
    // dragged object identity ever changes without passing through null.
    if (activeDrag != null) {
        LaunchedEffect(activeDrag) {
            val maxEdgePx = with(density) { 64.dp.toPx() }
            val scrollStepPx = with(density) { 4.dp.toPx() }
            while (true) {
                val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                // Clamp so the top and bottom edge zones can't overlap in a short viewport.
                val edgePx = maxEdgePx.coerceAtMost(viewportHeight / 2f)
                val step = when {
                    activeDrag.pointerY < edgePx && listState.canScrollBackward -> -scrollStepPx
                    activeDrag.pointerY > viewportHeight - edgePx && listState.canScrollForward ->
                        scrollStepPx

                    else -> 0f
                }
                if (step != 0f) {
                    listState.scrollBy(step)
                    // Treat the scroll as a drag event: the finger is held still while rows move
                    // under it, so re-resolve the target against the newly-scrolled content instead
                    // of letting it freeze (DRAG-UI-004).
                    state.resolveTargetAndZone(haptics)
                }
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { state.rootOrigin = it.positionInRoot() },
    ) {
        // @spec DRAG-UI-001, DRAG-UI-015, DRAG-UI-016 — the drag gesture, hosted on the
        // LazyColumn's own modifier rather than on each row's handle. This node is the list
        // itself, never a list item, so the LazyColumn disposing the dragged row as it
        // auto-scrolls off-screen can't tear down this gesture's coroutine — the drag
        // survives sustained auto-scroll (DRAG-UI-015).
        //
        // The modifier passed to LazyColumn wraps its internal scrollable, so this node is
        // *outer* to the scroll: it sees every touch on the Initial pass, before the scroll
        // gesture's Main pass. On a touch that lands on a row's drag handle it consumes on
        // the Initial pass — which preempts the scroll — then picks the row up after slop
        // (DRAG-UI-001; a stationary tap crosses no slop and picks up nothing). A touch that
        // misses every handle is left entirely unconsumed, so it falls through to this same
        // node's internal scroll on the Main pass and scrolls the list normally, fling and
        // all (DRAG-UI-016). No sibling sharing is needed, because the gesture host and the
        // scroll are the same node.
        val gestureInterceptor = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // First look, ahead of the internal scroll (Initial vs Main pass).
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    // Settling (DRAG-UI-014) blocks new pickups; leave the touch for the
                    // exclusivity overlay, which is consuming it anyway.
                    if (state.settledDisplayOrder != null) continue

                    val pointerInRoot = down.position + state.rootOrigin
                    // isAttached skips a handle whose icon has left composition (e.g. a list that
                    // just shrank to one row).
                    val hitHandle = state.handleCoordinates.entries.firstOrNull { (_, coords) ->
                        coords.isAttached && coords.boundsInRoot().contains(pointerInRoot)
                    }
                    // A miss leaves the down unconsumed so the list scrolls it (DRAG-UI-016); a
                    // single-row list draws no handle, so it misses here too (DRAG-UI-001).
                    if (hitHandle == null) continue
                    val rowId = hitHandle.key

                    // On a handle: claim the gesture on the Initial pass so the internal
                    // scroll never starts, and wait for slop before committing to a drag
                    // (a tap on a handle picks up nothing — DRAG-UI-001).
                    down.consume()
                    val touchSlop = viewConfiguration.touchSlop
                    var overSlop = Offset.Zero
                    var startedDrag = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break // released = tap
                        overSlop += change.positionChange()
                        if (overSlop.getDistance() >= touchSlop) {
                            change.consume()
                            startedDrag = true
                            break
                        }
                    }
                    if (!startedDrag) continue

                    state.pickUp(rowId)

                    // Drive the drag on the Initial pass too, so every move is consumed
                    // before the internal scroll can act on it — the drag stays exclusive
                    // for its whole lifetime, including throughout any auto-scroll.
                    var completed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            completed = true
                            break
                        }
                        state.onDragMove(change, change.positionChange(), haptics)
                        change.consume()
                    }
                    if (completed) state.endDragAndMaybeMove(currentItems, haptics, onMove)
                    else state.cancelDrag()
                }
            }
        }

        LazyColumn(state = listState, modifier = gestureInterceptor) {
            items(displayOrder, key = { it.id }) { item ->
                // Prune this row's handle bounds when its slot leaves composition (scrolled
                // out of the lazy window, or removed from the list), so handleCoordinates
                // doesn't retain stale entries for rows that no longer exist. Keyed on the
                // stable row id, outside AnimatedVisibility so a collapse (which is not a
                // disposal) doesn't drop a still-present row's bounds.
                DisposableEffect(item.id) {
                    onDispose { state.handleCoordinates.remove(item.id) }
                }
                val isDraggedRow = item.id == activeDrag?.id
                val parentId = if (item.depth > 0) groupAnchorOf(displayOrder, item.id) else null
                val collapsed = parentId != null && draggedItem != null &&
                        shouldCollapseChildrenOf(parentId, draggedItem, draggedHasChildren)
                // Animated, not a plain value: the row's *position* already glides smoothly
                // via Modifier.animateItem() below, but depth (and so indent) can change in
                // the same hypothetical-order update (e.g. crossing into/out of a nest) —
                // without animating this too, the indent snaps instantly while the position
                // is still gliding, which reads as the row sliding into place and then
                // hopping sideways once it settles.
                val indent by animateDpAsState(
                    targetValue = (16 + item.depth * 24).dp,
                    label = "rowIndent"
                )

                AnimatedVisibility(visible = !collapsed, modifier = Modifier.animateItem()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        // Center the handle on the row: the caller's content can be taller than
                        // the 48dp handle, and without this the handle pins to the row's top
                        // edge instead of sitting mid-height.
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isDraggedRow) {
                            // Real content moved to the floating overlay below; this reserves
                            // the row's own former height so the placeholder reads as a full
                            // row-height box (DRAG-UI-003), not a collapsed empty one. A nest
                            // is communicated by this placeholder's own indentation (one
                            // level deeper than the target) alone — no separate highlight on
                            // the target row itself (DRAG-UI-003).
                            val placeholderHeight =
                                with(density) { (activeDrag?.rowSize?.height ?: 0).toDp() }
                            val placeholderShape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(placeholderHeight)
                                    .padding(start = indent)
                                    // Placed after the depth indent so its reported
                                    // semantics bounds (used by tests) reflect the indented
                                    // region itself, not the full-width box the indent is
                                    // carving it out of.
                                    .testTag("drop_placeholder_${item.id}")
                                    .padding(4.dp)
                                    .clip(placeholderShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        placeholderShape
                                    ),
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f)) { content(sourceNodes.getValue(item.id)) }
                        }
                        // Purely a visual affordance — the drag gesture is hosted on the
                        // LazyColumn interceptor above, not here. Kept mounted (not gated on
                        // !isDraggedRow) even for the dragged row so its onGloballyPositioned
                        // bounds stay current for the interceptor's pickup hit-testing; made
                        // invisible (alpha 0, which doesn't affect layout or hit-testing, only
                        // drawing) while dragged, so it doesn't read as left behind with the
                        // static placeholder — a decorative copy floats with the row instead.
                        if (displayOrder.size > 1) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = stringResource(R.string.cd_drag_handle),
                                modifier = Modifier
                                    .testTag("drag_handle_${item.id}")
                                    .onGloballyPositioned { state.handleCoordinates[item.id] = it }
                                    .size(48.dp)
                                    .alpha(if (isDraggedRow) 0f else 1f)
                                    // Padding comes after the size so it only shrinks what's
                                    // drawn, not the 48dp bounds the interceptor hit-tests —
                                    // the standard Material 24dp icon within a 48dp target.
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }

        // @spec DRAG-UI-013, DRAG-UI-014 — blocks new touches elsewhere (a second finger
        // starting a new drag, or a long-press on another row) without disturbing the
        // pointer already driving this drag: consuming that pointer's own changes here too
        // would starve the interceptor's drag loop of the moves it's tracking. Also covers
        // settling (draggingPointerId is null by then, so every touch — including a new
        // pickup attempt — gets consumed; the interceptor also declines pickups while
        // settledDisplayOrder is non-null).
        if (activeDrag != null || state.settledDisplayOrder != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(1f)
                    .pointerInput(activeDrag, state.settledDisplayOrder != null) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.id != activeDrag?.pointerId) change.consume()
                                }
                            }
                        }
                    },
            )
        }

        // @spec DRAG-UI-002
        if (draggedItem != null && activeDrag != null) {
            val start = activeDrag.startPosition
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .zIndex(2f)
                    .offset {
                        IntOffset(
                            (start.x + activeDrag.delta.x).roundToInt(),
                            (start.y + activeDrag.delta.y).roundToInt()
                        )
                    },
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { content(sourceNodes.getValue(draggedItem.id)) }
                    // Decorative only — the real, interactive handle (which hosts the
                    // active gesture) stays mounted at the placeholder's position, just
                    // invisible there; this copy is what actually reads as "attached to
                    // the thing you're dragging," matching the floating row it's part of.
                    if (displayOrder.size > 1) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = null,
                            // Same 48dp footprint / 24dp drawn icon as the real handle, for
                            // visual consistency between the placeholder and floating rows.
                            modifier = Modifier
                                .size(48.dp)
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
