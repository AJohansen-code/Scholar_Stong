package com.academicstrong.nutrition.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Used for AI meal ideas. Logged food now lives in the fitness app's MacroLog table. */
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * Single-row table (id = 1). Only holds what the fitness app's UserSettings has no place for.
 * Calorie and macro goals are calculated from the fitness profile (see NutritionGoals.kt).
 */
@Entity(tableName = "nutrition_profile")
data class NutritionProfile(
    @PrimaryKey val id: Int = 1,
    val dietLabels: String = "",     // comma separated, e.g. "Vegan, Keto"
    val allergies: String = "",      // comma separated, e.g. "peanut, gluten"
    val showCalories: Boolean = true // lets users hide calorie numbers
)

@Entity(tableName = "pantry")
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
