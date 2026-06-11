package net.clahey.trackr.ui.category

import androidx.lifecycle.SavedStateHandle
import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.clahey.trackr.ui.SaveResult
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
        vm.setName("")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("name", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-020
    @Test fun `save with whitespace-only name produces validation error`() = runTest {
        vm.setName("   ")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.ValidationError)
    }

    // @spec CAT-UI-021
    @Test fun `save with empty emoji produces validation error`() = runTest {
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, ""))
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-022
    @Test fun `save with multi-grapheme emoji produces validation error`() = runTest {
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃🏃"))
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // ---------- New category save behavior ----------

    // @spec CAT-UI-040
    @Test fun `new category gets a UUID on save`() = runTest {
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
        val savedCategory = getSavedCategory()
        assertTrue(savedCategory.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    // @spec CAT-UI-041
    @Test fun `new category gets sortOrder currentMin minus 1`() = runTest {
        repo.saveCategory(makeCategory("existing", sortOrder = 5))
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        assertEquals(4, getSavedCategoryByName("Running").sortOrder)
    }

    // @spec CAT-UI-042
    @Test fun `new category gets allowEmptyText true`() = runTest {
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        assertTrue(getSavedCategory().allowEmptyText)
    }

    // @spec CAT-UI-043
    @Test fun `new category default color pre-populated from counter on init`() = runTest {
        assertEquals(0xFFE53935L, vm.colorState.value)
    }

    // @spec CAT-UI-043
    @Test fun `new category default color is saved`() = runTest {
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        assertEquals(0xFFE53935L, getSavedCategory().color)
    }

    // @spec CAT-UI-043
    @Test fun `second new category gets next palette color`() = runTest {
        vm.setName("Running"); vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃")); vm.save()
        vm = CategoryEditViewModel(repo, SavedStateHandle())
        vm.setName("Sleep"); vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "💤")); vm.save()
        assertEquals(0xFFE53935L, getSavedCategoryByName("Running").color)
        assertEquals(0xFFFB8C00L, getSavedCategoryByName("Sleep").color)
    }

    // @spec CAT-UI-043
    @Test fun `new category saves picker-selected color when user overrides default`() = runTest {
        vm.setColorState(0xFF1E88E5L) // Blue
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        vm.save()
        assertEquals(0xFF1E88E5L, getSavedCategory().color)
    }

    // @spec CAT-UI-014
    @Test fun `existing category saves picker-selected color`() = runTest {
        repo.saveCategory(makeCategory("c1", color = 0xFFE53935L))
        vm = editVm("c1")
        vm.setColorState(0xFF43A047L) // Green
        vm.setName("c1")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
        vm.save()
        assertEquals(0xFF43A047L, getSavedCategoryById("c1").color)
    }

    // ---------- ValueType warning tiers ----------

    // @spec CAT-UI-030
    @Test fun `reversible conversion with events shows no warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        assertNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030
    @Test fun `no warning when no events even for non-reversible conversion`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        assertNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-036
    @Test fun `none to number shows irreversible safe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        assertEquals(ValueTypeWarningTier.IrreversibleSafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-036
    @Test fun `duration to text shows irreversible safe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Duration))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        assertEquals(ValueTypeWarningTier.IrreversibleSafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-037
    @Test fun `text to number shows partial warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        assertEquals(ValueTypeWarningTier.Partial, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-037
    @Test fun `text to boolean shows partial warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Boolean)
        assertEquals(ValueTypeWarningTier.Partial, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-038
    @Test fun `number to none shows unsafe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.None)
        assertEquals(ValueTypeWarningTier.Unsafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-038
    @Test fun `boolean to duration shows unsafe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Duration)
        assertEquals(ValueTypeWarningTier.Unsafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-031
    @Test fun `warning disappears when value type reverted to original`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.None)
        assertNotNull(vm.valueTypeWarning.value)
        vm.setValueTypeState(ValueType.Number)
        assertNull(vm.valueTypeWarning.value)
    }

    // ---------- Migration: fully safe conversions ----------

    // @spec CAT-UI-032
    @Test fun `none to number migrates null to default number`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        assertEquals(EventValue.NumberValue(0.0, null), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to scale migrates null to default scale`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Scale)
        vm.save()
        assertEquals(EventValue.Scale(5), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to boolean migrates null to true`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Boolean)
        vm.save()
        assertEquals(EventValue.BooleanValue(true), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to text migrates null to empty string`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue(""), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `none to duration migrates null to zero duration`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Duration)
        vm.save()
        assertEquals(EventValue.DurationValue(Duration.ZERO), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `scale to number migrates correctly`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.Scale(7)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        assertEquals(EventValue.NumberValue(7.0, null), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `scale to text migrates correctly`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Scale))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.Scale(7)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue("7"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `boolean true to text migrates to Yes`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.BooleanValue(true)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue("Yes"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `boolean false to text migrates to No`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Boolean))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.BooleanValue(false)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue("No"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `number to text includes unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.NumberValue(3.5, "kg")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue("3.5 kg"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `number to text without unit omits unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.NumberValue(3.5, null)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue("3.5"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `duration to text migrates correctly`() = runTest {
        val dur = 90.seconds
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Duration))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.DurationValue(dur)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue(dur.toString()), eventValue("c1"))
    }

    // ---------- Migration: partially safe conversions ----------

    // @spec CAT-UI-032, CAT-UI-035
    @Test fun `text Yes to boolean migrates to true`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("Yes")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Boolean)
        vm.save()
        assertEquals(EventValue.BooleanValue(true), eventValue("c1"))
    }

    // @spec CAT-UI-032, CAT-UI-035
    @Test fun `text No to boolean migrates to false`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("No")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Boolean)
        vm.save()
        assertEquals(EventValue.BooleanValue(false), eventValue("c1"))
    }

    // @spec CAT-UI-032, CAT-UI-034
    @Test fun `text to number parses bare number`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("3.5")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        assertEquals(EventValue.NumberValue(3.5, null), eventValue("c1"))
    }

    // @spec CAT-UI-032, CAT-UI-034
    @Test fun `text to number parses number with unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("3.5 kg")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        assertEquals(EventValue.NumberValue(3.5, "kg"), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `text to scale migrates parseable int in range`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("7")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Scale)
        vm.save()
        assertEquals(EventValue.Scale(7), eventValue("c1"))
    }

    // @spec CAT-UI-032
    @Test fun `empty text to none migrates to null`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.None)
        vm.save()
        assertNull(eventValue("c1"))
    }

    // ---------- Migration: unconvertible values left unchanged ----------

    // @spec CAT-UI-033, CAT-UI-035
    @Test fun `non-yes-no text to boolean left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("maybe")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Boolean)
        vm.save()
        assertEquals(EventValue.TextValue("maybe"), eventValue("c1"))
    }

    // @spec CAT-UI-033, CAT-UI-034
    @Test fun `non-parseable text to number left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("hello")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        vm.save()
        assertEquals(EventValue.TextValue("hello"), eventValue("c1"))
    }

    // @spec CAT-UI-033
    @Test fun `text out of scale range left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("11")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Scale)
        vm.save()
        assertEquals(EventValue.TextValue("11"), eventValue("c1"))
    }

    // @spec CAT-UI-033
    @Test fun `non-empty text to none left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("hello")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.None)
        vm.save()
        assertEquals(EventValue.TextValue("hello"), eventValue("c1"))
    }

    // @spec CAT-UI-044
    @Test fun `none to exercise migrates null to default ExerciseValue`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        vm.save()
        assertEquals(EventValue.ExerciseValue(3, 15), eventValue("c1"))
    }

    // @spec CAT-UI-045
    @Test fun `exercise to text migrates to sets times reps format`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.ExerciseValue(4, 12)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        vm.save()
        assertEquals(EventValue.TextValue("4 × 12"), eventValue("c1"))
    }

    // @spec CAT-UI-046
    @Test fun `text to exercise parses unicode times format`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("5 × 8")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        vm.save()
        assertEquals(EventValue.ExerciseValue(5, 8), eventValue("c1"))
    }

    // @spec CAT-UI-046
    @Test fun `text to exercise parses ascii x format`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("4 x 10")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        vm.save()
        assertEquals(EventValue.ExerciseValue(4, 10), eventValue("c1"))
    }

    // @spec CAT-UI-033, CAT-UI-046
    @Test fun `non-parseable text to exercise left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("bench press")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        vm.save()
        assertEquals(EventValue.TextValue("bench press"), eventValue("c1"))
    }

    // @spec CAT-UI-033, CAT-UI-046
    @Test fun `text to exercise with zero reps left unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.TextValue("3 × 0")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        vm.save()
        assertEquals(EventValue.TextValue("3 × 0"), eventValue("c1"))
    }

    // @spec CAT-UI-030
    @Test fun `exercise to text shows no warning (reversible)`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.ExerciseValue(3, 15)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Text)
        assertNull(vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-036
    @Test fun `none to exercise shows irreversible safe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.None))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        assertEquals(ValueTypeWarningTier.IrreversibleSafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-037
    @Test fun `text to exercise shows partial warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Text))
        repo.saveEvent(makeEvent("e1", "c1"))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Exercise)
        assertEquals(ValueTypeWarningTier.Partial, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-030, CAT-UI-038
    @Test fun `exercise to number shows unsafe warning`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.ExerciseValue(3, 15)))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.Number)
        assertEquals(ValueTypeWarningTier.Unsafe, vm.valueTypeWarning.value)
    }

    // @spec CAT-UI-033
    @Test fun `non-safe conversion leaves event value unchanged`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        repo.saveEvent(makeEvent("e1", "c1", EventValue.NumberValue(3.5, "kg")))
        vm = editVm("c1")
        vm.setValueTypeState(ValueType.None)
        vm.save()
        assertEquals(EventValue.NumberValue(3.5, "kg"), eventValue("c1"))
    }

    // ---------- Emoji UIState ----------

    // @spec CAT-UI-062
    @Test fun `loading SubCategory with null emoji initializes INHERIT with parent emoji as customValue`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        val state = vm.emojiUIState.value
        assertEquals(EmojiMode.INHERIT, state.mode)
        assertEquals("📌", state.customValue)
    }

    // @spec CAT-UI-062
    @Test fun `creating SubCategory initializes INHERIT with parent emoji as customValue`() = runTest {
        val parent = makeCategory("parent")
        repo.saveCategory(parent)
        vm = createSubVm("parent")
        val state = vm.emojiUIState.value
        assertEquals(EmojiMode.INHERIT, state.mode)
        assertEquals("📌", state.customValue)
    }

    // @spec CAT-UI-055
    @Test fun `saving SubCategory in INHERIT mode saves null emoji to domain model`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        vm.setName("child")
        vm.save()
        val saved = repo.getCategoryById("child").first() as Category.SubCategory
        assertNull(saved.emoji)
    }

    // @spec CAT-UI-055
    @Test fun `saving SubCategory in CUSTOM mode saves customValue as emoji`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🎯"))
        vm.setName("child")
        vm.save()
        val saved = repo.getCategoryById("child").first() as Category.SubCategory
        assertEquals("🎯", saved.emoji)
    }

    // @spec CAT-UI-021
    @Test fun `saving SubCategory in INHERIT mode skips emoji validation`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        vm.setName("child")
        vm.save()
        assertTrue(vm.saveResult.value is SaveResult.Success)
    }

    // @spec CAT-UI-021
    @Test fun `saving SubCategory in CUSTOM mode with empty emoji produces validation error`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, ""))
        vm.setName("child")
        vm.save()
        val result = vm.saveResult.value
        assertTrue(result is SaveResult.ValidationError)
        assertEquals("emoji", (result as SaveResult.ValidationError).field)
    }

    // @spec CAT-UI-061
    @Test fun `custom emoji preserved when user switches to inherit and back then saves`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🎯"))
        vm.setEmojiUIState(vm.emojiUIState.value.copy(mode = EmojiMode.INHERIT))
        vm.setEmojiUIState(vm.emojiUIState.value.copy(mode = EmojiMode.CUSTOM))
        vm.setName("child")
        vm.save()
        val saved = repo.getCategoryById("child").first() as Category.SubCategory
        assertEquals("🎯", saved.emoji)
    }

    // ---------- Preview event value ----------

    // @spec CAT-UI-059
    @Test fun `previewEventValue for None is null`() = runTest {
        vm.setValueTypeState(ValueType.None)
        assertNull(vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Number with blank unit produces NumberValue(0, null)`() = runTest {
        vm.setValueTypeState(ValueType.Number)
        vm.numberDefaultUnit.value = ""
        assertEquals(EventValue.NumberValue(0.0, null), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Number with unit produces NumberValue(0, unit)`() = runTest {
        vm.setValueTypeState(ValueType.Number)
        vm.numberDefaultUnit.value = "kg"
        assertEquals(EventValue.NumberValue(0.0, "kg"), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Scale produces ScaleValue(7)`() = runTest {
        vm.setValueTypeState(ValueType.Scale)
        assertEquals(EventValue.Scale(7), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Boolean produces BooleanValue(true)`() = runTest {
        vm.setValueTypeState(ValueType.Boolean)
        assertEquals(EventValue.BooleanValue(true), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Text produces TextValue("Sample")`() = runTest {
        vm.setValueTypeState(ValueType.Text)
        assertEquals(EventValue.TextValue("Sample"), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Duration produces DurationValue(90s)`() = runTest {
        vm.setValueTypeState(ValueType.Duration)
        assertEquals(EventValue.DurationValue(90.seconds), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Exercise produces ExerciseValue(3, 15)`() = runTest {
        vm.setValueTypeState(ValueType.Exercise)
        assertEquals(EventValue.ExerciseValue(3, 15), vm.previewEventValue.value)
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

    // ---------- Delete ----------

    // @spec CAT-UI-004
    @Test fun `requestDelete on MetaCategory with no events and no subcategories deletes immediately`() = runTest {
        repo.saveCategory(makeCategory("m1"))
        vm = editVm("m1")
        vm.requestDelete()
        assertNull(vm.pendingDeleteConfirmation.value)
        assertTrue(repo.getCategories().first().isEmpty())
    }

    // @spec CAT-UI-004
    @Test fun `requestDelete on SubCategory with no events deletes immediately`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("child")
        vm.requestDelete()
        assertNull(vm.pendingDeleteConfirmation.value)
        assertFalse(repo.getCategories().first().any { it.id == "child" })
        assertTrue(repo.getCategories().first().any { it.id == "parent" })
    }

    // @spec CAT-UI-005
    @Test fun `requestDelete on MetaCategory with subcategories shows confirmation with subCategoryCount`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("parent")
        vm.requestDelete()
        val confirmation = vm.pendingDeleteConfirmation.value
        assertNotNull(confirmation)
        assertEquals(1, confirmation!!.subCategoryCount)
        assertEquals(0, confirmation.ownEventCount)
    }

    // @spec CAT-UI-005
    @Test fun `requestDelete on SubCategory with events shows confirmation with ownEventCount`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child"))
        repo.saveEvent(makeEvent("e2", "child"))
        vm = editVm("child")
        vm.requestDelete()
        val confirmation = vm.pendingDeleteConfirmation.value
        assertNotNull(confirmation)
        assertEquals(2, confirmation!!.ownEventCount)
        assertEquals(0, confirmation.subCategoryCount)
    }

    // @spec CAT-UI-006
    @Test fun `confirmDelete on MetaCategory promotes subcategories and deletes parent's own events`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e_parent", "parent"))
        repo.saveEvent(makeEvent("e_child", "child"))
        vm = editVm("parent")
        vm.requestDelete()
        vm.confirmDelete()
        val remaining = repo.getCategories().first()
        assertFalse(remaining.any { it.id == "parent" })
        assertTrue(remaining.any { it.id == "child" })
        assertNull(repo.getEventById("e_parent").first())
        assertNotNull(repo.getEventById("e_child").first())
    }

    // @spec CAT-UI-006
    @Test fun `confirmDelete on SubCategory deletes it and its events`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        repo.saveEvent(makeEvent("e1", "child"))
        vm = editVm("child")
        vm.requestDelete()
        vm.confirmDelete()
        assertFalse(repo.getCategories().first().any { it.id == "child" })
        assertNull(repo.getEventById("e1").first())
        assertTrue(repo.getCategories().first().any { it.id == "parent" })
    }

    // @spec CAT-UI-005
    @Test fun `cancelDelete on edit screen clears confirmation without deleting`() = runTest {
        val parent = makeCategory("parent")
        val child = makeSubCategory("child", parent)
        repo.saveCategory(parent)
        repo.saveCategory(child)
        vm = editVm("parent")
        vm.requestDelete()
        vm.cancelDelete()
        assertNull(vm.pendingDeleteConfirmation.value)
        assertTrue(repo.getCategories().first().any { it.id == "parent" })
    }

    // ---------- Default Value ----------

    // @spec CAT-UI-011
    @Test fun `loading Number category seeds numberDefaultUnit from stored unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(0.0, "kg")))
        vm = editVm("c1")
        assertEquals("kg", vm.numberDefaultUnit.value)
    }

    // @spec CAT-UI-011
    @Test fun `loading Number category with null defaultValue seeds blank unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number, defaultValue = null))
        vm = editVm("c1")
        assertEquals("", vm.numberDefaultUnit.value)
    }

    // @spec CAT-UI-063
    @Test fun `saving Number category stores NumberValue with unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number, defaultValue = null))
        vm = editVm("c1")
        vm.updateNumberDefaultUnit("kg")
        vm.setName("c1")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
        vm.save()
        assertEquals(EventValue.NumberValue(0.0, "kg"), getSavedCategoryById("c1").defaultValue)
    }

    // @spec CAT-UI-063
    @Test fun `saving Number category preserves existing non-zero numeric value`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(42.0, "kg")))
        vm = editVm("c1")
        vm.updateNumberDefaultUnit("lbs")
        vm.setName("c1")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
        vm.save()
        assertEquals(EventValue.NumberValue(42.0, "lbs"), getSavedCategoryById("c1").defaultValue)
    }

    // @spec CAT-UI-063
    @Test fun `saving Number category with blank unit stores NumberValue with null unit`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number, defaultValue = null))
        vm = editVm("c1")
        vm.updateNumberDefaultUnit("")
        vm.setName("c1")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
        vm.save()
        assertEquals(EventValue.NumberValue(0.0, null), getSavedCategoryById("c1").defaultValue)
    }

    // @spec CAT-UI-011a
    @Test fun `loading Exercise category seeds exerciseSets and Reps from stored defaultValue`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise,
            defaultValue = EventValue.ExerciseValue(5, 10)))
        vm = editVm("c1")
        assertEquals("5", vm.exerciseDefaultSets.value)
        assertEquals("10", vm.exerciseDefaultReps.value)
    }

    // @spec CAT-UI-011a
    @Test fun `loading Exercise category with null defaultValue seeds 3 and 15`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise, defaultValue = null))
        vm = editVm("c1")
        assertEquals("3", vm.exerciseDefaultSets.value)
        assertEquals("15", vm.exerciseDefaultReps.value)
    }

    // @spec CAT-UI-064
    @Test fun `saving Exercise category stores ExerciseValue from sets and reps fields`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise, defaultValue = null))
        vm = editVm("c1")
        vm.updateExerciseDefaultSets("5")
        vm.updateExerciseDefaultReps("10")
        vm.setName("c1")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
        vm.save()
        assertEquals(EventValue.ExerciseValue(5, 10), getSavedCategoryById("c1").defaultValue)
    }

    // @spec CAT-UI-065
    @Test fun `saving Scale category leaves defaultValue unchanged`() = runTest {
        val existing = EventValue.Scale(7)
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Scale, defaultValue = existing))
        vm = editVm("c1")
        vm.setName("c1")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "📌"))
        vm.save()
        assertEquals(existing, getSavedCategoryById("c1").defaultValue)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Number uses resolvedDefaultValue when non-null`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number,
            defaultValue = EventValue.NumberValue(42.0, "kg")))
        vm = editVm("c1")
        assertEquals(EventValue.NumberValue(42.0, "kg"), vm.previewEventValue.value)
    }

    // @spec CAT-UI-059
    @Test fun `previewEventValue for Number with null defaultValue uses unit field`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number, defaultValue = null))
        vm = editVm("c1")
        vm.numberDefaultUnit.value = "kg"
        assertEquals(EventValue.NumberValue(0.0, "kg"), vm.previewEventValue.value)
    }

    // ---------- Dirty tracking ----------

    // @spec CAT-UI-067
    @Test fun `isDirty is false initially in edit mode`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `isDirty is true initially in MetaCategory create mode`() = runTest {
        vm = CategoryEditViewModel(repo, SavedStateHandle())
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `isDirty is true initially in SubCategory create mode`() = runTest {
        repo.saveCategory(makeCategory("parent"))
        vm = createSubVm("parent")
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `setName marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.setName("New Name")
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `setEmojiUIState marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🎯"))
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `setColorState marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.setColorState(0xFF43A047L)
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `setValueTypeState marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1"))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.setValueTypeState(ValueType.Number)
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `updateNumberDefaultUnit marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Number))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.updateNumberDefaultUnit("kg")
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `updateExerciseDefaultSets marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.updateExerciseDefaultSets("5")
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `updateExerciseDefaultReps marks isDirty`() = runTest {
        repo.saveCategory(makeCategory("c1", valueType = ValueType.Exercise))
        vm = editVm("c1")
        assertFalse(vm.isDirty.value)
        vm.updateExerciseDefaultReps("10")
        assertTrue(vm.isDirty.value)
    }

    // @spec CAT-UI-067
    @Test fun `successful save clears isDirty`() = runTest {
        vm.setName("Running")
        vm.setEmojiUIState(EmojiUIState(EmojiMode.CUSTOM, "🏃"))
        assertTrue(vm.isDirty.value)
        vm.save()
        assertFalse(vm.isDirty.value)
    }

    // ---------- Helpers ----------

    private fun editVm(categoryId: String) =
        CategoryEditViewModel(repo, SavedStateHandle(mapOf("categoryId" to categoryId)))

    private fun createSubVm(parentId: String) =
        CategoryEditViewModel(repo, SavedStateHandle(mapOf("parentId" to parentId)))

    private suspend fun eventValue(categoryId: String): EventValue? =
        repo.getEventsByCategory(categoryId).first().first().value

    private suspend fun getSavedCategory(): Category.MetaCategory =
        repo.getCategories().first().first() as Category.MetaCategory

    private suspend fun getSavedCategoryByName(name: String): Category.MetaCategory =
        repo.getCategories().first().first { it.name == name } as Category.MetaCategory

    private suspend fun getSavedCategoryById(id: String): Category.MetaCategory =
        repo.getCategoryById(id).first()!! as Category.MetaCategory

    private fun makeCategory(
        id: String,
        sortOrder: Int = 0,
        valueType: ValueType = ValueType.None,
        color: Long = 0xFFE53935L,
        defaultValue: EventValue? = null,
    ) = Category.MetaCategory(
        id = id, name = id, emoji = "📌", color = color,
        valueType = valueType, defaultValue = defaultValue, allowEmptyText = true, sortOrder = sortOrder,
    )

    private fun makeSubCategory(id: String, parent: Category.MetaCategory) = Category.SubCategory(
        id = id, name = id, emoji = null, color = null, valueType = null,
        defaultValue = null, allowEmptyText = true, sortOrder = 0, parent = parent,
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
