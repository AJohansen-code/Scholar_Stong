package com.academicstrong.nutrition.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, DRINK }

/** One thing the student ate or drank. Replaces NutriTrack's planned-only MealPlanRecipes. */
@Entity(tableName = "food_log", indices = [Index("epochDay")])
data class FoodLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val mealType: MealType,
    val name: String,
    val calories: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val sugarG: Int = 0,
    val caffeineMg: Int = 0,
    val volumeMl: Int = 0,          // for drinks
    val isEstimate: Boolean = false // true when Gemini estimated the numbers
)

/** Single-row table (id = 1). Merges NutriTrack's DietaryRestrictions + the README "Health Profiler". */
@Entity(tableName = "nutrition_profile")
data class NutritionProfile(
    @PrimaryKey val id: Int = 1,
    val calorieGoal: Int = 2000,     // placeholder defaults: the student sets their own
    val proteinGoalG: Int = 100,
    val carbsGoalG: Int = 250,
    val fatGoalG: Int = 65,
    val fluidGoalMl: Int = 2000,
    val dietLabels: String = "",     // comma separated, e.g. "Vegan, Keto"
    val allergies: String = "",      // comma separated, e.g. "peanut, gluten"
    val showCalories: Boolean = true // lets users hide calorie numbers
)

@Entity(tableName = "pantry")
data class PantryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/** Shared with the mental-health side of Academic Strong. Delete this if Alicia already has a mood table. */
@Entity(tableName = "mood_checkins", indices = [Index(value = ["epochDay"], unique = true)])
data class MoodCheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val mood: Int,    // 1 (low) .. 5 (great)
    val stress: Int   // 1 (calm) .. 5 (very stressed)
)

data class Totals(
    val calories: Int = 0, val proteinG: Int = 0, val carbsG: Int = 0, val fatG: Int = 0,
    val sugarG: Int = 0, val caffeineMg: Int = 0, val fluidMl: Int = 0
)

fun List<FoodLogEntry>.totals() = Totals(
    calories = sumOf { it.calories }, proteinG = sumOf { it.proteinG }, carbsG = sumOf { it.carbsG },
    fatG = sumOf { it.fatG }, sugarG = sumOf { it.sugarG }, caffeineMg = sumOf { it.caffeineMg },
    fluidMl = sumOf { it.volumeMl }
)
