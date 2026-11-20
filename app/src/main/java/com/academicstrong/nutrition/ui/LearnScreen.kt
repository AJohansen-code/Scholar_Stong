package com.academicstrong.nutrition.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Guide(val title: String, val body: String)

// General, conservative guidance written for students.
private val guides = listOf(
    Guide("Build a balanced plate",
        "A simple starting point: fill about half your plate with vegetables or fruit, a quarter with a protein (eggs, beans, chicken, tofu, yogurt), and a quarter with whole grains or starchy carbs (oats, rice, whole-wheat bread). Add a little healthy fat like nuts, olive oil, or avocado."),
    Guide("Protein basics",
        "Protein helps with muscle repair and keeps you full. Spreading it across meals works better than one big serving. A common minimum for adults is about 0.8 g per kg of body weight, and people who lift regularly often aim higher. A dietitian can help you pick a number."),
    Guide("Hydration and caffeine",
        "Drinks count toward your fluids, and water is the easiest default. Pale yellow urine is a rough sign you're hydrated. Up to about 400 mg of caffeine a day is generally considered fine for most healthy adults, but caffeine late in the day can hurt sleep, and poor sleep makes stress and cravings worse."),
    Guide("Eating around class and study",
        "Long gaps between meals can leave you foggy and snacky. Keep portable options on hand: fruit, nut butter sandwiches, yogurt, cheese sticks, trail mix. Pair carbs with protein so the energy lasts through a lecture or study block."),
    Guide("Reading a nutrition label",
        "Check the serving size first, because every number on the label is per serving. Then look at added sugars, fiber, and protein. Foods with more fiber and protein tend to be more filling."),
    Guide("Food and mood",
        "Regular meals, enough fluids, and decent sleep tend to go along with steadier mood and focus for many people. Use your Progress tab to see your own pattern. If food or body image is causing you stress, turn off calorie numbers in Goals and talk to your campus health or counseling center.")
)

@Composable
fun LearnScreen() {
    var open by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(guides.size) { i ->
            val g = guides[i]
            Card(Modifier.fillMaxWidth().clickable { open = if (open == g.title) null else g.title }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium)
                    if (open == g.title) Text(g.body)
                }
            }
        }
        item { Text("General information, not medical advice.", style = MaterialTheme.typography.bodySmall) }
    }
}
