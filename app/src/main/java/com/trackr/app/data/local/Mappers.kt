package com.trackr.app.data.local

import com.trackr.app.data.local.converters.EventValueConverter
import com.trackr.app.data.local.converters.InstantConverter
import com.trackr.app.data.local.converters.StringListConverter
import com.trackr.app.data.local.converters.ValueTypeConverter
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event

fun CategoryEntity.toDomain(): Category = Category(
    id = id, name = name, emoji = emoji, color = color,
    valueType = ValueTypeConverter.decode(valueType),
    unit = unit, allowEmptyText = allowEmptyText, sortOrder = sortOrder,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id, name = name, emoji = emoji, color = color,
    valueType = ValueTypeConverter.encode(valueType),
    unit = unit, allowEmptyText = allowEmptyText, sortOrder = sortOrder,
)

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
