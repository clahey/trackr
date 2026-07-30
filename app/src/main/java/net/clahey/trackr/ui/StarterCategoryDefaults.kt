package net.clahey.trackr.ui

import android.content.Context
import androidx.annotation.StringRes
import net.clahey.trackr.R
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.StarterCategoryInput
import net.clahey.trackr.domain.ValueType

/**
 * A seeded starter category. The name and (for Number types) the unit are `@StringRes` so they
 * localize; everything else is a plain constant. Colors are drawn from the preset palette.
 */
data class StarterCategorySpec(
    @StringRes val nameRes: Int,
    val emoji: String,
    val color: Long,
    val valueType: ValueType,
    @StringRes val unitRes: Int? = null,
)

// @spec CAT-UI-090
val STARTER_CATEGORIES: List<StarterCategorySpec> = listOf(
    StarterCategorySpec(R.string.starter_mood, "🙂", 0xFFFFB300L, ValueType.Scale),
    StarterCategorySpec(R.string.starter_sleep, "😴", 0xFF3949ABL, ValueType.Duration),
    StarterCategorySpec(R.string.starter_water, "💧", 0xFF1E88E5L, ValueType.Number, R.string.starter_unit_glasses),
    StarterCategorySpec(R.string.starter_exercise, "🏋️", 0xFF43A047L, ValueType.Exercise),
    StarterCategorySpec(R.string.starter_medication, "💊", 0xFFE53935L, ValueType.Number, R.string.starter_unit_mg),
    StarterCategorySpec(R.string.starter_pain, "🤕", 0xFF8E24AAL, ValueType.Scale),
)

/**
 * Resolves [STARTER_CATEGORIES] into repository inputs with names/units pulled from resources.
 * Plain (non-`@Composable`) function — call it where it's actually needed (e.g. on the "Add
 * starter categories" click) rather than resolving on every composition of a screen that offers it.
 */
fun resolveStarterCategoryInputs(context: Context): List<StarterCategoryInput> =
    STARTER_CATEGORIES.map { spec ->
        StarterCategoryInput(
            name = context.getString(spec.nameRes),
            emoji = spec.emoji,
            color = spec.color,
            valueType = spec.valueType,
            defaultValue = spec.unitRes?.let { EventValue.NumberValue(0.0, context.getString(it)) },
        )
    }
