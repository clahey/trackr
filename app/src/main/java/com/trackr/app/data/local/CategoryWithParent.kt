package com.trackr.app.data.local

import androidx.room.Embedded

data class CategoryWithParent(
    @Embedded val category: CategoryEntity,
    @Embedded(prefix = "parent_") val parent: CategoryEntity?,
)
