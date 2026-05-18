package com.trackr.app

import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeTrackrRepository : TrackrRepository {
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val events = MutableStateFlow<List<Event>>(emptyList())
    private var nextColorIndex = 0

    override fun getCategories(): Flow<List<Category>> = categories.map { it.sortedBy { c -> c.sortOrder } }
    override fun getCategoryById(id: String): Flow<Category?> = categories.map { it.find { c -> c.id == id } }
    override suspend fun saveCategory(category: Category) {
        categories.update { list -> list.filter { it.id != category.id } + category }
    }
    override suspend fun deleteCategory(id: String) {
        categories.update { it.filter { c -> c.id != id } }
        events.update { it.filter { e -> e.categoryId != id } }
    }
    override suspend fun reorderCategories(orderedIds: List<String>) {
        val map = categories.value.associateBy { it.id }
        categories.value = orderedIds.mapIndexed { i, id -> map[id]!!.copy(sortOrder = i) }
    }
    override fun getEventCountForCategory(categoryId: String): Flow<Int> =
        events.map { it.count { e -> e.categoryId == categoryId } }

    override fun getEvents(start: Instant?, end: Instant?): Flow<List<Event>> = events.map { list ->
        list.filter { (start == null || !it.timestamp.isBefore(start)) && (end == null || it.timestamp.isBefore(end)) }
            .sortedWith(compareByDescending<Event> { it.timestamp }.thenByDescending { it.createdAt }.thenBy { it.id })
    }
    override fun getEventsByCategory(categoryId: String): Flow<List<Event>> =
        events.map { it.filter { e -> e.categoryId == categoryId }
            .sortedWith(compareByDescending<Event> { it.timestamp }.thenByDescending { it.createdAt }.thenBy { it.id }) }
    override fun getEventById(id: String): Flow<Event?> = events.map { it.find { e -> e.id == id } }
    override suspend fun saveEvent(event: Event) {
        events.update { list -> list.filter { it.id != event.id } + event }
    }
    override suspend fun deleteEvent(id: String) { events.update { it.filter { e -> e.id != id } } }

    override suspend fun getAndIncrementNextCategoryColorIndex(): Int = nextColorIndex++

    override suspend fun onStartup() {}

    fun setEvents(vararg e: Event) { events.value = e.toList() }
    fun setCategories(vararg c: Category) { categories.value = c.toList() }
}
