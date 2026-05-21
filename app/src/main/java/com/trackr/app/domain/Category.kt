package com.trackr.app.domain

// @spec DM-DATA-025, DM-DATA-026, DM-DATA-027, DM-PROC-018
sealed class Category {
    abstract val id: String
    abstract val name: String
    abstract val unit: String?
    abstract val allowEmptyText: Boolean
    abstract val sortOrder: Int
    abstract val resolvedEmoji: String
    abstract val resolvedColor: Long
    abstract val resolvedValueType: ValueType

    data class MetaCategory(
        override val id: String,
        override val name: String,
        val emoji: String,
        val color: Long,
        val valueType: ValueType,
        override val unit: String?,
        override val allowEmptyText: Boolean,
        override val sortOrder: Int,
    ) : Category() {
        override val resolvedEmoji get() = emoji
        override val resolvedColor get() = color
        override val resolvedValueType get() = valueType
    }

    data class SubCategory(
        override val id: String,
        override val name: String,
        val emoji: String?,
        val color: Long?,
        val valueType: ValueType?,
        override val unit: String?,
        override val allowEmptyText: Boolean,
        override val sortOrder: Int,
        val parent: MetaCategory,
    ) : Category() {
        override val resolvedEmoji get() = emoji ?: parent.emoji
        override val resolvedColor get() = color ?: parent.color
        override val resolvedValueType get() = valueType ?: parent.valueType
    }
}
