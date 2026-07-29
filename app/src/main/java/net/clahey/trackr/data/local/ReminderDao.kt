package net.clahey.trackr.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// @spec REM-DATA-006, REM-DATA-007, REM-DATA-008
@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE categoryId = :categoryId")
    fun getByCategoryId(categoryId: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE categoryId = :categoryId")
    suspend fun getByCategoryIdOnce(categoryId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getAllEnabledOnce(): List<ReminderEntity>

    @Upsert
    suspend fun upsert(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: String)
}
