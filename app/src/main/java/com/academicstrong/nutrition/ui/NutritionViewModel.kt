package com.academicstrong.nutrition.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.academicstrong.nutrition.ai.GeminiNutritionService
import com.academicstrong.nutrition.ai.MealIdea
import com.academicstrong.nutrition.ai.conflictsWith
import com.academicstrong.nutrition.data.*
import com.team2.studentfitness.DatabaseCreation
import com.team2.studentfitness.database.HealthData
import com.team2.studentfitness.database.MacroLog
import com.team2.studentfitness.database.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AiState(val busy: Boolean = false, val error: String? = null)

/**
 * NutriTrack's brain. Food entries go into the fitness app's MacroLog table, goals come from the
 * fitness profile, and mood is read from the fitness app's MentalHealth log. Only diet labels,
 * allergies and the pantry live in NutriTrack's own database.
 */
class NutritionViewModel(app: Application) : AndroidViewModel(app) {

    companion object { const val HISTORY_DAYS = 14 }

    private val nutritionDb = NutritionDatabase.get(app)
    private val fitnessDb = (app as DatabaseCreation).database
    private val gemini = GeminiNutritionService()

    val today: Long = LocalDate.now().toEpochDay()
    val firstDay: Long = today - (HISTORY_DAYS - 1)
    val selectedDay = MutableStateFlow(today)

    val profile: StateFlow<NutritionProfile> = nutritionDb.profile().observe()
        .map { it ?: NutritionProfile() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NutritionProfile())

    val pantry: StateFlow<List<PantryItem>> = nutritionDb.pantry().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val settings: StateFlow<UserSettings?> = fitnessDb.settingsDao().getLatestFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val health = MutableStateFlow<HealthData?>(null)

    /** Same goals the Macros screen shows. */
    val goals: StateFlow<NutritionGoals> = combine(health, settings) { h, s -> computeGoals(h, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, computeGoals(null, null))

    /** Every MacroLog entry, newest first. */
    val allLogs: StateFlow<List<MacroLog>> = fitnessDb.macroLogDao().getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dayEntries: StateFlow<List<MacroLog>> = combine(allLogs, selectedDay) { logs, day ->
        logs.filter { it.epochDay() == day }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Average mood per day on a 1-4 scale, from the fitness app's emoji check-ins. */
    val moodByDay = MutableStateFlow<Map<Long, Double>>(emptyMap())

    val ai = MutableStateFlow(AiState())
    val ideas = MutableStateFlow<List<MealIdea>>(emptyList())
    val skippedIdeas = MutableStateFlow(0)
    val insight = MutableStateFlow<String?>(null)

    val moodInsight: StateFlow<String?> = combine(allLogs, moodByDay, goals) { logs, moods, g ->
        val calsByDay = logs.groupBy { it.epochDay() }.mapValues { (_, v) -> v.sumOf { it.calories } }
        val points = moods.mapNotNull { (day, mood) -> calsByDay[day]?.let { mood to it } }
        if (points.size < 5) return@combine null
        val (near, under) = points.partition { it.second >= g.calories * 0.85 }
        if (near.size < 2 || under.size < 2) return@combine null
        "On days you ate close to your goal, your mood averaged %.1f (scale 1-4). On days well under it, it averaged %.1f. That is a pattern in your own data, not proof of cause."
            .format(near.map { it.first }.average(), under.map { it.first }.average())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            health.value = fitnessDb.healthDao().getLatest()
            moodByDay.value = loadMoods()
        }
    }

    private suspend fun loadMoods(): Map<Long, Double> {
        val score = mapOf("😔" to 1, "😐" to 2, "🙂" to 3, "🤩" to 4)
        return fitnessDb.mentalHealthDao().getAll()
            .mapNotNull { m -> score[m.mood]?.let { epochDayOf(m.timestamp) to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.average() }
    }

    // ---------- day navigation ----------
    fun shiftDay(delta: Int) {
        selectedDay.value = (selectedDay.value + delta).coerceIn(firstDay, today)
        insight.value = null
    }

    private fun stampForSelectedDay(): Long =
        if (selectedDay.value == today) System.currentTimeMillis() else noonMillis(selectedDay.value)

    // ---------- logging (writes to the fitness app's MacroLog) ----------
    fun addEntry(log: MacroLog) = viewModelScope.launch {
        fitnessDb.macroLogDao().insert(log.copy(timestamp = stampForSelectedDay()))
    }

    fun deleteEntry(log: MacroLog) = viewModelScope.launch { fitnessDb.macroLogDao().deleteById(log.id) }

    fun logFromText(description: String, onDone: () -> Unit) = runAi {
        val stamp = stampForSelectedDay()
        gemini.estimateFoods(description).forEach { f ->
            fitnessDb.macroLogDao().insert(
                MacroLog(
                    name = "${f.name} (est.)", calories = f.calories, protein = f.proteinG, carbs = f.carbsG,
                    fat = f.fatG, fiber = f.fiberG, sugar = f.sugarG, sodium = f.sodiumMg, timestamp = stamp
                )
            )
        }
        onDone()
    }

    fun saveProfile(p: NutritionProfile) = viewModelScope.launch { nutritionDb.profile().save(p.copy(id = 1)) }

    // ---------- pantry + meal ideas ----------
    fun addPantry(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) nutritionDb.pantry().insert(PantryItem(name = name.trim()))
    }
    fun removePantry(item: PantryItem) = viewModelScope.launch { nutritionDb.pantry().delete(item) }

    fun generateIdeas(type: MealType) = runAi {
        val g = goals.value
        val t = dayEntries.value.totals()
        val all = gemini.suggestMeals(
            profile.value, pantry.value.map { it.name }, type, count = 3,
            caloriesLeft = g.calories - t.calories, proteinLeftG = g.proteinG - t.proteinG
        )
        val safe = all.filterNot { it.conflictsWith(profile.value) }
        skippedIdeas.value = all.size - safe.size
        ideas.value = safe
    }

    fun logIdea(idea: MealIdea) = viewModelScope.launch {
        fitnessDb.macroLogDao().insert(
            MacroLog(
                name = "${idea.name} (est.)", calories = idea.calories, protein = idea.proteinG,
                carbs = idea.carbsG, fat = idea.fatG, timestamp = stampForSelectedDay()
            )
        )
    }

    // ---------- AI feedback ----------
    fun refreshInsight() = runAi {
        val p = profile.value
        val g = goals.value
        val t = dayEntries.value.totals()
        val calorieLine = if (p.showCalories) "Calories ${t.calories} of ${g.calories}. "
        else "The student hides calorie numbers, so do not mention calories. "
        insight.value = gemini.dailyInsight(
            calorieLine +
                "Protein ${t.proteinG} of ${g.proteinG} g. Carbs ${t.carbsG} of ${g.carbsG} g. " +
                "Fat ${t.fatG} of ${g.fatG} g. Fiber ${t.fiberG} of ${g.fiberG} g. " +
                "Sugar ${t.sugarG} g. Sodium ${t.sodiumMg} mg. Logged: ${dayEntries.value.joinToString { it.name }}."
        )
    }

    private fun runAi(block: suspend () -> Unit) {
        viewModelScope.launch {
            ai.value = AiState(busy = true)
            try {
                block()
                ai.value = AiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ai.value = AiState(error = e.message ?: "Something went wrong. Try again.")
            }
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NutritionViewModel(app) as T
    }
}
