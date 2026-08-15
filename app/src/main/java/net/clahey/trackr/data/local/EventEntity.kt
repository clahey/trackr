package net.clahey.trackr.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// @spec LS-BE-071
@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId"), Index("timestamp"), Index("createdAt")],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val timestamp: Long,
    val value: String?,
    val notes: String?,
    val imagePaths: String,
    val createdAt: Long,
)
