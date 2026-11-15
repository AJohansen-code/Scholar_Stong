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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AiState(val busy: Boolean = false, val error: String? = null)
private data class DayPoint(val mood: Int, val stress: Int, val calories: Int)

class NutritionViewModel(app: Application) : AndroidViewModel(app) {

    companion object { const val HISTORY_DAYS = 14 }

    private val db = NutritionDatabase.get(app)
    private val gemini = GeminiNutritionService()

    val today: Long = LocalDate.now().toEpochDay()
    val firstDay: Long = today - (HISTORY_DAYS - 1)
    val selectedDay = MutableStateFlow(today)

    val profile: StateFlow<NutritionProfile> = db.profile().observe()
        .map { it ?: NutritionProfile() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NutritionProfile())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayEntries: StateFlow<List<FoodLogEntry>> = selectedDay
        .flatMapLatest { db.foodLog().observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<FoodLogEntry>> = db.foodLog().observeRange(firstDay, today)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val moods: StateFlow<List<MoodCheckIn>> = db.mood().observeRange(firstDay, today)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pantry: StateFlow<List<PantryItem>> = db.pantry().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ai = MutableStateFlow(AiState())
    val ideas = MutableStateFlow<List<MealIdea>>(emptyList())
    val skippedIdeas = MutableStateFlow(0)
    val insight = MutableStateFlow<String?>(null)

    /** Plain-language link between eating and mood, computed on the device from the student's own data. */
    val moodInsight: StateFlow<String?> = combine(history, moods, profile) { log, moods, p ->
        val calsByDay = log.groupBy { it.epochDay }.mapValues { (_, v) -> v.sumOf { it.calories } }
        val points = moods.mapNotNull { m -> calsByDay[m.epochDay]?.let { DayPoint(m.mood, m.stress, it) } }
        if (points.size < 5) return@combine null
        val (near, under) = points.partition { it.calories >= p.calorieGoal * 0.85 }
        if (near.size < 2 || under.size < 2) return@combine null
        fun avg(l: List<DayPoint>, f: (DayPoint) -> Int) = l.map(f).average()
        "On days you ate close to your goal, your mood averaged %.1f and stress %.1f. On days well under it, mood averaged %.1f and stress %.1f. That is a pattern in your own data, not proof of cause."
            .format(avg(near) { it.mood }, avg(near) { it.stress }, avg(under) { it.mood }, avg(under) { it.stress })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------- day navigation ----------
    fun shiftDay(delta: Int) {
        selectedDay.value = (selectedDay.value + delta).coerceIn(firstDay, today)
        insight.value = null
    }

    // ---------- logging ----------
    fun addEntry(e: FoodLogEntry) = viewModelScope.launch { db.foodLog().insert(e.copy(epochDay = selectedDay.value)) }
    fun deleteEntry(e: FoodLogEntry) = viewModelScope.launch { db.foodLog().delete(e) }

    fun logFromText(description: String, onDone: () -> Unit) = runAi {
        val foods = gemini.estimateFoods(description)
        db.foodLog().insertAll(foods.map {
            FoodLogEntry(
                epochDay = selectedDay.value, mealType = it.mealType, name = it.name, calories = it.calories,
                proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG, sugarG = it.sugarG,
                caffeineMg = it.caffeineMg, volumeMl = it.volumeMl, isEstimate = true
            )
        })
        onDone()
    }

    fun saveMood(mood: Int, stress: Int) = viewModelScope.launch {
        db.mood().upsert(MoodCheckIn(epochDay = selectedDay.value, mood = mood, stress = stress))
    }

    fun saveProfile(p: NutritionProfile) = viewModelScope.launch { db.profile().save(p.copy(id = 1)) }

    // ---------- pantry + meal ideas ----------
    fun addPantry(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) db.pantry().insert(PantryItem(name = name.trim()))
    }
    fun removePantry(item: PantryItem) = viewModelScope.launch { db.pantry().delete(item) }

    fun generateIdeas(type: MealType) = runAi {
        val p = profile.value
        val t = dayEntries.value.totals()
        val all = gemini.suggestMeals(
            p, pantry.value.map { it.name }, type, count = 3,
            caloriesLeft = p.calorieGoal - t.calories, proteinLeftG = p.proteinGoalG - t.proteinG
        )
        val safe = all.filterNot { it.conflictsWith(p) }
        skippedIdeas.value = all.size - safe.size
        ideas.value = safe
    }

    fun logIdea(idea: MealIdea, type: MealType) = viewModelScope.launch {
        db.foodLog().insert(
            FoodLogEntry(
                epochDay = selectedDay.value, mealType = type, name = idea.name, calories = idea.calories,
                proteinG = idea.proteinG, carbsG = idea.carbsG, fatG = idea.fatG, isEstimate = true
            )
        )
    }

    // ---------- AI feedback ----------
    fun refreshInsight() = runAi {
        val p = profile.value
        val t = dayEntries.value.totals()
        val calorieLine = if (p.showCalories) "Calories ${t.calories} of ${p.calorieGoal}. "
        else "The student hides calorie numbers, so do not mention calories. "
        insight.value = gemini.dailyInsight(
            calorieLine +
                "Protein ${t.proteinG} of ${p.proteinGoalG} g. Carbs ${t.carbsG} of ${p.carbsGoalG} g. " +
                "Fat ${t.fatG} of ${p.fatGoalG} g. Sugar ${t.sugarG} g. Caffeine ${t.caffeineMg} mg. " +
                "Fluids ${t.fluidMl} of ${p.fluidGoalMl} ml. Logged: ${dayEntries.value.joinToString { it.name }}."
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
