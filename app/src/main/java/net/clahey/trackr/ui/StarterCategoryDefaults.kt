package net.clahey.trackr.ui

import android.content.Context
import net.clahey.trackr.R
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.StarterCategoryInput
import net.clahey.trackr.domain.ValueType

/**
 * The seeded starter categories, with names/units resolved from resources so they localize.
 * Plain (non-`@Composable`) function — call it where it's actually needed (e.g. on the "Add
 * starter categories" click) rather than resolving on every composition of a screen that offers it.
 */
// @spec CAT-UI-090
fun getStarterCategoryInputs(context: Context): List<StarterCategoryInput> = listOf(
    StarterCategoryInput(context.getString(R.string.starter_mood), "🙂", 0xFFFFB300L, ValueType.Scale, null),
    StarterCategoryInput(context.getString(R.string.starter_sleep), "😴", 0xFF3949ABL, ValueType.Duration, null),
    StarterCategoryInput(
        context.getString(R.string.starter_water), "💧", 0xFF1E88E5L, ValueType.Number,
        EventValue.NumberValue(0.0, context.getString(R.string.starter_unit_glasses)),
    ),
    StarterCategoryInput(context.getString(R.string.starter_exercise), "🏋️", 0xFF43A047L, ValueType.Exercise, null),
    StarterCategoryInput(
        context.getString(R.string.starter_medication), "💊", 0xFFE53935L, ValueType.Number,
        EventValue.NumberValue(0.0, context.getString(R.string.starter_unit_mg)),
    ),
    StarterCategoryInput(context.getString(R.string.starter_pain), "🤕", 0xFF8E24AAL, ValueType.Scale, null),
)
