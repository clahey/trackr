package com.trackr.app.data

import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TrackrRepository {
    fun getCategories(): Flow<List<Category>>
    fun getCategoryById(id: String): Flow<Category?>
    suspend fun saveCategory(category: Category)
    suspend fun deleteCategory(id: String)
    suspend fun reorderCategories(orderedIds: List<String>)
    fun getEventCountForCategory(categoryId: String): Flow<Int>

    fun getEvents(start: Instant? = null, end: Instant? = null): Flow<List<Event>>
    fun getEventsByCategory(categoryId: String): Flow<List<Event>>
    fun getEventById(id: String): Flow<Event?>
    suspend fun saveEvent(event: Event)
    suspend fun deleteEvent(id: String)

    suspend fun getAndIncrementNextCategoryColorIndex(): Int

    suspend fun onStartup()
}
