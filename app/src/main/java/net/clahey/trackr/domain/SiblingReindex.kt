package net.clahey.trackr.domain

/** One current member of a sibling group: its id and its present `sortOrder`. */
data class SiblingSlot(val id: String, val sortOrder: Int)

/**
 * Reconciles a stale drop-time ordering hint against the destination group's *current* members,
 * producing the id order to dense-reindex.
 *
 * `orderedSiblingIds` is only an ordering hint (a UI snapshot taken outside the write transaction,
 * possibly held across a confirmation dialog), not an authoritative membership list:
 * - a member whose id is in the hint takes the hint's relative order;
 * - a hint id that is no longer a current member is dropped;
 * - a current member absent from the hint (added concurrently) is folded back in at its prior
 *   position — immediately after whichever hint-known member it currently follows by `sortOrder`,
 *   or at the front if it currently precedes every hint-known member. Multiple such members
 *   sharing one anchor keep their mutual `sortOrder` order.
 *
 * @spec CAT-UI-083
 */
fun reconcileSiblingOrder(
    currentMembers: List<SiblingSlot>,
    orderedSiblingIds: List<String>,
): List<String> {
    val presentIds = currentMembers.mapTo(HashSet()) { it.id }
    val hintIds = orderedSiblingIds.toHashSet()
    // Hint-known members, in hint order; ids no longer present are dropped.
    val known = orderedSiblingIds.filter { it in presentIds }
    // Current order (by sortOrder, id as a deterministic tie-break) drives where each unknown
    // member currently sits relative to the known ones.
    val currentOrder = currentMembers.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val unknownsByAnchor = LinkedHashMap<String?, MutableList<String>>()
    var lastKnown: String? = null
    for (slot in currentOrder) {
        if (slot.id in hintIds) {
            lastKnown = slot.id
        } else {
            unknownsByAnchor.getOrPut(lastKnown) { mutableListOf() }.add(slot.id)
        }
    }
    val result = ArrayList<String>(currentMembers.size)
    unknownsByAnchor[null]?.let(result::addAll)
    for (id in known) {
        result.add(id)
        unknownsByAnchor[id]?.let(result::addAll)
    }
    return result
}
