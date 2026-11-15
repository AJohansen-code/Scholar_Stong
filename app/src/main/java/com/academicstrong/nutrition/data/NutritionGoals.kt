package com.academicstrong.nutrition.data

import com.team2.studentfitness.database.HealthData
import com.team2.studentfitness.database.MacroLog
import com.team2.studentfitness.database.UserSettings
import com.team2.studentfitness.viewmodels.HealthCalculations
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class NutritionGoals(
    val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int,
    val fiberG: Int, val sugarG: Int, val sodiumMg: Int
)

data class DayTotals(
    val calories: Int = 0, val proteinG: Int = 0, val carbsG: Int = 0, val fatG: Int = 0,
    val fiberG: Int = 0, val sugarG: Int = 0, val sodiumMg: Int = 0
)

/**
 * Same formulas as the fitness app's Macros screen, so both screens always show the same goals:
 * calories from Mifflin-St Jeor x activity level, then a 30% protein / 40% carb / 30% fat split.
 * Falls back to 2000 kcal if the profile is missing or incomplete.
 */
fun computeGoals(health: HealthData?, settings: UserSettings?): NutritionGoals {
    val calories = runCatching {
        if (health == null || settings == null) error("No profile yet")
        val calc = HealthCalculations()
        val bmr = calc.calculateBmrMifflinStJeor(
            weightKg = health.weight.toDouble(),
            heightCm = health.height.toDouble(),
            ageYears = health.age,
            sex = runCatching { HealthCalculations.Sex.valueOf(settings.sex) }.getOrDefault(HealthCalculations.Sex.MALE)
        )
        val level = runCatching { HealthCalculations.ActivityLevel.valueOf(settings.activityLevel) }
            .getOrDefault(HealthCalculations.ActivityLevel.SEDENTARY)
        calc.calculateTdee(bmr, level).toInt()
    }.getOrDefault(2000)
    return NutritionGoals(
        calories = calories,
        proteinG = (calories * 0.30 / 4).toInt(),
        carbsG = (calories * 0.40 / 4).toInt(),
        fatG = (calories * 0.30 / 9).toInt(),
        fiberG = 25,
        sugarG = (calories * 0.10 / 4).toInt(),
        sodiumMg = 2300
    )
}

fun List<MacroLog>.totals() = DayTotals(
    calories = sumOf { it.calories }, proteinG = sumOf { it.protein }, carbsG = sumOf { it.carbs },
    fatG = sumOf { it.fat }, fiberG = sumOf { it.fiber }, sugarG = sumOf { it.sugar },
    sodiumMg = sumOf { it.sodium }
)

fun epochDayOf(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

fun MacroLog.epochDay(): Long = epochDayOf(timestamp)

/** Timestamp used when logging food for a past day: noon on that day. */
fun noonMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
