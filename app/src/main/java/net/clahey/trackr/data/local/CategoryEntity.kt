package net.clahey.trackr.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String?,
    val color: Long?,
    val valueType: String?,
    val defaultValue: String?,
    val allowEmptyText: Boolean,
    @ColumnInfo(index = true) val sortOrder: Int,
    @ColumnInfo(index = true) val parentId: String? = null,
)
