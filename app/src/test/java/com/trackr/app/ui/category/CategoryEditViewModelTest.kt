package com.trackr.app.ui.category

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.FakeTrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.trackr.app.ui.SaveResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeTrackrRepository
    private lateinit var vm: CategoryEditViewModel

    companion object {
        val anchor: Instant = Instant.parse("2024-01-15T12:00:00Z")
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTrackrRepository()
        vm = CategoryEditViewModel(repo, SavedStateHandle())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ---------- Validation ----------

    // @spec CAT-UI-020
    @Test fun `save with empty name produces validation error`() = runTest {
        vm.name.value = ""
        vm.emoji.value = "🏃"
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("name", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-020
    @Test fun `save with whitespace-only name produces validation error`() = runTest {
        vm.name.value = "   "
        vm.emoji.value = "🏃"
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec CAT-UI-021
    @Test fun `save with empty emoji produces validation error`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = ""
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-022
    @Test fun `save with multi-grapheme emoji produces validation error`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃🏃"
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // ---------- New category save behavior ----------

    // @spec CAT-UI-040
    @Test fun `new category gets a UUID on save`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        val savedCategory = getSavedCategory()
        assertTrue(savedCategory.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    // @spec CAT-UI-041
    @Test fun `new category gets sortOrder currentMin minus 1`() = runTest {
        repo.saveCategory(makeCategory("existing", sortOrder = 5))
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertEquals(4, getSavedCategoryByName("Running").sortOrder)
    }

    // @spec CAT-UI-042
    @Test fun `new category gets allowEmptyText true`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertTrue(getSavedCategory().allowEmptyText)
    }

    // @spec CAT-UI-043
    @Test fun `new category default color pre-populated from counter on init`() = runTest {
        assertEquals(0xFFE53935L, vm.color.value)
    }

    // @spec CAT-UI-043
    @Test fun `new category default color is saved`() = runTest {
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertEquals(0xFFE53935L, getSavedCategory().color)
    }

    // @spec CAT-UI-043
    @Test fun `second new category gets next palette color`() = runTest {
        vm.name.value = "Running"; vm.emoji.value = "🏃"; vm.save()
        vm = CategoryEditViewModel(repo, SavedStateHandle())
        vm.name.value = "Sleep"; vm.emoji.value = "💤"; vm.save()
        assertEquals(0xFFE53935L, getSavedCategoryByName("Running").color)
        assertEquals(0xFFFB8C00L, getSavedCategoryByName("Sleep").color)
    }

    // @spec CAT-UI-043
    @Test fun `new category saves picker-selected color when user overrides default`() = runTest {
        vm.color.value = 0xFF1E88E5L // Blue
        vm.name.value = "Running"
        vm.emoji.value = "🏃"
        vm.save()
        assertEquals(0xFF1E88E5L, getSavedCategory().color)
    }

    // @spec CAT-UI-014
    @Test fun `existing category saves picker-selected color`() = runTest {
        repo.saveCategory(makeCategory("c1", color = 0xFFE53935L))
        vm = editVm("c1")
        vm.color.value = 0xFF43A047L // Green
        vm.name.value = "c1"
        vm.emoji.value = "📌"
        vm.save()
        assertEquals(0xFF43A047L, getSavedCategoryById("c1").color)
    }

    // ---------- ValueType warning tiers ----------

    // @spec CAT-UI-030
    @Test fun `reversible conversion with events shows no warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        assertNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030
    @Test fun `no warning when no events even for non-reversible conversion`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        assertNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-036
    @Test fun `none to number shows irreversible safe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        assertEquals(ValueTypeWarningTier.IrreversibleSafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-036
    @Test fun `duration to text shows irreversible safe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Duration))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        assertEquals(ValueTypeWarningTier.IrreversibleSafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-037
    @Test fun `text to number shows partial warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        assertEquals(ValueTypeWarningTier.Partial, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-037
    @Test fun `text to boolean shows partial warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Boolean
        assertEquals(ValueTypeWarningTier.Partial, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-038
    @Test fun `number to none shows unsafe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.None
        assertEquals(ValueTypeWarningTier.Unsafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-038
    @Test fun `boolean to duration shows unsafe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Duration
        assertEquals(ValueTypeWarningTier.Unsafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-031
    @Test fun `warning disappears when value type reverted to original`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.None
        assertNotNull(vm.valueTypeWarning.value)
        vm.valueType.value = ValueType.Number
        assertNull(vm.valueTypeWarning.value)
    }

    // ---------- Migration: fully safe conversions ----------

    // @spec CAT-UI-032
    @Test fun `none to number migrates null to default number`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        vm.save()
        assertEquals(EventValue.NumberValue(0.0, null), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to scale migrates null to default scale`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Scale
        vm.save()
        assertEquals(EventValue.Scale(5), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to boolean migrates null to true`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Boolean
        vm.save()
        assertEquals(EventValue.BooleanValue(true), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to text migrates null to empty string`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue(""), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to duration migrates null to zero duration`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Duration
        vm.save()
        assertEquals(EventValue.DurationValue(Duration.ZERO), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `scale to number migrates correctly`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.Scale(7)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        vm.save()
        assertEquals(EventValue.NumberValue(7.0, null), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `scale to text migrates correctly`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.Scale(7)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue("7"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `boolean true to text migrates to Yes`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.BooleanValue(true)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue("Yes"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `boolean false to text migrates to No`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.BooleanValue(false)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue("No"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `number to text includes unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.NumberValue(3.5, "kg")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue("3.5 kg"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `number to text without unit omits unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.NumberValue(3.5, null)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue("3.5"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `duration to text migrates correctly`() = runTest {
        val dur = 90.seconds
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Duration))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.DurationValue(dur)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue(dur.toString()), eventValue("c1"))
    }

    // ---------- Migration: partially safe conversions ----------

    // @spec CAT-UI-032, CAT-UI-035
    @Test fun `text Yes to boolean migrates to true`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("Yes")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Boolean
        vm.save()
        assertEquals(EventValue.BooleanValue(true), eventValue("c1"))
    }

    // @spec CAT-UI-032, CAT-UI-035
    @Test fun `text No to boolean migrates to false`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("No")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Boolean
        vm.save()
        assertEquals(EventValue.BooleanValue(false), eventValue("c1"))
    }

    // @spec CAT-UI-032, CAT-UI-034
    @Test fun `text to number parses bare number`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("3.5")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        vm.save()
        assertEquals(EventValue.NumberValue(3.5, null), eventValue("c1"))
    }

    // @spec CAT-UI-032, CAT-UI-034
    @Test fun `text to number parses number with unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("3.5 kg")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        vm.save()
        assertEquals(EventValue.NumberValue(3.5, "kg"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `text to scale migrates parseable int in range`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("7")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Scale
        vm.save()
        assertEquals(EventValue.Scale(7), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `empty text to none migrates to null`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.None
        vm.save()
        assertNull(eventValue("c1"))
    }

    // ---------- Migration: unconvertible values left unchanged ----------

    // @spec CAT-UI-033, CAT-UI-035
    @Test fun `non-yes-no text to boolean left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("maybe")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Boolean
        vm.save()
        assertEquals(EventValue.TextValue("maybe"), eventValue("c1"))
    }

    // @spec CAT-UI-033, CAT-UI-034
    @Test fun `non-parseable text to number left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("hello")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        vm.save()
        assertEquals(EventValue.TextValue("hello"), eventValue("c1"))
    }

    // @spec CAT-UI-033
    @Test fun `text out of scale range left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("11")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Scale
        vm.save()
        assertEquals(EventValue.TextValue("11"), eventValue("c1"))
    }

    // @spec CAT-UI-033
    @Test fun `non-empty text to none left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("hello")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.None
        vm.save()
        assertEquals(EventValue.TextValue("hello"), eventValue("c1"))
    }

    // @spec CAT-UI-044
    @Test fun `none to exercise migrates null to default ExerciseValue`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        vm.save()
        assertEquals(EventValue.ExerciseValue(3, 15), eventValue("c1"))
    }

    // @spec CAT-UI-045
    @Test fun `exercise to text migrates to sets times reps format`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.ExerciseValue(4, 12)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        vm.save()
        assertEquals(EventValue.TextValue("4 × 12"), eventValue("c1"))
    }

    // @spec CAT-UI-046
    @Test fun `text to exercise parses unicode times format`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("5 × 8")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        vm.save()
        assertEquals(EventValue.ExerciseValue(5, 8), eventValue("c1"))
    }

    // @spec CAT-UI-046
    @Test fun `text to exercise parses ascii x format`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("4 x 10")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        vm.save()
        assertEquals(EventValue.ExerciseValue(4, 10), eventValue("c1"))
    }

    // @spec CAT-UI-033, CAT-UI-046
    @Test fun `non-parseable text to exercise left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("bench press")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        vm.save()
        assertEquals(EventValue.TextValue("bench press"), eventValue("c1"))
    }

    // @spec CAT-UI-033, CAT-UI-046
    @Test fun `text to exercise with zero reps left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("3 × 0")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        vm.save()
        assertEquals(EventValue.TextValue("3 × 0"), eventValue("c1"))
    }

    // @spec CAT-UI-030
    @Test fun `exercise to text shows no warning (reversible)`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.ExerciseValue(3, 15)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Text
        assertNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-036
    @Test fun `none to exercise shows irreversible safe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        assertEquals(ValueTypeWarningTier.IrreversibleSafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-037
    @Test fun `text to exercise shows partial warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Exercise
        assertEquals(ValueTypeWarningTier.Partial, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-038
    @Test fun `exercise to number shows unsafe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.ExerciseValue(3, 15)))
        vm = editVm("c1")
        vm.valueType.value = ValueType.Number
        assertEquals(ValueTypeWarningTier.Unsafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-033
    @Test fun `non-safe conversion leaves event value unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.NumberValue(3.5, "kg")))
        vm = editVm("c1")
        vm.valueType.value = ValueType.None
        vm.save()
        assertEquals(EventValue.NumberValue(3.5, "kg"), eventValue("c1"))
    }

    // ---------- Stale category guard ----------

    // @spec CAT-UI-017
    @Test fun `navigateBack emits true when category not found in edit mode`() = runTest {
        vm = CategoryEditViewModel(repo, SavedStateHandle(mapOf("categoryId" to "nonexistent")))
        assertTrue(vm.navigateBack.value)
    }

    // @spec CAT-UI-017
    @Test fun `navigateBack stays false when category is found`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm = CategoryEditViewModel(repo, SavedStateHandle(mapOf("categoryId" to "c1")))
        assertFalse(vm.navigateBack.value)
    }

    // @spec CAT-UI-017
    @Test fun `navigateBack stays false in create mode`() = runTest {
        vm = CategoryEditViewModel(repo, SavedStateHandle())
        assertFalse(vm.navigateBack.value)
    }

    // ---------- Helpers ----------

    private fun editVm(categoryId: String) =
        CategoryEditViewModel(repo, SavedStateHandle(mapOf("categoryId" to categoryId)))

    private suspend fun eventValue(categoryId: String): EventValue? =
        repo.getEventsByCategory(categoryId).first().first().value

    private suspend fun getSavedCategory(): Category =
        repo.getCategories().first().first()

    private suspend fun getSavedCategoryByName(name: String): Category =
        repo.getCategories().first().first { it.name == name }

    private suspend fun getSavedCategoryById(id: String): Category =
        repo.getCategoryById(id).first()!!

    private fun makeCategory(
        id: String,
        sortOrder: Int = 0,
        valueType: ValueType = ValueType.None,
        color: Long = 0xFFE53935L,
    ) = Category(
        id = id, name = id, emoji = "📌", color = color,
        valueType = valueType, unit = null, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeEvent(
        id: String,
        categoryId: String,
        value: EventValue? = null,
    ) = Event(
        id = id, categoryId = categoryId, timestamp = anchor,
        value = value, notes = null, imagePaths = emptyList(), createdAt = anchor,
    )
}
