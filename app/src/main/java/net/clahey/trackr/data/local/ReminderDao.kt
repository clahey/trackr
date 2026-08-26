package net.clahey.trackr.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    // @spec REM-DATA-011
    @Query("SELECT * FROM reminders WHERE categoryId = :categoryId")
    fun getByCategoryId(categoryId: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE categoryId = :categoryId")
    suspend fun getByCategoryIdOnce(categoryId: String): ReminderEntity?

    // @spec REM-DATA-007
    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getAllEnabledOnce(): List<ReminderEntity>

    // @spec REM-DATA-009
    @Query("SELECT EXISTS(SELECT 1 FROM reminders WHERE enabled = 1)")
    fun hasEnabled(): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: ReminderEntity)
}
