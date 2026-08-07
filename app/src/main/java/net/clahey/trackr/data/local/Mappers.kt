package net.clahey.trackr.data.local

import net.clahey.trackr.data.local.converters.EventValueConverter
import net.clahey.trackr.data.local.converters.InstantConverter
import net.clahey.trackr.data.local.converters.StringListConverter
import net.clahey.trackr.data.local.converters.ValueTypeConverter
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.theme.DEFAULT_CATEGORY_COLOR
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun CategoryEntity.toMetaCategory() = Category.MetaCategory(
    id = id, name = name,
    emoji = emoji ?: "",
    color = color ?: DEFAULT_CATEGORY_COLOR,
    valueType = valueType?.let { ValueTypeConverter.decode(it) } ?: ValueType.None,
    defaultValue = EventValueConverter.decode(defaultValue),
    allowEmptyText = allowEmptyText, sortOrder = sortOrder,
)

private fun CategoryEntity.toSubCategory(parent: Category.MetaCategory) = Category.SubCategory(
    id = id, name = name,
    emoji = emoji, color = color,
    valueType = valueType?.let { ValueTypeConverter.decode(it) },
    defaultValue = EventValueConverter.decode(defaultValue),
    allowEmptyText = allowEmptyText, sortOrder = sortOrder,
    parent = parent,
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
                entity.toSubCategory(parent)
            }
        }
    }
}

// @spec DM-PROC-022
fun CategoryWithParent.toDomain(): Category {
    val parentMeta = parent?.toMetaCategory()
    return if (parentMeta != null) {
        category.toSubCategory(parentMeta)
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

private val hhMm = DateTimeFormatter.ofPattern("HH:mm")
private fun LocalTime.toHHmm(): String = format(hhMm)
private fun String.toLocalTimeHHmm(): LocalTime = LocalTime.parse(this, hhMm)

// @spec REM-DATA-002
fun ReminderEntity.toDomain(): Reminder {
    val decodedDaysActive = StringListConverter.decode(daysActive).map { DayOfWeek.valueOf(it) }.toSet()
    return Reminder(
        categoryId = categoryId,
        // An empty daysActive is malformed — the UI never saves one (REM-UI-009) — but
        // ReminderScheduling.kt's day-walking loops spin forever against it, so a row that
        // somehow has one is decoded as disabled rather than risking a hang.
        enabled = enabled && decodedDaysActive.isNotEmpty(),
        // .uppercase() (added 2026-08-07) accepts rows saved by earlier test builds under the old
        // lowercase "fixed"/"random" encoding, since that test data made it onto a live device.
        mode = ReminderMode.valueOf(mode.uppercase()),
        times = times?.let { StringListConverter.decode(it).map { t -> t.toLocalTimeHHmm() } } ?: emptyList(),
        windowStart = windowStart?.toLocalTimeHHmm(),
        windowEnd = windowEnd?.toLocalTimeHHmm(),
        occurrencesPerDay = occurrencesPerDay,
        daysActive = decodedDaysActive.ifEmpty { DayOfWeek.entries.toSet() },
        showCategoryInNotification = showCategoryInNotification,
        nextFireAt = nextFireAt?.let { InstantConverter.decode(it) },
    )
}

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    categoryId = categoryId,
    enabled = enabled,
    mode = mode.name,
    times = if (times.isNotEmpty()) StringListConverter.encode(times.map { it.toHHmm() }) else null,
    windowStart = windowStart?.toHHmm(),
    windowEnd = windowEnd?.toHHmm(),
    occurrencesPerDay = occurrencesPerDay,
    daysActive = StringListConverter.encode(daysActive.map { it.name }),
    showCategoryInNotification = showCategoryInNotification,
    nextFireAt = nextFireAt?.let { InstantConverter.encode(it) },
)
