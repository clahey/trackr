package com.trackr.app.data.local

import com.trackr.app.data.local.converters.EventValueConverter
import com.trackr.app.data.local.converters.InstantConverter
import com.trackr.app.data.local.converters.StringListConverter
import com.trackr.app.data.local.converters.ValueTypeConverter
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.theme.DEFAULT_CATEGORY_COLOR

private fun CategoryEntity.toMetaCategory() = Category.MetaCategory(
    id = id, name = name,
    emoji = emoji ?: "",
    color = color ?: DEFAULT_CATEGORY_COLOR,
    valueType = valueType?.let { ValueTypeConverter.decode(it) } ?: ValueType.None,
    defaultValue = EventValueConverter.decode(defaultValue),
    allowEmptyText = allowEmptyText, sortOrder = sortOrder,
)

fun List<CategoryEntity>.toDomainList(): List<Category> {
    val metaMap = mutableMapOf<String, Category.MetaCategory>()
    forEach { entity -> if (entity.parentId == null) metaMap[entity.id] = entity.toMetaCategory() }
    // @spec DM-PROC-022
    return map { entity ->
        if (entity.parentId == null) {
            metaMap[entity.id]!!
        } else {
            val parent = metaMap[entity.parentId]
            if (parent == null) {
                entity.toMetaCategory()
            } else {
                Category.SubCategory(
                    id = entity.id,
                    name = entity.name,
                    emoji = entity.emoji,
                    color = entity.color,
                    valueType = entity.valueType?.let { ValueTypeConverter.decode(it) },
                    defaultValue = EventValueConverter.decode(entity.defaultValue),
                    allowEmptyText = entity.allowEmptyText,
                    sortOrder = entity.sortOrder,
                    parent = parent,
                )
            }
        }
    }
}

// @spec DM-PROC-022
fun CategoryWithParent.toDomain(): Category {
    val parentMeta = parent?.toMetaCategory()
    return if (parentMeta != null) {
        Category.SubCategory(
            id = category.id, name = category.name,
            emoji = category.emoji, color = category.color,
            valueType = category.valueType?.let { ValueTypeConverter.decode(it) },
            defaultValue = EventValueConverter.decode(category.defaultValue),
            allowEmptyText = category.allowEmptyText,
            sortOrder = category.sortOrder, parent = parentMeta,
        )
    } else {
        category.toMetaCategory()
    }
}

fun Category.toEntity(): CategoryEntity = when (this) {
    is Category.MetaCategory -> CategoryEntity(
        id = id, name = name, emoji = emoji, color = color,
        valueType = ValueTypeConverter.encode(valueType),
        defaultValue = EventValueConverter.encode(defaultValue),
        allowEmptyText = allowEmptyText, sortOrder = sortOrder,
        parentId = null,
    )
    is Category.SubCategory -> CategoryEntity(
        id = id, name = name, emoji = emoji, color = color,
        valueType = valueType?.let { ValueTypeConverter.encode(it) },
        defaultValue = EventValueConverter.encode(defaultValue),
        allowEmptyText = allowEmptyText, sortOrder = sortOrder,
        parentId = parent.id,
    )
}

fun EventEntity.toDomain(): Event = Event(
    id = id, categoryId = categoryId,
    timestamp = InstantConverter.decode(timestamp),
    value = EventValueConverter.decode(value),
    notes = notes,
    imagePaths = StringListConverter.decode(imagePaths),
    createdAt = InstantConverter.decode(createdAt),
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id, categoryId = categoryId,
    timestamp = InstantConverter.encode(timestamp),
    value = EventValueConverter.encode(value),
    notes = notes,
    imagePaths = StringListConverter.encode(imagePaths),
    createdAt = InstantConverter.encode(createdAt),
)
