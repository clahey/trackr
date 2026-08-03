package net.clahey.trackr.domain

import net.clahey.trackr.domain.EventValue

// @spec DM-DATA-025, DM-DATA-026, DM-DATA-027, DM-PROC-018
sealed class Category {
    abstract val id: String
    abstract val name: String
    abstract val defaultValue: EventValue?
    abstract val allowEmptyText: Boolean
    abstract val sortOrder: Int
    abstract val resolvedEmoji: String
    abstract val resolvedColor: Long
    abstract val resolvedValueType: ValueType
    abstract val resolvedDefaultValue: EventValue?

    data class MetaCategory(
        override val id: String,
        override val name: String,
        val emoji: String,
        val color: Long,
        val valueType: ValueType,
        override val defaultValue: EventValue?,
        override val allowEmptyText: Boolean,
        override val sortOrder: Int,
    ) : Category() {
        override val resolvedEmoji get() = emoji
        override val resolvedColor get() = color
        override val resolvedValueType get() = valueType
        override val resolvedDefaultValue get() = defaultValue
    }

    data class SubCategory(
        override val id: String,
        override val name: String,
        val emoji: String?,
        val color: Long?,
        val valueType: ValueType?,
        override val defaultValue: EventValue?,
        override val allowEmptyText: Boolean,
        override val sortOrder: Int,
        val parent: MetaCategory,
    ) : Category() {
        override val resolvedEmoji get() = emoji ?: parent.emoji
        override val resolvedColor get() = color ?: parent.color
        override val resolvedValueType get() = valueType ?: parent.valueType
        override val resolvedDefaultValue get() = defaultValue ?: parent.defaultValue
    }
}
