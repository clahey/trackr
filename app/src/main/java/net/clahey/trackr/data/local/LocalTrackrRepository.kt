package net.clahey.trackr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.withTransaction
import net.clahey.trackr.data.ImageStore
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.CategoryHasChildrenException
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.SiblingSlot
import net.clahey.trackr.domain.StarterCategoryInput
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.convertEventValue
import net.clahey.trackr.domain.reconcileSiblingOrder
import net.clahey.trackr.domain.starterCategoriesToInsert
import net.clahey.trackr.ui.theme.DEFAULT_CATEGORY_COLOR
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
    private val reminderDao: ReminderDao,
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

    private suspend fun requireNoChildren(category: Category) {
        if (category is Category.SubCategory) {
            val childCount = categoryDao.countByParentIdOnce(category.id)
            if (childCount != 0) throw CategoryHasChildrenException(category.id, childCount)
        }
    }

    private suspend fun migrateEventsForCategory(categoryId: String, targetType: ValueType) {
        eventDao.getByCategoryIncludingChildrenWithNullTypeOnce(categoryId).forEach { entity ->
            val event = entity.toDomain()
            val newValue = convertEventValue(event.value, targetType)
            if (newValue != event.value) {
                eventDao.upsert(event.copy(value = newValue).toEntity())
            }
        }
    }

    // @spec DM-DATA-028
    override suspend fun saveCategory(category: Category) {
        db.withTransaction {
            requireNoChildren(category)
            categoryDao.upsert(category.toEntity())
        }
    }

    // @spec CAT-UI-032, CAT-UI-033, CAT-UI-034, CAT-UI-035, DM-PROC-021, DM-DATA-028
    override suspend fun saveCategoryAndMigrateEvents(category: Category, fromType: ValueType) {
        db.withTransaction {
            requireNoChildren(category)
            categoryDao.upsert(category.toEntity())
            migrateEventsForCategory(category.id, category.resolvedValueType)
        }
    }

    // Re-reads the destination group's current members within the caller's transaction and
    // dense-reindexes them, treating `orderedSiblingIds` as a stale ordering hint (CAT-UI-083)
    // rather than an authoritative membership list — closing the read-outside-transaction gap.
    private suspend fun reindexDestinationGroup(category: Category, orderedSiblingIds: List<String>) {
        val members = when (category) {
            is Category.MetaCategory -> categoryDao.getTopLevelOnce()
            is Category.SubCategory -> categoryDao.getChildrenByParentIdOnce(category.parent.id)
        }
        val ordered = reconcileSiblingOrder(
            members.map { SiblingSlot(it.id, it.sortOrder) },
            orderedSiblingIds,
        )
        categoryDao.updateSortOrders(ordered)
    }

    // @spec CAT-UI-080, CAT-UI-083
    override suspend fun moveCategory(category: Category, orderedSiblingIds: List<String>) {
        db.withTransaction {
            requireNoChildren(category)
            categoryDao.upsert(category.toEntity())
            reindexDestinationGroup(category, orderedSiblingIds)
        }
    }

    // @spec CAT-UI-080, CAT-UI-081, CAT-UI-083
    override suspend fun moveCategoryAndMigrateEvents(
        category: Category,
        orderedSiblingIds: List<String>,
        fromType: ValueType,
    ) {
        db.withTransaction {
            requireNoChildren(category)
            categoryDao.upsert(category.toEntity())
            reindexDestinationGroup(category, orderedSiblingIds)
            migrateEventsForCategory(category.id, category.resolvedValueType)
        }
    }

    // @spec LS-BE-031
    // @spec CAT-UI-006
    override suspend fun deleteCategory(id: String) {
        var imagePaths: List<String> = emptyList()
        db.withTransaction {
            val parent = categoryDao.getByIdOnce(id)
            val children = categoryDao.getChildrenByParentIdOnce(id)
            for (child in children) {
                categoryDao.upsert(child.copy(
                    parentId = null,
                    emoji = child.emoji ?: parent?.emoji ?: "",
                    color = child.color ?: parent?.color ?: DEFAULT_CATEGORY_COLOR,
                    valueType = child.valueType ?: parent?.valueType ?: "none",
                ))
            }
            imagePaths = eventDao.getByCategoryOnce(id).flatMap { it.imagePaths() }
            categoryDao.deleteById(id)  // Room CASCADE deletes the category's own events
        }
        imagePaths.forEach { imageStore.delete(it) }
    }

    override suspend fun reorderCategories(orderedIds: List<String>) {
        categoryDao.updateSortOrders(orderedIds)
    }

    override fun getEventCountForCategory(categoryId: String, includeSubCategoriesWithNullType: Boolean): Flow<Int> =
        if (includeSubCategoriesWithNullType)
            eventDao.countByCategoryIncludingChildrenWithNullType(categoryId)
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

    // @spec EL-UI-011
    override fun getEventsByCategoryIdIncludingChildren(id: String): Flow<List<Event>> =
        eventDao.getByCategoryIncludingChildren(id).map { it.map { e -> e.toDomain() } }

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
        eventDao.deleteById(id)
    }

    override suspend fun deleteEventFiles(imagePaths: List<String>) {
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

    // @spec CAT-UI-090, LS-BE-093
    override suspend fun addStarterCategories(specs: List<StarterCategoryInput>): Int =
        db.withTransaction {
            val existingNames = categoryDao.getAllOnce().map { it.name }
            val toInsert = starterCategoriesToInsert(existingNames, categoryDao.getMinSortOrder(), specs)
            toInsert.forEach { categoryDao.upsert(it.toEntity()) }
            toInsert.size
        }

    // @spec LS-BE-040, EL-PROC-003
    override suspend fun onStartup() {
        val referencedPaths = eventDao.getAllOnce().flatMap { it.imagePaths() }.toSet()
        imageStore.allStoredPaths()
            .filter { it !in referencedPaths }
            .forEach { imageStore.delete(it) }
    }

    private fun EventEntity.imagePaths(): List<String> =
        net.clahey.trackr.data.local.converters.StringListConverter.decode(imagePaths)

    // @spec REM-DATA-006
    override fun getReminderForCategory(categoryId: String): Flow<Reminder?> =
        reminderDao.getByCategoryId(categoryId).map { it?.toDomain() }

    // @spec REM-DATA-006
    override suspend fun saveReminder(reminder: Reminder) {
        reminderDao.upsert(reminder.toEntity())
    }

    // @spec REM-DATA-006, REM-DATA-008
    override suspend fun saveCategoryWithReminder(category: Category, reminder: Reminder?, migrateFromType: ValueType?) {
        db.withTransaction {
            requireNoChildren(category)
            categoryDao.upsert(category.toEntity())
            if (migrateFromType != null) {
                migrateEventsForCategory(category.id, category.resolvedValueType)
            }
            if (reminder == null) {
                reminderDao.deleteByCategoryId(category.id)
            } else {
                // nextFireAt is ignored on `reminder` — the DB's current value survives this
                // write untouched, so a save can never clobber a value ReminderScheduler set
                // concurrently while the edit screen was open (REM-DATA-008).
                val currentNextFireAt = reminderDao.getByCategoryIdOnce(category.id)?.nextFireAt
                reminderDao.upsert(reminder.toEntity().copy(nextFireAt = currentNextFireAt))
            }
        }
    }

    // @spec REM-DATA-007
    override suspend fun getAllEnabledRemindersOnce(): List<Reminder> =
        reminderDao.getAllEnabledOnce().map { it.toDomain() }
}
