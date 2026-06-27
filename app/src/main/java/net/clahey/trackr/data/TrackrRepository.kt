package net.clahey.trackr.data

import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.ValueType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TrackrRepository {
    fun getCategories(): Flow<List<Category>>
    fun getCategoryById(id: String): Flow<Category?>
    suspend fun saveCategory(category: Category)
    suspend fun saveCategoryAndMigrateEvents(category: Category, fromType: ValueType)
    // @spec CAT-UI-006
    suspend fun deleteCategory(id: String)
    suspend fun reorderCategories(orderedIds: List<String>)
    // @spec CAT-UI-080
    suspend fun moveCategory(category: Category, orderedSiblingIds: List<String>)
    // @spec CAT-UI-080, CAT-UI-081
    suspend fun moveCategoryAndMigrateEvents(category: Category, orderedSiblingIds: List<String>, fromType: ValueType)
    fun getEventCountForCategory(categoryId: String, includeSubCategoriesWithNullType: Boolean = false): Flow<Int>
    fun getSubCategoryCount(categoryId: String): Flow<Int>

    fun getEvents(start: Instant? = null, end: Instant? = null): Flow<List<Event>>
    fun getEventsByCategory(categoryId: String): Flow<List<Event>>
    // @spec EL-UI-011
    fun getEventsByCategoryIdIncludingChildren(id: String): Flow<List<Event>>
    fun getEventById(id: String): Flow<Event?>
    suspend fun saveEvent(event: Event)
    suspend fun deleteEvent(id: String)
    suspend fun deleteEventFiles(imagePaths: List<String>)

    suspend fun getAndIncrementNextCategoryColorIndex(paletteSize: Int): Int

    suspend fun onStartup()
}
