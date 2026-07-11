package net.clahey.trackr.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SiblingReindexTest {

    private fun slots(vararg pairs: Pair<String, Int>) =
        pairs.map { (id, order) -> SiblingSlot(id, order) }

    // @spec CAT-UI-083
    @Test fun `hint members keep their hint order`() {
        val ordered = reconcileSiblingOrder(
            currentMembers = slots("a" to 0, "b" to 1, "c" to 2),
            orderedSiblingIds = listOf("c", "a", "b"),
        )
        assertEquals(listOf("c", "a", "b"), ordered)
    }

    // @spec CAT-UI-083
    @Test fun `an id in the hint that is no longer a member is dropped`() {
        val ordered = reconcileSiblingOrder(
            currentMembers = slots("a" to 0, "c" to 2),  // "b" deleted/reparented away since the snapshot
            orderedSiblingIds = listOf("c", "b", "a"),
        )
        assertEquals(listOf("c", "a"), ordered)
    }

    // @spec CAT-UI-083
    @Test fun `an unknown member added at the top is folded in at the front`() {
        // "new" was created concurrently at sortOrder = min - 1 (CAT-UI-041), so it precedes
        // every hint-known member and must land at the front, ahead of the user's arranged order.
        val ordered = reconcileSiblingOrder(
            currentMembers = slots("new" to -1, "a" to 0, "b" to 1, "c" to 2),
            orderedSiblingIds = listOf("c", "a", "b"),
        )
        assertEquals(listOf("new", "c", "a", "b"), ordered)
    }

    // @spec CAT-UI-083
    @Test fun `an unknown member in the middle stays immediately after the sibling it currently follows`() {
        // "x" was added between a2 and a3 (sortOrder 15) after the snapshot was taken.
        val ordered = reconcileSiblingOrder(
            currentMembers = slots("a1" to 0, "a2" to 10, "x" to 15, "a3" to 20),
            orderedSiblingIds = listOf("a3", "a2", "a1"),
        )
        // "x" currently follows a2 by sortOrder, so it trails a2 in the result — not pulled to
        // front or end, never displacing the user's arranged [a3, a2, a1].
        assertEquals(listOf("a3", "a2", "x", "a1"), ordered)
    }

    // @spec CAT-UI-083
    @Test fun `multiple unknowns sharing one anchor keep their mutual sortOrder order`() {
        val ordered = reconcileSiblingOrder(
            currentMembers = slots("a" to 0, "x" to 5, "y" to 6, "b" to 10),
            orderedSiblingIds = listOf("b", "a"),
        )
        // x (5) and y (6) both currently follow a and precede b; they stay after a in
        // ascending sortOrder order (x before y), regardless of input list order.
        assertEquals(listOf("b", "a", "x", "y"), ordered)
    }

    // @spec CAT-UI-083
    @Test fun `a hint that already matches current membership dense-orders unchanged`() {
        val ordered = reconcileSiblingOrder(
            currentMembers = slots("a" to 0, "b" to 1),
            orderedSiblingIds = listOf("a", "b"),
        )
        assertEquals(listOf("a", "b"), ordered)
    }
}
