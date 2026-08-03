package net.clahey.trackr.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// @spec LS-BE-020, LS-BE-021, LS-BE-030, LS-BE-031, LS-BE-032, LS-BE-040
@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY timestamp DESC, createdAt DESC, id ASC")
    fun getAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE timestamp >= :start ORDER BY timestamp DESC, createdAt DESC, id ASC")
    fun getAllFrom(start: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE timestamp < :end ORDER BY timestamp DESC, createdAt DESC, id ASC")
    fun getAllBefore(end: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC, createdAt DESC, id ASC")
    fun getAllInRange(start: Long, end: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE categoryId = :categoryId ORDER BY timestamp DESC, createdAt DESC, id ASC")
    fun getByCategory(categoryId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun getById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getByIdOnce(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE categoryId = :categoryId")
    suspend fun getByCategoryOnce(categoryId: String): List<EventEntity>

    // @spec EL-UI-011
    @Query("""
        SELECT * FROM events
        WHERE categoryId = :categoryId
           OR categoryId IN (
               SELECT id FROM categories WHERE parentId = :categoryId
           )
        ORDER BY timestamp DESC, createdAt DESC, id ASC
    """)
    fun getByCategoryIncludingChildren(categoryId: String): Flow<List<EventEntity>>

    // @spec DM-PROC-021
    @Query("""
        SELECT * FROM events
        WHERE categoryId = :categoryId
           OR categoryId IN (
               SELECT id FROM categories WHERE parentId = :categoryId AND valueType IS NULL
           )
    """)
    suspend fun getByCategoryIncludingChildrenWithNullTypeOnce(categoryId: String): List<EventEntity>

    @Query("SELECT * FROM events")
    suspend fun getAllOnce(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE categoryId = :categoryId")
    fun countByCategory(categoryId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM events
        WHERE categoryId = :categoryId
           OR categoryId IN (
               SELECT id FROM categories WHERE parentId = :categoryId AND valueType IS NULL
           )
    """)
    fun countByCategoryIncludingChildrenWithNullType(categoryId: String): Flow<Int>

    @Upsert
    suspend fun upsert(entity: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}
