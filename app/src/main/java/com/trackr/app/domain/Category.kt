package com.trackr.app.domain

import java.time.Instant

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val valueType: ValueType,
    val unit: String?,
    val allowEmptyText: kotlin.Boolean,
    val sortOrder: Int,
)
