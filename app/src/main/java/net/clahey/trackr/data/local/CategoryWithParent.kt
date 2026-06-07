package net.clahey.trackr.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class CategoryWithParent(
    @Embedded val category: CategoryEntity,
    @Relation(parentColumn = "parentId", entityColumn = "id")
    val parent: CategoryEntity?,
)
