package com.trackr.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.withTransaction
import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ValueType
import com.trackr.app.domain.convertEventValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

// @spec LS-BE-010, LS-BE-011, LS-BE-012, LS-BE-013, LS-BE-020, LS-BE-021,
// LS-BE-030, LS-BE-031, LS-BE-032, LS-BE-040, LS-BE-050, LS-BE-051,
// LS-BE-052, LS-BE-053, LS-BE-054, LS-BE-071, LS-BE-080, LS-BE-081
class LocalTrackrRepository @javax.inject.Inject constructor(
    private val db: TrackrDatabase,
    private val categoryDao: CategoryDao,
    private val eventDao: EventDao,
    private val imageStore: ImageStore,
    private val dataStore: DataStore<Preferences>,
) : TrackrRepository {

    private val nextColorKey = intPreferencesKey("next_category_color_index")

    // @spec CAT-UI-001
    override fun getCategories(): Flow<List<Category>> =
        categoryDao.getAll().map { entities ->
            entities.toDomainList().sortedWith(compareBy(
                { cat -> if (cat is Category.SubCategory) cat.parent.sortOrder else cat.sortOrder },
                { it is Category.SubCategory },
                { it.sortOrder },
            ))
        }

    override fun getCategoryById(id: String): Flow<Category?> =
        categoryDao.getByIdWithParent(id).map { it?.toDomain() }

    // @spec DM-DATA-028
    override suspend fun saveCategory(category: Category) {
        db.withTransaction {
            if (category is Category.SubCategory) {
                val childCount = categoryDao.countByParentIdOnce(category.id)
                require(childCount == 0) {
                    "Cannot nest category '${category.id}': it has $childCount SubCategory children"
                }
            }
            categoryDao.upsert(category.toEntity())
        }
    }

    // @spec CAT-UI-032, CAT-UI-033, CAT-UI-034, CAT-UI-035, DM-PROC-021, DM-DATA-028
    override suspend fun saveCategoryAndMigrateEvents(category: Category, fromType: ValueType) {
        val targetType = category.resolvedValueType
        db.withTransaction {
            if (category is Category.SubCategory) {
                val childCount = categoryDao.countByParentIdOnce(category.id)
                require(childCount == 0) {
                    "Cannot nest category '${category.id}': it has $childCount SubCategory children"
                }
            }
            categoryDao.upsert(category.toEntity())
            eventDao.getByCategoryIncludingInheriting(category.id).forEach { entity ->
                val event = entity.toDomain()
                val newValue = convertEventValue(event.value, targetType)
                if (newValue != event.value) {
                    eventDao.upsert(event.copy(value = newValue).toEntity())
                }
            }
        }
    }

    // @spec LS-BE-031
    override suspend fun deleteCategory(id: String) {
        val imagePaths = eventDao.getByCategoryOnce(id).flatMap { it.imagePaths() }
        categoryDao.deleteById(id)
        imagePaths.forEach { imageStore.delete(it) }
    }

    // @spec CAT-UI-006
    override suspend fun deleteMetaCategoryAndPromoteSubcategories(id: String) {
        var imagePaths: List<String> = emptyList()
        db.withTransaction {
            val parent = categoryDao.getByIdOnce(id)
            val children = categoryDao.getChildrenByParentIdOnce(id)
            for (child in children) {
                categoryDao.upsert(child.copy(
                    parentId = null,
                    emoji = child.emoji ?: parent?.emoji ?: "",
                    color = child.color ?: parent?.color ?: 0xFFE53935L,
                    valueType = child.valueType ?: parent?.valueType ?: "none",
                ))
            }
            imagePaths = eventDao.getByCategoryOnce(id).flatMap { it.imagePaths() }
            categoryDao.deleteById(id)  // Room CASCADE deletes the category's events
        }
        imagePaths.forEach { imageStore.delete(it) }
    }

    override suspend fun reorderCategories(orderedIds: List<String>) {
        categoryDao.updateSortOrders(orderedIds)
    }

    override fun getEventCountForCategory(categoryId: String, includeSubCategoriesWithNullType: Boolean): Flow<Int> =
        if (includeSubCategoriesWithNullType)
            eventDao.countByCategoryIncludingInheriting(categoryId)
        else
            eventDao.countByCategory(categoryId)

    override fun getSubCategoryCount(categoryId: String): Flow<Int> =
        categoryDao.countByParentId(categoryId)

    override fun getEvents(start: Instant?, end: Instant?): Flow<List<Event>> {
        val startMs = start?.toEpochMilli()
        val endMs = end?.toEpochMilli()
        val flow = when {
            startMs != null && endMs != null -> eventDao.getAllInRange(startMs, endMs)
            startMs != null -> eventDao.getAllFrom(startMs)
            endMs != null -> eventDao.getAllBefore(endMs)
            else -> eventDao.getAll()
        }
        return flow.map { it.map { e -> e.toDomain() } }
    }

    override fun getEventsByCategory(categoryId: String): Flow<List<Event>> =
        eventDao.getByCategory(categoryId).map { it.map { e -> e.toDomain() } }

    override fun getEventsByCategoryIds(ids: Collection<String>): Flow<List<Event>> =
        eventDao.getByCategoryIds(ids).map { it.map { e -> e.toDomain() } }

    override fun getEventById(id: String): Flow<Event?> =
        eventDao.getById(id).map { it?.toDomain() }

    // @spec LS-BE-032
    override suspend fun saveEvent(event: Event) {
        val oldPaths = eventDao.getByIdOnce(event.id)?.imagePaths() ?: emptyList()
        eventDao.upsert(event.toEntity())
        val removedPaths = oldPaths - event.imagePaths.toSet()
        removedPaths.forEach { imageStore.delete(it) }
    }

    // @spec LS-BE-030
    override suspend fun deleteEvent(id: String) {
        val imagePaths = eventDao.getByIdOnce(id)?.imagePaths() ?: emptyList()
        eventDao.deleteById(id)
        imagePaths.forEach { imageStore.delete(it) }
    }

    // @spec LS-BE-080, LS-BE-081
    override suspend fun getAndIncrementNextCategoryColorIndex(paletteSize: Int): Int {
        var current = 0
        dataStore.edit { prefs ->
            current = prefs[nextColorKey] ?: 0
            prefs[nextColorKey] = (current + 1) % paletteSize
        }
        return current
    }

    // @spec LS-BE-040
    override suspend fun onStartup() {
        val referencedPaths = eventDao.getAllOnce().flatMap { it.imagePaths() }.toSet()
        imageStore.allStoredPaths()
            .filter { it !in referencedPaths }
            .forEach { imageStore.delete(it) }
    }

    private fun EventEntity.imagePaths(): List<String> =
        com.trackr.app.data.local.converters.StringListConverter.decode(imagePaths)
}
