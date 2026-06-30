package net.clahey.trackr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
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

// @spec DRAG-UI-006, DRAG-UI-007, DRAG-UI-008, DRAG-UI-009, DRAG-UI-010, DRAG-UI-011, DRAG-UI-012
//
// The widget's public input is a tree: each node carries its own ordered [children], and a
// leaf is simply an empty [children] list. There is deliberately no `depth` field — depth is
// implied by the nesting and the widget supports arbitrary nesting depth. Any per-app cap
// (e.g. the category screen's two-level limit) is the caller's concern, enforced by how it
// builds the tree, not by this type; and a malformed "jump" (a depth-0 row followed by a
// depth-2 row, skipping a level) is simply unrepresentable here. ids must be unique across
// the entire tree. See docs/llds/drag-reorder-list.md § Generic Widget API.
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

sealed class DropZone {
    data object Before : DropZone()
    data object After : DropZone()
    data object Nest : DropZone()
    data object None : DropZone()
}

data class DragMoveResult(
    val movedId: String,
    // The moved item's immediate enclosing node in the resulting order (its nearest preceding
    // shallower node), or null when it lands at the top level.
    val newParentId: String?,
    // The moved item's destination sibling group in final order, including the moved item.
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
internal fun hasChildrenStructurally(items: List<FlatNode>, index: Int, excludeId: String?): Boolean {
    val depth = items[index].depth
    var i = index + 1
    while (i < items.size && items[i].id == excludeId) i++
    return i < items.size && items[i].depth > depth
}

// @spec DRAG-UI-006, DRAG-UI-007, DRAG-UI-008, DRAG-UI-009
fun dropZone(
    targetCanHaveChildren: Boolean,
    targetHasChildren: Boolean,
    draggedCanBecomeChild: Boolean,
    pointerFraction: Float,
): DropZone {
    if (!draggedCanBecomeChild) {
        if (!targetCanHaveChildren) return DropZone.None
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

// @spec DRAG-UI-006, DRAG-UI-007, DRAG-UI-008, DRAG-UI-010
internal fun computeMoveResult(
    items: List<FlatNode>,
    draggedId: String,
    targetId: String,
    zone: DropZone,
): DragMoveResult? {
    if (zone == DropZone.None || targetId == draggedId) return null
    return when (zone) {
        DropZone.Nest -> {
            val children = childrenOf(items, targetId).filter { it.id != draggedId }.map { it.id }
            DragMoveResult(draggedId, targetId, listOf(draggedId) + children)
        }
        DropZone.Before, DropZone.After -> {
            val siblingIds = siblingGroup(items, targetId).filter { it.id != draggedId }.map { it.id }
            val targetPos = siblingIds.indexOf(targetId)
            val insertPos = if (zone == DropZone.Before) targetPos else targetPos + 1
            val ordered = siblingIds.toMutableList().apply { add(insertPos, draggedId) }
            DragMoveResult(draggedId, groupAnchorOf(items, targetId), ordered)
        }
        DropZone.None -> null
    }
}

// @spec DRAG-UI-010
internal fun endOfListMoveResult(items: List<FlatNode>, draggedId: String): DragMoveResult {
    val topLevel = items.filter { it.depth == 0 && it.id != draggedId }.map { it.id }
    return DragMoveResult(draggedId, null, topLevel + draggedId)
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
 * The full flattened list as it would read if dropped at [targetId]/[zone] right now —
 * drives the live-reflow animation. Returns [items] unchanged for an invalid drop.
 */
// @spec DRAG-UI-002
internal fun hypotheticalOrder(
    items: List<FlatNode>,
    draggedId: String,
    targetId: String,
    zone: DropZone,
): List<FlatNode> {
    if (zone == DropZone.None || targetId == draggedId) return items
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
        DropZone.None -> targetIndex + 1 // unreachable — zone == None returned early above
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
internal fun shouldCollapseChildrenOf(rowId: String, dragged: FlatNode, draggedHasChildren: Boolean): Boolean =
    if (rowId == dragged.id) draggedHasChildren else !dragged.canBecomeChild

/** A visible row's position/size within the list, as reported by `LazyListItemInfo`. */
data class VisibleRowGeometry(val index: Int, val offset: Int, val size: Int)

/**
 * The raw (target, zone) the pointer is over right now, against [order] — the order
 * actually currently rendered (so [visible]'s indices stay valid against it). Returns
 * null when there's nothing to report: nothing hit, the hit row offers no valid zone, or
 * the hit row is the dragged row's own already-relocated placeholder.
 */
private fun resolveHitDropCandidate(
    order: List<FlatNode>,
    draggedId: String,
    pointerYInList: Float,
    visible: List<VisibleRowGeometry>,
    canScrollForward: Boolean,
): Pair<String, DropZone>? {
    val hit = visible.firstOrNull { pointerYInList >= it.offset && pointerYInList < it.offset + it.size }
    if (hit != null) {
        val targetItem = order.getOrNull(hit.index) ?: return null
        if (targetItem.id == draggedId) return null
        val dItem = order.firstOrNull { it.id == draggedId } ?: return null
        val fraction = ((pointerYInList - hit.offset) / hit.size.toFloat()).coerceIn(0f, 1f)
        val targetHasChildren = hasChildrenStructurally(order, hit.index, excludeId = draggedId)
        val zone = dropZone(targetItem.canHaveChildren, targetHasChildren, dItem.canBecomeChild, fraction)
        return if (zone != DropZone.None) targetItem.id to zone else null
    }
    val lastInfo = visible.lastOrNull()
    if (lastInfo != null && pointerYInList >= lastInfo.offset + lastInfo.size && !canScrollForward) {
        val lastTopLevel = order.lastOrNull { it.depth == 0 && it.id != draggedId }
        if (lastTopLevel != null) return lastTopLevel.id to DropZone.After
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
    DragMoveResult(draggedId, groupAnchorOf(order, draggedId), siblingGroup(order, draggedId).map { it.id })

/**
 * Resolves the (target, zone) the drag should report given the row currently under the
 * pointer, against [order] — the order actually currently rendered (so [visible]'s
 * indices stay valid against it).
 *
 * Returns [currentTargetId]/[currentZone] **unchanged** whenever there's no genuinely new
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
// @spec DRAG-UI-002, DRAG-UI-003
internal fun computeDragTarget(
    order: List<FlatNode>,
    draggedId: String,
    pointerYInList: Float,
    visible: List<VisibleRowGeometry>,
    canScrollForward: Boolean,
    currentTargetId: String?,
    currentZone: DropZone,
): Pair<String?, DropZone> {
    val unchanged = currentTargetId to currentZone
    val (candidateId, candidateZone) = resolveHitDropCandidate(order, draggedId, pointerYInList, visible, canScrollForward)
        ?: return unchanged
    if (candidateId == currentTargetId && candidateZone == currentZone) return unchanged
    val candidateResult = computeMoveResult(order, draggedId, candidateId, candidateZone)
        ?: return candidateId to candidateZone
    val baselineResult = if (currentTargetId != null) {
        computeMoveResult(order, draggedId, currentTargetId, currentZone)
    } else {
        identityMoveResult(order, draggedId)
    }
    val sameDrop = baselineResult != null &&
        candidateResult.newParentId == baselineResult.newParentId &&
        candidateResult.orderedSiblingIds == baselineResult.orderedSiblingIds
    return if (sameDrop) unchanged else candidateId to candidateZone
}

// @spec DRAG-UI-001, DRAG-UI-002, DRAG-UI-003, DRAG-UI-004, DRAG-UI-005, DRAG-UI-011, DRAG-UI-012, DRAG-UI-013, DRAG-UI-015
@Composable
fun DragReorderList(
    items: List<DragListItem>,
    onMove: (result: DragMoveResult, onSettled: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    // TEMPORARY scaffolding (docs/llds/drag-reorder-list.md § Validation scaffolding): selects
    // the drag gesture host. true = a single persistent overlay strip that survives the
    // LazyColumn disposing the dragged row during auto-scroll (DRAG-UI-015); false = the old
    // per-row handle pointerInput. Removed once the strip is confirmed on-device, leaving the
    // strip as the sole host.
    useOverlayStrip: Boolean = true,
    content: @Composable (DragListItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
    var dragRowSize by remember { mutableStateOf(IntSize.Zero) }
    var currentTargetId by remember { mutableStateOf<String?>(null) }
    var currentZone by remember { mutableStateOf<DropZone>(DropZone.None) }
    var pointerYInList by remember { mutableStateOf(0f) }
    var rootTop by remember { mutableStateOf(0f) }
    var draggingPointerId by remember { mutableStateOf<PointerId?>(null) }
    // Non-null from the moment of drop until the caller calls the onSettled callback
    // passed alongside that drop's result (DRAG-UI-014) — the order the list had at the
    // moment of drop, kept frozen so the list doesn't snap back to the caller's
    // (still-stale) `items` and then re-animate forward the instant `items` catches up.
    // Also gates new pickups: see the exclusivity overlay and onDragStart below.
    var settledDisplayOrder by remember { mutableStateOf<List<FlatNode>?>(null) }
    // Every row's last-known position/size, updated continuously (independent of drag
    // state) so pickup can read an already-known value synchronously instead of racing
    // a fresh layout pass against the gesture coroutine.
    val rowCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    // Each visible row's drag-handle icon bounds, so the overlay strip can tell whether a
    // touch-down actually landed on a handle (start a drag) versus elsewhere in the handle
    // column (let it fall through to the list, e.g. to scroll). Only visible rows' handles
    // are looked up — entries for disposed rows are stale and never consulted.
    val handleCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    // The drag math runs over the internal flattened representation; the public `items` tree
    // is flattened on input (DragListItem -> FlatNode). pointerInput(item.id) below launches
    // one coroutine per row that is *not* relaunched on every recomposition (its key never
    // changes) — anything it reads that isn't itself a MutableState (like the flattened
    // `items`) must go through rememberUpdatedState, or the coroutine stays bound to the
    // closure from the row's very first composition.
    val currentItems by rememberUpdatedState(flatten(items))
    // The original tree node for each id, so the flat display rows can be rendered back via
    // the caller's `content`, which takes a (tree) DragListItem.
    val sourceNodes = remember(items) { nodesById(items) }

    val draggedIndex = draggedId?.let { id -> currentItems.indexOfFirst { it.id == id } } ?: -1
    val draggedItem = if (draggedIndex >= 0) currentItems[draggedIndex] else null
    val draggedHasChildren = if (draggedIndex >= 0) hasChildrenStructurally(currentItems, draggedIndex, excludeId = null) else false

    val displayOrder = when {
        draggedId != null && currentTargetId != null ->
            hypotheticalOrder(currentItems, draggedId!!, currentTargetId!!, currentZone)
        settledDisplayOrder != null -> settledDisplayOrder!!
        else -> currentItems
    }

    // The single source of truth for "what's actually on screen right now" — mirrored
    // from displayOrder below, once per settled composition. resolveTargetAndZone reads
    // this instead of recomputing the hypothetical order live from currentTargetId/
    // currentZone: several pointer-move events can fire within one un-recomposed frame,
    // and each one recomputing its own fresh hypothetical order would get hit-tested
    // against listState.layoutInfo indices that still belong to the *previous* frame's
    // layout — a mismatch that reads as the wrong target/zone, flickering haptics, and a
    // floating row that appears to jump. Tying hit-testing to the order that's actually
    // rendered means a pointer move that lands within the dragged row's new, not-yet-
    // laid-out position has no effect until the layout catches up — nothing is rendered
    // there yet to hit.
    var lastRenderedOrder by remember { mutableStateOf(currentItems) }
    SideEffect { lastRenderedOrder = displayOrder }

    // Recomputes everything from live state (draggedId / lastRenderedOrder / currentTargetId
    // / currentZone) rather than closing over outer `val`s like `draggedItem`/`displayOrder` —
    // this function is called from the pointerInput coroutine below, which is launched once
    // per row and never relaunched, so it stays bound to whichever closure it captured on
    // the row's *first* composition unless every read here goes through live state.
    fun resolveTargetAndZone() {
        val dId = draggedId ?: return
        val order = lastRenderedOrder
        if (order.none { it.id == dId }) return
        val visible = listState.layoutInfo.visibleItemsInfo.map {
            VisibleRowGeometry(it.index, it.offset, it.size)
        }
        val (resolvedTarget, resolvedZone) = computeDragTarget(
            order = order,
            draggedId = dId,
            pointerYInList = pointerYInList,
            visible = visible,
            canScrollForward = listState.canScrollForward,
            currentTargetId = currentTargetId,
            currentZone = currentZone,
        )
        android.util.Log.d(
            "DragReorderListDebug",
            "resolveTargetAndZone: order=${order.map { it.id }} visible=$visible -> $resolvedTarget/$resolvedZone",
        )
        if (resolvedTarget != currentTargetId || resolvedZone != currentZone) {
            if (resolvedTarget != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentTargetId = resolvedTarget
            currentZone = resolvedZone
        }
    }

    // The drop/cancel/move-step handlers are identical for both gesture hosts (the per-row
    // handle and the overlay strip) — only pickup differs (which row, and how it's located).
    // None of these read the picked-up row's id from a closure; they all go through live
    // state (draggedId / currentTargetId / currentZone / currentItems), so a single coroutine
    // hosting them is correct regardless of which row was grabbed.
    fun endDragAndMaybeMove() {
        android.util.Log.d("DragReorderListDebug", "onDragEnd (draggedId=$draggedId)")
        val finalDraggedId = draggedId
        val finalTarget = currentTargetId
        val finalZone = currentZone
        if (finalDraggedId != null && finalTarget != null) {
            computeMoveResult(currentItems, finalDraggedId, finalTarget, finalZone)?.let { result ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                settledDisplayOrder = hypotheticalOrder(currentItems, finalDraggedId, finalTarget, finalZone)
                onMove(result) { settledDisplayOrder = null }
            }
        }
        draggedId = null
        currentTargetId = null
        currentZone = DropZone.None
        dragDelta = Offset.Zero
        draggingPointerId = null
    }

    fun cancelDrag() {
        android.util.Log.d("DragReorderListDebug", "onDragCancel (draggedId=$draggedId)")
        draggedId = null
        currentTargetId = null
        currentZone = DropZone.None
        dragDelta = Offset.Zero
        draggingPointerId = null
    }

    fun onDragMove(change: PointerInputChange, amount: Offset) {
        change.consume()
        draggingPointerId = change.id
        dragDelta += amount
        dragStartPosition?.let { start ->
            pointerYInList = start.y + dragDelta.y + dragRowSize.height / 2f
        }
        android.util.Log.d(
            "DragReorderListDebug",
            "onDrag: changeId=${change.id} amount=$amount dragDelta=$dragDelta " +
                "pointerYInList=$pointerYInList currentTarget=$currentTargetId/$currentZone " +
                "canScrollForward=${listState.canScrollForward}",
        )
        resolveTargetAndZone()
    }

    // @spec DRAG-UI-004
    LaunchedEffect(draggedId) {
        val edgePx = with(density) { 64.dp.toPx() }
        while (isActive && draggedId != null) {
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            when {
                pointerYInList < edgePx && listState.canScrollBackward -> listState.scrollBy(-10f)
                pointerYInList > viewportHeight - edgePx && listState.canScrollForward -> listState.scrollBy(10f)
            }
            delay(16)
        }
    }

    Box(modifier = modifier.onGloballyPositioned { rootTop = it.positionInRoot().y }) {
        LazyColumn(state = listState) {
            items(displayOrder, key = { it.id }) { item ->
                val isDraggedRow = item.id == draggedId
                val parentId = if (item.depth > 0) groupAnchorOf(displayOrder, item.id) else null
                val collapsed = draggedId != null && parentId != null && draggedItem != null &&
                    shouldCollapseChildrenOf(parentId, draggedItem, draggedHasChildren)
                // Animated, not a plain value: the row's *position* already glides smoothly
                // via Modifier.animateItem() below, but depth (and so indent) can change in
                // the same hypothetical-order update (e.g. crossing into/out of a nest) —
                // without animating this too, the indent snaps instantly while the position
                // is still gliding, which reads as the row sliding into place and then
                // hopping sideways once it settles.
                val indent by animateDpAsState(targetValue = (16 + item.depth * 24).dp, label = "rowIndent")

                DisposableEffect(item.id) {
                    android.util.Log.d("DragReorderListDebug", "row composed: ${item.id} (isDraggedRow=$isDraggedRow)")
                    onDispose {
                        android.util.Log.d("DragReorderListDebug", "row disposed: ${item.id}")
                    }
                }

                AnimatedVisibility(visible = !collapsed, modifier = Modifier.animateItem()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords -> rowCoordinates[item.id] = coords },
                    ) {
                        if (isDraggedRow) {
                            // Real content moved to the floating overlay below; this reserves
                            // the row's own former height so the placeholder reads as a full
                            // row-height box (DRAG-UI-003), not a collapsed empty one. A nest
                            // is communicated by this placeholder's own indentation (one
                            // level deeper than the target) alone — no separate highlight on
                            // the target row itself (DRAG-UI-003).
                            val placeholderHeight = with(density) { dragRowSize.height.toDp() }
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
                                    .border(2.dp, MaterialTheme.colorScheme.primary, placeholderShape),
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f)) { content(sourceNodes.getValue(item.id)) }
                        }
                        // Stays mounted for the dragged row too — removing it from
                        // composition mid-drag (i.e. gating on !isDraggedRow) would tear
                        // down the very pointerInput coroutine that's tracking this
                        // gesture, silently killing the drag after its first event. Made
                        // invisible (not removed) while dragged: the handle should visually
                        // stick to the floating row that's actually tracking the finger
                        // (see its own decorative copy below), not stay behind with the
                        // static placeholder — alpha doesn't affect layout, hit-testing, or
                        // accessibility, only drawing, so the real interactive instance
                        // keeps working exactly as before.
                        if (displayOrder.size > 1) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = stringResource(R.string.cd_drag_handle),
                                modifier = Modifier
                                    .testTag("drag_handle_${item.id}")
                                    .onGloballyPositioned { handleCoordinates[item.id] = it }
                                    .size(48.dp)
                                    .alpha(if (isDraggedRow) 0f else 1f)
                                    .then(
                                        // Legacy per-row gesture host. With the overlay strip
                                        // active (the default), this handle is purely
                                        // decorative — the strip hosts the gesture — so no
                                        // pointerInput is attached here at all.
                                        if (!useOverlayStrip) {
                                            Modifier.pointerInput(item.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        // Settling (DRAG-UI-014) blocks new pickups the same way an
                                                        // active drag does — belt-and-suspenders alongside the
                                                        // exclusivity overlay below, which should already be
                                                        // consuming this touch.
                                                        if (settledDisplayOrder == null) {
                                                            draggedId = item.id
                                                            dragDelta = Offset.Zero
                                                            currentTargetId = null
                                                            currentZone = DropZone.None
                                                            rowCoordinates[item.id]?.let { coords ->
                                                                val pos = coords.positionInRoot()
                                                                dragStartPosition = Offset(pos.x, pos.y - rootTop)
                                                                dragRowSize = coords.size
                                                                pointerYInList = (pos.y - rootTop) + coords.size.height / 2f
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = { endDragAndMaybeMove() },
                                                    onDragCancel = { cancelDrag() },
                                                ) { change, amount -> onDragMove(change, amount) }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    // Padding comes after the touch target is established
                                    // (size above, plus pointerInput in the legacy path) so it
                                    // only shrinks what's drawn, not the 48dp hit area — the
                                    // standard Material icon size (24dp) within a 48dp touch
                                    // target, not a 48dp icon.
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }

        // @spec DRAG-UI-001, DRAG-UI-015 — the drag gesture, hosted on a single persistent strip
        // over the handle column rather than on each row's handle. Because the strip is never a
        // LazyColumn item, the LazyColumn disposing the dragged row as it auto-scrolls
        // off-screen can't dispose this gesture's coroutine, so the drag survives sustained
        // auto-scroll. Confined to the handle column so it doesn't reach the row content.
        //
        // Unlike a blanket detectDragGestures (which would grab every touch anywhere in the
        // column), this hand-rolled loop only claims a touch that actually lands on a row's
        // drag-handle icon: it finds the visible row under the down, then checks the down is
        // within that row's handle bounds (DRAG-UI-001 — pickup is *on the handle*). A touch
        // that misses every handle is left unconsumed, so it falls through to the LazyColumn
        // underneath and scrolls normally. Pickup waits for touch slop, so a tap on a handle
        // is not a drag. TEMPORARY: only attached on the default (useOverlayStrip) path.
        if (useOverlayStrip && displayOrder.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(48.dp)
                    .testTag("drag_strip")
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // requireUnconsumed = false: the LazyColumn under the strip may
                                // also see this down; we still want first look so we can decide
                                // whether to claim it (handle) or leave it (scroll).
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Settling (DRAG-UI-014) blocks new pickups; let the touch fall
                                // through (the exclusivity overlay is consuming it anyway).
                                if (settledDisplayOrder != null) continue

                                // The strip shares the outer Box's coordinate origin with the
                                // LazyColumn's viewport, so the down's Y is directly comparable
                                // to visibleItemsInfo offsets and to handle bounds expressed in
                                // that same (root - rootTop) space.
                                val order = lastRenderedOrder
                                val y = down.position.y
                                val hit = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { y >= it.offset && y < it.offset + it.size }
                                val rowId = hit?.let { order.getOrNull(it.index)?.id }
                                val handle = rowId?.let { handleCoordinates[it] }
                                val overHandle = handle != null && run {
                                    val top = handle.positionInRoot().y - rootTop
                                    y >= top && y < top + handle.size.height
                                }
                                android.util.Log.d(
                                    "DragReorderListDebug",
                                    "strip down y=$y -> row=$rowId overHandle=$overHandle",
                                )
                                if (hit == null || rowId == null || !overHandle || order.size <= 1) {
                                    // Not on a handle — leave the down unconsumed so the list scrolls.
                                    continue
                                }

                                // Claim the gesture so the list's scroll doesn't also start, then
                                // wait for slop before committing to a drag (a tap does nothing).
                                down.consume()
                                val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                if (slop == null) continue

                                draggedId = rowId
                                dragDelta = Offset.Zero
                                currentTargetId = null
                                currentZone = DropZone.None
                                dragStartPosition = Offset(0f, hit.offset.toFloat())
                                dragRowSize = IntSize(0, hit.size)
                                pointerYInList = hit.offset + hit.size / 2f

                                val completed = drag(slop.id) { change ->
                                    onDragMove(change, change.positionChange())
                                    change.consume()
                                }
                                if (completed) endDragAndMaybeMove() else cancelDrag()
                            }
                        }
                    },
            )
        }

        // @spec DRAG-UI-013, DRAG-UI-014 — blocks new touches elsewhere (a second finger
        // starting a new drag, or a long-press on another row) without disturbing the
        // pointer already driving this drag: consuming that pointer's own changes here too
        // would make detectDragGestures below cancel its own gesture mid-drag. Also covers
        // settling (draggingPointerId is null by then, so every touch — including a new
        // pickup attempt — gets consumed; see the onDragStart guard above too).
        if (draggedId != null || settledDisplayOrder != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(1f)
                    .pointerInput(draggedId, settledDisplayOrder != null) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.id != draggingPointerId) change.consume()
                                }
                            }
                        }
                    },
            )
        }

        // @spec DRAG-UI-002
        if (draggedItem != null && dragStartPosition != null) {
            val start = dragStartPosition!!
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .zIndex(2f)
                    .offset { IntOffset((start.x + dragDelta.x).roundToInt(), (start.y + dragDelta.y).roundToInt()) },
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
                            modifier = Modifier.size(48.dp).padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
