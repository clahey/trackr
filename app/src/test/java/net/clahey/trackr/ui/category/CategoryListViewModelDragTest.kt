package net.clahey.trackr.ui.category

import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.ValueTypeWarningTier
import net.clahey.trackr.ui.components.DragMoveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TestFunctionName")
class CategoryListViewModelDragTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var vm: CategoryListViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        vm = CategoryListViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // @spec CAT-UI-002, CAT-UI-080, CAT-UI-082
    @Test fun `onDragMove reorders top-level MetaCategories without changing parent and settles immediately`() = runTest {
        repo.setCategories(makeMetaCategory("a", sortOrder = 0), makeMetaCategory("b", sortOrder = 1))
        var settled = false
        vm.onDragMove(DragMoveResult("b", null, listOf("b", "a"))) { settled = true }
        val cats = repo.getCategories().first().associateBy { it.id }
        assertEquals(0, cats["b"]!!.sortOrder)
        assertEquals(1, cats["a"]!!.sortOrder)
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertTrue("expected onSettled to have been called", settled)
    }

    // @spec CAT-UI-002, CAT-UI-080
    @Test fun `onDragMove moves a SubCategory to a new group preserving explicit overrides`() = runTest {
        val oldParent = makeMetaCategory("oldParent")
        val newParent = makeMetaCategory("newParent")
        val child = makeSubCategory("child", oldParent, emoji = "🎯", color = 0xFF00BCD4L, valueType = ValueType.Text)
        repo.setCategories(oldParent, newParent, child)
        vm.onDragMove(DragMoveResult("child", "newParent", listOf("child"))) {}
        val saved = repo.getCategoryById("child").first() as Category.SubCategory
        assertEquals("newParent", saved.parent.id)
        assertEquals("🎯", saved.emoji)
        assertEquals(0xFF00BCD4L, saved.color)
        assertEquals(ValueType.Text, saved.valueType)
    }

    // @spec CAT-UI-002, CAT-UI-080
    @Test fun `onDragMove with null anchor promotes a SubCategory to top-level resolving inherited fields`() = runTest {
        val parent = makeMetaCategory("parent", emoji = "🏋️", color = 0xFF1E88E5L, valueType = ValueType.Number)
        val child = makeSubCategory("child", parent) // all nulls = inheriting
        repo.setCategories(parent, child)
        vm.onDragMove(DragMoveResult("child", null, listOf("child", "parent"))) {}
        val promoted = repo.getCategoryById("child").first() as Category.MetaCategory
        assertEquals("🏋️", promoted.emoji)
        assertEquals(0xFF1E88E5L, promoted.color)
        assertEquals(ValueType.Number, promoted.valueType)
    }

    // @spec CAT-UI-002, CAT-UI-080
    @Test fun `onDragMove nests a childless MetaCategory as a SubCategory`() = runTest {
        val target = makeMetaCategory("target", emoji = "🎯", valueType = ValueType.None)
        val newParent = makeMetaCategory("parent")
        repo.setCategories(target, newParent)
        vm.onDragMove(DragMoveResult("target", "parent", listOf("target"))) {}
        val saved = repo.getCategoryById("target").first() as Category.SubCategory
        assertEquals("parent", saved.parent.id)
        assertEquals("🎯", saved.emoji)
        assertNull("a successful nest emits no rejection message", vm.reparentRejectedCategoryName.value)
    }

    // @spec CAT-UI-082, CAT-UI-084
    @Test fun `onDragMove nesting a MetaCategory that concurrently gained children is rejected, settles once, persists nothing, and emits a rejection message`() = runTest {
        // The menu/snapshot saw "target" as a childless MetaCategory (nest-eligible), but it
        // gained a SubCategory before the drop applied — nesting it now would break the cap.
        val target = makeMetaCategory("target", valueType = ValueType.None)
        val newParent = makeMetaCategory("parent")
        val sneakyChild = makeSubCategory("sneaky", target)
        repo.setCategories(target, newParent, sneakyChild)
        var settledCount = 0
        vm.onDragMove(DragMoveResult("target", "parent", listOf("target"))) { settledCount++ }
        // Rolled back: target is still a top-level MetaCategory and still parents "sneaky".
        assertTrue(repo.getCategoryById("target").first() is Category.MetaCategory)
        assertEquals("sneaky still nested under target", "target",
            (repo.getCategoryById("sneaky").first() as Category.SubCategory).parent.id)
        assertEquals("onSettled fires exactly once even on rejection", 1, settledCount)
        assertEquals("target", vm.reparentRejectedCategoryName.value)
        // The message is one-shot: consuming it clears the flow.
        vm.consumeReparentRejection()
        assertNull(vm.reparentRejectedCategoryName.value)
    }

    // @spec CAT-UI-084
    @Test fun `Add to group of a MetaCategory that concurrently gained children is rejected and emits a message without crashing`() = runTest {
        val target = makeMetaCategory("target")
        val newParent = makeMetaCategory("parent")
        val child = makeSubCategory("child", target)
        repo.setCategories(target, newParent, child)
        vm.reparentCategory("target", "parent")
        assertTrue("nothing persisted; target stays top-level",
            repo.getCategoryById("target").first() is Category.MetaCategory)
        assertEquals("target", vm.reparentRejectedCategoryName.value)
    }

    // @spec CAT-UI-081, CAT-UI-082
    @Test fun `onDragMove with a fully safe and reversible type change skips the confirmation dialog and settles immediately`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.None)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Text)
        val child = makeSubCategory("child", oldParent) // inherits None -> Text: reversible, no warning
        repo.setCategories(oldParent, newParent, child)
        repo.setEvents(Event("e1", "child", Instant.parse("2024-01-15T12:00:00Z"), null, null, emptyList(), Instant.parse("2024-01-15T12:00:00Z")))
        var settled = false
        vm.onDragMove(DragMoveResult("child", "newParent", listOf("child"))) { settled = true }
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertEquals("newParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
        assertTrue("expected onSettled to have been called", settled)
    }

    // @spec CAT-UI-081
    @Test fun `onDragMove with zero own events skips the confirmation dialog even for an unsafe conversion`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.Number)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Duration)
        val child = makeSubCategory("child", oldParent) // inherits Number -> Duration: unsafe, but zero events
        repo.setCategories(oldParent, newParent, child)
        vm.onDragMove(DragMoveResult("child", "newParent", listOf("child"))) {}
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertEquals("newParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
    }

    // @spec CAT-UI-081, CAT-UI-082
    @Test fun `onDragMove with an unsafe type change and existing events shows a confirmation dialog, does not persist yet, and does not settle yet`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.Number)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Duration)
        val child = makeSubCategory("child", oldParent)
        repo.setCategories(oldParent, newParent, child)
        repo.setEvents(Event("e1", "child", Instant.parse("2024-01-15T12:00:00Z"), null, null, emptyList(), Instant.parse("2024-01-15T12:00:00Z")))
        var settled = false
        vm.onDragMove(DragMoveResult("child", "newParent", listOf("child"))) { settled = true }
        val pending = vm.pendingValueTypeConfirmation.value
        assertEquals(ValueTypeWarningTier.Unsafe, pending?.tier)
        // Nothing persisted yet — the move is abandoned until the user confirms.
        assertEquals("oldParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
        assertFalse("onSettled should not fire until the dialog is resolved", settled)
    }

    // @spec CAT-UI-081, CAT-UI-082
    @Test fun `confirmPendingValueTypeChange persists the move, clears the pending state, and settles`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.Number)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Duration)
        val child = makeSubCategory("child", oldParent)
        repo.setCategories(oldParent, newParent, child)
        repo.setEvents(Event("e1", "child", Instant.parse("2024-01-15T12:00:00Z"), null, null, emptyList(), Instant.parse("2024-01-15T12:00:00Z")))
        var settled = false
        vm.onDragMove(DragMoveResult("child", "newParent", listOf("child"))) { settled = true }
        assertFalse(settled)
        vm.confirmPendingValueTypeChange()
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertEquals("newParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
        assertTrue("expected onSettled to have been called after confirm", settled)
    }

    // @spec CAT-UI-082, CAT-UI-084
    // The deferred (confirm-path) persist can still be rejected: "d" was a childless,
    // inheriting SubCategory when the drag opened the type-change dialog, but a concurrent
    // write promoted it to a MetaCategory and gave it children while the dialog was open. On
    // confirm the in-transaction guard re-reads d's live children and throws. The guarantee
    // under test is graceful, non-freezing degradation — onSettled must still fire so the drag
    // widget unfreezes — not the (deferred, sync-gated) reactive-dialog-dismiss behavior; see
    // category-management.md Open Questions.
    @Test fun `a confirm whose deferred persist is rejected still settles the row and clears the dialog`() = runTest {
        val p1 = makeMetaCategory("p1", valueType = ValueType.Number)
        val p2 = makeMetaCategory("p2", valueType = ValueType.Duration)
        val d = makeSubCategory("d", p1)  // inheriting: dragging it to p2 needs a type change -> dialog
        repo.setCategories(p1, p2, d)
        repo.setEvents(Event("e1", "d", Instant.parse("2024-01-15T12:00:00Z"), null, null, emptyList(), Instant.parse("2024-01-15T12:00:00Z")))
        var settledCount = 0
        vm.onDragMove(DragMoveResult("d", "p2", listOf("d"))) { settledCount++ }
        // Dialog is up; nothing has settled yet.
        assertEquals(ValueTypeWarningTier.Unsafe, vm.pendingValueTypeConfirmation.value?.tier)
        assertEquals(0, settledCount)
        // Concurrently: d is promoted to a MetaCategory and gains a child, so the pending
        // SubCategory snapshot of d can no longer be nested.
        val dPromoted = makeMetaCategory("d", valueType = ValueType.Number)
        repo.setCategories(p1, p2, dPromoted, makeSubCategory("gc", dPromoted))
        vm.confirmPendingValueTypeChange()
        // The row settles exactly once (widget unfreezes), the dialog clears, and the move is
        // rejected rather than applied.
        assertEquals("onSettled must fire so the drag widget never freezes", 1, settledCount)
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertEquals("d", vm.reparentRejectedCategoryName.value)
        assertTrue("d stays a MetaCategory; the nest was not persisted",
            repo.getCategoryById("d").first() is Category.MetaCategory)
    }

    // @spec CAT-UI-081, CAT-UI-082
    @Test fun `cancelPendingValueTypeChange abandons the move entirely and still settles`() = runTest {
        val oldParent = makeMetaCategory("oldParent", valueType = ValueType.Number)
        val newParent = makeMetaCategory("newParent", valueType = ValueType.Duration)
        val child = makeSubCategory("child", oldParent)
        repo.setCategories(oldParent, newParent, child)
        repo.setEvents(Event("e1", "child", Instant.parse("2024-01-15T12:00:00Z"), null, null, emptyList(), Instant.parse("2024-01-15T12:00:00Z")))
        var settled = false
        vm.onDragMove(DragMoveResult("child", "newParent", listOf("child"))) { settled = true }
        assertFalse(settled)
        vm.cancelPendingValueTypeChange()
        assertNull(vm.pendingValueTypeConfirmation.value)
        assertEquals("oldParent", (repo.getCategoryById("child").first() as Category.SubCategory).parent.id)
        assertTrue("expected onSettled to have been called after cancel", settled)
    }

    // ---------- Helpers ----------

    private fun makeMetaCategory(
        id: String,
        sortOrder: Int = 0,
        emoji: String = "📌",
        color: Long = 0xFFE53935L,
        valueType: ValueType = ValueType.None,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = emoji, color = color,
        valueType = valueType, defaultValue = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(
        id: String,
        parent: Category.MetaCategory,
        emoji: String? = null,
        color: Long? = null,
        valueType: ValueType? = null,
    ) = Category.SubCategory(
        id = id, name = id, emoji = emoji, color = color, valueType = valueType,
        defaultValue = null, allowEmptyText = true, sortOrder = 0, parent = parent,
    )
}
