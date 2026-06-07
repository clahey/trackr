package net.clahey.trackr.domain

import java.time.Instant

data class Event(
    val id: String,
    val categoryId: String,
    val timestamp: Instant,
    val value: EventValue?,
    val notes: String?,
    val imagePaths: List<String>,
    val createdAt: Instant,
)
