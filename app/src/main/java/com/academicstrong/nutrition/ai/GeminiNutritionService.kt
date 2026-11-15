package com.academicstrong.nutrition.ai

import com.academicstrong.nutrition.data.MealType
import com.academicstrong.nutrition.data.NutritionProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import java.io.IOException

data class ParsedFood(
    val name: String, val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int,
    val fiberG: Int, val sugarG: Int, val sodiumMg: Int
)

data class MealIdea(
    val name: String, val minutes: Int, val ingredients: List<String>, val instructions: String,
    val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int
)

/**
 * Talks to Gemini through Firebase AI Logic (Gemini Developer API backend).
 * There is no API key in the app. Requests go through Firebase, which checks App Check first.
 * Call installAppCheck(...) from your Application class (see AppCheckSetup.kt).
 * Check the Firebase docs for current model names and free-tier limits.
 */
class GeminiNutritionService {

    private val ai by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }

    private fun jsonModel(schema: Schema): GenerativeModel = ai.generativeModel(
        modelName = MODEL,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        }
    )

    private val textModel: GenerativeModel by lazy { ai.generativeModel(MODEL) }

    // ---------- public API (same signatures as the direct-HTTP version) ----------

    /** "2 eggs, toast and a latte" -> separate estimated entries. This is the "I don't count calories" feature. */
    suspend fun estimateFoods(description: String): List<ParsedFood> {
        val prompt = """
            A college student describes what they ate or drank: "$description"
            Split it into separate items (foods and drinks) and estimate nutrition for each.
            Use typical portion sizes when none are given.
            Calories are kcal, sodiumMg is milligrams, everything else is grams. Round to whole numbers.
        """.trimIndent()
        val arr = JSONArray(askJson(prompt, Schema.array(foodSchema)))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ParsedFood(
                name = o.getString("name"), calories = o.optInt("calories"), proteinG = o.optInt("proteinG"),
                carbsG = o.optInt("carbsG"), fatG = o.optInt("fatG"), fiberG = o.optInt("fiberG"),
                sugarG = o.optInt("sugarG"), sodiumMg = o.optInt("sodiumMg")
            )
        }
    }

    /** Healthy meal ideas from the pantry, sized to what is left in today's goals. */
    suspend fun suggestMeals(
        profile: NutritionProfile, pantry: List<String>, mealType: MealType,
        count: Int, caloriesLeft: Int, proteinLeftG: Int
    ): List<MealIdea> {
        val prompt = """
            Suggest $count healthy ${mealType.name.lowercase()} ideas for a college student.
            Keep them cheap, quick (under 30 minutes) and doable in a dorm or small kitchen.
            Pantry items they already have (use them where it makes sense): ${pantry.ifEmpty { listOf("none listed") }.joinToString()}.
            Diet labels: ${profile.dietLabels.ifBlank { "none" }}.
            Allergies, which must NOT appear in any ingredient: ${profile.allergies.ifBlank { "none" }}.
            Aim for roughly ${caloriesLeft.coerceAtLeast(250)} kcal and ${proteinLeftG.coerceAtLeast(10)} g protein per idea.
            ingredients is a list of short strings like "2 eggs". instructions is 2-4 short sentences.
        """.trimIndent()
        val arr = JSONArray(askJson(prompt, Schema.array(mealSchema)))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ing = o.getJSONArray("ingredients")
            MealIdea(
                name = o.getString("name"), minutes = o.optInt("minutes"),
                ingredients = (0 until ing.length()).map { ing.getString(it) },
                instructions = o.getString("instructions"), calories = o.optInt("calories"),
                proteinG = o.optInt("proteinG"), carbsG = o.optInt("carbsG"), fatG = o.optInt("fatG")
            )
        }
    }

    /** Short, kind feedback on the day's balance. Free text. */
    suspend fun dailyInsight(summary: String): String = call {
        textModel.generateContent(
            """
            You are a supportive nutrition coach inside a student wellness app. Here is today's log versus the student's goals:
            $summary
            Reply in 2-3 short, friendly sentences about balance (protein, fiber, fruit/veg, sugar, sodium) and one easy next step.
            Do not shame, do not suggest eating less than their goal, do not give medical advice, and do not mention weight.
            """.trimIndent()
        ).text.orEmpty()
    }.trim()

    // ---------- plumbing ----------

    private suspend fun askJson(prompt: String, schema: Schema): String = call {
        jsonModel(schema).generateContent(prompt).text ?: throw IOException("Empty response from the AI.")
    }

    /** Turns SDK errors into short messages a student can act on. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        val m = e.message.orEmpty().lowercase()
        throw IOException(
            when {
                "quota" in m || "429" in m -> "Free-tier limit reached. Try again in a minute."
                "app check" in m || "403" in m || "permission" in m ->
                    "The AI request was blocked. Check Firebase AI Logic and App Check setup."
                else -> "The AI request failed. Check your connection and try again."
            }, e
        )
    }

    // The Firebase SDK treats every field as required unless listed in optionalProperties.
    private val foodSchema = Schema.obj(
        mapOf(
            "name" to Schema.string(), "calories" to Schema.integer(), "proteinG" to Schema.integer(),
            "carbsG" to Schema.integer(), "fatG" to Schema.integer(), "fiberG" to Schema.integer(),
            "sugarG" to Schema.integer(), "sodiumMg" to Schema.integer()
        )
    )

    private val mealSchema = Schema.obj(
        mapOf(
            "name" to Schema.string(), "minutes" to Schema.integer(),
            "ingredients" to Schema.array(Schema.string()),
            "instructions" to Schema.string(), "calories" to Schema.integer(), "proteinG" to Schema.integer(),
            "carbsG" to Schema.integer(), "fatG" to Schema.integer()
        )
    )

    private companion object {
        // Verify this against the model list in the Firebase AI Logic docs before you ship.
        const val MODEL = "gemini-3.5-flash"
    }
}

/**
 * Safety net for the "100% certainty gluten/nut-free" persona in the NutriTrack README.
 * The model can slip, so ideas are also checked locally. This is a simple text match,
 * not a guarantee, and the UI says so.
 */
fun MealIdea.conflictsWith(profile: NutritionProfile): Boolean {
    val terms = profile.allergies.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val haystack = (name + " " + ingredients.joinToString(" ")).lowercase()
    return terms.any { haystack.contains(it) }
}
