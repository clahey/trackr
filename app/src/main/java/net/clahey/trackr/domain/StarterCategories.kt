package net.clahey.trackr.domain

import java.util.UUID

/**
 * A starter category with its display name already resolved from resources. The UI layer holds the
 * `@StringRes` seed and resolves it before handing the list to the repository, so the data layer
 * stays free of Android resource dependencies.
 */
data class StarterCategoryInput(
    val name: String,
    val emoji: String,
    val color: Long,
    val valueType: ValueType,
    val defaultValue: EventValue?,
)

/**
 * Builds the top-level categories to create for "Add starter categories": those whose name is not
 * already present (case-insensitive), placed at the top of the list in the given order.
 *
 * Pure so both the real and fake repositories share one behaviour and it can be unit-tested without
 * Room. Ids default to random UUIDs; callers may inject a generator for deterministic tests.
 *
 * @spec CAT-UI-090, LS-BE-093
 */
fun starterCategoriesToInsert(
    existingNames: Collection<String>,
    minSortOrder: Int?,
    specs: List<StarterCategoryInput>,
    newId: () -> String = { UUID.randomUUID().toString() },
): List<Category.MetaCategory> {
    val present = existingNames.map { it.trim().lowercase() }.toSet()
    val missing = specs.filter { it.name.trim().lowercase() !in present }
    // Place the block just above the current top, preserving the listed order (first spec on top).
    // sortOrder sorts ascending, so lower values are the top of the list; these are intentionally
    // below `base` (the current minimum) to land above every existing category.
    val base = minSortOrder ?: 0
    return missing.mapIndexed { i, spec ->
        Category.MetaCategory(
            id = newId(),
            name = spec.name,
            emoji = spec.emoji,
            color = spec.color,
            valueType = spec.valueType,
            defaultValue = spec.defaultValue,
            allowEmptyText = true,
            sortOrder = base - missing.size + i,
        )
    }
}
