package com.trackr.app.data.local

import com.trackr.app.data.local.converters.EventValueConverter
import com.trackr.app.data.local.converters.InstantConverter
import com.trackr.app.data.local.converters.StringListConverter
import com.trackr.app.data.local.converters.ValueTypeConverter
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ValueType

fun List<CategoryEntity>.toDomainList(): List<Category> {
    val metaMap = mutableMapOf<String, Category.MetaCategory>()
    forEach { entity ->
        if (entity.parentId == null) {
            metaMap[entity.id] = Category.MetaCategory(
                id = entity.id,
                name = entity.name,
                emoji = entity.emoji ?: "",
                color = entity.color ?: 0xFFE53935L,
                valueType = entity.valueType?.let { ValueTypeConverter.decode(it) } ?: ValueType.None,
                unit = entity.unit,
                allowEmptyText = entity.allowEmptyText,
                sortOrder = entity.sortOrder,
            )
        }
    }
    return map { entity ->
        if (entity.parentId == null) {
            metaMap[entity.id]!!
        } else {
            val parent = metaMap[entity.parentId]
                ?: return@map null
            Category.SubCategory(
                id = entity.id,
                name = entity.name,
                emoji = entity.emoji,
                color = entity.color,
                valueType = entity.valueType?.let { ValueTypeConverter.decode(it) },
                unit = entity.unit,
                allowEmptyText = entity.allowEmptyText,
                sortOrder = entity.sortOrder,
                parent = parent,
            )
        }
    }.filterNotNull()
}

fun Category.toEntity(): CategoryEntity = when (this) {
    is Category.MetaCategory -> CategoryEntity(
        id = id, name = name, emoji = emoji, color = color,
        valueType = ValueTypeConverter.encode(valueType),
        unit = unit, allowEmptyText = allowEmptyText, sortOrder = sortOrder,
        parentId = null,
    )
    is Category.SubCategory -> CategoryEntity(
        id = id, name = name, emoji = emoji, color = color,
        valueType = valueType?.let { ValueTypeConverter.encode(it) },
        unit = unit, allowEmptyText = allowEmptyText, sortOrder = sortOrder,
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
