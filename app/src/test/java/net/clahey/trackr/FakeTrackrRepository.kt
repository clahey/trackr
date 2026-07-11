package net.clahey.trackr

import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.SiblingSlot
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.convertEventValue
import net.clahey.trackr.domain.reconcileSiblingOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeTrackrRepository : TrackrRepository {
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val events = MutableStateFlow<List<Event>>(emptyList())
    private var nextColorIndex = 0

    // @spec CAT-UI-001, DM-PROC-022
    override fun getCategories(): Flow<List<Category>> = categories.map { list ->
        val metaIds = list.filterIsInstance<Category.MetaCategory>().map { it.id }.toSet()
        val resolved = list.map { cat ->
            if (cat is Category.SubCategory && cat.parent.id !in metaIds) {
                Category.MetaCategory(
                    id = cat.id, name = cat.name,
                    emoji = cat.emoji ?: cat.parent.emoji,
                    color = cat.color ?: cat.parent.color,
                    valueType = cat.valueType ?: cat.parent.valueType,
                    defaultValue = cat.defaultValue, allowEmptyText = cat.allowEmptyText, sortOrder = cat.sortOrder,
                )
            } else cat
        }
        resolved.sortedWith(compareBy(
            { cat -> if (cat is Category.SubCategory) cat.parent.sortOrder else cat.sortOrder },
            { it is Category.SubCategory },
            { it.sortOrder },
        ))
    }
    override fun getCategoryById(id: String): Flow<Category?> = categories.map { it.find { c -> c.id == id } }
    // @spec DM-DATA-028
    override suspend fun saveCategory(category: Category) {
        categories.update { list ->
            if (category is Category.SubCategory) {
                val childCount = list.count { c ->
                    c is Category.SubCategory && c.parent.id == category.id
                }
                require(childCount == 0) {
                    "Cannot nest category '${category.id}': it has $childCount SubCategory children"
                }
            }
            val updated = list.filter { it.id != category.id } + category
            if (category is Category.MetaCategory) {
                updated.map { cat ->
                    if (cat is Category.SubCategory && cat.parent.id == category.id) cat.copy(parent = category)
                    else cat
                }
            } else updated
        }
    }

    // @spec DM-PROC-021, DM-DATA-028
    override suspend fun saveCategoryAndMigrateEvents(category: Category, fromType: ValueType) {
        saveCategory(category)  // constraint check is inside saveCategory
        val targetType = category.resolvedValueType
        val affectedIds = buildAffectedIds(category)
        events.update { list ->
            list.map { event ->
                if (event.categoryId !in affectedIds) event
                else {
                    val newValue = convertEventValue(event.value, targetType)
                    if (newValue != event.value) event.copy(value = newValue) else event
                }
            }
        }
    }

    private fun buildAffectedIds(category: Category): Set<String> {
        if (category !is Category.MetaCategory) return setOf(category.id)
        val inheritingChildIds = categories.value
            .filterIsInstance<Category.SubCategory>()
            .filter { it.parent.id == category.id && it.valueType == null }
            .map { it.id }
        return setOf(category.id) + inheritingChildIds
    }

    // @spec CAT-UI-006
    override suspend fun deleteCategory(id: String) {
        categories.update { list ->
            val promoted = list.filterIsInstance<Category.SubCategory>()
                .filter { it.parent.id == id }
                .map { sub ->
                    Category.MetaCategory(
                        id = sub.id,
                        name = sub.name,
                        emoji = sub.resolvedEmoji,
                        color = sub.resolvedColor,
                        valueType = sub.resolvedValueType,
                        defaultValue = sub.defaultValue,
                        allowEmptyText = sub.allowEmptyText,
                        sortOrder = sub.sortOrder,
                    )
                }
            list.filter { it.id != id && !(it is Category.SubCategory && it.parent.id == id) } + promoted
        }
        events.update { it.filter { e -> e.categoryId != id } }
    }

    override suspend fun reorderCategories(orderedIds: List<String>) {
        val map = categories.value.associateBy { it.id }
        categories.value = orderedIds.mapIndexed { i, id ->
            when (val cat = map[id]!!) {
                is Category.MetaCategory -> cat.copy(sortOrder = i)
                is Category.SubCategory -> cat.copy(sortOrder = i)
            }
        }
    }

    // Reindexes sortOrder for just the given ids (a sibling group), leaving every other
    // category's sortOrder untouched — unlike reorderCategories, which assumes its
    // argument is the complete list.
    private fun updateSortOrdersFor(orderedIds: List<String>) {
        val positions = orderedIds.withIndex().associate { (i, id) -> id to i }
        categories.update { list ->
            list.map { cat ->
                val newOrder = positions[cat.id] ?: return@map cat
                when (cat) {
                    is Category.MetaCategory -> cat.copy(sortOrder = newOrder)
                    is Category.SubCategory -> cat.copy(sortOrder = newOrder)
                }
            }
        }
    }

    // Mirrors LocalTrackrRepository.reindexDestinationGroup: re-reads the destination group's
    // current members after the move and dense-reindexes them, using orderedSiblingIds only as
    // a stale ordering hint (CAT-UI-083).
    private fun reindexDestinationGroup(category: Category, orderedSiblingIds: List<String>) {
        val list = categories.value
        val members = when (category) {
            is Category.MetaCategory -> list.filterIsInstance<Category.MetaCategory>()
            is Category.SubCategory ->
                list.filterIsInstance<Category.SubCategory>().filter { it.parent.id == category.parent.id }
        }
        val ordered = reconcileSiblingOrder(
            members.map { SiblingSlot(it.id, it.sortOrder) },
            orderedSiblingIds,
        )
        updateSortOrdersFor(ordered)
    }

    // @spec CAT-UI-080, CAT-UI-083
    override suspend fun moveCategory(category: Category, orderedSiblingIds: List<String>) {
        saveCategory(category)  // constraint check is inside saveCategory
        reindexDestinationGroup(category, orderedSiblingIds)
    }

    // @spec CAT-UI-080, CAT-UI-081, CAT-UI-083
    override suspend fun moveCategoryAndMigrateEvents(
        category: Category,
        orderedSiblingIds: List<String>,
        fromType: ValueType,
    ) {
        saveCategoryAndMigrateEvents(category, fromType)
        reindexDestinationGroup(category, orderedSiblingIds)
    }

    override fun getEventCountForCategory(categoryId: String, includeSubCategoriesWithNullType: Boolean): Flow<Int> {
        return if (!includeSubCategoriesWithNullType) {
            events.map { it.count { e -> e.categoryId == categoryId } }
        } else {
            combine(categories, events) { cats, evts ->
                val inheritingChildIds = cats
                    .filterIsInstance<Category.SubCategory>()
                    .filter { it.parent.id == categoryId && it.valueType == null }
                    .map { it.id }
                    .toSet()
                evts.count { e -> e.categoryId == categoryId || e.categoryId in inheritingChildIds }
            }
        }
    }

    override fun getSubCategoryCount(categoryId: String): Flow<Int> =
        categories.map { cats ->
            cats.count { c -> c is Category.SubCategory && c.parent.id == categoryId }
        }

    override fun getEvents(start: Instant?, end: Instant?): Flow<List<Event>> = events.map { list ->
        list.filter { (start == null || !it.timestamp.isBefore(start)) && (end == null || it.timestamp.isBefore(end)) }
            .sortedWith(compareByDescending<Event> { it.timestamp }.thenByDescending { it.createdAt }.thenBy { it.id })
    }
    override fun getEventsByCategory(categoryId: String): Flow<List<Event>> =
        events.map { it.filter { e -> e.categoryId == categoryId }
            .sortedWith(compareByDescending<Event> { it.timestamp }.thenByDescending { it.createdAt }.thenBy { it.id }) }

    // @spec EL-UI-011
    override fun getEventsByCategoryIdIncludingChildren(id: String): Flow<List<Event>> =
        combine(categories, events) { cats, evts ->
            val childIds = cats.filterIsInstance<Category.SubCategory>()
                .filter { it.parent.id == id }
                .map { it.id }.toSet()
            evts.filter { e -> e.categoryId == id || e.categoryId in childIds }
                .sortedWith(compareByDescending<Event> { it.timestamp }.thenByDescending { it.createdAt }.thenBy { it.id })
        }
    override fun getEventById(id: String): Flow<Event?> = events.map { it.find { e -> e.id == id } }
    override suspend fun saveEvent(event: Event) {
        events.update { list -> list.filter { it.id != event.id } + event }
    }
    override suspend fun deleteEvent(id: String) { events.update { it.filter { e -> e.id != id } } }
    override suspend fun deleteEventFiles(imagePaths: List<String>) { }

    override suspend fun getAndIncrementNextCategoryColorIndex(paletteSize: Int): Int {
        val current = nextColorIndex
        nextColorIndex = (nextColorIndex + 1) % paletteSize
        return current
    }

    override suspend fun onStartup() {}

    fun setEvents(vararg e: Event) { events.value = e.toList() }
    fun setCategories(vararg c: Category) { categories.value = c.toList() }

    // Test helpers for CategoryEditViewModelHierarchyTest (Phase 6 Step 3)
    fun resetColorCounter(value: Int) { nextColorIndex = value }
    fun peekColorCounter(): Int = nextColorIndex
}
