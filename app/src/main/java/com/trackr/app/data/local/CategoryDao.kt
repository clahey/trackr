package com.trackr.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// @spec LS-BE-010, LS-BE-011, LS-BE-012, LS-BE-013
@Dao
abstract class CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    abstract fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    abstract fun getById(id: String): Flow<CategoryEntity?>

    @Query("SELECT MIN(sortOrder) FROM categories")
    abstract suspend fun getMinSortOrder(): Int?

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    abstract suspend fun setSortOrder(id: String, sortOrder: Int)

    @Transaction
    open suspend fun updateSortOrders(ids: List<String>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Upsert
    abstract suspend fun upsert(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    abstract suspend fun deleteById(id: String)
}
